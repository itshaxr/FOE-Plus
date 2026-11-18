package dannypx.foe.handler.renderer;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.BaitboxHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Beside a baitbox's Package (or any menu holding bait): what the bait in it is worth at your bait prices, by
 * rarity, styled like the Fish Value box. Only shows when the menu itself holds bait.
 */
public final class BaitValueBox {
    private BaitValueBox() {}

    static final String[] NAMES = {"Common", "Rare", "Epic", "Legendary", "Mythical"};
    static final int[] COLORS = {0xFFFFFF, 0x2B85C4, 0x1CD832, 0xD98103, 0xC93832}; // the game's own rarity colours (see FoeLocations.rarityColor)
    static final int W = 112;

    /// Package-private for tests: the rows {rarity index} that have bait, and the box's height for them.
    static List<Integer> rows(long[] got) {
        List<Integer> out = new ArrayList<>();
        for (int r = 0; r < Math.min(got.length, NAMES.length); r++) if (got[r] > 0) out.add(r);
        return out;
    }

    static int height(int rows) {
        return 18 + rows * 20 + 14 + 4;
    }

    /// `belowSorters`: the sorter boxes are on the right (a chest or the vault): go under them, and under the Fish
    /// Value box when `fishBox`.
    public static void render(GuiGraphics g, net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen, boolean belowSorters, boolean fishBox) {
        if (!Configs.mainConfig.enableMod.get()) return;
        long[] got = BaitboxHandler.baitIn(screen.getMenu());
        List<Integer> rows = rows(got);
        if (rows.isEmpty()) return;
        var pos = (dannypx.foe.mixin.accessor.ContainerScreenPosAccessor) screen;
        int right = pos.foe$leftPos() + pos.foe$imageWidth();
        int menuTop = pos.foe$topPos();
        // On a chest or the vault the sorter boxes (and the fish value) are on the right already: go under them.
        int top = belowSorters ? menuTop + 18 + 5 * 22 + 2 + 6 + (fishBox ? 18 + 2 * 20 + 14 + 4 + 4 : 0) : menuTop;
        draw(g, right + 4, top, got, BaitboxHandler.worthIn(screen.getMenu()), rows, BaitboxHandler.instance().packageOpen(screen) ? "Package Value" : "Bait Value");
    }

    static void draw(GuiGraphics g, int x, int top, long[] got, long[] worth, List<Integer> rows, String title) {
        Font font = Minecraft.getInstance().font;
        int h = height(rows.size());
        g.fill(x + 1, top + 1, x + W - 1, top + h - 1, 0xC0101418);
        g.fill(x + 1, top, x + W - 1, top + 1, 0xFF5A6770);
        g.fill(x + 1, top + h - 1, x + W - 1, top + h, 0xFF5A6770);
        g.fill(x, top + 1, x + 1, top + h - 1, 0xFF5A6770);
        g.fill(x + W - 1, top + 1, x + W, top + h - 1, 0xFF5A6770);
        Component head = Component.literal(title).withStyle(ChatFormatting.BOLD);
        g.drawString(font, head, x + W / 2 - font.width(head) / 2, top + 5, 0xFFFFFFFF, true);
        int y = top + 18;
        long total = 0;
        for (int r : rows) {
            long value = worth[r];
            total += value;
            Component name = Component.literal(NAMES[r]).withColor(COLORS[r]);
            Component money = Component.literal(BaitboxHandler.money(value)).withStyle(ChatFormatting.GOLD);
            Component count = Component.literal(String.format(java.util.Locale.ROOT, "%,d bait", got[r])).withStyle(ChatFormatting.DARK_GRAY);
            g.drawString(font, name, x + 5, y, 0xFFFFFFFF, true);
            g.drawString(font, money, x + W - 5 - font.width(money), y, 0xFFFFFFFF, true);
            g.pose().pushMatrix();
            g.pose().translate(x + W - 5, y + 9);
            g.pose().scale(0.75f, 0.75f);
            g.drawString(font, count, -font.width(count), 0, 0xFFFFFFFF, false);
            g.pose().popMatrix();
            y += 20;
        }
        g.fill(x + 5, y - 2, x + W - 5, y - 1, 0x40FFFFFF);
        Component sum = Component.literal(BaitboxHandler.money(total)).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        g.drawString(font, Component.literal("Total").withStyle(ChatFormatting.WHITE), x + 5, y + 2, 0xFFFFFFFF, true);
        g.drawString(font, sum, x + W - 5 - font.width(sum), y + 2, 0xFFFFFFFF, true);
    }
}
