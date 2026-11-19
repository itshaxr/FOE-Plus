package dannypx.foe.helper;

/// The colours the server gives each level (its tab list and chat), by bracket of levels. Ported from FOECollab's
/// LevelColors (AGPL-3.0).
public final class LevelColors {
    private LevelColors() {}

    /// {first level, last level, colour}.
    private static final int[][] BRACKETS = {
            {0, 4, 0x8A897E}, {5, 19, 0x868A6E}, {20, 34, 0xBBCF53}, {35, 49, 0xE8D41E}, {50, 64, 0xE8A81E},
            {65, 79, 0x7FD223}, {80, 94, 0x23F133}, {95, 109, 0x1EE8B2}, {110, 124, 0x1EB6E8}, {125, 139, 0x1958E9},
            {140, 154, 0x411DE8}, {155, 169, 0x9B1EE8}, {170, 184, 0xBE12D2}, {185, 199, 0xE81ED4}, {200, 209, 0xE81E61},
            {210, 219, 0xAC1648}, {220, 229, 0x83232B}, {230, 239, 0x71316B}, {240, 249, 0xA00950}, {250, 259, 0xD03EC2},
            {260, 269, 0xE675DB}, {270, 279, 0xDE9DD8}, {280, 289, 0xE4C1E1}, {290, 299, 0xE1C9DF},
            {300, Integer.MAX_VALUE, 0xFFFEFA}};

    /// The colour of this level (white for one below 0).
    public static int of(int level) {
        for (int[] b : BRACKETS) if (level >= b[0] && level <= b[1]) return b[2];
        return 0xFFFFFF;
    }
}
