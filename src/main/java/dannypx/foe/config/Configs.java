package dannypx.foe.config;

import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;

public class Configs {
    public static MainConfig mainConfig = ConfigApiJava.registerAndLoadConfig(MainConfig::new, RegisterType.CLIENT);

    public static HUDConfig hudConfig = ConfigApiJava.registerAndLoadConfig(HUDConfig::new, RegisterType.CLIENT);
    public static InventoryScreenConfig inventoryScreenConfig = ConfigApiJava.registerAndLoadConfig(InventoryScreenConfig::new, RegisterType.CLIENT);
    public static RendererConfig rendererConfig = ConfigApiJava.registerAndLoadConfig(RendererConfig::new, RegisterType.CLIENT);
    public static KeyBindConfig keyBindConfig = ConfigApiJava.registerAndLoadConfig(KeyBindConfig::new, RegisterType.CLIENT);
    public static MixinConfig mixinConfig = ConfigApiJava.registerAndLoadConfig(MixinConfig::new, RegisterType.CLIENT);
    public static HandlerConfig handlerConfig = ConfigApiJava.registerAndLoadConfig(HandlerConfig::new, RegisterType.CLIENT);
    public static AutotipConfig autotipConfig = ConfigApiJava.registerAndLoadConfig(AutotipConfig::new, RegisterType.CLIENT);
    public static _DebugConfig debugConfig = ConfigApiJava.registerAndLoadConfig(_DebugConfig::new, RegisterType.CLIENT);

    public static void init() {}
}
