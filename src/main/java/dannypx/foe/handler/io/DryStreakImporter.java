package dannypx.foe.handler.io;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.handler.logic.RareCatchHandler;
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
 * Fabled entry, which FOE keeps as a plain counter. Pets (any rarity), shards, Infusion Capsules
 * and Lightning Bottles come over too.
 */
public final class DryStreakImporter {
    private DryStreakImporter() {}

    static final List<String> RARITIES = List.of("common", "rare", "epic", "legendary", "mythical", "special");
    static final List<String> VARIANTS = List.of("albino", "melanistic", "trophy", "fabled");
    static final List<String> SIZES = List.of("baby", "juvenile", "adult", "large", "gigantic");

    /// Pets of any rarity, as one streak.
    static final String PET_ANY = "pet";
    static final String COUNT_PETS = "pets";
    static final String COUNT_SHARDS = "shards";

    /// Dry streaks read from one file: category → field → streak, plus pets and items, and
    /// {@code special}: the any-rarity pet streak ("pet") and the rare pulls ("infusion_capsule",
    /// "lightning_bottle", which count only Blood Moon / thunderstorm catches).
    /// {@code counts}: totals the file knows ("pets", "shards", "infusion_capsule",
    /// "lightning_bottle"), brought over when higher than here. {@code fishTotal}: the file's own
    /// fish count, or 0 when it isn't comparable with this install's.
    public record Source(Path file, String kind, long lastModified,
                         Map<String, Map<String, Integer>> fish,
                         Map<String, Map<String, Integer>> pets,
                         Map<String, Integer> items,
                         Map<String, Integer> special,
                         Map<String, Integer> counts,
                         int fishTotal) {
        public Source(Path file, String kind, long lastModified, Map<String, Map<String, Integer>> fish,
                      Map<String, Map<String, Integer>> pets, Map<String, Integer> items) {
            this(file, kind, lastModified, fish, pets, items, new LinkedHashMap<>(), new LinkedHashMap<>(), 0);
        }

        public Source(Path file, String kind, long lastModified, Map<String, Map<String, Integer>> fish,
                      Map<String, Map<String, Integer>> pets, Map<String, Integer> items, Map<String, Integer> special) {
            this(file, kind, lastModified, fish, pets, items, special, new LinkedHashMap<>(), 0);
        }

        int count() {
            int n = items.size() + special.size();
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
        // FOE keeps the fish count at the last one for these too. For capsules and bottles it also
        // moves that count along on every catch outside a Blood Moon / thunderstorm, so the
        // difference is exactly the event catches since the last one.
        Map<String, Integer> special = new LinkedHashMap<>();
        if (foe.has("petDryStreak")) special.put(PET_ANY, Math.max(0, all - getInt(foe, "petDryStreak")));
        if (foe.has("infusionCapsuleDryStreak")) special.put(RareCatchHandler.INFUSION_CAPSULE, Math.max(0, all - getInt(foe, "infusionCapsuleDryStreak")));
        if (foe.has("lightningBottleDryStreak")) special.put(RareCatchHandler.LIGHTNING_BOTTLE, Math.max(0, all - getInt(foe, "lightningBottleDryStreak")));
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (foe.has("allPetCaughtCount")) counts.put(COUNT_PETS, getInt(foe, "allPetCaughtCount"));
        if (foe.has("allShardCaughtCount")) counts.put(COUNT_SHARDS, getInt(foe, "allShardCaughtCount"));
        if (foe.has("allInfusionCapsuleCount")) counts.put(RareCatchHandler.INFUSION_CAPSULE, getInt(foe, "allInfusionCapsuleCount"));
        if (foe.has("allLightningBottleCount")) counts.put(RareCatchHandler.LIGHTNING_BOTTLE, getInt(foe, "allLightningBottleCount"));
        // FOE takes its fish count from your server stats, like FOE+, so the two can be compared.
        return new Source(file, "FOE", lastModified, fish, new LinkedHashMap<>(), items, special, counts, all);
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
        Map<String, Integer> special = new LinkedHashMap<>();
        // Another FOE+ install: its any-rarity pet streak and event-only streaks.
        int petOn = model.petCaughtOn != null ? model.petCaughtOn : StatsDataHandler.lastPetCaughtOnFromRarities(model);
        if (model.petCaughtOn != null || petOn > 0) special.put(PET_ANY, Math.max(0, model.fishTotal - petOn));
        if (model.rareEventStreak != null) {
            model.rareEventStreak.forEach((key, streak) -> {
                if (streak != null && RareCatchHandler.isEventPull(key)) special.put(key, Math.max(0, streak));
            });
        }
        return new Source(file, "FOE-R", lastModified, fish, pets, items, special);
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
    /// What an import did: streaks set, streaks kept because this install's are newer, and totals
    /// raised to the file's.
    public record Result(int set, int kept, int counts) {
        public Result(int set, int kept) {
            this(set, kept, 0);
        }
    }

    /**
     * Sets the streaks in {@code source} on {@code data}; counts are left alone. A streak is kept
     * as it is when this install knows better: it caught that rarity/variant/item (or you set it)
     * after the source file was saved, or its streak is already lower than the source's (you
     * caught one more recently). A streak of 0 means "not known yet" and is always filled in.
     */
    static Result apply(Source source, StatsDataHandler.StatsDataModel data) {
        int[] counts = new int[2];
        for (var category : source.fish().entrySet()) applyMap(source, data, "fish", data.fishData, category.getKey(), category.getValue(), counts);
        for (var category : source.pets().entrySet()) applyMap(source, data, "pet", data.petData, category.getKey(), category.getValue(), counts);
        for (var item : source.items().entrySet()) {
            String key = itemKey(data, item.getKey());
            StatsDataHandler.Stat<Integer, Integer> stat = data.itemData.get(key);
            if (keepCurrent(source, data, "item:" + key, stat, item.getValue())) {
                counts[1]++;
                continue;
            }
            int amount = stat == null || stat.amount() == null ? 0 : stat.amount();
            // Negative is fine: it means the streak started before this install counted any fish.
            data.itemData.put(key, StatsDataHandler.Stat.of(amount, data.fishTotal - streakNow(source, data, item.getValue())));
            counts[0]++;
        }
        for (var special : source.special().entrySet()) {
            if (applySpecial(source, data, special.getKey(), special.getValue())) counts[0]++;
            else counts[1]++;
        }
        return new Result(counts[0], counts[1], applyCounts(source, data));
    }

    /// Totals from the file, where they're higher than here (you never lose any you counted
    /// here, and nothing is counted twice). Returns how many were raised.
    private static int applyCounts(Source source, StatsDataHandler.StatsDataModel data) {
        int raised = 0;
        for (var count : source.counts().entrySet()) {
            int value = Math.max(0, count.getValue());
            switch (count.getKey()) {
                case COUNT_PETS -> {
                    if (value > data.petTotal) { data.petTotal = value; raised++; }
                }
                case COUNT_SHARDS -> {
                    int have = 0;
                    for (var e : data.itemData.entrySet()) {
                        if (e.getKey().toLowerCase(Locale.ROOT).contains("shard") && e.getValue() != null && e.getValue().amount() != null) have += e.getValue().amount();
                    }
                    if (value > have) {
                        String key = itemKey(data, "shard");
                        StatsDataHandler.Stat<Integer, Integer> stat = data.itemData.get(key);
                        int amount = stat == null || stat.amount() == null ? 0 : stat.amount();
                        Integer on = stat == null ? null : stat.caughtOn();
                        data.itemData.put(key, StatsDataHandler.Stat.of(amount + value - have, on == null ? data.fishTotal : on));
                        raised++;
                    }
                }
                default -> {
                    if (!RareCatchHandler.isEventPull(count.getKey())) continue;
                    if (data.rareCatchData == null) data.rareCatchData = new HashMap<>();
                    StatsDataHandler.Stat<Integer, Integer> stat = data.rareCatchData.get(count.getKey());
                    int amount = stat == null || stat.amount() == null ? 0 : stat.amount();
                    if (value > amount) {
                        data.rareCatchData.put(count.getKey(), StatsDataHandler.Stat.of(value, stat == null || stat.caughtOn() == null ? data.fishTotal : stat.caughtOn()));
                        raised++;
                    }
                }
            }
        }
        return raised;
    }

    private static void applyMap(Source source, StatsDataHandler.StatsDataModel data, String set,
                                 Map<String, Map<String, StatsDataHandler.Stat<Integer, Integer>>> target, String category,
                                 Map<String, Integer> streaks, int[] counts) {
        Map<String, StatsDataHandler.Stat<Integer, Integer>> fields = target.computeIfAbsent(category, k -> new HashMap<>());
        for (var streak : streaks.entrySet()) {
            StatsDataHandler.Stat<Integer, Integer> stat = fields.get(streak.getKey());
            if (keepCurrent(source, data, set + ":" + category + ":" + streak.getKey(), stat, streak.getValue())) {
                counts[1]++;
                continue;
            }
            int amount = stat == null || stat.amount() == null ? 0 : stat.amount();
            fields.put(streak.getKey(), StatsDataHandler.Stat.of(amount, data.fishTotal - streakNow(source, data, streak.getValue())));
            counts[0]++;
        }
    }

    /// The any-rarity pet streak, or an event-only rare pull streak. True when it was set.
    private static boolean applySpecial(Source source, StatsDataHandler.StatsDataModel data, String key, int streak) {
        if (key.equals(PET_ANY)) {
            int on = data.petCaughtOn != null ? data.petCaughtOn : StatsDataHandler.lastPetCaughtOnFromRarities(data);
            boolean havePet = data.petCaughtOn != null || on > 0;
            StatsDataHandler.Stat<Integer, Integer> current = havePet ? StatsDataHandler.Stat.of(0, on) : null;
            if (keepCurrent(source, data, "pet:any", current, streak)) return false;
            data.petCaughtOn = data.fishTotal - streakNow(source, data, streak);
            return true;
        }
        if (!RareCatchHandler.isEventPull(key)) return false;
        if (data.rareEventStreak == null) data.rareEventStreak = new HashMap<>();
        Long knownAt = data.streakKnownAt == null ? null : data.streakKnownAt.get("rare:" + key);
        if (knownAt != null && knownAt > source.lastModified()) return false;
        Integer current = data.rareEventStreak.get(key);
        // A lower streak here means you pulled one more recently than that file knows.
        if (current != null && current > 0 && current < streak) return false;
        data.rareEventStreak.put(key, streak);
        return true;
    }

    /// True when this install's streak is newer than the source's and must stay. Package-private for tests.
    static boolean keepCurrent(Source source, StatsDataHandler.StatsDataModel data, String key,
                               StatsDataHandler.Stat<Integer, Integer> current, int sourceStreak) {
        // Both fish counts come from your server stats: a streak here that's longer than the
        // file's plus every fish since it was saved can't be right (an older FOE+ build let pet
        // and shard streaks jump when the fish total changed), so the file's is taken.
        if (comparable(source, data) && current != null && current.caughtOn() != null
                && data.fishTotal - current.caughtOn() > streakNow(source, data, sourceStreak)) {
            return false;
        }
        Long knownAt = data.streakKnownAt == null ? null : data.streakKnownAt.get(key);
        if (knownAt != null && knownAt > source.lastModified()) return true;
        if (current == null || current.caughtOn() == null) return false;
        int currentStreak = data.fishTotal - current.caughtOn();
        return currentStreak > 0 && currentStreak < sourceStreak;
    }

    /// True when the file's fish count and this install's count the same fish (both from your
    /// server stats), so fish caught since the file was saved = this total minus the file's. Only
    /// when the difference is no more than you could have fished since then (FOE counts from when
    /// it was installed unless you imported your server stats into it).
    static boolean comparable(Source source, StatsDataHandler.StatsDataModel data) {
        if (source.fishTotal() <= 0 || data.fishTotal < source.fishTotal() || source.lastModified() <= 0L) return false;
        long hours = Math.max(0L, System.currentTimeMillis() - source.lastModified()) / 3_600_000L;
        long mostFish = MAX_FISH_SLACK + hours * MAX_FISH_PER_HOUR;
        return data.fishTotal - source.fishTotal() <= mostFish;
    }

    /// Far more than anyone catches, so real fishing never looks impossible.
    static final long MAX_FISH_PER_HOUR = 1_500L;
    static final long MAX_FISH_SLACK = 2_000L;

    /// The file's streak as it stands now: plus the fish you've caught since it was saved, when
    /// the two fish counts can be compared.
    static int streakNow(Source source, StatsDataHandler.StatsDataModel data, int sourceStreak) {
        return comparable(source, data) ? sourceStreak + (data.fishTotal - source.fishTotal()) : sourceStreak;
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
                    message(Component.literal("No FOE or FOE-R dry streaks found on this computer.").withStyle(ChatFormatting.RED));
                    return;
                }
                StatsDataHandler stats = StatsDataHandler.instance();
                Result result = apply(source.get(), stats.getStatsData());
                stats.markChanged();
                message(Component.literal("Imported " + result.set() + " dry streaks"
                                + (result.counts() > 0 ? " and " + result.counts() + " totals" : "") + " from " + source.get().kind() + " (").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(describe(source.get().file())).withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(")").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(result.kept() > 0 ? " · kept " + result.kept() + " newer here" : "").withStyle(ChatFormatting.GRAY)));
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
            mc.player.displayClientMessage(dannypx.foe.helper.FoeChat.line("STATS", dannypx.foe.helper.FoeChat.AQUA).append(text), false);
        }
    }
    //endregion
}
