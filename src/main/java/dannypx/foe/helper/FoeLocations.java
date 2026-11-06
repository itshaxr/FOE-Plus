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

    /// A location's name as the server colours it (from /tt; colours from FOECollab's Constant,
    /// AGPL-3.0): one colour, or one per letter for the gradients (Hawaii, Lofoten Islands, Nile).
    private record NameColors(String name, int[] perChar) {}

    private static final java.util.Map<Location, NameColors> NAME_COLORS = new java.util.EnumMap<>(Location.class);
    static {
        NAME_COLORS.put(Location.CYPRESS_LAKE, new NameColors("Cypress Lake", new int[]{0x5CAE65, 0x5CAE65, 0x5CAE65, 0x5CAE65, 0x5CAE65, 0x5CAE65, 0x5CAE65, 0x5CAE65, 0x5CAE65, 0x5CAE65, 0x5CAE65, 0x5CAE65}));
        NAME_COLORS.put(Location.KENAI_RIVER, new NameColors("Kenai River", new int[]{0x68D499, 0x68D499, 0x68D499, 0x68D499, 0x68D499, 0x68D499, 0x68D499, 0x68D499, 0x68D499, 0x68D499, 0x68D499}));
        NAME_COLORS.put(Location.LAKE_BIWA, new NameColors("Lake Biwa", new int[]{0xFBC0FA, 0xFBC0FA, 0xFBC0FA, 0xFBC0FA, 0xFBC0FA, 0xFBC0FA, 0xFBC0FA, 0xFBC0FA, 0xFBC0FA}));
        NAME_COLORS.put(Location.MURRAY_RIVER, new NameColors("Murray River", new int[]{0xCD5916, 0xCD5916, 0xCD5916, 0xCD5916, 0xCD5916, 0xCD5916, 0xCD5916, 0xCD5916, 0xCD5916, 0xCD5916, 0xCD5916, 0xCD5916}));
        NAME_COLORS.put(Location.EVERGLADES, new NameColors("Everglades", new int[]{0x2EBB8D, 0x2EBB8D, 0x2EBB8D, 0x2EBB8D, 0x2EBB8D, 0x2EBB8D, 0x2EBB8D, 0x2EBB8D, 0x2EBB8D, 0x2EBB8D}));
        NAME_COLORS.put(Location.KEY_WEST, new NameColors("Key West", new int[]{0xFBF17C, 0xFBF17C, 0xFBF17C, 0xFBF17C, 0xFBF17C, 0xFBF17C, 0xFBF17C, 0xFBF17C}));
        NAME_COLORS.put(Location.TOLEDO_BEND, new NameColors("Toledo Bend Reservoir", new int[]{0x99A7D0, 0x99A7D0, 0x99A7D0, 0x99A7D0, 0x99A7D0, 0x99A7D0, 0x99A7D0, 0x99A7D0, 0x99A7D0, 0x99A7D0, 0x99A7D0, 0x99A7D0, 0x99A7D0, 0x99A7D0, 0x99A7D0, 0x99A7D0, 0x99A7D0, 0x99A7D0, 0x99A7D0, 0x99A7D0, 0x99A7D0}));
        NAME_COLORS.put(Location.GREAT_LAKES, new NameColors("Great Lakes", new int[]{0x3CABF3, 0x3CABF3, 0x3CABF3, 0x3CABF3, 0x3CABF3, 0x3CABF3, 0x3CABF3, 0x3CABF3, 0x3CABF3, 0x3CABF3, 0x3CABF3}));
        NAME_COLORS.put(Location.DANUBE_RIVER, new NameColors("Danube River", new int[]{0xFBC598, 0xFBC598, 0xFBC598, 0xFBC598, 0xFBC598, 0xFBC598, 0xFBC598, 0xFBC598, 0xFBC598, 0xFBC598, 0xFBC598, 0xFBC598}));
        NAME_COLORS.put(Location.OIL_RIG, new NameColors("Oil Rig", new int[]{0xFCEB47, 0xFCEB47, 0xFCEB47, 0xFCEB47, 0xFCEB47, 0xFCEB47, 0xFCEB47}));
        NAME_COLORS.put(Location.AMAZON_RIVER, new NameColors("Amazon River", new int[]{0x3EA729, 0x3EA729, 0x3EA729, 0x3EA729, 0x3EA729, 0x3EA729, 0x3EA729, 0x3EA729, 0x3EA729, 0x3EA729, 0x3EA729, 0x3EA729}));
        NAME_COLORS.put(Location.MEDITERRANEAN_SEA, new NameColors("Mediterranean Sea", new int[]{0xF0FB37, 0xF0FB37, 0xF0FB37, 0xF0FB37, 0xF0FB37, 0xF0FB37, 0xF0FB37, 0xF0FB37, 0xF0FB37, 0xF0FB37, 0xF0FB37, 0xF0FB37, 0xF0FB37, 0xF0FB37, 0xF0FB37, 0xF0FB37, 0xF0FB37}));
        NAME_COLORS.put(Location.CAPE_COD, new NameColors("Cape Cod", new int[]{0xBBF5FB, 0xBBF5FB, 0xBBF5FB, 0xBBF5FB, 0xBBF5FB, 0xBBF5FB, 0xBBF5FB, 0xBBF5FB}));
        NAME_COLORS.put(Location.HAWAII, new NameColors("Hawaii", new int[]{0xFB933B, 0xFCB140, 0xEACD4D, 0xB2E66C, 0x75F0A6, 0x35F4EF}));
        NAME_COLORS.put(Location.CAIRNS, new NameColors("Cairns", new int[]{0xA1C2FB, 0xA1C2FB, 0xA1C2FB, 0xA1C2FB, 0xA1C2FB, 0xA1C2FB}));
        NAME_COLORS.put(Location.LOFOTEN_ISLANDS, new NameColors("Lofoten Islands", new int[]{0xCDDAD7, 0xCCDFD2, 0xCCE3CC, 0xCBE8C7, 0xCBECC1, 0xCAF1BC, 0xCFF2C3, 0xCFF2C3, 0xD9F3D0, 0xDEF4D7, 0xE4F5DD, 0xE9F6E4, 0xEEF6EB, 0xF3F7F1, 0xF8F8F8}));
        NAME_COLORS.put(Location.NILE_RIVER, new NameColors("Nile River", new int[]{0xE6EFCB, 0xE6EFCB, 0xE7EDCA, 0xE8EBCA, 0xE9E9C9, 0xE9E6C8, 0xEAE4C7, 0xEBE2C7, 0xECE0C6, 0xECE0C6}));
    }

    /// The server's colour for a location (its first letter's, for the gradients), or -1.
    public static int nameColor(Location location) {
        NameColors c = location == null ? null : NAME_COLORS.get(location);
        return c == null ? -1 : c.perChar()[0];
    }

    /// {@code text} coloured like the location it names, as in /tt: letter by letter along the
    /// full name, so a shortened name ("Toledo") or one with something after it ("Cypress Lake ✓")
    /// keeps the right colours. Text that isn't a location stays aqua.
    public static net.minecraft.network.chat.MutableComponent styledName(String text) {
        if (text == null) text = "";
        Location location = fromName(text);
        NameColors colors = location == null ? null : NAME_COLORS.get(location);
        if (colors == null) return net.minecraft.network.chat.Component.literal(text).withStyle(net.minecraft.ChatFormatting.AQUA);
        net.minecraft.network.chat.MutableComponent out = net.minecraft.network.chat.Component.empty();
        int[] per = colors.perChar();
        String name = colors.name().toLowerCase(Locale.ROOT);
        int color = per[0];
        StringBuilder run = new StringBuilder();
        int runColor = color;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (i < name.length() && Character.toLowerCase(ch) == name.charAt(i)) color = per[i];
            if (color != runColor && run.length() > 0) {
                out.append(net.minecraft.network.chat.Component.literal(run.toString()).withColor(runColor));
                run.setLength(0);
            }
            runColor = color;
            run.append(ch);
        }
        if (run.length() > 0) out.append(net.minecraft.network.chat.Component.literal(run.toString()).withColor(runColor));
        return out;
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

    /// FOE's spawn hub box at Cypress Lake (x 95..145, z -58..38): you're at spawn, not at the
    /// water yet (Big Bear Dock, Thunderwood Marina...), so gear warnings wait.
    public static boolean isCypressHub(double x, double z) {
        return x >= 95 && x <= 145 && z >= -58 && z <= 38;
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
