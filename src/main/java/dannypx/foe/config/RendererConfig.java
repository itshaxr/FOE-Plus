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

    @Name("Fishing line colour")
    @Desc("§7Your own fishing line in your colour (only on your screen). Default is the game's own")
    public ValidatedString fishingLineColor = new ValidatedString("");

    @Name("Equipped pet item star colour")
    @Desc("§7The star on a pet holding an item: Default is the held item's own colour (pink for a Bigfoot Amulet); pick one to use it for every pet")
    public ValidatedString petEquippedMarkerColor = new ValidatedString("");

    @Name("+ above FOE+ users' heads")
    @Desc("§7Players near you who use this mod get a + after their name above their head")
    public ValidatedBoolean showFoePlusMarker = new ValidatedBoolean(true);

    @Name("FOE+ icon above FOE+ users' heads")
    @Desc("§7The FOE+ icon before the name above the head of players who use this mod")
    public ValidatedBoolean showFoePlusIcon = new ValidatedBoolean(true);

    @Name("+ in the tab list")
    @Desc("§7Players who use this mod get their + after their name in the tab list (Tab)")
    public ValidatedBoolean showFoePlusTabMarker = new ValidatedBoolean(true);

    @Name("Floating text backgrounds")
    @Desc("§7The dark box behind server text like FISH MERCHANT. Some shaders (Photon) draw it solid black. AUTO hides it only while a shader pack is on")
    public me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum<dannypx.foe.type.TextBackgroundMode> textDisplayBackground =
            new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum<>(dannypx.foe.type.TextBackgroundMode.AUTO, me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum.WidgetType.CYCLING);

    @Name("Your + colour")
    @Desc("§7The colour of the + after your name, for everyone with FOE+ (pick it in the O menu). -1 = default")
    public ValidatedInt foePlusColor = new ValidatedInt(-1, 16, -1);

    /// Your + in a colour of your own from the colour wheel ("#FF77CC"; "" for one from the list). Picked in the O
    /// menu, so it has no line of its own in the settings.
    public ValidatedString foePlusCustomColor = new ValidatedString("");

    @Name("Clean tab list tags")
    @Desc("§7Show rank tags in the tab list as FOE's compact lettered squares")
    public ValidatedBoolean cleanTabListTags = new ValidatedBoolean(true);

    @Name("Gold ring on equipped pet")
    public ValidatedBoolean showEquippedPetHighlight = new ValidatedBoolean(true);

    @Name("Always show pet rating letter")
    public ValidatedBoolean showPetRatingLetter = new ValidatedBoolean(true);

    @Name("Show max Luck/Scale pet marker")
    public ValidatedBoolean showMaxPetStatsMarker = new ValidatedBoolean(true);

    @Name("Show max stat rod part marker")
    @Desc("§7A purple dot in the top-right corner of rod parts rolled at the highest their rarity can roll, and on rods with two different max stats (only with the calibration icons off: those turn purple instead)")
    public ValidatedBoolean showMaxRodPartMarker = new ValidatedBoolean(true);

    @Name("Show rod part calibration icon")
    @Desc("§7A small icon in the bottom-right of a rod part (and of a rod, for its parts) for what it's calibrated for: XP, money, luck, scale, prospect, albino, melanistic, trophy or bite/reel speed. All purple when rolled at its max")
    public ValidatedBoolean showRodPartRollIcon = new ValidatedBoolean(true);

    @Name("Show Rarity marker")
    public ValidatedBoolean showRarityMarker = new ValidatedBoolean(true);

    @Name("Item icon style")
    @Desc("§7How the markers on items look: FOE+ (the rarity dot and the rating letter) or FOE (the rarity corner and a corner in the rating's colour, for players coming from FOE). The held-item star takes the amulet's colour in both. Also in O → Icons")
    public me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum<dannypx.foe.type.IconStyle> iconStyle =
            new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum<>(dannypx.foe.type.IconStyle.FOE_PLUS, me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum.WidgetType.CYCLING);

    @Name("Skin-coloured pet tooltips")
    @Desc("§7A pet wearing a skin: its tooltip framed in the skin's colours")
    public ValidatedBoolean skinTooltipFrames = new ValidatedBoolean(true);

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

    @Name("3D bobber style")
    @Desc("§7The colours of your 3D bobber. Also in O → Bobber")
    public me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum<dannypx.foe.type.BobberStyle> bobberStyle =
            new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum<>(dannypx.foe.type.BobberStyle.CLASSIC, me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum.WidgetType.CYCLING);

    @Name("3D bobber shape")
    @Desc("§7Classic (the block bobber) or Round (a round float, like a real one). Every colour preset works on both. Also in O → Bobber")
    public me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum<dannypx.foe.type.BobberShape> bobberShape =
            new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum<>(dannypx.foe.type.BobberShape.CLASSIC, me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum.WidgetType.CYCLING);

    @Name("Show other FOE+ users' bobber styles")
    @Desc("§7Other FOE+ users' bobbers show the preset they picked. Off, or for players without FOE+: Classic")
    public ValidatedBoolean showOtherBobberStyles = new ValidatedBoolean(true);

    @Name("Bobber timer")
    @Desc("§7How long your bobber has been out, in seconds, over your own bobber (hidden once a fish is on)")
    public ValidatedBoolean showBobberTimer = new ValidatedBoolean(true);

    @Name("Bobber timer: show \"s\" after the time")
    public ValidatedBoolean bobberTimerSecondsSuffix = new ValidatedBoolean(true);

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

    // From tacklebox by landofif (MIT), with its permission.
    @Name("Water Rings")
    @Desc("§7Flat rings on the water at your bobber: they come quicker as a fish swims in, a bigger one on the bite, a wake behind your fish, and one in the fish's rarity colour when you catch it")
    public ConfigGroup waterRingsGroup = new ConfigGroup("water_rings_group");

    @Name("Water rings")
    public ValidatedBoolean waterRingsOn = new ValidatedBoolean(true);

    @Name("Rings at the bobber as a fish comes")
    @Desc("§7They come quicker as the fish nears, and one bigger ring on the bite")
    public ValidatedBoolean waterRingsBite = new ValidatedBoolean(false);

    @Name("Wake behind your fish")
    @Desc("§7Small rings along your fish's trail as it swims in")
    public ValidatedBoolean waterRingsWake = new ValidatedBoolean(false);

    @Name("Ring when you catch a fish")
    @Desc("§7One big ring where your bobber was, in the fish's rarity colour")
    public ValidatedBoolean waterRingsCatch = new ValidatedBoolean(true);

    @Name("Sparkles on a legendary, mythical or special catch")
    public ValidatedBoolean waterRingsSparkles = new ValidatedBoolean(true);

    @Name("Strength in %")
    public ValidatedInt waterRingsOpacity = new ValidatedInt(60, 100, 5, ValidatedNumber.WidgetType.SLIDER);

    @Name("Size")
    public ValidatedFloat waterRingsSize = ValidatedNumber.withIncrement(new ValidatedFloat(0.95f, 2.5f, 0.5f), 0.05f);

    @ConfigGroup.Pop
    @Name("Thickness")
    public ValidatedFloat waterRingsWidth = ValidatedNumber.withIncrement(new ValidatedFloat(1.0f, 3.0f, 0.5f), 0.05f);

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
