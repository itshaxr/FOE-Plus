package dannypx.foe.type;

/// How the small markers on items look: FOE+'s own (FOE-R's rarity dot, the held-item star in the
/// amulet's colour, the rating letter), or FOE's (the rarity corner, the rating's coloured corner; the same
/// amulet-coloured star) for players coming from FOE.
public enum IconStyle implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
    FOE_PLUS("FOE+"),
    FOE("FOE");

    private final String displayName;

    IconStyle(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public IconStyle next() {
        return values()[(ordinal() + 1) % values().length];
    }

    @Override
    public String prefix() {
        return "fishonmcextras.icon_style";
    }

    @Override
    public String toString() {
        return displayName;
    }
}
