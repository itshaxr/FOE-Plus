package dannypx.foe.handler.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.BossEventHandler;
import dannypx.foe.helper.FoeLocations;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Event alerts for the Alerts panel: FOE's Witching Hour, and the cryptids with the window you
 * can catch them in and how long it has left.
 * <ul>
 *   <li>Clock windows use the in-game time on the boss bar. How many real seconds an in-game
 *       minute takes is measured while you play (vanilla-speed until then), so countdowns are
 *       shown as approximate.</li>
 *   <li>Moons come from chat (risen / has set); how long a moon lasts is learned from the first
 *       one you see set, and remembered.</li>
 *   <li>Rainbows come from chat: one starts with "has appeared" and ends with "has faded" (or,
 *       failing that, when the weather on the boss bar changes); its length is learned the same way.</li>
 * </ul>
 */
public class AlertHandler {
    private static AlertHandler INSTANCE = new AlertHandler();

    public enum Kind { CLOCK, MOON, RAINBOW, FABLED }

    public enum Alert {
        WITCHING_HOUR("Witching Hour", 0xD23B3B, FoeLocations.Location.CYPRESS_LAKE, Kind.CLOCK, 1, 3,
                "Bigfoot 1/300", "steals a fish, leaves a gift"),
        TITANOBOA("Titanoboa", 0x3FA34D, FoeLocations.Location.AMAZON_RIVER, Kind.CLOCK, 2, 4,
                "Titan's Fang 1/1500", "8 + Fabled Shard: Titan's Graceful Necklace"),
        BESSIE_NIGHT("Bessie", 0x7FB8FF, FoeLocations.Location.GREAT_LAKES, Kind.CLOCK, 4, 6,
                "Silver Scale 1/1000", "7 + 2 Fabled Shards: Cryptid Amulet"),
        BESSIE_MOON("Bessie", 0x7FB8FF, FoeLocations.Location.GREAT_LAKES, Kind.MOON, 0, 0,
                "Silver Scale 1/500", "7 + 2 Fabled Shards: Cryptid Amulet"),
        ROUGAROU("Rougarou", 0xB34A2E, FoeLocations.Location.TOLEDO_BEND, Kind.MOON, 0, 0,
                "1/300: Blood or Claw (50/50)", "8 Claws + Fabled Shard: Moon Amulet"),
        GARABONCIAS("Garaboncias", 0xC77DFF, FoeLocations.Location.DANUBE_RIVER, Kind.RAINBOW, 0, 0,
                "1/250: Concoction or Essence (50/50)", "4 + Infusion Capsule: Wizard's Infusion"),
        /// Location comes from the event's chat message.
        FABLED("Fabled Fish", 0xFC3E35, null, Kind.FABLED, 0, 0,
                "Fabled 1/3000 (better with Variant Chance)", "Fabled Shard · 30x value");

        public final String displayName;
        public final int color;
        public final FoeLocations.Location location;
        public final Kind kind;
        public final int startHour;
        public final int endHour;
        public final String odds;
        public final String drop;

        Alert(String displayName, int color, FoeLocations.Location location, Kind kind, int startHour, int endHour, String odds, String drop) {
            this.displayName = displayName;
            this.color = color;
            this.location = location;
            this.kind = kind;
            this.startHour = startHour;
            this.endHour = endHour;
            this.odds = odds;
            this.drop = drop;
        }
    }

    /// One alert as the panel shows it. {@code msLeft} is -1 when the end isn't known yet;
    /// {@code msSince} is how long it has been going (-1 when unknown or not shown).
    public record Active(Alert alert, long msLeft, long msSince) {
        public Active(Alert alert, long msLeft) {
            this(alert, msLeft, -1L);
        }
    }

    public record Upcoming(String what, long msUntil) {}

    private final java.util.Set<String> shownUpcoming = new java.util.HashSet<>();
    private final java.util.Set<String> nowUpcoming = new java.util.HashSet<>();

    //region Learned timings (saved)
    private static final long DEFAULT_MS_PER_GAME_MINUTE = 50_000L / 60L; // vanilla: an in-game hour is 50 s
    static final class Learned {
        double msPerGameMinute = DEFAULT_MS_PER_GAME_MINUTE;
        long moonDurationMs = 0L;
        long rainbowDurationMs = 0L;
        // The most recent Fabled event (0 = not seen yet; start 0 with an end = start not seen)
        long lastFabledStart = 0L;
        long lastFabledEnd = 0L;
        String lastFabledLocation = "";
        String lastFabledCatcher = "";
    }

