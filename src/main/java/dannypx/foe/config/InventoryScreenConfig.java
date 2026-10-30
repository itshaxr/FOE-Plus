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

    @Name("Show Moon Timer")
    @Desc("§7Time until the next moonrise, shown left of your inventory")
    public ValidatedBoolean showMoonTimer = new ValidatedBoolean(true);

    @Override
    public @NotNull FileType fileType() {
        return FileType.JSON;
    }
}
