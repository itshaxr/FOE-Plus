package dannypx.foe.helper;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * FishOnMC locations with the IDs the server puts in pet NBT and their climates (the climate IDs
 * armor and pets use). Ported from FOECollab's Constant / LocationInfo / ClimateConstant (AGPL-3.0).
 */
public final class FoeLocations {
    private FoeLocations() {}

    public enum Climate {
        SUBTROPICAL("subtropical", "Subtropical", 0x4FB07A, 0x47F7D6),
        SUBARCTIC("subarctic", "Subarctic", 0x53A1C1, 0x98B7CA),
        SEMI_ARID("semi-arid", "Semi-Arid", 0xE6902E, 0xE4B76D),
        SAVANNA("savanna", "Savanna", 0xBAC153, 0xE2DD7E),
        CONTINENTAL("continental", "Continental", 0xA4A9AB, 0xE5EBE8),
        RAINFOREST("rainforest", "Rainforest", 0x569579, 0x2ADD8E),
        MEDITERRANEAN("mediterranean", "Mediterranean", 0x80C4EF, 0xB0D8F3),
        OCEANIC("oceanic", "Oceanic", 0x397FAC, 0x319CE3),
        MONSOON("monsoon", "Monsoon", 0x6141DF, 0x7B93E2),
        ARID("arid", "Arid", 0xFD3535, 0xC12D2D);

        public final String id;
        public final String name;
        private final int from;
        private final int to;

        Climate(String id, String name, int from, int to) {
            this.id = id;
            this.name = name;
            this.from = from;
            this.to = to;
        }

        /// The climate name in FOE's two-colour gradient.
        public Component tag() {
            MutableComponent out = Component.empty();
            int n = name.length();
            for (int i = 0; i < n; i++) {
                float t = n == 1 ? 0 : (float) i / (n - 1);
                out.append(Component.literal(String.valueOf(name.charAt(i))).withColor(lerp(from, to, t)));
            }
            return out;
        }

        private static int lerp(int a, int b, float t) {
            int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
            int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
            int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
            return (r << 16) | (g << 8) | bl;
        }
    }

    public enum Location {
        // Each location has exactly one pet: its species id (pet NBT "pet") and name. The species
        // order matches FOECollab's pet constants, which follow the location order; Great Lakes =
        // Eagle and Danube = Wolf as on the server.
        CYPRESS_LAKE("spawn", "cypresslake", Climate.SUBTROPICAL, "bullfrog", "Bullfrog"),
        KENAI_RIVER("kenai", "kenairiver", Climate.SUBARCTIC, "bear", "Bear"),
        LAKE_BIWA("biwa", "lakebiwa", Climate.SUBTROPICAL, "fox", "Fox"),
        MURRAY_RIVER("murray", "murrayriver", Climate.SEMI_ARID, "kangaroo", "Kangaroo"),
        EVERGLADES("everglades", "everglades", Climate.SAVANNA, "marshrabbit", "Marsh Rabbit"),
        KEY_WEST("keywest", "keywest", Climate.SAVANNA, "seaturtle", "Sea Turtle"),
        TOLEDO_BEND("toledobend", "toledobend", Climate.SUBTROPICAL, "duck", "Duck"),
        GREAT_LAKES("greatlakes", "greatlakes", Climate.CONTINENTAL, "eagle", "Eagle"),
        DANUBE_RIVER("danube", "danuberiver", Climate.CONTINENTAL, "wolf", "Wolf"),
        OIL_RIG("oilrig", "oilrig", Climate.SAVANNA, "pelican", "Pelican"),
        AMAZON_RIVER("amazon", "amazonriver", Climate.RAINFOREST, "capybara", "Capybara"),
        MEDITERRANEAN_SEA("mediterranean", "mediterraneansea", Climate.MEDITERRANEAN, "lynx", "Lynx"),
        CAPE_COD("capecod", "capecod", Climate.OCEANIC, "shark", "Shark"),
        HAWAII("hawaii", "hawaii", Climate.SAVANNA, "dolphin", "Dolphin"),
        CAIRNS("cairns", "cairns", Climate.MONSOON, "koala", "Koala"),
        LOFOTEN_ISLANDS("lofotenislands", "lofotenislands", Climate.SUBARCTIC, "sheep", "Sheep"),
        NILE_RIVER("nile", "nileriver", Climate.ARID, "hippo", "Hippo");

        /// ID used in pet NBT ("location").
        public final String id;
        /// Display name with everything but letters removed, for matching the boss bar.
        private final String key;
        public final Climate climate;
        /// Species id of this location's pet (pet NBT "pet").
        public final String petId;
        /// e.g. "Wolf" (the item is called "Wolf Pet").
        public final String petName;

        Location(String id, String key, Climate climate, String petId, String petName) {
            this.id = id;
            this.key = key;
            this.climate = climate;
            this.petId = petId;
            this.petName = petName;
        }
    }

    /// The location for a boss-bar/contest name like "Great Lakes" or "Toledo Bend Reservoir", or null.
    public static Location fromName(String displayName) {
        String name = displayName.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (name.isEmpty()) return null;
        for (Location location : Location.values()) {
            if (name.startsWith(location.key) || location.key.startsWith(name)) return location;
        }
        return null;
    }

    /// The location whose pet NBT id is {@code id} ("danube"), or null.
    public static Location fromId(String id) {
        if (id == null) return null;
        for (Location location : Location.values()) {
            if (location.id.equalsIgnoreCase(id.trim())) return location;
        }
        return null;
    }

    public static Climate climateFromId(String id) {
        if (id == null) return null;
        String key = id.toLowerCase(Locale.ROOT).trim();
        for (Climate climate : Climate.values()) {
            if (climate.id.equals(key)) return climate;
        }
        return null;
    }

    /// FOE's rarity colours, by rarity ID.
    public static int rarityColor(String rarity) {
        return switch (rarity == null ? "" : rarity.toLowerCase(Locale.ROOT)) {
            case "common" -> 0xFFFFFF;
            case "rare" -> 0x2B85C4;
            case "epic" -> 0x1CD832;
            case "legendary" -> 0xD98103;
            case "mythical", "mythic" -> 0xC93832;
            case "special" -> 0xDD7ACF;
            default -> -1;
        };
    }
}
