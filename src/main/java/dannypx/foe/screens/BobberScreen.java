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
    private static final int COLUMNS = 4;
    private static final int BUTTON_W = 108;
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
        int rows = (all.length + COLUMNS - 1) / COLUMNS;
        int gridW = COLUMNS * BUTTON_W + (COLUMNS - 1) * GAP;
        int gridH = rows * BUTTON_H + (rows - 1) * GAP;
        int previewH = PREVIEW_PX * 6 + 18;
        int left = (width - gridW) / 2;
        gridTop = Math.max(40, (height - gridH - previewH - 36) / 2 + 20);
        gridBottom = gridTop + gridH;

        for (int i = 0; i < all.length; i++) {
            BobberStyle style = all[i];
            int x = left + (i % COLUMNS) * (BUTTON_W + GAP);
            int y = gridTop + (i / COLUMNS) * (BUTTON_H + GAP);
            Button button = Button.builder(Component.literal(style.displayName()), b -> pick(style))
                    .bounds(x, y, BUTTON_W, BUTTON_H).build();
            this.addRenderableWidget(button);
            options.add(new Option(style, button));
        }

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(width / 2 - 50, gridBottom + previewH + 8, 100, BUTTON_H).build());
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
        for (Option option : options) {
            Button b = option.button();
            int sx = b.getX() + 5, sy = b.getY() + (b.getHeight() - SWATCH) / 2;
            guiGraphics.fill(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, 0xFF000000);
            drawBody(guiGraphics, option.style(), sx, sy, SWATCH, SWATCH);
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
        guiGraphics.fill(bx - 1, by - 1, bx + body + 1, by + body + 1, 0xFF000000); // outline
        drawBody(guiGraphics, current, bx, by, body, body);
        int hy = by + body;
        guiGraphics.fill(bx + p, hy, bx + 2 * p, hy + 2 * p, hookColor);          // shank
        guiGraphics.fill(bx, hy + 2 * p, bx + 2 * p, hy + 3 * p, hookColor);      // bend
        guiGraphics.fill(bx - p, hy + p, bx, hy + 2 * p, hookColor);              // point
    }

    /// The side of the bobber: outer colour, band across the middle, outer colour.
    private static void drawBody(GuiGraphics g, BobberStyle style, int x, int y, int w, int h) {
        int third = Math.max(1, Math.round(h / 3f));
        g.fill(x, y, x + w, y + h, 0xFF000000 | style.outer());
        g.fill(x, y + third, x + w, y + h - third, 0xFF000000 | style.band());
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
