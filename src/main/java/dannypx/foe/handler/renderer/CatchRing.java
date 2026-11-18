package dannypx.foe.handler.renderer;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.GlyphDump;
import dannypx.foe.mixin.accessor.GuiAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The catch ring: while you reel a fish in, a ring round the crosshair fills clockwise in green as the catch
 * progresses, and flashes full when it's caught.
 * <p>
 * The server draws its reel-in bar in the action bar with pictures from its fishing fonts, and picks a different
 * picture code every frame, so the codes say nothing. The pictures do: the catch progress is a thin green strip that
 * fills from the left towards a white end mark. FOE+ looks at the picture of each code (once, then remembers it) and
 * measures the strip.
 */
public final class CatchRing {
    private CatchRing() {}

    /// Per font and code: the strip's progress (0-1), or -1 when the picture isn't the progress strip.
    private static final Map<String, Float> strips = new HashMap<>();
    static { dannypx.foe.handler.logic.MemoryGuard.register("catchRingStrips", strips::size, strips::clear); }
    private static float shown = 0f, last = 0f;
    private static long caughtAt = 0L;
    private static boolean wasOn = false;

    /// Package-private for tests: the progress a picture shows ({w, h, ARGB...}), or -1 when it isn't the strip: a
    /// band at most 6 pixels tall holding green pixels, with the (nearly see-through) end mark on the right.
    static float stripProgress(int[] px) {
        if (px == null) return -1;
        int w = px[0], h = px[1];
        int minY = h, maxY = -1, minGreenX = w, endX = -1;
        boolean[] greenCol = new boolean[w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int c = px[2 + y * w + x];
            if ((c >>> 24) == 0) continue;            // the end mark is all but see-through (alpha 1)
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
            if (g > r + 40 && g > b + 40) { greenCol[x] = true; minGreenX = Math.min(minGreenX, x); }
            else endX = Math.max(endX, x);
        }
        if (maxY < 0 || maxY - minY > 6 || minGreenX >= w || endX <= minGreenX) return -1;
        int green = 0;
        for (boolean b : greenCol) if (b) green++;
        return Math.min(1f, green / (float) (endX - minGreenX));
    }

    private static Identifier fontOf(Style style) {
        return style.getFont() instanceof FontDescription.Resource r ? r.id() : null;
    }

    /// The catch progress in the action bar (0-1), 0 when the reel-in bar is up with an empty strip, or -1 when
    /// there's no reel-in bar.
    static float progress(Component bar) {
        if (bar == null) return -1;
        float[] found = {-1};
        boolean[] fishingFont = {false};
        bar.visit((style, text) -> {
            Identifier font = fontOf(style);
            if (font == null || !font.getPath().startsWith("fishing")) return Optional.empty();
            fishingFont[0] = true;
            text.codePoints().forEach(cp -> {
                if (found[0] >= 0) return;
                String key = font + ":" + cp;
                Float p = strips.get(key);
                if (p == null) {
                    p = stripProgress(GlyphDump.pixels(font, cp));
                    if (strips.size() > 20_000) strips.clear();
                    strips.put(key, p);
                }
                if (p >= 0) found[0] = p;
            });
            return Optional.empty();
        }, Style.EMPTY);
        return found[0] >= 0 ? found[0] : fishingFont[0] ? 0f : -1f;
    }

    private static long shownAt = 0L;

    /// Its size (gui pixels): outer radius and thickness.
    static final float RADIUS = 7f, THICKNESS = 2f;

