package dannypx.foe.type;

/// Your 3D bobber's shape (O → Bobber): FOE+'s block bobber, or a round one like a real float. Every colour preset
/// works on both.
public enum BobberShape implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
    CLASSIC("Classic"),
    ROUND("Round");

    private final String displayName;

    BobberShape(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public BobberShape next() {
        return values()[(ordinal() + 1) % values().length];
    }

    @Override
    public String prefix() {
        return "fishonmcextras.bobber_shape";
    }

    @Override
    public String toString() {
        return displayName;
    }
}
