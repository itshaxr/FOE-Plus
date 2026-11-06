package dannypx.foe.handler.renderer;

import dannypx.foe.handler.ScreenHandler;
import dannypx.foe.handler.logic.LoadingHandler;
import dannypx.foe.handler.logic.SearchHandler;
import dannypx.foe.search.editbox.SearchBarEditBox;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.config.Configs;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChestScreenRenderHandler extends ScreenHandler {
    private static ChestScreenRenderHandler INSTANCE = new ChestScreenRenderHandler();

    public static ChestScreenRenderHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new ChestScreenRenderHandler();
        }
        return INSTANCE;
    }

    //region Fields
    SearchBarEditBox searchBarWidget;
    //endregion

    //region Methods
    public void init(Screen screen) {
        SearchHandler.instance().setFocused(false);
        SearchHandler.instance().setOnScreen(true);
        this.initWidgets(screen);
    }

    private void initWidgets(Screen screen) {
        if(LoadingHandler.instance().isLoadingDone()
                && Configs.mainConfig.enableMod.get()
        ) {
            List<AbstractWidget> widgets = new ArrayList<>();

            searchBarWidget = SearchHandler.getSearchBar(
                    minecraft.getWindow().getGuiScaledWidth() / 2 - 80,
                    minecraft.getWindow().getGuiScaledHeight() / 2 - 133,
                    160,
                    20
            );

            widgets.add(searchBarWidget);
            // The Bait Sorter and Pet Sorter boxes, as on the Personal Vault (FOE+).
            if (screen instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen chest) {
                SorterBoxes.add(widgets, chest.getMenu().getRowCount(), "chest");
            }

            widgets.forEach(Screens.getButtons(screen)::add);
        }
    }

    @Override
    public void render(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
        super.render(screen, guiGraphics, mouseX, mouseY, tickDelta);

        if(searchBarWidget != null) searchBarWidget.renderHelp(guiGraphics, mouseX, mouseY, tickDelta);
    }

    public boolean checkMouseClick(Screen screen, MouseButtonEvent context, boolean consumed) {
        searchBarWidget.setFocused(searchBarWidget.isMouseOver(context.x(), context.y()));
        SearchHandler.instance().setFocused(searchBarWidget.isMouseOver(context.x(), context.y()));

        return consumed;
    }

    public void onClose(Screen screen) {
        screen.setFocused(null);
        Screens.getButtons(screen).remove(searchBarWidget);
        SearchHandler.instance().setFocused(false);
        SearchHandler.instance().setOnScreen(false);
        searchBarWidget = null;
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
