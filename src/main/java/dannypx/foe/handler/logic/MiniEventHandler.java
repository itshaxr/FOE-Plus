package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.TabOverlayHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the server's /minievent answer:
 * <pre>
 * KENAI MINI EVENT » Ends in 3d 6h 55m 2s
 * Daily missions reroll in 23h 55m 2s
 *
 * — Kenai River GIGANTIC
 *   └ (0/2) One Timer
 * — King Salmon
 *   └ (0/1) Daily
 * </pre>
 * for the Mini Event panel: the event, when it ends, when the daily missions reroll, and each
 * mission with its progress (in the server's own colours). FOE+ runs /minievent quietly now and
 * then and a little after catches (never while a menu is open) and hides that answer; when you
 * run it yourself it shows as usual.
 */
public class MiniEventHandler {
    private static MiniEventHandler INSTANCE = new MiniEventHandler();

    public static MiniEventHandler instance() {
        if (INSTANCE == null) INSTANCE = new MiniEventHandler();
        return INSTANCE;
    }

    private static final long REFRESH_MS = 10L * 60L * 1000L;
    /// With no event on, check far less often.
    private static final long IDLE_REFRESH_MS = 30L * 60L * 1000L;
    private static final long FIRST_CHECK_MS = 40_000L;
    /// After a catch, refresh the progress at most this often, a few seconds later.
    private static final long CATCH_REFRESH_GAP_MS = 3L * 60L * 1000L;
    private static final long CATCH_REFRESH_DELAY_MS = 5_000L;
    private static final long HIDE_WINDOW_MS = 3_000L;
    private static final long CAPTURE_MS = 2_000L;

    static final Pattern HEADER = Pattern.compile("^\\s*(.*?\\bMINI EVENT)\\s*[»>]\\s*Ends in\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
    static final Pattern REROLL = Pattern.compile("^\\s*Daily missions?\\s+rerolls?\\s+in\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
    static final Pattern MISSION = Pattern.compile("^\\s*[—–-]\\s*\\S.*$");
    static final Pattern PROGRESS = Pattern.compile("^\\s*└\\s*(.*)$");
    static final Pattern COUNT = Pattern.compile("\\(\\s*([\\d,]+)\\s*/\\s*([\\d,]+)\\s*\\)");
    private static final Pattern SEPARATOR = Pattern.compile("^[\\s\\-=_─━]+$");

    /// One mission: its name line ("— ⛏ Kenai River GIGANTIC") and progress ("(0/2) One Timer").
    public record Mission(Component name, Component progress, long done, long goal) {
        public boolean complete() {
            return goal > 0 && done >= goal;
        }
    }

    private String eventName = "";
    private int eventColor = 0x55FF55;
    private long endsAt = 0L, rerollAt = 0L;
    private List<Mission> missions = new ArrayList<>();

    private boolean capturing = false;
    private long captureUntil = 0L;
    private Component pendingName = null;
    private List<Mission> incoming = null;

    private long joinedAt = 0L, lastCheckAt = 0L, hideUntil = 0L, catchRefreshAt = 0L;

    public boolean active() {
        return endsAt > System.currentTimeMillis();
    }

    public String eventName() {
        return eventName;
    }

    public int eventColor() {
        return eventColor;
    }

    public long endsInMs() {
        return Math.max(0L, endsAt - System.currentTimeMillis());
    }

    public long rerollInMs() {
        return Math.max(0L, rerollAt - System.currentTimeMillis());
    }

    public List<Mission> missions() {
        return missions;
    }

    /// From chat: true when the message is part of our own quiet /minievent answer (hidden).
    public boolean onGameMessage(Component message) {
        long now = System.currentTimeMillis();
        boolean ours = now < hideUntil;
        boolean handled = false;
        for (MutableComponent line : splitLines(message)) {
            if (onLine(line, now, ours)) handled = true;
        }
        return ours && handled;
    }

    /// One line of chat; true when it belonged to a /minievent answer. Package-private for tests.
    boolean onLine(MutableComponent line, long now, boolean ours) {
        String text = ChatFormatting.stripFormatting(line.getString());
        if (text == null) return false;
        if (capturing && now > captureUntil) finishCapture();

        Matcher m = HEADER.matcher(text);
        if (m.matches()) {
            long ms = EventTimesHandler.parseDuration(m.group(2));
            eventName = m.group(1).trim();
            eventColor = firstColor(line, 0x55FF55);
            if (ms >= 0) endsAt = now + ms;
            capturing = true;
            captureUntil = now + CAPTURE_MS;
            pendingName = null;
            incoming = new ArrayList<>();
            return true;
        }
        if (!capturing) {
            // Ours but not an event answer ("There is no mini event right now"): none is on.
            if (ours && text.toLowerCase(java.util.Locale.ROOT).contains("mini event")) {
                endsAt = 0L;
                missions = new ArrayList<>();
                return true;
            }
            return false;
        }
        captureUntil = now + CAPTURE_MS;
        m = REROLL.matcher(text);
        if (m.matches()) {
            long ms = EventTimesHandler.parseDuration(m.group(1));
            if (ms >= 0) rerollAt = now + ms;
            return true;
        }
        if (text.isBlank() || SEPARATOR.matcher(text).matches()) return true;
        m = PROGRESS.matcher(text);
        if (m.matches()) {
            if (pendingName != null) {
                Component progress = dropLeading(line, text.indexOf('└') + 1);
                long done = -1, goal = -1;
                Matcher c = COUNT.matcher(text);
                if (c.find()) {
                    done = Long.parseLong(c.group(1).replace(",", ""));
                    goal = Long.parseLong(c.group(2).replace(",", ""));
                }
                incoming.add(new Mission(pendingName, progress, done, goal));
                missions = new ArrayList<>(incoming);
                pendingName = null;
            }
            return true;
        }
        if (MISSION.matcher(text).matches()) {
            pendingName = dropLeading(line, leadingSpaces(text));
            return true;
        }
        finishCapture();
        return false;
    }

    private void finishCapture() {
        capturing = false;
        pendingName = null;
        if (incoming != null) missions = new ArrayList<>(incoming);
        incoming = null;
    }

    /// Quiet /minievent now and then, and a little after catches (only when nothing is open).
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.player == null || mc.getConnection() == null || !TabOverlayHandler.instance().isInInstance()) {
            joinedAt = 0L;
            return;
        }
        if (joinedAt == 0L) joinedAt = now;
        if (!Configs.hudConfig.showMiniEventPanel.get() || !Configs.hudConfig.miniEventAutoRefresh.get()) return;
        boolean due;
        if (lastCheckAt == 0L) due = now - joinedAt >= FIRST_CHECK_MS;
        else if (catchRefreshAt != 0L && now >= catchRefreshAt) due = true;
        else due = now - lastCheckAt >= (active() ? REFRESH_MS : IDLE_REFRESH_MS);
        if (!due || !SilentMenuHandler.instance().isQuietForBackgroundCommand()) return;
        lastCheckAt = now;
        catchRefreshAt = 0L;
        hideUntil = now + HIDE_WINDOW_MS;
        SilentMenuHandler.instance().noteBackgroundCommand("minievent");
        mc.getConnection().sendCommand("minievent");
    }

    /// A catch can move a mission along: check again shortly (at most every few minutes).
    public void onFishCaught() {
        long now = System.currentTimeMillis();
        if (!active() || catchRefreshAt != 0L || now - lastCheckAt < CATCH_REFRESH_GAP_MS) return;
        catchRefreshAt = now + CATCH_REFRESH_DELAY_MS;
    }

    /// The server said to wait: check again after that.
    public void retryIn(long ms) {
        catchRefreshAt = 0L;
        lastCheckAt = System.currentTimeMillis() - (active() ? REFRESH_MS : IDLE_REFRESH_MS) + ms;
    }

    //region Text helpers (package-private for tests)

    /// A message split at its line breaks, each line keeping its colours.
    static List<MutableComponent> splitLines(Component message) {
        List<MutableComponent> out = new ArrayList<>();
        MutableComponent[] current = {Component.empty()};
        message.visit((style, text) -> {
            String[] parts = text.split("\n", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    out.add(current[0]);
                    current[0] = Component.empty();
                }
                if (!parts[i].isEmpty()) current[0].append(Component.literal(parts[i]).setStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        out.add(current[0]);
        return out;
    }

    /// The line without its first {@code count} characters (and the spaces after them).
    static MutableComponent dropLeading(Component line, int count) {
        MutableComponent out = Component.empty();
        int[] skip = {Math.max(0, count)};
        boolean[] trimming = {true};
        line.visit((style, text) -> {
            String t = text;
            if (skip[0] > 0) {
                int n = Math.min(skip[0], t.length());
                t = t.substring(n);
                skip[0] -= n;
            }
            if (skip[0] == 0 && trimming[0]) {
                t = t.stripLeading();
                if (!t.isEmpty()) trimming[0] = false;
            }
            if (!t.isEmpty()) out.append(Component.literal(t).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static int leadingSpaces(String text) {
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        return i;
    }

    private static int firstColor(Component line, int fallback) {
        return line.visit((style, text) -> text.isBlank() || style.getColor() == null
                        ? Optional.<Integer>empty()
                        : Optional.of(style.getColor().getValue() & 0xFFFFFF),
                Style.EMPTY).orElse(fallback);
    }

    /// "3d 6h 55m 2s" like the server writes it (largest unit first, no zero leaders).
    public static String dhms(long ms) {
        long s = Math.max(0L, ms) / 1000L;
        long d = s / 86_400L, h = s / 3_600L % 24L, m = s / 60L % 60L, sec = s % 60L;
        StringBuilder b = new StringBuilder();
        if (d > 0) b.append(d).append("d ");
        if (d > 0 || h > 0) b.append(h).append("h ");
        if (d > 0 || h > 0 || m > 0) b.append(m).append("m ");
        b.append(sec).append('s');
        return b.toString();
    }
    //endregion
}
