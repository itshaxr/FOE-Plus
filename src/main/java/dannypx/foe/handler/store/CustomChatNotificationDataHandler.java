package dannypx.foe.handler.store;

import dannypx.foe.handler.Handler;
import dannypx.foe.handler.io.DataFileHandler;
import dannypx.foe.handler.io.DataModels;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.*;

public class CustomChatNotificationDataHandler extends Handler {
    private static CustomChatNotificationDataHandler INSTANCE = new CustomChatNotificationDataHandler();

    public static CustomChatNotificationDataHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new CustomChatNotificationDataHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private CustomChatNotificationDataModel customChatNotificationData = new CustomChatNotificationDataModel();
    private boolean needsUpdate = false;

    public CustomChatNotificationDataModel getCustomChatNotificationData() {
        return customChatNotificationData;
    }

    public void setCustomChatNotificationData(CustomChatNotificationDataModel customChatNotificationData) {
        this.customChatNotificationData = customChatNotificationData;
        this.removeOldVariantNotification();
        this.updateCustomChatNotificationData();
    }

    /// FOE-R's "Caught a <variant> fish with a drystreak of N" line, as it shipped. FOE+ posts its
    /// own variant line, so this one is removed from saved notifications unless you edited it.
    static final String OLD_VARIANT_NAME = "Variant Notification";
    static final String OLD_VARIANT_TEXT =
            "%hide_line.(<not.(<or.(<condition.(<catch.last_caught.fish.variant.id>==\"albino\")>,<condition.(<catch.last_caught.fish.variant.id>==\"melanistic\")>,<condition.(<catch.last_caught.fish.variant.id>==\"trophy\")>)>)>)%&7Caught a &r%catch.last_caught.fish.variant.icon% &7fish with a drystreak of &e%catch.last_caught.fish.variant.last_drystreak%";

    private void removeOldVariantNotification() {
        if (customChatNotificationData == null || customChatNotificationData.notificationList == null) return;
        if (OLD_VARIANT_TEXT.equals(customChatNotificationData.notificationList.get(OLD_VARIANT_NAME))) {
            customChatNotificationData.notificationList.remove(OLD_VARIANT_NAME);
            needsUpdate = true;
        }
    }

    private void updateCustomChatNotificationData() {
        if(needsUpdate) {
            DataFileHandler.instance().saveToFile(DataModels.DataModelType.CUSTOM_CHAT_NOTIFICATION_DATA);
        }
        this.needsUpdate = false;
    }
    //endregion

    //region Methods
    public void tick() {
        if(customChatNotificationData.uuid == null && minecraft.player != null) {
            customChatNotificationData.uuid = minecraft.player.getUUID();
        } else if(customChatNotificationData.uuid != null && this.needsUpdate) {
            this.updateCustomChatNotificationData();
        } else if(!CustomChatNotificationDataModel.CUSTOM_CHAT_NOTIFICATION_DATA_MODEL_VERSION.equals(customChatNotificationData.version)) {
            customChatNotificationData.version = CustomChatNotificationDataModel.CUSTOM_CHAT_NOTIFICATION_DATA_MODEL_VERSION;
            this.updateDefault();
            needsUpdate = true;
        }
    }

    public void init() {
        if(minecraft.player != null) this.setUUID(minecraft.player.getUUID());
    }

    private void setUUID(UUID uuid) {
        this.customChatNotificationData.uuid = uuid;
    }

    public void createNewChatCustomNotification(String id) {
        customChatNotificationData.notificationList.put(id, "");
        needsUpdate = true;
    }

    public void createNewChatCustomNotification(String id, String customChatNotification) {
        customChatNotificationData.notificationList.put(id, customChatNotification);
        needsUpdate = true;
    }

    public String deleteCustomChatNotification(String id) {
        needsUpdate = true;
        return customChatNotificationData.notificationList.remove(id);
    }

    public void updateChatNotification(String currentSelectedChatNotification, String newName, String newText) {
        String newChatNotification;


        if(!Objects.equals(currentSelectedChatNotification, newName)) {
            deleteCustomChatNotification(currentSelectedChatNotification);
            currentSelectedChatNotification = newName;
        }

        newChatNotification = newText;

        customChatNotificationData.notificationList.put(currentSelectedChatNotification, newChatNotification);
        needsUpdate = true;
    }

    public void updateDefault() {
        CustomChatNotificationDataModel.defaultNotifications.forEach((key, timer) -> {
            customChatNotificationData.notificationList.putIfAbsent(key, timer);
        });
    }

    public void fixDefault() {
        customChatNotificationData.notificationList.putAll(CustomChatNotificationDataModel.defaultNotifications);
        needsUpdate = true;
    }

    public void resetChatNotifications() {
        customChatNotificationData.notificationList = new HashMap<>(CustomChatNotificationDataModel.defaultNotifications);

        needsUpdate = true;
    }
    //endregion

    //region Model
    public static class CustomChatNotificationDataModel extends DataModels.DataModel {
        private static final String CUSTOM_CHAT_NOTIFICATION_DATA_MODEL_VERSION = "0.2";

        // FOE-R shipped a variant line here; FOE+ has its own ([FOE+] <variant> Fish · Dry streak N).
        private static final Map<String, String> defaultNotifications = Map.of();

        //Name Notification, Notification
        public Map<String, String> notificationList = new HashMap<>(defaultNotifications);

        public CustomChatNotificationDataModel() {
            super(CUSTOM_CHAT_NOTIFICATION_DATA_MODEL_VERSION, null);
        }
    }
    //endregion

    //region Dev
    /// Field, Pair<Value, Tooltip>
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
                "customNotificationData", Pair.of(Component.literal("[customNotificationData]"), TextHelper.literal(getCustomChatNotificationData()))
        );
    }
    //endregion
}
