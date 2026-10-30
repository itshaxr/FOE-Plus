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
 *   <li>Rainbows come from chat; one ends when the weather on the boss bar changes, and its length
 *       is learned the same way.</li>
 * </ul>
 */
public class AlertHandler {
    private static AlertHandler INSTANCE = new AlertHandler();

    public enum Kind { CLOCK, MOON, RAINBOW }

    public enum Alert {
        WITCHING_HOUR("Witching Hour", 0xD23B3B, FoeLocations.Location.CYPRESS_LAKE, Kind.CLOCK, 0, 3,
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
                "1/250: Concoction or Essence (50/50)", "4 + Infusion Capsule: Wizard's Infusion");

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

    /// One alert as the panel shows it. {@code msLeft} is -1 when the end isn't known yet.
    public record Active(Alert alert, long msLeft) {}

    public record Upcoming(String what, long msUntil) {}

    //region Learned timings (saved)
    private static final long DEFAULT_MS_PER_GAME_MINUTE = 50_000L / 60L; // vanilla: an in-game hour is 50 s
    static final class Learned {
        double msPerGameMinute = DEFAULT_MS_PER_GAME_MINUTE;
        long moonDurationMs = 0L;
        long rainbowDurationMs = 0L;
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
            } catch (Exception e) {
                LoggerHandler.error(e);
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
        } catch (Exception e) {
            LoggerHandler.error(e);
        }
    }

    private static Path learnedFile() {
        return FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("alert_timings.json");
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
        return parseGameMinutes(BossEventHandler.instance().getTime().getString());
    }

    private void tickClock(long now) {
        int minute = gameMinute();
        if (minute < 0) return;
        if (minute != lastGameMinute) {
            if (lastGameMinute >= 0) {
                int delta = Math.floorMod(minute - lastGameMinute, 1440);
                long realDelta = now - lastGameMinuteChangedAt;
                // The first change after joining only covers part of a step; skip it.
                if (clockSampleReady && delta > 0 && delta <= 60 && realDelta > 200 && realDelta < 10 * 60_000L) {
                    double sample = (double) realDelta / delta;
                    Learned l = learned();
                    l.msPerGameMinute = l.msPerGameMinute * 0.8 + sample * 0.2;
                    if (now - lastClockSaveAt > 5 * 60_000L) {
                        lastClockSaveAt = now;
                        saveLearned();
                    }
                }
                clockSampleReady = delta > 0 && delta <= 60;
            }
            lastGameMinute = minute;
            lastGameMinuteChangedAt = now;
        }
    }

    private long gameMinutesToMs(int minutes) {
        return Math.round(minutes * learned().msPerGameMinute);
    }
    //endregion

    //region Moons and rainbows
    public boolean isMoonUp() {
        return MoonTimerHandler.instance().getCurrentMoon() != null;
    }

    private long moonElapsedMs() {
        return MoonTimerHandler.CYCLE_MS - MoonTimerHandler.instance().getTimeUntilNextMoon();
    }

    /// Called when chat says the moon has set: remembers how long moons last.
    public void onMoonSet() {
        long elapsed = moonElapsedMs();
        if (elapsed > 30_000L && elapsed < MoonTimerHandler.CYCLE_MS) {
            learned().moonDurationMs = elapsed;
            saveLearned();
        }
    }

    public void onReceiveMessage(Component message) {
        String text = message.getString();
        if (text.startsWith("RAINBOW »") && text.contains("appeared")) {
            rainbowStartedAt = System.currentTimeMillis();
            rainbowLocation = PetLocationHandler.currentLocation();
            rainbowWeather = null;
        }
    }

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
                    if (left > 0) out.add(new Active(alert, gameMinutesToMs(left)));
                }
                case MOON -> {
                    if (!isMoonUp()) continue;
                    long known = learned().moonDurationMs;
                    out.add(new Active(alert, known > 0 ? Math.max(0L, known - moonElapsedMs()) : -1L));
                }
                case RAINBOW -> {
                    if (!isRainbowUp()) continue;
                    long known = learned().rainbowDurationMs;
                    out.add(new Active(alert, known > 0 ? Math.max(0L, known - (System.currentTimeMillis() - rainbowStartedAt)) : -1L));
                }
            }
        }
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
                    long ms = gameMinutesToMs(-window);
                    if (ms <= withinMs) out.add(new Upcoming(alert.displayName, ms));
                }
            }
        }
        if (!isMoonUp() && (enabled(Alert.ROUGAROU) || enabled(Alert.BESSIE_MOON))) {
            long ms = MoonTimerHandler.instance().getTimeUntilNextMoon();
            if (ms <= withinMs) out.add(new Upcoming("Moon (Rougarou, Bessie)", ms));
        }
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
        };
        if (!on) return false;
        return !hud.alertsOnlyHere.get() || alert.location == PetLocationHandler.currentLocation();
    }
    //endregion

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        tickClock(now);
        tickRainbow(now);

        // Chat line + sound when an alert starts.
        Set<Alert> current = EnumSet.noneOf(Alert.class);
        for (Active active : active()) {
            current.add(active.alert());
            if (announced.contains(active.alert())) continue;
            announced.add(active.alert());
            if (Configs.hudConfig.alertChat.get()) {
                mc.player.displayClientMessage(Component.literal("[FOE+] ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(active.alert().displayName.toUpperCase(Locale.ROOT) + "! ").withColor(active.alert().color).withStyle(ChatFormatting.BOLD))
                        .append(Component.literal(locationName(active.alert().location)).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" · " + active.alert().odds).withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(" · " + timeLeft(active.msLeft())).withStyle(ChatFormatting.GREEN)), false);
            }
            if (Configs.hudConfig.alertSound.get()) {
                var sound = Configs.hudConfig.alertSoundType.get().sound.value();
                mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1f, 1f));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.2f, 1f));
            }
        }
        announced.retainAll(current);
    }

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

    /// "~12:34 left", or "until it ends" when the end isn't known yet.
    public static String timeLeft(long ms) {
        if (ms < 0) return "until it ends";
        return "~" + clock(ms) + " left";
    }

    public static String clock(long ms) {
        long s = Math.max(0L, ms) / 1000L;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }
    //endregion
}
