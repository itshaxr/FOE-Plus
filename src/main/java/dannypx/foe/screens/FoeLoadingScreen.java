package dannypx.foe.screens;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * FOE+'s loading screen, in place of the red Mojang one (when the game starts, on F3+T, and while a server's pack loads):
 * a dark screen where a bobber is cast in on a curve, lands on the line with a ripple, and F, O and E rise out of the
 * water one after another before the "+" pops in with a glow. The line is the loading bar: it fills behind the bobber,
 * which bobs at its end. Everything is drawn at your screen's own pixels (the pictures in assets/foeplus/loading, made by
 * tools/loading_art.py, are made exactly the size they're shown), so it stays sharp at any size and GUI scale.
 */
public final class FoeLoadingScreen {
    private FoeLoadingScreen() {}

    /// The screen behind it, the "+" and the filled line.
    public static final int BACKGROUND = 0xFF0A0E17;
    private static final int ACCENT = 0x34D5FF;
    private static final int ACCENT_DEEP = 0x1E8CFF;

    //region The pictures, made for this window's size

    private record Pic(Identifier id, int w, int h) {}

    private static final Map<String, Pic> pics = new HashMap<>();
    private static final Map<String, BufferedImage> masters = new HashMap<>();
    private static String builtFor = "";
    private static boolean broken = false;
    /// The ripple, drawn at each of these sizes (so it grows smoothly without stretching a picture).
    private static final int RIPPLE_FRAMES = 18;

    private static BufferedImage master(String name) throws Exception {
        BufferedImage m = masters.get(name);
        if (m != null) return m;
        try (InputStream in = FoeLoadingScreen.class.getResourceAsStream("/assets/foeplus/loading/" + name + ".png")) {
            if (in == null) throw new IllegalStateException("missing " + name);
            m = toArgb(ImageIO.read(in));
        }
        masters.put(name, m);
        return m;
    }

