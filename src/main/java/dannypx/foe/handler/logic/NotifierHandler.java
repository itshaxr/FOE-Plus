package dannypx.foe.handler.logic;

import dannypx.foe.handler.Handler;
import dannypx.foe.handler.store.*;
import dannypx.foe.helper.ItemStackHelper;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.item.FishTagObject;
import dannypx.foe.item.TagObject;
import dannypx.foe.item.PetTagObject;
import dannypx.foe.placeholder.evaluator.PlaceholderResult;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.config.Configs;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

public class NotifierHandler extends Handler {
    private static NotifierHandler INSTANCE = new NotifierHandler();

    public static NotifierHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new NotifierHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private final List<Notification> notifications = new ArrayList<>();
    private final List<UUID> removeQueue = new ArrayList<>();
    private final Map<String, UUID> persistentNotifications = new HashMap<>();

    public static final String IMPORT_STATS_KEY = "importStatsKey";
    public static final String IMPORT_CREW_KEY = "importCrewKey";
    public static final String EMPTY_SLOTS_KEY = "emptySlotsKey";

    private final int WIDTH = 200;
    private final int BOX_PADDING = 7;
    private final int CONTENT_WIDTH = WIDTH - BOX_PADDING * 2 - 2;
    private final int ICON_CONTENT_WIDTH = CONTENT_WIDTH - 18;

    public List<Notification> getNotifications() {
        return notifications;
    }
    //endregion

    //region Methods
    public void init() {
        if(!ProfileDataHandler.instance().getProfileData().hasImportedStats) {
            this.notifyImportStats();
        }
    }

    public void tick() {
        boolean isRemoved = notifications.removeIf(notification -> removeQueue.contains(notification.uuid));
        if(isRemoved) removeQueue.clear();

        notifications.forEach(notification -> {
            if(System.currentTimeMillis() > notification.startTime + notification.notificationTime * 1000L) {
                this.removeNotification(notification.uuid);
            }
        });
    }

    public UUID addNotification(Notification notification) {
        notifications.add(notification);
        return notification.uuid;
    }

    public void removeNotification(UUID uuid) {
        removeQueue.add(uuid);
    }

    public void removeNotification(String key) {
        if(persistentNotifications.containsKey(key)) {
            UUID uuid = persistentNotifications.remove(key);
            this.removeNotification(uuid);
        }
    }

    public void notifyFish(
            FishTagObject fish,
            Pair<String, Integer> rarityDrystreak,
            Pair<String, Integer> variantDrystreak,
            Pair<String, Integer> sizeDryStreak
    ) {
        if(!Configs.hudConfig.showFishCatchNotification.get()) {
            return;
        }

        // FOE-style floating catch text (ported from FOECollab)
        if(Configs.hudConfig.fishCatchStyle.get() == dannypx.foe.type.CatchPopupStyle.FOE) {
            dannypx.foe.screens.element.hud.CatchPopupElement.show(fish);
            return;
        }

        Component tagComponent = !Objects.equals(fish.getVariant(), "normal")
                ? TextHelper.concat(fish.getVariantComponent(), Component.literal(" "), fish.getRarityComponent())
                : TextHelper.concat(fish.getRarityComponent());
        Component rarityComponent = fish.getRarityComponent();
        Component variantComponent = fish.getVariantComponent();
        Component sizeComponent = fish.getFishSizeComponent();

        Component lengthComponent = TextHelper.concat(
                Component.literal(TextHelper.floatToString(fish.getLength(), 2)).withStyle(ChatFormatting.GRAY),
                Component.literal("in ").withStyle(ChatFormatting.GRAY)
        );

        Component weightComponent = TextHelper.concat(
                Component.literal(TextHelper.floatToString(fish.getWeight(), 2)).withStyle(ChatFormatting.GRAY),
                Component.literal("lb ").withStyle(ChatFormatting.GRAY)
        );

        List<Component> notificationComponentList = new ArrayList<>(Arrays.asList(
                tagComponent,
                fish.getName(),
                TextHelper.concat(fish.getFishSizeComponent(), Component.literal(" "), lengthComponent, weightComponent),
                Component.empty(),
                Component.literal(" - Drystreaks before catch").withStyle(ChatFormatting.GRAY),
                TextHelper.concat(rarityComponent, Component.literal(" "), TextHelper.literal(rarityDrystreak.value2())),
                TextHelper.concat(sizeComponent, Component.literal(" "), TextHelper.literal(sizeDryStreak.value2()))
        ));

        if(!Objects.equals(fish.getVariant(), "normal")) {
            notificationComponentList.add(TextHelper.concat(variantComponent, Component.literal(" "), TextHelper.literal(variantDrystreak.value2())));
        }

        int rows = !Configs.hudConfig.showFishDrystreakNotification.get() ? 3 : notificationComponentList.size();

        this.addNotification(
                new NotifierHandler.Notification(fish.getItemStack(),
                        rows, 1,
                        Configs.hudConfig.fishDismissalTime.get(),
                        notificationComponentList
                )
        );
    }

