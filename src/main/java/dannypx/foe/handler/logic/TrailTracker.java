package dannypx.foe.handler.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Whose bobber each fish trail is swimming to. Before a bite the server puts a fish's splashes a tenth of a block from
 * its bobber for every tick left, so a trail closes in on its own bobber at exactly a tenth of a block a tick (two in
 * the rain), wherever it wanders. Each splash is measured from every bobber nearby and goes to the trail it carries
 * on most exactly: a fish swimming to someone else's bobber closes in on that one at the right speed and on yours
 * unevenly. Pure logic (no game types), so it can be tested.
 */
public final class TrailTracker {
    /// The farthest a trail starts (80 ticks at a tenth of a block), with some room.
    static final double MAX_START = 9.0;
    /// How many ticks running a trail must close in at the right speed to count.
    static final int SURE = 4;
    /// A trail splashes every tick: one missing for more than this many ticks (packets can come a little late) is gone.
    static final int GONE_TICKS = 3;
    /// One known to be coming to a bobber is kept through a longer gap (a lag spike), so the ring doesn't drop it.
    static final int SURE_GONE_TICKS = 10;
    /// How near a tenth (or two) of a block a tick a splash must close in.
    static final double TOLERANCE = 0.03;

    /// One trail: where it started and is now from its bobber, when last seen, how many ticks it closed in right.
    public static final class Trail {
        public double start, last;
        long lastTick;
        public int steps;

        Trail(double d, long tick) {
            start = d;
            last = d;
            lastTick = tick;
        }
    }

    /// Per bobber (its entity id), the trails that might be coming to it.
    private final Map<Integer, List<Trail>> trails = new HashMap<>();

    public void clear() {
        trails.clear();
    }

    /// A splash at these distances from the bobbers around (bobber id → blocks) at game tick `tick`. Returns the
    /// bobber whose trail it carries on, or -1 when it starts a trail (or is nobody's, as yet).
    public int offer(Map<Integer, Double> distances, long tick) {
        trails.keySet().retainAll(distances.keySet());
        for (List<Trail> list : trails.values()) list.removeIf(t -> tick - t.lastTick > (t.steps >= SURE ? SURE_GONE_TICKS : GONE_TICKS));
        int bestHook = -1;
        Trail best = null;
        double bestErr = Double.MAX_VALUE;
        for (var e : distances.entrySet()) {
            List<Trail> list = trails.get(e.getKey());
            if (list == null) continue;
            double d = e.getValue();
            for (Trail t : list) {
                // the second splash of the same tick, at the same place: the same trail
                if (tick == t.lastTick && Math.abs(t.last - d) < 0.02) return t.steps >= SURE ? e.getKey() : -1;
                // each tick's place is a tenth of a block nearer than the last (whenever its packet arrives); a
                // splash or two missed (or the rain's double steps) makes it two to four tenths
                double step = t.last - d;
                long k = Math.round(step / 0.1);
                if (k < 1 || k > 4) continue;
                double err = Math.abs(step - 0.1 * k);
                if (err < TOLERANCE && err < bestErr) { bestErr = err; best = t; bestHook = e.getKey(); }
            }
        }
        if (best != null) {
            best.last = distances.get(bestHook);
            best.lastTick = tick;
            best.steps++;
            return best.steps >= SURE ? bestHook : -1;
        }
        // a new trail, maybe coming to any of the bobbers it's near
        for (var e : distances.entrySet()) {
            double d = e.getValue();
            if (d > MAX_START || d < 0.3) continue;
            List<Trail> list = trails.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
            if (list.size() < 16) list.add(new Trail(d, tick));
        }
        return -1;
    }

    /// The trail coming to bobber `hook` (the surest), or null.
    public Trail coming(int hook, long tick) {
        List<Trail> list = trails.get(hook);
        if (list == null) return null;
        Trail best = null;
        for (Trail t : list) {
            if (t.steps < SURE || tick - t.lastTick > SURE_GONE_TICKS) continue;
            if (best == null || t.steps > best.steps) best = t;
        }
        return best;
    }

    /// Whether bobber `hook` has a trail that might be coming to it (not sure yet, or sure).
    public boolean maybe(int hook, long tick) {
        List<Trail> list = trails.get(hook);
        if (list == null) return false;
        for (Trail t : list) if (tick - t.lastTick <= GONE_TICKS) return true;
        return false;
    }
}
