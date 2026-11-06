package dannypx.foe.type;

/// When inspect info (pet %, fish details, armor rolls) shows without holding the inspect key.
public enum InspectAlways {
    OFF("Hold key"),
    ALWAYS("Always On"),
    PETS("Pets Only"),
    ARMOR("Armor Only");

    private final String displayName;

    InspectAlways(String displayName) {
        this.displayName = displayName;
    }

    /// What an item is, for inspect.
    public enum Kind { PET, ARMOR, OTHER }

    /// True when this mode shows inspect info for {@code kind} without the key.
    public boolean covers(Kind kind) {
        return switch (this) {
            case OFF -> false;
            case ALWAYS -> true;
            case PETS -> kind == Kind.PET;
            case ARMOR -> kind == Kind.ARMOR;
        };
    }

    public InspectAlways next() {
        InspectAlways[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    @Override
    public String toString() {
        return displayName;
    }
}
