package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.TabOverlayHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the server's /active answer, one line per global booster:
 * <pre>
 * Global Money Booster » 2x activated by makophobia_ with 51m 24s remaining. Use /tipall ...
 * </pre>
 * and sets each booster's time left from it, so boosters popped before you logged in show
 * too, and one /active no longer lists is cleared. FOE+ runs /active quietly after you join
 * and every 15 minutes (never while a menu is open) and hides that answer; when you run it
 * yourself it shows as usual (and still updates the timers).
 */
public final class ActiveBoostersHandler {
    private static ActiveBoostersHandler INSTANCE;

    public static ActiveBoostersHandler instance() {
        if (INSTANCE == null) INSTANCE = new ActiveBoostersHandler();
        return INSTANCE;
    }

    private static final long FIRST_CHECK_MS = 30_000L;
    private static final long REFRESH_MS = 15L * 60L * 1000L;
    private static final long HIDE_WINDOW_MS = 3_000L;

    static final Pattern LINE = Pattern.compile(
            "^\\s*Global (luck|scale|xp|money) booster\\s*[»>]\\s*([\\d.]+)\\s*x\\s+activated by\\s+\\S+\\s+with\\s+(.+?)\\s+remaining\\b",
            Pattern.CASE_INSENSITIVE);

    private long joinedAt = 0L, lastCheckAt = 0L, hideUntil = 0L;
    /// Our quiet check is waiting for its answer: which boosters it listed so far.
    private boolean answering = false, sawAnswer = false;
    private final Set<String> listed = new HashSet<>();

    private ActiveBoostersHandler() {}

    /// From chat: true when the line is part of our own quiet /active answer (hidden).
    public boolean onGameMessage(Component message) {
        String text = ChatFormatting.stripFormatting(message.getString());
        if (text == null) return false;
        boolean ours = System.currentTimeMillis() < hideUntil;
        Booster b = parse(text);
        if (b != null) {
            StoreTimerHandler.instance().syncBooster(b.kind(), b.multiplier(), b.msLeft());
            if (ours) {
                sawAnswer = true;
                listed.add(b.kind());
            }
            return ours;
        }
        // The rest of our own answer: blank lines, or the line saying there's nothing on.
        if (ours && answering) {
            String lower = text.toLowerCase(Locale.ROOT);
            if (text.isBlank()) return true;
            if (lower.contains("booster") || lower.contains("no active") || lower.contains("nothing active")) {
                sawAnswer = true;
                return true;
            }
        }
        return false;
    }

    /// One booster from /active. Package-private for tests.
    record Booster(String kind, double multiplier, long msLeft) {}

    static Booster parse(String text) {
        Matcher m = LINE.matcher(text);
        if (!m.find()) return null;
        long ms = EventTimesHandler.parseDuration(m.group(3));
        if (ms < 0) return null;
        double multiplier;
        try {
            multiplier = Double.parseDouble(m.group(2));
        } catch (NumberFormatException e) {
            multiplier = 0;
        }
        return new Booster(m.group(1).toLowerCase(Locale.ROOT), multiplier, ms);
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        // Our answer is in: any booster it didn't list isn't on.
        if (answering && now >= hideUntil) {
            if (sawAnswer) {
                for (String kind : StoreTimerHandler.BOOSTER_KINDS) {
                    if (!listed.contains(kind)) StoreTimerHandler.instance().clearBooster(kind);
                }
            }
            answering = false;
        }
        if (mc.player == null || mc.getConnection() == null || !TabOverlayHandler.instance().isInInstance()) {
            joinedAt = 0L;
            return;
        }
        if (joinedAt == 0L) joinedAt = now;
        if (!Configs.hudConfig.eventsAutoRefresh.get()) return;
        boolean due = lastCheckAt == 0L ? now - joinedAt >= FIRST_CHECK_MS : now - lastCheckAt >= REFRESH_MS;
        if (!due || !SilentMenuHandler.instance().isQuietForBackgroundCommand()) return;
        lastCheckAt = now;
        hideUntil = now + HIDE_WINDOW_MS;
        answering = true;
        sawAnswer = false;
        listed.clear();
        SilentMenuHandler.instance().noteBackgroundCommand("active");
        mc.getConnection().sendCommand("active");
    }

    /// The server said to wait: check again after that.
    public void retryIn(long ms) {
        answering = false;
        lastCheckAt = System.currentTimeMillis() - REFRESH_MS + ms;
    }
}
