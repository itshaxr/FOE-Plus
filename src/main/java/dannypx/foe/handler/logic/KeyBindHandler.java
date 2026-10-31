package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.Handler;
import dannypx.foe.helper.KeyBindHelper;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.type.placeholder.PlaceholderValue;
import dannypx.foe.type.placeholder.StringValue;
import dannypx.foe.screens.MainScreen;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class KeyBindHandler extends Handler {
    private static KeyBindHandler INSTANCE = new KeyBindHandler();

    public static KeyBindHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new KeyBindHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private boolean isPressingInspect = false;
    private boolean wasInspectKeyDown = false;
    private boolean wasDismissKeyDown = false;

    /// Inspect info is shown: the key is held (or toggled on), or "Inspect always on" is set.
    public boolean isPressingInspect() {
        return isPressingInspect || Configs.keyBindConfig.inspectAlwaysOn.get();
    }

    /// The O menu button: flips "Inspect always on" and saves it.
    public static void toggleInspectAlwaysOn() {
        Configs.keyBindConfig.inspectAlwaysOn.accept(!Configs.keyBindConfig.inspectAlwaysOn.get());
        Configs.keyBindConfig.save();
    }
    //endregion

    //region Methods
    public void tick() {
        if(minecraft.screen == null
                && KeyBindHelper.isPressed(Configs.keyBindConfig.openMainKeybind)
        ) {
            minecraft.setScreen(new dannypx.foe.screens.FoeMainScreen(minecraft.screen));
        }

        if(minecraft.screen == null
                && KeyBindHelper.isPressed(Configs.keyBindConfig.openAutotipKeybind)
        ) {
            minecraft.setScreen(new dannypx.foe.screens.AutotipScreen(minecraft.screen));
        }

        if(minecraft.screen == null
                && KeyBindHelper.isPressed(Configs.keyBindConfig.openPetPricesKeybind)
        ) {
            minecraft.setScreen(new dannypx.foe.screens.PetPriceScreen(minecraft.screen));
        }

        // Hide/show the warning box until you move to another location.
        boolean dismissDown = minecraft.screen == null && KeyBindHelper.isPressed(Configs.keyBindConfig.dismissWarningKeybind);
        if (dismissDown && !wasDismissKeyDown) WarningDismissHandler.instance().toggle();
        wasDismissKeyDown = dismissDown;

        switch (Configs.keyBindConfig.inspectMode.get()) {
            case HOLD -> isPressingInspect = KeyBindHelper.isPressed(Configs.keyBindConfig.inspectKeybind);
            case TOGGLE -> {
                boolean isKeyDown = KeyBindHelper.isPressed(Configs.keyBindConfig.inspectKeybind);
                if (isKeyDown && !wasInspectKeyDown) isPressingInspect = !isPressingInspect;
                wasInspectKeyDown = isKeyDown;
            }
        }
    }
    //endregion

    //region Dev
    /// Field, Pair<Value, Tooltip>
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
                "openMainKeybind", Pair.of(Component.literal(KeyBindHelper.getKeyString(Configs.keyBindConfig.openMainKeybind)), Component.empty()),
                "inspectKeybind", Pair.of(Component.literal(KeyBindHelper.getKeyString(Configs.keyBindConfig.inspectKeybind)), Component.empty())
        );
    }
    //endregion
}
