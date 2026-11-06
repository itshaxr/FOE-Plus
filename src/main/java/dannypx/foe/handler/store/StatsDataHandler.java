package dannypx.foe.handler.store;

import dannypx.foe.handler.Handler;
import dannypx.foe.handler.io.DataFileHandler;
import dannypx.foe.handler.io.DataModels;
import dannypx.foe.handler.logic.NotifierHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.item.FishTagObject;
import dannypx.foe.item.TagObject;
import dannypx.foe.item.PetTagObject;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.type.tuple.Triplet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class StatsDataHandler extends Handler {
    private static StatsDataHandler INSTANCE = new StatsDataHandler();

    public static StatsDataHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new StatsDataHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private StatsDataModel statsData = new StatsDataModel();
    private boolean needsUpdate = false;

    public StatsDataModel getStatsData() {
        return statsData;
    }

    public void setStatsData(StatsDataModel statsData) {
        this.statsData = statsData;
        this.updateStatsData();
    }

    private void updateStatsData() {
        if(needsUpdate) {
            DataFileHandler.instance().saveToFile(DataModels.DataModelType.STATS_DATA);
        }
        this.needsUpdate = false;
    }
    //endregion

    //region Methods
    public void tick() {
        if(statsData.uuid == null && minecraft.player != null) {
            statsData.uuid = minecraft.player.getUUID();
        } else if(statsData.uuid != null && needsUpdate) {
            this.updateStatsData();
        } else if(!StatsDataModel.STATS_DATA_MODEL_VERSION.equals(statsData.version)) {
            statsData.version = StatsDataModel.STATS_DATA_MODEL_VERSION;
            this.fixDefault();
            needsUpdate = true;
        }
    }

    public void init() {
        if(minecraft.player != null) this.setUUID(minecraft.player.getUUID());
    }

    private void setUUID(UUID uuid) {
        this.statsData.uuid = uuid;
    }

    private void fixDefault() {
        if(statsData.fishData == null) statsData.fishData = new HashMap<>();
        if(statsData.petData == null) statsData.petData = new HashMap<>();
        if(statsData.itemData == null) statsData.itemData = new HashMap<>();
        if(statsData.rareCatchData == null) statsData.rareCatchData = new HashMap<>();
        if(statsData.rareEventStreak == null) statsData.rareEventStreak = new HashMap<>();

        if(statsData.questItemData == null) statsData.questItemData = new HashMap<>();
        if(statsData.questPetData == null) statsData.questItemData = new HashMap<>();
    }

    public Triplet<Pair<String, Integer>, Pair<String, Integer>, Pair<String, Integer>> setFish(FishTagObject fish) {
        // Dry streak of this fish's variant: fish since the last one, counting this catch.
        Map<String, Stat<Integer, Integer>> variants = statsData.fishData.getOrDefault(FishTagObject.VARIANT, Map.of());
        Stat<Integer, Integer> lastOfVariant = variants.get(fish.getVariant());
        int variantDryStreak = lastOfVariant == null || lastOfVariant.amount() == null || lastOfVariant.amount() == 0
                ? statsData.fishTotal + 1 : statsData.fishTotal + 1 - lastOfVariant.caughtOn();
        // Same for Gigantic size.
        Stat<Integer, Integer> lastGigantic = statsData.fishData.getOrDefault(FishTagObject.FISH_SIZE, Map.of()).get("gigantic");
        int giganticDryStreak = lastGigantic == null || lastGigantic.amount() == null || lastGigantic.amount() == 0
                ? statsData.fishTotal + 1 : statsData.fishTotal + 1 - lastGigantic.caughtOn();
        statsData.fishTotal++;
        countEventCatch();

        Pair<String, Integer> rarityDrystreak = this.updateFishData(statsData, FishTagObject.RARITY, fish.getRarity(), 1);
        ConstantDataHandler.instance().updateFishData(FishTagObject.RARITY, fish.getRarity(), fish.getRarityComponent());

        Pair<String, Integer> variantDrystreak = this.updateFishData(statsData, FishTagObject.VARIANT, fish.getVariant(), 1);
        ConstantDataHandler.instance().updateFishData(FishTagObject.VARIANT, fish.getVariant(), fish.getVariantComponent());

        Pair<String, Integer> sizeDryStreak = this.updateFishData(statsData, FishTagObject.FISH_SIZE, fish.getFishSize(), 1);
        ConstantDataHandler.instance().updateFishData(FishTagObject.FISH_SIZE, fish.getFishSize(), fish.getFishSizeComponent());

        NotifierHandler.instance().chatVariant(fish, variantDryStreak);
        if ("gigantic".equalsIgnoreCase(fish.getFishSize())) NotifierHandler.instance().chatGigantic(fish, giganticDryStreak);

        return Triplet.of(rarityDrystreak, variantDrystreak, sizeDryStreak);
    }



    // Field, Old Drystreak
    private Pair<String, Integer> updateFishData(StatsDataModel statsData, String category, String field, int valueToAdd) {
        Map<String, Stat<Integer, Integer>> categoryMapData = statsData.fishData.getOrDefault(category, new HashMap<>());
        Stat<Integer, Integer> fieldStat = categoryMapData.getOrDefault(field, Stat.of(0, statsData.fishTotal));

        Stat<Integer, Integer> newFieldStat = Stat.of(fieldStat.amount() + valueToAdd, statsData.fishTotal);
        categoryMapData.put(field, newFieldStat);
        statsData.fishData.put(category, categoryMapData);
        markStreakKnown("fish:" + category + ":" + field);
        this.needsUpdate = true;

        return Pair.of(field, statsData.fishTotal - fieldStat.caughtOn());
    }

    public Pair<Pair<String, Integer>, Pair<String, Integer>> setPet(PetTagObject pet) {
        // Fish since the last pet of any rarity (the newest pet catch), before this one is added.
        int petDryStreak = statsData.fishTotal - this.lastPetCaughtOn();
        Map<String, Stat<Integer, Integer>> petRarities = statsData.petData.getOrDefault(PetTagObject.RARITY, Map.of());
        Stat<Integer, Integer> lastOfRarity = petRarities.get(pet.getRarity());
        int petRarityDryStreak = lastOfRarity == null || lastOfRarity.amount() == null || lastOfRarity.amount() == 0
                ? statsData.fishTotal : statsData.fishTotal - lastOfRarity.caughtOn();
        statsData.petTotal++;
        statsData.petCaughtOn = statsData.fishTotal;
        markStreakKnown("pet:any");

        Pair<String, Integer> rarityDrystreak = this.updatePetData(statsData, PetTagObject.RARITY, pet.getRarity());
        ConstantDataHandler.instance().updatePetData(PetTagObject.RARITY, pet.getRarity(), pet.getRarityComponent());

        Pair<String, Integer> ratingDrystreak = this.updatePetData(statsData, PetTagObject.RATING, pet.getRatingComponent().getString());
        ConstantDataHandler.instance().updatePetData(PetTagObject.RATING, pet.getRatingComponent().getString(), pet.getRatingComponent());

        // Notify Pet
        NotifierHandler.instance().notifyPet(pet, rarityDrystreak, ratingDrystreak);
        NotifierHandler.instance().chatPet(pet, petDryStreak, petRarityDryStreak);

        return Pair.of(rarityDrystreak, ratingDrystreak);
    }

    /// Fish count at your most recent pet catch (0 if you have never caught one).
    int lastPetCaughtOn() {
        if (statsData.petCaughtOn != null) return statsData.petCaughtOn;
        return lastPetCaughtOnFromRarities(statsData);
    }

    /// The newest pet catch going by the pet rarities alone (older files have nothing else).
    public static int lastPetCaughtOnFromRarities(StatsDataModel statsData) {
        if (statsData.petData == null) return 0;
        Map<String, Stat<Integer, Integer>> byRarity = statsData.petData.get(PetTagObject.RARITY);
        if (byRarity == null) return 0;
        int last = 0;
        for (Stat<Integer, Integer> stat : byRarity.values()) {
            if (stat != null && stat.amount() != null && stat.amount() > 0 && stat.caughtOn() != null) last = Math.max(last, stat.caughtOn());
        }
        return last;
    }

    // Field, Old Drystreak
    private Pair<String, Integer> updatePetData(StatsDataModel statsData, String category, String field) {
        Map<String, Stat<Integer, Integer>> categoryMapData = statsData.petData.getOrDefault(category, new HashMap<>());
        Stat<Integer, Integer> fieldStat = categoryMapData.getOrDefault(field, Stat.of(0, statsData.fishTotal));

        Stat<Integer, Integer> newFieldStat = Stat.of(fieldStat.amount() + 1, statsData.fishTotal);
        categoryMapData.put(field, newFieldStat);
        statsData.petData.put(category, categoryMapData);
        markStreakKnown("pet:" + category + ":" + field);
        this.needsUpdate = true;

        return Pair.of(field, statsData.fishTotal - fieldStat.caughtOn());
    }

    public void setQuestPet(PetTagObject pet) {
        statsData.questPetTotal++;

        this.updateQuestPetData(statsData, PetTagObject.RARITY, pet.getRarity());
        ConstantDataHandler.instance().updatePetData(PetTagObject.RARITY, pet.getRarity(), pet.getRarityComponent());

        this.updateQuestPetData(statsData, PetTagObject.RATING, pet.getRatingComponent().getString());
        ConstantDataHandler.instance().updatePetData(PetTagObject.RATING, pet.getRatingComponent().getString(), pet.getRatingComponent());

        // Notify Pet
        // NotifierHandler.instance().notifyPet(pet, rarityDrystreak, ratingDrystreak);
    }

    // Field, Old Drystreak
    private void updateQuestPetData(StatsDataModel statsData, String category, String field) {
        Map<String, Integer> categoryMapData = statsData.questPetData.getOrDefault(category, new HashMap<>());
        Integer fieldStat = categoryMapData.getOrDefault(field, 0);

        categoryMapData.put(field, fieldStat + 1);
        statsData.questPetData.put(category, categoryMapData);
        this.needsUpdate = true;
    }

    public Pair<String, Integer> setOtherItem(TagObject item, int count) {
        Pair<String, Integer> itemDrystreak = this.updateOtherItemData(statsData, item.getType(), count);
        ConstantDataHandler.instance().updateItemData(item.getType(), item.getItemStack());

        // Notify Item
        NotifierHandler.instance().notifyItem(item, count, itemDrystreak);

        return itemDrystreak;
    }

    private Pair<String, Integer> updateOtherItemData(StatsDataModel statsData, String item, int valueToAdd) {
        Stat<Integer, Integer> itemStat = statsData.itemData.getOrDefault(item, Stat.of(0, statsData.fishTotal));

        Stat<Integer, Integer> newItemStat = Stat.of(itemStat.amount() + valueToAdd, statsData.fishTotal);
        statsData.itemData.put(item, newItemStat);
        markStreakKnown("item:" + item);
        this.needsUpdate = true;

        return Pair.of(item, statsData.fishTotal - itemStat.caughtOn());
    }

    /// A rare pull announced in chat ("RARE CATCH! You pulled 1x Infusion Capsule"), keyed like
    /// "infusion_capsule". Returns the dry streak (fish since the last one), or -1 when this is the
    /// first one this install has seen.
    public int onRareCatch(String key, int count) {
        if (statsData.rareCatchData == null) statsData.rareCatchData = new HashMap<>();
        if (statsData.rareEventStreak == null) statsData.rareEventStreak = new HashMap<>();
        Stat<Integer, Integer> old = statsData.rareCatchData.get(key);
        int streak = rareCatchStreak(key);
        int amount = (old == null || old.amount() == null ? 0 : old.amount()) + Math.max(1, count);
        statsData.rareCatchData.put(key, Stat.of(amount, statsData.fishTotal));
        if (dannypx.foe.handler.logic.RareCatchHandler.isEventPull(key)) statsData.rareEventStreak.put(key, 0);
        markStreakKnown("rare:" + key);
        this.needsUpdate = true;
        return streak;
    }

    /// Fish since the last rare pull of this kind, or -1 if none has been seen (or set by hand).
    public int rareCatchStreak(String key) {
        if (dannypx.foe.handler.logic.RareCatchHandler.isEventPull(key)) {
            Integer streak = statsData.rareEventStreak == null ? null : statsData.rareEventStreak.get(key);
            return streak == null ? -1 : Math.max(0, streak);
        }
        Stat<Integer, Integer> stat = statsData.rareCatchData == null ? null : statsData.rareCatchData.get(key);
        return stat == null || stat.caughtOn() == null ? -1 : Math.max(0, statsData.fishTotal - stat.caughtOn());
    }

    /// A fish was caught: the event-only dry streaks count it if their event is on.
    private void countEventCatch() {
        if (statsData.rareEventStreak == null) statsData.rareEventStreak = new HashMap<>();
        for (String key : dannypx.foe.handler.logic.RareCatchHandler.EVENT_PULLS) {
            if (!dannypx.foe.handler.logic.RareCatchHandler.eventOn(key)) continue;
            Integer streak = statsData.rareEventStreak.get(key);
            // Not known yet (no catch or import so far): don't guess, it starts at the next one.
            if (streak != null) statsData.rareEventStreak.put(key, streak + 1);
        }
    }

    /// How many of a rare pull you've had ("infusion_capsule").
    public int rareCatchAmount(String key) {
        Stat<Integer, Integer> stat = statsData.rareCatchData == null ? null : statsData.rareCatchData.get(key);
        return stat == null || stat.amount() == null ? 0 : stat.amount();
    }

    /// Fish since your last pet of any rarity.
    public int petDryStreak() {
        return Math.max(0, statsData.fishTotal - lastPetCaughtOn());
    }

    /// Shards of every kind you've caught.
    public int shardTotal() {
        int shards = 0;
        for (Map.Entry<String, Stat<Integer, Integer>> entry : statsData.itemData.entrySet()) {
            if (entry.getKey().toLowerCase(java.util.Locale.ROOT).contains("shard") && entry.getValue() != null && entry.getValue().amount() != null) {
                shards += entry.getValue().amount();
            }
        }
        return shards;
    }

    /// Fish since your last shard of any kind (your fish total if you've never had one).
    public int shardDryStreak() {
        int last = Integer.MIN_VALUE;
        for (Map.Entry<String, Stat<Integer, Integer>> entry : statsData.itemData.entrySet()) {
            if (entry.getKey().toLowerCase(java.util.Locale.ROOT).contains("shard") && entry.getValue() != null && entry.getValue().caughtOn() != null) {
                last = Math.max(last, entry.getValue().caughtOn());
            }
        }
        return last == Integer.MIN_VALUE ? statsData.fishTotal : Math.max(0, statsData.fishTotal - last);
    }

    public void setQuestItem(TagObject item, int count) {
        this.updateQuestItemData(statsData, item.getType(), count);
        ConstantDataHandler.instance().updateItemData(item.getType(), item.getItemStack());

        // Notify Item
        // NotifierHandler.instance().notifyItem(item, count, itemDrystreak);
    }

    private void updateQuestItemData(StatsDataModel statsData, String item, int valueToAdd) {
        Integer itemStat = statsData.questItemData.getOrDefault(item, 0);

        statsData.questItemData.put(item, itemStat + valueToAdd);
        this.needsUpdate = true;
    }

    public void addFishTime(long ms, int fish) {
        statsData.fishTimeMs += ms;
        statsData.fishTimeFish += fish;
        this.needsUpdate = true;
    }

    /// Sets how many fish ago a category was last caught (FOE's dry streak) without changing its count.
    public void setDryStreak(String category, String field, int dryStreak) {
        Map<String, Stat<Integer, Integer>> categoryMap = statsData.fishData.getOrDefault(category, new HashMap<>());
        Stat<Integer, Integer> stat = categoryMap.getOrDefault(field, Stat.of(0, 0));
        // May go below 0: the streak started before this install counted any fish.
        categoryMap.put(field, Stat.of(stat.amount(), statsData.fishTotal - Math.max(0, dryStreak)));
        statsData.fishData.put(category, categoryMap);
        this.needsUpdate = true;
    }

    public void markChanged() {
        this.needsUpdate = true;
    }

    /// Changes the fish total without changing any dry streak: every "caught on" fish count (fish,
    /// pets, items like shards, rare pulls, the last pet) moves by the same amount. Used when the
    /// total comes from the server's stats or is typed in by hand. Before, only the fish streaks
    /// moved, so pet and shard streaks jumped by the whole difference.
    public void setFishTotalKeepingStreaks(int newTotal) {
        shiftStreaks(statsData, newTotal);
        this.needsUpdate = true;
    }

    /// Package-private for tests.
    static void shiftStreaks(StatsDataModel data, int newTotal) {
        int delta = newTotal - data.fishTotal;
        data.fishTotal = newTotal;
        if (delta == 0) return;
        if (data.fishData != null) data.fishData.values().forEach(m -> shift(m, delta));
        if (data.petData != null) data.petData.values().forEach(m -> shift(m, delta));
        shift(data.itemData, delta);
        shift(data.rareCatchData, delta);
        if (data.petCaughtOn != null) data.petCaughtOn += delta;
    }

    private static void shift(Map<String, Stat<Integer, Integer>> map, int delta) {
        if (map == null) return;
        map.replaceAll((k, s) -> s == null || s.caughtOn() == null ? s : Stat.of(s.amount(), s.caughtOn() + delta));
    }

    /// Remembers that this install knows this dry streak first-hand (a catch here, or you set it),
    /// so an import never replaces it with older numbers.
    public void markStreakKnown(String key) {
        if (statsData.streakKnownAt == null) statsData.streakKnownAt = new HashMap<>();
        statsData.streakKnownAt.put(key, System.currentTimeMillis());
    }

    /// Sets a dry streak by hand (from the O menu), keeping the count.
    public void setStreakManually(String set, String category, String field, int dryStreak) {
        if (set.equals("rare") && dannypx.foe.handler.logic.RareCatchHandler.isEventPull(field)) {
            if (statsData.rareEventStreak == null) statsData.rareEventStreak = new HashMap<>();
            statsData.rareEventStreak.put(field, Math.max(0, dryStreak));
            markStreakKnown("rare:" + field);
            this.needsUpdate = true;
            return;
        }
        if (set.equals("pet") && category.equals("any")) {
            statsData.petCaughtOn = statsData.fishTotal - Math.max(0, dryStreak);
            markStreakKnown("pet:any");
            this.needsUpdate = true;
            return;
        }
        if (set.equals("item")) {
            // Every shard kind counts as one streak: set them all, so the newest is this one.
            boolean any = false;
            for (Map.Entry<String, Stat<Integer, Integer>> e : statsData.itemData.entrySet()) {
                if (!e.getKey().toLowerCase(java.util.Locale.ROOT).contains(field)) continue;
                int amount = e.getValue() == null || e.getValue().amount() == null ? 0 : e.getValue().amount();
                e.setValue(Stat.of(amount, statsData.fishTotal - Math.max(0, dryStreak)));
                markStreakKnown("item:" + e.getKey());
                any = true;
            }
            if (!any) {
                statsData.itemData.put(field, Stat.of(0, statsData.fishTotal - Math.max(0, dryStreak)));
                markStreakKnown("item:" + field);
            }
            this.needsUpdate = true;
            return;
        }
        if (set.equals("rare")) {
            if (statsData.rareCatchData == null) statsData.rareCatchData = new HashMap<>();
            Stat<Integer, Integer> stat = statsData.rareCatchData.get(field);
            int amount = stat == null || stat.amount() == null ? 0 : stat.amount();
            statsData.rareCatchData.put(field, Stat.of(amount, statsData.fishTotal - Math.max(0, dryStreak)));
            markStreakKnown("rare:" + field);
            this.needsUpdate = true;
            return;
        }
        Map<String, Map<String, Stat<Integer, Integer>>> target = set.equals("pet") ? statsData.petData : statsData.fishData;
        Map<String, Stat<Integer, Integer>> fields = target.computeIfAbsent(category, k -> new HashMap<>());
        Stat<Integer, Integer> stat = fields.get(field);
        int amount = stat == null || stat.amount() == null ? 0 : stat.amount();
        fields.put(field, Stat.of(amount, statsData.fishTotal - Math.max(0, dryStreak)));
        markStreakKnown(set + ":" + category + ":" + field);
        this.needsUpdate = true;
    }

    /// From Edit All-Time Stats: sets a total by hand. "fish" keeps every dry streak, "shards"
    /// puts the difference on one shard kind, rare pulls ("infusion_capsule") keep their streak.
    public void setTotalManually(String what, int value) {
        int v = Math.max(0, value);
        switch (what) {
            case "fish" -> setFishTotalKeepingStreaks(v);
            case "pets" -> statsData.petTotal = v;
            case "quests" -> statsData.questsCompleted = v;
            case "shards" -> setShardTotal(statsData, v);
            default -> {
                if (statsData.rareCatchData == null) statsData.rareCatchData = new HashMap<>();
                Stat<Integer, Integer> stat = statsData.rareCatchData.get(what);
                statsData.rareCatchData.put(what, Stat.of(v, stat == null || stat.caughtOn() == null ? statsData.fishTotal : stat.caughtOn()));
            }
        }
        this.needsUpdate = true;
    }

    /// Package-private for tests: the shard total becomes {@code value}, other shard kinds kept
    /// where they fit and the rest on the main one.
    static void setShardTotal(StatsDataModel data, int value) {
        String main = null;
        int others = 0;
        for (Map.Entry<String, Stat<Integer, Integer>> e : data.itemData.entrySet()) {
            if (!e.getKey().toLowerCase(java.util.Locale.ROOT).contains("shard")) continue;
            if (main == null || e.getKey().length() < main.length()) main = e.getKey();
        }
        if (main == null) main = "shard";
        for (Map.Entry<String, Stat<Integer, Integer>> e : data.itemData.entrySet()) {
            if (e.getKey().equals(main) || !e.getKey().toLowerCase(java.util.Locale.ROOT).contains("shard")) continue;
            int amount = e.getValue() == null || e.getValue().amount() == null ? 0 : e.getValue().amount();
            if (others + amount > value) {
                amount = value - others;
                e.setValue(Stat.of(amount, e.getValue() == null ? null : e.getValue().caughtOn()));
            }
            others += amount;
        }
        Stat<Integer, Integer> stat = data.itemData.get(main);
        data.itemData.put(main, Stat.of(value - others, stat == null || stat.caughtOn() == null ? data.fishTotal : stat.caughtOn()));
    }

    /// From Edit All-Time Stats: how many of a rarity / variant / size you've caught, keeping its streak.
    public void setFishCountManually(String category, String field, int value) {
        Map<String, Stat<Integer, Integer>> fields = statsData.fishData.computeIfAbsent(category, k -> new HashMap<>());
        Stat<Integer, Integer> stat = fields.get(field);
        fields.put(field, Stat.of(Math.max(0, value), stat == null || stat.caughtOn() == null ? statsData.fishTotal : stat.caughtOn()));
        this.needsUpdate = true;
    }

    public void addQuestCompleted() {
        statsData.questsCompleted++;
        this.needsUpdate = true;
    }

    public void updateData(String set, int value) {
        switch (set) {
            case "fish" -> setFishTotalKeepingStreaks(value);
            case "pet" -> {
                this.statsData.petTotal = value;
                this.needsUpdate = true;
            }
        }
    }

    public void updateData(String set, String category, String field, String type, int value) {
        switch (set) {
            case "fish" -> {
                Map<String, Stat<Integer, Integer>> statsData = this.statsData.fishData.get(category);
                Stat<Integer, Integer> stat = statsData.get(field);

                switch (type) {
                    case "amount" -> stat = new Stat<>(value, stat.caughtOn);
                    case "caught_on" -> stat = new Stat<>(stat.amount, value);
                }

                statsData.put(field, stat);
                this.statsData.fishData.put(category, statsData);
                this.needsUpdate = true;
            }
            case "pet" -> {
                if(Objects.equals(category, "rating")) field = TextHelper.smallCaps(field);
                Map<String, Stat<Integer, Integer>> statsData = this.statsData.petData.get(category);
                Stat<Integer, Integer> stat = statsData.get(field);

                switch (type) {
                    case "amount" -> stat = new Stat<>(value, stat.caughtOn);
                    case "caught_on" -> stat = new Stat<>(stat.amount, value);
                }

                statsData.put(field, stat);
                this.statsData.petData.put(category, statsData);
                this.needsUpdate = true;
            }
        }
    }

    public void updateData(String set, String field, String type, int value) {
        switch (set) {
            case "item" -> {
                Stat<Integer, Integer> stat = this.statsData.itemData.get(field);

                switch (type) {
                    case "amount" -> stat = new Stat<>(value, stat.caughtOn);
                    case "caught_on" -> stat = new Stat<>(stat.amount, value);
                }

                this.statsData.itemData.put(field, stat);
                this.needsUpdate = true;
            }
        }
    }

    public void updateImportStats(boolean updatedStats, @NotNull Map<String, Map<String, Stat<Integer, Integer>>> newData) {
        if(updatedStats) {
            this.statsData.fishData = newData;
            this.needsUpdate = true;
        }
    }

    public void resetStats() {
        this.reset();
        this.needsUpdate = true;
    }

    private void reset() {
        statsData.fishData = new HashMap<>();
        statsData.petData = new HashMap<>();
        statsData.itemData = new HashMap<>();
        statsData.fishTotal = 0;
        statsData.petTotal = 0;

        statsData.questPetData = new HashMap<>();
        statsData.questPetTotal = 0;
        statsData.questItemData = new HashMap<>();

        this.needsUpdate = true;
    }
    //endregion

    //region Model
    public static class StatsDataModel extends DataModels.DataModel {
        public static final String STATS_DATA_MODEL_VERSION = "0.3";

        /// FISHING

        /**
         * Fish
         * - Rarities
         * - Size
         * - Variants
         * Pair: Amount, Drystreak
         */
        public Map<String, Map<String, Stat<Integer, Integer>>> fishData = new HashMap<>();
        public int fishTotal = 0;

        /**
         * Pet
         * - Rarities
         * - Rating
         * Pair: Amount, Drystreak
         */
        public Map<String, Map<String, Stat<Integer, Integer>>> petData = new HashMap<>();
        public int petTotal = 0;

        /**
         * Other items
         */
        public Map<String, Stat<Integer, Integer>> itemData = new HashMap<>();

        /// Rare pulls announced in chat ("RARE CATCH! You pulled 1x Infusion Capsule"), keyed like
        /// "infusion_capsule": how many and the fish count at the last one (for the dry streak)
        public Map<String, Stat<Integer, Integer>> rareCatchData = new HashMap<>();

        /// Dry streaks of rare pulls that only drop during an event, like FOE: fish caught during
        /// that event since the last one ("infusion_capsule": Blood Moon catches,
        /// "lightning_bottle": thunderstorm catches). Missing = not known yet.
        public Map<String, Integer> rareEventStreak = new HashMap<>();

        /// Fish count at your last pet (any rarity), or null to work it out from the pet rarities.
        /// Set on every pet catch, by an import and by hand.
        public Integer petCaughtOn = null;

        /// QUESTS

        /**
         * Pet
         * - Rarities
         * - Rating
         */
        public Map<String, Map<String, Integer>> questPetData = new HashMap<>();
        public int questPetTotal = 0;

        /**
         * Other items
         */
        public Map<String, Integer> questItemData = new HashMap<>();

        /// Quests completed (counted from chat, used by the All-Time Stats panel)
        public int questsCompleted = 0;

        /// FOE-style fish timer: time spent fishing and fish caught during it (for fish/hour)
        public long fishTimeMs = 0L;
        public int fishTimeFish = 0;

        /// Set once stats from a previous FOE install have been imported
        public boolean importedFoeStats = false;

        /// Stats file of the other FOE-R install these stats were imported from (null if none), so
        /// the automatic import never runs twice
        public String importedFoerFrom = null;

        /// Set once FOE's dry streaks were imported with the corrected reading
        public boolean foeStreaksFixed = false;

        /// When this install last learned each dry streak first-hand (a catch here, or set by
        /// hand), keyed "fish:variant:albino", "pet:rarity:rare", "item:shard". Imports never
        /// replace these with older numbers.
        public Map<String, Long> streakKnownAt = new HashMap<>();

        public StatsDataModel() {
            super(STATS_DATA_MODEL_VERSION, null);
        }
    }

    public record Stat<Amount, CaughtOn>(Amount amount, CaughtOn caughtOn) {
        public static <Amount, CaughtOn> Stat<Amount, CaughtOn> of(Amount amount, CaughtOn caughtOn) {
            return new Stat<>(amount, caughtOn);
        }
    }
    //endregion

    //region Dev
    /// Field, Pair<Value, Tooltip>
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
                "statsData", Pair.of(Component.literal("[statsData]"), TextHelper.literal(getStatsData()))
        );
    }
    //endregion
}
