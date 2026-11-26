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
        // the middle line it snapped to
        if (snappedX) {
            int mx = minecraft.getWindow().getGuiScaledWidth() / 2;
            guiGraphics.fill(mx, 0, mx + 1, minecraft.getWindow().getGuiScaledHeight(), 0x9955FF55);
        }
        if (snappedY) {
            int my = minecraft.getWindow().getGuiScaledHeight() / 2;
            guiGraphics.fill(0, my, minecraft.getWindow().getGuiScaledWidth(), my + 1, 0x9955FF55);
        }
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
                    TextHelper.concat(Component.literal("Grows: ").withStyle(ChatFormatting.GRAY),
                            Component.literal(growsUp(element.alignment) ? "Up (new lines push it up)" : "Down (new lines go below)")),
                    Component.empty(),
                    Component.literal("Hold Left Click and Drag to change position").withStyle(ChatFormatting.ITALIC),
                    Component.literal(flipVertical(element.alignment) != null && alignmentList.contains(flipVertical(element.alignment))
                            ? "Right Click to make it grow " + (growsUp(element.alignment) ? "down" : "up")
                            : "Right Click to cycle Alignment").withStyle(ChatFormatting.ITALIC),
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

                Alignment flipped = flipVertical(element.alignment);
                if (flipped != null && alignmentList.contains(flipped)) {
                    // grows the other way (down from its top, or up from its bottom), staying just where it is
                    int screenH = minecraft.getWindow().getGuiScaledHeight();
                    int top = getY();
                    element.alignment = flipped;
                    element.yPos = growsUp(flipped) ? (screenH - (top + element.height)) / (float) screenH : top / (float) screenH;
                } else {
                    element.alignment = this.nextAlignment();
                }
                this.setup(element);

                callback.onReleaseExact(element.xPos, element.yPos, element.alignment);
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

        // to the pixel (whole percents made it jump, and nothing could sit exactly in the middle)
        float fx = Math.clamp((float) ((originalX + this.deltaX) / currentWidth), 0f, 1f);
        float fy = Math.clamp((float) ((originalY + this.deltaY) / currentHeight), 0f, 1f);
        // near the middle of the screen it snaps there: the box's own middle on the screen's
        snappedX = false;
        snappedY = false;
        float midX = switch (element.alignment) {
            case TOP, BOTTOM -> 0.5f;
            default -> (currentWidth / 2f - element.width / 2f) / currentWidth;
        };
        float midY = switch (element.alignment) {
            case LEFT, RIGHT -> 0.5f;
            default -> (currentHeight / 2f - element.height / 2f) / currentHeight;
        };
        if (Math.abs(fx - midX) * currentWidth <= SNAP_PX) { fx = midX; snappedX = true; }
        if (Math.abs(fy - midY) * currentHeight <= SNAP_PX) { fy = midY; snappedY = true; }

        element.setXPercent(fx);
        element.setYPercent(fy);

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
        this.snappedX = false;
        this.snappedY = false;

        this.callback.onReleaseExact(element.xPos, element.yPos, element.alignment);
    }

    /// The box sits on the screen's middle line across or down (snapped there while dragging): a guide line shows.
    public boolean snappedX = false, snappedY = false;
    /// How close (GUI pixels) to the middle a box snaps onto it.
    static final int SNAP_PX = 3;

    public interface Callback {
        void onRelease(int xPercent, int yPercent, Alignment alignment);
        void onConfig();

        /// The exact place (0-1 of the screen); by default its whole percents go to {@link #onRelease}.
        default void onReleaseExact(float x, float y, Alignment alignment) {
            onRelease(Math.round(x * 100f), Math.round(y * 100f), alignment);
        }
    }

    /// Anchored at its bottom: it grows upward.
    static boolean growsUp(Alignment a) {
        return a == Alignment.BOTTOM_LEFT || a == Alignment.BOTTOM || a == Alignment.BOTTOM_RIGHT;
    }

    /// The same side, anchored at the other edge (top ↔ bottom), or null.
    static Alignment flipVertical(Alignment a) {
        return switch (a) {
            case TOP_LEFT -> Alignment.BOTTOM_LEFT;
            case BOTTOM_LEFT -> Alignment.TOP_LEFT;
            case TOP_RIGHT -> Alignment.BOTTOM_RIGHT;
            case BOTTOM_RIGHT -> Alignment.TOP_RIGHT;
            case TOP -> Alignment.BOTTOM;
            case BOTTOM -> Alignment.TOP;
            default -> null;
        };
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
