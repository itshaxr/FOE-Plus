package dannypx.foe.screens.widget;

import dannypx.foe.helper.TextHelper;
import dannypx.foe.type.Alignment;
import dannypx.foe.screens.element.Element;
import dannypx.foe.helper.GuiGraphicsHelper;
import dannypx.foe.screens.interfaces.ScreenConstants;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.jetbrains.annotations.NotNull;

public class MovableBoxWidget extends AbstractWidget implements ScreenConstants {
    private final Minecraft minecraft;
    private final Callback callback;
    private final List<Alignment> alignmentList;
    private Element element;

    private double deltaX = 0;
    private double deltaY = 0;
    private int originalX;
    private int originalY;

    public MovableBoxWidget(Minecraft minecraft,
                            Element element,
                            List<Alignment> alignmentList,
                            Callback callback) {
        super(1, 1, 1, 1, element.message);
        this.minecraft = minecraft;
        this.callback = callback;
        this.alignmentList = alignmentList;
        setup(element);
    }

    private void setup(Element element) {
        this.element = element;

        switch (element.alignment) {
            case LEFT, TOP_LEFT, BOTTOM_LEFT -> {
                this.setX(Math.round(minecraft.getWindow().getGuiScaledWidth() * element.xPos));
                this.originalX = this.getX();
            }
            case RIGHT, BOTTOM_RIGHT, TOP_RIGHT -> {
                this.setX(minecraft.getWindow().getGuiScaledWidth()
                        - Math.round(minecraft.getWindow().getGuiScaledWidth() * element.xPos));
                this.originalX = minecraft.getWindow().getGuiScaledWidth() - this.getX();
                this.setX(this.getX() - element.width);
            }
            case TOP, BOTTOM -> {
                this.setX(Math.round(minecraft.getWindow().getGuiScaledWidth() * element.xPos));
                this.originalX = this.getX();
                this.setX(this.getX() - (element.width / 2));
            }
        }

        switch (element.alignment) {
            case TOP_LEFT, TOP, TOP_RIGHT -> {
                this.setY(Math.round(minecraft.getWindow().getGuiScaledHeight() * element.yPos));
                this.originalY = this.getY();
            }
            case BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT -> {
                this.setY(minecraft.getWindow().getGuiScaledHeight()
                        - Math.round(minecraft.getWindow().getGuiScaledHeight() * element.yPos));
                this.originalY = minecraft.getWindow().getGuiScaledHeight() - this.getY();
                this.setY(this.getY() - element.height);
            }
            case LEFT, RIGHT -> {
                this.setY(Math.round(minecraft.getWindow().getGuiScaledHeight() * element.yPos));
                this.originalY = this.getY();
                this.setY(this.getY() - (element.height / 2));
            }
        }

        this.width = element.width;
        this.height = element.height;
    }

    /// The HUD this box moves.
    public Element element() {
        return element;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.renderBox(guiGraphics);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        this.renderAlignment(guiGraphics);
        this.element.render(guiGraphics);
    }

