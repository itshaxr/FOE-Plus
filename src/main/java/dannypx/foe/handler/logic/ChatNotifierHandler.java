package dannypx.foe.handler.logic;

import dannypx.foe.handler.Handler;
import dannypx.foe.handler.store.CustomChatNotificationDataHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.placeholder.evaluator.PlaceholderResult;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Map;

public class ChatNotifierHandler extends Handler {
    private static ChatNotifierHandler INSTANCE = new ChatNotifierHandler();

    public static ChatNotifierHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new ChatNotifierHandler();
        }
        return INSTANCE;
    }

    //region Fields
    public void notifyChatOnTrigger(String[] notificationIds) {
        for (String notificationId : notificationIds) {
            if(notificationId != null) {
                String notification = CustomChatNotificationDataHandler.instance().getCustomChatNotificationData().notificationList.getOrDefault(notificationId.trim(), "");

                if(!notification.isBlank()) {
                    PlaceholderResult result = PlaceholderHandler.instance().resolve(notification, false);

                    if((result.success()[0] || !result.errors().isEmpty()) && !result.success()[1]) {
                        this.sendChatMessage(result.text());
                    }
                }
            }
        }
    }

    public void sendChatMessage(Component message) {
        minecraft.gui.getChat().addMessage(
                TextHelper.concat(
                        dannypx.foe.helper.FoeChat.prefix(),
                        message
                )
        );
    }
    //endregion

    //region Methods
    //endregion

    //region Dev

    /// Field, Pair<Value, Tooltip>
    @Override
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
                "key", Pair.of(Component.literal("value"), Component.empty())
        );
    }
    //endregion
}
