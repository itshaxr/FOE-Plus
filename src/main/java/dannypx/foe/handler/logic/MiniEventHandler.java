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
    private static final long CATCH_REFRESH_GAP_MS = 60L * 1000L;
    private static final long CATCH_REFRESH_DELAY_MS = 5_000L;
    private static final long HIDE_WINDOW_MS = 3_000L;
    private static final long CAPTURE_MS = 750L;

    static final Pattern HEADER = Pattern.compile("^\\s*(.*?\\bMINI EVENT)\\s*[»>]\\s*Ends in\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
    static final Pattern REROLL = Pattern.compile("^\\s*Daily missions?\\s+rerolls?\\s+in\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
    /// A progress line: it has the "(0/2)" count (after a └ or similar).
    static final Pattern COUNT = Pattern.compile("\\(\\s*([\\d,]+)\\s*/\\s*([\\d,]+)\\s*\\)");
    private static final Pattern SEPARATOR = Pattern.compile("^[\\s\\-=_─━]+$");

    /// One mission: its name line ("— ⛏ Kenai River GIGANTIC") and progress ("(0/2) One Timer").
    public record Mission(Component name, Component progress, long done, long goal) {
        public boolean complete() {
            return goal > 0 && done >= goal;
        }

        /// A mission that goes on ("(46/100) ∞ x0"): it stays on the panel when done.
        public boolean repeatable() {
            return progress.getString().contains("∞");
        }

        /// The mission as plain words ("Kenai River GIGANTIC"), without its icons.
        public String key() {
            String plain = ChatFormatting.stripFormatting(name.getString());
            if (plain == null) return "";
            return plain.replaceAll("[\\uE000-\\uF8FF]", " ").replaceFirst("^[^\\p{L}]+", "").trim().replaceAll("\\s+", " ");
        }
    }

    /// A finished mission shows its ✔ this long, then leaves the panel (until the daily reroll).
    static final long SHOW_DONE_MS = 5_000L;
    /// When each mission was seen finishing (by key), for {@link #SHOW_DONE_MS}.
    private final java.util.Map<String, Long> completedAt = new java.util.HashMap<>();
    /// The missions as they were when the current answer started, to spot ones that just finished.
    private java.util.Map<String, Boolean> doneBefore = java.util.Map.of();

    private String eventName = "";
    private int eventColor = 0x55FF55;
    private long endsAt = 0L, rerollAt = 0L;
    private List<Mission> missions = new ArrayList<>();

    private boolean capturing = false;
    /// The answer being read is our own quiet check's: all of it is hidden.
    private boolean captureOurs = false;
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
        boolean hide = false;
        for (MutableComponent line : splitLines(message)) {
            boolean partOfOurs = ours || (capturing && captureOurs && now <= captureUntil);
            if (onLine(line, now, ours) && (partOfOurs || (capturing && captureOurs))) hide = true;
        }
        return hide;
    }

    /// One line of chat; true when it belonged to a /minievent answer. Package-private for tests.
    boolean onLine(MutableComponent line, long now, boolean ours) {
        String text = ChatFormatting.stripFormatting(line.getString());
        if (text == null) return false;
        if (capturing && now > captureUntil) finishCapture();
        if (capturing && captureOurs) ours = true;

        Matcher m = HEADER.matcher(text);
        if (m.matches()) {
            long ms = EventTimesHandler.parseDuration(m.group(2));
            eventName = m.group(1).trim();
            eventColor = firstColor(line, 0x55FF55);
            if (ms >= 0) endsAt = now + ms;
            capturing = true;
            captureOurs = ours;
            java.util.Map<String, Boolean> before = new java.util.HashMap<>();
            for (Mission old : missions) before.put(old.key(), old.complete());
            doneBefore = before;
            captureUntil = now + CAPTURE_MS;
            pendingName = null;
            incoming = new ArrayList<>();
            return true;
        }
        if (!capturing) {
            // The blank divider bar the server puts above its answer (spaces with a strike-through).
            if (ours && (text.isBlank() || SEPARATOR.matcher(text).matches())) return true;
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
        Matcher c = COUNT.matcher(text);
        if (c.find()) {
            if (pendingName != null) {
                // "└ (0/2) One Timer" → "(0/2) One Timer" (whatever the corner glyph is).
                Component progress = dropLeading(line, c.start());
                long done = Long.parseLong(c.group(1).replace(",", ""));
                long goal = Long.parseLong(c.group(2).replace(",", ""));
                Mission mission = new Mission(pendingName, progress, done, goal);
                if (mission.complete() && Boolean.FALSE.equals(doneBefore.get(mission.key()))) completedAt.put(mission.key(), now);
                incoming.add(mission);
                missions = new ArrayList<>(incoming);
                pendingName = null;
            }
            return true;
        }
        // Any other line in the answer is a mission's name ("— ⛏ Kenai River GIGANTIC", its
        // first mark being the server's status icon); it only counts once its progress line
        // follows. Hidden only when the answer is ours.
        pendingName = dropLeading(line, leadingSpaces(text));
        return ours;
    }

    private void finishCapture() {
        capturing = false;
        captureOurs = false;
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
        // Right after the daily missions reroll: show the new ones.
        else if (rerollAt > 0L && now >= rerollAt + 10_000L && lastCheckAt < rerollAt) due = true;
        else due = now - lastCheckAt >= (active() ? REFRESH_MS : IDLE_REFRESH_MS);
        if (!due || !SilentMenuHandler.instance().isQuietForBackgroundCommand()) return;
        lastCheckAt = now;
        catchRefreshAt = 0L;
        hideUntil = now + HIDE_WINDOW_MS;
        SilentMenuHandler.instance().noteBackgroundCommand("minievent");
        mc.getConnection().sendCommand("minievent");
    }

    /// A catch can move a mission along: check again shortly (at most every few minutes).
    /// A catch: every unfinished mission it counts for goes up by one straight away (like the
    /// Daily Quests panel), and /minievent is checked again shortly for the exact numbers.
    public void onFishCaught(dannypx.foe.item.FishTagObject fish) {
        try {
            applyCatch(DailyQuestHandler.catchOf(fish), System.currentTimeMillis());
        } catch (RuntimeException e) {
            // the check below still catches up
        }
        onFishCaught();
    }

    /// Package-private for tests.
    void applyCatch(DailyQuestHandler.Catch caught, long now) {
        if (missions.isEmpty()) return;
        List<Mission> next = new ArrayList<>(missions.size());
        boolean changed = false;
        for (Mission m : missions) {
            if (!m.complete() && m.goal() > 0 && countsCatch(m, caught)) {
                Mission up = new Mission(m.name(), replaceFirstNumber(m.progress(), m.done() + 1), m.done() + 1, m.goal());
                if (up.complete()) completedAt.put(up.key(), now);
                next.add(up);
                changed = true;
            } else {
                next.add(m);
            }
        }
        if (changed) missions = next;
    }

    /// Whether a catch counts for a mission ("King Salmon", "Kenai River GIGANTIC", "Kenai
    /// River"), with the Daily Quests matching. A mission naming only a place counts any fish there.
    static boolean countsCatch(Mission m, DailyQuestHandler.Catch caught) {
        String goal = m.key();
        if (goal.isEmpty()) return false;
        if (DailyQuestHandler.locationNamedIn(goal.toLowerCase(java.util.Locale.ROOT)) != null) {
            goal = goal.toLowerCase(java.util.Locale.ROOT).replaceAll("\\b(river|lake|lakes|sea|islands?|bend|rig|keys?)\\b", " ") + " fish";
        }
        return DailyQuestHandler.countsCatch(goal, null, caught);
    }

    /// Whether a mission belongs on the panel: unfinished, repeatable, or finished in the last
    /// few seconds.
    public boolean shouldShow(Mission m) {
        if (!m.complete() || m.repeatable()) return true;
        Long at = completedAt.get(m.key());
        return at != null && System.currentTimeMillis() - at < SHOW_DONE_MS;
    }

    /// "(46/100) ∞ x0" with its first number changed, keeping the colours.
    static MutableComponent replaceFirstNumber(Component progress, long value) {
        MutableComponent out = Component.empty();
        boolean[] done = {false};
        progress.visit((style, text) -> {
            String t = text;
            if (!done[0]) {
                Matcher n = Pattern.compile("[\\d,]+").matcher(t);
                while (n.find()) {
                    if (n.group().chars().anyMatch(Character::isDigit)) {
                        t = t.substring(0, n.start()) + value + t.substring(n.end());
                        done[0] = true;
                        break;
                    }
                }
            }
            out.append(Component.literal(t).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    public void onFishCaught() {
        long now = System.currentTimeMillis();
        if (!active() || catchRefreshAt != 0L) return;
        // A few seconds after the catch, or (when we checked under a minute ago) as soon as the
        // minute is up: a catch is never dropped, so a finished mission shows within about a minute.
        catchRefreshAt = Math.max(now + CATCH_REFRESH_DELAY_MS, lastCheckAt + CATCH_REFRESH_GAP_MS);
    }

    /// Tests: when the next check after a catch is due (0 = none).
    long catchRefreshAtForTest() {
        return catchRefreshAt;
    }

    /// Tests: as if an event is on and we last checked at {@code at}.
    void setForTest(long endsAt, long lastCheckAt) {
        this.endsAt = endsAt;
        this.lastCheckAt = lastCheckAt;
    }

    /// Tests: act as if our quiet check was just sent.
    void hideForTest(long now) {
        hideUntil = now + HIDE_WINDOW_MS;
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
