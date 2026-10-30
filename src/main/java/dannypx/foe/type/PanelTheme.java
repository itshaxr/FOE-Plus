package dannypx.foe.type;

/// Border style for the FOE-style info panels (contest, quests, daily quests, all-time stats).
/// Textures ported from FOECollab (AGPL-3.0).
public enum PanelTheme {
    OFF("off"),
    CLEAN_SQUARE("clean_square"),
    CLEAN_ROUNDED("clean_rounded"),
    CLEAN_SQUARE_THICK("clean_square_thick"),
    CLEAN_ROUNDED_THICK("clean_rounded_thick");

    public final String id;

    PanelTheme(String id) {
        this.id = id;
    }
}