    public static void render(GuiGraphics g) {
        if (!Configs.hudConfig.catchRingOn.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        GuiAccessor gui = (GuiAccessor) mc.gui;
        float p = gui.getOverlayMessageTime() > 0 && mc.player.fishing != null ? progress(gui.getOverlayMessageString()) : -1f;
        long now = System.currentTimeMillis();
        if (p >= 0) {
            if (!wasOn) { shown = 0f; shownAt = now; }
            wasOn = true;
            last = p;
            shown += (p - shown) * 0.25f;
            float in = Math.min(1f, (now - shownAt) / 160f);
            float pop = 0.6f + 0.4f * easeOutBack(in);
            draw(g, shown, in, pop, now, 0f);
            return;
        }
        if (wasOn) {
            wasOn = false;
            if (last >= 0.75f) caughtAt = now;      // the bar went with the strip nearly full: caught
        }
        long since = now - caughtAt;
        if (caughtAt > 0 && since < 650) {
            float t = since / 650f;
            draw(g, 1f, 1f - t * t, 1f + 0.12f * (float) Math.sin(Math.min(1f, t * 3f) * Math.PI), now, t);
        }
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    /// The ring left of the crosshair, `fraction` filled clockwise from the top, at `alpha`, `size` times its size.
    /// `caught` (0-1, 0 while reeling): the flash and the ripple spreading out after the catch.
    static void draw(GuiGraphics g, float fraction, float alpha, float size, long now, float caught) {
        Minecraft mc = Minecraft.getInstance();
        draw(g, mc.getWindow().getGuiScaledWidth() * centreX(), mc.getWindow().getGuiScaledHeight() * centreY(),
                fraction, alpha, size * Configs.hudConfig.catchRingScale.get(), now, caught);
    }

    /// Where its centre is, as a fraction of the screen (from the settings).
    public static float centreX() { return Math.max(0f, Math.min(100f, Configs.hudConfig.catchRingX.get())) / 100f; }
    public static float centreY() { return Math.max(0f, Math.min(100f, Configs.hudConfig.catchRingY.get())) / 100f; }

    /// How far it reaches from its centre at this size (gui pixels), glow and ripple included: for its box in
    /// Move HUD Elements.
    public static int reach(float size) {
        return (int) Math.ceil((RADIUS + 2.5f) * size) + 1;
    }

    /// A preview for Move HUD Elements: filling round and round.
    public static void preview(GuiGraphics g, double centreX, double centreY, float size) {
        long now = System.currentTimeMillis();
        float f = (now % 2400L) / 2000f;
        draw(g, centreX, centreY, Math.min(1f, f), 1f, size, now, f > 1f ? (f - 1f) / 0.2f * 0.6f : 0f);
    }

    /// The ring centred on (`centreX`, `centreY`) in gui pixels, drawn pixel by pixel on the GUI's own pixel grid
    /// like the rest of the game's art: a dark outline, the track, and the fill in the server's greens with a light
    /// edge on top, a bright pixel at its head. A catch flashes it white and sends a pixel ring out.
    static void draw(GuiGraphics g, double centreX, double centreY, float fraction, float alpha, float size, long now, float caught) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        if (a <= 4) return;
        int cx = (int) Math.round(centreX), cy = (int) Math.round(centreY);
        double outer = Math.max(4, Math.round(RADIUS * size)), inner = Math.max(2, outer - Math.max(2, Math.round(THICKNESS * size)));
        double end = fraction * Math.PI * 2;
        double headAng = end;
        float flash = caught > 0 ? Math.max(0f, 1f - caught * 3f) : 0f;
        int ripple = caught > 0 ? (int) Math.round(outer + 1 + caught * 6) : -1;
        int reach = (int) Math.max(outer + 2, ripple + 1);
        for (int dy = -reach; dy < reach; dy++) {
            int runStart = 0, runColor = 0;
            for (int dx = -reach; dx <= reach; dx++) {
                int color = 0;
                if (dx < reach) {
                    double fx = dx + 0.5, fy = dy + 0.5, d = Math.sqrt(fx * fx + fy * fy);
                    double ang = Math.atan2(fx, -fy);
                    if (ang < 0) ang += Math.PI * 2;
                    if (d <= outer && d >= inner) {
                        boolean filled = ang <= end;
                        if (filled) {
                            // lighter on the ring's top-left edge, deeper on its bottom-right, like the game's items
                            boolean light = d > outer - 1 && fy < 0 || d < inner + 1 && fy > 0;
                            int base = light ? 0x7CF07C : 0x3FCB5A;
                            double headGap = Math.abs(ang - headAng);
                            if (fraction > 0.02f && fraction < 0.999f && caught == 0f && headGap * d < 1.2) base = 0xD8FFD8;
                            color = a << 24 | toward(base, 0xFFFFFF, flash);
                        } else color = (a * 0xC0 / 255) << 24 | 0x2A2A2A;
                    } else if (d > outer && d <= outer + 1 || d < inner && d >= inner - 1) {
                        color = (a * 0xD0 / 255) << 24 | 0x0B0B0B;      // the outline
                    } else if (ripple > 0 && d > ripple - 1 && d <= ripple) {
                        color = ((int) (a * 0.7f * (1 - caught))) << 24 | 0x7CF07C;
                    }
                }
                if (color != runColor) {
                    if (runColor != 0) g.fill(cx + runStart, cy + dy, cx + dx, cy + dy + 1, runColor);
                    runStart = dx;
                    runColor = color;
                }
            }
        }
    }

    /// `rgb` moved `t` (0-1) of the way to `to`.
    private static int toward(int rgb, int to, float t) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        r += (int) (((to >> 16 & 255) - r) * t); g += (int) (((to >> 8 & 255) - g) * t); b += (int) (((to & 255) - b) * t);
        return r << 16 | g << 8 | b;
    }
}
