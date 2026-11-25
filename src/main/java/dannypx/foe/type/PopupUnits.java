package dannypx.foe.type;

/// Which units the catch popup gives a fish's weight and length in.
public enum PopupUnits implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
    BOTH("Both"),
    IMPERIAL("lb / in"),
    METRIC("kg / cm");

    private final String displayName;

    PopupUnits(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public PopupUnits next() {
        return values()[(ordinal() + 1) % values().length];
    }

    @Override
    public String prefix() {
        return "fishonmcextras.popup_units";
    }

    @Override
    public String toString() {
        return displayName;
    }
}
