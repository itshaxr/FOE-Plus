package dannypx.foe.handler.renderer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The look of the Bait Value and Fish Value boxes beside a menu: a dark panel with a title bar, one row per kind (a
 * coloured marker, its name with its count under it, its worth on the right) and the total under a line. The box is as
 * wide as its widest row needs, so a name never runs into its number.
 */
final class ValueBox {
    private ValueBox() {}

    /// One row: its marker colour, name (in its colour), the small count under it, and its worth.
    record Row(int color, Component name, Component count, Component money) {}

    static final int PAD = 6, MARK = 2, GAP = 14, ROW = 22, HEAD = 17, FOOT = 17, MIN_W = 120;

    static int width(List<Row> rows, Component title, Component total) {
        Font font = Minecraft.getInstance().font;
        int w = font.width(title) + PAD * 2 + 8;
        for (Row r : rows) {
            int left = Math.max(font.width(r.name()), Math.round(font.width(r.count()) * 0.75f));
            w = Math.max(w, PAD + MARK + 4 + left + GAP + font.width(r.money()) + PAD);
        }
        w = Math.max(w, PAD + font.width("Total") + GAP + font.width(total) + PAD);
        return Math.max(MIN_W, w);
    }

    static int height(int rows) {
        return HEAD + 3 + rows * ROW + FOOT + 2;
    }

    static void draw(GuiGraphics g, int x, int top, Component title, List<Row> rows, Component total) {
        Font font = Minecraft.getInstance().font;
        int w = width(rows, title, total), h = height(rows.size());
        // the panel, its border, and a slightly lighter title bar
        g.fill(x, top, x + w, top + h, 0xFF2B3440);
        g.fill(x + 1, top + 1, x + w - 1, top + h - 1, 0xEC0E1217);
        g.fill(x + 1, top + 1, x + w - 1, top + HEAD, 0xFF161C24);
        g.fill(x + 1, top + HEAD, x + w - 1, top + HEAD + 1, 0xFF2B3440);
        Component head = title.copy().withStyle(ChatFormatting.BOLD);
        g.drawString(font, head, x + (w - font.width(head)) / 2, top + 5, 0xFFF2E6C8, true);

        int y = top + HEAD + 4;
        for (Row r : rows) {
            // marker, name, count under the name; the worth on the right, between the two lines
            // pure white (Common) drawn a hair off-white: an exact white fill went missing in game
            int mark = (r.color() & 0xFFFFFF) == 0xFFFFFF ? 0xFFEEF0F2 : 0xFF000000 | r.color();
            g.fill(x + PAD, y + 1, x + PAD + MARK, y + ROW - 5, mark);
            int tx = x + PAD + MARK + 4;
            g.drawString(font, r.name(), tx, y + 1, 0xFFFFFFFF, true);
            g.pose().pushMatrix();
            g.pose().translate(tx, y + 11);
            g.pose().scale(0.75f, 0.75f);
            g.drawString(font, r.count(), 0, 0, 0xFFFFFFFF, false);
            g.pose().popMatrix();
            g.drawString(font, r.money(), x + w - PAD - font.width(r.money()), y + 5, 0xFFFFFFFF, true);
            y += ROW;
        }
        // the total
        g.fill(x + PAD, y, x + w - PAD, y + 1, 0x33FFFFFF);
        g.drawString(font, Component.literal("Total").withStyle(ChatFormatting.GRAY), x + PAD, y + 5, 0xFFFFFFFF, true);
        g.drawString(font, total, x + w - PAD - font.width(total), y + 5, 0xFFFFFFFF, true);
    }
}
