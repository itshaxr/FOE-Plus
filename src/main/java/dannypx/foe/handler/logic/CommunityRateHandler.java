package dannypx.foe.handler.logic;

import dannypx.foe.handler.fetch.BossEventHandler;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * How fast the community goal is filling: the boss bar's count, noted each time it goes up (each catch), over the
 * last hour. From that, catches per hour and when the goal will be reached. When the count drops (the goal was
 * hit and a new one started) it starts over.
 */
public final class CommunityRateHandler {
    private static final CommunityRateHandler INSTANCE = new CommunityRateHandler();

    public static CommunityRateHandler instance() {
        return INSTANCE;
    }

    /// Package-private for tests.
    CommunityRateHandler() {}

    record Sample(long at, long value) {}

    private final Deque<Sample> samples = new ArrayDeque<>();
    static final long WINDOW_MS = 60L * 60_000L, MIN_SPAN_MS = 90_000L;

    /// The last good readings, kept through a moment where neither the boss bar nor /communityevent says (the boss
    /// bar showing something else for a while), so the panel doesn't come and go.
    private long lastCur = -1, lastGoal = -1, lastGoodAt = 0L;
    static final long KEEP_MS = 30L * 60_000L;

    public long current() {
        // the boss bar's number only while it's showing it (between its turns it's old: the panel froze on it)
        long v = BossEventHandler.instance().communityFresh() ? CommunityEventHandler.parse(BossEventHandler.instance().getCommunityGoalCurrent().getString()) : -1;
        if (v < 0) {
            CommunityEventHandler e = CommunityEventHandler.instance();
            if (e.hasData()) v = CommunityEventHandler.parse(e.total());
        }
        return remember(v, -2, System.currentTimeMillis())[0];
    }

    public long goal() {
        long v = BossEventHandler.instance().communityFresh() ? CommunityEventHandler.parse(BossEventHandler.instance().getCommunityGoalMax().getString()) : -1;
        if (v <= 0) {
            CommunityEventHandler e = CommunityEventHandler.instance();
            if (e.hasData()) v = CommunityEventHandler.parse(e.goal());
        }
        return remember(-2, v, System.currentTimeMillis())[1];
    }

    /// Package-private for tests: a reading of the count and/or goal (-2: not read this time), and what to show
    /// {count, goal}: the reading when good, else the last good one for half an hour.
    long[] remember(long cur, long goal, long now) {
        if (cur >= 0) { lastCur = cur; lastGoodAt = now; }
        if (goal > 0) { lastGoal = goal; lastGoodAt = now; }
        boolean fresh = now - lastGoodAt <= KEEP_MS;
        return new long[]{cur >= 0 ? cur : fresh ? lastCur : -1, goal > 0 ? goal : fresh ? lastGoal : -1};
    }

    public void tick() {
        long v = current();
        // last session's saved number is only for showing until the fresh one comes: counting the
        // pace from it would make the first "per hour" wildly off
        boolean bossSays = BossEventHandler.instance().communityFresh()
                && CommunityEventHandler.parse(BossEventHandler.instance().getCommunityGoalCurrent().getString()) >= 0;
        if (!bossSays && CommunityEventHandler.instance().fromLastSession()) return;
        note(v, System.currentTimeMillis());
    }

    /// Package-private for tests: a reading of the count at `now`.
    void note(long value, long now) {
        if (value < 0) return;
        Sample last = samples.peekLast();
        if (last != null && value < last.value()) samples.clear();     // the goal was reached: a new one
        if (last == null || value != last.value()) samples.addLast(new Sample(now, value));
        while (samples.size() > 2 && now - samples.peekFirst().at() > WINDOW_MS) samples.removeFirst();
    }

    /// Catches per hour, or -1 until there's enough to tell (a minute and a half of counting).
    public double perHour(long now) {
        if (samples.size() < 2) return -1;
        Sample a = samples.peekFirst(), b = samples.peekLast();
        long span = Math.max(now - a.at(), b.at() - a.at());
        if (span < MIN_SPAN_MS) return -1;
        return (b.value() - a.value()) * 3_600_000.0 / span;
    }

    /// How long until the goal at this pace (ms), or -1 when unknown.
    public long msToGoal(long now) {
        double rate = perHour(now);
        long cur = samples.isEmpty() ? -1 : samples.peekLast().value(), g = goal();
        if (rate <= 0 || cur < 0 || g <= 0 || cur >= g) return -1;
        return Math.round((g - cur) / rate * 3_600_000.0);
    }

    /// Package-private for tests (the goal given rather than read).
    long msToGoal(long now, long goal) {
        double rate = perHour(now);
        long cur = samples.isEmpty() ? -1 : samples.peekLast().value();
        if (rate <= 0 || cur < 0 || goal <= 0 || cur >= goal) return -1;
        return Math.round((goal - cur) / rate * 3_600_000.0);
    }

    /// "12h 21m", "45m", "2d 3h".
    public static String duration(long ms) {
        long m = ms / 60_000L, h = m / 60, d = h / 24;
        if (d > 0) return d + "d " + (h % 24) + "h";
        if (h > 0) return h + "h " + (m % 60) + "m";
        return Math.max(1, m) + "m";
    }
}
