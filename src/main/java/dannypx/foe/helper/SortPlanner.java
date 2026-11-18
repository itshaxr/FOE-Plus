// Ported from FOECollab (https://github.com/Kimberlyow/FOECollab), io.github.foecollab.util.SortPlanner.
// Licensed under AGPL-3.0 by its authors; package changed for FishOnMC-Extras-R.
package dannypx.foe.helper;

import dannypx.foe.type.baitsorter.BaitSortOrder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Turns the baits and lures found in a set of slots into a list of click groups. Pure logic with
/// no Minecraft types so it can be unit tested. Every group is a complete operation that leaves
/// the cursor empty, so the caller may stop between groups.
public final class SortPlanner {
    public static final String BAIT = "bait";
    public static final String LURE = "lure";

    /// Rarities from best to worst. Mythical baits and lures merge and sort too (they used to be
    /// left out, so a mythical lure never combined with its twin).
    private static final List<String> RARITY_ORDER = List.of("special", "mythical", "legendary", "epic", "rare", "common");

    private static String rarity(Entry e) {
        String r = e.rarity() == null ? "" : e.rarity().toLowerCase(java.util.Locale.ROOT);
        return r.equals("mythic") ? "mythical" : r;
    }

    /// One bait or lure in a slot. {@code slot} is the container slot id used in click packets.
    /// {@code color} and {@code size} only matter for lures: two lures merge only when name, color and size all match.
    /// {@code amount} is how many baits (or lure uses) the stack holds, for sorting by amount.
    public record Entry(int slot, String type, String name, String rarity, String color, String size, int amount) {
        public Entry(int slot, String type, String name, String rarity) {
            this(slot, type, name, rarity, "", "", 0);
        }

        public Entry(int slot, String type, String name, String rarity, String color, String size) {
            this(slot, type, name, rarity, color, size, 0);
        }

        /// What must be equal for two entries to merge, or null when this entry never merges.
        String mergeKey() {
            if (BAIT.equals(type)) return name;
            if (LURE.equals(type) && !color.isEmpty() && !size.isEmpty()) {
                return name + "|" + color + "|" + size;
            }
            return null;
        }
    }

    /// A click on a slot: a plain pickup click, or a shift click (quick move).
    public record Click(int slot, boolean shift) {
        public static Click pick(int slot) {
            return new Click(slot, false);
        }

        public static Click shift(int slot) {
            return new Click(slot, true);
        }
    }

    private SortPlanner() {}

    public static boolean isSortable(Entry e) {
        return (BAIT.equals(e.type()) || LURE.equals(e.type())) && RARITY_ORDER.contains(rarity(e));
    }

    private static List<Entry> sortable(List<Entry> found) {
        List<Entry> entries = new ArrayList<>();
        for (Entry e : found) {
            if (isSortable(e)) entries.add(e);
        }
        entries.sort(Comparator.comparingInt(Entry::slot));
        return entries;
    }

    /// Merge same-name baits (when {@code merge}) and lay everything out by rarity (when
    /// {@code sort}, common first when {@code commonFirst}) within one group of slots. Merging only works where baits can stack, which
    /// is the player inventory; in a chest or vault pass {@code merge = false}.
    public static List<List<Click>> plan(List<Entry> found, boolean merge, boolean sort, boolean commonFirst) {
        return plan(found, merge, sort, commonFirst ? BaitSortOrder.COMMON_TO_LEGENDARY : BaitSortOrder.LEGENDARY_TO_COMMON);
    }

    /// As above, laid out in {@code order}: by rarity, or by amount (merged stacks count their
    /// total). Ties go rarest first, then baits before lures, then by name.
    public static List<List<Click>> plan(List<Entry> found, boolean merge, boolean sort, BaitSortOrder order) {
        List<Entry> entries = sortable(found);
        List<List<Click>> groups = new ArrayList<>();

        // Merge baits with the same name (lures: same name, color and size) into the lowest slot.
        // Picking one up and clicking another adds their stock (uses for lures) on the server.
        Map<String, Entry> mergeTarget = new HashMap<>();
        Map<Entry, Integer> total = new java.util.IdentityHashMap<>();
        List<Entry> kept = new ArrayList<>();
        List<Integer> freed = new ArrayList<>();
        for (Entry e : entries) {
            String key = e.mergeKey();
            total.put(e, e.amount());
            if (!merge || key == null) {
                kept.add(e);
                continue;
            }
            Entry target = mergeTarget.get(key);
            if (target == null) {
                mergeTarget.put(key, e);
                kept.add(e);
            } else {
                total.merge(target, e.amount(), Integer::sum);
                groups.add(List.of(Click.pick(e.slot()), Click.pick(target.slot())));
                freed.add(e.slot());
            }
        }
        if (!sort) return groups;

        // Lay the survivors out in rarity order in the lowest slots this sort touches.
        List<Integer> slots = new ArrayList<>();
        for (Entry e : kept) slots.add(e.slot());
        slots.addAll(freed);
        slots.sort(Comparator.naturalOrder());

        // RARITY_ORDER lists the rarest first, so a lower index is rarer.
        Comparator<Entry> rarestFirst = Comparator.comparingInt((Entry e) -> RARITY_ORDER.indexOf(rarity(e)));
        Comparator<Entry> mostFirst = Comparator.comparingInt((Entry e) -> -total.getOrDefault(e, 0));
        Comparator<Entry> primary = switch (order) {
            case LEGENDARY_TO_COMMON -> rarestFirst;
            case COMMON_TO_LEGENDARY -> rarestFirst.reversed();
            case MOST_TO_FEWEST -> mostFirst.thenComparing(rarestFirst);
            case FEWEST_TO_MOST -> mostFirst.reversed().thenComparing(rarestFirst);
            case NAME_A_TO_Z -> Comparator.comparing((Entry e) -> BaitSortOrder.nameKey(e.name())).thenComparing(rarestFirst);
            case NAME_Z_TO_A -> Comparator.comparing((Entry e) -> BaitSortOrder.nameKey(e.name())).reversed().thenComparing(rarestFirst);
            case SAVED_LAYOUT -> rarestFirst;
        };
        List<Entry> desired = new ArrayList<>(kept);
        desired.sort(primary
                .thenComparing(e -> BAIT.equals(e.type()) ? 0 : 1)
                .thenComparing(Entry::name)
                .thenComparingInt(Entry::slot));

        Map<Integer, Entry> state = new HashMap<>();
        for (Entry e : kept) state.put(e.slot(), e);
        groups.addAll(arrange(state, desired, slots));
        return groups;
    }

