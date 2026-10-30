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

        if(statsData.questItemData == null) statsData.questItemData = new HashMap<>();
        if(statsData.questPetData == null) statsData.questItemData = new HashMap<>();
    }

    public Triplet<Pair<String, Integer>, Pair<String, Integer>, Pair<String, Integer>> setFish(FishTagObject fish) {
        // Dry streak of this fish's variant: fish since the last one, counting this catch.
        Map<String, Stat<Integer, Integer>> variants = statsData.fishData.getOrDefault(FishTagObject.VARIANT, Map.of());
        Stat<Integer, Integer> lastOfVariant = variants.get(fish.getVariant());
        int variantDryStreak = lastOfVariant == null || lastOfVariant.amount() == null || lastOfVariant.amount() == 0
                ? statsData.fishTotal + 1 : statsData.fishTotal + 1 - lastOfVariant.caughtOn();
        statsData.fishTotal++;

        Pair<String, Integer> rarityDrystreak = this.updateFishData(statsData, FishTagObject.RARITY, fish.getRarity(), 1);
        ConstantDataHandler.instance().updateFishData(FishTagObject.RARITY, fish.getRarity(), fish.getRarityComponent());

        Pair<String, Integer> variantDrystreak = this.updateFishData(statsData, FishTagObject.VARIANT, fish.getVariant(), 1);
        ConstantDataHandler.instance().updateFishData(FishTagObject.VARIANT, fish.getVariant(), fish.getVariantComponent());

        Pair<String, Integer> sizeDryStreak = this.updateFishData(statsData, FishTagObject.FISH_SIZE, fish.getFishSize(), 1);
        ConstantDataHandler.instance().updateFishData(FishTagObject.FISH_SIZE, fish.getFishSize(), fish.getFishSizeComponent());

        NotifierHandler.instance().chatVariant(fish, variantDryStreak);

        return Triplet.of(rarityDrystreak, variantDrystreak, sizeDryStreak);
    }



    // Field, Old Drystreak
    private Pair<String, Integer> updateFishData(StatsDataModel statsData, String category, String field, int valueToAdd) {
        Map<String, Stat<Integer, Integer>> categoryMapData = statsData.fishData.getOrDefault(category, new HashMap<>());
        Stat<Integer, Integer> fieldStat = categoryMapData.getOrDefault(field, Stat.of(0, statsData.fishTotal));

        Stat<Integer, Integer> newFieldStat = Stat.of(fieldStat.amount() + valueToAdd, statsData.fishTotal);
        categoryMapData.put(field, newFieldStat);
        statsData.fishData.put(category, categoryMapData);
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
        this.needsUpdate = true;

        return Pair.of(item, statsData.fishTotal - itemStat.caughtOn());
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
        categoryMap.put(field, Stat.of(stat.amount(), Math.max(0, statsData.fishTotal - Math.max(0, dryStreak))));
        statsData.fishData.put(category, categoryMap);
        this.needsUpdate = true;
    }

    public void markChanged() {
        this.needsUpdate = true;
    }

    public void addQuestCompleted() {
        statsData.questsCompleted++;
        this.needsUpdate = true;
    }

    public void updateData(String set, int value) {
        switch (set) {
            case "fish" -> {
                this.statsData.fishTotal = value;
                this.needsUpdate = true;
            }
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
