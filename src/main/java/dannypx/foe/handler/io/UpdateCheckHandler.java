package dannypx.foe.handler.io;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.helper.FoeChat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Looks at FOE+'s GitHub releases and, when there's a newer FOE+ for your Minecraft version than
 * the one you're running, says so once in chat a few seconds after you join. Nothing is said when
 * you're up to date, when you're ahead (a test build), or when GitHub can't be reached.
 */
public final class UpdateCheckHandler {
    private static UpdateCheckHandler INSTANCE;

    public static UpdateCheckHandler instance() {
        if (INSTANCE == null) INSTANCE = new UpdateCheckHandler();
        return INSTANCE;
    }

    private static final String RELEASES_API = "https://api.github.com/repos/itshaxr/FOE-Plus/releases?per_page=30";
    private static final String RELEASES_PAGE = "https://github.com/itshaxr/FOE-Plus/releases/latest";
    /// Look again this often while the game stays open.
    private static final long RECHECK_MS = 6L * 60L * 60L * 1000L;
    /// Say it this long after joining, so it isn't lost in the server's join messages.
    private static final long ANNOUNCE_DELAY_MS = 5_000L;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private volatile boolean fetching = false;
    private volatile long fetchedAt = 0L;
    /// The newest release for this Minecraft version ("1.0.75") and its page; null until known.
    private volatile String latest = null;
    private volatile String latestUrl = null;
    /// The version we've already told you about (once per version per game session).
    private String announced = null;
    private long onServerSince = 0L;

    private UpdateCheckHandler() {}

    /// Ask GitHub (in the background) unless we did recently.
    public void fetch() {
        long now = System.currentTimeMillis();
        if (fetching || (fetchedAt != 0L && now - fetchedAt < RECHECK_MS)) return;
        fetching = true;
        fetchedAt = now;
        HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES_API))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "FOE-Plus-update-check")
                .GET().build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            try {
                if (error != null || response == null || response.statusCode() != 200) {
                    LoggerHandler._debug("Update check: couldn't reach GitHub");
                    return;
                }
                String[] found = newestFor(response.body(), minecraftVersion());
                if (found != null) {
                    latest = found[0];
                    latestUrl = found[1];
                }
            } catch (Exception e) {
                LoggerHandler._debug("Update check failed: " + e.getMessage());
            } finally {
                fetching = false;
            }
        });
    }

    /// Every tick while on FishOnMC: say so once when a newer version is out.
    public void tick() {
        long now = System.currentTimeMillis();
        if (onServerSince == 0L) onServerSince = now;
        if (!Configs.mainConfig.updateCheck.get()) return;
        fetch();
        String newest = latest;
        if (newest == null || newest.equals(announced) || now - onServerSince < ANNOUNCE_DELAY_MS) return;
        if (!isNewer(newest, localVersion())) return;
        announced = newest;
        String url = latestUrl != null ? latestUrl : RELEASES_PAGE;
        Component link = Component.literal("Click here to download").withStyle(style -> style
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open the FOE+ " + newest + " release on GitHub"))));
        FoeChat.send(FoeChat.line("UPDATE", FoeChat.GREEN)
                .append(FoeChat.text("FOE+ "))
                .append(FoeChat.highlight(newest))
                .append(FoeChat.text(" is out (you have " + localVersion() + "). "))
                .append(link));
    }

    /// Left the server: the next join waits its few seconds again.
    public void onLeave() {
        onServerSince = 0L;
    }

    //region Helpers (package-private for tests)

    /// "1.0.74" from "1.0.74+1.21.11".
    static String localVersion() {
        return stripBuild(FishOnMCExtras.VERSION);
    }

    /// "1.21.11" from "1.0.74+1.21.11" (null when there's no "+").
    static String minecraftVersion() {
        String v = FishOnMCExtras.VERSION;
        int plus = v == null ? -1 : v.indexOf('+');
        return plus < 0 ? null : v.substring(plus + 1);
    }

    static String stripBuild(String version) {
        if (version == null) return "";
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        int plus = v.indexOf('+');
        return plus < 0 ? v : v.substring(0, plus);
    }

    /// From GitHub's release list: the newest published, non-prerelease FOE+ whose jar is for
    /// {@code mc} ("foe-plus-1.0.75+1.21.11.jar"), as {version, page}; null when there's none.
    static String[] newestFor(String json, String mc) {
        JsonArray releases = JsonParser.parseString(json).getAsJsonArray();
        String best = null, bestUrl = null;
        for (JsonElement e : releases) {
            if (!e.isJsonObject()) continue;
            JsonObject r = e.getAsJsonObject();
            if (bool(r, "draft") || bool(r, "prerelease")) continue;
            String version = stripBuild(string(r, "tag_name"));
            if (version.isEmpty() || !Character.isDigit(version.charAt(0))) continue;
            if (mc != null && !hasJarFor(r, mc)) continue;
            if (best == null || isNewer(version, best)) {
                best = version;
                bestUrl = string(r, "html_url");
            }
        }
        return best == null ? null : new String[]{best, bestUrl};
    }

    private static boolean hasJarFor(JsonObject release, String mc) {
        JsonArray assets = release.has("assets") && release.get("assets").isJsonArray() ? release.getAsJsonArray("assets") : null;
        if (assets == null) return false;
        for (JsonElement a : assets) {
            if (!a.isJsonObject()) continue;
            String name = string(a.getAsJsonObject(), "name");
            if (name != null && name.endsWith("+" + mc + ".jar")) return true;
        }
        return false;
    }

    /// True when {@code a} is a later version than {@code b} ("1.0.75" > "1.0.74", "1.1.0" >
    /// "1.0.99"), going by the numbers ("1.0.69-test1" counts as 1.0.69).
    static boolean isNewer(String a, String b) {
        int[] x = numbers(a), y = numbers(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int[] numbers(String version) {
        String v = stripBuild(version);
        int dash = v.indexOf('-');
        if (dash >= 0) v = v.substring(0, dash);
        String[] parts = v.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String digits = parts[i].replaceAll("\\D.*$", "");
            try {
                out[i] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    private static boolean bool(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
    }
    //endregion
}
