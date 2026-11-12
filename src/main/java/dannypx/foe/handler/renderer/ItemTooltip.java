package dannypx.foe.handler.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

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
        if (stack == null || stack.isEmpty()) return;
        List<Component> lines = Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
        if (lines.isEmpty()) return;
        List<ClientTooltipComponent> parts = new ArrayList<>();
        for (Component line : lines) parts.add(ClientTooltipComponent.create(line.getVisualOrderText()));
        stack.getTooltipImage().ifPresent(image -> parts.add(Math.min(1, parts.size()), ClientTooltipComponent.create(image)));
        g.nextStratum();
        g.renderTooltip(font, parts, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, stack.get(DataComponents.TOOLTIP_STYLE));
    }
}
