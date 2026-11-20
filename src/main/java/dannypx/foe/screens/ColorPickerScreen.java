package dannypx.foe.screens;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * A colour wheel over the settings: around the wheel is the hue, out from the middle how strong it is, and the bar
 * beside it how bright. Below, a few ready colours, the colour as hex ("#FF77CC") to type in, Default (the setting's
 * own colour) and Done. The colour is used as you pick it, so you see it on the HUD straight away.
 */
public class ColorPickerScreen extends Screen {
    private static final int WHEEL = 100, TEX = 128, BAR_W = 10, PANEL_W = 236, PANEL_H = 176;
    private static final Identifier WHEEL_TEX = Identifier.fromNamespaceAndPath(dannypx.foe.FishOnMCExtras.MOD_ID, "color_wheel");
    private static boolean made = false;
    /// Ready colours, one click each.
    private static final int[] PRESETS = {0x00ECB0, 0x55FF55, 0x55FFFF, 0x5599FF, 0xB98CFF, 0xFF77CC, 0xFF5555, 0xFF9933, 0xFFAA00, 0xFFFF55, 0xFFFFFF, 0xAAAAAA};

    private final Screen parent;
    private final String what;
    /// The colour as the setting keeps it ("#RRGGBB", or "" for Default), and what to do with a new one.
    private final Consumer<String> set;
    private final Runnable done;
    /// The colour the setting has when it's left at Default (shown then).
    private final int own;
    private float h, s, v;
    private boolean isDefault;
    private EditBox hex;
    private int px, py, wx, wy, bx;
    /// What's being dragged: 1 the wheel, 2 the brightness.
    private int drag = 0;

    public ColorPickerScreen(Screen parent, String what, String current, int own, Consumer<String> set, Runnable done) {
        super(Component.literal(what));
        this.parent = parent;
        this.what = what;
        this.set = set;
        this.done = done;
        this.own = own;
        int rgb = dannypx.foe.screens.element.hud.GameXpBar.parse(current);
        isDefault = rgb < 0;
        fromRgb(isDefault ? own : rgb);
    }

    private void fromRgb(int rgb) {
        float[] hsv = java.awt.Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
        h = hsv[0];
        s = hsv[1];
        v = hsv[2];
    }

    private int rgb() {
        return java.awt.Color.HSBtoRGB(h, s, v) & 0xFFFFFF;
    }

