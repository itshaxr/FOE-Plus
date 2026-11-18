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

    /// LUCK and SCALE: a pet's luck or scale (its climate and location base, together). CLIMATE: an armor
    /// piece's climate, then its quality. ROLLS: what an armor piece's bonus rolls add up to.
    public enum Mode { AMOUNT, RARITY, NAME, PERCENT, LUCK, SCALE, CLIMATE, ROLLS }

    /// What a sort moves: baits and lures (the Bait Sorter), pets (the Pet Sorter) or armor (the Armor Sorter).
    public enum Kind { BAITS, PETS, ARMOR }

    /// Vault item slots: rows 1-5 of the 6-row vault.
    static final int ITEM_SLOTS = 45;

    /// One item in the vault. Compared by identity while planning. {@code movable}: a bait or
    /// lure (the only things the sort moves).
    /// {@code percent}: a pet's percent, or an armor piece's quality. {@code climate}: an armor piece's climate.
    record Item(int slot, String name, int amount, int rarityRank, boolean movable, float percent,
                float luck, float scale, String climate, float rolls) {
        Item(int slot, String name, int amount, int rarityRank, boolean movable, float percent) {
            this(slot, name, amount, rarityRank, movable, percent, 0f, 0f, "", 0f);
        }

        Item(int slot, String name, int amount, int rarityRank, boolean movable) {
            this(slot, name, amount, rarityRank, movable, 0f);
        }

        Item(int slot, String name, int amount, int rarityRank) {
            this(slot, name, amount, rarityRank, true, 0f);
        }
    }

    /// Sorts the pets on the open vault page by percent (highest first), rarity or name;
    /// {@code reverse} flips it (Shift-click). Baits and everything else stay where they are.
    public static boolean sortPets(Mode mode, boolean reverse) {
        return sortKind(Kind.PETS, mode, reverse);
    }

    /// Sorts the armor on the open page by quality, rarity, climate or rolls; everything else stays put.
    public static boolean sortArmor(Mode mode, boolean reverse) {
        return sortKind(Kind.ARMOR, mode, reverse);
    }

    private static boolean sortKind(Kind kind, Mode mode, boolean reverse) {
        // sorting another way ends the saved layout (B goes back to the order it had before)
        VaultLayoutHandler.leaveSavedOrder();
        return BaitSorterHandler.instance().queueSort(Minecraft.getInstance(), () -> {
            List<Integer> slots = new ArrayList<>();
            List<Item> items = scan(slots, kind);
            return plan(items, slots, mode, reverse);
        });
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
        if (order == BaitSortOrder.SAVED_LAYOUT) return VaultLayoutHandler.planner();
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
        return scan(slots, Kind.BAITS);
    }

    private static List<Item> scan(List<Integer> slots, Kind kind) {
        List<Item> items = new ArrayList<>();
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)
                || !(screen.getMenu() instanceof ChestMenu)) return items;
        // The vault's bottom row holds the page arrows; a chest's rows are all items.
        int itemSlots = ITEM_SLOTS;
        if (screen.getMenu() instanceof ChestMenu chest) {
            boolean vault = screen.getTitle().getString().startsWith(
                    dannypx.foe.handler.fetch.GenericContainerScreenHandler.PERSONAL_VAULT_SCREEN_CONTAINER);
            itemSlots = chest.getRowCount() * 9 - (vault ? 9 : 0);
        }
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof Inventory || slot.index >= itemSlots) continue;
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
            boolean pet = "pet".equals(type);
            boolean armor = "armor".equals(type);
            float percent = 0f, luck = 0f, scale = 0f, rolls = 0f;
            String climate = "";
            if (pet) {
                dannypx.foe.item.PetTagObject p = ValidateItem.isPet(tag.value2()).value2();
                percent = p.getTotalPercent();
                luck = (p.getClimatePercentMaxLuck() + p.getLocationPercentMaxLuck()) / 2f;
                scale = (p.getClimatePercentMaxScale() + p.getLocationPercentMaxScale()) / 2f;
            } else if (armor) {
                dannypx.foe.item.ArmorTagObject a = ValidateItem.isArmor(tag.value2()).value2();
                percent = a.contains("quality") ? a.getInt("quality") : 0f;
                climate = a.contains("name") ? a.getString("name") : "";
                rolls = armorRolls(a.getArmorRolls());
            }
            boolean movable = switch (kind) { case PETS -> pet; case ARMOR -> armor; default -> bait; };
            items.add(new Item(slot.index, stack.getHoverName().getString(), amount, rarityRank(rarity),
                    movable, percent, luck, scale, climate, rolls));
        }
        slots.sort(Comparator.naturalOrder());
        return items;
    }

    /// What an armor piece's rolled bonuses add up to (each "+8%" counts 8). Package-private for tests.
    static float armorRolls(net.minecraft.nbt.ListTag bonuses) {
        float sum = 0f;
        for (int i = 0; i < bonuses.size(); i++) {
            net.minecraft.nbt.CompoundTag b = bonuses.getCompound(i).orElse(null);
            if (b == null || !b.getBoolean("rolled").orElse(false)) continue;
            sum += b.getFloat("cur").orElse(0f);
        }
        return sum;
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
        Comparator<Item> byPercent = Comparator.comparingDouble((Item i) -> i.percent()).reversed();
        Comparator<Item> byLuck = Comparator.comparingDouble((Item i) -> i.luck()).reversed();
        Comparator<Item> byScale = Comparator.comparingDouble((Item i) -> i.scale()).reversed();
        Comparator<Item> byClimate = Comparator.comparing((Item i) -> i.climate() == null ? "" : i.climate().toLowerCase(Locale.ROOT));
        Comparator<Item> byRolls = Comparator.comparingDouble((Item i) -> i.rolls()).reversed();
        Comparator<Item> order = switch (mode) {
            case AMOUNT -> byAmount.thenComparing(byRarity);
            case RARITY -> byRarity.thenComparing(byPercent).thenComparing(byAmount);
            case NAME -> byName.thenComparing(byRarity);
            case PERCENT -> byPercent.thenComparing(byRarity);
            case LUCK -> byLuck.thenComparing(byPercent).thenComparing(byRarity);
            case SCALE -> byScale.thenComparing(byPercent).thenComparing(byRarity);
            case CLIMATE -> byClimate.thenComparing(byPercent).thenComparing(byRarity);
            case ROLLS -> byRolls.thenComparing(byPercent).thenComparing(byRarity);
        };
        if (reverse) order = order.reversed();
        order = order.thenComparing(Item::name).thenComparingInt(Item::slot);

        // Only the movable items (baits and lures, or pets) move. Everything else keeps its slot,
        // and the movable items go into the slots that are left (theirs and the empty ones), in order.
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
