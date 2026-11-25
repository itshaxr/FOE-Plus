package dannypx.foe.screens;

import dannypx.foe.config.Configs;
import dannypx.foe.type.BobberStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Pick your 3D bobber's colours: a grid of presets like the + Colour menu, each with a little
 * bobber swatch, and a bigger preview of the one you have on.
 */
public class BobberScreen extends Screen {
    private static final int MAX_COLUMNS = 6;
    private static final int BUTTON_W = 106;
    private static final int BUTTON_H = 20;
    private static final int GAP = 4;
    private static final int SWATCH = 10;
    /// Preview bobber: body size and pixel size of one texture pixel.
    private static final int PREVIEW_PX = 6;

    private final Screen parent;
    private final List<Option> options = new ArrayList<>();
    private int gridTop, gridBottom;

    private record Option(BobberStyle style, Button button) {}

    public BobberScreen(Screen parent) {
        super(Component.literal("Bobber"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        options.clear();
        BobberStyle[] all = BobberStyle.values();
        // as many columns as fit (up to six), so all of them show without scrolling
        int COLUMNS = Math.max(2, Math.min(MAX_COLUMNS, (width - 20 + GAP) / (BUTTON_W + GAP)));
        int rows = (all.length + COLUMNS - 1) / COLUMNS;
        int gridW = COLUMNS * BUTTON_W + (COLUMNS - 1) * GAP;
        int gridH = rows * BUTTON_H + (rows - 1) * GAP;
        int previewH = PREVIEW_PX * 6 + 18;
        int gap = Math.max(1, Math.min(GAP, (height - 60 - previewH - 36 - rows * BUTTON_H) / Math.max(1, rows - 1)));
        gridH = rows * BUTTON_H + (rows - 1) * gap;
        int left = (width - gridW) / 2;
        gridTop = Math.max(40, (height - gridH - previewH - 36) / 2 + 20);
        gridBottom = gridTop + gridH;

        for (int i = 0; i < all.length; i++) {
            BobberStyle style = all[i];
            int x = left + (i % COLUMNS) * (BUTTON_W + GAP);
            int y = gridTop + (i / COLUMNS) * (BUTTON_H + gap);
            Button button = Button.builder(Component.literal(style.displayName()), b -> pick(style))
                    .bounds(x, y, BUTTON_W, BUTTON_H).build();
            this.addRenderableWidget(button);
            options.add(new Option(style, button));
        }

        // the shape: the block, or round like a real float (every preset works on both)
        var shape = Configs.rendererConfig.bobberShape;
        this.addRenderableWidget(Button.builder(Component.literal("Shape: ").append(Component.literal(shape.get().displayName()).withStyle(ChatFormatting.AQUA)), b -> {
                    shape.accept(shape.get().next());
                    Configs.rendererConfig.save();
                    dannypx.foe.nearby_dispatcher.types.ModUserType.announceBobber();
                    rebuildWidgets();
                }).bounds(width / 2 - 104, gridBottom + previewH + 8, 100, BUTTON_H)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Classic: the block bobber. Round: a round float, like a real one. Your colours work on both"))).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(width / 2 + 4, gridBottom + previewH + 8, 100, BUTTON_H).build());
    }

    private void pick(BobberStyle style) {
        var cfg = Configs.rendererConfig;
        cfg.bobberStyle.accept(style);
        // Picking a preset means you want the 3D bobber.
        if (!cfg.showNewFishingHook.get()) cfg.showNewFishingHook.accept(true);
        cfg.save();
        dannypx.foe.nearby_dispatcher.types.ModUserType.announceBobber();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);
        guiGraphics.drawCenteredString(font, Component.literal("ʙᴏʙʙᴇʀ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                width / 2, gridTop - 30, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(font, Component.literal("Your 3D bobber's colours, for everyone with FOE+").withStyle(ChatFormatting.GRAY),
                width / 2, gridTop - 18, 0xFFFFFFFF);

        BobberStyle current = Configs.rendererConfig.bobberStyle.get();
        boolean round = Configs.rendererConfig.bobberShape.get() == dannypx.foe.type.BobberShape.ROUND;
        for (Option option : options) {
            Button b = option.button();
            int sx = b.getX() + 5, sy = b.getY() + (b.getHeight() - SWATCH) / 2;
            if (round) {
                drawRound(guiGraphics, option.style(), sx, sy, SWATCH);
            } else {
                guiGraphics.fill(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, 0xFF000000);
                drawBody(guiGraphics, option.style(), sx, sy, SWATCH, SWATCH);
            }
            if (option.style() == current) {
                guiGraphics.renderOutline(b.getX() - 1, b.getY() - 1, b.getWidth() + 2, b.getHeight() + 2, 0xFFFFFFFF);
                guiGraphics.drawString(font, Component.literal("✔").withStyle(ChatFormatting.GREEN),
                        b.getX() + b.getWidth() - 12, b.getY() + (b.getHeight() - font.lineHeight) / 2 + 1, 0xFFFFFFFF, true);
            }
        }

        // Preview: the bobber as it floats, nub on top, hook below.
        int p = PREVIEW_PX;
        int body = p * 3;
        int py = gridBottom + 10;
        Component label = Component.literal("Preview: ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(current.displayName()).withStyle(ChatFormatting.WHITE));
        int labelW = font.width(label);
        int totalW = labelW + 8 + body;
        int lx = (width - totalW) / 2;
        int bx = lx + labelW + 8;
        int by = py + p;
        guiGraphics.drawString(font, label, lx, by + (body - font.lineHeight) / 2 + 1, 0xFFFFFFFF, true);
        int hookColor = 0xFF2C2D35;
        guiGraphics.fill(bx + p, py, bx + 2 * p, py + p, hookColor);              // nub
        if (round) {
            drawRound(guiGraphics, current, bx, by, body);
        } else {
            guiGraphics.fill(bx - 1, by - 1, bx + body + 1, by + body + 1, 0xFF000000); // outline
            drawBody(guiGraphics, current, bx, by, body, body);
        }
        int hy = by + body;
        guiGraphics.fill(bx + p, hy, bx + 2 * p, hy + 2 * p, hookColor);          // shank
        guiGraphics.fill(bx, hy + 2 * p, bx + 2 * p, hy + 3 * p, hookColor);      // bend
        guiGraphics.fill(bx - p, hy + p, bx, hy + 2 * p, hookColor);              // point
    }

    /// The side of the bobber as it really looks: its texture's front face (stored upside down, so turned back over).
    private static void drawBody(GuiGraphics g, BobberStyle style, int x, int y, int w, int h) {
        net.minecraft.resources.Identifier texture = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                dannypx.foe.FishOnMCExtras.MOD_ID, style.texturePath());
        // read from the face's bottom row up (a negative height), so it shows the right way up
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, x, y, 12, 24, w, h, 12, -12, 64, 36);
    }

    /// The round bobber: the same face, row by row, as wide as a ball is at that height, with a dark rim.
    private static void drawRound(GuiGraphics g, BobberStyle style, int x, int y, int size) {
        net.minecraft.resources.Identifier texture = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                dannypx.foe.FishOnMCExtras.MOD_ID, style.texturePath());
        float r = size / 2f, cx = x + r;
        for (int j = -1; j <= size; j++) {
            float dy = (j + 0.5f - r) / (r + 1f);
            if (Math.abs(dy) >= 1f) continue;
            int half = Math.round((float) Math.sqrt(1f - dy * dy) * (r + 1f));
            g.fill(Math.round(cx) - half, y + j, Math.round(cx) + half, y + j + 1, 0xFF000000);
        }
        for (int j = 0; j < size; j++) {
            float dy = (j + 0.5f - r) / r;
            int half = Math.round((float) Math.sqrt(Math.max(0f, 1f - dy * dy)) * r);
            if (half <= 0) continue;
            // the face's row for this height (stored upside down: its bottom row is the top)
            int row = 23 - Math.min(11, j * 12 / size);
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, Math.round(cx) - half, y + j, 12, row,
                    half * 2, 1, 12, 1, 64, 36);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
