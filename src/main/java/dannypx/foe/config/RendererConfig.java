package dannypx.foe.config;

import dannypx.foe.FishOnMCExtras;
import me.fzzyhmstrs.fzzy_config.annotations.Version;
import me.fzzyhmstrs.fzzy_config.api.FileType;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.config.ConfigGroup;
import me.fzzyhmstrs.fzzy_config.util.Translatable;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedFloat;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedNumber;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

@Version(version = 8)
@Translatable.Name("Renderer Configuration")
@Translatable.Desc("§7Configure renderer of various things")
public class RendererConfig extends Config {
    public RendererConfig() {
        super(Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "renderer_config"));
    }

    @Name("Hat Renderer")
    @Desc("§7This is the hat renderer for server hats")
    public ConfigGroup hatGroup = new ConfigGroup("hat_group");

    @Name("The hat height")
    public ValidatedNumber<Float> hatHeight = new ValidatedFloat(1.00f, 1.50f, 0.50f, ValidatedNumber.WidgetType.SLIDER);

    @ConfigGroup.Pop
    @Name("The hat scale")
    public ValidatedNumber<Float> hatScale = new ValidatedFloat(1.00f, 1.25f, 0.50f, ValidatedNumber.WidgetType.SLIDER);

    @Name("Rod Renderer")
    @Desc("§7This is the rod renderer for server rods")
    public ConfigGroup rodGroup = new ConfigGroup("rod_group");

    @Name("The rod scale")
    public ValidatedNumber<Float> rodScale = new ValidatedFloat(1.00f, 1.50f, 0.50f, ValidatedNumber.WidgetType.SLIDER);

    @Name("The rod x position")
    public ValidatedNumber<Float> rodXPosition = new ValidatedFloat(0.00f, 0.50f, -0.50f, ValidatedNumber.WidgetType.SLIDER);

    @Name("The rod y position")
    public ValidatedNumber<Float> rodYPosition = new ValidatedFloat(0.00f, 0.50f, -0.50f, ValidatedNumber.WidgetType.SLIDER);

    @Name("The rod z position")
    public ValidatedNumber<Float> rodZPosition = new ValidatedFloat(0.00f, 0.50f, -0.50f, ValidatedNumber.WidgetType.SLIDER);

    @Name("The line x position after rod scaling")
    public ValidatedNumber<Float> lineXPosition = new ValidatedFloat(0.00f, 0.50f, -0.50f, ValidatedNumber.WidgetType.SLIDER);

    @Name("The rod y position  after rod scaling")
    public ValidatedNumber<Float> lineYPosition = new ValidatedFloat(0.00f, 0.50f, -0.50f, ValidatedNumber.WidgetType.SLIDER);

    @ConfigGroup.Pop
    @Name("The rod z position  after rod scaling")
    public ValidatedNumber<Float> lineZPosition = new ValidatedFloat(0.00f, 0.50f, -0.50f, ValidatedNumber.WidgetType.SLIDER);

    @Name("Item Marker")
    @Desc("§7This is the small markers on items")
    public ConfigGroup itemMarkerGroup = new ConfigGroup("item_marker_group");

    @Name("Show Equipped Pet Item")
    public ValidatedBoolean showPetEquippedMarker = new ValidatedBoolean(true);

    @Name("Gold ring on equipped pet")
    public ValidatedBoolean showEquippedPetHighlight = new ValidatedBoolean(true);

    @Name("Always show pet rating letter")
    public ValidatedBoolean showPetRatingLetter = new ValidatedBoolean(true);

    @Name("Show max Luck/Scale pet marker")
    public ValidatedBoolean showMaxPetStatsMarker = new ValidatedBoolean(true);

    @Name("Show Rarity marker")
    public ValidatedBoolean showRarityMarker = new ValidatedBoolean(true);

    @ConfigGroup.Pop
    @Name("Blacklist items from rendering rarity marker")
    @Desc("§7Blacklist item types here.\nYou can put item types here separated by commas.\n\nIf you don't know what item type certain items are, check in the Debug Screen.\n\n1. Turn on Debug mode in Debug Configurations\n2. Hold an item\n3. Go to the Debug Screen\n4. Scroll to InventoryHandler\n5. You can read the item type in Current Held Item")
    public ValidatedString blackListItems = new ValidatedString.Builder("")
            .withCorrector().build();

    @Name("Entity Model Renderer")
    @Desc("§7This is the model renderer for entities")
    public ConfigGroup entityModelGroup = new ConfigGroup("entity_model_renderer_group");

    @Name("Show pet")
    public ValidatedBoolean showPet = new ValidatedBoolean(true);

    @Name("Show pet names")
    public ValidatedBoolean showPetName = new ValidatedBoolean(true);

    @Name("Show nameplate on players")
    public ValidatedBoolean showPlayerNamePlate = new ValidatedBoolean(true);

    @Name("Use the 3D fishing hook texture")
    public ValidatedBoolean showNewFishingHook = new ValidatedBoolean(true);

    @Name("Use custom hat positions")
    public ValidatedBoolean showHatPositions = new ValidatedBoolean(true);

    @Name("Use custom fishing rod positions")
    public ValidatedBoolean showFishingRodPositions = new ValidatedBoolean(true);

    @Name("Fish Holding")
    public ConfigGroup fishHolding = new ConfigGroup("fish_holding_group");

    @Name("Hold fish in both hands in third person")
    public ValidatedBoolean useBothHandsHoldingFishThirdPerson = new ValidatedBoolean(true);

    @Name("Hold fish in both hands in first person")
    public ValidatedBoolean useBothHandsHoldingFishFirstPerson = new ValidatedBoolean(true);

    public ValidatedFloat firstPersonMinimumScaleInUnits = new ValidatedFloat(256, 2048, 16);

    public ValidatedFloat firstPersonMaximumScaleInUnits = new ValidatedFloat(1400, 2048, 16);

    public ValidatedFloat firstPersonRateScaleInUnits = new ValidatedFloat(1.00F, 10.00F, 0.01F, ValidatedNumber.WidgetType.SLIDER);

    public ValidatedFloat thirdPersonMinimumScaleInUnits = new ValidatedFloat(16, 2048, 16);

    public ValidatedFloat thirdPersonMaximumScaleInUnits = new ValidatedFloat(50, 2048, 16);

    @ConfigGroup.Pop
    public ValidatedFloat thirdPersonRateScaleInUnits = new ValidatedFloat(0.60F, 10.00F, 0.01F, ValidatedNumber.WidgetType.SLIDER);

    @Name("Fishing Hook Light")
    @Desc("§7This is the light that emits from the fishing hook")
    public ConfigGroup fishingHookLightGroup = new ConfigGroup("fishing_hook_light_renderer_group");

    @Name("Emit light from fishing hook")
    public ValidatedBoolean showLightFishingHook = new ValidatedBoolean(true);

    @Name("Light Radius")
    public ValidatedInt lightRadiusFishingHook = new ValidatedInt(5, 15, 0, ValidatedNumber.WidgetType.SLIDER);

    @ConfigGroup.Pop
    @Name("Max Light Level")
    public ValidatedInt maxLightLevelFishingHook = new ValidatedInt(10, 15, 0, ValidatedNumber.WidgetType.SLIDER);

    @ConfigGroup.Pop
    @Name("Show bait on fishing hook")
    public ValidatedBoolean showBaitOnFishingHook = new ValidatedBoolean(true);

    @Name("Show ripple on bite")
    public ValidatedBoolean showRippleOnBite = new ValidatedBoolean(true);

    @Name("Hide armor on players")
    public ValidatedBoolean hideArmor = new ValidatedBoolean(false);

    @Name("Small Stack Count Number")
    public ConfigGroup smallStackCountGroup = new ConfigGroup("small_stack_count_group");

    @Name("Use small stack count number")
    public ValidatedBoolean useSmallStackCountNumber = new ValidatedBoolean(true);

    @ConfigGroup.Pop
    @Name("Show stack count on bait")
    public ValidatedBoolean showStackCountOnBait = new ValidatedBoolean(true);

    @Name("Tab Renderer")
    @Desc("§7This is the renderer for tab")
    public ConfigGroup tabRendererGroup = new ConfigGroup("tab_renderer_group");

    @ConfigGroup.Pop
    @Name("Show online crew members")
    public ValidatedBoolean showOnlineCrewMembers = new ValidatedBoolean(true);

    @Name("Chat Renderer")
    @Desc("§7This is the renderer for chat")
    public ConfigGroup chatRendererGroup = new ConfigGroup("chat_renderer_group");

    @ConfigGroup.Pop
    @Name("Show crew chat marker")
    public ValidatedBoolean showCrewChatMarker = new ValidatedBoolean(true);

    @Override
    public @NotNull FileType fileType() {
        return FileType.JSON;
    }
}
