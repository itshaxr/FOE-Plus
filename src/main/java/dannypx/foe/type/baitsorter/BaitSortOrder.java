package dannypx.foe.type.baitsorter;

/// Which rarity the bait sorter puts first. Ported from FOECollab's BaitSorterConfig.SortOrder.
public enum BaitSortOrder {
    COMMON_TO_LEGENDARY("Common > Legendary"),
    LEGENDARY_TO_COMMON("Legendary > Common");

    private final String displayName;

    BaitSortOrder(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