    private static BufferedImage toArgb(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_ARGB) return img;
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return out;
    }

    /// A smooth smaller copy: halved again and again, then to the exact size (no jagged edges).
    static BufferedImage scaled(BufferedImage src, int w, int h) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        BufferedImage cur = src;
        while (cur.getWidth() / 2 >= w && cur.getHeight() / 2 >= h) cur = resize(cur, cur.getWidth() / 2, cur.getHeight() / 2);
        return resize(cur, w, h);
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setComposite(AlphaComposite.Src);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /// The "+": its shape filled with the accent, lighter at the top.
    private static BufferedImage tinted(BufferedImage mask) {
        int w = mask.getWidth(), h = mask.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            float t = h <= 1 ? 0 : y / (float) (h - 1);
            int rgb = mixRgb(0x6FE6FF, ACCENT_DEEP, t * 0.85f);
            for (int x = 0; x < w; x++) {
                int a = mask.getRGB(x, y) >>> 24;
                out.setRGB(x, y, (a << 24) | rgb);
            }
        }
        return out;
    }

    /// A soft round glow of this colour, brightest in the middle.
    private static BufferedImage glow(int size, int rgb, float strength) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        float c = (size - 1) / 2f;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            float d = (float) Math.hypot(x - c, y - c) / c;
            float a = d >= 1 ? 0 : (float) Math.exp(-d * d * 4.2) * (1 - d) * strength;
            out.setRGB(x, y, (Math.round(Math.min(1, a) * 255) << 24) | rgb);
        }
        return out;
    }

    /// The bobber: an accent dot with a white middle, in its own soft glow.
    private static BufferedImage bobber(int core) {
        int size = core * 5;
        BufferedImage out = glow(size, ACCENT, 0.55f);
        Graphics2D g = smooth(out);
        float c = size / 2f;
        g.setColor(new Color(ACCENT));
        g.fill(new Ellipse2D.Float(c - core / 2f, c - core / 2f, core, core));
        g.setColor(Color.WHITE);
        float in = core * 0.46f;
        g.fill(new Ellipse2D.Float(c - in / 2f, c - in / 2f, in, in));
        g.dispose();
        return out;
    }

    /// One frame of the ripple: a flat ring this wide.
    private static BufferedImage ripple(int width, float stroke) {
        int w = Math.max(4, width + (int) Math.ceil(stroke * 2) + 2);
        int h = Math.max(4, Math.round(width * 0.34f) + (int) Math.ceil(stroke * 2) + 2);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(out);
        g.setColor(new Color(ACCENT));
        g.setStroke(new BasicStroke(stroke));
        g.draw(new Ellipse2D.Float((w - width) / 2f, (h - width * 0.34f) / 2f, width, width * 0.34f));
        g.dispose();
        return out;
    }

    /// The light behind everything: a faint blue glow in the middle of the screen, dithered so it has no bands.
    private static BufferedImage backdrop(int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.util.Random noise = new java.util.Random(7);
        float cx = w / 2f, cy = h * 0.47f, r = Math.max(w, h) * 0.55f;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float d = (float) Math.hypot((x - cx) / r, (y - cy) / (r * 0.8f));
            float a = d >= 1 ? 0 : (1 - d) * (1 - d) * 0.30f;
            int alpha = (int) Math.floor(a * 255 + noise.nextFloat());
            out.setRGB(x, y, (Math.max(0, Math.min(255, alpha)) << 24) | 0x1C2E4E);
        }
        return out;
    }

    private static Graphics2D smooth(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    private static void put(String key, BufferedImage img) {
        NativeImage ni = new NativeImage(img.getWidth(), img.getHeight(), true);
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) ni.setPixel(x, y, img.getRGB(x, y));
        Identifier id = Identifier.fromNamespaceAndPath("foeplus", "loading/" + key);
        var tm = Minecraft.getInstance().getTextureManager();
        tm.release(id);
        tm.register(id, new DynamicTexture(() -> "FOE+ loading " + key, ni));
        pics.put(key, new Pic(id, img.getWidth(), img.getHeight()));
    }

    //endregion

    /// Where everything goes, in screen pixels.
    private record Layout(int gs, int cap, int x0, int top, int fW, int oW, int eW, int pW, int gap, int plusGap,
                          int line, int lineX0, int lineX1, int lineH, int bob, int tagH, int tagY, int width, int height) {}

    private static Layout layout(int sw, int sh, int gs) {
        // the letters' height in GUI pixels, then everything in real pixels from it
        int capGui = Math.max(22, Math.min(84, Math.round(Math.min(sw * 0.085f, sh * 0.15f))));
        int cap = capGui * gs;
        int fW = Math.round(cap * 307 / 512f), oW = cap, eW = Math.round(cap * 317 / 512f), pW = Math.round(cap * 0.62f);
        int gap = Math.round(cap * 0.14f), plusGap = Math.round(cap * 0.10f);
        int total = fW + gap + oW + gap + eW + plusGap + pW;
        int W = sw * gs, H = sh * gs;
        int x0 = (W - total) / 2;
        int top = (H - cap) / 2 - Math.round(cap * 0.16f);
        int line = top + cap + Math.round(cap * 0.30f);
        int lineH = Math.max(2, Math.round(cap * 0.024f));
        int bob = Math.max(6, Math.round(cap * 0.085f));
        int tagH = Math.max(6, Math.round(cap * 0.15f));
        int tagY = line + lineH + Math.round(cap * 0.26f);
        return new Layout(gs, cap, x0, top, fW, oW, eW, pW, gap, plusGap, line, x0, x0 + total, lineH, bob, tagH, tagY, W, H);
    }

    private static void build(Layout l) throws Exception {
        String key = l.width() + "x" + l.height() + "@" + l.gs();
        if (key.equals(builtFor)) return;
        put("f", scaled(master("f"), l.fW(), l.cap()));
        put("o", scaled(master("o"), l.oW(), l.cap()));
        put("e", scaled(master("e"), l.eW(), l.cap()));
        put("plus", tinted(scaled(master("plus"), l.pW(), l.pW())));
        put("plusglow", glow(l.pW() * 3, ACCENT, 0.42f));
        BufferedImage tag = master("tagline");
        put("tagline", scaled(tag, Math.round(tag.getWidth() * l.tagH() / (float) tag.getHeight()), l.tagH()));
        put("bobber", bobber(l.bob()));
        for (int i = 0; i < RIPPLE_FRAMES; i++) {
            float t = i / (float) (RIPPLE_FRAMES - 1);
            put("ripple" + i, ripple(Math.round(l.bob() * (1.2f + 5.5f * t)), Math.max(1.2f, l.bob() * 0.16f)));
        }
        put("backdrop", backdrop(l.width(), l.height()));
        builtFor = key;
    }

    //region Timing

    private static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    /// 0 before {@code start}, 1 after {@code start + length}, in between on the way.
    private static float phase(float t, float start, float length) {
        return clamp01((t - start) / length);
    }

    private static float easeOutCubic(float x) {
        float a = 1 - x;
        return 1 - a * a * a;
    }

    private static float easeInOutCubic(float x) {
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }

    private static int mixRgb(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int white(float alpha) {
        return (Math.round(clamp01(alpha) * 255) << 24) | 0xFFFFFF;
    }

    private static int argb(int rgb, float alpha) {
        return (Math.round(clamp01(alpha) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    //endregion

    /// When the bobber lands on the line (seconds after the screen first shows).
    private static final float LAND = 0.95f;

    /// Draws it all. {@code start}: when this loading screen first showed (ms); {@code progress}: 0-1; {@code alpha}: how
    /// much it shows (it fades in and out). False when it couldn't (the game's own is drawn then).
    public static boolean render(GuiGraphics g, long start, float progress, float alpha) {
        if (broken) return false;
        Minecraft mc = Minecraft.getInstance();
        int gs = Math.max(1, mc.getWindow().getGuiScale());
        Layout l = layout(g.guiWidth(), g.guiHeight(), gs);
        try {
            build(l);
        } catch (Throwable e) {
            broken = true;
            dannypx.foe.handler.logic.LoggerHandler.error(e instanceof Exception ex ? ex : new RuntimeException(e));
            return false;
        }
        float t = (Util.getMillis() - start) / 1000f;
        g.pose().pushMatrix();
        g.pose().scale(1f / gs, 1f / gs);
        try {
            draw(g, l, t, clamp01(progress), clamp01(alpha));
        } finally {
            g.pose().popMatrix();
        }
        return true;
    }

    private static void blit(GuiGraphics g, String key, float x, float y, float alpha) {
        Pic p = pics.get(key);
        if (p == null || alpha <= 0.004f) return;
        g.blit(RenderPipelines.GUI_TEXTURED, p.id(), Math.round(x), Math.round(y), 0f, 0f, p.w(), p.h(), p.w(), p.h(), p.w(), p.h(), white(alpha));
    }

    private static void blitCentered(GuiGraphics g, String key, float cx, float cy, float alpha) {
        Pic p = pics.get(key);
        if (p != null) blit(g, key, cx - p.w() / 2f, cy - p.h() / 2f, alpha);
    }

    private static void draw(GuiGraphics g, Layout l, float t, float progress, float a) {
        // the light behind
        blit(g, "backdrop", 0, 0, a * phase(t, 0f, 0.6f));

        int lineY = l.line(), x0 = l.lineX0(), x1 = l.lineX1(), len = x1 - x0, h = l.lineH();
        int mid = (x0 + x1) / 2;
        // the line draws itself out from the middle
        float grow = easeInOutCubic(phase(t, 0.10f, 0.55f));
        int half = Math.round(len / 2f * grow);
        if (half > 0) g.fill(mid - half, lineY, mid + half, lineY + h, white(0.10f * a));

        // the bobber's place on the line: where loading has got to
        float head = x0 + len * progress;
        float bobY = lineY + h / 2f;

        // after it lands, the line behind it fills in the accent colour
        float filled = phase(t, LAND, 0.35f);
        if (filled > 0 && head > x0) {
            g.fill(x0, lineY, Math.round(head), lineY + h, argb(ACCENT, 0.95f * a * filled));
        }

        // the cast: the bobber flies in on a curve from the top right, with a fading trail
        float fly = phase(t, 0.22f, LAND - 0.22f);
        if (fly > 0 && fly < 1) {
            for (int i = 22; i >= 0; i--) {
                float ft = fly - i * 0.007f;
                if (ft <= 0) continue;
                float[] p = castPoint(l, ft, head, bobY);
                blitCentered(g, "bobber", p[0], p[1], a * (i == 0 ? 1f : 0.30f * (1 - i / 23f) * (1 - i / 23f)));
            }
        }

        // the ripple where it lands
        float rp = phase(t, LAND, 0.75f);
        if (rp > 0 && rp < 1) {
            int frame = Math.min(RIPPLE_FRAMES - 1, Math.round(easeOutCubic(rp) * (RIPPLE_FRAMES - 1)));
            blitCentered(g, "ripple" + frame, head, bobY, a * (1 - rp) * 0.9f);
            float rp2 = phase(t, LAND + 0.16f, 0.75f);
            if (rp2 > 0 && rp2 < 1) {
                int f2 = Math.min(RIPPLE_FRAMES - 1, Math.round(easeOutCubic(rp2) * (RIPPLE_FRAMES - 1)));
                blitCentered(g, "ripple" + f2, head, bobY, a * (1 - rp2) * 0.55f);
            }
        }

        // F, O and E rise out of the water, one after another (hidden below the line until they come up through it)
        int capTop = l.top();
        int gsc = l.gs();
        g.pose().pushMatrix();
        g.pose().scale(gsc, gsc);
        g.enableScissor(0, 0, l.width() / gsc + 1, lineY / gsc);
        g.pose().popMatrix();
        float lx = l.x0();
        String[] letters = {"f", "o", "e"};
        int[] widths = {l.fW(), l.oW(), l.eW()};
        for (int i = 0; i < 3; i++) {
            float lt = phase(t, LAND + 0.05f + i * 0.09f, 0.62f);
            if (lt > 0) {
                float rise = (1 - easeOutBack(lt)) * (lineY - capTop);
                blit(g, letters[i], lx, capTop + rise, a * clamp01(lt * 2.2f));
            }
            lx += widths[i] + l.gap();
        }
        g.disableScissor();

        // the "+": pops in with a glow, which then breathes
        float px = lx - l.gap() + l.plusGap(), py = capTop;
        float pt = phase(t, LAND + 0.42f, 0.42f);
        if (pt > 0) {
            float s = Math.max(0.001f, easeOutBack(pt));
            float cx = px + l.pW() / 2f, cy = py + l.pW() / 2f;
            float breathe = t > LAND + 0.9f ? 0.78f + 0.22f * (float) Math.sin((t - LAND - 0.9f) * 2.2f) : 1f;
            blitCentered(g, "plusglow", cx, cy, a * clamp01(pt * 1.6f) * breathe);
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(s, s);
            g.pose().translate(-cx, -cy);
            blit(g, "plus", px, py, a * clamp01(pt * 3f));
            g.pose().popMatrix();
        }

        // the bobber, resting on the line at the loading's end, bobbing gently
        if (fly >= 1) {
            float bob = (float) Math.sin((t - LAND) * 3.1f) * l.bob() * 0.14f * phase(t, LAND + 0.25f, 0.5f);
            float dip = (1 - phase(t, LAND, 0.22f)) * l.bob() * 0.35f;
            blitCentered(g, "bobber", head, Math.round(bobY + bob + dip), a);
        }

        // the tagline
        float tg = phase(t, LAND + 0.70f, 0.55f);
        Pic tag = pics.get("tagline");
        if (tg > 0 && tag != null) {
            float y = l.tagY() + (1 - easeOutCubic(tg)) * l.tagH() * 0.6f;
            blit(g, "tagline", (l.width() - tag.w()) / 2f, y, a * 0.42f * tg);
        }
    }

    /// Where the bobber is on its cast ({@code f}: 0-1): from above the top right, curving down onto the line at {@code endX}.
    private static float[] castPoint(Layout l, float f, float endX, float endY) {
        float e = easeInOutCubic(f);
        float sx = l.width() * 0.86f, sy = -l.bob() * 4f;
        float cx = (sx + endX) / 2f + l.cap() * 0.4f, cy = endY - l.cap() * 2.6f;
        float u = 1 - e;
        float x = u * u * sx + 2 * u * e * cx + e * e * endX;
        float y = u * u * sy + 2 * u * e * cy + e * e * endY;
        return new float[]{x, y};
    }
}
