package dannypx.foe.handler.io;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.handler.store.StatsDataHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Brings dry streaks over from a previous FOE (FishOnMC-Extras-Collab) or FOE-R install,
 * without touching any other stat. Looks in this game folder and every other profile/instance
 * FOE-R can find, and uses the most recently saved file.
 * <p>
 * FOE-R keeps, per rarity/size/variant, the fish count at your last catch of it; the dry streak
 * is your fish total minus that. Importing turns each source streak back into that form against
 * this install's fish total, so the streaks read exactly as they did before.
 * <p>
 * FOE's file has the same idea with its own fish count ({@code allFishCaughtCount}), except the
 * Fabled entry, which FOE keeps as a plain counter.
 */
public final class DryStreakImporter {
    private DryStreakImporter() {}

    static final List<String> RARITIES = List.of("common", "rare", "epic", "legendary", "mythical", "special");
    static final List<String> VARIANTS = List.of("albino", "melanistic", "trophy", "fabled");
    static final List<String> SIZES = List.of("baby", "juvenile", "adult", "large", "gigantic");

    /// Dry streaks read from one file: category → field → streak, plus pets and items.
    public record Source(Path file, String kind, long lastModified,
                         Map<String, Map<String, Integer>> fish,
                         Map<String, Map<String, Integer>> pets,
                         Map<String, Integer> items) {
        int count() {
            int n = items.size();
            for (Map<String, Integer> m : fish.values()) n += m.size();
            for (Map<String, Integer> m : pets.values()) n += m.size();
            return n;
        }
    }

    //region Reading
    /// FOE's stats file. Package-private for tests.
    static Source readFoe(Path file, long lastModified, JsonObject foe) {
        int all = getInt(foe, "allFishCaughtCount");
        Map<String, Map<String, Integer>> fish = new LinkedHashMap<>();
        fish.put("rarity", foeMap(foe, "rarityDryStreak", all, RARITIES));
        fish.put("variant", foeMap(foe, "variantDryStreak", all, VARIANTS));
        fish.put("size", foeMap(foe, "fishSizeDryStreak", all, SIZES));
        Map<String, Integer> items = new LinkedHashMap<>();
        if (foe.has("shardDryStreak")) items.put("shard", Math.max(0, all - getInt(foe, "shardDryStreak")));
        return new Source(file, "FOE", lastModified, fish, new LinkedHashMap<>(), items);
    }