    public void notifyPet(PetTagObject pet, Pair<String, Integer> rarityDrystreak, Pair<String, Integer> ratingDrystreak) {
        if(!Configs.hudConfig.showPetCatchNotification.get()) {
            return;
        }

        Component petComponent = TextHelper.concat(pet.getRarityComponent(), Component.literal(" ") , pet.getName());

        List<Component> notificationComponentList =  new ArrayList<>(Arrays.asList(
                petComponent,
                pet.getRatingComponent(),
                Component.empty(),
                Component.literal(" - Drystreaks before catch").withStyle(ChatFormatting.GRAY),
                TextHelper.concat(pet.getRarityComponent(), Component.literal(" "), TextHelper.literal(rarityDrystreak.value2())),
                TextHelper.concat(pet.getRatingComponent(), Component.literal(" "), TextHelper.literal(ratingDrystreak.value2()))
        ));

        int rows = !Configs.hudConfig.showPetsDrystreakNotification.get() ? 2 : notificationComponentList.size();

        this.addNotification(
                new Notification(pet.getItemStack(),
                        rows, 1,
                        Configs.hudConfig.petDismissalTime.get(),
                        notificationComponentList
                )
        );
    }

    private static final java.util.Map<String, Integer> VARIANT_COLORS = java.util.Map.of(
            "albino", 0xF2F2F2, "melanistic", 0x5A5A6E, "trophy", 0xFFC93C, "fabled", 0xFC3E35);

