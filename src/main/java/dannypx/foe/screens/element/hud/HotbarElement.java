package dannypx.foe.screens.element.hud;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.fetch.TabOverlayHandler;
import dannypx.foe.handler.logic.InventoryHandler;
import dannypx.foe.handler.logic.LoadingHandler;
import dannypx.foe.helper.GuiGraphicsHelper;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.item.FishingRodTagObject;
import dannypx.foe.item.TagObject;
import dannypx.foe.item.ValidateItem;
import dannypx.foe.type.StringStyle;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.CommonColors;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import dannypx.foe.config.Configs;
import dannypx.foe.screens.element.Element;

public class HotbarElement extends Element {
    //region Fields
    private static final int WIDTH = 220;
    private static final int HEIGHT = 51;

    private static final Identifier HOTBAR_TEXTURE = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "elements/hotbar");
    private static final int HOTBAR_WIDTH = 170;
    private static final int HOTBAR_HEIGHT = 26;

    private static final Identifier GEAR_TEXTURE = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "elements/gear");
    private static final int GEAR_WIDTH = 60;
    private static final int GEAR_HEIGHT = 24;

    private static final Identifier SLOT_TEXTURE = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "elements/slot");
    private static final int SLOT_WIDTH = 24;
    private static final int SLOT_HEIGHT = 24;

    private static final Identifier SELECTOR_TEXTURE = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "elements/selector");
    private static final int SELECTOR_WIDTH = 20;
    private static final int SELECTOR_HEIGHT = 24;

    private int selectedSlot = -1;
    private long heldItemTooltipFade = 0L;
    //endregion

    public HotbarElement() {
        super(WIDTH,
                HEIGHT,
                Configs.hudConfig.hotbarElementXPosition.get() / 100f,
                Configs.hudConfig.hotbarElementYPosition.get() / 100f,
                Configs.hudConfig.hotbarElementAlignment.get(),
                Configs.hudConfig.hotbarElementGroup.translation("Hotbar Element"),
                false);
    }

    public HotbarElement(boolean isCopy) {
        super(WIDTH,
                HEIGHT,
                Configs.hudConfig.hotbarElementXPosition.get() / 100f,
                Configs.hudConfig.hotbarElementYPosition.get() / 100f,
                Configs.hudConfig.hotbarElementAlignment.get(),
                Configs.hudConfig.hotbarElementGroup.translation("Hotbar Element"),
                isCopy);
    }

    //region Methods
    @Override
    public void render(GuiGraphics guiGraphics) {
        int scaledWidth = (int) (Minecraft.getInstance().getWindow().getGuiScaledWidth() * (1 / Configs.hudConfig.hotbarElementScale.get()));
        int scaledHeight = (int) (Minecraft.getInstance().getWindow().getGuiScaledHeight() * (1 / Configs.hudConfig.hotbarElementScale.get()));

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(Configs.hudConfig.hotbarElementScale.get(), Configs.hudConfig.hotbarElementScale.get());

        if(LoadingHandler.instance().isLoadingDone() && Configs.hudConfig.showHotbarElement.get()) {
            // Position
            if(!isCopy) {
                xPos = Configs.hudConfig.hotbarElementXPosition.get() / 100f;
                yPos = Configs.hudConfig.hotbarElementYPosition.get() / 100f;
            }

            int x = switch (Configs.hudConfig.hotbarElementAlignment.get()) {
                case BOTTOM_LEFT -> Math.round(scaledWidth * xPos);
                case BOTTOM -> Math.round(scaledWidth * xPos) - WIDTH / 2;
                case BOTTOM_RIGHT -> scaledWidth
                        - Math.round(scaledWidth * xPos) - WIDTH;
                default -> 0;
            };
            int y = scaledHeight
                    - Math.round(scaledHeight * yPos) - HEIGHT;

            this.renderHotbar(guiGraphics, x, y);
            this.renderSelector(guiGraphics, x, y);
            this.renderItems(guiGraphics, Minecraft.getInstance().font, x, y);
            this.renderSelectedItemName(guiGraphics, Minecraft.getInstance().font, x, y);
            if(Configs.hudConfig.showHotbarParts.get() && TabOverlayHandler.instance().isInInstance()) this.renderParts(guiGraphics, x, y);
            if(Configs.hudConfig.showHotbarArmor.get() && TabOverlayHandler.instance().isInInstance()) this.renderArmor(guiGraphics, x, y);
            if(Configs.hudConfig.showHotbarBait.get() && TabOverlayHandler.instance().isInInstance()) this.renderBait(guiGraphics, Minecraft.getInstance().font, x, y);
        }
        guiGraphics.pose().popMatrix();
    }

    private void renderHotbar(GuiGraphics guiGraphics, int x, int y) {
        //region Texture
        int hotbarX = 25;
        int hotbarY = 25;

        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                HOTBAR_TEXTURE,
                x + hotbarX, y + hotbarY,
                HOTBAR_WIDTH, HOTBAR_HEIGHT
        );
        //endregion
    }

    private void renderItems(GuiGraphics guiGraphics, Font font, int x, int y) {
        //region Items
        if(Minecraft.getInstance().player != null) {
            int itemX = 30;
            int itemY = 30;

            int countX = 47;
            int countY = 41;

            for(int i = 0; i < 9; i++) {
                ItemStack item = Minecraft.getInstance().player.getInventory().getNonEquipmentItems().get(i);
                Pair<Boolean, TagObject> validatedItem = ValidateItem.isServerItem(item, true);

                guiGraphics.renderItem(item, x + itemX + (18 * i), y + itemY);

                // Pet markers (ported from FOECollab): this hotbar draws items itself, so the
                // decoration hook that adds them elsewhere doesn't run here.
                dannypx.foe.handler.renderer.ItemRendererHandler.instance().drawMaxPetStats(guiGraphics, item, x + itemX + (18 * i), y + itemY);
                dannypx.foe.handler.renderer.ItemRendererHandler.instance().drawMaxRodPart(guiGraphics, item, x + itemX + (18 * i), y + itemY);
                if (dannypx.foe.handler.logic.KeyBindHandler.instance().isInspecting(dannypx.foe.type.InspectAlways.Kind.PET)) {
                    dannypx.foe.handler.renderer.ItemRendererHandler.instance().drawPetRating(guiGraphics, font, item, x + itemX + (18 * i), y + itemY);
                } else {
                    dannypx.foe.handler.renderer.ItemRendererHandler.instance().drawPetRatingLetter(guiGraphics, font, item, x + itemX + (18 * i), y + itemY);
                }

                if(Configs.rendererConfig.useSmallStackCountNumber.get()) {
                    int count = Configs.rendererConfig.showStackCountOnBait.get()
                            ? validatedItem.value2().getCount()
                            : item.getCount();
                    Component countComponent = TextHelper.literal(TextHelper.smallCaps(TextHelper.shortenNumber(count, 0)));
                    int countWidth = font.width(countComponent);

                    if(count > 1) GuiGraphicsHelper.drawString(guiGraphics, font, countComponent,
                            x + countX + (18 * i) - countWidth, y + countY,
                            StringStyle.SHADOW, StringStyle.MIDDLE);
                } else {
                    int count = Configs.rendererConfig.showStackCountOnBait.get()
                            ? validatedItem.value2().getCount()
                            : item.getCount();
                    Component countComponent = TextHelper.literal(TextHelper.shortenNumber(count, 0));
                    int countWidth = font.width(countComponent);

                    if(count > 1) GuiGraphicsHelper.drawString(guiGraphics, font, countComponent,
                            x + countX + (18 * i) - countWidth, y + countY - 2,
                            StringStyle.SHADOW);
                }
            }
        }
        //endregion
    }

    private void renderSelector(GuiGraphics guiGraphics, int x, int y) {

        if(Minecraft.getInstance().player != null) {
            //region Texture
            int selectorX = 29;
            int selectorY = 26;
            int index = Minecraft.getInstance().player.getInventory().getSelectedSlot();

            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    SELECTOR_TEXTURE,
                    x + selectorX + (18 * index) - 1, y + selectorY,
                    SELECTOR_WIDTH, SELECTOR_HEIGHT
            );
            //endregion
        }
    }

    private void renderSelectedItemName(GuiGraphics guiGraphics, Font font, int x, int y) {
        if(Minecraft.getInstance().player != null) {
            int index = Minecraft.getInstance().player.getInventory().getSelectedSlot();
            ItemStack selectedStack = Minecraft.getInstance().player.getInventory().getNonEquipmentItems().get(index);

            int itemNameX = x + (WIDTH / 2);
            int itemNameY = y - font.lineHeight - 2;
            int width = font.width(selectedStack.getHoverName());

            if(selectedSlot != index) {
                selectedSlot = index;
                this.heldItemTooltipFade = System.currentTimeMillis();
            }

            itemNameX = itemNameX - (font.width(selectedStack.getHoverName()) / 2);

            if (selectedStack.isEmpty()) {
                this.heldItemTooltipFade = 0;
            } else if (System.currentTimeMillis() < this.heldItemTooltipFade + 2000L) {
                long time = Math.min(500, this.heldItemTooltipFade + 2000 - System.currentTimeMillis());
                float alpha = Math.min((((float) time) / 500) * 255, 255);
                if(alpha > 5f) {
                    guiGraphics.drawStringWithBackdrop(font, selectedStack.getHoverName(), itemNameX, itemNameY, width, ARGB.color((int) alpha, CommonColors.WHITE));
                }
            }
        }
    }

    private void renderParts(GuiGraphics guiGraphics, int x, int y) {
        //region Texture
        int gearX = 35;

        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                GEAR_TEXTURE,
                x + gearX, y,
                GEAR_WIDTH, GEAR_HEIGHT
        );
        //endregion

        //region Items
        if(Minecraft.getInstance().player != null) {
            int partsX = 39;
            int partsY = 4;

            FishingRodTagObject fishingRodTagObject = InventoryHandler.instance().getCurrentFishingRod();
            ItemStack reel = fishingRodTagObject.getReelItem().isEmpty() ? ItemStack.EMPTY : fishingRodTagObject.getReelItem().getFirst().getItemStack();
            ItemStack pole = fishingRodTagObject.getPoleItem().isEmpty() ? ItemStack.EMPTY : fishingRodTagObject.getPoleItem().getFirst().getItemStack();
            ItemStack line = fishingRodTagObject.getLineItem().isEmpty() ? ItemStack.EMPTY : fishingRodTagObject.getLineItem().getFirst().getItemStack();

            guiGraphics.renderItem(reel, x + partsX, y + partsY);
            guiGraphics.renderItem(pole, x + partsX + 18, y + partsY);
            guiGraphics.renderItem(line, x + partsX + 36, y + partsY);
        }
        //endregion
    }

    private void renderArmor(GuiGraphics guiGraphics, int x, int y) {
        //region Texture
        int gearX = 125;

        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                GEAR_TEXTURE,
                x + gearX, y,
                GEAR_WIDTH, GEAR_HEIGHT
        );
        //endregion

        //region Items
        if(Minecraft.getInstance().player != null) {
            int armorX = 129;
            int armorY = 4;

            ItemStack chestplate = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.CHEST);
            ItemStack leggings = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.LEGS);
            ItemStack boots = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.FEET);

            guiGraphics.renderItem(chestplate, x + armorX, y + armorY);
            guiGraphics.renderItem(leggings, x + armorX + 18, y + armorY);
            guiGraphics.renderItem(boots, x + armorX + 36, y + armorY);
        }
        //endregion
    }

    private void renderBait(GuiGraphics guiGraphics, Font font, int x, int y) {
        if(Minecraft.getInstance().player != null) {
            int partsX = 4;
            int partsY = 30;


            int countX = 21;
            int countY = 41;

            FishingRodTagObject fishingRodTagObject = InventoryHandler.instance().getCurrentFishingRod();
            TagObject bait = fishingRodTagObject.getActiveBait().isEmpty() ? null : fishingRodTagObject.getActiveBait().getFirst();
            if(bait != null) {
                //region Texture
                int baitY = 26;

                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        SLOT_TEXTURE,
                        x, y + baitY,
                        SLOT_WIDTH, SLOT_HEIGHT
                );
                //endregion
                //region Items
                guiGraphics.renderItem(bait.getItemStack(), x + partsX, y + partsY);

                if(Configs.rendererConfig.showStackCountOnBait.get()) {
                    boolean isSmall = Configs.rendererConfig.useSmallStackCountNumber.get();
                    int count = bait.getCount();
                    Component countComponent = isSmall
                            ? TextHelper.literal(TextHelper.smallCaps(TextHelper.shortenNumber(count, 0)))
                            : TextHelper.literal(TextHelper.shortenNumber(count, 0));
                    int countWidth = font.width(countComponent);

                    if(count > 1) {
                        if(isSmall) {
                            GuiGraphicsHelper.drawString(guiGraphics, font, countComponent,
                                    x + countX - countWidth, y + countY,
                                    StringStyle.SHADOW, StringStyle.MIDDLE, StringStyle.SMALL_CAPS
                            );
                        } else {
                            GuiGraphicsHelper.drawString(guiGraphics, font, countComponent,
                                    x + countX - countWidth, y + countY - 2,
                                    StringStyle.SHADOW
                            );
                        }
                    }
                }

                if(Configs.hudConfig.showBaitLock.get()
                        && fishingRodTagObject.getDisableBait()
                ) {
                    guiGraphics.drawString(font, Component.literal("\uD83D\uDD12"), x + 2, y + baitY, CommonColors.WHITE, true);
                }
                //endregion
            }
        }
    }
    //endregion
}
