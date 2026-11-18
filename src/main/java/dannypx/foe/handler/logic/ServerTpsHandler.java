package dannypx.foe.handler.logic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * The server's ticks per second, worked out from its clock: it sends the world's time about once a second, so how
 * many ticks passed between two of those over how long it really took is its TPS. A few readings are averaged so
 * one late packet doesn't make it jump. Your ping is the server's own figure for you, from the player list.
 */
public final class ServerTpsHandler {
    private static final ServerTpsHandler INSTANCE = new ServerTpsHandler();
    public static ServerTpsHandler instance() { return INSTANCE; }

    static final int SAMPLES = 6;
    /// No time from the server for this long: TPS unknown (lagging hard, or not in a world).
    static final long STALE_MS = 6_000L;

    private final double[] samples = new double[SAMPLES];
    private int count = 0, next = 0;
    private long lastGameTime = -1L, lastAt = 0L;

    /// From the server's time packet (game thread).
    public void onTime(long gameTime) {
        onTime(gameTime, System.currentTimeMillis());
    }

    /// Package-private for tests.
    void onTime(long gameTime, long now) {
        if (lastGameTime >= 0) {
            long ticks = gameTime - lastGameTime, ms = now - lastAt;
            if (ticks < 0 || ms > 15_000L) reset();                       // another world, or a long gap
            else if (ms >= 250L && ticks > 0) add(Math.min(20.0, ticks * 1000.0 / ms));
            else if (ms < 250L) return;                                   // too close to tell; keep the older start
        }
        lastGameTime = gameTime;
        lastAt = now;
    }

    private void add(double tps) {
        samples[next] = tps;
        next = (next + 1) % SAMPLES;
        count = Math.min(SAMPLES, count + 1);
    }

    public void reset() {
        count = 0;
        next = 0;
        lastGameTime = -1L;
    }

    /// The server's TPS (0 to 20), or -1 when not known.
    public double tps() {
        return tps(System.currentTimeMillis());
    }

    /// Package-private for tests.
    double tps(long now) {
        if (count == 0 || now - lastAt > STALE_MS) return -1;
        double sum = 0;
        for (int i = 0; i < count; i++) sum += samples[i];
        return sum / count;
    }

    /// Your ping to the server in ms, or -1 when not known.
    public static int ping() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return -1;
        PlayerInfo me = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return me == null ? -1 : me.getLatency();
    }

    public static String tpsText(double tps) {
        return tps < 0 ? "?" : String.format(java.util.Locale.ROOT, "%.1f", tps);
    }
}
