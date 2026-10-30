package dannypx.foe.screens.element.hud;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.fetch.TabOverlayHandler;
import dannypx.foe.handler.logic.LoadingHandler;
import dannypx.foe.handler.store.CustomHudDataHandler;
import dannypx.foe.helper.GuiGraphicsHelper;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.placeholder.evaluator.PlaceholderResult;
import dannypx.foe.handler.logic.PlaceholderHandler;
import dannypx.foe.type.Alignment;
import dannypx.foe.type.StringStyle;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.type.tuple.Triplet;
import dannypx.foe.screens.element.Element;
import dannypx.foe.screens.interfaces.ScreenConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class CustomHudElement extends Element implements ScreenConstants {
    //region Fields
    // isCentre, isSmall, Line
    private List<Triplet<Boolean, Boolean, Component>> componentLines = new ArrayList<>();
    private Pair<Integer, Integer> contentDimensions = Pair.of(0, 0);

    private int boxWidth = 0;
    private int boxHeight = 0;

    private CustomHudDataHandler.CustomHud customHud;

    private static final Identifier BOX_TEXTURE = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "textures/gui/sprites/elements/sidebar_atlas.png");
    private static final int TEXTURE_WIDTH = 17;
    private static final int TEXTURE_HEIGHT = 11;
    private static final int BOX_PADDING = 5;
    private static final int MIN_WIDTH = 75;
    private static final int LINE_HEIGHT = Minecraft.getInstance().font.lineHeight + 1;
    //endregion

    public CustomHudElement(CustomHudDataHandler.CustomHud customHud, Component message) {
        super(75,
                50,
                customHud.getxPos() / 100f,
                customHud.getyPos() / 100f,
                customHud.getAlignment(),
                message,
                false);
        this.customHud = customHud;
    }

    //region Methods
    @Override
    public void render(GuiGraphics guiGraphics) {
        if(!customHud.isShowElement()) { return; }

        int scaledWidth = (int) (Minecraft.getInstance().getWindow().getGuiScaledWidth() * (1 / customHud.getScale()));
        int scaledHeight = (int) (Minecraft.getInstance().getWindow().getGuiScaledHeight() * (1 / customHud.getScale()));

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(customHud.getScale(), customHud.getScale());
        if(LoadingHandler.instance().isLoadingDone()
                && TabOverlayHandler.instance().isInInstance()
        ) {
            contentDimensions = this.assembleHud();
            boxWidth = contentDimensions.value1() + BOX_PADDING * 2 + PADDING * 2;
            boxHeight = contentDimensions.value2() + BOX_PADDING * 2 + PADDING_QUART * 2;

            int x = switch (customHud.getAlignment()) {
                case TOP_LEFT, BOTTOM_LEFT, LEFT -> Math.round(scaledWidth * xPos);
                case TOP, BOTTOM -> Math.round(scaledWidth * xPos) - boxWidth / 2;
                case TOP_RIGHT, BOTTOM_RIGHT, RIGHT -> scaledWidth
                        - Math.round(scaledWidth * xPos);
                default -> 0;
            };

            int y = switch (customHud.getAlignment()) {
                case TOP_LEFT, TOP_RIGHT, TOP -> Math.round(scaledHeight * yPos);
                case LEFT, RIGHT -> Math.round(scaledHeight * yPos) - boxHeight / 2;
                case BOTTOM_LEFT, BOTTOM_RIGHT, BOTTOM -> scaledHeight
                        - Math.round(scaledHeight * yPos);
                default -> 0;
            };

            if(!componentLines.isEmpty()) {
                x = switch (customHud.getAlignment()) {
                    case TOP_LEFT, BOTTOM_LEFT, LEFT -> x;
                    case TOP, BOTTOM -> x - boxWidth / 2;
                    case TOP_RIGHT, BOTTOM_RIGHT, RIGHT -> x - boxWidth;
                    default -> 0;
                };

                y = switch (customHud.getAlignment()) {
                    case TOP_LEFT, TOP_RIGHT, TOP -> y;
                    case BOTTOM_LEFT, BOTTOM_RIGHT, BOTTOM -> y - boxHeight;
                    case LEFT, RIGHT -> y - boxHeight / 2;
                    default -> 0;
                };

                // Same layout rules as the FOE panels: on screen, never over another HUD box.
                float scale = customHud.getScale();
                dannypx.foe.helper.HudLayout.Rect wanted = new dannypx.foe.helper.HudLayout.Rect(
                        Math.round(x * scale), Math.round(y * scale), Math.round(boxWidth * scale), Math.round(boxHeight * scale));
                dannypx.foe.type.Alignment alignment = customHud.getAlignment();
                boolean fromBottom = alignment == dannypx.foe.type.Alignment.BOTTOM_LEFT || alignment == dannypx.foe.type.Alignment.BOTTOM_RIGHT || alignment == dannypx.foe.type.Alignment.BOTTOM;
                boolean fromRight = alignment == dannypx.foe.type.Alignment.TOP_RIGHT || alignment == dannypx.foe.type.Alignment.BOTTOM_RIGHT || alignment == dannypx.foe.type.Alignment.RIGHT;
                dannypx.foe.helper.HudLayout.Rect placed = isCopy
                        ? dannypx.foe.helper.HudLayout.clamp(wanted, Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight())
                        : dannypx.foe.helper.HudLayout.FRAME.place(wanted, fromBottom, fromRight);
                x = Math.round(placed.x() / scale);
                y = Math.round(placed.y() / scale);

                this.renderBox(guiGraphics, x, y);
                this.renderComponent(guiGraphics, x, y);
            }
        }
        guiGraphics.pose().popMatrix();
    }

    private void renderComponent(GuiGraphics guiGraphics, int x, int y) {
        int componentX;
        int componentY;

        if(customHud.getAlignment() == Alignment.TOP || customHud.getAlignment() == Alignment.BOTTOM) {
            componentX = x + PADDING + BOX_PADDING + boxWidth / 2;
        } else {
            componentX = x + PADDING + BOX_PADDING;
        }

        if(customHud.getAlignment() == Alignment.LEFT || customHud.getAlignment() == Alignment.RIGHT) {
            componentY = y + PADDING_QUART + BOX_PADDING + boxHeight / 2;
        } else {
            componentY = y + PADDING_QUART + BOX_PADDING;
        }

        AtomicInteger line = new AtomicInteger(0);
        componentLines.forEach(componentParts -> {
            if(componentParts.value1()) {
                if(componentParts.value2()) {
                    GuiGraphicsHelper.drawString(guiGraphics, Minecraft.getInstance().font, componentParts.value3(),
                            componentX - (PADDING + BOX_PADDING) + boxWidth / 2 - TextHelper.getWidth(Minecraft.getInstance().font, componentParts.value3(), componentParts.value2()) / 2,
                            componentY + line.getAndIncrement() * LINE_HEIGHT,
                            StringStyle.SHADOW, StringStyle.MIDDLE, StringStyle.HAS_CUSTOM_FONT, StringStyle.SMALL_CAPS
                    );
                } else {
                    GuiGraphicsHelper.drawString(guiGraphics, Minecraft.getInstance().font, componentParts.value3(),
                            componentX - (PADDING + BOX_PADDING) + boxWidth / 2 - TextHelper.getWidth(Minecraft.getInstance().font, componentParts.value3(), componentParts.value2()) / 2,
                            componentY + line.getAndIncrement() * LINE_HEIGHT,
                            StringStyle.SHADOW, StringStyle.HAS_CUSTOM_FONT
                    );
                }
            } else {
                if(componentParts.value2()) {
                    GuiGraphicsHelper.drawString(guiGraphics, Minecraft.getInstance().font, componentParts.value3(),
                            componentX,
                            componentY + line.getAndIncrement() * LINE_HEIGHT,
                            StringStyle.SHADOW, StringStyle.MIDDLE, StringStyle.HAS_CUSTOM_FONT, StringStyle.SMALL_CAPS
                    );
                } else {
                    GuiGraphicsHelper.drawString(guiGraphics, Minecraft.getInstance().font, componentParts.value3(),
                            componentX,
                            componentY + line.getAndIncrement() * LINE_HEIGHT,
                            StringStyle.SHADOW, StringStyle.HAS_CUSTOM_FONT
                    );
                }
            }
        });
    }

    private void renderBox(GuiGraphics guiGraphics, int x, int y) {
        int boxX = x;
        int boxY = y;

        if(customHud.getAlignment() == Alignment.TOP || customHud.getAlignment() == Alignment.BOTTOM) {
            boxX = boxX + boxWidth / 2;
        }

        if(customHud.getAlignment() == Alignment.LEFT || customHud.getAlignment() == Alignment.RIGHT) {
            boxY = boxY + boxHeight / 2;
        }

        int ATLAS_CORNER = 8;
        int ATLAS_BAR_WIDTH = 1;
        int ATLAS_BAR_HEIGHT = 5;
        int NIB_HEIGHT = 3;

        // Alpha Box
        if(customHud.isShowBackground()) guiGraphics.fill(
                boxX + BOX_PADDING, boxY + 1,
                boxX + this.boxWidth - BOX_PADDING, boxY + this.boxHeight - 3,
                0x7f000000
        );

        // Top Left
        if(customHud.isShowBars()) guiGraphics.blit(RenderPipelines.GUI_TEXTURED,
                BOX_TEXTURE,
                boxX, boxY,
                0, NIB_HEIGHT,
                ATLAS_CORNER, ATLAS_CORNER,
                ATLAS_CORNER, ATLAS_CORNER,
                TEXTURE_WIDTH, TEXTURE_HEIGHT
        );

        // Top
        if(customHud.isShowBars())guiGraphics.blit(RenderPipelines.GUI_TEXTURED,
                BOX_TEXTURE,
                boxX + ATLAS_CORNER, boxY,
                ATLAS_CORNER, NIB_HEIGHT,
                this.boxWidth - ATLAS_CORNER * 2, ATLAS_BAR_HEIGHT,
                ATLAS_BAR_WIDTH, ATLAS_BAR_HEIGHT,
                TEXTURE_WIDTH, TEXTURE_HEIGHT
        );

        // Top Right
        if(customHud.isShowBars())guiGraphics.blit(RenderPipelines.GUI_TEXTURED,
                BOX_TEXTURE,
                boxX + this.boxWidth - ATLAS_CORNER, boxY,
                ATLAS_CORNER + ATLAS_BAR_WIDTH, NIB_HEIGHT,
                ATLAS_CORNER, ATLAS_CORNER,
                ATLAS_CORNER, ATLAS_CORNER,
                TEXTURE_WIDTH, TEXTURE_HEIGHT
        );

        // Bottom Left
        if(customHud.isShowBars())guiGraphics.blit(RenderPipelines.GUI_TEXTURED,
                BOX_TEXTURE,
                boxX, boxY + this.boxHeight - ATLAS_CORNER,
                0, 0,
                ATLAS_CORNER, ATLAS_CORNER,
                ATLAS_CORNER, ATLAS_CORNER,
                TEXTURE_WIDTH, TEXTURE_HEIGHT
        );

        // Bottom
        if(customHud.isShowBars())guiGraphics.blit(RenderPipelines.GUI_TEXTURED,
                BOX_TEXTURE,
                boxX + ATLAS_CORNER, boxY + this.boxHeight - ATLAS_CORNER + NIB_HEIGHT,
                ATLAS_CORNER, NIB_HEIGHT,
                this.boxWidth - ATLAS_CORNER * 2, ATLAS_BAR_HEIGHT,
                ATLAS_BAR_WIDTH, ATLAS_BAR_HEIGHT,
                TEXTURE_WIDTH, TEXTURE_HEIGHT
        );

        // Bottom Right
        if(customHud.isShowBars())guiGraphics.blit(RenderPipelines.GUI_TEXTURED,
                BOX_TEXTURE,
                boxX + this.boxWidth - ATLAS_CORNER, boxY + this.boxHeight - ATLAS_CORNER,
                ATLAS_CORNER + ATLAS_BAR_WIDTH, 0,
                ATLAS_CORNER, ATLAS_CORNER,
                ATLAS_CORNER, ATLAS_CORNER,
                TEXTURE_WIDTH, TEXTURE_HEIGHT
        );
    }

    private Pair<Integer, Integer> assembleHud() {
        componentLines.clear();

        AtomicBoolean hasData = new AtomicBoolean(false);

        customHud.getStringLines().forEach(componentParts -> {
            PlaceholderResult result = PlaceholderHandler.instance().resolve(componentParts.value1());

            if((result.success()[0] || !result.errors().isEmpty()) && !result.success()[1]) {
                componentLines.add(Triplet.of(componentParts.value2(), componentParts.value3(), result.text()));
                hasData.set(true);
            }
        });

        if(!hasData.get()) {
            componentLines.clear();
        }

        return Pair.of(
                Math.max(MIN_WIDTH, componentLines.stream()
                        .mapToInt(
                                line -> TextHelper.getWidth(Minecraft.getInstance().font, line.value3(), line.value2())
                        ).max().orElse(0)),
                LINE_HEIGHT * componentLines.size()
        );
    }
    //endregion
}
