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
    /// {@code look} is what the item's tooltip says apart from how many it holds (see {@link #look}): a lure the server
    /// gives no colour or size (a Volcano Popper) still merges with one whose name and tooltip are the same.
    public record Entry(int slot, String type, String name, String rarity, String color, String size, int amount, String look, String data) {
        public Entry(int slot, String type, String name, String rarity, String color, String size, int amount, String look) {
            this(slot, type, name, rarity, color, size, amount, look, "");
        }

        public Entry(int slot, String type, String name, String rarity) {
            this(slot, type, name, rarity, "", "", 0, "");
        }

        public Entry(int slot, String type, String name, String rarity, String color, String size) {
            this(slot, type, name, rarity, color, size, 0, "");
        }

        public Entry(int slot, String type, String name, String rarity, String color, String size, int amount) {
            this(slot, type, name, rarity, color, size, amount, "");
        }

        /// What must be equal for two entries to merge, or null when this entry never merges.
        public String mergeKey() {
            if (BAIT.equals(type)) return name;
            // a lure's own data, what makes it that lure ({@link #LURE_FIELDS}): two alike are one lure, whatever their
            // tooltips say (their uses-left lines are in small capitals, which the tooltip compare didn't see past)
            if (LURE.equals(type) && data != null && !data.isEmpty()) return "data|" + data;
            // a lure's name, colour and size each as read, whatever codes the server wrote them with (older and newer
            // lures write them differently, and only the colour used to be read past its codes)
            String n = plainColor(name), c = plainColor(color), z = plainColor(size);
            if (LURE.equals(type) && !c.isEmpty() && !z.isEmpty()) return n + "|" + c + "|" + z;
            if (LURE.equals(type) && !n.isEmpty() && look != null && !look.isEmpty()) return n + "|look|" + look;
            return null;
        }
    }

    /// Package-private for tests: a tooltip's lines as one text to compare, leaving out the line that says how many
    /// the stack holds (one with that number in it, or one about uses / stock / what's left).
    public static String look(List<String> lines, int amount) {
        StringBuilder b = new StringBuilder();
        String n = String.valueOf(amount);
        for (String line : lines) {
            String t = plainColor(line).replaceAll("\\s+", " ").trim();
            if (t.isEmpty()) continue;
            if (t.matches(".*\\b(uses?|stock|left|remaining|durability|amount|quantity)\\b.*")) continue;
            if (amount > 0 && (" " + t.replace(",", "") + " ").matches(".*[^0-9.]" + n + "[^0-9.].*")) continue;
            b.append(t).append('\n');
        }
        return b.toString();
    }

    /// The fields of a lure's data that say which lure it is (not how many uses it has left, nor its rolls).
    public static final List<String> LURE_FIELDS = List.of("name", "rarity", "color", "size", "intricacy", "water");

    /// Which lure it is, from its data (`get` reads a field): its {@link #LURE_FIELDS}, each as read. Two lures with
    /// the same key are the same lure (the sorter merges them, and one on the cursor shows a + over the other).
    public static String lureKey(java.util.function.Function<String, String> get) {
        StringBuilder b = new StringBuilder();
        for (String k : LURE_FIELDS) {
            String v = get.apply(k);
            b.append(k).append('=').append(plainValue(v == null ? "" : v)).append('|');
        }
        return b.toString();
    }

    /// A text value as compared between two lures: as read (codes and small capitals made plain, see {@link #plainColor}).
    public static String plainValue(String value) {
        String t = plainColor(value);
        StringBuilder b = new StringBuilder(t.length());
        t.codePoints().forEach(cp -> b.appendCodePoint(cp < 0x10000 ? Character.toLowerCase(TextHelper.normalLetter((char) cp)) : cp));
        return b.toString();
    }

    /// A lure's colour as you read it ("Redhead"), whichever way the server wrote it: older lures carry it
    /// as "§x§f§b§4§7§2§fR§x...", newer ones as "<#FB472F>R<#F46B59>e...", so two of the same lure only
    /// matched (and merged) when both were the same age. Package-private for tests.
    public static String plainColor(String color) {
        if (color == null) return "";
        return color.replaceAll("<#[0-9A-Fa-f]{6}>|</?[a-zA-Z_:#0-9]+>", "")
                .replaceAll("§x(§[0-9A-Fa-f]){6}", "")
                .replaceAll("&x(&[0-9A-Fa-f]){6}", "")
                .replaceAll("[&{]#[0-9A-Fa-f]{6}}?", "")
                .replaceAll("§.", "")
                .replaceAll("&[0-9A-Fa-fK-Ok-oRr]", "")
                .replaceAll("\\s+", " ")
                .trim().toLowerCase(java.util.Locale.ROOT);
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
