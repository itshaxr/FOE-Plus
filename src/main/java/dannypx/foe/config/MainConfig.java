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
@Translatable.Name("FishOnExtras Rebirth")
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
    @Desc("§7Shows the Locations menu (/tt) as FOE+'s world map: click a place to travel, right-click to view its species. \"Classic menu\" on the map switches back any time")
    public ValidatedBoolean travelMap = new ValidatedBoolean(true);

    @Name("Presets screen")
    @Desc("§7Shows the Fishing Presets menu with all ten presets on one panel and an Armor / Rods switch, instead of paging through the server's menu. \"Classic menu\" on the panel switches back any time")
    public ValidatedBoolean presetsPanel = new ValidatedBoolean(true);

    @Name("Tell me about FOE+ updates")
    @Desc("§7When a newer FOE+ is on GitHub, a line in chat a few seconds after you join (nothing when you're up to date)")
    public ValidatedBoolean updateCheck = new ValidatedBoolean(true);

    @Name("Use FOE title screen")
    @Desc("§7Replace the main menu with the FOE title screen (Join FishOnMC button, player count)")
    public ValidatedBoolean useCustomTitleScreen = new ValidatedBoolean(true);

    @Override
    public @NotNull FileType fileType() {
        return FileType.JSON;
    }
}
