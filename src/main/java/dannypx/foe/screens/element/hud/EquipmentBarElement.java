package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.InventoryHandler;
import dannypx.foe.handler.logic.LoadingHandler;
import dannypx.foe.helper.FoeLocations;
import dannypx.foe.item.FishingRodTagObject;
import dannypx.foe.item.TagObject;
import dannypx.foe.item.ValidateItem;
import dannypx.foe.screens.element.Element;
import dannypx.foe.type.Alignment;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * FOE's equipment bar above the hotbar: armor (chestplate, leggings, boots) on the left and rod
 * parts (reel, pole, line) on the right, each in a small box underlined in its rarity colour.
 * Ported from FOECollab's EquipmentHud (AGPL-3.0); the items come from FOE-R's inventory tracking.
 */
public class EquipmentBarElement extends Element {
    private static final int BOX = 20;
    private static final int STEP = 22;

    public EquipmentBarElement() {
        super(0, 0, 0, 0, Alignment.BOTTOM, Component.literal("Equipment Bar"), false);
    }

    @Override
    public void render(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!Configs.hudConfig.showEquipmentBar.get() || mc.player == null || !LoadingHandler.instance().isLoadingDone()) return;
        if (mc.options.hideGui) return;

        Font font = mc.font;
        float scale = Configs.hudConfig.equipmentBarScale.get();
        int centerX = Math.round(mc.getWindow().getGuiScaledWidth() / 2f / scale);
        int top = Math.round(mc.getWindow().getGuiScaledHeight() / scale) - Configs.hudConfig.equipmentBarOffsetFromBottom.get() - BOX;
        int alpha = Math.round(Configs.hudConfig.equipmentBarOpacity.get() / 100f * 255f) << 24;

        FishingRodTagObject rod = InventoryHandler.instance().getCurrentFishingRod();
        List<TagObject> reel = rod.getReelItem();
        List<TagObject> pole = rod.getPoleItem();
        List<TagObject> line = rod.getLineItem();

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(scale, scale);

        int armorLeft = centerX - 64 - 1;
        drawArmor(guiGraphics, font, armorLeft, top, alpha, mc.player.getItemBySlot(EquipmentSlot.CHEST), "ᴄ");
        drawArmor(guiGraphics, font, armorLeft + STEP, top, alpha, mc.player.getItemBySlot(EquipmentSlot.LEGS), "ʟ");
        drawArmor(guiGraphics, font, armorLeft + STEP * 2, top, alpha, mc.player.getItemBySlot(EquipmentSlot.FEET), "ʙ");

        int partsLeft = centerX + 1;
        drawPart(guiGraphics, font, partsLeft, top, alpha, reel, "ʀ");
        drawPart(guiGraphics, font, partsLeft + STEP, top, alpha, pole, "ᴘ");
        drawPart(guiGraphics, font, partsLeft + STEP * 2, top, alpha, line, "ʟ");

        guiGraphics.pose().popMatrix();
    }

    private static void drawArmor(GuiGraphics guiGraphics, Font font, int x, int y, int alpha, ItemStack stack, String placeholder) {
        String rarity = null;
        if (!stack.isEmpty()) {
            Pair<Boolean, TagObject> item = ValidateItem.isServerItem(stack, true);
            if (item.value1()) rarity = item.value2().getRarity();
        }
        drawBox(guiGraphics, font, x, y, alpha, stack, rarity, placeholder);
    }

    private static void drawPart(GuiGraphics guiGraphics, Font font, int x, int y, int alpha, List<TagObject> part, String placeholder) {
        if (part.isEmpty()) {
            drawBox(guiGraphics, font, x, y, alpha, ItemStack.EMPTY, null, placeholder);
        } else {
            TagObject item = part.getFirst();
            drawBox(guiGraphics, font, x, y, alpha, item.getItemStack(), item.getRarity(), placeholder);
        }
    }

    private static void drawBox(GuiGraphics guiGraphics, Font font, int x, int y, int alpha, ItemStack stack, String rarity, String placeholder) {
        guiGraphics.fill(x, y, x + BOX, y + BOX, alpha);

        int color = FoeLocations.rarityColor(rarity);
        int underline = color >= 0 ? 0xFF000000 | color : alpha | 0xFFFFFF;
        guiGraphics.fill(x, y + BOX, x + BOX, y + BOX + 1, underline);

        if (!stack.isEmpty()) {
            guiGraphics.renderItem(stack, x + 2, y + 2);
        } else {
            Component text = Component.literal(placeholder).withStyle(ChatFormatting.GRAY);
            guiGraphics.drawString(font, text, x + BOX / 2 - font.width(text) / 2, y + BOX / 2 - font.lineHeight / 2, 0xFFFFFFFF, true);
        }
    }
}
