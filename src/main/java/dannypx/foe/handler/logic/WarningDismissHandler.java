package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.helper.KeyBindHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Lets you hide the wrong-gear / inventory warning box with a key (default -). It stays hidden everywhere (through
 * location changes and restarts) until you press the key again, or turn "Warning box hidden" off in HUD settings.
 */
public class WarningDismissHandler {
    private static WarningDismissHandler INSTANCE = new WarningDismissHandler();

    public static WarningDismissHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new WarningDismissHandler();
        }
        return INSTANCE;
    }

    /// True while the warning box is hidden.
    public boolean isDismissed() {
        return Configs.hudConfig.gearWarningDismissed.get();
    }

    /// The dismiss key's name, as shown in the box ("-").
    public static String key() {
        try {
            return KeyBindHelper.getKeyString(Configs.keyBindConfig.dismissWarningKeybind);
        } catch (RuntimeException e) {
            return "-";
        }
    }

    public void toggle() {
        Minecraft mc = Minecraft.getInstance();
        boolean hide = !isDismissed();
        Configs.hudConfig.gearWarningDismissed.accept(hide);
        Configs.hudConfig.save();
        if (mc.player == null) return;
        if (hide) {
            mc.player.displayClientMessage(Component.literal("Warning box hidden (press " + key() + " to show it again).")
                    .withStyle(ChatFormatting.GRAY), true);
        } else {
            mc.player.displayClientMessage(Component.literal("Warning box shown again.").withStyle(ChatFormatting.YELLOW), true);
        }
    }
}
