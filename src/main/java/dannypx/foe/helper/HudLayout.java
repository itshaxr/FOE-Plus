package dannypx.foe.helper;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-frame layout for the HUD boxes. Every frame starts empty ({@link #beginFrame}); the fixed
 * pieces (top bar, equipment bar, hotbar area) are reserved first, then each box asks for its
 * configured spot with {@link #place}. A box is first kept fully on screen, then moved away from
 * any box placed before it, so boxes never cover each other and never leave the screen, whatever
 * their content does to their size.
 * <p>
 * Pure logic in GUI-scaled pixels with no Minecraft types, so it can be unit tested.
 */
public final class HudLayout {
    /// Space kept between two boxes.
    public static final int GAP = 2;
    /// Safety limit for the push loops (each step moves past one box).
    private static final int MAX_STEPS = 64;

    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public Rect withX(int newX) {
            return new Rect(newX, y, w, h);
        }

        public Rect withY(int newY) {
            return new Rect(x, newY, w, h);
        }

        /// True when the two boxes are closer than {@code gap} pixels (touching counts as apart).
        public boolean overlaps(Rect o, int gap) {
            return x < o.right() + gap && o.x < right() + gap && y < o.bottom() + gap && o.y < bottom() + gap;
        }
    }

    /// The layout the HUD uses this frame.
    public static final HudLayout FRAME = new HudLayout();

    private final List<Rect> placed = new ArrayList<>();
    private int screenW = 0;
    private int screenH = 0;

    public void beginFrame(int screenWidth, int screenHeight) {
        placed.clear();
        screenW = screenWidth;
        screenH = screenHeight;
        previousFrame = currentFrame;
        currentFrame = new ArrayList<>();
    }

    /// One placement: what was asked, what was already on screen, and where it went. Frames are
    /// nearly always the same as the one before, so a placement is reused when nothing changed
    /// and the (sometimes slow) search only runs when a box really moves or changes size.
    private record Placement(Rect wanted, boolean fromBottom, boolean fromRight, int screenW, int screenH, List<Rect> taken, Rect result) {}
    private List<Placement> previousFrame = new ArrayList<>();
    private List<Placement> currentFrame = new ArrayList<>();

    /// A fixed area other boxes must stay out of (it is never moved).
    public void reserve(Rect rect) {
        if (rect.w() > 0 && rect.h() > 0) placed.add(rect);
    }

    public List<Rect> placed() {
        return List.copyOf(placed);
    }

    /**
     * Finds where a box goes and claims that space. {@code fromBottom}/{@code fromRight} say
     * which screen edge the box is anchored to, so it is pushed away from that edge (a box at the
     * top moves down past whatever is in the way, one at the bottom moves up).
     */
    public Rect place(Rect wanted, boolean fromBottom, boolean fromRight) {
        int index = currentFrame.size();
        Rect result = null;
        if (index < previousFrame.size()) {
            Placement last = previousFrame.get(index);
            if (last.wanted().equals(wanted) && last.fromBottom() == fromBottom && last.fromRight() == fromRight
                    && last.screenW() == screenW && last.screenH() == screenH && last.taken().equals(placed)) {
                result = last.result();
            }
        }
        if (result == null) result = resolve(wanted, fromBottom, fromRight, screenW, screenH, placed);
        currentFrame.add(new Placement(wanted, fromBottom, fromRight, screenW, screenH, List.copyOf(placed), result));
        placed.add(result);
        return result;
    }

    /// Keeps the box inside the screen. A box bigger than the screen sticks to the top/left edge.
    public static Rect clamp(Rect r, int screenW, int screenH) {
        int x = Math.max(0, Math.min(r.x(), screenW - r.w()));
        int y = Math.max(0, Math.min(r.y(), screenH - r.h()));
        return new Rect(x, y, r.w(), r.h());
    }

    static Rect resolve(Rect wanted, boolean fromBottom, boolean fromRight, int screenW, int screenH, List<Rect> taken) {
        Rect start = clamp(wanted, screenW, screenH);
        if (firstHit(start, taken) == null) return start;

        // 1. Same x, the closest free height above or below: the box keeps its place and only
        //    moves up or down as little as it takes to clear what's in the way.
        Rect r = nearestVertical(start, fromBottom, screenH, taken);
        if (r != null) return r;
        // 2. No free height in this column: sideways, towards the middle of the screen.
        r = pushHorizontal(start, !fromRight, screenW, taken);
        if (r != null) return r;
        // 3. The other sideways direction.
        r = pushHorizontal(start, fromRight, screenW, taken);
        if (r != null) return r;
        // 4. Crowded screen: the free spot nearest to where the box wants to be.
        r = nearestFreeSpot(start, screenW, screenH, taken);
        if (r != null) return r;
        // No free spot big enough (tiny window or huge boxes): stay on screen at least.
        return start;
    }

    /// Step through the screen on a small grid and take the free position closest to {@code r}.
    private static Rect nearestFreeSpot(Rect r, int screenW, int screenH, List<Rect> taken) {
        final int step = 4;
        Rect best = null;
        long bestDistance = Long.MAX_VALUE;
        int maxX = screenW - r.w();
        int maxY = screenH - r.h();
        if (maxX < 0 || maxY < 0) return null;
        for (int y = 0; y <= maxY; y = nextGrid(y, step, maxY)) {
            for (int x = 0; x <= maxX; x = nextGrid(x, step, maxX)) {
                long dx = x - r.x(), dy = y - r.y();
                long distance = dx * dx + dy * dy;
                if (distance >= bestDistance) continue;
                Rect candidate = new Rect(x, y, r.w(), r.h());
                if (firstHit(candidate, taken) == null) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /// Next grid position; the last one is always the far edge itself.
    private static int nextGrid(int value, int step, int max) {
        if (value == max) return max + 1;
        return Math.min(value + step, max);
    }

    /**
     * The free position with the same x whose height is closest to {@code r}'s. The closest free
     * height is always right under or right over one of the boxes in this column, so only those
     * heights are tried. On a tie it moves away from its anchored edge (down for top boxes).
     */
    private static Rect nearestVertical(Rect r, boolean fromBottom, int screenH, List<Rect> taken) {
        Rect best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Rect t : taken) {
            if (!(r.x() < t.right() + GAP && t.x() < r.right() + GAP)) continue; // not in this column
            for (int y : new int[]{t.bottom() + GAP, t.y() - GAP - r.h()}) {
                if (y < 0 || y + r.h() > screenH) continue;
                int distance = Math.abs(y - r.y());
                boolean awayFromEdge = fromBottom ? y < r.y() : y > r.y();
                if (distance > bestDistance) continue;
                if (distance == bestDistance && !awayFromEdge) continue;
                Rect candidate = r.withY(y);
                if (firstHit(candidate, taken) != null) continue;
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static Rect pushHorizontal(Rect r, boolean right, int screenW, List<Rect> taken) {
        for (int i = 0; i < MAX_STEPS; i++) {
            Rect hit = firstHit(r, taken);
            if (hit == null) return r;
            r = r.withX(right ? hit.right() + GAP : hit.x() - GAP - r.w());
            if (r.x() < 0 || r.right() > screenW) return null;
        }
        return null;
    }

    private static Rect firstHit(Rect r, List<Rect> taken) {
        // Nearest blocking box first keeps the push steps small and predictable.
        Rect best = null;
        for (Rect t : taken) {
            if (r.overlaps(t, GAP) && (best == null || t.y() < best.y() || (t.y() == best.y() && t.x() < best.x()))) best = t;
        }
        return best;
    }
}