    /// The last Fabled event that was caught, or null if none has been seen.
    public record FabledRecord(long startedAt, long endedAt, String location, String catcher) {}

    public FabledRecord lastFabled() {
        Learned l = learned();
        return l.lastFabledEnd == 0L ? null : new FabledRecord(l.lastFabledStart, l.lastFabledEnd, l.lastFabledLocation, l.lastFabledCatcher);
    }

    private Learned learned = null;

    private Learned learned() {
        if (learned == null) {
            learned = new Learned();
            try {
                Path file = learnedFile();
                if (Files.exists(file)) {
                    Learned read = new Gson().fromJson(Files.readString(file), Learned.class);
                    if (read != null) learned = read;
                }
            } catch (Exception | LinkageError e) {
                LoggerHandler.error(e instanceof Exception ex ? ex : new RuntimeException(e));
            }
            if (learned.msPerGameMinute < 100 || learned.msPerGameMinute > 60_000) learned.msPerGameMinute = DEFAULT_MS_PER_GAME_MINUTE;
        }
        return learned;
    }

    private void saveLearned() {
        try {
            Path file = learnedFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(learned()));
        } catch (Exception | LinkageError e) {
            LoggerHandler.error(e instanceof Exception ex ? ex : new RuntimeException(e));
        }
    }

    /// Where learned timings are saved (swappable in tests).
    java.util.function.Supplier<Path> learnedLocation =
            () -> FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("alert_timings.json");

    private Path learnedFile() {
        return learnedLocation.get();
    }
    //endregion

    //region State
    private int lastGameMinute = -1;
    private long lastGameMinuteChangedAt = 0L;
    private boolean clockSampleReady = false;
    private long lastClockSaveAt = 0L;

    private long rainbowStartedAt = 0L;
    private FoeLocations.Location rainbowLocation = null;
    private String rainbowWeather = null;

    private final Set<Alert> announced = EnumSet.noneOf(Alert.class);
    //endregion

    public static AlertHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new AlertHandler();
        }
        return INSTANCE;
    }

    //region Clock
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])?");

    /// "2:30 AM" → 150, "14:05" → 845, or -1 when there's no time. Package-private for tests.
    static int parseGameMinutes(String text) {
        if (text == null) return -1;
        Matcher m = TIME.matcher(text);
        if (!m.find()) return -1;
        int hour = Integer.parseInt(m.group(1));
        int minute = Integer.parseInt(m.group(2));
        if (minute > 59 || hour > 23) return -1;
        String suffix = m.group(3);
        if (suffix != null) {
            if (hour < 1 || hour > 12) return -1;
            hour = hour % 12;
            if (suffix.equalsIgnoreCase("PM")) hour += 12;
        }
        return hour * 60 + minute;
    }

    /// In-game minutes left in [start, end) hours if {@code now} is inside it, else -(minutes until
    /// it starts). Handles windows that wrap past midnight. Package-private for tests.
    static int windowMinutes(int now, int startHour, int endHour) {
        int start = startHour * 60;
        int end = endHour * 60;
        int length = Math.floorMod(end - start, 1440);
        int sinceStart = Math.floorMod(now - start, 1440);
        if (sinceStart < length) return length - sinceStart;
        return -Math.floorMod(start - now, 1440);
    }

    public int gameMinute() {
        if (testMinute != null) return testMinute.getAsInt(); // tests drive the clock directly
        return filterMinute(parseGameMinutes(BossEventHandler.instance().getTime().getString()), now());
    }

    /// The boss bar can show a wrong time for a moment (a glitched update read as midnight made the
    /// Witching Hour pop up at noon). A reading that jumps more than a few in-game minutes from the
    /// trusted one is only believed once it has held, still moving normally, for a few seconds.
    static final long JUMP_CONFIRM_MS = 3_000L;
    private static final int NORMAL_STEP_MINUTES = 30;
    private int trustedMinute = -1;
    private int jumpMinute = -1;
    private long jumpSince = 0L;

    int filterMinute(int raw, long now) {
        if (raw < 0) return trustedMinute;
        if (trustedMinute < 0 || isNormalStep(trustedMinute, raw)) {
            trustedMinute = raw;
            jumpMinute = -1;
            return raw;
        }
        // A jump: keep the trusted time until the new one has held for a while.
        if (jumpMinute < 0 || !isNormalStep(jumpMinute, raw)) {
            jumpMinute = raw;
            jumpSince = now;
        } else {
            jumpMinute = raw;
            if (now - jumpSince >= JUMP_CONFIRM_MS) {
                trustedMinute = raw;
                jumpMinute = -1;
                return raw;
            }
        }
        return trustedMinute;
    }

    /// Same minute or a little later (the clock moves forward a few minutes at a time).
    private static boolean isNormalStep(int from, int to) {
        return Math.floorMod(to - from, 1440) <= NORMAL_STEP_MINUTES;
    }

    /// Clock and boss bar time, swappable in tests.
    java.util.function.LongSupplier clock = System::currentTimeMillis;
    java.util.function.IntSupplier testMinute = null;

    private long now() {
        return clock.getAsLong();
    }

    /// Test hook: the countdown shown for a window that ends at {@code endHour} (whole hours).
    long countdownForTest(String key, int endHour) {
        int left = Math.floorMod(endHour * 60 - gameMinute(), 1440);
        return smooth(key, gameMinutesToMs(left));
    }

    /// Test hook: one update of the clock tracking.
    void tickClockForTest() {
        tickClock(now());
        dropUnusedCountdowns();
    }

    /// The boss bar clock moves in steps of a few in-game minutes every couple of seconds, so the
    /// speed is measured over a long stretch (all steps since joining, reset on any jump) instead
    /// of step by step, which made it wobble.
    private long baselineRealAt = 0L;
    private int baselineMinutes = 0;

    private void tickClock(long now) {
        int minute = gameMinute();
        if (minute < 0) return;
        if (minute == lastGameMinute) return;
        if (lastGameMinute >= 0) {
            int delta = Math.floorMod(minute - lastGameMinute, 1440);
            long realDelta = now - lastGameMinuteChangedAt;
            if (clockSampleReady && delta > 0 && delta <= 30 && realDelta < 60_000L) {
                if (baselineRealAt == 0L) {
                    baselineRealAt = lastGameMinuteChangedAt;
                    baselineMinutes = 0;
                }
                baselineMinutes += delta;
                long span = now - baselineRealAt;
                if (span >= 30_000L && baselineMinutes >= 20) {
                    learned().msPerGameMinute = (double) span / baselineMinutes;
                    if (now - lastClockSaveAt > 5 * 60_000L) {
                        lastClockSaveAt = now;
                        saveLearned();
                    }
                }
            } else {
                // Time skipped, the world changed or we just joined: start measuring again.
                baselineRealAt = 0L;
            }
            clockSampleReady = true;
        }
        lastGameMinute = minute;
        lastGameMinuteChangedAt = now;
    }

    /// Real time until {@code minutes} in-game minutes after the minute now on the boss bar.
    /// Time since the bar last changed is taken off, so it counts down smoothly between steps.
    private long gameMinutesToMs(int minutes) {
        long sinceStep = lastGameMinuteChangedAt == 0L ? 0L : now() - lastGameMinuteChangedAt;
        return Math.max(0L, Math.round(minutes * learned().msPerGameMinute) - sinceStep);
    }

    //region Smooth countdowns
    /// Each countdown keeps the moment it ends. A better estimate (the clock speed gets measured
    /// as you play) moves that moment gently, at most a quarter second per second, so the timer
    /// always ticks down smoothly and never jumps back up. Only a big change (a new window) snaps.
    private static final long SNAP_MS = 60_000L;
    private static final double MAX_DRIFT = 0.25;
    private final java.util.Map<String, long[]> endsAt = new java.util.HashMap<>(); // {endAt, lastUpdate}
    private final java.util.Set<String> seenThisFrame = new java.util.HashSet<>();

    private long smooth(String key, long msFromNow) {
        long now = now();
        long target = now + msFromNow;
        long[] entry = endsAt.get(key);
        if (entry == null || Math.abs(entry[0] - target) > SNAP_MS) {
            entry = new long[]{target, now};
            endsAt.put(key, entry);
        } else {
            long maxStep = Math.round((now - entry[1]) * MAX_DRIFT);
            long diff = target - entry[0];
            entry[0] += Math.max(-maxStep, Math.min(maxStep, diff));
            entry[1] = now;
        }
        seenThisFrame.add(key);
        return Math.max(0L, entry[0] - now);
    }

    /// Forgets countdowns that weren't asked for in the last update.
    private void dropUnusedCountdowns() {
        endsAt.keySet().retainAll(seenThisFrame);
        seenThisFrame.clear();
    }
    //endregion
    //endregion

    //region Moons and rainbows
    public boolean isMoonUp() {
        return MoonTimerHandler.instance().getCurrentMoon() != null;
    }

    private long moonElapsedMs() {
        return MoonTimerHandler.instance().getTimeSinceMoonrise();
    }

    /// Called when chat says the moon has set: remembers how long moons last.
    public void onMoonSet() {
        long elapsed = moonElapsedMs();
        if (elapsed > 30_000L && elapsed < MoonTimerHandler.instance().getCycleMs()) {
            learned().moonDurationMs = elapsed;
            saveLearned();
        }
    }

    //region Fabled events
    private static final Pattern FABLED_VISIT = Pattern.compile("^Visit the (.+?) to\\b.*$");
    private static final Pattern FABLED_ACTIVE = Pattern.compile("^FABLED » A Fabled Event is active at (.+?)\\.?$");
    private static final Pattern FABLED_CAUGHT = Pattern.compile("^Caught by (\\S+).*$");
    /// A Fabled event nobody seems to catch is dropped after this (we missed the end).
    private static final long FABLED_MAX_MS = 2L * 60L * 60L * 1000L;

    private boolean fabledActive = false;
    private long fabledStartedAt = 0L;
    private String fabledLocationName = "";

    public boolean isFabledActive() {
        return fabledActive;
    }

    public String fabledLocationName() {
        return fabledLocationName;
    }

    /// Reads the Fabled event messages. Package-private parts are kept static for tests.
    void onFabledMessage(String text, long now) {
        Matcher visit = FABLED_VISIT.matcher(text);
        Matcher active = FABLED_ACTIVE.matcher(text);
        if (visit.matches() || active.matches()) {
            String location = (visit.matches() ? visit.group(1) : active.group(1)).trim();
            if (!fabledActive) {
                fabledActive = true;
                // "Visit the X to ..." is the announcement itself; "is active at" is the reminder
                // you get when you join while one is going (start time unknown then).
                fabledStartedAt = visit.matches() ? now : 0L;
                fabledLocationName = location;
            } else if (fabledLocationName.isEmpty()) {
                fabledLocationName = location;
            }
            return;
        }
        Matcher caught = FABLED_CAUGHT.matcher(text);
        if (fabledActive && caught.matches()) {
            String catcher = caught.group(1);
            Learned l = learned();
            l.lastFabledStart = fabledStartedAt;
            l.lastFabledEnd = now;
            l.lastFabledLocation = fabledLocationName;
            l.lastFabledCatcher = catcher;
            saveLearned();
            fabledActive = false;
            announceFabledCaught(new FabledRecord(fabledStartedAt, now, fabledLocationName, catcher));
        }
    }

    private void announceFabledCaught(FabledRecord record) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || !Configs.hudConfig.alertChat.get() || !Configs.hudConfig.alertFabled.get()) return;
        // [FOE+] FABLED CAUGHT Danube River · by Steve
        net.minecraft.network.chat.MutableComponent line = dannypx.foe.helper.FoeChat.line("FABLED CAUGHT", Alert.FABLED.color)
                .append(dannypx.foe.helper.FoeChat.place(record.location().isEmpty() ? "Unknown location" : record.location()))
                .append(dannypx.foe.helper.FoeChat.sep())
                .append(dannypx.foe.helper.FoeChat.text("by "))
                .append(dannypx.foe.helper.FoeChat.white(record.catcher()));
        dannypx.foe.helper.FoeChat.send(line);
    }

    /// "2:05 PM - 2:09 PM, 4:12", or "? - 2:09 PM" when the start wasn't seen.
    public static String fabledSpan(FabledRecord record) {
        String start = record.startedAt() > 0 ? clockTime(record.startedAt()) : "?";
        String span = start + " - " + clockTime(record.endedAt());
        if (record.startedAt() > 0) span += ", " + clock(record.endedAt() - record.startedAt());
        return span;
    }

    /// Local time of day like "2:05 PM" (with the date when it isn't today).
    public static String clockTime(long epochMs) {
        java.time.ZonedDateTime time = java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault());
        String text = time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.US));
        if (!time.toLocalDate().equals(java.time.LocalDate.now(java.time.ZoneId.systemDefault()))) {
            text = time.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.US)) + " " + text;
        }
        return text;
    }
    //endregion

    public void onReceiveMessage(Component message) {
        String text = message.getString();
        onFabledMessage(text.strip(), now());
        onRainbowMessage(text.strip(), now());
    }

    /// "RAINBOW » A Rainbow has appeared…" starts one; "RAINBOW » The Rainbow has faded into the
    /// sky." ends it there and then (and how long it lasted is remembered). Package-private for tests.
    void onRainbowMessage(String text, long now) {
        if (!RAINBOW_LINE.matcher(text).find()) return;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("faded") || lower.contains("disappeared") || lower.contains("ended") || lower.contains("gone")) {
            if (rainbowStartedAt != 0L) endRainbow(true, now - rainbowStartedAt);
        } else if (lower.contains("appeared")) {
            rainbowStartedAt = now;
            rainbowLocation = PetLocationHandler.currentLocation();
            rainbowWeather = null;
        }
    }

    private static final java.util.regex.Pattern RAINBOW_LINE = java.util.regex.Pattern.compile("^RAINBOW\\s*[»>]", java.util.regex.Pattern.CASE_INSENSITIVE);

    public boolean isRainbowUp() {
        return rainbowStartedAt != 0L;
    }

    private void tickRainbow(long now) {
        if (rainbowStartedAt == 0L) return;
        long elapsed = now - rainbowStartedAt;
        long known = learned().rainbowDurationMs;
        if (elapsed > 45 * 60_000L || (known > 0 && elapsed > known * 3 / 2)) {
            endRainbow(false, elapsed);
            return;
        }
        if (PetLocationHandler.currentLocation() != rainbowLocation) return;
        String weather = BossEventHandler.instance().getWeather().getString().trim();
        if (weather.isEmpty()) return;
        // The boss bar shows the rainbow's weather a moment after the message.
        if (rainbowWeather == null) {
            if (elapsed > 3_000L) rainbowWeather = weather;
        } else if (!weather.equals(rainbowWeather)) {
            endRainbow(true, elapsed);
        }
    }

    private void endRainbow(boolean sawItEnd, long elapsed) {
        if (sawItEnd && elapsed > 30_000L) {
            learned().rainbowDurationMs = elapsed;
            saveLearned();
        }
        rainbowStartedAt = 0L;
        rainbowLocation = null;
        rainbowWeather = null;
    }
    //endregion

    //region What's active
    /// Alerts going on right now, with how long they have left.
    public List<Active> active() {
        List<Active> out = new ArrayList<>();
        int minute = gameMinute();
        for (Alert alert : Alert.values()) {
            if (!enabled(alert)) continue;
            switch (alert.kind) {
                case CLOCK -> {
                    if (minute < 0) continue;
                    int left = windowMinutes(minute, alert.startHour, alert.endHour);
                    if (left > 0) out.add(new Active(alert, smooth("end:" + alert.name(), gameMinutesToMs(left))));
                }
                case MOON -> {
                    if (!isMoonUp()) continue;
                    long known = learned().moonDurationMs;
                    long since = moonElapsedMs();
                    out.add(new Active(alert, known > 0 && since >= 0 ? smooth("end:" + alert.name(), Math.max(0L, known - since)) : -1L));
                }
                case RAINBOW -> {
                    if (!isRainbowUp()) continue;
                    long known = learned().rainbowDurationMs;
                    out.add(new Active(alert, known > 0 ? smooth("end:" + alert.name(), Math.max(0L, known - (now() - rainbowStartedAt))) : -1L));
                }
                case FABLED -> {
                    if (!fabledActive) continue;
                    out.add(new Active(alert, -1L, fabledStartedAt > 0 ? now() - fabledStartedAt : -1L));
                }
            }
        }
        // Bessie during a moon that falls in her 4-6 AM window: one Bessie line, with the moon's
        // better odds (1/500), not two.
        if (out.stream().anyMatch(a -> a.alert() == Alert.BESSIE_MOON)) out.removeIf(a -> a.alert() == Alert.BESSIE_NIGHT);
        return out;
    }

    /// Windows starting within the next few minutes (clock windows and the next moonrise).
    public List<Upcoming> upcoming(long withinMs) {
        List<Upcoming> out = new ArrayList<>();
        int minute = gameMinute();
        if (minute >= 0) {
            for (Alert alert : Alert.values()) {
                if (alert.kind != Kind.CLOCK || !enabled(alert)) continue;
                int window = windowMinutes(minute, alert.startHour, alert.endHour);
                if (window < 0) {
                    long ms = smooth("start:" + alert.name(), gameMinutesToMs(-window));
                    // A little slack once it's listed, so it doesn't blink in and out at the edge.
                    boolean listed = shownUpcoming.contains(alert.name());
                    if (ms <= withinMs || (listed && ms <= withinMs + 15_000L)) {
                        out.add(new Upcoming(alert.displayName, ms));
                        nowUpcoming.add(alert.name());
                    }
                }
            }
        }
        if (!isMoonUp() && (enabled(Alert.ROUGAROU) || enabled(Alert.BESSIE_MOON))) {
            long ms = MoonTimerHandler.instance().getTimeUntilNextMoon();
            if (ms >= 0 && ms <= withinMs) out.add(new Upcoming("Moon (Rougarou, Bessie)", ms));
        }
        shownUpcoming.clear();
        shownUpcoming.addAll(nowUpcoming);
        nowUpcoming.clear();
        out.sort((a, b) -> Long.compare(a.msUntil(), b.msUntil()));
        return out;
    }

    public static boolean enabled(Alert alert) {
        var hud = Configs.hudConfig;
        boolean on = switch (alert) {
            case WITCHING_HOUR -> hud.alertWitchingHour.get();
            case TITANOBOA -> hud.alertTitanoboa.get();
            case BESSIE_NIGHT, BESSIE_MOON -> hud.alertBessie.get();
            case ROUGAROU -> hud.alertRougarou.get();
            case GARABONCIAS -> hud.alertGaraboncias.get();
            case FABLED -> hud.alertFabled.get();
        };
        if (!on) return false;
        return !hud.alertsOnlyHere.get() || instance().locationOf(alert) == PetLocationHandler.currentLocation();
    }

    /// Where an alert is (a Fabled event's location comes from its chat message; may be null).
    public FoeLocations.Location locationOf(Alert alert) {
        return alert == Alert.FABLED ? FoeLocations.fromName(fabledLocationName) : alert.location;
    }

    /// Location name for the panel and chat.
    public String locationLabel(Alert alert) {
        if (alert == Alert.FABLED) return fabledLocationName.isEmpty() ? "Unknown location" : fabledLocationName;
        return locationName(alert.location);
    }
    //endregion

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = now();
        tickClock(now);
        tickRainbow(now);
        dropUnusedCountdowns();
        if (fabledActive && fabledStartedAt > 0 && now - fabledStartedAt > FABLED_MAX_MS) fabledActive = false;

        // Chat line + sound when an alert starts.
        Set<Alert> current = EnumSet.noneOf(Alert.class);
        for (Active active : active()) {
            current.add(active.alert());
            if (announced.contains(active.alert())) continue;
            // Only announce once it has really started (held for a moment), never for a blip.
            long since = activeSince.computeIfAbsent(active.alert(), a -> now);
            if (now - since < ANNOUNCE_AFTER_MS) continue;
            announced.add(active.alert());
            if (Configs.hudConfig.alertChat.get()) {
                // [FOE+] WITCHING HOUR Cypress Lake · Bigfoot 1/300 · ~2:10 left
                dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.line(active.alert().displayName.toUpperCase(Locale.ROOT), active.alert().color)
                        .append(dannypx.foe.helper.FoeChat.place(locationLabel(active.alert())))
                        .append(dannypx.foe.helper.FoeChat.sep())
                        .append(dannypx.foe.helper.FoeChat.text(active.alert().odds))
                        .append(dannypx.foe.helper.FoeChat.sep())
                        .append(dannypx.foe.helper.FoeChat.highlight(active.alert() == Alert.FABLED ? "until caught" : timeLeft(active.msLeft()))));
            }
            if (Configs.hudConfig.alertSound.get()) {
                var sound = Configs.hudConfig.alertSoundType.get().sound.value();
                mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1f, 1f));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.2f, 1f));
            }
        }
        announced.retainAll(current);
        activeSince.keySet().retainAll(current);
    }

    private static final long ANNOUNCE_AFTER_MS = 2_000L;
    private final java.util.Map<Alert, Long> activeSince = new java.util.EnumMap<>(Alert.class);

    //region Text helpers
    public static String locationName(FoeLocations.Location location) {
        return switch (location) {
            case CYPRESS_LAKE -> "Cypress Lake";
            case AMAZON_RIVER -> "Amazon River";
            case GREAT_LAKES -> "Great Lakes";
            case TOLEDO_BEND -> "Toledo Bend";
            case DANUBE_RIVER -> "Danube River";
            default -> location.name();
        };
    }

    /// "12:34 left", or "until it ends" when the end isn't known yet.
    public static String timeLeft(long ms) {
        if (ms < 0) return "until it ends";
        return clock(ms) + " left";
    }

    public static String clock(long ms) {
        long s = Math.max(0L, ms) / 1000L;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }
    //endregion
}
