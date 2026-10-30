package dannypx.foe.handler.logic;

import dannypx.foe.handler.store.StatsDataHandler;
import net.minecraft.client.Minecraft;

/**
 * FOE-style fish timer for the All-Time Stats panel's "fish time" and "fish/hour" lines.
 * Time only counts while you have a line out, or caught something in the last two minutes, so AFK
 * time in town doesn't drag fish/hour down. Saved with your stats (written every 30 seconds), so
 * it carries across restarts; it can be seeded from a previous FOE install.
 */
public class FishSessionHandler {
    private static FishSessionHandler INSTANCE = new FishSessionHandler();

    private static final long IDLE_GRACE_MS = 2L * 60L * 1000L;
    private static final long FLUSH_MS = 30_000L;

    private long lastTickMs = 0L;
    private long lastCatchMs = 0L;
    private long lastFlushMs = 0L;
    private int lastFishTotal = -1;
    /// Not yet written to the stats file.
    private long pendingMs = 0L;
    private int pendingFish = 0;

    public static FishSessionHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new FishSessionHandler();
        }
        return INSTANCE;
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        int fishTotal = StatsDataHandler.instance().getStatsData().fishTotal;

        if (lastFishTotal < 0) lastFishTotal = fishTotal;
        // Big jumps are a stats load or import, not catches.
        if (fishTotal > lastFishTotal && fishTotal - lastFishTotal <= 20) {
            pendingFish += fishTotal - lastFishTotal;
            lastCatchMs = now;
        }
        lastFishTotal = fishTotal; // an import or reset can move it backwards; just follow it

        boolean fishing = mc.player != null && mc.player.fishing != null;
        if (lastTickMs != 0L && (fishing || now - lastCatchMs < IDLE_GRACE_MS)) {
            pendingMs += Math.min(now - lastTickMs, 1000L);
        }
        lastTickMs = now;

        if (now - lastFlushMs > FLUSH_MS && (pendingMs > 0 || pendingFish > 0)) {
            StatsDataHandler.instance().addFishTime(pendingMs, pendingFish);
            pendingMs = 0L;
            pendingFish = 0;
            lastFlushMs = now;
        }
    }

    public long getActiveTimeMs() {
        return StatsDataHandler.instance().getStatsData().fishTimeMs + pendingMs;
    }

    public int getTimedFish() {
        return StatsDataHandler.instance().getStatsData().fishTimeFish + pendingFish;
    }

    public double getFishPerHour() {
        double hours = getActiveTimeMs() / 3_600_000.0;
        return hours <= 0 ? 0 : getTimedFish() / hours;
    }

    /// Clears the fish timer (time and fish/hour), like FOE's timer reset.
    public void reset() {
        var stats = StatsDataHandler.instance().getStatsData();
        stats.fishTimeMs = 0L;
        stats.fishTimeFish = 0;
        pendingMs = 0L;
        pendingFish = 0;
        StatsDataHandler.instance().markChanged();
    }
}