    /// The wheel's picture, made once: the hue around, white in the middle, full strength at the edge.
    private static void makeWheel() {
        if (made) return;
        NativeImage img = new NativeImage(TEX, TEX, true);
        float r = TEX / 2f;
        for (int y = 0; y < TEX; y++) {
            for (int x = 0; x < TEX; x++) {
                float dx = x + 0.5f - r, dy = y + 0.5f - r;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / r;
                if (d > 1f) { img.setPixel(x, y, 0); continue; }
                float hue = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1) % 1.0);
                int c = java.awt.Color.HSBtoRGB(hue, d, 1f) & 0xFFFFFF;
                // a soft edge, a pixel wide
                int a = d > 1f - 1f / r ? Math.round((1f - d) * r * 255) : 255;
                img.setPixel(x, y, Math.max(0, Math.min(255, a)) << 24 | c);
            }
        }
        net.minecraft.client.Minecraft.getInstance().getTextureManager().register(WHEEL_TEX, new DynamicTexture(() -> "FOE+ colour wheel", img));
        made = true;
    }

    @Override
    protected void init() {
        makeWheel();
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;
        wx = px + 12;
        wy = py + 26;
        bx = wx + WHEEL + 10;
        hex = new EditBox(font, bx + BAR_W + 12, wy + 46, 80, 14, Component.literal("Hex"));
        hex.setMaxLength(7);
        hex.setValue(hexOf(rgb()));
        hex.setResponder(t -> {
            int c = dannypx.foe.screens.element.hud.GameXpBar.parse(t);
            if (c < 0 || c == rgb()) return;
            fromRgb(c);
            apply(false);
        });
        addRenderableWidget(hex);
    }

    private static String hexOf(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb);
    }

    /// Uses the colour now (or Default), and shows its hex unless that's being typed.
    private void apply(boolean updateHex) {
        isDefault = false;
        set.accept(hexOf(rgb()));
        if (updateHex && hex != null) hex.setValue(hexOf(rgb()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        parent.render(g, -1, -1, delta);
        g.nextStratum();
        g.fill(0, 0, width, height, 0x90000000);
        g.fill(px - 1, py - 1, px + PANEL_W + 1, py + PANEL_H + 1, 0xFF3E6C94);
        g.fill(px, py, px + PANEL_W, py + PANEL_H, 0xFF0D1A28);
        g.drawString(font, font.plainSubstrByWidth(what, PANEL_W - 20), px + 10, py + 9, 0xFFFFFFFF, true);

        // the wheel, as bright as picked
        int grey = Math.round(v * 255);
        g.blit(RenderPipelines.GUI_TEXTURED, WHEEL_TEX, wx, wy, 0f, 0f, WHEEL, WHEEL, TEX, TEX, TEX, TEX, 0xFF000000 | grey << 16 | grey << 8 | grey);
        double ang = h * 2 * Math.PI;
        int mx = wx + WHEEL / 2 + (int) Math.round(Math.cos(ang) * s * WHEEL / 2);
        int my = wy + WHEEL / 2 + (int) Math.round(Math.sin(ang) * s * WHEEL / 2);
        g.renderOutline(mx - 3, my - 3, 7, 7, 0xFF000000);
        g.renderOutline(mx - 2, my - 2, 5, 5, 0xFFFFFFFF);

        // the brightness: the colour at full brightness down to black
        int full = java.awt.Color.HSBtoRGB(h, s, 1f) | 0xFF000000;
        g.fillGradient(bx, wy, bx + BAR_W, wy + WHEEL, full, 0xFF000000);
        g.renderOutline(bx - 1, wy - 1, BAR_W + 2, WHEEL + 2, 0xFF22405E);
        int ky = wy + Math.round((1 - v) * WHEEL);
        g.fill(bx - 2, ky - 1, bx + BAR_W + 2, ky + 1, 0xFFFFFFFF);

        // what it looks like, and its hex
        int sx = bx + BAR_W + 12;
        g.fill(sx, wy, sx + 80, wy + 22, 0xFF000000 | rgb());
        g.renderOutline(sx - 1, wy - 1, 82, 24, 0xFF22405E);
        // as an XP bar: the filled part in the colour, the rest grey
        g.fill(sx, wy + 30, sx + 50, wy + 32, 0xFF000000 | rgb());
        g.fill(sx + 50, wy + 30, sx + 80, wy + 32, 0xFF555555);
        if (isDefault) g.drawString(font, "Default", sx + 4, wy + 7, 0xFF000000, false);
        g.drawString(font, "Hex", sx, wy + 37, 0xFF9FB3C8, false);

        // ready colours
        int ry = wy + WHEEL + 10;
        for (int i = 0; i < PRESETS.length; i++) {
            int x = wx + i * 18;
            boolean hot = mouseX >= x && mouseX < x + 14 && mouseY >= ry && mouseY < ry + 14;
            g.fill(x - 1, ry - 1, x + 15, ry + 15, hot ? 0xFFFFFFFF : 0xFF22405E);
            g.fill(x, ry, x + 14, ry + 14, 0xFF000000 | PRESETS[i]);
        }

        // Default and Done
        int by = py + PANEL_H - 22;
        button(g, px + 10, by, 70, "Default", mouseX, mouseY);
        button(g, px + PANEL_W - 80, by, 70, "Done", mouseX, mouseY);
        super.render(g, mouseX, mouseY, delta);
    }

    private void button(GuiGraphics g, int x, int y, int w, String text, int mouseX, int mouseY) {
        boolean hot = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
        g.fill(x - 1, y - 1, x + w + 1, y + 17, hot ? 0xFF3FA9F5 : 0xFF22405E);
        g.fill(x, y, x + w, y + 16, hot ? 0xFF1B3450 : 0xFF14263A);
        g.drawCenteredString(font, text, x + w / 2, y + 4, 0xFFFFFFFF);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // the settings stay behind it, drawn in render
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (super.mouseClicked(event, doubleClick)) return true;
        if (hex != null) hex.setFocused(false);
        setFocused(null);
        if (inWheel(mx, my)) { drag = 1; pickWheel(mx, my); return true; }
        if (mx >= bx - 2 && mx < bx + BAR_W + 2 && my >= wy - 2 && my < wy + WHEEL + 2) { drag = 2; pickBar(my); return true; }
        int ry = wy + WHEEL + 10;
        for (int i = 0; i < PRESETS.length; i++) {
            int x = wx + i * 18;
            if (mx >= x && mx < x + 14 && my >= ry && my < ry + 14) { fromRgb(PRESETS[i]); apply(true); return true; }
        }
        int by = py + PANEL_H - 22;
        if (my >= by && my < by + 16) {
            if (mx >= px + 10 && mx < px + 80) {
                isDefault = true;
                set.accept("");
                fromRgb(own);
                hex.setValue(hexOf(own));
                isDefault = true;
                return true;
            }
            if (mx >= px + PANEL_W - 80 && mx < px + PANEL_W - 10) { onClose(); return true; }
        }
        // a click outside the panel closes it
        if (mx < px || mx >= px + PANEL_W || my < py || my >= py + PANEL_H) { onClose(); return true; }
        return true;
    }

    private boolean inWheel(double mx, double my) {
        double dx = mx - (wx + WHEEL / 2.0), dy = my - (wy + WHEEL / 2.0);
        return dx * dx + dy * dy <= (WHEEL / 2.0 + 2) * (WHEEL / 2.0 + 2);
    }

    private void pickWheel(double mx, double my) {
        double dx = mx - (wx + WHEEL / 2.0), dy = my - (wy + WHEEL / 2.0);
        h = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1) % 1.0);
        s = (float) Math.min(1.0, Math.sqrt(dx * dx + dy * dy) / (WHEEL / 2.0));
        if (v < 0.05f) v = 1f;      // picking on a black wheel: bright again
        apply(true);
    }

    private void pickBar(double my) {
        v = (float) Math.max(0, Math.min(1, 1 - (my - wy) / WHEEL));
        apply(true);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (drag == 1) { pickWheel(event.x(), event.y()); return true; }
        if (drag == 2) { pickBar(event.y()); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        drag = 0;
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        done.run();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
