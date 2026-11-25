package dannypx.foe.screens;

import dannypx.foe.config.Configs;
import dannypx.foe.screens.debug.DebugHandlerScreen;
import dannypx.foe.screens.interfaces.ScreenConstants;
import java.util.ArrayList;
import java.util.List;

import dannypx.foe.screens.widget.SmallButtonBuilder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public class DefaultModScreen extends Screen implements ScreenConstants {
    final Screen parentScreen;
    final boolean isMiddle;

    protected DefaultModScreen(Screen parent, Component title) {
        super(title);
        this.parentScreen = parent;
        this.isMiddle = false;
    }

    protected DefaultModScreen(Screen parent, Component title, boolean isMiddle) {
        super(title);
        this.parentScreen = parent;
        this.isMiddle = isMiddle;
    }

    @Override
    protected void init() {
        super.init();
        this.renderDefaultWidgets();
    }

    private void renderDefaultWidgets() {
        List<AbstractWidget> widgets = new ArrayList<>();

        widgets.add(this.backButton());

        if(!isMiddle && Configs.debugConfig.debugMode.get()) widgets.add(this.debugButton());

        widgets.forEach(this::addRenderableWidget);
    }

    /// The Return button in the bottom left corner instead of the bottom right (the HUD mover).
    protected boolean returnOnLeft() {
        return false;
    }

    private AbstractWidget backButton() {
        // the HUD mover's Return sits in the bottom left, wherever the screen would otherwise put it
        return !isMiddle || returnOnLeft() ? new SmallButtonBuilder(
                returnOnLeft() ? PADDING_HALF : width - PADDING_HALF - 50,
                height - PADDING_HALF - BUTTON_HEIGHT,
                50, BUTTON_HEIGHT,
                button -> this.onClose())
                .icon("Return")
                .color(0xFFCC0000)
                .build()
        : new SmallButtonBuilder(
                width / 2 - 50 / 2,
                height / 2 - BUTTON_HEIGHT / 2,
                50, BUTTON_HEIGHT,
                button -> this.onClose())
                .icon("Return")
                .color(0xFFCC0000)
                .build();
    }

    private AbstractWidget debugButton() {
        return new SmallButtonBuilder(
                width - PADDING_HALF - 50 - PADDING_HALF - 50,
                height - PADDING_HALF - BUTTON_HEIGHT,
                50, BUTTON_HEIGHT,
                button -> this.minecraft.setScreen(new DebugHandlerScreen(this.minecraft.screen)))
                .icon("Debug")
                .color(0xFF800000)
                .build();
    }

    @Override
    public void render(@NonNull GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parentScreen);
    }
}
