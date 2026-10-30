package dannypx.foe.config;

import dannypx.foe.FishOnMCExtras;
import me.fzzyhmstrs.fzzy_config.annotations.Version;
import me.fzzyhmstrs.fzzy_config.api.FileType;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.util.Translatable;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/// Settings for auto-tipping, ported from FishOnAutotip (GPL-3.0). Edit them in O → Auto Tip.
@Version(version = 1)
@Translatable.Name("Auto Tip")
@Translatable.Desc("§7Automatically /pay reaction winners and players who place chummers")
public class AutotipConfig extends Config {
    public AutotipConfig() {
        super(Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "autotip_config"));
    }

    @Name("Enable Auto Tip")
    public ValidatedBoolean enabled = new ValidatedBoolean(false);

    @Name("Tip reaction winners")
    public ValidatedBoolean tipReactions = new ValidatedBoolean(true);
    @Name("Reaction tip")
    public ValidatedInt reactionAmount = new ValidatedInt(100, 100_000_000, 100);

    @Name("Tip common chummers")
    public ValidatedBoolean tipCommonChummer = new ValidatedBoolean(true);
    @Name("Common chummer tip")
    public ValidatedInt commonChummerAmount = new ValidatedInt(500, 100_000_000, 100);

    @Name("Tip rare chummers")
    public ValidatedBoolean tipRareChummer = new ValidatedBoolean(true);
    @Name("Rare chummer tip")
    public ValidatedInt rareChummerAmount = new ValidatedInt(1000, 100_000_000, 100);

    @Name("Tip epic chummers")
    public ValidatedBoolean tipEpicChummer = new ValidatedBoolean(true);
    @Name("Epic chummer tip")
    public ValidatedInt epicChummerAmount = new ValidatedInt(1500, 100_000_000, 100);

    @Name("Tip legendary chummers")
    public ValidatedBoolean tipLegendaryChummer = new ValidatedBoolean(true);
    @Name("Legendary chummer tip")
    public ValidatedInt legendaryChummerAmount = new ValidatedInt(2000, 100_000_000, 100);

    @Name("Tip mythical chummers")
    public ValidatedBoolean tipMythicalChummer = new ValidatedBoolean(true);
    @Name("Mythical chummer tip")
    public ValidatedInt mythicalChummerAmount = new ValidatedInt(2500, 100_000_000, 100);

    @Name("Session spending limit ($, 0 = no limit)")
    public ValidatedInt sessionLimit = new ValidatedInt(0, 1_000_000_000, 0);

    @Name("Never tip (names, comma separated)")
    public ValidatedString ignoredPlayers = new ValidatedString("");

    @Name("Show a chat message when tipping")
    public ValidatedBoolean announceTips = new ValidatedBoolean(true);

    @Override
    public @NotNull FileType fileType() {
        return FileType.JSON;
    }
}
