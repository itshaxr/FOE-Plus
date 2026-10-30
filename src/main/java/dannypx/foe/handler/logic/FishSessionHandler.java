package dannypx.foe.handler.logic;

import dannypx.foe.handler.store.StatsDataHandler;
import net.minecraft.client.Minecraft;

/**
 * Session timer for the All-Time Stats panel's "fish time" and "fish/hour" lines.
 * Time only counts while you have a line out, or caught something in the last two minutes,
 * so AFK time in town doesn't drag fish/hour down. Resets when you restart the game.
 */
public class FishSessionHandler {
    private static FishSessionHandler INSTANCE = new FishSessionHandler();

    private static final long IDLE_GRACE_MS = 2L * 60L * 1000L;

    private long activeTimeMs = 0L;
    private long lastTickMs = 0L;
    private long lastCatchMs = 0L;
    private int startFishTotal = -1;
    private int lastFishTotal = -1;

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

        if (startFishTotal < 0) {
            startFishTotal = fishTotal;
            lastFishTotal = fishTotal;
        }
        if (fishTotal != lastFishTotal) {
            // An imported/reset stats file can move the total backwards; rebase on it.
            if (fishTotal < lastFishTotal) startFishTotal = fishTotal;
            else lastCatchMs = now;
            lastFishTotal = fishTotal;
        }

        boolean fishing = mc.player != null && mc.player.fishing != null;
        if (lastTickMs != 0L && (fishing || now - lastCatchMs < IDLE_GRACE_MS)) {
            activeTimeMs += Math.min(now - lastTickMs, 1000L);
        }
        lastTickMs = now;
    }

    public long getActiveTimeMs() {
        return activeTimeMs;
    }

    public int getSessionFish() {
        return startFishTotal < 0 ? 0 : Math.max(0, lastFishTotal - startFishTotal);
    }

    public double getFishPerHour() {
        double hours = activeTimeMs / 3_600_000.0;
        return hours <= 0 ? 0 : getSessionFish() / hours;
    }
}
