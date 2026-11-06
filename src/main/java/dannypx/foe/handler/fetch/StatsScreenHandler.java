package dannypx.foe.handler.fetch;

import dannypx.foe.handler.Handler;
import dannypx.foe.handler.logic.CodeExecuterHandler;
import dannypx.foe.handler.logic.NotifierHandler;
import dannypx.foe.handler.store.ConstantDataHandler;
import dannypx.foe.handler.store.ProfileDataHandler;
import dannypx.foe.handler.store.StatsDataHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.item.FishTagObject;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.type.tuple.Triplet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class StatsScreenHandler extends Handler {
    private static StatsScreenHandler INSTANCE = new StatsScreenHandler();

    public static StatsScreenHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new StatsScreenHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private boolean importStats = false;
    private boolean isScanned = false;
    private List<Component> statsLore = new ArrayList<>();

    public void setImportStats(boolean importStats) {
        this.importStats = importStats;
    }

    public List<Component> getStatsLore() {
        return statsLore;
    }
    //endregion

    //region Methods
    public void init(ContainerScreen screen) {
        isScanned = false;

        ScreenEvents.afterTick(screen).register(screen1 -> {
            this.tick(screen);
        });
    }

    public void tick(ContainerScreen screen) {
        if(!isScanned
                && screen.getMenu().slots.get(23).getItem().getItem() == Items.KNOWLEDGE_BOOK
        ) {
            isScanned = true;
            this.checkStats(screen.getMenu());
        }
    }

    public void checkStats(ChestMenu chestMenu) {
        if(this.importStats) {
            CodeExecuterHandler.runLater(2, () -> {
                Slot statSlot = chestMenu.getSlot(23);
                Pair<Boolean, Map<String, Map<String, StatsDataHandler.Stat<Integer, Integer>>>> completed = this.extractData(statSlot.getItem());

                if(completed.value1()) {
                    ProfileDataHandler.instance().updateImportStats(true);
                    StatsDataHandler.instance().updateImportStats(true, completed.value2());
                    NotifierHandler.instance().notifyImportStatsCompleted();
                }
            });

            this.importStats = false;
        }
    }

    private Pair<Boolean, Map<String, Map<String, StatsDataHandler.Stat<Integer, Integer>>>> extractData(ItemStack stack) {
        if(stack.get(DataComponents.LORE) != null) {
            List<Component> loreLines = stack.get(DataComponents.LORE).lines();
            this.statsLore = loreLines;
            if(loreLines.size() > 7) {
                Map<String, Map<String, StatsDataHandler.Stat<Integer, Integer>>> newData = StatsDataHandler.instance().getStatsData().fishData;

                int totalFish = this.extractTotal(loreLines.get(6));
                // The server only knows counts, not dry streaks: keep every streak you had (fish,
                // pets, shards, rare pulls) across the new fish total. After the shift each
                // streak is already right for the new total, so keepStreak leaves it as it is.
                StatsDataHandler.instance().setFishTotalKeepingStreaks(totalFish);
                int oldTotal = totalFish;

                // Rarity
                for (int i = 8; i < 13; i++) {
                    Component line = loreLines.get(i);
                    Triplet<Boolean, String, Integer> data =
                            this.extractStat(ConstantDataHandler.instance().getConstantData().fishData.getOrDefault(FishTagObject.RARITY, new HashMap<>()), line);

                    if(data.value1()) {
                        Map<String, StatsDataHandler.Stat<Integer, Integer>> newCategoryData = newData
                                .getOrDefault(FishTagObject.RARITY, new HashMap<>());

                        newCategoryData.put(data.value2(), new StatsDataHandler.Stat<>(data.value3(), keepStreak(newCategoryData.get(data.value2()), oldTotal, totalFish)));

                        newData.put(FishTagObject.RARITY, newCategoryData);
                    }
                }

                // Fish Size
                for (int i = 14; i < 19; i++) {
                    Component line = loreLines.get(i);
                    Triplet<Boolean, String, Integer> data =
                            this.extractStat(ConstantDataHandler.instance().getConstantData().fishData.getOrDefault(FishTagObject.FISH_SIZE, new HashMap<>()), line);

                    if(data.value1()) {
                        Map<String, StatsDataHandler.Stat<Integer, Integer>> newCategoryData = newData
                                .getOrDefault(FishTagObject.FISH_SIZE, new HashMap<>());

                        newCategoryData.put(data.value2(), new StatsDataHandler.Stat<>(data.value3(), keepStreak(newCategoryData.get(data.value2()), oldTotal, totalFish)));

                        newData.put(FishTagObject.FISH_SIZE, newCategoryData);
                    }
                }

                // Variant
                AtomicInteger normalCount = new AtomicInteger(totalFish);
                for (int i = 20; i < 24; i++) {
                    Component line = loreLines.get(i);
                    Triplet<Boolean, String, Integer> data =
                            this.extractStat(ConstantDataHandler.instance().getConstantData().fishData.getOrDefault(FishTagObject.VARIANT, new HashMap<>()), line);

                    if(data.value1()) {
                        Map<String, StatsDataHandler.Stat<Integer, Integer>> newCategoryData = newData
                                .getOrDefault(FishTagObject.VARIANT, new HashMap<>());

                        normalCount.set(normalCount.get() - data.value3());
                        newCategoryData.put(data.value2(), new StatsDataHandler.Stat<>(data.value3(), keepStreak(newCategoryData.get(data.value2()), oldTotal, totalFish)));

                        newData.put(FishTagObject.VARIANT, newCategoryData);
                    }
                }

                Map<String, StatsDataHandler.Stat<Integer, Integer>> variants = newData.getOrDefault(FishTagObject.VARIANT, new HashMap<>());
                variants.put("normal", new StatsDataHandler.Stat<>(normalCount.get(), keepStreak(variants.get("normal"), oldTotal, totalFish)));

                return Pair.ofTrue(newData);
            }
        }
        return Pair.ofFalse(new HashMap<>());
    }

    /// The "caught on" value that keeps an existing dry streak with the new fish total; a field
    /// you had no record of starts at 0 like before.
    public static int keepStreak(StatsDataHandler.Stat<Integer, Integer> old, int oldTotal, int newTotal) {
        if (old == null || old.caughtOn() == null) return newTotal;
        int streak = Math.max(0, oldTotal - old.caughtOn());
        return newTotal - streak;
    }

    private Triplet<Boolean, String, Integer> extractStat(Map<String, Component> constants, Component line) {
        if(line.getSiblings().size() > 2) {
            String field = line.getSiblings().get(1).getString().trim();
            String key = ConstantDataHandler.keysFromField(constants, field).findFirst().orElse(null);
            if(key != null) {
                int amount = TextHelper.toIntFromString(line.getSiblings().get(2).getString());

                return Triplet.ofTrue(key, amount);
            }
        }
        return Triplet.ofFalse("", 0);
    }

    private int extractTotal(Component component) {
        if(component.getSiblings().size() > 1) {
            return TextHelper.toIntFromString(component.getSiblings().get(2).getString());
        }
        return 1;
    }
    //endregion

    //region Dev
    /// Field, Pair<Value, Tooltip>
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
                "statsLore", Pair.of(Component.literal("[statsLore]"), TextHelper.literal(getStatsLore()))
        );
    }
    //endregion
}
