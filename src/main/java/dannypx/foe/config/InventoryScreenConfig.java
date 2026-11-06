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

    @Name("Show Stats Element")
    public ValidatedBoolean showStatsElement = new ValidatedBoolean(true);

    @Name("Show Timers & Crew box")
    @Desc("§7Left of your inventory: moon, Tackle Shop, Cosmetic Store and vote timers, then your crew and friends")
    public ValidatedBoolean showMoonTimer = new ValidatedBoolean(true);

    @Name("Show the moon timer")
    public ValidatedBoolean timersMoon = new ValidatedBoolean(true);

    @Name("Show the Tackle Shop timer")
    @Desc("§7Restocks every 4 hours; re-synced whenever the restock message shows in chat. ✔ once you've opened it since")
    public ValidatedBoolean timersTackleShop = new ValidatedBoolean(true);

    @Name("Show the Cosmetic Store timer")
    @Desc("§7Resets every day at 8pm New York time")
    public ValidatedBoolean timersCosmeticStore = new ValidatedBoolean(true);

    @Name("Show your votes")
    @Desc("§7How many of the 7 sites you've voted on in the last 24 hours, and when the first can be voted again")
    public ValidatedBoolean timersVote = new ValidatedBoolean(true);

    @Name("Show your crew and friends")
    public ValidatedBoolean timersCrew = new ValidatedBoolean(true);

    @Name("Show offline crew members and friends")
    public ValidatedBoolean timersOffline = new ValidatedBoolean(true);

    @Override
    public @NotNull FileType fileType() {
        return FileType.JSON;
    }
}
