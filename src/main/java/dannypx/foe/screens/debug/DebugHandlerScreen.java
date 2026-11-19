package dannypx.foe.screens.debug;

import dannypx.foe.handler.debug._DebugHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.screens.DefaultModScreen;
import dannypx.foe.screens.widget.ButtonListWidget;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.CommonColors;

public class DebugHandlerScreen extends DefaultModScreen {
    //region Fields
    private final Minecraft minecraftClient = Minecraft.getInstance();

    private ButtonListWidget handlerList;
    private String selectedHandler;
    private String hoveredName = "";
    private Pair<MutableComponent, MutableComponent> hoveredValue = Pair.of(Component.empty(), Component.empty());

    private final List<String> handlerNames;
    private Map<String, Map<String, Pair<MutableComponent, MutableComponent>>> handlerFields;
    //endregion

    //region Methods
    public DebugHandlerScreen(Screen parent) {
        super(parent, Component.literal("Debug Handler Screen"));
        handlerNames = _DebugHandler.instance()._getHandlerNames();
    }

    @Override
    protected void init() {
        super.init();
        this.renderWidgets();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);
        this.updateFields();
        this.handlerList.render(guiGraphics, mouseX, mouseY, delta);
        this.renderHandlerFields(guiGraphics, mouseX, mouseY, delta);
    }

    private void updateFields() {
        handlerFields = _DebugHandler.instance()._getFields();
    }

    private void renderWidgets() {
        List<AbstractWidget> widgets = new ArrayList<>();

        //Scrollable Handler List Widget
        widgets.add(getHandlerList());

        widgets.forEach(this::addRenderableWidget);
    }

    private AbstractWidget getHandlerList() {
        handlerList = new ButtonListWidget(
                minecraft,
                (BUTTON_WIDTH + PADDING * 2),
                height,
                0,
                BUTTON_HEIGHT + PADDING_HALF,
                BUTTON_HEIGHT,
                "Handlers"
        );

        // Add buttons
        handlerNames.forEach(handler -> handlerList.addEntry(new ButtonListWidget.ButtonEntry(
                Button.builder(
                        Component.literal(handler.replace("dannypx.foe.handler.", "")),
                        button -> selectedHandler = handler
                ).width(BUTTON_WIDTH).build()
        )));

        return handlerList;
    }

    private void renderHandlerFields(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        if(selectedHandler != null) {
            AtomicInteger atomicInteger = new AtomicInteger(0);
            handlerFields.get(selectedHandler).forEach((name, value) -> {
                Component component = TextHelper.concat(
                        Component.literal(name).withStyle(ChatFormatting.BOLD),
                        Component.literal(": "),
                        value.value1()
                );

                int componentx = (BUTTON_WIDTH + PADDING * 2) + PADDING;
                int componenty = PADDING + (font.lineHeight + LINE_SPACING) * atomicInteger.getAndIncrement();
                int componentWidth = font.width(component);
                int componentHeight = font.lineHeight;
                int color = CommonColors.WHITE;

                guiGraphics.drawString(font, component, componentx, componenty, color, true);

                if(mouseX >= componentx && mouseX <= componentx + componentWidth
                        && mouseY >= componenty && mouseY <= componenty + componentHeight) {
                    if(!Objects.equals(value.value2(), Component.empty())) {
                        guiGraphics.setTooltipForNextFrame(value.value2(), mouseX, mouseY);
                    }

                    hoveredName = name;
                    hoveredValue = value;
                }
            });
        }
    }

    @Override
    public boolean keyPressed(KeyEvent input) {

        this.copyField(input);

        return super.keyPressed(input);
    }

    public void copyField(KeyEvent input) {
        boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0
                || (input.modifiers() & GLFW.GLFW_MOD_SUPER) != 0;

        if(ctrl && input.key() == GLFW.GLFW_KEY_C) {
            String json;
            if(Objects.equals(hoveredValue.value2(), Component.empty())) {
                json = TextHelper.componentToJsonPretty(hoveredValue.value1());
            } else {
                json = hoveredValue.value2().getString();
            }

            minecraftClient.keyboardHandler.setClipboard(json);

            SystemToast.add(minecraftClient.getToastManager(),
                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal("FOE+"),
                    Component.literal("Copied " + hoveredName + " JSON"));
        }
    }
    //endregion
}
