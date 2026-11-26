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
 * the one you're running, says so once in chat: a few seconds after you join, or, when there was none
 * then, the moment one comes out while you play (it looks every 10 minutes while you're on FishOnMC).
 * Only once each time the game is opened: after that it neither looks nor says it again until you
 * restart the game (still on the old version, you're told on that join). The release's own
 * message for players (an {@code <!-- in-game: ... -->} line in its notes, from the changelog's
 * "message") goes under it, and its changes show when you hover "What's new". Nothing is said when
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
    /// Look again this often while you're on the server, so a release reaches players already in game.
    private static final long RECHECK_MS = 10L * 60L * 1000L;
    /// At most this many changes in the "What's new" hover.
    private static final int MAX_CHANGES = 10;
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
    /// That release's message for players ("" for none) and its changes, from its notes.
    private volatile String latestMessage = "";
    private volatile java.util.List<String> latestChanges = java.util.List.of();
    /// GitHub's tag for the list we last read: unchanged lists come back as "304", which is quick and
    /// doesn't use up GitHub's allowance.
    private volatile String etag = null;
    /// Told you this game session: nothing more (no more looking either) until the game is restarted.
    private boolean told = false;
    private long onServerSince = 0L;

    private UpdateCheckHandler() {}

    /// Ask GitHub (in the background) unless we did recently, or already told you this game session.
    public void fetch() {
        long now = System.currentTimeMillis();
        if (told || fetching || (fetchedAt != 0L && now - fetchedAt < RECHECK_MS)) return;
        fetching = true;
        fetchedAt = now;
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(RELEASES_API))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "FOE-Plus-update-check");
        String tag = etag;
        if (tag != null) builder.header("If-None-Match", tag);
        HttpRequest request = builder.GET().build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            try {
                if (error == null && response != null && response.statusCode() == 304) return;   // nothing new
                if (error != null || response == null || response.statusCode() != 200) {
                    LoggerHandler._debug("Update check: couldn't reach GitHub");
                    return;
                }
                String[] found = newestFor(response.body(), minecraftVersion());
                if (found != null) {
                    latestUrl = found[1];
                    latestMessage = message(found[2]);
                    latestChanges = changes(found[2]);
                    latest = found[0];   // last, so the tick never sees the new version with the old notes
                }
                etag = response.headers().firstValue("ETag").orElse(null);
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
        if (told || !Configs.mainConfig.updateCheck.get()) return;
        fetch();
        String newest = latest;
        if (newest == null || now - onServerSince < ANNOUNCE_DELAY_MS) return;
        if (!isNewer(newest, localVersion())) return;
        told = true;
        String url = latestUrl != null ? latestUrl : RELEASES_PAGE;
        Component link = Component.literal("Click here to download").withStyle(style -> style
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open the FOE+ " + newest + " release on GitHub"))));
        dannypx.foe.handler.store.NotifySounds.play("update");
        net.minecraft.network.chat.MutableComponent line = FoeChat.line("UPDATE", FoeChat.GREEN)
                .append(FoeChat.text("FOE+ "))
                .append(FoeChat.highlight(newest))
                .append(FoeChat.text(" is out (you have " + localVersion() + "). "))
                .append(link);
        java.util.List<String> changes = latestChanges;
        if (!changes.isEmpty()) {
            net.minecraft.network.chat.MutableComponent hover = Component.literal("What's new in " + newest).withStyle(ChatFormatting.GOLD);
            for (String c : changes) hover.append(Component.literal("\n• " + c).withStyle(ChatFormatting.GRAY));
            line.append(FoeChat.text(" ")).append(Component.literal("[What's new]").withStyle(style -> style
                    .withColor(ChatFormatting.YELLOW)
                    .withHoverEvent(new HoverEvent.ShowText(hover))
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))));
        }
        FoeChat.send(line);
        String message = latestMessage;
        if (!message.isEmpty()) FoeChat.send(FoeChat.line("UPDATE", FoeChat.GREEN).append(FoeChat.white(message)));
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

    /// The release's message for players: the text of its {@code <!-- in-game: ... -->} line ("" for none).
    static String message(String body) {
        if (body == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<!--\\s*in-game:\\s*(.*?)\\s*-->", java.util.regex.Pattern.DOTALL).matcher(body);
        if (!m.find()) return "";
        String text = m.group(1).replaceAll("\\s+", " ").trim();
        return text.length() > 256 ? text.substring(0, 256) : text;
    }

    /// The release's changes: the "- " lines under "## What's Changed", without "by phckd" (at most
    /// {@link #MAX_CHANGES}, then "and N more").
    static java.util.List<String> changes(String body) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (body == null) return out;
        boolean in = false;
        int more = 0;
        for (String raw : body.split("\\r?\\n")) {
            String l = raw.trim();
            if (l.startsWith("#")) { in = l.toLowerCase(java.util.Locale.ROOT).contains("what's changed"); continue; }
            if (!in || !l.startsWith("- ")) continue;
            String c = l.substring(2).replaceAll("\\s+by @?phckd$", "").replace("**", "").trim();
            if (c.isEmpty()) continue;
            if (out.size() < MAX_CHANGES) out.add(c.length() > 120 ? c.substring(0, 117) + "..." : c);
            else more++;
        }
        if (more > 0) out.add("and " + more + " more");
        return out;
    }

    /// From GitHub's release list: the newest published, non-prerelease FOE+ whose jar is for
    /// {@code mc} ("foe-plus-1.0.75+1.21.11.jar"), as {version, page, notes}; null when there's none.
    static String[] newestFor(String json, String mc) {
        JsonArray releases = JsonParser.parseString(json).getAsJsonArray();
        String best = null, bestUrl = null, bestBody = null;
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
                bestBody = string(r, "body");
            }
        }
        return best == null ? null : new String[]{best, bestUrl, bestBody == null ? "" : bestBody};
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
