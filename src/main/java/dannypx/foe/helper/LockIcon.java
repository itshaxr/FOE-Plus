package dannypx.foe.helper;

import net.minecraft.client.gui.GuiGraphics;

/// A small red padlock drawn with pixels (no font glyph that some packs lack): the bait lock on the bait boxes.
public final class LockIcon {
    private LockIcon() {}

    static final String[] MASK = {
            "..###..",
            ".#...#.",
            ".#...#.",
            "#######",
            "###.###",
            "###.###",
            "#######"};
    public static final int SIZE = 7;
    static final int RED = 0xFFFF3B3B, DARK_RED = 0xFFB01818, OUTLINE = 0xFF1A0505, HOLE = 0xFF2A0808;

    /// The lock with its top-left at (x, y), each of its pixels `px` GUI pixels; (SIZE + 2) * px across with the outline.
    public static void draw(GuiGraphics g, int x, int y, int px) {
        px = Math.max(1, px);
        // the dark outline: every empty cell next to a filled one
        for (int r = -1; r <= SIZE; r++) {
            for (int c = -1; c <= SIZE; c++) {
                if (filled(r, c)) continue;
                if (filled(r - 1, c) || filled(r + 1, c) || filled(r, c - 1) || filled(r, c + 1)) {
                    g.fill(x + (c + 1) * px, y + (r + 1) * px, x + (c + 2) * px, y + (r + 2) * px, OUTLINE);
                }
            }
        }
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                boolean body = r >= 3;
                int colour;
                if (filled(r, c)) colour = body && c >= 4 ? DARK_RED : RED;
                else if (body) colour = HOLE;      // the keyhole
                else continue;
                g.fill(x + (c + 1) * px, y + (r + 1) * px, x + (c + 2) * px, y + (r + 2) * px, colour);
            }
        }
    }

    private static boolean filled(int r, int c) {
        return r >= 0 && r < SIZE && c >= 0 && c < SIZE && MASK[r].charAt(c) == '#';
    }
}
