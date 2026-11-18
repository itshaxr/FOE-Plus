package dannypx.foe.handler.logic;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.Handler;
import dannypx.foe.handler.store.CustomChatNotificationDataHandler;
import dannypx.foe.handler.store.CustomHudDataHandler;
import dannypx.foe.handler.store.CustomTrackerDataHandler;
import dannypx.foe.handler.store.ProfileDataHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.type.version.Version;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class UpdateHandler extends Handler {
    private static UpdateHandler INSTANCE = new UpdateHandler();

    public static UpdateHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new UpdateHandler();
        }
        return INSTANCE;
    }

    //region Fields
    public static final Version v038 = Version.of("0.3.8");
    public static final Version v039 = Version.of("0.3.9");
    /// FOE+'s first version: anything older in your profile was FOE-R.
    public static final Version v100 = Version.of("1.0.0");
    //endregion

    //region Methods
    public static void checkUpdate() {
        Version before = Version.of(ProfileDataHandler.instance().getProfileData().modVersion);
        Version now = Version.of(FishOnMCExtras.VERSION);

        if(before.get() == null || now.compareTo(before) > 0) {
            // Put all update cycles here
            if(before.get() == null  || (v038.compareTo(before) > 0)) updateToV038();
            if(before.get() == null  || (v039.compareTo(before) > 0)) updateToV039();
            if(before.get() == null  || (v100.compareTo(before) > 0)) leaveFoeRHuds();
            // The first time anyone joins with FOE+ on this install: a tidy HUD (players who've used FOE+ keep theirs).
            var window = net.minecraft.client.Minecraft.getInstance().getWindow();
            dannypx.foe.helper.FirstLayout.onJoin(before.get() == null || v100.compareTo(before) > 0,
                    window.getGuiScaledWidth(), window.getGuiScaledHeight());

            ProfileDataHandler.instance().updateVersion();
        }
    }

    public static void updateToV038() {
        LoggerHandler.info("Update to 0.3.8 defaults");
        LoggerHandler.info("- CustomTrackerDataHandler");
        LoggerHandler.info("- CustomHudDataHandler");
        LoggerHandler.info("- CustomChatNotificationDataHandler");

        CustomTrackerDataHandler.instance().fixDefault();
        CustomHudDataHandler.instance().fixDefault();
        CustomChatNotificationDataHandler.instance().fixDefault();
    }

    /// Coming from FOE-R: its custom HUDs (and the edits made to the example ones) stay behind. FOE+ starts
    /// with only its own HUDs (the examples, hidden) and whatever you make from now on. What FOE-R had is kept
    /// in a copy next to the data files (custom_hud.foe-r.json, custom_hud_icon.foe-r.json), not deleted.
    static void leaveFoeRHuds() {
        LoggerHandler.info("First FOE+ start after FOE-R: leaving FOE-R's custom HUDs behind (copies kept)");
        dannypx.foe.handler.io.DataFileHandler io = dannypx.foe.handler.io.DataFileHandler.instance();
        if (!CustomHudDataHandler.instance().onlyExamples())
            io.saveCopy(dannypx.foe.handler.io.DataModels.DataModelType.CUSTOM_HUD_DATA, "foe-r");
        if (!dannypx.foe.handler.store.CustomHudIconDataHandler.instance().onlyExamples())
            io.saveCopy(dannypx.foe.handler.io.DataModels.DataModelType.CUSTOM_HUD_ICON_DATA, "foe-r");
        CustomHudDataHandler.instance().resetHuds();
        dannypx.foe.handler.store.CustomHudIconDataHandler.instance().resetHudIcons();
        CustomHudDataHandler.instance().needsRenderUpdate = true;
    }

    private static void updateToV039() {
        LoggerHandler.info("Update to 0.3.9 defaults");
        LoggerHandler.info("- CustomChatNotificationDataHandler");

        CustomChatNotificationDataHandler.instance().fixDefault();
    }
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