    private static Map<String, Integer> foeMap(JsonObject foe, String key, int all, List<String> known) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (!foe.has(key) || !foe.get(key).isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> entry : foe.getAsJsonObject(key).entrySet()) {
            String field = entry.getKey().toLowerCase(Locale.ROOT);
            if (field.equals("mythic")) field = "mythical";
            if (!known.contains(field) || !entry.getValue().isJsonPrimitive()) continue;
            int stored = entry.getValue().getAsInt();
            // FOE stores the fish count at the last catch, except Fabled, which is a counter.
            int streak = field.equals("fabled") ? stored : all - stored;
            out.put(field, Math.max(0, streak));
        }
        return out;
    }

    /// Another FOE-R install's stats file. Package-private for tests.
    static Source readFoer(Path file, long lastModified, StatsDataHandler.StatsDataModel model) {
        Map<String, Map<String, Integer>> fish = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> pets = new LinkedHashMap<>();
        Map<String, Integer> items = new LinkedHashMap<>();
        if (model.fishData != null) model.fishData.forEach((category, fields) -> fish.put(category, foerMap(fields, model.fishTotal)));
        if (model.petData != null) model.petData.forEach((category, fields) -> pets.put(category, foerMap(fields, model.fishTotal)));
        if (model.itemData != null) items.putAll(foerMap(model.itemData, model.fishTotal));
        return new Source(file, "FOE-R", lastModified, fish, pets, items);
    }

    private static Map<String, Integer> foerMap(Map<String, StatsDataHandler.Stat<Integer, Integer>> fields, int fishTotal) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (fields == null) return out;
        fields.forEach((field, stat) -> {
            if (stat == null || stat.caughtOn() == null) return;
            out.put(field, Math.max(0, fishTotal - stat.caughtOn()));
        });
        return out;
    }
    //endregion

    //region Applying
    /// Sets every streak in {@code source} on {@code data}; counts are left alone. Returns how many were set.
    static int apply(Source source, StatsDataHandler.StatsDataModel data) {
        int set = 0;
        for (var category : source.fish().entrySet()) set += applyMap(data.fishData, category.getKey(), category.getValue(), data.fishTotal);
        for (var category : source.pets().entrySet()) set += applyMap(data.petData, category.getKey(), category.getValue(), data.fishTotal);
        for (var item : source.items().entrySet()) {
            String key = itemKey(data, item.getKey());
            StatsDataHandler.Stat<Integer, Integer> stat = data.itemData.get(key);
            int amount = stat == null || stat.amount() == null ? 0 : stat.amount();
            // Negative is fine: it means the streak started before this install counted any fish.
            data.itemData.put(key, StatsDataHandler.Stat.of(amount, data.fishTotal - item.getValue()));
            set++;
        }
        return set;
    }

    private static int applyMap(Map<String, Map<String, StatsDataHandler.Stat<Integer, Integer>>> target, String category,
                                Map<String, Integer> streaks, int fishTotal) {
        Map<String, StatsDataHandler.Stat<Integer, Integer>> fields = target.computeIfAbsent(category, k -> new HashMap<>());
        for (var streak : streaks.entrySet()) {
            StatsDataHandler.Stat<Integer, Integer> stat = fields.get(streak.getKey());
            int amount = stat == null || stat.amount() == null ? 0 : stat.amount();
            fields.put(streak.getKey(), StatsDataHandler.Stat.of(amount, fishTotal - streak.getValue()));
        }
        return streaks.size();
    }

    /// FOE calls it "shard"; use whatever shard key this install already has.
    private static String itemKey(StatsDataHandler.StatsDataModel data, String key) {
        if (!key.equals("shard")) return key;
        for (String existing : data.itemData.keySet()) {
            if (existing.toLowerCase(Locale.ROOT).contains("shard")) return existing;
        }
        return key;
    }
    //endregion

    //region Finding and running
    static List<Source> findSources(UUID uuid) {
        Path gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        Path ownFoer = gameDir.resolve("config/" + FishOnMCExtras.MOD_ID + "/data/" + uuid + "/stats.json").normalize();
        Set<Path> dirs = new LinkedHashSet<>();
        dirs.add(gameDir);
        dirs.addAll(FoerStatsImporter.candidateGameDirs(gameDir, System.getProperty("user.home"), System.getenv("APPDATA")));

        List<Source> out = new ArrayList<>();
        for (Path dir : dirs) {
            Path foe = dir.resolve("config/foe/stats/" + uuid + ".json").normalize();
            if (Files.isRegularFile(foe)) {
                try {
                    out.add(readFoe(foe, Files.getLastModifiedTime(foe).toMillis(), JsonParser.parseString(Files.readString(foe)).getAsJsonObject()));
                } catch (Exception e) {
                    LoggerHandler.error(e);
                }
            }
            Path foer = dir.resolve("config/" + FishOnMCExtras.MOD_ID + "/data/" + uuid + "/stats.json").normalize();
            if (!foer.equals(ownFoer) && Files.isRegularFile(foer)) {
                try {
                    StatsDataHandler.StatsDataModel model = new Gson().fromJson(Files.readString(foer), StatsDataHandler.StatsDataModel.class);
                    if (model != null) out.add(readFoer(foer, Files.getLastModifiedTime(foer).toMillis(), model));
                } catch (Exception e) {
                    LoggerHandler.error(e);
                }
            }
        }
        return out;
    }

    /// The most recently saved source that has any streaks (that's the most up to date one).
    static Optional<Source> newest(List<Source> sources) {
        return sources.stream().filter(s -> s.count() > 0).max(Comparator.comparingLong(Source::lastModified));
    }

    /// From the O menu: imports the dry streaks of the newest FOE / FOE-R file found.
    public static void importNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID uuid = mc.player.getUUID();
        Thread thread = new Thread(() -> {
            List<Source> sources = findSources(uuid);
            mc.execute(() -> {
                Optional<Source> source = newest(sources);
                if (source.isEmpty()) {
                    message(Component.literal("No FOE or FOE-R dry streaks were found in this or your other Minecraft folders.").withStyle(ChatFormatting.RED));
                    return;
                }
                StatsDataHandler stats = StatsDataHandler.instance();
                int set = apply(source.get(), stats.getStatsData());
                stats.markChanged();
                message(Component.literal("Imported " + set + " dry streaks from " + source.get().kind() + " (").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(describe(source.get().file())).withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(").").withStyle(ChatFormatting.GREEN)));
            });
        }, "FOE+ dry streak import");
        thread.setDaemon(true);
        thread.start();
    }

    /// "Fabric 1.21.11 profile" style: the game folder's name.
    private static String describe(Path file) {
        // .../<game folder>/config/foe/stats/<uuid>.json or .../<game folder>/config/fishonmcextras/data/<uuid>/stats.json
        Path game = file.toString().contains("fishonmcextras") ? up(file, 5) : up(file, 4);
        if (game == null || game.getFileName() == null) return file.toString();
        String name = game.getFileName().toString();
        if ((name.equals(".minecraft") || name.equals("minecraft")) && game.getParent() != null && game.getParent().getFileName() != null
                && !game.getParent().getFileName().toString().equalsIgnoreCase("roaming")) {
            name = game.getParent().getFileName() + "/" + name;
        }
        return name;
    }

    private static Path up(Path p, int n) {
        for (int i = 0; i < n && p != null; i++) p = p.getParent();
        return p;
    }

    private static int getInt(JsonObject json, String key) {
        try {
            return json.has(key) ? json.get(key).getAsInt() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static void message(Component text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[FOE+] ").withStyle(ChatFormatting.AQUA).append(text), false);
        }
    }
    //endregion
}
