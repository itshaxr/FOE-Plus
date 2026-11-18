package dannypx.foe.screens.element.hud;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.fetch.TabOverlayHandler;
import dannypx.foe.handler.logic.LoadingHandler;
import dannypx.foe.handler.store.CustomHudIconDataHandler;
import dannypx.foe.handler.store.CustomTrackerDataHandler;
import dannypx.foe.helper.ItemStackHelper;
import dannypx.foe.placeholder.evaluator.PlaceholderResult;
import dannypx.foe.handler.logic.PlaceholderHandler;
import dannypx.foe.screens.element.Element;
import dannypx.foe.screens.interfaces.ScreenConstants;
import dannypx.foe.type.Alignment;
import dannypx.foe.type.custom_value.ItemStackValue;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class CustomHudIconElement extends Element implements ScreenConstants {
    //region Fields
    // isCentre, isSmall, Line
    private Pair<Integer, Integer> contentDimensions = Pair.of(0, 0);

    private int boxWidth = 0;
    private int boxHeight = 0;

    private CustomHudIconDataHandler.CustomHudIcon customHudIcon;

    private static final Identifier BOX_TEXTURE = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "textures/gui/sprites/elements/sidebar_atlas.png");
    private static final int TEXTURE_WIDTH = 17;
    private static final int TEXTURE_HEIGHT = 11;
    private static final int BOX_PADDING = 5;
    private static final int MIN_WIDTH = 16 + BOX_PADDING * 2 + PADDING * 2;
    private static final int MIN_HEIGHT = 16 + BOX_PADDING * 2 + PADDING_QUART * 2;
    private static final int LINE_HEIGHT = Minecraft.getInstance().font.lineHeight + 1;
    //endregion

    public CustomHudIconElement(CustomHudIconDataHandler.CustomHudIcon customHudIcon, Component message) {
        super(MIN_WIDTH,
                MIN_HEIGHT,
                customHudIcon.getxPos() / 100f,
                customHudIcon.getyPos() / 100f,
                customHudIcon.getAlignment(),
                message,
                false);
        this.customHudIcon = customHudIcon;
    }

    //region Methods
    @Override
    public void render(GuiGraphics guiGraphics) {
        if(!customHudIcon.isShowElement()) { return; }

        int scaledWidth = (int) (Minecraft.getInstance().getWindow().getGuiScaledWidth() * (1 / dannypx.foe.utils.HudScale.crisp(customHudIcon.getScale())));
        int scaledHeight = (int) (Minecraft.getInstance().getWindow().getGuiScaledHeight() * (1 / dannypx.foe.utils.HudScale.crisp(customHudIcon.getScale())));

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(dannypx.foe.utils.HudScale.crisp(customHudIcon.getScale()), dannypx.foe.utils.HudScale.crisp(customHudIcon.getScale()));
        if(LoadingHandler.instance().isLoadingDone()
                && TabOverlayHandler.instance().isInInstance()
        ) {
            boxWidth = MIN_WIDTH;
            boxHeight = MIN_HEIGHT;

            int x = switch (customHudIcon.getAlignment()) {
                case TOP_LEFT, BOTTOM_LEFT, LEFT -> Math.round(scaledWidth * xPos);
                case TOP, BOTTOM -> Math.round(scaledWidth * xPos) - boxWidth / 2;
                case TOP_RIGHT, BOTTOM_RIGHT, RIGHT -> scaledWidth
                        - Math.round(scaledWidth * xPos);
                default -> 0;
            };

            int y = switch (customHudIcon.getAlignment()) {
                case TOP_LEFT, TOP_RIGHT, TOP -> Math.round(scaledHeight * yPos);
                case LEFT, RIGHT -> Math.round(scaledHeight * yPos) - boxHeight / 2;
                case BOTTOM_LEFT, BOTTOM_RIGHT, BOTTOM -> scaledHeight
                        - Math.round(scaledHeight * yPos);
                default -> 0;
            };

            x = switch (customHudIcon.getAlignment()) {
                case TOP_LEFT, BOTTOM_LEFT, LEFT -> x;
                case TOP, BOTTOM -> x - boxWidth / 2;
                case TOP_RIGHT, BOTTOM_RIGHT, RIGHT -> x - boxWidth;
                default -> 0;
            };

            y = switch (customHudIcon.getAlignment()) {
                case TOP_LEFT, TOP_RIGHT, TOP -> y;
                case BOTTOM_LEFT, BOTTOM_RIGHT, BOTTOM -> y - boxHeight;
                case LEFT, RIGHT -> y - boxHeight / 2;
                default -> 0;
            };

            this.renderBox(guiGraphics, x, y);
            this.renderComponent(guiGraphics, x, y);
        }
        guiGraphics.pose().popMatrix();
    }

    private void renderComponent(GuiGraphics guiGraphics, int x, int y) {
        int componentX;
        int componentY;

        if(customHudIcon.getAlignment() == Alignment.TOP || customHudIcon.getAlignment() == Alignment.BOTTOM) {
            componentX = x + PADDING + BOX_PADDING + boxWidth / 2;
        } else {
            componentX = x + PADDING + BOX_PADDING;
        }

        if(customHudIcon.getAlignment() == Alignment.LEFT || customHudIcon.getAlignment() == Alignment.RIGHT) {
            componentY = y + PADDING_QUART + BOX_PADDING + boxHeight / 2;
        } else {
            componentY = y + PADDING_QUART + BOX_PADDING;
        }

        ItemStack itemStack = getItemStack();

        if(!itemStack.isEmpty()) {
            guiGraphics.renderItem(itemStack, componentX, componentY);
        }
    }

    private ItemStack getItemStack() {
        return switch (customHudIcon.getIconType()) {
            case SLOT -> {
                int slot = Integer.parseInt(customHudIcon.getIcon());
                yield slot >= 0 && slot < 36 ? Minecraft.getInstance().player.getInventory().getItem(slot) : ItemStack.EMPTY;
            }
            case ITEM -> ItemStackHelper.valueOf(customHudIcon.getIcon());
            case PLACEHOLDER -> {
                PlaceholderResult result = PlaceholderHandler.instance().resolve(customHudIcon.getIcon());

                if(result.success()[0] && !result.success()[1]) {
                    try {
                        int slot = Integer.parseInt(result.text().getString());
                        yield slot >= 0 && slot < 36 ? Minecraft.getInstance().player.getInventory().getItem(slot) : ItemStack.EMPTY;
                    } catch (NumberFormatException ignored) {}

                    yield ItemStackHelper.valueOf(result.text().getString());
                }

                yield ItemStack.EMPTY;
            }
            case TRACKER -> {
                CustomTrackerDataHandler.CustomTracker customTracker = CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.getOrDefault(customHudIcon.getIcon(), null);

                if(customTracker != null && customTracker.getValue() instanceof ItemStackValue itemStackValue) {
                    yield itemStackValue.value().value1();
                }

                yield ItemStack.EMPTY;
            }
        };
    }

    private void renderBox(GuiGraphics guiGraphics, int x, int y) {
        int boxX = x;
        int boxY = y;

        if(customHudIcon.getAlignment() == Alignment.TOP || customHudIcon.getAlignment() == Alignment.BOTTOM) {
            boxX = boxX + boxWidth / 2;
        }

        if(customHudIcon.getAlignment() == Alignment.LEFT || customHudIcon.getAlignment() == Alignment.RIGHT) {
            boxY = boxY + boxHeight / 2;
        }

        int ATLAS_CORNER = 8;
        int ATLAS_BAR_WIDTH = 1;
        int ATLAS_BAR_HEIGHT = 5;
        int NIB_HEIGHT = 3;

        // Alpha Box
        if(customHudIcon.isShowBackground()) guiGraphics.fill(
                boxX + BOX_PADDING, boxY + 3,
                boxX + this.boxWidth - BOX_PADDING, boxY + this.boxHeight - 3,
                0x7f000000
        );

        // Top Left
        if(customHudIcon.isShowBars()) guiGraphics.blit(RenderPipelines.GUI_TEXTURED,
                BOX_TEXTURE,
                boxX, boxY,
                0, NIB_HEIGHT,
                ATLAS_CORNER, ATLAS_CORNER,
                ATLAS_CORNER, ATLAS_CORNER,
                TEXTURE_WIDTH, TEXTURE_HEIGHT
        );

        // Top
        if(customHudIcon.isShowBars())guiGraphics.blit(RenderPipelines.GUI_TEXTURED,
                BOX_TEXTURE,
                boxX + ATLAS_CORNER, boxY,
                ATLAS_CORNER, NIB_HEIGHT,
                this.boxWidth - ATLAS_CORNER * 2, ATLAS_BAR_HEIGHT,
                ATLAS_BAR_WIDTH, ATLAS_BAR_HEIGHT,
                TEXTURE_WIDTH, TEXTURE_HEIGHT
        );

        // Top Right
        if(customHudIcon.isShowBars())guiGraphics.blit(RenderPipelines.GUI_TEXTURED,
                BOX_TEXTURE,
                boxX + this.boxWidth - ATLAS_CORNER, boxY,
                ATLAS_CORNER + ATLAS_BAR_WIDTH, NIB_HEIGHT,
                ATLAS_CORNER, ATLAS_CORNER,
                ATLAS_CORNER, ATLAS_CORNER,
                TEXTURE_WIDTH, TEXTURE_HEIGHT
        );

        // Bottom Left
        if(customHudIcon.isShowBars())guiGraphics.blit(RenderPipelines.GUI_TEXTURED,
                BOX_TEXTURE,
                boxX, boxY + this.boxHeight - ATLAS_CORNER,
                0, 0,
                ATLAS_CORNER, ATLAS_CORNER,
                ATLAS_CORNER, ATLAS_CORNER,
                TEXTURE_WIDTH, TEXTURE_HEIGHT
        );

        // Bottom
        if(customHudIcon.isShowBars())guiGraphics.blit(RenderPipelines.GUI_TEXTURED,
                BOX_TEXTURE,
                boxX + ATLAS_CORNER, boxY + this.boxHeight - ATLAS_CORNER + NIB_HEIGHT,
                ATLAS_CORNER, NIB_HEIGHT,
                this.boxWidth - ATLAS_CORNER * 2, ATLAS_BAR_HEIGHT,
                ATLAS_BAR_WIDTH, ATLAS_BAR_HEIGHT,
                TEXTURE_WIDTH, TEXTURE_HEIGHT
        );

        // Bottom Right
        if(customHudIcon.isShowBars())guiGraphics.blit(RenderPipelines.GUI_TEXTURED,
                BOX_TEXTURE,
                boxX + this.boxWidth - ATLAS_CORNER, boxY + this.boxHeight - ATLAS_CORNER,
                ATLAS_CORNER + ATLAS_BAR_WIDTH, 0,
                ATLAS_CORNER, ATLAS_CORNER,
                ATLAS_CORNER, ATLAS_CORNER,
                TEXTURE_WIDTH, TEXTURE_HEIGHT
        );
    }
    //endregion
}
