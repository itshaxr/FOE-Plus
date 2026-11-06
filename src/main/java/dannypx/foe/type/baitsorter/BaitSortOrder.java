package dannypx.foe.type.baitsorter;

/// How the bait sorter (B) and the vault sort buttons order things. Ported from FOECollab's
/// BaitSorterConfig.SortOrder; FOE+ added the amount orders. The vault's Amount and Rarity
/// buttons set this too, so B keeps whatever order you picked last.
public enum BaitSortOrder {
    COMMON_TO_LEGENDARY("Common > Legendary"),
    LEGENDARY_TO_COMMON("Legendary > Common"),
    MOST_TO_FEWEST("Most > Fewest"),
    FEWEST_TO_MOST("Fewest > Most"),
    /// By kind (the last word, so every Crankbait sits together), then by full name.
    NAME_A_TO_Z("Name A > Z"),
    NAME_Z_TO_A("Name Z > A");

    private final String displayName;

    BaitSortOrder(String displayName) {
        this.displayName = displayName;
    }

    public boolean byAmount() {
        return this == MOST_TO_FEWEST || this == FEWEST_TO_MOST;
    }

    public boolean byName() {
        return this == NAME_A_TO_Z || this == NAME_Z_TO_A;
    }

    /// "Firetiger Crankbait" → "crankbait firetiger crankbait": the kind first, so a name sort
    /// groups every Crankbait (or Jig, Spinnerbait...) together.
    public static String nameKey(String name) {
        String clean = name == null ? "" : name.replaceAll("§.", "").trim().toLowerCase(java.util.Locale.ROOT);
        int space = clean.lastIndexOf(' ');
        return (space < 0 ? clean : clean.substring(space + 1)) + " " + clean;
    }

    /// The order in the vault buttons' terms: Amount = most first and Rarity = rarest first,
    /// reversed for Shift-click.
    public boolean isVaultReverse() {
        return this == FEWEST_TO_MOST || this == COMMON_TO_LEGENDARY || this == NAME_Z_TO_A;
    }

    public static BaitSortOrder fromVaultName(boolean reverse) {
        return reverse ? NAME_Z_TO_A : NAME_A_TO_Z;
    }

    public static BaitSortOrder fromVault(boolean amount, boolean reverse) {
        if (amount) return reverse ? FEWEST_TO_MOST : MOST_TO_FEWEST;
        return reverse ? COMMON_TO_LEGENDARY : LEGENDARY_TO_COMMON;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
