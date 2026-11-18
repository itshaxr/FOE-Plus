package dannypx.foe.handler.store;

import dannypx.foe.handler.Handler;
import dannypx.foe.handler.io.DataFileHandler;
import dannypx.foe.handler.io.DataModels;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.type.Alignment;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

public class CustomHudIconDataHandler extends Handler {
    private static CustomHudIconDataHandler INSTANCE = new CustomHudIconDataHandler();

    public static CustomHudIconDataHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new CustomHudIconDataHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private CustomHudIconDataModel customHudIconData = new CustomHudIconDataModel();

    private boolean needsUpdate = false;
    public boolean needsRenderUpdate = false;

    public CustomHudIconDataModel getCustomHudIconData() {
        return customHudIconData;
    }

    public void setCustomHudIconData(CustomHudIconDataModel customHudIconData) {
        this.customHudIconData = customHudIconData;
        this.needsRenderUpdate = true;
        this.updateCustomHudData();
    }

    private void updateCustomHudData() {
        if(needsUpdate) {
            needsRenderUpdate = true;
            DataFileHandler.instance().saveToFile(DataModels.DataModelType.CUSTOM_HUD_ICON_DATA);
        }
        this.needsUpdate = false;
    }
    //endregion

    //region Methods
    public void tick() {
        if(customHudIconData.uuid == null && minecraft.player != null) {
            customHudIconData.uuid = minecraft.player.getUUID();
        } else if(customHudIconData.uuid != null && this.needsUpdate) {
            this.updateCustomHudData();
        } else if(customHudIconData.uuid != null && !CustomHudIconDataModel.CUSTOM_HUD_ICON_DATA_MODEL_VERSION.equals(customHudIconData.version)) {
            // Older data (FOE-R's, or FOE+ from before 0.2): its icon HUDs are left behind once, for everyone
            // (a copy kept as custom_hud_icon.removed.json). Only icon HUDs made from now on are kept.
            if (!customHudIconData.customHudIconDataList.isEmpty()) {
                dannypx.foe.handler.logic.LoggerHandler.info("Custom HUD icons: removing " + customHudIconData.customHudIconDataList.size() + " left from FOE-R / older FOE+ (copy kept)");
                dannypx.foe.handler.io.DataFileHandler.instance().saveCopy(dannypx.foe.handler.io.DataModels.DataModelType.CUSTOM_HUD_ICON_DATA, "removed");
            }
            customHudIconData.customHudIconDataList = new HashMap<>();
            customHudIconData.version = CustomHudIconDataModel.CUSTOM_HUD_ICON_DATA_MODEL_VERSION;
            needsUpdate = true;
            needsRenderUpdate = true;
        }
    }

    public void init() {
        if(minecraft.player != null) {
            this.setUUID(minecraft.player.getUUID());
            needsRenderUpdate = true;
        }
    }

    private void setUUID(UUID uuid) {
        this.customHudIconData.uuid = uuid;
    }

    public void createNewCustomHudIcon(String id) {
        customHudIconData.customHudIconDataList.put(id, new CustomHudIcon());
        needsUpdate = true;
    }

    public void createNewCustomHudIcon(String id, CustomHudIcon customHudIcon) {
        customHudIconData.customHudIconDataList.put(id, customHudIcon);
        needsUpdate = true;
    }

    public CustomHudIcon deleteCustomHudIcon(String id) {
        needsUpdate = true;
        return customHudIconData.customHudIconDataList.remove(id);
    }

    public CustomHudIcon updateHudIcon(String currentSelectedHud, String newName, float scale, boolean showBackground, boolean showBars, boolean showElement, boolean useTrackerIcon, String icon, IconType iconType) {
        CustomHudIcon newHudIcon = customHudIconData.customHudIconDataList.get(currentSelectedHud);

        if(!Objects.equals(currentSelectedHud, newName)) {
            newHudIcon = deleteCustomHudIcon(currentSelectedHud);
            currentSelectedHud = newName;
        }

        newHudIcon.icon = icon;
        newHudIcon.scale = scale;
        newHudIcon.showBackground = showBackground;
        newHudIcon.showBars = showBars;
        newHudIcon.useTrackerItem = useTrackerIcon;
        newHudIcon.iconType = iconType;
        newHudIcon.showElement = showElement;

        customHudIconData.customHudIconDataList.put(currentSelectedHud, newHudIcon);
        needsUpdate = true;

        return newHudIcon;
    }

