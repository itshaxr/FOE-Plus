package dannypx.foe.helper;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Salt or fresh: which water each place has and which water each part of your rod is made for. The rod's
 * line, pole, reel and its bait or lure each carry "water" (freshwater, saltwater, or global/any for
 * anywhere). The places' waters are FOECollab's LocationInfo (AGPL-3.0); the Everglades has both.
 */
public final class RodWater {
    private RodWater() {}

    public enum Water {
        FRESH("Freshwater", 0x3F87EF), SALT("Saltwater", 0x86D9E6);

        public final String label;
        public final int color;

        Water(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    /// The water(s) at a place.
    public static Set<Water> at(FoeLocations.Location place) {
        if (place == null) return EnumSet.allOf(Water.class);
        return switch (place) {
            case EVERGLADES -> EnumSet.of(Water.FRESH, Water.SALT);
            case KEY_WEST, OIL_RIG, MEDITERRANEAN_SEA, CAPE_COD, HAWAII, CAIRNS, LOFOTEN_ISLANDS -> EnumSet.of(Water.SALT);
            default -> EnumSet.of(Water.FRESH);
        };
    }

    /// A part's "water" tag: FRESH, SALT, or null for anywhere (global, any, or not set).
    public static Water of(String tag) {
        String t = tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("fresh")) return Water.FRESH;
        if (t.startsWith("salt")) return Water.SALT;
        return null;
    }

    /// One part of the rod: what to call it, and its "water" tag.
    public record Part(String name, String waterTag) {}

    /// The parts that won't work at a place with these waters.
    public static List<Part> wrong(List<Part> parts, Set<Water> here) {
        List<Part> out = new ArrayList<>();
        for (Part p : parts) {
            Water w = of(p.waterTag());
            if (w != null && !here.contains(w)) out.add(p);
        }
        return out;
    }
}
