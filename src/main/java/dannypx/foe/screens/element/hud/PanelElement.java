package dannypx.foe.screens.element.hud;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.LoadingHandler;
import dannypx.foe.screens.element.Element;
import dannypx.foe.type.Alignment;
import dannypx.foe.type.PanelTheme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Text panel drawn in the FishOnMC-Extras-Collab (FOE) style: a translucent box with a themed
 * border and the title set into the top edge. Layout and border pieces ported from FOECollab's
 * QuestHud / Theming (AGPL-3.0).
 * <p>
 * Subclasses supply the title, the lines, and their config values; this class handles anchoring,
 * scaling and drawing, and keeps {@link #width}/{@link #height} current for the Move Elements screen.
 */
public abstract class PanelElement extends Element {
    private static final int PADDING = 8;
    private static final int LINE_SPACING = 2;

    protected PanelElement(float xPos, float yPos, Alignment alignment, Component message, boolean isCopy) {
        super(120, 60, xPos, yPos, alignment, message, isCopy);
        // Give the editor a sensible starting size.
        this.updateSize(this.buildLines(), Minecraft.getInstance().font);
    }

    //region Subclass hooks
    protected abstract Component title();

    /// The panel's lines; return an empty list to hide the panel.
    protected abstract List<Component> lines();

    /// Shown in the Move Elements screen when there is no live data yet.
    protected abstract List<Component> previewLines();

    protected abstract boolean enabled();

    protected abstract float configX();

    protected abstract float configY();

    protected abstract Alignment configAlignment();

    protected abstract float scale();

    protected abstract int opacityPercent();

    /// Right-aligned text when anchored on the right, like FOE.
    protected boolean alignTextToAnchor() {
        return true;
    }
    //endregion

    private List<Component> buildLines() {
        List<Component> lines = new ArrayList<>();
        if (isCopy || LoadingHandler.instance().isLoadingDone()) lines.addAll(this.lines());
        if (lines.isEmpty() && isCopy) lines.addAll(this.previewLines());
        // Trim blank lines at the ends.
        while (!lines.isEmpty() && lines.getFirst().getString().isBlank()) lines.removeFirst();
        while (!lines.isEmpty() && lines.getLast().getString().isBlank()) lines.removeLast();
        if (!lines.isEmpty() && Configs.hudConfig.panelTheme.get() == PanelTheme.OFF) {
            lines.addFirst(this.title().copy().withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private static int lineHeight(Font font) {
        return font.lineHeight + LINE_SPACING;
    }

    /// Outer size of the panel including the border, before scaling.
    private int outerWidth(List<Component> lines, Font font) {
        int max = lines.stream().mapToInt(font::width).max().orElse(0);
        max = Math.max(max, font.width(this.title()) + 8);
        return max + PADDING * 4;
    }

    private int outerHeight(List<Component> lines, Font font) {
        return (Math.max(lines.size(), 1) - 1) * lineHeight(font) + PADDING * 5;
    }

    private void updateSize(List<Component> lines, Font font) {
        this.width = Math.round(outerWidth(lines, font) * scale());
        this.height = Math.round(outerHeight(lines, font) * scale());
    }

    @Override
    public void render(GuiGraphics guiGraphics) {
        if (!enabled()) return;

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        if (!isCopy) {
            xPos = configX();
            yPos = configY();
            alignment = configAlignment();
        }

        List<Component> lines = this.buildLines();
        this.updateSize(lines, font);
        if (lines.isEmpty()) return;

        float scale = scale();
        int screenWidth = (int) (minecraft.getWindow().getGuiScaledWidth() / scale);
        int screenHeight = (int) (minecraft.getWindow().getGuiScaledHeight() / scale);
        int outerWidth = outerWidth(lines, font);
        int outerHeight = outerHeight(lines, font);

        boolean right = alignment == Alignment.TOP_RIGHT || alignment == Alignment.BOTTOM_RIGHT || alignment == Alignment.RIGHT;
        boolean bottom = alignment == Alignment.BOTTOM_LEFT || alignment == Alignment.BOTTOM_RIGHT || alignment == Alignment.BOTTOM;

        int anchorX = right ? screenWidth - Math.round(screenWidth * xPos) : Math.round(screenWidth * xPos);
        int anchorY = bottom ? screenHeight - Math.round(screenHeight * yPos) : Math.round(screenHeight * yPos);
        int outerX = right ? anchorX - outerWidth : anchorX;
        int outerY = bottom ? anchorY - outerHeight : anchorY;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(scale, scale);
        this.drawPanel(guiGraphics, font, lines, outerX, outerY, outerWidth, right);
        guiGraphics.pose().popMatrix();
    }

    private void drawPanel(GuiGraphics guiGraphics, Font font, List<Component> lines, int outerX, int outerY, int outerWidth, boolean right) {
        int lineHeight = lineHeight(font);
        int contentWidth = outerWidth - PADDING * 4;

        // Same geometry as FOE: the border pieces sit PADDING outside the filled box.
        int top = outerY + PADDING;
        int contentLeft = outerX + PADDING * 2;
        int contentRight = contentLeft + contentWidth;
        int boxLeft = contentLeft - PADDING;
        int boxRight = contentRight + PADDING;
        int innerHeight = (lines.size() - 1) * lineHeight;

        int bgAlpha = Math.round(opacityPercent() / 100f * 255f) << 24;
        guiGraphics.fill(boxLeft, top, boxRight, top + innerHeight + PADDING * 3, bgAlpha);

        PanelTheme theme = Configs.hudConfig.panelTheme.get();
        if (theme != PanelTheme.OFF) {
            int white = 0xFFFFFFFF;
            int bottomY = top + PADDING * 2 + innerHeight;
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "top_left"), boxLeft - PADDING, top - PADDING, 16, 16, white);
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "top_right"), boxRight - PADDING, top - PADDING, 16, 16, white);
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "bottom_left"), boxLeft - PADDING, bottomY, 16, 16, white);
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "bottom_right"), boxRight - PADDING, bottomY, 16, 16, white);
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "left"), boxLeft - PADDING, top + PADDING, 16, innerHeight + PADDING, white);
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "right"), boxRight - PADDING, top + PADDING, 16, innerHeight + PADDING, white);
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "top"), contentLeft, top - PADDING, contentWidth, 16, white);
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "bottom"), contentLeft, bottomY, contentWidth, 16, white);

            Component title = this.title();
            int titleWidth = font.width(title);
            int titleX = contentLeft + (contentWidth - titleWidth) / 2;
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "text_left"), titleX - 16, top - PADDING, 16, 16, white);
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "text_middle"), titleX, top - PADDING, titleWidth, 16, white);
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "text_right"), titleX + titleWidth, top - PADDING, 16, 16, white);
            guiGraphics.drawString(font, title, titleX, top - font.lineHeight / 2 - 1, white, false);
        }

        boolean textRight = right && alignTextToAnchor();
        int y = top + PADDING;
        for (Component line : lines) {
            int x = textRight ? contentRight - font.width(line) : contentLeft;
            guiGraphics.drawString(font, line, x, y, 0xFFFFFFFF, true);
            y += lineHeight;
        }
    }

    private static Identifier sprite(PanelTheme theme, String piece) {
        return Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "themes/" + theme.id + "/container/" + piece);
    }

    //region Shared text helpers
    protected static Component label(String smallCapsLabel) {
        return Component.literal(smallCapsLabel).withStyle(ChatFormatting.GRAY);
    }

    protected static Component join(Component... parts) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        for (Component part : parts) out.append(part);
        return out;
    }

    protected static String formatTime(long ms) {
        long totalSeconds = ms / 1000L;
        return String.format("%02d:%02d:%02d", totalSeconds / 3600, (totalSeconds / 60) % 60, totalSeconds % 60);
    }

    protected static String formatMinSec(long ms) {
        long totalSeconds = ms / 1000L;
        return String.format("%02d:%02d", (totalSeconds / 60) % 60, totalSeconds % 60);
    }
    //endregion
}
