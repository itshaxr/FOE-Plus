package dannypx.foe.handler.store;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.logic.LoggerHandler;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Your own price for a single bait ("Salmon Head is worth $2,000 this week"), set in Bait Prices. A bait without one
 * is worth its rarity's price. Kept in config/fishonmcextras/bait_prices.json.
 */
public final class BaitPriceOverrides {
    private BaitPriceOverrides() {}

    private static final Map<String, Long> prices = new HashMap<>();
    private static boolean loaded = false;
    /// For tests: no file.
    static boolean inMemory = false;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("bait_prices.json");
    }

    /// Package-private for tests: a bait's name as it's matched ("§6Small Cut Squid " → "small cut squid").
    static String key(String name) {
        if (name == null) return "";
        String t = net.minecraft.ChatFormatting.stripFormatting(name);
        if (t == null) return "";
        // the Smokin' Shad crankbait is "Sexy Shad" to the server: one price for both names
        return t.replaceAll("[\\uE000-\\uF8FF]", " ").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim()
                .replace("sexy shad", "smokin shad");
    }

    /// Your price for this bait, or null when it goes by its rarity.
    public static Long get(String baitName) {
        load();
        String k = key(baitName);
        return k.isEmpty() ? null : prices.get(k);
    }

    /// Sets your price for this bait; null goes back to its rarity's price.
    public static void set(String baitName, Long price) {
        load();
        String k = key(baitName);
        if (k.isEmpty()) return;
        if (price == null || price < 0) prices.remove(k); else prices.put(k, price);
        save();
    }

    public static int count() {
        load();
        return prices.size();
    }

    public static void clear() {
        load();
        prices.clear();
        save();
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        if (inMemory) return;
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonElement root = JsonParser.parseString(Files.readString(f));
            if (!root.isJsonObject()) return;
            for (var e : root.getAsJsonObject().entrySet()) {
                try { prices.put(e.getKey(), e.getValue().getAsLong()); } catch (RuntimeException ignored) {}
            }
        } catch (Exception e) {
            LoggerHandler.error(e);
        }
    }

    private static void save() {
        if (inMemory) return;
        try {
            JsonObject root = new JsonObject();
            new java.util.TreeMap<>(prices).forEach(root::addProperty);
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception e) {
            LoggerHandler.error(e);
        }
    }
}
