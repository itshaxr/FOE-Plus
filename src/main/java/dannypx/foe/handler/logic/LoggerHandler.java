package dannypx.foe.handler.logic;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class LoggerHandler {
    //region Methods
    public static void error(Exception e) {
        error(e.getMessage());
    }

    public static void error(String e) {
        FishOnMCExtras.LOGGER.error("[FOE+] {}", e);
    }

    public static void info(String m) {
        FishOnMCExtras.LOGGER.info("[FOE+] {}", m);
    }

    public static void info(Component m) {
        LoggerHandler.info(m.getString());
    }

    public static void _debug(String m) {
        _debug(Component.literal(m).withStyle(ChatFormatting.YELLOW));
    }

    public static void _debug(String m, ItemStack item) {
        _debug(Component.literal(m).withStyle(ChatFormatting.YELLOW), item);
    }

    public static void _debug(Component m) {
        if(Configs.debugConfig.debugMode.get()) {
            LoggerHandler.info(Component.empty().append("DEBUG: ").append(m));

            if(Configs.debugConfig.showNotification.get()) {
                NotifierHandler.instance().addNotification(
                        new NotifierHandler.Notification(1, 1, Configs.handlerConfig.debugDismissalTime.get(), List.of(m))
                );
            }

        }
    }

    public static void _debug(Component m, ItemStack item) {
        if(Configs.debugConfig.debugMode.get()) {
            LoggerHandler.info(Component.empty().append("DEBUG: ").append(m));

            if(Configs.debugConfig.showNotification.get()) {
                NotifierHandler.instance().addNotification(
                        new NotifierHandler.Notification(item, 1, 1, Configs.handlerConfig.debugDismissalTime.get(), List.of(m))
                );
            }

        }
    }
    //endregion
}
