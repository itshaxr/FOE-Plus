package dannypx.foe.config;

import dannypx.foe.FishOnMCExtras;
import me.fzzyhmstrs.fzzy_config.annotations.IgnoreVisibility;
import me.fzzyhmstrs.fzzy_config.annotations.RootConfig;
import me.fzzyhmstrs.fzzy_config.annotations.Version;
import me.fzzyhmstrs.fzzy_config.api.FileType;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.util.Translatable;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

@Version(version = 4)
@RootConfig
@Translatable.Name("FOE+")
@IgnoreVisibility
public class MainConfig extends Config {
    public MainConfig() {
        super(Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "main_config"));
    }

    @Name("§7§oDev§8§o: §f§oEnable Mod")
    @Desc("§4WARNING\nThis will turn off the mod when false. NOTHING will work when false.\nTo turn it back on, do /foe config")
    public ValidatedBoolean enableMod = new ValidatedBoolean(true);

    @Name("Wiki URL")
    @Desc("§7The URL used for the wiki button")
    public ValidatedString wikiUrl = new ValidatedString.Builder("https://fishonextras.github.io/FishonMC-Extras-R-Wiki/").withCorrector().build();

    @Name("Pet price sheet link")
    @Desc("§7Google Sheets link (shared 'anyone with the link') for the pet price menu (U). Rating bands down column A, rarities across the top")
    public ValidatedString petPriceSheetUrl = new ValidatedString.Builder("https://docs.google.com/spreadsheets/d/1mmOt9-hvCfT3flCekELM7F3FcOrbygwKGOrNyLwA2dk/edit?gid=0#gid=0").withCorrector().build();

    @Name("Travel map")
    @Desc("§7Shows the Locations menu (/tt) as FOE+'s world map: click a place to travel, right-click to view its species. \"Change view\" on the map switches between the maps and the server's menu any time")
    public ValidatedBoolean travelMap = new ValidatedBoolean(true);

    @Name("Travel map view")
    @Desc("§7Realistic: the map built from the places themselves. Fantasy: the first painted map. Classic: the server's own menu. Also \"Change view\" on the map")
    public me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum<dannypx.foe.type.MapView> travelMapView =
            new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum<>(dannypx.foe.type.MapView.REALISTIC, me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum.WidgetType.CYCLING);

    @Name("Bait price: Common")
    @Desc("§7What one Common bait is worth to you, for the baitbox total in chat")
    public me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt baitPriceCommon = new me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt(250, 100_000_000, 0);

    @Name("Bait price: Rare")
    public me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt baitPriceRare = new me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt(500, 100_000_000, 0);

    @Name("Bait price: Epic")
    public me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt baitPriceEpic = new me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt(1000, 100_000_000, 0);

    @Name("Bait price: Legendary")
    public me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt baitPriceLegendary = new me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt(5000, 100_000_000, 0);

    @Name("Bait price: Mythical")
    public me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt baitPriceMythical = new me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt(10000, 100_000_000, 0);

    @Name("Baitbox total in chat")
    @Desc("§7When you open a baitbox's Package: what the bait in it is worth at your prices")
    public ValidatedBoolean baitboxValueChat = new ValidatedBoolean(true);

    @Name("Bait stack value in tooltips")
    @Desc("§7On a bait's Stock line: what the stack is worth at your prices (Stock: 18x · $4,500)")
    public ValidatedBoolean baitTooltipValue = new ValidatedBoolean(true);

    @Name("Pet price in tooltips")
    @Desc("§7At the top right of a pet's tooltip, on its name's line: what it's worth on your pet price sheet (Pet Prices, U)")
    public ValidatedBoolean petTooltipPrice = new ValidatedBoolean(true);

    // Pets on the travel map: off for now (TravelMapHandler.PETS_OFF), the setting kept for when they return.
    public transient me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean travelMapPets = new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean(true);

    @Name("Presets screen")
    @Desc("§7Shows the Fishing Presets menu with all ten presets on one panel and an Armor / Rods switch, instead of paging through the server's menu. Turn off to use the server's own menu")
    public ValidatedBoolean presetsPanel = new ValidatedBoolean(true);

    @Name("Presets screen style")
    @Desc("§7How the preset rows look. A: clean rows. B: cards with the pieces on a tray. C: lighter slots and a round marker. Classic: the server's own menu. Also the Style menu on the panel")
    public me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum<dannypx.foe.type.PresetsStyle> presetsStyle =
            new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum<>(dannypx.foe.type.PresetsStyle.B, me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum.WidgetType.CYCLING);

    @Name("Tell me about FOE+ updates")
    @Desc("§7When a newer FOE+ is on GitHub, a line in chat a few seconds after you join, or while you play when one comes out (checked every 10 minutes). Once per game: after that, only on your next join after restarting")
    public ValidatedBoolean updateCheck = new ValidatedBoolean(true);

    @Name("Use FOE title screen")
    @Desc("§7Replace the main menu with the FOE title screen (Join FishOnMC button, player count)")
    public ValidatedBoolean useCustomTitleScreen = new ValidatedBoolean(true);

    @Override
    public @NotNull FileType fileType() {
        return FileType.JSON;
    }
}