    /// Click groups that move items so {@code desired.get(i)} ends up in {@code slots.get(i)}.
    /// {@code state} maps slot to the item there now (it is updated as the moves are planned).
    /// Items are compared by identity. Moving into an empty slot takes 2 clicks, a swap 3; each
    /// group leaves the cursor empty. Only for slots where items don't stack (vaults, chests of
    /// unique items), since a click on a same-item stack would merge instead of swap.
    public static <T> List<List<Click>> arrange(Map<Integer, T> state, List<T> desired, List<Integer> slots) {
        List<List<Click>> groups = new ArrayList<>();
        for (int i = 0; i < desired.size() && i < slots.size(); i++) {
            int target = slots.get(i);
            T want = desired.get(i);
            T there = state.get(target);
            if (there == want) continue;
            int from = -1;
            for (var en : state.entrySet()) {
                if (en.getValue() == want) {
                    from = en.getKey();
                    break;
                }
            }
            if (from < 0) continue;
            if (there == null) {
                groups.add(List.of(Click.pick(from), Click.pick(target)));
                state.remove(from);
            } else {
                groups.add(List.of(Click.pick(from), Click.pick(target), Click.pick(from)));
                state.put(from, there);
            }
            state.put(target, want);
        }
        return groups;
    }

    /// For every bait in the inventory that has same-name baits (or same name, color and size
    /// lures) in the chest or vault: pick each chest twin up and click it onto the inventory bait
    /// (the stock adds up there), then shift click the merged bait back into the chest. The merges
    /// and the shift click are separate groups so the server can answer in between. Chest baits
    /// can't merge with each other directly, but every twin of one merges through the inventory.
    public static List<List<Click>> planCrossMerge(List<Entry> chest, List<Entry> inventory) {
        Map<String, List<Entry>> inChest = new HashMap<>();
        for (Entry e : sortable(chest)) {
            if (e.mergeKey() != null) inChest.computeIfAbsent(e.mergeKey(), k -> new ArrayList<>()).add(e);
        }
        List<List<Click>> groups = new ArrayList<>();
        java.util.Set<String> done = new java.util.HashSet<>();
        for (Entry inv : sortable(inventory)) {
            String key = inv.mergeKey();
            List<Entry> twins = key != null ? inChest.get(key) : null;
            if (twins == null || !done.add(key)) continue;
            for (Entry twin : twins) groups.add(List.of(Click.pick(twin.slot()), Click.pick(inv.slot())));
            groups.add(List.of(Click.shift(inv.slot())));
        }
        return groups;
    }

    /// Chest or vault baits with a twin there but none in the inventory: one of each is shift
    /// clicked into the inventory (at most {@code room}, the empty inventory slots), so the next
    /// cross merge can combine all of them.
    public static List<List<Click>> planPullTwins(List<Entry> chest, List<Entry> inventory, int room) {
        java.util.Set<String> mine = new java.util.HashSet<>();
        for (Entry e : sortable(inventory)) if (e.mergeKey() != null) mine.add(e.mergeKey());
        Map<String, List<Entry>> inChest = new java.util.LinkedHashMap<>();
        for (Entry e : sortable(chest)) {
            if (e.mergeKey() != null) inChest.computeIfAbsent(e.mergeKey(), k -> new ArrayList<>()).add(e);
        }
        List<List<Click>> groups = new ArrayList<>();
        for (var en : inChest.entrySet()) {
            if (groups.size() >= room) break;
            if (en.getValue().size() < 2 || mine.contains(en.getKey())) continue;
            groups.add(List.of(Click.shift(en.getValue().getFirst().slot())));
        }
        return groups;
    }
}
