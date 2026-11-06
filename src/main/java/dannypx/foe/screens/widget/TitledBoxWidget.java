package dannypx.foe.screens.widget;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * A small box with a title at the top, drawn behind a group of buttons (the vault's Bait Sorter
 * and Pet Sorter). Added before its buttons so it renders under them; it takes no clicks.
 */
public class TitledBoxWidget extends AbstractWidget {
    private static final int BACKGROUND = 0xC0101418;
    private static final int BORDER = 0xFF5A6770;
    private static final int TITLE = 0xFFFFFFFF;

    public TitledBoxWidget(int x, int y, int width, int height, Component title) {
        super(x, y, width, height, title);
        this.active = false;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BACKGROUND);
        g.fill(x + 1, y, x + w - 1, y + 1, BORDER);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, BORDER);
        g.fill(x, y + 1, x + 1, y + h - 1, BORDER);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, BORDER);
        Component title = getMessage().copy().withStyle(ChatFormatting.BOLD);
        var font = Minecraft.getInstance().font;
        g.drawString(font, title, x + w / 2 - font.width(title) / 2, y + 5, TITLE, true);
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubled) {
        return false;
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {}
}
