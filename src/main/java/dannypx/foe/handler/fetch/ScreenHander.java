package dannypx.foe.handler.fetch;

import dannypx.foe.handler.Handler;
import dannypx.foe.handler.logic.CodeExecuterHandler;
import dannypx.foe.handler.logic.EventHandler;
import dannypx.foe.handler.logic.InventoryHandler;
import dannypx.foe.handler.renderer.InventoryScreenRenderHandler;
import dannypx.foe.type.tuple.Pair;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Map;

public class ScreenHander extends Handler {
    private static ScreenHander INSTANCE = new ScreenHander();

    public static ScreenHander instance() {
        if (INSTANCE == null) {
            INSTANCE = new ScreenHander();
        }
        return INSTANCE;
    }

    //region Fields
    private Component lastScreen = Component.empty();

    public Component getLastScreen() {
        return lastScreen;
    }
    //endregion

    //region Methods
    public void onAfterInitScreen(Minecraft minecraft, Screen screen, int scaledWidth, int scaledHeight) {
        // the HUD's hover tooltips (the contest's other baits), over chat and any screen the HUD shows under
        ScreenEvents.afterRender(screen).register(dannypx.foe.helper.SafeScreen.render("ScreenHander:41", (s, g, mx, my, d) -> dannypx.foe.screens.element.hud.HudHover.render(g, mx, my)));
        // the Social HUD's names scroll under the mouse wheel while chat is open
        if (screen instanceof net.minecraft.client.gui.screens.ChatScreen chat) {
            ScreenMouseEvents.allowMouseScroll(chat).register((s, mx, my, h, v) -> {
                try {
                    return !dannypx.foe.screens.element.hud.CrewPanelElement.onScroll(mx, my, v);
                } catch (Throwable t) {
                    return true;
                }
            });
        }
        if(screen instanceof InventoryScreen inventoryScreen) {
            InventoryScreenRenderHandler.instance().init(inventoryScreen);
            ScreenMouseEvents.afterMouseScroll(inventoryScreen).register(dannypx.foe.helper.SafeScreen.scroll("ScreenHander:44", InventoryScreenRenderHandler.instance()::onMouseScrolled));
            ScreenMouseEvents.afterMouseClick(inventoryScreen).register(dannypx.foe.helper.SafeScreen.click("ScreenHander:45", InventoryScreenRenderHandler.instance()::onMouseClicked));
            ScreenEvents.remove(inventoryScreen).register(dannypx.foe.helper.SafeScreen.remove("ScreenHander:46", InventoryScreenRenderHandler.instance()::onClose));
        } else if(screen instanceof ContainerScreen genericContainerScreen) {
            GenericContainerScreenHandler.instance().init(genericContainerScreen);
            ScreenEvents.afterRender(screen).register(dannypx.foe.helper.SafeScreen.render("ScreenHander:49", (s, g, mx, my, d) -> {
                long _p = dannypx.foe.handler.logic.FoeProfiler.mark();
                GenericContainerScreenHandler.instance().render(s, g, mx, my, d);
                dannypx.foe.handler.logic.FoeProfiler.end("screen menu boxes", _p);
            }));
            // FOE+'s travel map over the Locations menu (/tt)
            var travelMap = dannypx.foe.handler.renderer.TravelMapHandler.instance();
            ScreenEvents.afterRender(screen).register(dannypx.foe.helper.SafeScreen.render("ScreenHander:56", travelMap::render));
            ScreenEvents.afterRender(screen).register(dannypx.foe.helper.SafeScreen.render("ScreenHander:57", (s, g, mx, my, d) -> {
                long _p = dannypx.foe.handler.logic.FoeProfiler.mark();
                MenuDumpHandler.instance().look(s);
                dannypx.foe.handler.logic.FoeProfiler.end("screen menu dump", _p);
            }));
            // FOE+'s presets panel over the Fishing Presets menu
            var presetsPanel = dannypx.foe.handler.renderer.PresetsPanelHandler.instance();
            ScreenEvents.afterRender(screen).register(dannypx.foe.helper.SafeScreen.render("ScreenHander:64", presetsPanel::render));
            ScreenMouseEvents.allowMouseClick(screen).register(dannypx.foe.helper.SafeScreen.allowClick("ScreenHander:65", presetsPanel::allowClick));
            ScreenMouseEvents.allowMouseRelease(screen).register(dannypx.foe.helper.SafeScreen.allowRelease("ScreenHander:66", (s, event) -> presetsPanel.allowOther(s, event.x(), event.y())));
            ScreenMouseEvents.allowMouseDrag(screen).register(dannypx.foe.helper.SafeScreen.allowDrag("ScreenHander:67", (s, event, dx, dy) -> presetsPanel.allowOther(s, event.x(), event.y())));
            ScreenMouseEvents.allowMouseClick(screen).register(dannypx.foe.helper.SafeScreen.allowClick("ScreenHander:68", travelMap::allowClick));
            ScreenMouseEvents.allowMouseRelease(screen).register(dannypx.foe.helper.SafeScreen.allowRelease("ScreenHander:69", (s, event) -> travelMap.allowOther(s)));
            ScreenMouseEvents.allowMouseDrag(screen).register(dannypx.foe.helper.SafeScreen.allowDrag("ScreenHander:70", (s, event, dx, dy) -> travelMap.allowOther(s)));
        }

        this.dispatchLastScreen(screen.getTitle());
        ScreenEvents.remove(screen).register(dannypx.foe.helper.SafeScreen.remove("ScreenHander:74", this::onRemoveScreen));
    }

    public void dispatchLastScreen(Component lastScreen) {
        CodeExecuterHandler.runLater(3, () -> {
            this.lastScreen = lastScreen;
            EventHandler.instance().onScreenOpen();
        });
    }

    private void onRemoveScreen(Screen screen) {
        InventoryHandler.instance().trackFishOffSide();
        EventHandler.instance().onScreenClose();
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
