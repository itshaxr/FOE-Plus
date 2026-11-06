package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.helper.SortPlanner;
import dannypx.foe.item.TagObject;
import dannypx.foe.item.ValidateItem;
import dannypx.foe.type.baitsorter.BaitSortOrder;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Sorts the baits and lures on the open Personal Vault page by amount, rarity or name. Pets and
 * every other item stay exactly where they are; baits fill the free slots around them. Only the
 * 45 item slots are touched (the bottom row holds the page arrows). The moves go through the bait sorter's paced click
 * queue, so they follow its click speed setting and stop if you close the vault.
 */
public final class VaultSortHandler {
    private VaultSortHandler() {}

    public enum Mode { AMOUNT, RARITY, NAME }

    /// Vault item slots: rows 1-5 of the 6-row vault.
    static final int ITEM_SLOTS = 45;

    /// One item in the vault. Compared by identity while planning. {@code movable}: a bait or
    /// lure (the only things the sort moves).
    record Item(int slot, String name, int amount, int rarityRank, boolean movable) {
        Item(int slot, String name, int amount, int rarityRank) {
            this(slot, name, amount, rarityRank, true);
        }
    }

    /// Starts a sort of the open vault page; {@code reverse} flips the order (Shift-click). The
    /// choice is saved as the bait sorter's order, so pressing B afterwards keeps it.
    public static boolean sort(Mode mode, boolean reverse) {
        Minecraft mc = Minecraft.getInstance();
        BaitSortOrder order = mode == Mode.NAME ? BaitSortOrder.fromVaultName(reverse) : BaitSortOrder.fromVault(mode == Mode.AMOUNT, reverse);
        boolean started = BaitSorterHandler.instance().queueSort(mc, planner(order));
        if (started && Configs.keyBindConfig.baitSortOrder.get() != order) {
            Configs.keyBindConfig.baitSortOrder.accept(order);
            Configs.keyBindConfig.save();
        }
        return started;
    }

    /// A sort of the open vault page in {@code order}, planned when it runs.
    public static Supplier<List<List<SortPlanner.Click>>> planner(BaitSortOrder order) {
        Mode mode = order.byName() ? Mode.NAME : order.byAmount() ? Mode.AMOUNT : Mode.RARITY;
        boolean reverse = order.isVaultReverse();
        return () -> {
            List<Integer> slots = new ArrayList<>();
            List<Item> items = scan(slots);
            return plan(items, slots, mode, reverse);
        };
    }

    public static boolean isSorting() {
        return BaitSorterHandler.instance().isSorting();
    }

    /// Items in the vault's item slots; fills {@code slots} with all item slot ids, in order.
    private static List<Item> scan(List<Integer> slots) {
        List<Item> items = new ArrayList<>();
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)
                || !(screen.getMenu() instanceof ChestMenu)) return items;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof Inventory || slot.index >= ITEM_SLOTS) continue;
            slots.add(slot.index);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            int amount = stack.getCount();
            String rarity = "";
            String type = "";
            Pair<Boolean, TagObject> tag = ValidateItem.isServerItem(stack, true);
            if (tag.value1() && tag.value2() != null) {
                amount = tag.value2().getCount();
                rarity = tag.value2().getRarity();
                type = tag.value2().getType();
            }
            boolean bait = SortPlanner.BAIT.equals(type) || SortPlanner.LURE.equals(type);
            items.add(new Item(slot.index, stack.getHoverName().getString(), amount, rarityRank(rarity), bait));
        }
        slots.sort(Comparator.naturalOrder());
        return items;
    }

    /// Higher is rarer; unknown rarities sort last.
    static int rarityRank(String rarity) {
        return switch (rarity == null ? "" : rarity.toLowerCase(Locale.ROOT)) {
            case "special" -> 6;
            case "mythical", "mythic" -> 5;
            case "legendary" -> 4;
            case "epic" -> 3;
            case "rare" -> 2;
            case "common" -> 1;
            default -> 0;
        };
    }

    /// Order: Amount = most first; Rarity = rarest first. Ties by name, then the other key, then
    /// slot, so the result is stable. Items are packed into the first slots. Package-private for tests.
    static List<List<SortPlanner.Click>> plan(List<Item> items, List<Integer> slots, Mode mode, boolean reverse) {
        Comparator<Item> byAmount = Comparator.comparingInt(Item::amount).reversed();
        Comparator<Item> byRarity = Comparator.comparingInt(Item::rarityRank).reversed();
        Comparator<Item> byName = Comparator.comparing((Item i) -> BaitSortOrder.nameKey(i.name()));
        Comparator<Item> order = switch (mode) {
            case AMOUNT -> byAmount.thenComparing(byRarity);
            case RARITY -> byRarity.thenComparing(byAmount);
            case NAME -> byName.thenComparing(byRarity);
        };
        if (reverse) order = order.reversed();
        order = order.thenComparing(Item::name).thenComparingInt(Item::slot);

        // Only baits and lures move. Pets and everything else keep their slots, and the baits go
        // into the slots that are left (theirs and the empty ones), in order.
        List<Integer> free = new ArrayList<>();
        java.util.Set<Integer> fixed = new java.util.HashSet<>();
        for (Item item : items) if (!item.movable()) fixed.add(item.slot());
        for (int slot : slots) if (!fixed.contains(slot)) free.add(slot);
        List<Item> desired = new ArrayList<>();
        for (Item item : items) if (item.movable()) desired.add(item);
        desired.sort(order);
        Map<Integer, Item> state = new HashMap<>();
        for (Item item : items) if (item.movable()) state.put(item.slot(), item);
        return SortPlanner.arrange(state, desired, free);
    }
}
