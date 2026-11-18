package dannypx.foe.screens.element.hud;

import dannypx.foe.handler.debug._DebugHandler;
import dannypx.foe.handler.fetch.ScoreboardHandler;
import dannypx.foe.handler.logic.LoadingHandler;
import dannypx.foe.helper.GuiGraphicsHelper;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.type.StringStyle;
import dannypx.foe.type.tuple.Quartet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import dannypx.foe.config.Configs;
import dannypx.foe.screens.element.Element;

public class _DebugField extends Element {
    //region Fields
    private static final int WIDTH = 100;
    private static final int HEIGHT = 16;
    //endregion

    public _DebugField() {
        super(WIDTH,
                HEIGHT,
                Configs.debugConfig.debugFieldXPosition.get() / 100f,
                Configs.debugConfig.debugFieldYPosition.get() / 100f,
                Configs.debugConfig.debugFieldAlignment.get(),
                Configs.debugConfig.debugFieldGroup.translation("Debug Field"),
                false);
    }

    public _DebugField(boolean isCopy) {
        super(WIDTH,
                HEIGHT,
                Configs.debugConfig.debugFieldXPosition.get() / 100f,
                Configs.debugConfig.debugFieldYPosition.get() / 100f,
                Configs.debugConfig.debugFieldAlignment.get(),
                Configs.debugConfig.debugFieldGroup.translation("Debug Field"),
                isCopy);
    }

    //region Methods
    @Override
    public void render(GuiGraphics guiGraphics) {
        int scaledWidth = (int) (Minecraft.getInstance().getWindow().getGuiScaledWidth() * (1 / dannypx.foe.utils.HudScale.crisp(Configs.debugConfig.debugFieldElementScale.get())));
        int scaledHeight = (int) (Minecraft.getInstance().getWindow().getGuiScaledHeight() * (1 / dannypx.foe.utils.HudScale.crisp(Configs.debugConfig.debugFieldElementScale.get())));

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(dannypx.foe.utils.HudScale.crisp(Configs.debugConfig.debugFieldElementScale.get()), dannypx.foe.utils.HudScale.crisp(Configs.debugConfig.debugFieldElementScale.get()));
        if(LoadingHandler.instance().isLoadingDone()
                && Configs.debugConfig.debugFieldElement.get()
                && Configs.debugConfig.debugMode.get()
                && !ScoreboardHandler.instance().isNoScoreboard()
        ) {
            // Position
            if(!isCopy) {
                xPos = Configs.debugConfig.debugFieldXPosition.get() / 100f;
                yPos = Configs.debugConfig.debugFieldYPosition.get() / 100f;
            }

            int x = switch (Configs.debugConfig.debugFieldAlignment.get()) {
                case TOP_LEFT, BOTTOM_LEFT -> Math.round(scaledWidth * xPos);
                case TOP_RIGHT, BOTTOM_RIGHT -> scaledWidth
                        - Math.round(scaledWidth * xPos);
                default -> 0;
            };

            int y = switch (Configs.debugConfig.debugFieldAlignment.get()) {
                case TOP_LEFT, TOP_RIGHT -> Math.round(scaledHeight * yPos);
                case BOTTOM_LEFT, BOTTOM_RIGHT -> scaledHeight
                        - Math.round(scaledHeight * yPos);
                default -> 0;
            };

            this.renderFieldComponent(guiGraphics, Minecraft.getInstance().font, x, y);
        }
        guiGraphics.pose().popMatrix();
    }

    private void renderFieldComponent(GuiGraphics guiGraphics, Font font, int x, int y) {
        Component fieldComponent;

        Quartet<String, String, MutableComponent, MutableComponent> fieldParts = _DebugHandler.instance()._getField(
                Configs.debugConfig.debugFieldHandlerChoice.get(),
                Configs.debugConfig.debugFieldFieldChoice.get()
        );

        if(fieldParts == null) {
            fieldComponent = Component.empty().append(Component.literal(TextHelper.smallCaps("Field and Handler combination does not exist")).withStyle(ChatFormatting.RED));
        } else {
            fieldComponent = TextHelper.concat(
                    Component.literal(TextHelper.smallCaps("DEBUG ")).withStyle(ChatFormatting.RED),
                    Component.literal(fieldParts.value2()).withStyle(ChatFormatting.GRAY),
                    Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY),
                    fieldParts.value3()
            );
        }

        int width = font.width(fieldComponent);
        int height = font.lineHeight;

        switch (Configs.debugConfig.debugFieldAlignment.get()) {
            case TOP_LEFT -> GuiGraphicsHelper.drawString(guiGraphics, font, fieldComponent, x, y, StringStyle.SHADOW, StringStyle.MIDDLE);
            case TOP_RIGHT -> GuiGraphicsHelper.drawString(guiGraphics, font, fieldComponent, x - width, y, StringStyle.SHADOW, StringStyle.MIDDLE);
            case BOTTOM_LEFT -> GuiGraphicsHelper.drawString(guiGraphics, font, fieldComponent, x, y - height, StringStyle.SHADOW, StringStyle.MIDDLE);
            case BOTTOM_RIGHT -> GuiGraphicsHelper.drawString(guiGraphics, font, fieldComponent, x - width, y - height, StringStyle.SHADOW, StringStyle.MIDDLE);
        }
    }
    //endregion
}