    private void renderAlignment(GuiGraphics guiGraphics) {
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        switch (element.alignment) {
            case TOP_LEFT -> {
                guiGraphics.fill(
                        getX() - 1,
                        getY() - 1,
                        getX() + 1,
                        getY() + 1,
                        CommonColors.RED
                );

                GuiGraphicsHelper.drawLine(guiGraphics,
                        getX() - 1, getY() - 1,
                        0,0,
                        CommonColors.RED
                );
            }
            case TOP_RIGHT -> {
                guiGraphics.fill(
                        getX() - 1 + getWidth(),
                        getY() - 1,
                        getX() + 1 + getWidth(),
                        getY() + 1,
                        CommonColors.RED
                );

                GuiGraphicsHelper.drawLine(guiGraphics,
                        getX() + 1 + getWidth(), getY() - 1,
                        screenWidth,0,
                        CommonColors.RED
                );
            }
            case BOTTOM_LEFT -> {
                guiGraphics.fill(
                        getX() - 1,
                        getY() - 1 + getHeight(),
                        getX() + 1,
                        getY() + 1 + getHeight(),
                        CommonColors.RED
                );

                GuiGraphicsHelper.drawLine(guiGraphics,
                        getX() - 1, getY() + 1 + getHeight(),
                        0, screenHeight,
                        CommonColors.RED
                );
            }
            case BOTTOM_RIGHT -> {
                guiGraphics.fill(
                        getX() - 1 + getWidth(),
                        getY() - 1 + getHeight(),
                        getX() + 1 + getWidth(),
                        getY() + 1 + getHeight(),
                        CommonColors.RED
                );

                GuiGraphicsHelper.drawLine(guiGraphics,
                        getX() + 1 + getWidth(), getY() + 1 + getHeight(),
                        screenWidth, screenHeight,
                        CommonColors.RED
                );
            }
            case TOP -> {
                guiGraphics.fill(
                        getX() - 1 + (getWidth() / 2),
                        getY() - 1,
                        getX() + 1 + (getWidth() / 2),
                        getY() + 1,
                        CommonColors.RED
                );

                GuiGraphicsHelper.drawLine(guiGraphics,
                        getX() + (getWidth() / 2), getY() - 1,
                        screenWidth / 2, 0,
                        CommonColors.RED
                );
            }
            case BOTTOM -> {
                guiGraphics.fill(
                        getX() - 1 + (getWidth() / 2),
                        getY() - 1 + getHeight(),
                        getX() + 1 + (getWidth() / 2),
                        getY() + 1 + getHeight(),
                        CommonColors.RED
                );

                GuiGraphicsHelper.drawLine(guiGraphics,
                        getX() + (getWidth() / 2), getY() + 1 + getHeight(),
                        screenWidth / 2, screenHeight,
                        CommonColors.RED
                );
            }
            case LEFT -> {
                guiGraphics.fill(
                        getX() - 1,
                        getY() - 1 + (getHeight() / 2),
                        getX() + 1,
                        getY() + 1 + (getHeight() / 2),
                        CommonColors.RED
                );

                GuiGraphicsHelper.drawLine(guiGraphics,
                        getX() - 1, getY() + (getHeight() / 2),
                        0, screenHeight / 2,
                        CommonColors.RED
                );
            }
            case RIGHT -> {
                guiGraphics.fill(
                        getX() - 1 + getWidth(),
                        getY() - 1 + (getHeight() / 2),
                        getX() + 1 + getWidth(),
                        getY() + 1 + (getHeight() / 2),
                        CommonColors.RED
                );

                GuiGraphicsHelper.drawLine(guiGraphics,
                        getX() + 1 + getWidth(), getY() + (getHeight() / 2),
                        screenWidth, screenHeight / 2,
                        CommonColors.RED
                );
            }
        }
    }

    private void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if(this.isHovered()) {
            List<Component> componentList = List.of(
                    Component.literal(element.message.getString()).withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD),
                    TextHelper.concat(Component.literal("X Position: ").withStyle(ChatFormatting.GRAY),
                            Component.literal(String.valueOf(Math.round(element.xPos * 100f)))),
                    TextHelper.concat(Component.literal("Y Position: ").withStyle(ChatFormatting.GRAY),
                            Component.literal(String.valueOf(Math.round(element.yPos * 100f)))),
                    TextHelper.concat(Component.literal("Alignment: ").withStyle(ChatFormatting.GRAY),
                            Component.literal(element.alignment.toString())),
                    Component.empty(),
                    Component.literal("Hold Left Click and Drag to change position").withStyle(ChatFormatting.ITALIC),
                    Component.literal("Right Click to cycle Alignment").withStyle(ChatFormatting.ITALIC),
                    Component.literal("Middle Click to open " + element.message.getString() + " config")
                            .withStyle(ChatFormatting.ITALIC)

            );

