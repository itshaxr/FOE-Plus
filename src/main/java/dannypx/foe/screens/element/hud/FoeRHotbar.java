package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.InventoryHandler;
import dannypx.foe.helper.FoeLocations;
import dannypx.foe.item.FishingRodTagObject;
import dannypx.foe.item.TagObject;
import dannypx.foe.item.ValidateItem;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The FOE-R HUD Style's bottom bar, in place of the game's hotbar (and FOE's equipment bar): FOE-R's idea (the hotbar
 * in a frame, the rod parts and armour in small frames above its ends) in FOE+'s dress, the same family as the
 * profile, pet and location boxes. Every frame is the same dark brown, with rounded corners; the picked slot has a
 * light tan outline, a warm glow and a notch above it; the rod parts and armour are underlined in their rarity's colour.
 */
public final class FoeRHotbar {
    private FoeRHotbar() {}

    static final int SLOT = 20, BAR_H = 24, GROUP_SLOT = 18, GROUP_H = 22;
    static final int RING = 0x6B4A2B, PICKED = 0xFFC9A26B;

    /// Drawn in place of the game's hotbar: the FOE-R style is on, the bar is switched on, and you're playing (not
    /// spectating).
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return Configs.mainConfig.enableMod.get() && FoeRBoxes.on() && Configs.hudConfig.showFoeRHotbar.get()
                && mc.player != null && !mc.player.isSpectator()
                && dannypx.foe.handler.logic.ConnectionHandler.instance().isOnServer();
    }

    /// A frame `w` by `h`: a dark outline with rounded corners, the dark brown ring, dark glass with a glint on top.
    static void frame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y, x + w - 2, y + h, 0xFF0B0B0B);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0B0B0B);
        g.fill(x, y + 2, x + w, y + h - 2, 0xFF0B0B0B);
        g.fill(x + 2, y + 1, x + w - 2, y + h - 1, 0xFF000000 | RING);
        g.fill(x + 1, y + 2, x + w - 1, y + h - 2, 0xFF000000 | RING);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xD0101418);
        g.fill(x + 3, y + 2, x + w - 3, y + 3, 0x30FFFFFF);
    }

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Font font = mc.font;
        int w = 9 * SLOT + 4;
        int x = g.guiWidth() / 2 - w / 2, y = g.guiHeight() - BAR_H;
        frame(g, x, y, w, BAR_H);
        for (int i = 1; i < 9; i++) g.fill(x + 2 + SLOT * i, y + 5, x + 3 + SLOT * i, y + BAR_H - 5, 0x22FFFFFF);

        // the picked slot: a light tan outline with a warm glow, and a notch above
        int picked = mc.player.getInventory().getSelectedSlot();
        if (picked >= 0 && picked < 9) {
            int sx = x + 2 + SLOT * picked;
            g.fill(sx, y + 2, sx + SLOT, y + BAR_H - 2, 0x33C9A26B);
            g.fill(sx, y + 1, sx + SLOT, y + 2, PICKED);
            g.fill(sx, y + BAR_H - 2, sx + SLOT, y + BAR_H - 1, PICKED);
            g.fill(sx - 1, y + 2, sx, y + BAR_H - 2, PICKED);
            g.fill(sx + SLOT, y + 2, sx + SLOT + 1, y + BAR_H - 2, PICKED);
            g.fill(sx + 8, y - 2, sx + 12, y - 1, PICKED);
            g.fill(sx + 9, y - 1, sx + 11, y, PICKED);
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getNonEquipmentItems().get(i);
            int ix = x + 4 + SLOT * i, iy = y + 4;
            if (stack.isEmpty()) continue;
            dannypx.foe.handler.renderer.ItemRendererHandler.instance().drawEquippedPetHighlight(g, stack, ix, iy);
            g.renderItem(stack, ix, iy);
            g.renderItemDecorations(font, stack, ix, iy);
        }

        // the rod parts over the left end, the armour over the right, clear of the experience bar
        int gw = 3 * GROUP_SLOT + 4, gy = g.guiHeight() - 30 - GROUP_H;
        if (Configs.hudConfig.foeRHotbarParts.get()) {
            FishingRodTagObject rod = InventoryHandler.instance().getCurrentFishingRod();
            group(g, x, gy, gw, new List[]{rod.getReelItem(), rod.getPoleItem(), rod.getLineItem()}, null);
        }
        if (Configs.hudConfig.foeRHotbarArmor.get()) {
            group(g, x + w - gw, gy, gw, null, new ItemStack[]{mc.player.getItemBySlot(EquipmentSlot.CHEST),
                    mc.player.getItemBySlot(EquipmentSlot.LEGS), mc.player.getItemBySlot(EquipmentSlot.FEET)});
        }
    }

    /// Three slots in a small frame: the rod's parts (`parts`) or armour pieces (`armor`), each underlined in its
    /// rarity's colour.
    @SuppressWarnings("rawtypes")
    private static void group(GuiGraphics g, int x, int y, int w, List[] parts, ItemStack[] armor) {
        frame(g, x, y, w, GROUP_H);
        for (int i = 0; i < 3; i++) {
            int sx = x + 2 + GROUP_SLOT * i;
            if (i > 0) g.fill(sx, y + 5, sx + 1, y + GROUP_H - 5, 0x22FFFFFF);
            ItemStack stack = ItemStack.EMPTY;
            String rarity = null;
            if (parts != null) {
                @SuppressWarnings("unchecked") List<TagObject> part = parts[i];
                if (part != null && !part.isEmpty()) { stack = part.getFirst().getItemStack(); rarity = part.getFirst().getRarity(); }
            } else if (armor[i] != null && !armor[i].isEmpty()) {
                stack = armor[i];
                Pair<Boolean, TagObject> item = ValidateItem.isServerItem(stack, true);
                if (item.value1()) rarity = item.value2().getRarity();
            }
            if (stack.isEmpty()) continue;
            g.renderItem(stack, sx + 1, y + 2);
            if (parts != null) dannypx.foe.handler.renderer.ItemRendererHandler.instance().drawMaxRodPart(g, stack, sx + 1, y + 2);
            int color = FoeLocations.rarityColor(rarity);
            if (color >= 0) g.fill(sx + 2, y + GROUP_H - 3, sx + 16, y + GROUP_H - 2, 0xFF000000 | color);
        }
    }
}
