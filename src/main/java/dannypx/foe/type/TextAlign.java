package dannypx.foe.type;

/// How a panel's lines sit: following the corner it's anchored to (right when on the right), or always left,
/// centred or right.
public enum TextAlign implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
    AUTO("Follow corner"),
    LEFT("Left"),
    CENTER("Centre"),
    RIGHT("Right");

    private final String displayName;

    TextAlign(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public TextAlign next() {
        return values()[(ordinal() + 1) % values().length];
    }

    @Override
    public String prefix() {
        return "fishonmcextras.text_align";
    }

    @Override
    public String toString() {
        return displayName;
    }
}
