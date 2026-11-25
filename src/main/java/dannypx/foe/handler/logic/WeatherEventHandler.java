package dannypx.foe.handler.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Rain showers, thunderstorms and supercells, for the Events panel.
 *
 * <p>They start from chat ("STORM » A thunderstorm has formed!", "RAIN » A rain shower has
 * begun!") or, if that was missed (you joined in the middle of one), from the icon on the location
 * bar (⚡ for a storm, ☂ for rain). They end when another line from the same source says so, or
 * when the icon has been gone from the bar for a few seconds. The weather is the same at every
 * location, so there's only ever one storm (or one rain shower), wherever you are.
 *
 * <p>How long each kind lasts is learned from ones seen from start to end, and saved; with that
 * the panel counts down ("4:12 left"), until then it counts up ("up 3:12").
 */
public final class WeatherEventHandler {
    public enum Kind {
        // how long each lasts, from the wiki (wiki.fishonmc.net, Fishing Events)
        RAIN("Rain Shower", "☂", 0x63B6EC, "+50 Bite Speed", "☂", "RAIN »", "rain shower has begun", 450, 600),
        THUNDERSTORM("Thunderstorm", "⚡", 0xFFFF55, "+100 Bite Speed", "⚡", "STORM »", "thunderstorm has formed", 300, 680),
        SUPERCELL("Supercell", "⚡", 0xFCE211, "+150 Bite Speed", "⚡", "STORM »", "supercell has formed", 250, 540);

        public final String displayName, icon, description;
        public final int color;
        /// The icon on the location bar while it's on; the chat line's prefix and how a start reads.
        final String barIcon, chatPrefix, startWords;
        /// The shortest and longest it lasts (wiki), in ms.
        public final long minMs, maxMs;

        Kind(String displayName, String icon, int color, String description, String barIcon, String chatPrefix, String startWords,
             int minSeconds, int maxSeconds) {
            this.minMs = minSeconds * 1000L;
            this.maxMs = maxSeconds * 1000L;
            this.displayName = displayName;
            this.icon = icon;
            this.color = color;
            this.description = description;
            this.barIcon = barIcon;
            this.chatPrefix = chatPrefix;
            this.startWords = startWords;
        }
    }

    /// One going on: since when, and whether that's its real start (from chat).
    public static final class Active {
        public final Kind kind;
        public final long startedAt;
        public final boolean exactStart;
        long goneAt = 0L;
        /// Named by chat (not only guessed from the bar's icon).
        boolean fromChat = false;
        /// Where its icon was last seen on the bar ("" = not seen yet).
        String barPlace = "";

        Active(Kind kind, long startedAt, boolean exactStart) {
            this.kind = kind;
            this.startedAt = startedAt;
            this.exactStart = exactStart;
        }
    }

    /// Gone from the bar this long means it's over (a teleport's blink is shorter).
    static final long GONE_MS = 5_000L;
    /// Lengths outside this are a misread, not learned.
    static final long MIN_LEARN_MS = 30_000L, MAX_LEARN_MS = 3 * 60 * 60_000L;

    private static final WeatherEventHandler INSTANCE = new WeatherEventHandler();
    public static WeatherEventHandler instance() { return INSTANCE; }

    /// Where you are now, as the location bar names it ("" before it's read).
    public static String here() {
        try {
            return dannypx.foe.handler.fetch.BossEventHandler.instance().getLocation().getString().replaceAll("[\\uE000-\\uF8FF]", "").trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private final List<Active> active = new ArrayList<>();
    final Map<Kind, Long> learned = new EnumMap<>(Kind.class);
    private boolean loaded = false;
    /// Where learned lengths are kept; swappable for tests (null: not saved).
    static java.util.function.Supplier<Path> FILE = () -> net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve(dannypx.foe.FishOnMCExtras.MOD_ID).resolve("weather_events.json");
    static Consumer<String> LOG = s -> {};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    //region Reading
    /// Everything going on, newest first.
    public synchronized List<Active> active() {
        load();
        List<Active> out = new ArrayList<>(active);
        out.sort((a, b) -> Long.compare(b.startedAt, a.startedAt));
        return out;
    }

    /// Time left at most (it lasts no longer than the wiki's longest), when its start was seen; otherwise -1.
    public synchronized long msLeft(Active a, long now) {
        if (!a.exactStart) return -1L;
        return Math.max(0L, a.startedAt + a.kind.maxMs - now);
    }
    //endregion

    //region Chat
    /// "STORM » A Thunderstorm has formed! (+100 Bite Speed)", "STORM » A Supercell has formed!", "STORM » A Thunderstorm
    /// is ongoing (+100 Bite Speed)" (when you join), "STORM » The Supercell has dissipated!", "RAIN » A Rain Shower has
    /// begun!". Rain, thunderstorms and supercells are each their own event with their own timer.
    public synchronized void onChat(String text, long now) {
        if (text == null) return;
        String t = text.strip().replaceAll("^[^A-Za-z0-9]+", "");
        String upper = t.toUpperCase(java.util.Locale.ROOT);
        boolean storm = upper.startsWith("STORM »") || upper.startsWith("STORM >"),
                rain = upper.startsWith("RAIN »") || upper.startsWith("RAIN >");
        if (!storm && !rain) return;
        String lower = t.toLowerCase(java.util.Locale.ROOT);
        Kind named = lower.contains("supercell") ? Kind.SUPERCELL : lower.contains("thunderstorm") ? Kind.THUNDERSTORM
                : lower.contains("rain") && !storm ? Kind.RAIN : null;
        boolean ended = ENDED.matcher(lower).find();
        if (!ended) {
            boolean ongoing = lower.contains("ongoing") || lower.contains("is active");
            if (ongoing || STARTED.matcher(lower).find()) {
                Kind kind = named != null ? named : storm ? Kind.THUNDERSTORM : Kind.RAIN;
                start(kind, now, !ongoing);
            }
            return;
        }
        // over: the kind it names, or (a line naming none) every one from that source
        for (Active a : new ArrayList<>(active)) {
            boolean match = named != null ? a.kind == named : rain ? a.kind == Kind.RAIN : a.kind != Kind.RAIN;
            if (match) end(a, now, true);
        }
    }

    static final java.util.regex.Pattern STARTED = java.util.regex.Pattern.compile("\\b(formed|begun|began|started|arrived|rolled in|appeared)\\b");
    static final java.util.regex.Pattern ENDED = java.util.regex.Pattern.compile("\\b(dissipated|passed|ended|faded|stopped|cleared|over|died down)\\b");

    /// One of each kind at a time; an "is ongoing" line (you joined in the middle) never moves a start already seen.
    private void start(Kind kind, long now, boolean exact) {
        load();
        Active same = null;
        for (Active a : active) if (a.kind == kind) same = a;
        if (same != null && (!exact || same.exactStart && now - same.startedAt < 5_000L)) {
            same.goneAt = 0L;
            return;
        }
        active.removeIf(a -> a.kind == kind);
        // the bar's guess (it can't tell a thunderstorm from a supercell) gives way to what chat names
        if (kind != Kind.RAIN) active.removeIf(a -> a.kind != Kind.RAIN && !a.fromChat);
        Active a = new Active(kind, now, exact);
        a.fromChat = true;
        active.add(a);
    }
    //endregion

    //region Every tick
    /// `icons`: the weather icons on the location bar; `place`: where you are ("" while it's not
    /// read yet, mid-teleport: nothing is decided then).
    public synchronized void tick(String icons, String place, long now) {
        load();
        if (place == null || place.isEmpty()) return;
        String bar = icons == null ? "" : icons;
        boolean stormIcon = bar.contains(Kind.THUNDERSTORM.barIcon), rainIcon = bar.contains(Kind.RAIN.barIcon);
        boolean anyIcon = stormIcon || rainIcon;
        // seen on the bar without its start (joined in the middle), and nothing from chat: counted from now. Chat
        // names the kind; the bar's icon is only a guess (a storm can show the rain's icon too), so it never adds
        // rain next to a storm chat told us about.
        boolean stormKnown = active.stream().anyMatch(a -> a.kind != Kind.RAIN);
        if (stormIcon && !stormKnown) active.add(new Active(Kind.THUNDERSTORM, now, false));
        if (rainIcon && !stormIcon && !stormKnown && active.stream().noneMatch(a -> a.kind == Kind.RAIN))
            active.add(new Active(Kind.RAIN, now, false));
        for (Active a : new ArrayList<>(active)) {
            // one named by chat lasts while any weather icon shows (chat says when it ends); a guess, while its own does
            boolean showing = a.fromChat ? anyIcon : a.kind == Kind.RAIN ? rainIcon : stormIcon;
            if (showing) {
                a.goneAt = 0L;
                a.barPlace = place;
            } else if (bar.isBlank() || !a.barPlace.isEmpty() && !a.barPlace.equalsIgnoreCase(place)) {
                // a bar with no weather on it (the hub), or another place than where it was seen: not a sign it's
                // over; chat says when it ends
                a.goneAt = 0L;
            } else if (a.goneAt == 0L) a.goneAt = now;
            else if (now - a.goneAt >= GONE_MS) { end(a, a.goneAt, true); continue; }
            // never runs on past its longest (wiki), whatever the bar says (one already going when you came: from then)
            if (now - a.startedAt > a.kind.maxMs + 15_000L) end(a, now, false);
        }
    }

    private void end(Active a, long endedAt, boolean sawItEnd) {
        active.remove(a);
        if (!sawItEnd || !a.exactStart) return;
        long lasted = endedAt - a.startedAt;
        if (lasted < MIN_LEARN_MS || lasted > MAX_LEARN_MS) return;
        Long old = learned.get(a.kind);
        // the longest seen: one cut short (you teleported in late) never shortens it
        if (old == null || lasted > old) {
            learned.put(a.kind, lasted);
            save();
            LOG.accept("[Weather] " + a.kind + " lasted " + lasted / 1000 + "s");
        }
    }
    //endregion

    //region Saving
    private static final class Saved { Map<String, Long> lastedMs = new java.util.HashMap<>(); }

    private void load() {
        if (loaded) return;
        loaded = true;
        Path file;
        try { file = FILE.get(); } catch (Throwable t) { return; }
        if (file == null || !Files.exists(file)) return;
        try {
            Saved s = GSON.fromJson(Files.readString(file), Saved.class);
            if (s == null || s.lastedMs == null) return;
            for (Kind k : Kind.values()) {
                Long v = s.lastedMs.get(k.name());
                if (v != null && v >= MIN_LEARN_MS && v <= MAX_LEARN_MS) learned.put(k, v);
            }
        } catch (Exception e) {
            LOG.accept("[Weather] couldn't read " + file + ": " + e);
        }
    }

    private void save() {
        Path file;
        try { file = FILE.get(); } catch (Throwable t) { return; }
        if (file == null) return;
        try {
            Saved s = new Saved();
            learned.forEach((k, v) -> s.lastedMs.put(k.name(), v));
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(s));
        } catch (Exception e) {
            LOG.accept("[Weather] couldn't save " + file + ": " + e);
        }
    }

    /// For tests: forget everything.
    synchronized void reset() {
        active.clear();
        learned.clear();
        loaded = true;
    }
    //endregion
}
