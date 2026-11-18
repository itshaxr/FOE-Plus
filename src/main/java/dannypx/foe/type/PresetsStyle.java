package dannypx.foe.type;

/// How the rows of the Fishing Presets panel look, or CLASSIC: the server's own menu, with just the Style menu over
/// it to come back. Picked with the Style menu; B until changed.
public enum PresetsStyle implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
    A("Style A"),
    B("Style B"),
    C("Style C"),
    CLASSIC("Classic");

    private final String displayName;

    PresetsStyle(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String prefix() {
        return "fishonmcextras.presets_style";
    }

    @Override
    public String toString() {
        return displayName;
    }
}
