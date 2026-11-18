package dannypx.foe.type;

/// How FOE+'s info panels look: FOE+'s own (the FOE-style border with the title set into it), or FOE-R's
/// (a see-through box between the dark sidebar bars, the title in small caps on the first line).
public enum HudStyle implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
    FOE_PLUS("FOE+"),
    FOE_R("FOE-R");

    private final String displayName;

    HudStyle(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public HudStyle next() {
        return values()[(ordinal() + 1) % values().length];
    }

    @Override
    public String prefix() {
        return "fishonmcextras.hud_style";
    }

    @Override
    public String toString() {
        return displayName;
    }
}
