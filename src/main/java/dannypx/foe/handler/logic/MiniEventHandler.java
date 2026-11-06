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

        /// The mission without its leading dash and icon ("Kenai River GIGANTIC"). Badges in the
        /// name (ALBINO, TROPHY... are server glyphs) are kept: they're part of the mission.
        public String key() {
            String plain = ChatFormatting.stripFormatting(name.getString());
            if (plain == null) return "";
            return plain.replaceFirst("^[^\\p{L}]+", "").trim().replaceAll("\\s+", " ");
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
        boolean special = false;
        try {
            DailyQuestHandler.Catch caught = DailyQuestHandler.catchOf(fish);
            applyCatch(caught, System.currentTimeMillis());
            // A gigantic or a variant (the catches FOE+ alerts about) can move the missions only
            // the server counts: check again in a few seconds, not after the usual minute.
            special = "gigantic".equals(caught.size())
                    || (!caught.variant().isEmpty() && !"normal".equals(caught.variant()));
        } catch (RuntimeException e) {
            // the check below still catches up
        }
        if (special && active()) catchRefreshAt = System.currentTimeMillis() + CATCH_REFRESH_DELAY_MS;
        else onFishCaught();
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

    /// Whether a catch counts for a mission. Everything the mission names has to match the
    /// catch: the place ("Kenai River"), the size ("GIGANTIC", written in small capitals by the
    /// server), the variant ("ALBINO", a badge glyph), the rarity and the fish's name. These are
    /// the same details the FOE+ catch alerts go by, so a GIGANTIC mission only goes up for a
    /// gigantic. Pet missions, and anything FOE+ can't read, are left to the server's numbers.
    /// Package-private for tests.
    static boolean countsCatch(Mission m, DailyQuestHandler.Catch c) {
        String text = normalize(m.key());
        if (text == null || text.isBlank()) return false;
        if (hasWord(text, "pet") || hasWord(text, "pets")) return false;

        dannypx.foe.helper.FoeLocations.Location place = null;
        for (dannypx.foe.helper.FoeLocations.Location l : dannypx.foe.helper.FoeLocations.Location.values()) {
            String name = dannypx.foe.helper.FoeLocations.displayName(l);
            if (name == null) continue;
            String n = name.toLowerCase(java.util.Locale.ROOT);
            if (hasWord(text, n)) {
                place = l;
                text = text.replace(n, " ");
                break;
            }
        }
        String size = null, variant = null, rarity = null;
        for (String w : SIZES) if (hasWord(text, w)) { size = w; text = removeWord(text, w); }
        for (String w : VARIANTS) if (hasWord(text, w)) { variant = w; text = removeWord(text, w); }
        for (String w : RARITIES) if (hasWord(text, w)) { rarity = w.equals("mythic") ? "mythical" : w; text = removeWord(text, w); }
        String fish = text.replaceAll("[^a-z' ]", " ").trim().replaceAll("\\s+", " ");
        // "Catch 50 fish" / "any fish": any fish (the other details still have to match).
        fish = fish.replaceAll("(?<![a-z])(catch|catches|any|fish|fishes)(?![a-z])", " ").trim().replaceAll("\\s+", " ");

        if (place == null && size == null && variant == null && rarity == null && fish.isEmpty()) return false;
        if (place != null && place != c.here()) return false;
        if (size != null && !size.equals(lower(c.size()))) return false;
        if (variant != null && !variant.equals(lower(c.variant()))) return false;
        if (rarity != null && !rarity.equals(lower(c.rarity()))) return false;
        if (!fish.isEmpty()) {
            String caught = normalize(c.fishName() == null ? "" : c.fishName());
            if (caught == null || !hasWord(caught.replaceAll("[^a-z' ]", " ").replaceAll("\\s+", " "), fish)) return false;
        }
        return true;
    }

    private static final List<String> SIZES = List.of("baby", "juvenile", "adult", "large", "gigantic");
    private static final List<String> VARIANTS = List.of("albino", "melanistic", "trophy", "fabled");
    private static final List<String> RARITIES = List.of("common", "rare", "epic", "legendary", "mythical", "mythic");
    /// The server's badge glyphs, as words.
    private static final java.util.Map<Character, String> BADGES = java.util.Map.ofEntries(
            java.util.Map.entry('\uF033', "common"), java.util.Map.entry('\uF034', "rare"),
            java.util.Map.entry('\uF035', "epic"), java.util.Map.entry('\uF036', "legendary"),
            java.util.Map.entry('\uF037', "mythical"), java.util.Map.entry('\uF040', " "),
            java.util.Map.entry('\uF041', "albino"), java.util.Map.entry('\uF042', "melanistic"),
            java.util.Map.entry('\uF043', "trophy"), java.util.Map.entry('\uF044', "fabled"));
    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫꞯʀѕꜱᴛᴜᴠᴡʏᴢ";
    private static final String SMALL_CAPS_AS = "abcdefghijklmnopqqrsstuvwyz";

    /// Lower-case plain words: formatting removed, small capitals ("ɢɪɢᴀɴᴛɪᴄ") as letters, the
    /// server's badge glyphs as their words. Null when there's a glyph FOE+ doesn't know.
    static String normalize(String text) {
        String plain = ChatFormatting.stripFormatting(text);
        if (plain == null) return null;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < plain.length(); i++) {
            char ch = plain.charAt(i);
            int sc = SMALL_CAPS.indexOf(ch);
            if (sc >= 0) b.append(SMALL_CAPS_AS.charAt(sc));
            else if (ch >= '\uE000' && ch <= '\uF8FF') {
                String word = BADGES.get(ch);
                if (word == null) return null;
                b.append(' ').append(word).append(' ');
            } else b.append(Character.toLowerCase(ch));
        }
        return b.toString().replaceAll("\\s+", " ").trim();
    }

    private static boolean hasWord(String text, String word) {
        return Pattern.compile("(?<![a-z])" + Pattern.quote(word) + "(?![a-z])").matcher(text).find();
    }

    private static String removeWord(String text, String word) {
        return text.replaceAll("(?<![a-z])" + Pattern.quote(word) + "(?![a-z])", " ");
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT).trim();
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
