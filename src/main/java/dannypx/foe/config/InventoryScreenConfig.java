package dannypx.foe.config;

import dannypx.foe.FishOnMCExtras;
import me.fzzyhmstrs.fzzy_config.annotations.Version;
import me.fzzyhmstrs.fzzy_config.api.FileType;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.util.Translatable;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

@Version(version = 1)
@Translatable.Name("Inventory Configuration")
@Translatable.Desc("§7Configure Inventory elements")
public class InventoryScreenConfig extends Config {
    public InventoryScreenConfig() {
        super(Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "inventory_config"));
    }

    /// Off in FOE+: All-Time Stats already shows the same numbers. (Renamed from
    /// showStatsElement, so it's off for everyone once, even if it was saved as on.)
    @Name("Stats tab open")
    @Desc("§7The fishing stats beside your inventory: opened and closed with the S tab on its right edge")
    public ValidatedBoolean showFishingStatsList = new ValidatedBoolean(false);

    @Name("Show the search bar")
    @Desc("§7Above your inventory, like the Personal Vault's: matching items get a red outline")
    public ValidatedBoolean showSearchBar = new ValidatedBoolean(true);

    @Name("Show Timers & Crew box")
    @Desc("§7Left of your inventory: moon, Tackle Shop, Cosmetic Store and vote timers, then your crew and friends")
    public ValidatedBoolean showMoonTimer = new ValidatedBoolean(true);

    @Name("Show the moon timer")
    public ValidatedBoolean timersMoon = new ValidatedBoolean(true);

    @Name("Show the Tackle Shop timer")
    @Desc("§7Restocks every 4 hours; re-synced whenever the restock message shows in chat. ✔ once you've opened it since")
    public ValidatedBoolean timersTackleShop = new ValidatedBoolean(true);

    @Name("Show the Cosmetic Store timer")
    @Desc("§7Resets every day at midnight UTC (7pm EST); re-synced from /events")
    public ValidatedBoolean timersCosmeticStore = new ValidatedBoolean(true);

    @Name("Show your votes")
    @Desc("§7How many of the 7 sites you've voted on in the last 24 hours, and when the first can be voted again")
    public ValidatedBoolean timersVote = new ValidatedBoolean(true);

    @Name("Show your crew and friends")
    public ValidatedBoolean timersCrew = new ValidatedBoolean(true);

    @Name("Show offline crew members and friends")
    public ValidatedBoolean timersOffline = new ValidatedBoolean(true);

    @Name("Show what your fish are worth beside chests and your vault")
    public ValidatedBoolean showFishValue = new ValidatedBoolean(true);

    @Name("Show which instance each is on")
    @Desc("§7\"InfernalShadow (Instance 5)\": FOE+ runs /find quietly for each of them (all at once when you join, then every so often) and hides the answers")
    public ValidatedBoolean timersInstances = new ValidatedBoolean(true);

    @Override
    public @NotNull FileType fileType() {
        return FileType.JSON;
    }
}
