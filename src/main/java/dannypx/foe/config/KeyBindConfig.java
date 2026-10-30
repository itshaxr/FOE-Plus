package dannypx.foe.config;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.type.baitsorter.BaitSortOrder;
import dannypx.foe.type.keybind.KeyBindMode;
import me.fzzyhmstrs.fzzy_config.annotations.Version;
import me.fzzyhmstrs.fzzy_config.api.FileType;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.config.ConfigGroup;
import me.fzzyhmstrs.fzzy_config.screen.context.ContextInput;
import me.fzzyhmstrs.fzzy_config.util.Translatable;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedKeybind;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedNumber;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

@Version(version = 4)
@Translatable.Name("Controls")
public class KeyBindConfig extends Config {
    public KeyBindConfig() {
        super(Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "keybinding_config"));
    }

    @Name("Open FOER Menu")
    public ValidatedKeybind openMainKeybind = new ValidatedKeybind(GLFW.GLFW_KEY_O, ContextInput.KEYBOARD, false, false, false);

    @Name("Open Auto Tip menu")
    public ValidatedKeybind openAutotipKeybind = new ValidatedKeybind(GLFW.GLFW_KEY_M, ContextInput.KEYBOARD, false, false, false);

    @Name("Open Pet Prices menu")
    public ValidatedKeybind openPetPricesKeybind = new ValidatedKeybind(GLFW.GLFW_KEY_U, ContextInput.KEYBOARD, false, false, false);

    @Name("Inspect")
    public ConfigGroup inspectGroup = new ConfigGroup("inspect_group");

    @Name("Inspect Key")
    public ValidatedKeybind inspectKeybind = new ValidatedKeybind(GLFW.GLFW_KEY_LEFT_SHIFT, ContextInput.KEYBOARD, false, false, false);

    @Name("Button mode")
    public ValidatedEnum<KeyBindMode> inspectMode = new ValidatedEnum<>(KeyBindMode.HOLD, ValidatedEnum.WidgetType.CYCLING);

    @ConfigGroup.Pop
    @Name("Inspect always on")
    @Desc("§7Always show pet %, armor rolls and other inspect info, without holding the inspect key")
    public ValidatedBoolean inspectAlwaysOn = new ValidatedBoolean(false);

    @Name("Enable page scrolling with scroll wheel")
    public ValidatedBoolean scrollWheelScrolling = new ValidatedBoolean(true);


    // Bait sorter (ported from FOECollab)
    @Name("Bait Sorter")
    public ConfigGroup baitSorterGroup = new ConfigGroup("bait_sorter_group");

    @Name("Sort Baits Key")
    public ValidatedKeybind sortBaitsKeybind = new ValidatedKeybind(GLFW.GLFW_KEY_B, ContextInput.KEYBOARD, false, false, false);

    @Name("Sort order")
    public ValidatedEnum<BaitSortOrder> baitSortOrder = new ValidatedEnum<>(BaitSortOrder.COMMON_TO_LEGENDARY, ValidatedEnum.WidgetType.CYCLING);

    @ConfigGroup.Pop
    @Name("Click speed (1 = safest, 8 = fastest)")
    public ValidatedInt baitSortClickSpeed = new ValidatedInt(1, 8, 1, ValidatedNumber.WidgetType.SLIDER);

    @Override
    public @NotNull FileType fileType() {
        return FileType.JSON;
    }
}