    /// Chat line for every variant fish (albino, melanistic, trophy, fabled): the fish, its
    /// variant and the dry streak (fish since your last of that variant, this catch included).
    public void chatVariant(FishTagObject fish, int dryStreak) {
        String variant = fish.getVariant().toLowerCase(java.util.Locale.ROOT);
        if (!Configs.hudConfig.showVariantCatchChat.get() || minecraft.player == null || !VARIANT_COLORS.containsKey(variant)) return;
        // [FOE+] You caught <variant badge> <fish picture> Largemouth Bass · Dry streak 1,234
        dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.prefix()
                .append(dannypx.foe.helper.FoeChat.text("You caught "))
                .append(variantBadge(variant, fish))
                .append(Component.literal(" "))
                .append(dannypx.foe.helper.FoeChat.itemIcon(fish.getItemStack()))
                .append(fish.getName())
                .append(dannypx.foe.helper.FoeChat.dryStreak(dryStreak)));
    }

    /// The server's badge box for a variant (the same glyphs it uses on items), so the line always
    /// shows the box, never the word. Falls back to the badge from the fish's own lore.
    private static final java.util.Map<String, String> VARIANT_BADGES = java.util.Map.of(
            "albino", "\uF041", "melanistic", "\uF042", "trophy", "\uF043", "fabled", "\uF044");

    private static Component variantBadge(String variant, FishTagObject fish) {
        String glyph = VARIANT_BADGES.get(variant);
        if (glyph != null) return Component.literal(glyph).withStyle(ChatFormatting.WHITE);
        return fish.getVariantComponent();
    }

    /// [FOE+] You caught <GIGANTIC badge> <fish picture> Largemouth Bass · Dry streak 1,234
    public void chatGigantic(FishTagObject fish, int dryStreak) {
        if (!Configs.hudConfig.showGiganticCatchChat.get() || minecraft.player == null) return;
        dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.prefix()
                .append(dannypx.foe.helper.FoeChat.text("You caught "))
                .append(dannypx.foe.screens.element.hud.StatsPanelElement.GIGANTIC_BADGE)
                .append(Component.literal(" "))
                .append(dannypx.foe.helper.FoeChat.itemIcon(fish.getItemStack()))
                .append(fish.getName())
                .append(dannypx.foe.helper.FoeChat.dryStreak(dryStreak)));
    }

    /// [FOE+] PET PULLED! <rarity badge> Eagle Pet (87.0% A) · Dry streak 164
    public void chatPet(PetTagObject pet, int petDryStreak, int rarityDryStreak) {
        if (!Configs.hudConfig.showPetCatchChat.get() || minecraft.player == null) return;
        MutableComponent line = dannypx.foe.helper.FoeChat.line("PET PULLED!", dannypx.foe.helper.FoeChat.PURPLE)
                .append(rarityBadge(pet))
                .append(Component.literal(" "))
                .append(pet.getName())
                .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.format(java.util.Locale.ROOT, "%.1f%%", pet.getTotalPercent() * 100f)).withStyle(ChatFormatting.WHITE));
        Component rating = pet.getRatingComponent();
        if (!rating.getString().isBlank()) line.append(Component.literal(" ")).append(TextHelper.substring(rating, 0, 1));
        line.append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY))
                .append(dannypx.foe.helper.FoeChat.dryStreak(petDryStreak));
        dannypx.foe.helper.FoeChat.send(line);
    }

    /// The server's rarity badge for the pet (the same glyphs as on items and in chat).
    private static Component rarityBadge(PetTagObject pet) {
        String glyph = switch (pet.getRarity().toLowerCase(java.util.Locale.ROOT)) {
            case "common" -> "\uF033";
            case "rare" -> "\uF034";
            case "epic" -> "\uF035";
            case "legendary" -> "\uF036";
            case "mythical", "mythic" -> "\uF037";
            case "special" -> "\uF092";
            default -> null;
        };
        return glyph != null ? Component.literal(glyph).withStyle(ChatFormatting.WHITE) : pet.getRarityComponent();
    }

    public void notifyItem(TagObject item, int count, Pair<String, Integer> itemDrystreak) {
        if(!Configs.hudConfig.showOtherItemCatchNotification.get()) {
            return;
        }

        Component itemComponent = TextHelper.concat(item.getName(), Component.literal(" "), TextHelper.literal(count), Component.literal("x").withStyle(ChatFormatting.GRAY));
        Component typeComponent = Component.literal(TextHelper.convertField(itemDrystreak.value1()));


        List<Component> notificationComponentList =  new ArrayList<>(Arrays.asList(
                itemComponent,
                Component.empty(),
                Component.literal(" - Drystreak before catch").withStyle(ChatFormatting.GRAY),
                TextHelper.concat(typeComponent, Component.literal(" ") , TextHelper.literal(itemDrystreak.value2()))
        ));

        int rows = !Configs.hudConfig.showOtherItemDrystreakNotification.get() ? 1 : notificationComponentList.size();

        this.addNotification(
                new Notification(item.getItemStack(),
                        rows, 1,
                        Configs.hudConfig.otherDismissalTime.get(),
                        notificationComponentList
                )
        );
    }

    public void notifyQuest(QuestDataHandler.Quest quest) {
        if(!Configs.hudConfig.showQuestCompletionNotification.get()) {
            return;
        }

        Component completionComponent = Component.literal("Completed quest").withStyle(ChatFormatting.GREEN);
        Component goalComponent = TextHelper.concat(
                ConstantDataHandler.instance().getConstantFishComponent(quest.goal),
                Component.literal(" "),
                TextHelper.literal(quest.current).withStyle(ChatFormatting.YELLOW),
                Component.literal("/").withStyle(ChatFormatting.GRAY),
                TextHelper.literal(quest.max).withStyle(ChatFormatting.WHITE)
        );

        this.addNotification(
                new Notification(
                        2, 1,
                        Configs.hudConfig.questDismissalTime.get(),
                        new ArrayList<>(Arrays.asList(completionComponent, goalComponent))
                )
        );
    }

    public void notifyImportStats() {
        UUID importStatsUUID = this.addNotification(
                new Notification(11, 1,
                        new ArrayList<>(Arrays.asList(
                                Component.literal("You have yet to import your stats").withStyle(ChatFormatting.GOLD),
                                Component.empty(),
                                TextHelper.concat(
                                        Component.literal("Do "),
                                        Component.literal("/foe stats import ").withStyle(ChatFormatting.GREEN),
                                        Component.literal("to import")
                                ),
                                Component.literal("your stats"),
                                Component.literal("This will open the stats screen").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                                Component.literal("and import your stats").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                                Component.empty(),
                                TextHelper.concat(
                                        Component.literal("Do "),
                                        Component.literal("/foe stats cancel ").withStyle(ChatFormatting.GREEN),
                                        Component.literal("to cancel")
                                ),
                                Component.literal("this notification"),
                                TextHelper.concat(
                                        Component.literal("You can still do ").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                                        Component.literal("/foe stats ").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC)
                                ),
                                TextHelper.concat(
                                        Component.literal("import ").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC),
                                        Component.literal("to import at a later time").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
                                )
                        ))
                )
        );

        this.persistentNotifications.put(IMPORT_STATS_KEY, importStatsUUID);
    }

    public void notifyImportCrew() {
        UUID importCrewUUID = this.addNotification(
                new Notification(12, 1,
                        new ArrayList<>(Arrays.asList(
                                Component.literal("You have yet to import ").withStyle(ChatFormatting.GOLD),
                                Component.literal("your crew info").withStyle(ChatFormatting.GOLD),
                                Component.empty(),
                                TextHelper.concat(
                                        Component.literal("Do "),
                                        Component.literal("/foe crew import ").withStyle(ChatFormatting.GREEN),
                                        Component.literal("to import")
                                ),
                                Component.literal("crew info"),
                                Component.literal("This will open the crew screen").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                                Component.literal("and import your crew info").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                                Component.empty(),
                                TextHelper.concat(
                                        Component.literal("Do "),
                                        Component.literal("/foe crew cancel ").withStyle(ChatFormatting.GREEN),
                                        Component.literal("to cancel")
                                ),
                                Component.literal("this notification"),
                                TextHelper.concat(
                                        Component.literal("You can still do ").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                                        Component.literal("/foe crew ").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC)
                                ),
                                TextHelper.concat(
                                        Component.literal("import ").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC),
                                        Component.literal("to import at a later time").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
                                )
                        ))
                )
        );

        this.persistentNotifications.put(IMPORT_CREW_KEY, importCrewUUID);
    }

    public void notifyImportStatsCompleted() {
        this.addNotification(
                new Notification(1, 1,
                        10,
                        Collections.singletonList(
                                Component.literal("✔ Stats imported successfully").withStyle(ChatFormatting.GREEN)
                        )
                )
        );
    }

    public void notifyImportCrewCompleted() {
        this.addNotification(
                new Notification(1, 1,
                        10,
                        Collections.singletonList(
                                Component.literal("✔ Crew imported successfully").withStyle(ChatFormatting.GREEN)
                        )
                )
        );
    }

    public void notifyEmptySlots(int currentEmptySlots) {
        this.removeNotification(EMPTY_SLOTS_KEY);

        if(!Configs.hudConfig.showEmptySlotsNotification.get()
                || Configs.hudConfig.showInventoryWarning.get()) { // FOE's warning replaces this one
            return;
        }

        if(currentEmptySlots <= Configs.hudConfig.showNotificationAtEmptySlots.get()) {
            Component notificationComponent = currentEmptySlots == 0
                    ? Component.literal("You have a full inventory!").withStyle(ChatFormatting.DARK_RED)
                    : Component.literal("You nearly have a full inventory").withStyle(ChatFormatting.RED);

            UUID emptySlotsUUID = this.addNotification(new Notification(
                2, 1,
                    new ArrayList<>(Arrays.asList(
                            notificationComponent,
                            TextHelper.concat(
                                    Component.literal("Slots left: ").withStyle(ChatFormatting.GRAY),
                                    TextHelper.literal(currentEmptySlots)
                            )
                    ))
            ));

            this.persistentNotifications.put(EMPTY_SLOTS_KEY, emptySlotsUUID);
        }
    }

    public void notifyPlayerStatus(boolean isJoin, Pair<String, ItemStack> player) {
        if(!Configs.hudConfig.showCrewStatusNotification.get()) {
            return;
        }

        Component icon = isJoin
                ? Component.literal("+ ").withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_GREEN)
                : Component.literal("- ").withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_RED);

        Component status = isJoin
                ? Component.literal(" has joined").withStyle(ChatFormatting.GREEN)
                : Component.literal(" has left").withStyle(ChatFormatting.RED);

        this.addNotification(
                new Notification(
                        player.value2(), 1, 1, Configs.hudConfig.crewDismissalTime.get(),
                        List.of(TextHelper.concat(
                                icon,
                                Component.literal(player.value1()).withStyle(ChatFormatting.YELLOW),
                                status
                        ))
                )
        );
    }

    public void notifyOnTrigger(String[] notificationIds) {
        for (String notificationId : notificationIds) {
            CustomNotificationDataHandler.CustomNotification notification = CustomNotificationDataHandler.instance().getCustomNotificationData().notificationList.getOrDefault(notificationId.trim(), null);

            if(notification != null && minecraft.player != null) {
                ItemStack itemStack = ItemStack.EMPTY;

                if(!notification.getIcon().isBlank()) {
                    itemStack = ItemStackHelper.valueOf(notification.getIcon());
                }

                List<MutableComponent> lines = notification.getStringLines().stream()
                        .map(line -> PlaceholderHandler.instance().resolve(line, false))
                        .filter(result -> (result.success()[0] || !result.errors().isEmpty()) && !result.success()[1])
                        .map(PlaceholderResult::text).toList();
                List<Component> newLines = new ArrayList<>();

                lines.forEach(line -> newLines.addAll(TextHelper.wrapStyledComponent(line, notification.getIcon().isBlank() ? CONTENT_WIDTH : ICON_CONTENT_WIDTH, true, minecraft.font)));

                if(itemStack == ItemStack.EMPTY) {
                    this.addNotification(
                            new Notification(
                                    newLines.size(), 1, 10,
                                    newLines
                            )
                    );
                } else {
                    this.addNotification(
                            new Notification(
                                    itemStack,
                                    newLines.size(), 1, 10,
                                    newLines
                            )
                    );
                }
            }
        }
    }
    //endregion

    //region Notification Object
    public static class Notification {
        public final ItemStack item;
        public final int rows;
        public final int columns;
        public final List<Component> componentList;
        protected final long startTime;
        protected final int notificationTime;
        protected final UUID uuid;

        public Notification(
                ItemStack item,
                int rows,
                int columns,
                int notificationTime,
                List<Component> componentList
        ) {
            this.item = item;
            this.rows = rows;
            this.columns = columns;
            this.startTime = System.currentTimeMillis();
            this.notificationTime = notificationTime;
            this.componentList = componentList;
            this.uuid = UUID.randomUUID();
        }

        public Notification(
                int rows,
                int columns,
                int notificationTime,
                List<Component> componentList
        ) {
            this.item = ItemStack.EMPTY;
            this.rows = rows;
            this.columns = columns;
            this.startTime = System.currentTimeMillis();
            this.notificationTime = notificationTime;
            this.componentList = componentList;
            this.uuid = UUID.randomUUID();
        }

        public Notification(
                ItemStack item,
                int rows,
                int columns,
                List<Component> componentList
        ) {
            this.item = item;
            this.rows = rows;
            this.columns = columns;
            this.startTime = System.currentTimeMillis();
            this.notificationTime = Integer.MAX_VALUE;
            this.componentList = componentList;
            this.uuid = UUID.randomUUID();
        }

        public Notification(
                int rows,
                int columns,
                List<Component> componentList
        ) {
            this.item = ItemStack.EMPTY;
            this.rows = rows;
            this.columns = columns;
            this.startTime = System.currentTimeMillis();
            this.notificationTime = Integer.MAX_VALUE;
            this.componentList = componentList;
            this.uuid = UUID.randomUUID();
        }
    }
    //endregion

    //region Dev
    /// Field, Pair<Value, Tooltip>
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
                "notifications", Pair.of(Component.literal("[notifications]]"), TextHelper.literal(getNotifications()))
        );
    }
    //endregion
}
