package dannypx.foe.handler.logic;

import dannypx.foe.handler.Handler;
import dannypx.foe.handler.store.QuestDataHandler;
import dannypx.foe.handler.store.StatsDataHandler;
import dannypx.foe.item.FishTagObject;
import dannypx.foe.item.PetTagObject;
import dannypx.foe.item.TagObject;
import dannypx.foe.item.ValidateItem;
import dannypx.foe.type.placeholder.PlaceholderValue;
import dannypx.foe.type.placeholder.StringValue;
import dannypx.foe.type.placeholder.ComponentValue;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.config.Configs;
import dannypx.foe.type.tuple.Triplet;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public class CatchingHandler extends Handler {
    private static CatchingHandler INSTANCE = new CatchingHandler();

    public static CatchingHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new CatchingHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private long startScanTime = 0L;
    private boolean scanDone = true;
    private String fishNameToFind = "";

    private FishTagObject lastCaughtFish = FishTagObject.empty();
    private PetTagObject lastCaughtPet = PetTagObject.empty();
    // Rarity Variant Size
    private Triplet<Pair<String, Integer>, Pair<String, Integer>, Pair<String, Integer>> lastDataFish = null;

    // Rarity Rating
    private Pair<Pair<String, Integer>, Pair<String, Integer>> lastDataPet = null;

    // Item RawItemName
    private List<Triplet<TagObject, Integer, Pair<String, Integer>>> lastCaughtItems = new ArrayList<>();

    public boolean isScanDone() {
        return scanDone;
    }

    public FishTagObject getLastCaughtFish() {
        return lastCaughtFish;
    }

    public Triplet<Pair<String, Integer>, Pair<String, Integer>, Pair<String, Integer>> getLastDataFish() {
        return lastDataFish;
    }

    public List<Triplet<TagObject, Integer, Pair<String, Integer>>> getLastCaughtItems() {
        return Collections.unmodifiableList(lastCaughtItems);
    }

    public PetTagObject getLastCaughtPet() {
        return lastCaughtPet;
    }

    public Pair<Pair<String, Integer>, Pair<String, Integer>> getLastDataPet() {
        return lastDataPet;
    }
    //endregion

    //region Methods
    public void tick() {
        this.scanFish();
    }

    private void scanFish() {
        if(!scanDone && System.currentTimeMillis() < startScanTime + (Configs.handlerConfig.catchingStatusCooldown.get() * 1000L)) {
            Pair<Boolean, FishTagObject> foundFish = this.findFish();
            if(foundFish.value1()) {
                InventoryHandler.instance().trackAllFish();

                // Store to Stats
                Triplet<Pair<String, Integer>, Pair<String, Integer>, Pair<String, Integer>> prevStats = StatsDataHandler.instance().setFish(foundFish.value2());
                NotifierHandler.instance().notifyFish(foundFish.value2(), prevStats.value1(), prevStats.value2(), prevStats.value3());

                QuestDataHandler.instance().setFish(foundFish.value2());
                ContestTrackerHandler.instance().onFishCaught(foundFish.value2());
                DailyQuestHandler.instance().onFishCaught(foundFish.value2());
                LoggerHandler._debug("Found Fish: " + foundFish.value2().getName().getString());

                CodeExecuterHandler.runLater(Configs.handlerConfig.catchingItemsDelayCheck.get(), this::checkForCaughtItems);

                lastCaughtFish = foundFish.value2();
                lastDataFish = prevStats;
                CodeExecuterHandler.runLater(1, EventHandler.instance()::onCatch);
                this.scanDone = true;
            }
        } else if (!scanDone && System.currentTimeMillis() > startScanTime + (Configs.handlerConfig.catchingStatusCooldown.get() * 1000L)){
            this.scanDone = true;
            LoggerHandler._debug("Did not find fish");
        }
    }

    private void checkForCaughtItems() {
        LoggerHandler._debug("Start finding items");
        LoggerHandler._debug("Start Time: " + System.currentTimeMillis());
        LoggerHandler._debug("Search Window: " + (Configs.handlerConfig.catchingItemsCheckWindow.get() + (System.currentTimeMillis() - startScanTime)));
        this.lastCaughtItems.clear();
        if(minecraft.player != null) {
            InventoryHandler.instance().getSnapshottedItems().stream()
                    .filter(item -> System.currentTimeMillis() - item.value1()
                            < Configs.handlerConfig.catchingItemsCheckWindow.get() + (System.currentTimeMillis() - startScanTime))
                    .toList().forEach(item -> scanItem(item.value2(), item.value3()));
        }
    }

    private void scanItem(ItemStack itemStack, int count) {
        Pair<Boolean, TagObject> validatedItem = ValidateItem.isType(itemStack);

        if(validatedItem.value1()) {
            // Store to Stats
            Pair<Boolean, PetTagObject> validatePet = ValidateItem.isPet(validatedItem.value2());
            if(validatePet.value1()) {
                Pair<Pair<String, Integer>, Pair<String, Integer>> prevStats = StatsDataHandler.instance().setPet(validatePet.value2());
                lastCaughtPet = validatePet.value2();
                lastDataPet = prevStats;
            } else {
                Pair<String, Integer> prevStats = StatsDataHandler.instance().setOtherItem(validatedItem.value2(), count);
                lastCaughtItems.add(Triplet.of(validatedItem.value2(), count, prevStats));
                DailyQuestHandler.instance().onItemCaught(validatedItem.value2(), count);
            }
            LoggerHandler._debug("Found Item: " + itemStack.getHoverName().getString(), itemStack);
        }
    }

    private Pair<Boolean, FishTagObject> findFish() {
        FishTagObject inventoryFish = this.findFishInInventory();
        if(inventoryFish != null) return Pair.ofTrue(inventoryFish);

        FishTagObject worldFish = this.findFishInWorld();
        if(worldFish != null) return Pair.ofTrue(worldFish);

        return Pair.ofFalse(FishTagObject.empty());
    }

    private FishTagObject findFishInInventory() {
        AtomicReference<FishTagObject> foundItemStack = new AtomicReference<>();

        if(minecraft.player != null) {
            minecraft.player.getInventory().getNonEquipmentItems().forEach(itemStack -> {
                FishTagObject validatedFish = validateFish(itemStack);
                if(validatedFish != null && foundItemStack.get() == null) foundItemStack.set(validatedFish);
            });
        }
        return foundItemStack.get();
    }

    private FishTagObject findFishInWorld() {
        AtomicReference<FishTagObject> foundItemStack = new AtomicReference<>();

        if(minecraft.player != null
                && minecraft.level != null
        ) {
            ClientLevel world = minecraft.level;
            AABB searchBoundingBox = minecraft.player.getBoundingBox().inflate(10d);

            List<ItemEntity> itemEntities = world.getEntitiesOfClass(
                    ItemEntity.class,
                    searchBoundingBox,
                    itemEntity -> {
                        ItemStack itemStack = itemEntity.getItem();
                        FishTagObject validatedFish = validateFish(itemStack);
                        return validatedFish != null;
                    }
            );

            if(!itemEntities.isEmpty() && foundItemStack.get() == null) {
                foundItemStack.set(ValidateItem.isFish(itemEntities.getFirst().getItem()).value2());
            }
        }
        return foundItemStack.get();
    }

    private FishTagObject validateFish(ItemStack itemStack) {
        if(!itemStack.isEmpty()) {
            Pair<Boolean, FishTagObject> validatedFish = ValidateItem.isFish(itemStack);
            if(validatedFish.value1()
                    && validatedFish.value2().isOwn()
                    && !InventoryHandler.instance().getTrackedFish().contains(validatedFish.value2().getID())
                    && fishNameToFind.contains(itemStack.getHoverName().getString())
            ) {
                return validatedFish.value2();
            }
        }
        return null;
    }

    public void scanFishListener(Component title) {
        if(scanDone
                && System.currentTimeMillis() - startScanTime > Configs.handlerConfig.catchingFishCheckWindow.get()
        ) {
            if(title.getString().length() != 1 || title.equals(Component.empty())) {
                return;
            }

            if(title.getString().charAt(0) > 0xE000 && title.getString().charAt(0) < 0xE999) {
                this.startScan();
                LoggerHandler._debug("Start finding fish [Title]");
            }
        }
    }

    public void scanFishNameListener(Component subTitle) {
        if(subTitle.equals(Component.empty()) || subTitle.getString().isBlank()) {
            return;
        }

        if(subTitle.getString().charAt(0) > 0xF000 && subTitle.getString().charAt(0) < 0xF999) {
            fishNameToFind = subTitle.getString();
        }
    }

    public void scanFishListener(String fishNameToFind) {
        if(scanDone
                && System.currentTimeMillis() - startScanTime > Configs.handlerConfig.catchingFishCheckWindow.get()
        ) {
            this.scanFishNameListener(fishNameToFind);
            this.startScan();
            LoggerHandler._debug("Start finding fish [Fish Summary]");
        }
    }

    public void scanFishNameListener(String fishNameToFind) {
        if(!fishNameToFind.isBlank()) {
            this.fishNameToFind = fishNameToFind;
        }
    }

    private void startScan() {
        startScanTime = System.currentTimeMillis();
        scanDone = false;
    }
    //endregion

    //region Dev
    /// Field, Pair<Value, Tooltip>
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
                "key", Pair.of(Component.literal("value"), Component.empty())
        );
    }
    //endregion
}