            guiGraphics.setComponentTooltipForNextFrame(minecraft.font, componentList, mouseX, mouseY);
        }
    }

    private void renderBox(GuiGraphics guiGraphics) {
        switch (element.alignment) {
            case LEFT, TOP_LEFT, BOTTOM_LEFT -> GuiGraphicsHelper.drawHorizontalGradient(guiGraphics,
                    getX() - PADDING_QUART, getY() - PADDING_QUART,
                    getX() + getWidth() + PADDING_QUART, getY() + getHeight() + PADDING_QUART,
                    this.isHovered() ? CommonColors.GRAY : CommonColors.DARK_GRAY,
                    this.isHovered() ? 0x00AAAAAA : 0x00555555);
            case RIGHT, TOP_RIGHT, BOTTOM_RIGHT -> GuiGraphicsHelper.drawHorizontalGradient(guiGraphics,
                    getX() - PADDING_QUART, getY() - PADDING_QUART,
                    getX() + getWidth() + PADDING_QUART, getY() + getHeight() + PADDING_QUART,
                    this.isHovered() ? 0x00AAAAAA : 0x00555555,
                    this.isHovered() ? CommonColors.GRAY : CommonColors.DARK_GRAY);
            case TOP -> guiGraphics.fillGradient(getX() - PADDING_QUART, getY() - PADDING_QUART,
                    getX() + getWidth() + PADDING_QUART, getY() + getHeight() + PADDING_QUART,
                    this.isHovered() ? CommonColors.GRAY : CommonColors.DARK_GRAY,
                    this.isHovered() ? 0x00AAAAAA : 0x00555555);
            case BOTTOM -> guiGraphics.fillGradient(getX() - PADDING_QUART, getY() - PADDING_QUART,
                    getX() + getWidth() + PADDING_QUART, getY() + getHeight() + PADDING_QUART,
                    this.isHovered() ? 0x00AAAAAA : 0x00555555,
                    this.isHovered() ? CommonColors.GRAY : CommonColors.DARK_GRAY);
        }
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {}

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubled) {
        if (!this.isActive()) {
            return false;
        } else {
            boolean bl2 = this.isMouseOver(mouseButtonEvent.x(), mouseButtonEvent.y());
            if (bl2) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                this.onClick(mouseButtonEvent, doubled);
                return true;
            }

            return false;
        }
    }

    @Override
    public void onClick(@NotNull MouseButtonEvent click, boolean doubled) {
        if (this.active && this.visible && this.isMouseOver(click.x(), click.y())) {
            if (click.button() == 0) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
            } else if (click.button() == 1) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());

                element.alignment = this.nextAlignment();
                this.setup(element);

                callback.onRelease(Math.round(element.xPos * 100f),
                        Math.round(element.yPos * 100f), element.alignment);
            } else if (click.button() == 2) {
                callback.onConfig();
            }
        }
    }

    @Override
    protected void onDrag(@NotNull MouseButtonEvent mouseButtonEvent, double deltaX, double deltaY) {
        super.onDrag(mouseButtonEvent, deltaX, deltaY);
        int currentWidth = minecraft.getWindow().getGuiScaledWidth();
        int currentHeight = minecraft.getWindow().getGuiScaledHeight();

        switch (element.alignment) {
            case LEFT, TOP, BOTTOM, TOP_LEFT, BOTTOM_LEFT-> this.deltaX += deltaX;
            case RIGHT, BOTTOM_RIGHT, TOP_RIGHT-> this.deltaX -= deltaX;
        }
        switch (element.alignment) {
            case TOP_LEFT, TOP, TOP_RIGHT, LEFT, RIGHT -> this.deltaY += deltaY;
            case BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT -> this.deltaY -= deltaY;
        }

        int calculatedPercentX = Math.clamp(Math.round((float) (originalX + this.deltaX) / (float) currentWidth * 100F), 0, 100);
        int calculatedPercentY = Math.clamp(Math.round((float) (originalY + this.deltaY) / (float) currentHeight * 100F), 0, 100);

        element.setXPercent((float) calculatedPercentX / 100F);
        element.setYPercent((float) calculatedPercentY / 100F);

        switch (element.alignment) {
            case LEFT, TOP_LEFT, BOTTOM_LEFT -> this.setX(Math.round(minecraft.getWindow().getGuiScaledWidth() * element.xPos));
            case RIGHT, BOTTOM_RIGHT, TOP_RIGHT -> this.setX(minecraft.getWindow().getGuiScaledWidth()
                    - Math.round(minecraft.getWindow().getGuiScaledWidth() * element.xPos)
                    - element.width);
            case TOP, BOTTOM -> this.setX(Math.round(minecraft.getWindow().getGuiScaledWidth() * element.xPos) - (element.width / 2));
        }

        switch (element.alignment) {
            case TOP_LEFT, TOP, TOP_RIGHT -> this.setY(Math.round(minecraft.getWindow().getGuiScaledHeight() * element.yPos));
            case BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT -> this.setY(minecraft.getWindow().getGuiScaledHeight()
                    - Math.round(minecraft.getWindow().getGuiScaledHeight() * element.yPos)
                    - element.height);
            case LEFT, RIGHT -> this.setY(Math.round(minecraft.getWindow().getGuiScaledHeight() * element.yPos) - (element.height / 2));
        }
    }

    @Override
    public void onRelease(@NotNull MouseButtonEvent mouseButtonEvent) {
        super.onRelease(mouseButtonEvent);

        switch (element.alignment) {
            case LEFT, TOP_LEFT, BOTTOM_LEFT -> this.originalX = getX();
            case RIGHT, BOTTOM_RIGHT, TOP_RIGHT -> this.originalX = minecraft.getWindow().getGuiScaledWidth() - (getX() + element.width);
            case TOP, BOTTOM -> this.originalX = getX() + (element.width / 2);
        }

        switch (element.alignment) {
            case TOP_LEFT, TOP, TOP_RIGHT -> this.originalY = getY();
            case BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT -> this.originalY = minecraft.getWindow().getGuiScaledHeight() - (getY() + element.height);
            case LEFT, RIGHT -> this.originalY = getY() + (element.height / 2);
        }

        this.deltaX = 0;
        this.deltaY = 0;

        this.callback.onRelease(Math.round(element.xPos * 100f),
                Math.round(element.yPos * 100f),
                element.alignment);
    }

    public interface Callback {
        void onRelease(int xPercent, int yPercent, Alignment alignment);
        void onConfig();
    }

    private Alignment nextAlignment() {
        int size = alignmentList.size();
        int index = alignmentList.indexOf(element.alignment);
        if(index + 1 != size) {
            return alignmentList.get(index + 1);
        } else {
            return alignmentList.getFirst();
        }
    }
}
