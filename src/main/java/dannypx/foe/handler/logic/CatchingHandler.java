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
        if (++petTicks % 10 == 0) safely("owned pets", this::notePets);
    }

    private int petTicks = 0;
    /// When each pet you have (in your inventory, or equipped) was first seen; 0 for those you already had when FOE+
    /// first looked. A pet seen long before is never taken for a new one (your equipped pet, wearing a skin, coming
    /// back into your inventory used to get a "QUEST PET!" line in place of the real reward).
    private final java.util.Map<java.util.UUID, Long> petFirstSeen = new java.util.HashMap<>();
    private boolean petsLooked = false;
    static final long NEW_PET_MS = 15_000L;

    private void notePets() {
        if (minecraft.player == null) {
            petsLooked = false;
            petFirstSeen.clear();
            return;
        }
        long now = System.currentTimeMillis();
        long when = petsLooked ? now : 0L;
        for (ItemStack stack : minecraft.player.getInventory().getNonEquipmentItems()) notePet(stack, when);
        try {
            PetTagObject equipped = InventoryHandler.instance().getCurrentPet();
            if (equipped != null && equipped.getID() != null) petFirstSeen.putIfAbsent(equipped.getID(), when);
        } catch (RuntimeException ignored) {}
        petsLooked = true;
    }

    private void notePet(ItemStack stack, long when) {
        if (stack == null || stack.isEmpty()) return;
        Pair<Boolean, TagObject> t = ValidateItem.isType(stack);
        if (!t.value1()) return;
        Pair<Boolean, PetTagObject> p = ValidateItem.isPet(t.value2());
        if (!p.value1()) return;
        try {
            java.util.UUID id = p.value2().getID();
            if (id != null) petFirstSeen.putIfAbsent(id, when);
        } catch (RuntimeException ignored) {}
    }

    /// A pet you had before now (seen a while ago, or your equipped one): not a new pet.
    private boolean hadBefore(PetTagObject pet) {
        try {
            java.util.UUID id = pet.getID();
            if (id == null) return false;
            PetTagObject equipped = InventoryHandler.instance().getCurrentPet();
            if (equipped != null && id.equals(equipped.getID())) return true;
            Long seen = petFirstSeen.get(id);
            return seen != null && System.currentTimeMillis() - seen > NEW_PET_MS;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void scanFish() {
        if(!scanDone && System.currentTimeMillis() < startScanTime + (Configs.handlerConfig.catchingStatusCooldown.get() * 1000L)) {
            Pair<Boolean, FishTagObject> foundFish = this.findFish();
            if(foundFish.value1()) {
                InventoryHandler.instance().trackAllFish();

                // Store to Stats
                Triplet<Pair<String, Integer>, Pair<String, Integer>, Pair<String, Integer>> prevStats = StatsDataHandler.instance().setFish(foundFish.value2());
                NotifierHandler.instance().notifyFish(foundFish.value2(), prevStats.value1(), prevStats.value2(), prevStats.value3());

                // Each panel gets the catch on its own: one that fails can't keep the catch from the others (a
                // failure here used to leave the daily quests waiting for /menu).
                FishTagObject caught = foundFish.value2();
                safely("catch sound", CatchSound::onCatch);
                safely("water rings", () -> dannypx.foe.handler.renderer.WaterRings.onCatch(caught.getRarity()));
                safely("big catch sound", () -> dannypx.foe.handler.store.NotifySounds.playCatch(caught.getRarity(), caught.getVariant(), caught.getFishSize()));
                safely("inventory full sound", () -> FullInventoryHandler.instance().onCatch());
                safely("variant pulse", dannypx.foe.handler.renderer.VariantPulse::onCatch);
                safely("big catch", () -> dannypx.foe.handler.renderer.BigCatchMoment.onCatch(caught));
                safely("quests", () -> QuestDataHandler.instance().setFish(caught));
                safely("contest", () -> ContestTrackerHandler.instance().onFishCaught(caught));
                safely("daily quests", () -> DailyQuestHandler.instance().onFishCaught(caught));
                safely("community event", () -> CommunityEventHandler.instance().onFishCaught());
                safely("mini event", () -> MiniEventHandler.instance().onFishCaught(caught));
                LoggerHandler._debug("Found Fish: " + foundFish.value2().getName().getString());

                CodeExecuterHandler.runLater(Configs.handlerConfig.catchingItemsDelayCheck.get(), this::checkForCaughtItems);

                lastCaughtFish = foundFish.value2();
                lastDataFish = prevStats;
                CodeExecuterHandler.runLater(1, EventHandler.instance()::onCatch);
                this.scanDone = true;
            }
        } else if (!scanDone && System.currentTimeMillis() > startScanTime + (Configs.handlerConfig.catchingStatusCooldown.get() * 1000L)){
            this.scanDone = true;
            // in the log, so a catch that was missed (no variant line, no dry streak) can be looked into
            LoggerHandler.info("catch not found: looked for \"" + fishNameToFind + "\" among your new fish");
        }
    }

    private static void safely(String what, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            LoggerHandler.error(e);
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
                // a pet you already had (moved, unequipped, its skin changed) is no new pet
                if (hadBefore(validatePet.value2())) return;
                // each pet once: it can be found both after a catch and from the server's line about it
                if (!firstTime(validatePet.value2())) return;
                // Only a pet fished up ("PET DROP!") counts; one from a contest, an event or a quest gets its own popup
                PetSourceHandler.Source source = PetSourceHandler.instance().sourceOfNewPet();
                PetSourceHandler.instance().used(source);
                if (source == PetSourceHandler.Source.FISHED) {
                    Pair<Pair<String, Integer>, Pair<String, Integer>> prevStats = StatsDataHandler.instance().setPet(validatePet.value2());
                    lastCaughtPet = validatePet.value2();
                    lastDataPet = prevStats;
                } else if (source == PetSourceHandler.Source.UNKNOWN) {
                    // the reward line ("QUEST Complete [#3] ... 1x Fox Pet") can come a moment after the pet:
                    // look again in two seconds
                    ItemStack copy = itemStack.copy();
                    PetTagObject pet = validatePet.value2();
                    CodeExecuterHandler.runLater(40, () -> RewardPets.notify(copy, pet, PetSourceHandler.instance().sourceOfNewPet()));
                } else {
                    RewardPets.notify(itemStack, validatePet.value2(), source);
                }
            } else {
                Pair<String, Integer> prevStats = StatsDataHandler.instance().setOtherItem(validatedItem.value2(), count);
                lastCaughtItems.add(Triplet.of(validatedItem.value2(), count, prevStats));
                DailyQuestHandler.instance().onItemCaught(validatedItem.value2(), count);
            }
            LoggerHandler._debug("Found Item: " + itemStack.getHoverName().getString(), itemStack);
        }
    }

    /// The pets already handled (their ids), so none gets two lines.
    private final java.util.Set<String> handledPets = new java.util.LinkedHashSet<>();

    /// Whether this pet is new to us (and from now on it isn't).
    private boolean firstTime(PetTagObject pet) {
        String key;
        try {
            java.util.UUID id = pet.getID();
            key = id != null ? id.toString() : pet.getName().getString() + "|" + pet.getTotalPercent();
        } catch (RuntimeException e) {
            key = pet.getName().getString() + "|" + pet.getTotalPercent();
        }
        if (!handledPets.add(key)) return false;
        if (handledPets.size() > 200) handledPets.remove(handledPets.iterator().next());
        return true;
    }

    /// The server said you got a pet ("PET DROP!", or a contest, event or quest reward): the pets that came into
    /// your inventory in the last few seconds that weren't handled yet get their line and count now. So a pet is
    /// never missed when its catch wasn't found (that used to leave it without a line or a count).
    public void lookForNewPets() {
        if (minecraft.player == null) return;
        long now = System.currentTimeMillis();
        InventoryHandler.instance().getSnapshottedItems().stream()
                .filter(item -> now - item.value1() < 8_000L)
                .toList()
                .forEach(item -> {
                    Pair<Boolean, TagObject> t = ValidateItem.isType(item.value2());
                    if (t.value1() && ValidateItem.isPet(t.value2()).value1()) scanItem(item.value2(), item.value3());
                });
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
                    && sameFishName(fishNameToFind, itemStack.getHoverName().getString())
            ) {
                return validatedFish.value2();
            }
        }
        return null;
    }

    /// Whether the catch's name on screen (`shown`, with the server's badges) is this item's name (`item`): by their
    /// letters alone, either holding the other (a variant's item or title can carry "Albino" where the other
    /// doesn't, and missed the catch: no variant line, no dry streak). Package-private for tests.
    static boolean sameFishName(String shown, String item) {
        if (shown == null || item == null) return false;
        if (shown.contains(item)) return true;
        String a = letters(shown), b = letters(item);
        if (a.length() < 3 || b.length() < 3) return false;
        return a.contains(b) || b.contains(a);
    }

    /// A name's ordinary letters and spaces only (small capitals made plain), lower case, single spaces.
    static String letters(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            char n = dannypx.foe.helper.TextHelper.normalLetter(c);
            if (n < 0x3000 && Character.isLetter(n)) b.append(Character.toLowerCase(n));
            else if (b.length() > 0 && b.charAt(b.length() - 1) != ' ') b.append(' ');
        }
        return b.toString().trim();
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
