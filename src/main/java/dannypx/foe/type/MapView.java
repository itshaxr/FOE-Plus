package dannypx.foe.type;

/// How the Locations menu (/tt) is shown: the server's plain menu, the first painted map, or the map
/// built from the places themselves. Picked with "Change view" on the map; Realistic until changed.
public enum MapView implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
    CLASSIC("Classic"),
    FANTASY("Fantasy"),
    REALISTIC("Realistic");

    private final String displayName;

    MapView(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String prefix() {
        return "fishonmcextras.map_view";
    }

    @Override
    public String toString() {
        return displayName;
    }
}
