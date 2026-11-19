package dannypx.foe.type;

/// How the chummer sits in the top bar: its picture with the rarity's badge over the time (the default), a short
/// badge (its first letter), the picture and the time in one line, or the short badge on the picture's corner.
public enum ChummerStyle implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
    BADGE_OVER_TIME("Badge over time"),
    SHORT_BESIDE("Short badge beside"),
    SHORT_CORNER("Short badge on corner");

    private final String displayName;

    ChummerStyle(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String prefix() {
        return "fishonmcextras.chummer_style";
    }

    @Override
    public String toString() {
        return displayName;
    }
}
