package dannypx.foe.handler.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dannypx.foe.FishOnMCExtras;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Moon timer. Instead of guessing when moons rise, it waits for the first moonrise you see in
 * chat ("BLOOD MOON » A Blood Moon has risen!"), remembers that moment and counts from there.
 * It's saved, so after that one moon the timer always knows the next one, also after a restart.
 * <p>
 * Moons come every {@link #getCycleMs()} (160 minutes to start with, as FOE assumed). Each further
 * moonrise you see corrects the anchor, and the time between two of them corrects the cycle
 * length if the server's is different. Which moon it is still comes from chat.
 */
public class MoonTimerHandler {
    private static MoonTimerHandler INSTANCE = new MoonTimerHandler();

    public static final long DEFAULT_CYCLE_MS = 160L * 60L * 1000L;
    private static final long MIN_CYCLE_MS = 15L * 60L * 1000L;
    private static final long MAX_CYCLE_MS = 6L * 60L * 60L * 1000L;

    /** The moon that is currently up, or null. */
    private Moon currentMoon = null;

    /// Saved: when a moon was last seen rising, and how long a cycle is.
    static final class Saved {
        long lastRiseAt = 0L;
        long cycleMs = DEFAULT_CYCLE_MS;
    }

    private Saved saved = null;

    public static MoonTimerHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new MoonTimerHandler();
        }
        return INSTANCE;
    }

    private Saved saved() {
        if (saved == null) {
            saved = new Saved();
            try {
                Path file = file();
                if (Files.exists(file)) {
                    Saved read = new Gson().fromJson(Files.readString(file), Saved.class);
                    if (read != null) saved = read;
                }
            } catch (Exception | LinkageError e) {
                LoggerHandler.error(e instanceof Exception ex ? ex : new RuntimeException(e));
            }
            if (saved.cycleMs < MIN_CYCLE_MS || saved.cycleMs > MAX_CYCLE_MS) saved.cycleMs = DEFAULT_CYCLE_MS;
        }
        return saved;
    }

    private void save() {
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(saved()));
        } catch (Exception | LinkageError e) {
            LoggerHandler.error(e instanceof Exception ex ? ex : new RuntimeException(e));
        }
    }

    /// Where the timer is saved (swappable in tests).
    java.util.function.Supplier<Path> fileLocation =
            () -> FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("moon_timer.json");

    private Path file() {
        return fileLocation.get();
    }

    /// True once a moonrise has been seen (ever), so the timer knows when moons come.
    public boolean isKnown() {
        return saved().lastRiseAt > 0L;
    }

    public long getCycleMs() {
        return saved().cycleMs;
    }

    /// Milliseconds until the next moonrise, or -1 while no moonrise has been seen yet.
    public long getTimeUntilNextMoon() {
        return getTimeUntilNextMoon(System.currentTimeMillis());
    }

    long getTimeUntilNextMoon(long now) {
        if (!isKnown()) return -1L;
        long cycle = saved().cycleMs;
        return cycle - Math.floorMod(now - saved().lastRiseAt, cycle);
    }

    /// Milliseconds since the last moonrise, or -1 while unknown.
    public long getTimeSinceMoonrise() {
        if (!isKnown()) return -1L;
        return Math.floorMod(System.currentTimeMillis() - saved().lastRiseAt, saved().cycleMs);
    }

    public Moon getCurrentMoon() {
        return currentMoon;
    }

    public void onReceiveMessage(Component message) {
        String text = message.getString();
        for (Moon moon : Moon.values()) {
            if (text.startsWith(moon.chatPrefix)) {
                boolean set = text.contains(" has set");
                if (set && this.currentMoon != null) AlertHandler.instance().onMoonSet();
                if (!set && text.contains("has risen")) this.onMoonrise(System.currentTimeMillis());
                this.currentMoon = set ? null : moon;
                return;
            }
        }
    }

    /// A moon just rose: anchor the timer here and, from the gap since the last one, correct the
    /// cycle length. Package-private for tests.
    void onMoonrise(long now) {
        Saved s = saved();
        if (s.lastRiseAt > 0L) {
            long gap = now - s.lastRiseAt;
            if (gap >= MIN_CYCLE_MS) {
                long k = Math.max(1L, Math.round((double) gap / s.cycleMs));
                long perCycle = gap / k;
                if (Math.abs(perCycle - s.cycleMs) <= s.cycleMs * 15 / 100) {
                    // Matches the known cycle (maybe with moons missed in between): fine-tune it.
                    if (perCycle >= MIN_CYCLE_MS && perCycle <= MAX_CYCLE_MS) s.cycleMs = perCycle;
                } else if (gap <= MAX_CYCLE_MS) {
                    // The cycle is something else: take the gap as the new cycle.
                    s.cycleMs = gap;
                }
            }
        }
        s.lastRiseAt = now;
        save();
    }

    public void onLeaveServer() {
        this.currentMoon = null;
    }

    public enum Moon {
        FULL("FULL MOON »", "Full Moon", 0xFFFFFF),
        BLUE("BLUE MOON »", "Blue Moon", 0x60C2E9),
        SUPER("SUPER MOON »", "Super Moon", 0xB0F9D5),
        BLOOD("BLOOD MOON »", "Blood Moon", 0xFF0000);

        public final String chatPrefix;
        public final String displayName;
        public final int color;

        Moon(String chatPrefix, String displayName, int color) {
            this.chatPrefix = chatPrefix;
            this.displayName = displayName;
            this.color = color;
        }
    }
}
