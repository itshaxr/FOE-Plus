package dannypx.foe.config;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.type.Alignment;
import dannypx.foe.type.PanelTheme;
import dannypx.foe.type.CatchPopupStyle;
import dannypx.foe.type.WarningSound;
import me.fzzyhmstrs.fzzy_config.annotations.Version;
import me.fzzyhmstrs.fzzy_config.api.FileType;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.config.ConfigGroup;
import me.fzzyhmstrs.fzzy_config.util.Translatable;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedChoice;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedFloat;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedNumber;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

@Version(version = 3)
@Translatable.Name("HUD Configuration")
@Translatable.Desc("§7Configure HUD elements")
public class HUDConfig extends Config {
    public HUDConfig() {
        super(Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "hud_config"));
    }

    @Name("Profile Element")
    @Desc("§7This is your profile shown on the hud. (Name, Level, Picture)")
    public ConfigGroup profileElementGroup = new ConfigGroup("profile_element_group");

    @Name("Show Profile Element")
    public ValidatedBoolean showProfileElement = new ValidatedBoolean(true);

    @Name("X Position in %")
    public ValidatedInt profileElementXPosition = new ValidatedInt(1, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt profileElementYPosition = new ValidatedInt(2, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Anchor point")
    public ValidatedChoice<Alignment> profileElementAlignment = new ValidatedChoice<>(Alignment.TOP_LEFT, Alignment.getTopCorners(), new ValidatedEnum<>(Alignment.class).instanceEntry(), ValidatedChoice.WidgetType.CYCLING);

    @ConfigGroup.Pop
    @Name("Scale")
    @Desc("§7This will scale based on the ratio. So 0.5 would be half the size")
    public ValidatedFloat profileElementScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("Location Element")
    @Desc("§7This the location element. (Time, Temperature, Weather, Location)")
    public ConfigGroup locationElementGroup = new ConfigGroup("location_element_group");

    @Name("Show Profile Element")
    public ValidatedBoolean showLocationElement = new ValidatedBoolean(true);

    @Name("X Position in %")
    public ValidatedInt locationElementXPosition = new ValidatedInt(1, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt locationElementYPosition = new ValidatedInt(2, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Anchor point")
    public ValidatedChoice<Alignment> locationElementAlignment = new ValidatedChoice<>(Alignment.TOP_RIGHT, Alignment.getTopCorners(), new ValidatedEnum<>(Alignment.class).instanceEntry(), ValidatedChoice.WidgetType.CYCLING);

    @ConfigGroup.Pop
    @Name("Scale")
    @Desc("§7This will scale based on the ratio. So 0.5 would be half the size")
    public ValidatedFloat locationElementScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("Hotbar Element")
    @Desc("§7This the hotbar element")
    public ConfigGroup hotbarElementGroup = new ConfigGroup("hotbar_element_group");

    @Name("Show Hotbar Element")
    public ValidatedBoolean showHotbarElement = new ValidatedBoolean(true);

    @Name("X Position in %")
    public ValidatedInt hotbarElementXPosition = new ValidatedInt(50, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt hotbarElementYPosition = new ValidatedInt(2, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Anchor point")
    public ValidatedChoice<Alignment> hotbarElementAlignment = new ValidatedChoice<>(Alignment.BOTTOM, Alignment.getBottom(), new ValidatedEnum<>(Alignment.class).instanceEntry(), ValidatedChoice.WidgetType.CYCLING);

    @Name("Scale")
    @Desc("§7This will scale based on the ratio. So 0.5 would be half the size")
    public ValidatedFloat hotbarElementScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("Hotbar Options")
    @Desc("§7Options for the hotbar")
    public ConfigGroup hotbarOptions = new ConfigGroup("hotbar_options_group");

    @Name("Show Armor")
    public ValidatedBoolean showHotbarArmor = new ValidatedBoolean(true);

    @Name("Show Fishing Parts")
    public ValidatedBoolean showHotbarParts = new ValidatedBoolean(true);

    @Name("Show Active Bait")
    public ValidatedBoolean showHotbarBait = new ValidatedBoolean(true);

    @ConfigGroup.Pop
    @ConfigGroup.Pop
    @Name("Show Tacklebox Lock")
    public ValidatedBoolean showBaitLock = new ValidatedBoolean(true);

    @Name("Pet Element")
    @Desc("§7This is your pet shown on the hud. (Name, Level, Picture)")
    public ConfigGroup petElementGroup = new ConfigGroup("pet_element_group");

    @Name("Show Pet Element")
    public ValidatedBoolean showPetElement = new ValidatedBoolean(true);

    @Name("X Position in %")
    public ValidatedInt petElementXPosition = new ValidatedInt(1, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt petElementYPosition = new ValidatedInt(15, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Anchor point")
    public ValidatedChoice<Alignment> petElementAlignment = new ValidatedChoice<>(Alignment.TOP_LEFT, Alignment.getTopCorners(), new ValidatedEnum<>(Alignment.class).instanceEntry(), ValidatedChoice.WidgetType.CYCLING);

    @ConfigGroup.Pop
    @Name("Scale")
    @Desc("§7This will scale based on the ratio. So 0.5 would be half the size")
    public ValidatedFloat petElementScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("Notifier Element")
    @Desc("§7Options for the notifier")
    public ConfigGroup notifierElementGroup = new ConfigGroup("notifier_element_group");

    @Name("Show Notifier Element")
    public ValidatedBoolean showNotifierElement = new ValidatedBoolean(true);

    @Name("X Position in %")
    public ValidatedInt notifierElementXPosition = new ValidatedInt(1, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt notifierElementYPosition = new ValidatedInt(2, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Anchor point")
    public ValidatedChoice<Alignment> notifierElementAlignment = new ValidatedChoice<>(Alignment.BOTTOM_RIGHT, Alignment.getCorners(), new ValidatedEnum<>(Alignment.class).instanceEntry(), ValidatedChoice.WidgetType.CYCLING);

    @Name("Scale")
    @Desc("§7This will scale based on the ratio. So 0.5 would be half the size")
    public ValidatedFloat notifierElementScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("Notifications Options")
    @Desc("§7Options for the notifications")
    public ConfigGroup notificationOptions = new ConfigGroup("notifications_options_group");

    @Name("Catch Notification")
    @Desc("§7Options for the notifications when catching")
    public ConfigGroup catchingOptions = new ConfigGroup("catching_options_group");

    @Name("Show notification when catching fish")
    public ValidatedBoolean showFishCatchNotification = new ValidatedBoolean(true);

    @Name("Fish catch style")
    @Desc("§7FOE: floating text in the middle of the screen (move it in Move HUD Elements)\nBOX: the notification box")
    public ValidatedEnum<CatchPopupStyle> fishCatchStyle = new ValidatedEnum<>(CatchPopupStyle.FOE, ValidatedEnum.WidgetType.CYCLING);

    @Name("Show drystreak in notification when catching fish")
    public ValidatedBoolean showFishDrystreakNotification = new ValidatedBoolean(true);

    @Name("Fish dismissal time")
    @Desc("§7How long in seconds, before the notification dismisses")
    public ValidatedInt fishDismissalTime = new ValidatedInt(15, 60, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Show notification when catching pets")
    public ValidatedBoolean showPetCatchNotification = new ValidatedBoolean(true);

    @Name("Chat message when you catch a variant")
    @Desc("§7Albino, Melanistic, Trophy and Fabled catches with the fish and its dry streak, in chat")
    public ValidatedBoolean showVariantCatchChat = new ValidatedBoolean(true);

    @Name("Chat message when you catch a Gigantic")
    @Desc("§7The fish and your Gigantic dry streak, in chat")
    public ValidatedBoolean showGiganticCatchChat = new ValidatedBoolean(true);

    @Name("Chat message when you pull an Infusion Capsule")
    @Desc("§7The capsule and your Infusion Capsule dry streak, in chat")
    public ValidatedBoolean showInfusionCapsuleChat = new ValidatedBoolean(true);

    @Name("Chat message when you pull a Lightning in a Bottle")
    @Desc("§7The bottle and your Lightning Bottle dry streak, in chat")
    public ValidatedBoolean showLightningBottleChat = new ValidatedBoolean(true);

    @Name("Chat message when you pull a pet")
    @Desc("§7Rarity, rating % and dry streak of every pet you catch, in chat")
    public ValidatedBoolean showPetCatchChat = new ValidatedBoolean(true);

    @Name("Show drystreak in notification when catching pets")
    public ValidatedBoolean showPetsDrystreakNotification = new ValidatedBoolean(true);

    @Name("Pet dismissal time")
    @Desc("§7How long in seconds, before the notification dismisses")
    public ValidatedInt petDismissalTime = new ValidatedInt(15, 60, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Show notification when catching other items")
    public ValidatedBoolean showOtherItemCatchNotification = new ValidatedBoolean(true);

    @Name("Show drystreak in notification when catching other items")
    public ValidatedBoolean showOtherItemDrystreakNotification = new ValidatedBoolean(true);

    @ConfigGroup.Pop
    @Name("Other items dismissal time")
    @Desc("§7How long in seconds, before the notification dismisses")
    public ValidatedInt otherDismissalTime = new ValidatedInt(15, 60, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Crew Notification")
    @Desc("§7Options for the notifications of crews")
    public ConfigGroup crewOptions = new ConfigGroup("crew_options_group");

    @Name("Show notification when crew join/leave the server (status)")
    public ValidatedBoolean showCrewStatusNotification = new ValidatedBoolean(true);

    @Name("Chat line when a crew member comes online / goes offline")
    @Desc("§7[FOE+] CREW Ewiksen is online")
    public ValidatedBoolean crewStatusChat = new ValidatedBoolean(true);

    @Name("Chat line when a friend comes online / goes offline")
    @Desc("§7Friends you added with /foe friend add <name>")
    public ValidatedBoolean friendStatusChat = new ValidatedBoolean(true);

    @ConfigGroup.Pop
    @Name("Crew status dismissal time")
    @Desc("§7How long in seconds, before the notification dismisses")
    public ValidatedInt crewDismissalTime = new ValidatedInt(10, 60, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Quest Notification")
    @Desc("§7Options for the notifications when questing")
    public ConfigGroup questOptions = new ConfigGroup("quest_options_group");

    @Name("Show notification when completing quests")
    public ValidatedBoolean showQuestCompletionNotification = new ValidatedBoolean(true);

    @ConfigGroup.Pop
    @Name("Quest completion dismissal time")
    @Desc("§7How long in seconds, before the notification dismisses")
    public ValidatedInt questDismissalTime = new ValidatedInt(15, 60, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Empty Slots Notification")
    @Desc("§7Options for empty slots notifications")
    public ConfigGroup emptySlotsOptions = new ConfigGroup("empty_slots_options_group");

    @Name("Show notification when nearing full inventory")
    public ValidatedBoolean showEmptySlotsNotification = new ValidatedBoolean(true);

    @ConfigGroup.Pop
    @ConfigGroup.Pop
    @ConfigGroup.Pop
    @Name("Show notification when empty slots left")
    @Desc("§7How many empty slots before it shows the notification")
    public ValidatedInt showNotificationAtEmptySlots = new ValidatedInt(3, 36, 0, ValidatedNumber.WidgetType.SLIDER);

    // FOE-style info panels (ported from FOECollab)
    @Name("Info panel border style")
    @Desc("§7Border style of the Contest, Quests, Daily Quests and All-Time Stats panels")
    public ValidatedEnum<PanelTheme> panelTheme = new ValidatedEnum<>(PanelTheme.CLEAN_ROUNDED, ValidatedEnum.WidgetType.CYCLING);

    @Name("Contest Panel")
    @Desc("§7Contest timer, type, location and top 3")
    public ConfigGroup contestPanelGroup = new ConfigGroup("contestPanel_group");

    @Name("Show Contest Panel")
    public ValidatedBoolean showContestPanel = new ValidatedBoolean(true);

    @Name("X Position in %")
    public ValidatedInt contestPanelXPosition = new ValidatedInt(1, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt contestPanelYPosition = new ValidatedInt(2, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Anchor point")
    public ValidatedChoice<Alignment> contestPanelAlignment = new ValidatedChoice<>(Alignment.TOP_LEFT, Alignment.getCorners(), new ValidatedEnum<>(Alignment.class).instanceEntry(), ValidatedChoice.WidgetType.CYCLING);

    @Name("Background opacity in %")
    public ValidatedInt contestPanelOpacity = new ValidatedInt(40, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Refresh every minute")
    @Desc("§7Quietly run /contest once a minute during a contest (the chat output is hidden)")
    public ValidatedBoolean contestAutoRefresh = new ValidatedBoolean(true);

    @Name("Refresh on a new personal best")
    @Desc("§7Run /contest when you catch a bigger qualifying fish at the contest location")
    public ValidatedBoolean contestRefreshOnBest = new ValidatedBoolean(true);

    @Name("Show best bait")
    @Desc("§7Look up the best bait for the current contest in the bait sheet")
    public ValidatedBoolean showContestBestBait = new ValidatedBoolean(true);

    @Name("Bait sheet link")
    @Desc("§7Google Sheets link (shared 'anyone with the link'). Columns: Location, Family, Fish, Rarity, Habitat, Bait Strategy")
    public ValidatedString contestBaitSheetUrl = new ValidatedString.Builder("https://docs.google.com/spreadsheets/d/1CJDgpV0FFdlJ4RuuZIHJEoZ02FJiCWRG/edit?usp=sharing").withCorrector().build();

    @ConfigGroup.Pop
    @Name("Scale")
    @Desc("§7This will scale based on the ratio. So 0.5 would be half the size")
    public ValidatedFloat contestPanelScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("Daily Quests Panel")
    @Desc("§7Your unfinished daily missions")
    public ConfigGroup dailyQuestPanelGroup = new ConfigGroup("dailyQuestPanel_group");

    @Name("Show Daily Quests Panel")
    public ValidatedBoolean showDailyQuestPanel = new ValidatedBoolean(true);

    @Name("Update after every catch")
    @Desc("§7Quietly reads Daily Missions from /menu after each catch (the menu never shows), so progress is exact")
    public ValidatedBoolean dailyQuestAutoRefresh = new ValidatedBoolean(true);

    @Name("X Position in %")
    public ValidatedInt dailyQuestPanelXPosition = new ValidatedInt(1, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt dailyQuestPanelYPosition = new ValidatedInt(18, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Anchor point")
    public ValidatedChoice<Alignment> dailyQuestPanelAlignment = new ValidatedChoice<>(Alignment.TOP_LEFT, Alignment.getCorners(), new ValidatedEnum<>(Alignment.class).instanceEntry(), ValidatedChoice.WidgetType.CYCLING);

    @Name("Background opacity in %")
    public ValidatedInt dailyQuestPanelOpacity = new ValidatedInt(40, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @ConfigGroup.Pop
    @Name("Scale")
    @Desc("§7This will scale based on the ratio. So 0.5 would be half the size")
    public ValidatedFloat dailyQuestPanelScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("All-Time Stats Panel")
    @Desc("§7Fish caught, fish/hour, rarities and variants")
    public ConfigGroup statsPanelGroup = new ConfigGroup("statsPanel_group");

    @Name("Show All-Time Stats Panel")
    public ValidatedBoolean showStatsPanel = new ValidatedBoolean(true);

    @Name("X Position in %")
    public ValidatedInt statsPanelXPosition = new ValidatedInt(1, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt statsPanelYPosition = new ValidatedInt(18, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Anchor point")
    public ValidatedChoice<Alignment> statsPanelAlignment = new ValidatedChoice<>(Alignment.TOP_RIGHT, Alignment.getCorners(), new ValidatedEnum<>(Alignment.class).instanceEntry(), ValidatedChoice.WidgetType.CYCLING);

    @Name("Background opacity in %")
    public ValidatedInt statsPanelOpacity = new ValidatedInt(40, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Show gigantic fish")
    public ValidatedBoolean statsPanelShowGigantic = new ValidatedBoolean(true);

    @Name("Show Infusion Capsules")
    @Desc("§7How many you've pulled, and the dry streak (Blood Moon catches since the last one)")
    public ValidatedBoolean statsPanelShowCapsules = new ValidatedBoolean(true);

    @Name("Show Lightning Bottles")
    @Desc("§7How many you've pulled, and the dry streak (catches in a thunderstorm or supercell since the last one)")
    public ValidatedBoolean statsPanelShowBottles = new ValidatedBoolean(true);

    @ConfigGroup.Pop
    @Name("Scale")
    @Desc("§7This will scale based on the ratio. So 0.5 would be half the size")
    public ValidatedFloat statsPanelScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("Session Stats Panel")
    @Desc("§7Everything All-Time Stats shows, for this session only (cleared when you log out). Gigantic, capsules and bottles follow the All-Time Stats settings")
    public ConfigGroup sessionStatsPanelGroup = new ConfigGroup("sessionStatsPanel_group");

    @Name("Show Session Stats Panel")
    public ValidatedBoolean showSessionStatsPanel = new ValidatedBoolean(true);

    @Name("X Position in %")
    public ValidatedInt sessionStatsPanelXPosition = new ValidatedInt(26, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt sessionStatsPanelYPosition = new ValidatedInt(18, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Anchor point")
    public ValidatedChoice<Alignment> sessionStatsPanelAlignment = new ValidatedChoice<>(Alignment.TOP_RIGHT, Alignment.getCorners(), new ValidatedEnum<>(Alignment.class).instanceEntry(), ValidatedChoice.WidgetType.CYCLING);

    @Name("Background opacity in %")
    public ValidatedInt sessionStatsPanelOpacity = new ValidatedInt(40, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @ConfigGroup.Pop
    @Name("Scale")
    public ValidatedFloat sessionStatsPanelScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("Quests Panel")
    @Desc("§7Quests for your current location")
    public ConfigGroup questPanelGroup = new ConfigGroup("questPanel_group");

    @Name("Show Quests Panel")
    public ValidatedBoolean showQuestPanel = new ValidatedBoolean(true);

    @Name("X Position in %")
    public ValidatedInt questPanelXPosition = new ValidatedInt(1, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt questPanelYPosition = new ValidatedInt(2, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Anchor point")
    public ValidatedChoice<Alignment> questPanelAlignment = new ValidatedChoice<>(Alignment.BOTTOM_RIGHT, Alignment.getCorners(), new ValidatedEnum<>(Alignment.class).instanceEntry(), ValidatedChoice.WidgetType.CYCLING);

    @Name("Background opacity in %")
    public ValidatedInt questPanelOpacity = new ValidatedInt(40, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @ConfigGroup.Pop
    @Name("Scale")
    @Desc("§7This will scale based on the ratio. So 0.5 would be half the size")
    public ValidatedFloat questPanelScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("Pet Panel")
    @Desc("§7FOE's pet panel: icon, name, level, XP and rating (replaces the FOE-R pet element while on)")
    public ConfigGroup petPanelGroup = new ConfigGroup("pet_panel_group");

    @Name("Show Pet Panel")
    public ValidatedBoolean showPetPanel = new ValidatedBoolean(true);

    @Name("X Position in %")
    public ValidatedInt petPanelXPosition = new ValidatedInt(1, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt petPanelYPosition = new ValidatedInt(2, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Anchor point")
    public ValidatedChoice<Alignment> petPanelAlignment = new ValidatedChoice<>(Alignment.TOP_RIGHT, Alignment.getCorners(), new ValidatedEnum<>(Alignment.class).instanceEntry(), ValidatedChoice.WidgetType.CYCLING);

    @Name("Background opacity in %")
    public ValidatedInt petPanelOpacity = new ValidatedInt(40, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @ConfigGroup.Pop
    @Name("Scale")
    public ValidatedFloat petPanelScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("Bait Panel")
    @Desc("§7The bait or lure on your rod and how many are left")
    public ConfigGroup baitPanelGroup = new ConfigGroup("bait_panel_group");

    @Name("Show Bait Panel")
    public ValidatedBoolean showBaitPanel = new ValidatedBoolean(true);

    @Name("X Position in %")
    public ValidatedInt baitPanelXPosition = new ValidatedInt(1, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt baitPanelYPosition = new ValidatedInt(2, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Anchor point")
    public ValidatedChoice<Alignment> baitPanelAlignment = new ValidatedChoice<>(Alignment.BOTTOM_LEFT, Alignment.getCorners(), new ValidatedEnum<>(Alignment.class).instanceEntry(), ValidatedChoice.WidgetType.CYCLING);

    @Name("Background opacity in %")
    public ValidatedInt baitPanelOpacity = new ValidatedInt(40, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @ConfigGroup.Pop
    @Name("Scale")
    public ValidatedFloat baitPanelScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("Alerts Panel")
    @Desc("§7Witching Hour and cryptid windows (Titanoboa, Rougarou, Garaboncias, Bessie) with how long they have left")
    public ConfigGroup alertPanelGroup = new ConfigGroup("alert_panel_group");

    @Name("Show Alerts Panel")
    public ValidatedBoolean showAlertPanel = new ValidatedBoolean(true);

    @Name("Witching Hour (Cypress Lake, 12-3 AM)")
    public ValidatedBoolean alertWitchingHour = new ValidatedBoolean(true);

    @Name("Titanoboa (Amazon, 2-4 AM)")
    public ValidatedBoolean alertTitanoboa = new ValidatedBoolean(true);

    @Name("Rougarou (Toledo Bend, Moons)")
    public ValidatedBoolean alertRougarou = new ValidatedBoolean(true);

    @Name("Garaboncias (Danube, Rainbows)")
    public ValidatedBoolean alertGaraboncias = new ValidatedBoolean(true);

    @Name("Bessie (Great Lakes, 4-6 AM and Moons)")
    public ValidatedBoolean alertBessie = new ValidatedBoolean(true);

    @Name("Fabled events (any location)")
    public ValidatedBoolean alertFabled = new ValidatedBoolean(true);

    @Name("Only alerts for the location you're at")
    public ValidatedBoolean alertsOnlyHere = new ValidatedBoolean(false);

    @Name("Show what starts soon (minutes ahead, 0 = off)")
    public ValidatedInt alertUpcomingMinutes = new ValidatedInt(3, 15, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Chat message when an alert starts")
    public ValidatedBoolean alertChat = new ValidatedBoolean(true);

    @Name("Sound when an alert starts")
    public ValidatedBoolean alertSound = new ValidatedBoolean(true);

    @Name("Alert sound")
    public ValidatedEnum<WarningSound> alertSoundType = new ValidatedEnum<>(WarningSound.DIDGERIDOO, ValidatedEnum.WidgetType.CYCLING);

    @Name("X Position in %")
    public ValidatedInt alertPanelXPosition = new ValidatedInt(50, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt alertPanelYPosition = new ValidatedInt(12, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Anchor point")
    public ValidatedChoice<Alignment> alertPanelAlignment = new ValidatedChoice<>(Alignment.TOP, Alignment.getCornersAndCenters(), new ValidatedEnum<>(Alignment.class).instanceEntry(), ValidatedChoice.WidgetType.CYCLING);

    @Name("Background opacity in %")
    public ValidatedInt alertPanelOpacity = new ValidatedInt(40, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @ConfigGroup.Pop
    @Name("Scale")
    public ValidatedFloat alertPanelScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("Events Panel")
    @Desc("§7A Fish Frenzy (where and how long is left of its hour), a Blooming Oasis, a Gold Rush, an active Fabled event (until it's caught), and global Money / XP boosters with their time left")
    public ConfigGroup eventsPanelGroup = new ConfigGroup("events_panel_group");

    @Name("Show Events Panel")
    public ValidatedBoolean showEventsPanel = new ValidatedBoolean(true);

    @Name("Show event descriptions")
    @Desc("§7What an event does, next to its name: \"Shards 1 in 25\" for a Gold Rush, \"+100% Pet Luck\" for a Blooming Oasis")
    public ValidatedBoolean eventsShowDescriptions = new ValidatedBoolean(true);

    @Name("Check /events quietly now and then")
    @Desc("§7/events and /active, after you join and every 15 minutes, never while a menu is open; their answers are hidden. Syncs the frenzy, the Tackle Shop / Cosmetic Store timers and the booster timers")
    public ValidatedBoolean eventsAutoRefresh = new ValidatedBoolean(true);

    @Name("X Position in %")
    public ValidatedInt eventsPanelXPosition = new ValidatedInt(22, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt eventsPanelYPosition = new ValidatedInt(2, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Anchor point")
    public ValidatedChoice<Alignment> eventsPanelAlignment = new ValidatedChoice<>(Alignment.TOP_LEFT, Alignment.getCornersAndCenters(), new ValidatedEnum<>(Alignment.class).instanceEntry(), ValidatedChoice.WidgetType.CYCLING);

    @Name("Background opacity in %")
    public ValidatedInt eventsPanelOpacity = new ValidatedInt(40, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @ConfigGroup.Pop
    @Name("Scale")
    public ValidatedFloat eventsPanelScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("Mini Event Panel")
    @Desc("§7The mini event from /minievent: when it ends, when daily missions reroll, and every mission's progress")
    public ConfigGroup miniEventPanelGroup = new ConfigGroup("mini_event_panel_group");

    @Name("Show Mini Event Panel")
    public ValidatedBoolean showMiniEventPanel = new ValidatedBoolean(true);

    @Name("Only show at the event's location")
    @Desc("§7Show the panel only while you're at the mini event's location (Kenai River for the Kenai mini event)")
    public ValidatedBoolean miniEventOnlyAtLocation = new ValidatedBoolean(false);

    @Name("Check /minievent quietly now and then")
    @Desc("§7Every 10 minutes and a few seconds after a catch (at most once a minute), never while a menu is open; its answer is hidden")
    public ValidatedBoolean miniEventAutoRefresh = new ValidatedBoolean(true);

    @Name("X Position in %")
    public ValidatedInt miniEventPanelXPosition = new ValidatedInt(99, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt miniEventPanelYPosition = new ValidatedInt(30, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Anchor point")
    public ValidatedChoice<Alignment> miniEventPanelAlignment = new ValidatedChoice<>(Alignment.TOP_RIGHT, Alignment.getCornersAndCenters(), new ValidatedEnum<>(Alignment.class).instanceEntry(), ValidatedChoice.WidgetType.CYCLING);

    @Name("Background opacity in %")
    public ValidatedInt miniEventPanelOpacity = new ValidatedInt(40, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @ConfigGroup.Pop
    @Name("Scale")
    public ValidatedFloat miniEventPanelScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("Timers & Crew Panel")
    @Desc("§7The Timers & Crew box from your inventory as a HUD panel too (what it shows is set in the inventory settings)")
    public ConfigGroup timersPanelGroup = new ConfigGroup("timers_panel_group");

    @Name("Show Timers & Crew Panel")
    public ValidatedBoolean showTimersPanel = new ValidatedBoolean(true);

    @Name("X Position in %")
    public ValidatedInt timersPanelXPosition = new ValidatedInt(1, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt timersPanelYPosition = new ValidatedInt(42, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Anchor point")
    public ValidatedChoice<Alignment> timersPanelAlignment = new ValidatedChoice<>(Alignment.TOP_LEFT, Alignment.getCorners(), new ValidatedEnum<>(Alignment.class).instanceEntry(), ValidatedChoice.WidgetType.CYCLING);

    @Name("Background opacity in %")
    public ValidatedInt timersPanelOpacity = new ValidatedInt(40, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @ConfigGroup.Pop
    @Name("Scale")
    public ValidatedFloat timersPanelScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("Luck & Scale Panel")
    @Desc("§7Your current Luck and Scale as shown on your profile in /menu (boosters included)")
    public ConfigGroup luckScalePanelGroup = new ConfigGroup("luck_scale_panel_group");

    @Name("Show Luck & Scale Panel")
    public ValidatedBoolean showLuckScalePanel = new ValidatedBoolean(true);

    @Name("Update automatically on every location change")
    @Desc("§7Quietly reads your profile from /menu a few seconds after you arrive somewhere (the menu never shows)")
    public ValidatedBoolean luckScaleAutoRefresh = new ValidatedBoolean(true);

    @Name("Show when the numbers were read from /menu")
    public ValidatedBoolean luckScalePanelBreakdown = new ValidatedBoolean(true);

    @Name("X Position in %")
    public ValidatedInt luckScalePanelXPosition = new ValidatedInt(1, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt luckScalePanelYPosition = new ValidatedInt(20, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Anchor point")
    public ValidatedChoice<Alignment> luckScalePanelAlignment = new ValidatedChoice<>(Alignment.BOTTOM_LEFT, Alignment.getCorners(), new ValidatedEnum<>(Alignment.class).instanceEntry(), ValidatedChoice.WidgetType.CYCLING);

    @Name("Background opacity in %")
    public ValidatedInt luckScalePanelOpacity = new ValidatedInt(40, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @ConfigGroup.Pop
    @Name("Scale")
    public ValidatedFloat luckScalePanelScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("Equipment Bar")
    @Desc("§7FOE's armor and rod-part boxes above the hotbar, underlined in their rarity colour")
    public ConfigGroup equipmentBarGroup = new ConfigGroup("equipment_bar_group");

    @Name("Show Equipment Bar")
    public ValidatedBoolean showEquipmentBar = new ValidatedBoolean(true);

    @Name("Distance from bottom")
    public ValidatedInt equipmentBarOffsetFromBottom = new ValidatedInt(28, 300, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Background opacity in %")
    public ValidatedInt equipmentBarOpacity = new ValidatedInt(40, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @ConfigGroup.Pop
    @Name("Scale")
    public ValidatedFloat equipmentBarScale = ValidatedNumber.withIncrement(new ValidatedFloat(0.8f, 2.0f, 0.1f), 0.05f);

    @Name("Wrong Gear Warning")
    @Desc("§7Warns while your pet or armor doesn't fit the location you're fishing in")
    public ConfigGroup gearWarningGroup = new ConfigGroup("gear_warning_group");

    @Name("Show Wrong Gear Warning")
    public ValidatedBoolean showGearWarning = new ValidatedBoolean(true);

    @Name("Show inventory almost full warning")
    public ValidatedBoolean showInventoryWarning = new ValidatedBoolean(true);

    @Name("Warn at this many free slots or fewer")
    public ValidatedInt inventoryWarningThreshold = new ValidatedInt(3, 35, 1, ValidatedNumber.WidgetType.SLIDER);

    @Name("Play a warning sound")
    public ValidatedBoolean inventoryWarningSound = new ValidatedBoolean(true);

    @Name("Seconds between sounds")
    public ValidatedInt inventoryWarningSoundSeconds = new ValidatedInt(10, 30, 1, ValidatedNumber.WidgetType.SLIDER);

    @Name("Warning sound")
    public ValidatedEnum<WarningSound> inventoryWarningSoundType = new ValidatedEnum<>(WarningSound.DIDGERIDOO, ValidatedEnum.WidgetType.CYCLING);

    @Name("X Position in %")
    public ValidatedInt gearWarningXPosition = new ValidatedInt(50, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt gearWarningYPosition = new ValidatedInt(35, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Anchor point")
    public ValidatedChoice<Alignment> gearWarningAlignment = new ValidatedChoice<>(Alignment.TOP, Alignment.getCornersAndCenters(), new ValidatedEnum<>(Alignment.class).instanceEntry(), ValidatedChoice.WidgetType.CYCLING);

    @Name("Background opacity in %")
    public ValidatedInt gearWarningOpacity = new ValidatedInt(40, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @ConfigGroup.Pop
    @Name("Scale")
    public ValidatedFloat gearWarningScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("Top Bar")
    @Desc("§7FOE's bar across the top: name, level, location, weather, time, balance, catches")
    public ConfigGroup topBarGroup = new ConfigGroup("top_bar_group");

    @Name("Show Top Bar")
    public ValidatedBoolean showTopBar = new ValidatedBoolean(true);

    @Name("Show climate")
    public ValidatedBoolean topBarShowClimate = new ValidatedBoolean(true);

    @Name("Show community event")
    @Desc("§7The community event total and your contribution")
    public ValidatedBoolean topBarShowCommunity = new ValidatedBoolean(true);

    @Name("Check the community event in the background")
    @Desc("§7Quietly runs /communityevent every 10 minutes (never while a menu is open) to keep your contribution current")
    public ValidatedBoolean communityAutoRefresh = new ValidatedBoolean(true);

    @Name("Background opacity in %")
    public ValidatedInt topBarOpacity = new ValidatedInt(40, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @ConfigGroup.Pop
    @Name("Scale")
    public ValidatedFloat topBarScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.5f), 0.05f);

    @Name("FOE Catch Popup")
    @Desc("§7The floating catch text used when Fish catch style is FOE")
    public ConfigGroup catchPopupGroup = new ConfigGroup("catch_popup_group");

    @Name("X Position in %")
    public ValidatedInt catchPopupXPosition = new ValidatedInt(50, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Y Position in %")
    public ValidatedInt catchPopupYPosition = new ValidatedInt(30, 100, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Show weight")
    public ValidatedBoolean catchPopupShowWeight = new ValidatedBoolean(true);

    @Name("Show length")
    public ValidatedBoolean catchPopupShowLength = new ValidatedBoolean(true);

    @Name("Display time in seconds")
    public ValidatedInt catchPopupTime = new ValidatedInt(5, 30, 1, ValidatedNumber.WidgetType.SLIDER);

    @ConfigGroup.Pop
    @Name("Scale")
    @Desc("§7This will scale based on the ratio. So 0.5 would be half the size")
    public ValidatedFloat catchPopupScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.5f), 0.05f);

    @Override
    public @NotNull FileType fileType() {
        return FileType.JSON;
    }
}
