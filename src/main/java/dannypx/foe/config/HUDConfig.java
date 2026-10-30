package dannypx.foe.config;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.type.Alignment;
import dannypx.foe.type.PanelTheme;
import me.fzzyhmstrs.fzzy_config.annotations.Version;
import me.fzzyhmstrs.fzzy_config.api.FileType;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.config.ConfigGroup;
import me.fzzyhmstrs.fzzy_config.util.Translatable;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedChoice;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum;
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

    @Name("Show drystreak in notification when catching fish")
    public ValidatedBoolean showFishDrystreakNotification = new ValidatedBoolean(true);

    @Name("Fish dismissal time")
    @Desc("§7How long in seconds, before the notification dismisses")
    public ValidatedInt fishDismissalTime = new ValidatedInt(15, 60, 0, ValidatedNumber.WidgetType.SLIDER);

    @Name("Show notification when catching pets")
    public ValidatedBoolean showPetCatchNotification = new ValidatedBoolean(true);

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

    @ConfigGroup.Pop
    @Name("Scale")
    @Desc("§7This will scale based on the ratio. So 0.5 would be half the size")
    public ValidatedFloat contestPanelScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

    @Name("Daily Quests Panel")
    @Desc("§7Your unfinished daily missions")
    public ConfigGroup dailyQuestPanelGroup = new ConfigGroup("dailyQuestPanel_group");

    @Name("Show Daily Quests Panel")
    public ValidatedBoolean showDailyQuestPanel = new ValidatedBoolean(true);

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

    @ConfigGroup.Pop
    @Name("Scale")
    @Desc("§7This will scale based on the ratio. So 0.5 would be half the size")
    public ValidatedFloat statsPanelScale = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 2.0f, 0.1f), 0.05f);

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

    @Override
    public @NotNull FileType fileType() {
        return FileType.JSON;
    }
}