    public void updateHudIcon(String currentSelectedHudIcon, int xPercent, int yPercent, Alignment alignment) {
        CustomHudIcon newHud = customHudIconData.customHudIconDataList.get(currentSelectedHudIcon);

        newHud.xPos = xPercent;
        newHud.yPos = yPercent;
        newHud.alignment = alignment;

        customHudIconData.customHudIconDataList.put(currentSelectedHudIcon, newHud);
        needsUpdate = true;
    }

    public void updateDefault() {
        CustomHudIconDataModel.defaultHudsIcon.forEach((key, hudIcon) -> {
            customHudIconData.customHudIconDataList.putIfAbsent(key, hudIcon);
        });
    }

    public void fixDefault() {
        customHudIconData.customHudIconDataList.putAll(CustomHudIconDataModel.defaultHudsIcon);
        needsUpdate = true;
    }

    /// Nothing but the example icon HUDs (a fresh install).
    public boolean onlyExamples() {
        return CustomHudIconDataModel.defaultHudsIcon.keySet().containsAll(customHudIconData.customHudIconDataList.keySet());
    }

    public void resetHudIcons() {
        customHudIconData.customHudIconDataList = new HashMap<>(CustomHudIconDataModel.defaultHudsIcon);

        needsUpdate = true;
    }
    //endregion

    //region Model
    public static class CustomHudIconDataModel extends DataModels.DataModel {
        private static final String CUSTOM_HUD_ICON_DATA_MODEL_VERSION = "0.2";

        private static final Map<String, CustomHudIcon> defaultHudsIcon = Map.of(

        );

        // ID of the HUD Icon
        public Map<String, CustomHudIcon> customHudIconDataList = new HashMap<>(defaultHudsIcon);

        public CustomHudIconDataModel() {
            super(CUSTOM_HUD_ICON_DATA_MODEL_VERSION, null);
        }
    }
    //endregion

    //region Hud Icon Object
    public static class CustomHudIcon {
        // String, isCentre, isSmall
        private String icon;
        private Alignment alignment;
        private int xPos;
        private int yPos;
        private float scale;
        private boolean showBackground;
        private boolean showBars;
        private boolean useTrackerItem;
        private IconType iconType;
        private boolean showElement;

        public String getIcon() {
            return icon;
        }

        public Alignment getAlignment() {
            return alignment != null ? alignment : Alignment.TOP_LEFT;
        }

        public int getxPos() {
            return xPos;
        }

        public int getyPos() {
            return yPos;
        }

        public float getScale() {
            return scale;
        }

        public boolean isShowBackground() {
            return showBackground;
        }

        public boolean isShowBars() {
            return showBars;
        }

        public boolean isUseTrackerItem() {
            return useTrackerItem;
        }

        public IconType getIconType() {
            return iconType;
        }

        public boolean isShowElement() {
            return showElement;
        }

        public CustomHudIcon(
                String icon,
                Alignment alignment,
                int xPos,
                int yPos,
                float scale,
                boolean showBackground,
                boolean showBars,
                boolean showElement,
                boolean useTrackerItem,
                IconType iconType
        ) {
            this.icon = icon;
            this.alignment = alignment;
            this.xPos = xPos;
            this.yPos = yPos;
            this.scale = scale;
            this.showBackground = showBackground;
            this.showBars = showBars;
            this.useTrackerItem = useTrackerItem;
            this.showElement = showElement;
            this.iconType = iconType;
        }

        public CustomHudIcon() {
            this.icon = "";
            this.alignment = Alignment.TOP_LEFT;
            this.xPos = 30;
            this.yPos = 30;
            this.scale = 1.0f;
            this.showBackground = true;
            this.showBars = true;
            this.useTrackerItem = false;
            this.showElement = true;
            this.iconType = IconType.ITEM;

        }
    }
    //endregion

    //region Icon Type
    public enum IconType {
        SLOT,
        ITEM,
        PLACEHOLDER,
        TRACKER
    }
    //endregion

    //region Dev
    /// Field, Pair<Value, Tooltip>
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
                "customHudIconData", Pair.of(Component.literal("[customHudIconData]"), TextHelper.literal(getCustomHudIconData()))
        );
    }
    //endregion
}
