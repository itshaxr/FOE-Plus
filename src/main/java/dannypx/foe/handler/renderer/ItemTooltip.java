package dannypx.foe.handler.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.joml.Vector2i;
import org.joml.Vector2ic;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws an item's tooltip right now, on top of what has been drawn so far. FOE+'s own panels are
 * painted after the game has already drawn the frame's tooltip, so a tooltip asked for "next frame"
 * from them never shows: this draws it at once instead, the same lines and frame style the game uses.
 */
final class ItemTooltip {
    private ItemTooltip() {}

    static void draw(GuiGraphics g, Font font, ItemStack stack, int mouseX, int mouseY) {
        draw(g, font, stack, mouseX, mouseY, List.of());
    }

    static final int GAP = 2, PAD_X = 5, PAD_Y = 4, LINE_H = 11;

    /// The tooltip, with a box just above it (below when there's no room) saying what's on at the place:
    /// "⚡ Frenzy!   23:41 left", "✦ Fabled Fish!   until caught". Lined up with the tooltip's left edge.
    static void draw(GuiGraphics g, Font font, ItemStack stack, int mouseX, int mouseY, List<PlaceEvents.Banner> banners) {
        if (stack == null || stack.isEmpty()) return;
        List<Component> lines = Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
        if (lines.isEmpty()) return;
        List<ClientTooltipComponent> parts = new ArrayList<>();
        for (Component line : lines) parts.add(ClientTooltipComponent.create(line.getVisualOrderText()));
        stack.getTooltipImage().ifPresent(image -> parts.add(Math.min(1, parts.size()), ClientTooltipComponent.create(image)));
        g.nextStratum();
        if (banners == null || banners.isEmpty()) {
            g.renderTooltip(font, parts, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, stack.get(DataComponents.TOOLTIP_STYLE));
            return;
        }
        int boxH = boxHeight(banners.size());
        int[] at = new int[5];     // the tooltip's text x, y, width, height, and 1 when the box goes below it
        ClientTooltipPositioner pos = (sw, sh, x, y, w, h) -> {
            Vector2ic p = DefaultTooltipPositioner.INSTANCE.positionTooltip(sw, sh, x, y, w, h);
            int[] r = place(sw, sh, p.x(), p.y(), w, h, boxH);
            System.arraycopy(r, 0, at, 0, 5);
            return new Vector2i(r[0], r[1]);
        };
        g.renderTooltip(font, parts, mouseX, mouseY, pos, stack.get(DataComponents.TOOLTIP_STYLE));
        g.nextStratum();
        drawBox(g, font, banners, at, g.guiWidth());
    }

    static int boxHeight(int lines) {
        return lines * LINE_H - (LINE_H - 8) + PAD_Y * 2;
    }

    /// Package-private for tests: where the tooltip's text goes {x, y, w, h, below}, making room above it for a
    /// box `boxH` tall (its frame drawn 3 pixels round the text, like the game's), or putting the box below
    /// when the screen is too short for both.
    static int[] place(int sw, int sh, int x, int y, int w, int h, int boxH) {
        int top = y - 3 - GAP - boxH;                    // the box's top, above the tooltip's frame
        if (top >= 2) return new int[]{x, y, w, h, 0};
        int down = 2 - top;
        if (y + h + 3 + down <= sh - 2) return new int[]{x, y + down, w, h, 0};
        return new int[]{x, y, w, h, 1};
    }

    private static void drawBox(GuiGraphics g, Font font, List<PlaceEvents.Banner> banners, int[] at, int screenW) {
        int content = 0;
        for (PlaceEvents.Banner b : banners) content = Math.max(content, lineWidth(font, b));
        int w = Math.max(at[2] + 6, content + PAD_X * 2), h = boxHeight(banners.size());
        int x0 = Math.min(at[0] - 3, screenW - 2 - w); x0 = Math.max(2, x0);
        int y0 = at[4] == 1 ? at[1] + at[3] + 3 + GAP : at[1] - 3 - GAP - h;
        int c = banners.get(0).color() & 0xFFFFFF;
        // The game's tooltip look (a near-black body, a border a pixel in), the border in the event's colour.
        g.fill(x0 + 1, y0, x0 + w - 1, y0 + h, 0xF0100010);
        g.fill(x0, y0 + 1, x0 + w, y0 + h - 1, 0xF0100010);
        int edge = 0xFF000000 | c, soft = 0x90000000 | c;
        g.fill(x0 + 1, y0 + 1, x0 + w - 1, y0 + 2, edge);
        g.fill(x0 + 1, y0 + h - 2, x0 + w - 1, y0 + h - 1, soft);
        g.fill(x0 + 1, y0 + 2, x0 + 2, y0 + h - 2, edge);
        g.fill(x0 + w - 2, y0 + 2, x0 + w - 1, y0 + h - 2, edge);
        int ty = y0 + PAD_Y;
        for (PlaceEvents.Banner b : banners) {
            int col = 0xFF000000 | b.color();
            Component title = Component.empty().append(b.icon())
                    .append(Component.literal(b.title()).withStyle(st -> st.withBold(true).withColor(b.color())));
            g.drawString(font, title, x0 + PAD_X, ty, col, true);
            g.drawString(font, Component.literal(b.detail()), x0 + w - PAD_X - font.width(b.detail()), ty, 0xFFD8D8D8, true);
            ty += LINE_H;
        }
    }

    private static int lineWidth(Font font, PlaceEvents.Banner b) {
        Component title = Component.empty().append(b.icon()).append(Component.literal(b.title()).withStyle(st -> st.withBold(true)));
        return font.width(title) + 12 + font.width(b.detail());
    }
}
