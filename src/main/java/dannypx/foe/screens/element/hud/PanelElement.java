package dannypx.foe.screens.element.hud;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.LoadingHandler;
import dannypx.foe.helper.HudLayout;
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
        // Give the editor a sensible starting size. The HUD panels are created during client
        // init, before Minecraft has a font, so only measure when one exists (render() keeps the
        // size current afterwards).
        Font font = Minecraft.getInstance().font;
        if (font != null) this.updateSize(this.buildLines(), font);
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

    /// Every line centred in the panel (the warning box uses this).
    protected boolean centerText() {
        return false;
    }

    /// An item box drawn beside the text. An empty stack draws an empty box (the "nothing here" state).
    public record PanelIcon(net.minecraft.world.item.ItemStack stack, int borderColor) {}

    /// Optional item boxes beside the text, side by side (the pet HUD shows the pet and its held item).
    protected List<PanelIcon> icons() {
        return List.of();
    }

    /// Called just before a line is drawn, so a panel can draw something behind it.
    protected void beforeLine(GuiGraphics guiGraphics, Font font, Component line, int x, int y) {
    }

    private static final long LINES_REFRESH_NS = 50_000_000L;
    private List<Component> cachedLines = null;
    private long cachedAtNs = 0L;
    private int cachedOuterWidth = 0, cachedOuterHeight = 0;

    private static final int ICON_SIZE = 18;
    private static final int ICON_GAP = 3;

    private int iconSpace() {
        int n = this.icons().size();
        return n == 0 ? 0 : n * ICON_SIZE + (n - 1) * ICON_GAP + PADDING;
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
        max = Math.max(max + iconSpace(), font.width(this.title()) + 8);
        return max + PADDING * 4;
    }

    /// A panel's width only grows straight away; it shrinks once it has been narrower for a few
    /// seconds. Countdowns ("2:10 left" → "59 left") change the width every second, which made
    /// centred or right-anchored panels jump sideways.
    private static final long SHRINK_AFTER_MS = 4_000L;
    private int shownWidth = -1;
    private long narrowerSince = -1L;

    /// Package-private for tests.
    int steadyWidth(int wanted, long nowMs) {
        if (shownWidth < 0 || wanted >= shownWidth) {
            shownWidth = wanted;
            narrowerSince = -1L;
        } else if (narrowerSince < 0) {
            narrowerSince = nowMs;
        } else if (nowMs - narrowerSince >= SHRINK_AFTER_MS) {
            shownWidth = wanted;
            narrowerSince = -1L;
        }
        return shownWidth;
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

        // The text is rebuilt 20 times a second (as often as the game updates), not every frame:
        // at high frame rates building and measuring every panel's lines each frame cost a lot.
        long nowNs = System.nanoTime();
        if (cachedLines == null || nowNs - cachedAtNs >= LINES_REFRESH_NS) {
            cachedLines = this.buildLines();
            cachedOuterWidth = outerWidth(cachedLines, font);
            cachedOuterHeight = outerHeight(cachedLines, font);
            cachedAtNs = nowNs;
        }
        List<Component> lines = cachedLines;
        int steadyWidth = steadyWidth(cachedOuterWidth, System.currentTimeMillis());
        float scale = scale();
        this.width = Math.round(steadyWidth * scale);
        this.height = Math.round(cachedOuterHeight * scale);
        if (lines.isEmpty()) return;

        int screenWidth = (int) (minecraft.getWindow().getGuiScaledWidth() / scale);
        int screenHeight = (int) (minecraft.getWindow().getGuiScaledHeight() / scale);
        int outerWidth = steadyWidth;
        int outerHeight = cachedOuterHeight;

        boolean right = alignment == Alignment.TOP_RIGHT || alignment == Alignment.BOTTOM_RIGHT || alignment == Alignment.RIGHT;
        boolean bottom = alignment == Alignment.BOTTOM_LEFT || alignment == Alignment.BOTTOM_RIGHT || alignment == Alignment.BOTTOM;

        int anchorX = right ? screenWidth - Math.round(screenWidth * xPos) : Math.round(screenWidth * xPos);
        int anchorY = bottom ? screenHeight - Math.round(screenHeight * yPos) : Math.round(screenHeight * yPos);
        // Top/bottom-centre anchors keep the box centred on its spot, so it grows evenly both ways.
        boolean centered = alignment == Alignment.TOP || alignment == Alignment.BOTTOM || alignment == Alignment.CENTER;
        int outerX = centered ? anchorX - outerWidth / 2 : right ? anchorX - outerWidth : anchorX;
        int outerY = bottom ? anchorY - outerHeight : anchorY;

        // The panel stays exactly where you put it (only kept on screen). Panels don't push each
        // other around any more: they may overlap if that's how you place them.
        HudLayout.Rect wanted = new HudLayout.Rect(Math.round(outerX * scale), Math.round(outerY * scale),
                Math.round(outerWidth * scale), Math.round(outerHeight * scale));
        HudLayout.Rect placed = HudLayout.clamp(wanted, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        outerX = Math.round(placed.x() / scale);
        outerY = Math.round(placed.y() / scale);

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

        // Icon boxes on the anchored side, text beside them.
        List<PanelIcon> icons = this.icons();
        int iconSpace = iconSpace();
        int textLeft = contentLeft;
        int textRightEdge = contentRight;
        if (iconSpace > 0) {
            int iconsWidth = iconSpace - PADDING;
            int iconBoxY = top + PADDING - 1 + (innerHeight + lineHeight - ICON_SIZE) / 2 - 2;
            int x = right ? contentRight - iconsWidth : contentLeft;
            // The frames are drawn again over the items afterwards: a big item model (some pets)
            // can otherwise cover the frame's edge.
            List<int[]> frames = new java.util.ArrayList<>();
            for (PanelIcon icon : icons) {
                guiGraphics.renderOutline(x, iconBoxY, ICON_SIZE, ICON_SIZE, icon.borderColor());
                if (icon.stack() == null || icon.stack().isEmpty()) {
                    // Empty state: darker inside and a dash.
                    guiGraphics.fill(x + 1, iconBoxY + 1, x + ICON_SIZE - 1, iconBoxY + ICON_SIZE - 1, 0x55000000);
                    guiGraphics.drawString(font, "-", x + (ICON_SIZE - font.width("-")) / 2, iconBoxY + (ICON_SIZE - font.lineHeight) / 2 + 1, 0xFF777777, false);
                } else {
                    guiGraphics.renderItem(icon.stack(), x + 1, iconBoxY + 1);
                }
                frames.add(new int[]{x, iconBoxY, icon.borderColor()});
                x += ICON_SIZE + ICON_GAP;
            }
            if (!frames.isEmpty()) {
                guiGraphics.nextStratum();
                for (int[] f : frames) guiGraphics.renderOutline(f[0], f[1], ICON_SIZE, ICON_SIZE, f[2]);
            }
            if (right) textRightEdge -= iconSpace;
            else textLeft += iconSpace;
        }

        boolean textRight = right && alignTextToAnchor();
        int y = top + PADDING;
        for (Component line : lines) {
            int x = centerText() ? textLeft + (textRightEdge - textLeft - font.width(line)) / 2
                    : textRight ? textRightEdge - font.width(line) : textLeft;
            this.beforeLine(guiGraphics, font, line, x, y);
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
