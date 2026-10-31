package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.BossEventHandler;
import dannypx.foe.helper.KeyBindHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Lets you hide the wrong-gear / inventory warning box with a key (default -). It stays hidden
 * only at the location where you hid it: moving to another location brings it back by itself,
 * and pressing the key again at the same location shows it again.
 */
public class WarningDismissHandler {
    private static WarningDismissHandler INSTANCE = new WarningDismissHandler();

    /// Location (as on the boss bar) where the warning was hidden, or null when it isn't hidden.
    private String dismissedAt = null;

    public static WarningDismissHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new WarningDismissHandler();
        }
        return INSTANCE;
    }

    private static String here() {
        return BossEventHandler.instance().getLocation().getString().replaceAll("[\\uE000-\\uF8FF]", "").trim();
    }

    /// True while the warning is hidden at the location you are at.
    public boolean isDismissed() {
        if (dismissedAt == null) return false;
        if (!dismissedAt.equals(here())) {
            dismissedAt = null; // you moved: the warning is back
            return false;
        }
        return true;
    }

    public void toggle() {
        Minecraft mc = Minecraft.getInstance();
        String key = KeyBindHelper.getKeyString(Configs.keyBindConfig.dismissWarningKeybind);
        if (isDismissed()) {
            dismissedAt = null;
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("Warning box shown again.").withStyle(ChatFormatting.YELLOW), true);
        } else {
            dismissedAt = here();
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("Warning box hidden until you change location (" + key + " to show it).")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }
}
