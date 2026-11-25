package dannypx.foe.handler.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dannypx.foe.FishOnMCExtras;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Moon timer, counted from a known moonrise (18 Nov 2026, 5:45:39 PM Eastern) every 160 minutes.
 * Every moonrise you see in chat ("BLOOD MOON » A Blood Moon has risen!") re-syncs it to that
 * exact moment, and it's saved, so it stays right across restarts.
 * <p>
 * Moons come every 160 minutes, always (the server's schedule; the wiki says the same), but a
 * server restart moves the whole schedule (on 18 Nov it moved by 17 minutes), so every moonrise
 * seen in chat re-syncs the timer to that exact second, however far off it was. Which moon it is
 * still comes from chat.
 */
public class MoonTimerHandler {
    private static MoonTimerHandler INSTANCE = new MoonTimerHandler();

    public static final long DEFAULT_CYCLE_MS = 160L * 60L * 1000L;

    /** The moon that is currently up, or null. */
    private Moon currentMoon = null;

    /// A moonrise seen on the server: 18 Nov 2026, 5:45:39 PM Eastern (22:45:39 UTC), after the
    /// server restart that moved the schedule. With the 160 minute cycle this puts every moon on the
    /// right time from the start; each moonrise you see in chat re-syncs it to the second.
    static final long KNOWN_MOONRISE_MS = java.time.Instant.parse("2026-11-18T22:45:39Z").toEpochMilli();

    /// Bumped to throw away timers saved by older builds, which could learn a wrong cycle or take a
    /// stray line as a moonrise and drift off the real moons, or still hold the schedule from before
    /// the 18 Nov restart.
    static final int SAVE_VERSION = 3;

    /// Saved: when a moon was last seen rising (the cycle is always 160 minutes).
    static final class Saved {
        int version;   // 0 in files from older builds (and before the first save)
        long lastRiseAt = KNOWN_MOONRISE_MS;
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
            if (saved.version != SAVE_VERSION) {
                // an older build's timer (or none yet): back to the known moonrise
                saved = new Saved();
                saved.version = SAVE_VERSION;
                save();
            }
            saved.cycleMs = DEFAULT_CYCLE_MS;
            if (saved.lastRiseAt <= 0L) saved.lastRiseAt = KNOWN_MOONRISE_MS;
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

    /// When chat last said a moon set (0: not since you joined).
    private long setAt = 0L;
    static final long MOON_LENGTH_MS = 10L * 60_000L;

    /// The schedule says a moon is up right now though chat hasn't named it (yet): its line shows from the second it
    /// rises, not when the chat line about it comes in, or when you joined in the middle of one.
    public boolean scheduledUp() {
        return scheduledUp(System.currentTimeMillis());
    }

    boolean scheduledUp(long now) {
        if (currentMoon != null || !isKnown()) return false;
        long since = Math.floorMod(now - saved().lastRiseAt, saved().cycleMs);
        return since < MOON_LENGTH_MS && setAt < now - since;
    }

    public void onReceiveMessage(Component message) {
        String text = message.getString();
        for (Moon moon : Moon.values()) {
            if (text.startsWith(moon.chatPrefix)) {
                boolean set = text.contains(" has set");
                if (set && this.currentMoon != null) AlertHandler.instance().onMoonSet();
                if (set) this.setAt = System.currentTimeMillis();
                if (!set && text.contains("has risen")) this.onMoonrise(System.currentTimeMillis());
                this.currentMoon = set ? null : moon;
                return;
            }
        }
    }

    /// A moon just rose: the timer is set to this exact moment, however far off the old schedule it
    /// was (a server restart moves the schedule). The cycle is never changed. Package-private for tests.
    void onMoonrise(long now) {
        Saved s = saved();
        s.lastRiseAt = now;
        save();
    }

    public void onLeaveServer() {
        this.currentMoon = null;
        this.setAt = 0L;
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
