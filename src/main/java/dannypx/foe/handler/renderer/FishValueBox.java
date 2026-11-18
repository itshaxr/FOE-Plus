package dannypx.foe.handler.renderer;

import dannypx.foe.config.Configs;
import dannypx.foe.item.TagObject;
import dannypx.foe.item.ValidateItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Beside a chest (not the Personal Vault): what the fish in it, and in your inventory, are worth (the value the
 * server puts on each fish), in a small box under the Pet Sorter, styled like it. Only shows when there are fish.
 */
public final class FishValueBox {
    private FishValueBox() {}

    /// {value of the fish, how many} in the container's own slots and in yours.
    public record Totals(double chest, int chestCount, double mine, int mineCount) {}

    static Totals count(AbstractContainerScreen<?> screen) {
        double chest = 0, mine = 0;
        int cn = 0, mn = 0;
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !ValidateItem.isFish(stack).value1()) continue;
            TagObject fish = ValidateItem.isFish(stack).value2();
            // the fish's own value (getMoney is an auction listing's price: always 0 here)
            double v = fish.getSellValue() * Math.max(1, stack.getCount());
            boolean own = slot.container instanceof Inventory;
            if (own && slot.getContainerSlot() >= 36) continue;
            if (own) { mine += v; mn += stack.getCount(); } else { chest += v; cn += stack.getCount(); }
        }
        return new Totals(chest, cn, mine, mn);
    }

    /// "$1.23M", "$210K", "$950".
    static String money(double v) {
        if (v >= 1_000_000_000) return String.format(Locale.ROOT, "$%.2fB", v / 1e9);
        if (v >= 1_000_000) return String.format(Locale.ROOT, "$%.2fM", v / 1e6);
        if (v >= 10_000) return String.format(Locale.ROOT, "$%.1fK", v / 1e3);
        return String.format(Locale.ROOT, "$%,.0f", v);
    }

    /// Whether the box shows on this menu (there are fish in it or in your inventory).
    public static boolean shown(AbstractContainerScreen<?> screen) {
        if (!Configs.inventoryScreenConfig.showFishValue.get()) return false;
        Totals t = count(screen);
        return t.chestCount() + t.mineCount() > 0;
    }

    /// Draws the box for a container `rows` rows tall, under the Pet Sorter on the right. `what`: "Chest" / "Vault".
    public static void render(GuiGraphics g, AbstractContainerScreen<?> screen, int rows, String what) {
        if (!Configs.inventoryScreenConfig.showFishValue.get()) return;
        Totals t = count(screen);
        if (t.chestCount() + t.mineCount() == 0) return;
        Minecraft mc = Minecraft.getInstance();
        int right = mc.getWindow().getGuiScaledWidth() / 2 + 88;
        int top = mc.getWindow().getGuiScaledHeight() / 2 - (114 + rows * 18) / 2 + 18 + 5 * 22 + 2 + 6;
        draw(g, right + 4, top, t, what);
    }

    /// The box itself, its top left at (x, top).
    public static void draw(GuiGraphics g, int x, int top, Totals t, String what) {
        Font font = Minecraft.getInstance().font;
        Component[][] rowsText = {
                {Component.literal(what).withStyle(ChatFormatting.GRAY), Component.literal(money(t.chest())).withStyle(ChatFormatting.GOLD),
                        Component.literal(t.chestCount() + " fish").withStyle(ChatFormatting.DARK_GRAY)},
                {Component.literal("Inventory").withStyle(ChatFormatting.GRAY), Component.literal(money(t.mine())).withStyle(ChatFormatting.GOLD),
                        Component.literal(t.mineCount() + " fish").withStyle(ChatFormatting.DARK_GRAY)},
        };
        Component total = Component.literal(money(t.chest() + t.mine())).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        int w = 104;
        int h = 18 + 2 * 20 + 14 + 4;
        g.fill(x + 1, top + 1, x + w - 1, top + h - 1, 0xC0101418);
        g.fill(x + 1, top, x + w - 1, top + 1, 0xFF5A6770);
        g.fill(x + 1, top + h - 1, x + w - 1, top + h, 0xFF5A6770);
        g.fill(x, top + 1, x + 1, top + h - 1, 0xFF5A6770);
        g.fill(x + w - 1, top + 1, x + w, top + h - 1, 0xFF5A6770);
        Component title = Component.literal("Fish Value").withStyle(ChatFormatting.BOLD);
        g.drawString(font, title, x + w / 2 - font.width(title) / 2, top + 5, 0xFFFFFFFF, true);
        int y = top + 18;
        for (Component[] r : rowsText) {
            g.drawString(font, r[0], x + 5, y, 0xFFFFFFFF, true);
            g.drawString(font, r[1], x + w - 5 - font.width(r[1]), y, 0xFFFFFFFF, true);
            g.pose().pushMatrix();
            g.pose().translate(x + w - 5, y + 9);
            g.pose().scale(0.75f, 0.75f);
            g.drawString(font, r[2], -font.width(r[2]), 0, 0xFFFFFFFF, false);
            g.pose().popMatrix();
            y += 20;
        }
        g.fill(x + 5, y - 2, x + w - 5, y - 1, 0x40FFFFFF);
        g.drawString(font, Component.literal("Total").withStyle(ChatFormatting.WHITE), x + 5, y + 2, 0xFFFFFFFF, true);
        g.drawString(font, total, x + w - 5 - font.width(total), y + 2, 0xFFFFFFFF, true);
    }
}
