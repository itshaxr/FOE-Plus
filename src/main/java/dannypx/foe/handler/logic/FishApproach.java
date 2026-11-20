package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;

import java.util.HashMap;
import java.util.Map;

/**
 * The fish coming to your bobber, for the catch ring, from the trails the server draws in the water (see
 * {@link TrailTracker}: each trail is matched to the bobber it's swimming to, so other players' fish nearby don't
 * count). With "Hide other players' fish trails" on, only your own fish's trail is drawn.
 */
public final class FishApproach {
    private FishApproach() {}

    private static final TrailTracker tracker = new TrailTracker();
    /// The splashes just hidden (x, z, tick), so the bubbles a hidden trail leaves go too.
    private static final double[][] hidden = new double[8][];
    private static int hiddenAt = 0;

    /// A "fishing" splash at (x, y, z) from the server; true when it should not be drawn (someone else's fish).
    public static boolean onSplash(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        FishingHook mine = mc.player.fishing;
        long tick = mc.level.getGameTime();
        Map<Integer, Double> distances = new HashMap<>();
        int nearHook = -1;
        double near = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof FishingHook hook) || hook.distanceToSqr(x, y, z) > 24 * 24) continue;
            if (Math.abs(y - hook.getY()) > 1.5) continue;
            double dx = x - hook.getX(), dz = z - hook.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            distances.put(hook.getId(), d);
            if (d < near) { near = d; nearHook = hook.getId(); }
        }
        int owner = distances.isEmpty() ? -1 : tracker.offer(distances, tick);
        if (!Configs.hudConfig.hideOtherFishTrails.get()) return false;
        int myId = mine == null ? Integer.MIN_VALUE : mine.getId();
        boolean hide;
        if (owner >= 0) hide = owner != myId;                       // a trail known to be someone's
        else if (near < 0.6) hide = nearHook != myId;                // the splash at a bobber (a bite)
        else {
            // not known yet whose (its first few ticks): shown only when it could be coming to yours
            Double toMine = distances.get(myId);
            hide = toMine == null || toMine > TrailTracker.MAX_START;
        }
        if (hide) {
            hidden[hiddenAt] = new double[]{x, z, tick};
            hiddenAt = (hiddenAt + 1) % hidden.length;
        }
        return hide;
    }

    /// A bubble at (x, y, z); true when it belongs to a trail just hidden.
    public static boolean onBubble(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !Configs.hudConfig.hideOtherFishTrails.get()) return false;
        long tick = mc.level.getGameTime();
        for (double[] h : hidden) {
            if (h == null || tick - (long) h[2] > 1) continue;
            double dx = x - h[0], dz = z - h[1];
            if (dx * dx + dz * dz < 0.3 * 0.3) return true;
        }
        return false;
    }

    private static TrailTracker.Trail current() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.fishing == null || mc.level == null) return null;
        return tracker.coming(mc.player.fishing.getId(), mc.level.getGameTime());
    }

    /// How close the fish is: 0 as the trail starts, 1 at the bobber; -1 when no fish is coming to yours.
    public static float progress() {
        TrailTracker.Trail t = current();
        return t == null ? -1f : closeness(t.start, t.last);
    }

    /// Package-private for tests: how far along a trail that started `start` blocks out is at `now` blocks.
    static float closeness(double start, double now) {
        if (start <= 0) return -1f;
        return (float) Math.max(0, Math.min(1, 1 - now / start));
    }

    /// Seconds until the bite (the trail's distance at a tenth of a block a tick), or -1.
    public static float secondsLeft() {
        TrailTracker.Trail t = current();
        return t == null ? -1f : (float) (t.last / 0.1 / 20.0);
    }
}
