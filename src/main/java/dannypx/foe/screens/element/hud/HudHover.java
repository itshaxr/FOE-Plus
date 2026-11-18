package dannypx.foe.screens.element.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * Tooltips on the HUD panels: a line that carries a hover text (the contest's other best baits) shows it when the
 * mouse is over it, while a screen with a cursor is open over the HUD (chat). Panels note where they drew such
 * lines each frame; the screen draws the tooltip after itself.
 */
public final class HudHover {
    private HudHover() {}

    /// One line drawn this frame: where (in GUI pixels), the scale it was drawn at, and the line.
    record Spot(float x, float y, float w, float h, float scale, Component line) {}

    private static final List<Spot> spots = new ArrayList<>();
    private static long frameNs = 0L;
    private static final long FRESH_NS = 250_000_000L;

    /// Package-private for tests: the line has a hover text somewhere in it.
    static boolean hasHover(Component line) {
        return line.visit((style, str) -> style.getHoverEvent() instanceof HoverEvent.ShowText
                ? java.util.Optional.of(Boolean.TRUE) : java.util.Optional.empty(), Style.EMPTY).isPresent();
    }

    /// Called by a panel for each line it draws with a hover text: at (x, y) in the panel's own pixels, scaled.
    static void note(int x, int y, int w, int h, float scale, Component line) {
        long now = System.nanoTime();
        if (now - frameNs > 20_000_000L) spots.clear();     // a new frame: last frame's spots are gone
        frameNs = now;
        if (spots.size() > 64) spots.clear();
        spots.add(new Spot(x * scale, y * scale, w * scale, h * scale, scale, line));
    }

    /// Package-private for tests: the hover text under the mouse among these spots, or null.
    static Component textAt(List<Spot> in, double mouseX, double mouseY, Font font) {
        for (Spot s : in) {
            if (mouseX < s.x() || mouseX >= s.x() + s.w() || mouseY < s.y() - 1 || mouseY >= s.y() + s.h()) continue;
            // the whole line answers, so the small "+3" is as easy to hover as the bait
            Component[] found = {null};
            s.line().visit((style, str) -> {
                if (found[0] == null && style.getHoverEvent() instanceof HoverEvent.ShowText show) found[0] = show.value();
                return java.util.Optional.empty();
            }, Style.EMPTY);
            if (found[0] != null) return found[0];
        }
        return null;
    }

    /// Draws the tooltip of the line under the mouse, if any. Hooked after every screen's own drawing.
    public static void render(GuiGraphics g, int mouseX, int mouseY) {
        if (spots.isEmpty() || System.nanoTime() - frameNs > FRESH_NS) return;
        Font font = Minecraft.getInstance().font;
        Component text = textAt(spots, mouseX, mouseY, font);
        if (text == null) return;
        List<ClientTooltipComponent> parts = new ArrayList<>();
        for (var line : font.split(text, 220)) parts.add(ClientTooltipComponent.create(line));
        g.nextStratum();
        g.renderTooltip(font, parts, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
    }
}
