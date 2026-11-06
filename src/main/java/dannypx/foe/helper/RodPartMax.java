package dannypx.foe.helper;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Whether a rod part's stat roll (its "calibration": Carbon XP, Titanium money, Elite luck, Ultra
 * scale, Extreme prospect, Ivory albino, Onyx melanistic, Gold trophy, Pro bite / reel speed) is
 * the highest its rarity can roll, from the server's table:
 * <pre>
 *              Common Rare Epic Legendary Mythical
 * Carbon/Ti     1-5   4-6  5-9    7-13    15-20   (%)
 * Elite/Ultra/  5-10 12-18 20-28  30-40   45-60
 *   Extreme
 * Ivory/Onyx/   1-3   2-4  3-6    5-9     7-15    (%)
 *   Gold
 * Pro           3-8   4-10 6-12   7-15    13-25
 * </pre>
 * The part's data has {@code calibration:[{calibration:"pro",cur:25,id:"global_reel+speed"}]}
 * and {@code rarity:"Mythical"}.
 */
public final class RodPartMax {
    private RodPartMax() {}

    private static final int[] XP_MONEY = {5, 6, 9, 13, 20};
    private static final int[] LUCK_SCALE_PROSPECT = {10, 18, 28, 40, 60};
    private static final int[] VARIANTS = {3, 4, 6, 9, 15};
    private static final int[] PRO = {8, 10, 12, 15, 25};

    /// The highest roll for this calibration at this rarity, or -1 when either isn't known.
    public static int maxFor(String calibration, String rarity) {
        int r = switch (rarity == null ? "" : rarity.toLowerCase(Locale.ROOT)) {
            case "common" -> 0;
            case "rare" -> 1;
            case "epic" -> 2;
            case "legendary" -> 3;
            case "mythical", "mythic" -> 4;
            default -> -1;
        };
        if (r < 0) return -1;
        int[] table = switch (calibration == null ? "" : calibration.toLowerCase(Locale.ROOT)) {
            case "carbon", "titanium" -> XP_MONEY;
            case "elite", "ultra", "extreme" -> LUCK_SCALE_PROSPECT;
            case "ivory", "onyx", "gold" -> VARIANTS;
            case "pro" -> PRO;
            default -> null;
        };
        return table == null ? -1 : table[r];
    }

    /// What a slot shows: nothing, a maxed part, or a rod with two different max stats.
    public enum Mark { NONE, PART, ROD }

    private static final java.util.Map<CompoundTag, Mark> CACHE = new java.util.IdentityHashMap<>();

    /// For an item's custom data (cached per tag, so it's cheap every frame): a rod part with a
    /// max roll → PART; a rod whose parts have at least two different max stats → ROD.
    public static Mark markFor(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return Mark.NONE;
        synchronized (CACHE) {
            Mark cached = CACHE.get(tag);
            if (cached != null) return cached;
        }
        Mark mark = compute(tag);
        synchronized (CACHE) {
            if (CACHE.size() > 1024) CACHE.clear();
            CACHE.put(tag, mark);
        }
        return mark;
    }

    /// Package-private for tests.
    static Mark compute(CompoundTag tag) {
        String type = tag.getStringOr("type", "").toLowerCase(Locale.ROOT);
        if (type.equals("line") || type.equals("pole") || type.equals("reel")) {
            return maxedStats(tag).isEmpty() ? Mark.NONE : Mark.PART;
        }
        if (!tag.contains("line") && !tag.contains("pole") && !tag.contains("reel")) return Mark.NONE;
        return rodMaxedStats(tag).size() >= 2 ? Mark.ROD : Mark.NONE;
    }

    /// Every max stat on a rod's line, pole and reel (each part is stored as an item).
    public static Set<String> rodMaxedStats(CompoundTag rod) {
        Set<String> out = new LinkedHashSet<>();
        for (String key : new String[]{"line", "pole", "reel"}) {
            for (Tag t : rod.getListOrEmpty(key)) {
                if (!(t instanceof CompoundTag item)) continue;
                CompoundTag data = item.getCompoundOrEmpty("components").getCompoundOrEmpty("minecraft:custom_data");
                out.addAll(maxedStats(data));
            }
        }
        return out;
    }

    /// The calibrations on this part rolled at their max ("pro"), in order; empty when none.
    public static Set<String> maxedStats(CompoundTag part) {
        Set<String> out = new LinkedHashSet<>();
        if (part == null) return out;
        String rarity = part.getStringOr("rarity", "");
        ListTag calibrations = part.getListOrEmpty("calibration");
        for (Tag t : calibrations) {
            if (!(t instanceof CompoundTag c)) continue;
            String kind = c.getStringOr("calibration", "");
            int max = maxFor(kind, rarity);
            double cur = c.getDoubleOr("cur", -1);
            if (max > 0 && cur >= max) out.add(kind.toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
