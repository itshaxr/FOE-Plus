package dannypx.foe.screens;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * FOE+'s loading screen, in place of the red Mojang one (when the game starts, on F3+T, and while a server's pack loads):
 * a pixel-art lake, at dusk or at night (one or the other at random each time). An angler on a dock has a line out to a
 * bobbing float; fish jump in the sun's (or moon's) reflection, birds and clouds drift, fireflies glow, and the stars
 * twinkle. "FOE+" sits on top, and a pixel loading bar fills with the game's real progress while fishing messages
 * cycle above it; when loading's done it says "Fish on!" and the float is pulled under.
 *
 * <p>The scene is drawn each frame into a small picture (320 × 180, a little wider or taller to fill other screen shapes)
 * scaled up by a whole number so every pixel stays square and sharp. The text is in Silkscreen (SIL Open Font License,
 * assets/foeplus/loading), made the exact size it's shown, away from the game's drawing, so nothing stalls a frame.
 */
public final class FoeLoadingScreen {
    private FoeLoadingScreen() {}

    /// Behind everything until the scene is ready.
    public static final int BACKGROUND = 0xFF07050F;
    private static final int ACCENT = 0x4FD8FF;
    private static final int CREAM = 0xFFF3DC;
    private static final int SHADOW = 0x1B1035;
    private static final String[] MESSAGES = {"Casting lines...", "Checking the tides...", "Untangling fishing line...",
            "Sharpening hooks...", "Polishing the tackle box...", "Waiting for a bite..."};
    private static final String DONE = "Fish on!";

    //region The scene: drawn by hand into a small picture each frame

    private static final int SW = 320, SH = 180, HOR = 112;

    private record Palette(boolean night, int[] sky, int[] water, int far, int near, int dock, int dockTop, int dockDark,
                           int rim, int glow, int glowEdge, int refl0, int refl1, int shimmer, int cloud, int cloudLit,
                           int fly, int horizon, int bird, int splash, int ring, int line, int bucket, int fish) {}

    private static final Palette DUSK = new Palette(false,
            new int[]{0x1b1035, 0x2b1749, 0x47205a, 0x6e2b63, 0xa23d63, 0xd4565a, 0xf08a4b, 0xf9b84a},
            new int[]{0xc24f5f, 0x6e2b63, 0x47205a, 0x2b1749, 0x1b1035},
            0x4a2460, 0x2a1538, 0x3b2418, 0x6b4329, 0x22140d, 0xffb35c, 0xffe08a, 0xff9a4b, 0xffd36b, 0xf08a4b, 0xd9706f,
            0x7a3566, 0xe07a6a, 0xffe98a, 0xffcf7a, 0x3a1840, 0xfff0d0, 0xffd0a0, 0xfff0d6, 0xc0603c, 0xffd9a8);
    private static final Palette NIGHT = new Palette(true,
            new int[]{0x05061a, 0x0a0d2a, 0x10173a, 0x172250, 0x1e2f63, 0x2a4174, 0x3b5684},
            new int[]{0x2a4174, 0x1a2a55, 0x121c40, 0x0b1230, 0x070b22},
            0x141b3d, 0x0b1029, 0x2a2130, 0x3e3346, 0x171220, 0x9fc3ff, 0xe8f1ff, 0xb7cdf5, 0xcfe0ff, 0x8eaee6, 0x3d5a92,
            0x1a2550, 0x3a527e, 0xd8ff8a, 0x3b5684, 0x24305e, 0xdce8ff, 0xa9c4f2, 0xdfe9ff, 0x5d7bb0, 0xbcd4ff);

    /// The still parts (sky, water, hills, stars) for one scene and picture size, drawn once.
    private record Backdrop(String key, int[] pixels, int[][] stars, int[] far, int[] near) {}

    private static final Map<String, Backdrop> backdrops = new HashMap<>();

    private static Backdrop backdrop(Palette p, int iw, int ih, int ox, int oy) {
        String key = (p.night() ? "n" : "d") + iw + "x" + ih;
        Backdrop b = backdrops.get(key);
        if (b != null) return b;
        int[] bayer = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};
        int[] px = new int[iw * ih];
        for (int y = 0; y < ih; y++) {
            int ys = y - oy;
            boolean sky = ys < HOR;
            int[] bands = sky ? p.sky() : p.water();
            float t = sky ? clamp01(ys / (float) (HOR - 1)) : clamp01((ys - HOR) / (float) (SH - HOR - 1));
            float f = t * (bands.length - 1);
            int i = Math.min(bands.length - 2, (int) Math.floor(f));
            float frac = f - i;
            for (int x = 0; x < iw; x++) {
                float th = (bayer[(y & 3) * 4 + (x & 3)] + 0.5f) / 16f;
                px[y * iw + x] = frac > th ? bands[i + 1] : bands[i];
            }
        }
        java.util.function.DoubleSupplier r = rng(7);
        int count = Math.round((p.night() ? 70 : 22) * iw / (float) SW);
        int[][] stars = new int[count][];
        for (int i = 0; i < count; i++) {
            int sx = (int) (r.getAsDouble() * iw);
            int sy = (int) (r.getAsDouble() * ((p.night() ? 85 : 46) + oy));
            stars[i] = new int[]{sx, sy, (int) (r.getAsDouble() * 628), r.getAsDouble() > 0.85 ? 1 : 0};
        }
        int[] far = new int[iw], near = new int[iw];
        for (int x = 0; x < iw; x++) {
            int xs = x - ox;
            far[x] = Math.round(ridge(1.7, HOR - 14, 10, xs)) + oy;
            near[x] = Math.round(ridge(4.2, HOR - 4, 7, xs) + (xs > 120 && xs < 250 ? 10 : 0)) + oy;
        }
        b = new Backdrop(key, px, stars, far, near);
        backdrops.put(key, b);
        return b;
    }

    private static float ridge(double seed, float base, float amp, int x) {
        return (float) (base - amp * (0.55 * Math.sin(x * 0.045 + seed) + 0.3 * Math.sin(x * 0.11 + seed * 2.1) + 0.15 * Math.sin(x * 0.27 + seed * 3.3)));
    }

    private static java.util.function.DoubleSupplier rng(int seed) {
        int[] a = {seed};
        return () -> {
            a[0] += 0x6D2B79F5;
            int t = a[0];
            t = (t ^ (t >>> 15)) * (t | 1);
            t ^= t + (t ^ (t >>> 7)) * (t | 61);
            return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
        };
    }

    /// One frame being drawn: the picture's pixels, its size, and where the 320 × 180 scene sits in it.
    private static final class Frame {
        final int[] px;
        final int w, h, ox, oy;

        Frame(int[] px, int w, int h, int ox, int oy) {
            this.px = px;
            this.w = w;
            this.h = h;
            this.ox = ox;
            this.oy = oy;
        }

        /// A pixel at scene coordinates, mixed in by {@code a}.
        void dot(double x, double y, int rgb, double a) {
            put((int) Math.round(x) + ox, (int) Math.round(y) + oy, rgb, a);
        }

        /// A rectangle at scene coordinates.
        void rect(double x, double y, int w, int h, int rgb, double a) {
            int x0 = (int) Math.round(x) + ox, y0 = (int) Math.round(y) + oy;
            for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) put(x0 + i, y0 + j, rgb, a);
        }

        /// A rectangle in the picture's own coordinates (for things that span the whole width).
        void rawRect(int x0, int y0, int w, int h, int rgb, double a) {
            for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) put(x0 + i, y0 + j, rgb, a);
        }

        void put(int x, int y, int rgb, double a) {
            if (x < 0 || y < 0 || x >= w || y >= h || a <= 0.004) return;
            int o = y * w + x;
            if (a >= 0.996) {
                px[o] = rgb;
                return;
            }
            int d = px[o];
            int r = (int) (((d >> 16) & 255) + (((rgb >> 16) & 255) - ((d >> 16) & 255)) * a);
            int g = (int) (((d >> 8) & 255) + (((rgb >> 8) & 255) - ((d >> 8) & 255)) * a);
            int b = (int) ((d & 255) + ((rgb & 255) - (d & 255)) * a);
            px[o] = (r << 16) | (g << 8) | b;
        }

        void ellipse(double cx, double cy, double rx, double ry, int rgb, double a) {
            int steps = Math.max(12, (int) Math.round(rx * 5));
            long last = Long.MIN_VALUE;
            for (int i = 0; i < steps; i++) {
                double an = i / (double) steps * Math.PI * 2;
                int x = (int) Math.round(cx + Math.cos(an) * rx), y = (int) Math.round(cy + Math.sin(an) * ry);
                long k = ((long) x << 32) ^ (y & 0xFFFFFFFFL);
                if (k != last) dot(x, y, rgb, a);
                last = k;
            }
        }

        void sprite(String[] rows, int x, int y, char c1, int rgb1, char c2, int rgb2) {
            for (int j = 0; j < rows.length; j++) for (int i = 0; i < rows[j].length(); i++) {
                char c = rows[j].charAt(i);
                if (c == c1) dot(x + i, y + j, rgb1, 1);
                else if (c == c2) dot(x + i, y + j, rgb2, 1);
            }
        }
    }

    /// The lantern on the dock ({@code x}, {@code y}: its top left): its light first, a round glow in stepped rings, then
    /// the lantern (handle, cap, glass, a flame that flickers).
    private static void lantern(Frame f, Palette p, int x, int y, double t) {
        double flick = 0.82 + 0.18 * Math.sin(t * 13) * Math.sin(t * 7.3);
        double gcx = x + 2, gcy = y + 4, rad = 7.5;
        for (int dy = -8; dy <= 8; dy++) for (int dx = -8; dx <= 8; dx++) {
            double d = Math.hypot(dx, dy * 1.1);
            if (d > rad) continue;
            double a = 0.34 * flick * Math.pow(1 - d / rad, 1.4);
            a = Math.round(a * 10) / 10.0;   // in steps, as pixel art does
            if (a > 0) f.dot(gcx + dx, gcy + dy, LANTERN, a);
        }
        boolean high = Math.sin(t * 11) * Math.sin(t * 5.7) > 0.1;
        String[] rows = {
                ".###.",
                ".#.#.",
                "#####",
                "#gyg#",
                high ? "#yWy#" : "#gWg#",
                "#gyg#",
                "#####"};
        int dark = p.dockDark(), glass = mix(LANTERN, 0x000000, 0.25), flame = LANTERN;
        for (int j = 0; j < rows.length; j++) for (int i = 0; i < 5; i++) {
            char c = rows[j].charAt(i);
            int rgb = switch (c) {
                case '#' -> dark;
                case 'g' -> glass;
                case 'y' -> flame;
                case 'W' -> 0xfffbe8;
                default -> -1;
            };
            if (rgb >= 0) f.dot(x + i, y + j, rgb, 1);
        }
    }

    /// The lantern's warm light (the same in both scenes).
    private static final int LANTERN = 0xffd77a;

    private static final String[] ANGLER = {
            "....###....",
            "...#####...",
            "..#######..",
            "....##r....",
            "....##r....",
            "...####r...",
            "..######r..",
            "..#########",
            "..######r..",
            "..######r..",
            "..#####r...",
            "..#####r...",
            "...#.#r....",
            "...#.#r....",
            "...#.#.....",
            "...#.#.....",
            "..##.##...."};

    /// Draws the scene at {@code t} seconds; {@code bite}: 0-1 while the float is pulled under (loading done).
    private static void scene(Frame f, Palette p, Backdrop bd, double t, double bite) {
        System.arraycopy(bd.pixels(), 0, f.px, 0, f.px.length);
        int iw = f.w;

        // stars
        for (int[] s : bd.stars()) {
            double tw = 0.5 + 0.5 * Math.sin(t * 2.2 + s[2] / 100.0);
            if (tw < 0.25) continue;
            double a = (p.night() ? 0.55 : 0.35) + 0.45 * tw;
            f.put(s[0], s[1], 0xfff6dc, a);
            if (s[3] == 1 && tw > 0.8) {
                f.put(s[0] - 1, s[1], 0xfff6dc, a);
                f.put(s[0] + 1, s[1], 0xfff6dc, a);
                f.put(s[0], s[1] - 1, 0xfff6dc, a);
                f.put(s[0], s[1] + 1, 0xfff6dc, a);
            }
        }

        // clouds drifting
        int[][] clouds = {{40, 34, 46}, {170, 22, 62}, {262, 46, 38}, {110, 58, 30}};
        for (int[] c : clouds) {
            double cx = ((c[0] + f.ox + t * 2.2) % (iw + 80)) - 40;
            f.rawRect((int) Math.round(cx), c[1] + f.oy, c[2], 2, p.cloud(), 1);
            f.rawRect((int) Math.round(cx + 6), c[1] - 1 + f.oy, c[2] - 14, 1, p.cloud(), 1);
            f.rawRect((int) Math.round(cx + 3), c[1] + 2 + f.oy, c[2] - 8, 1, p.cloudLit(), 1);
        }

        // the sun, striped where it meets the horizon; or the moon, high in the corner
        int sx = p.night() ? 270 : 212, sr = p.night() ? 13 : 21, cy = p.night() ? 36 : HOR - 2;
        for (int y = -sr; y <= sr; y++) {
            int yy = cy + y;
            if (yy >= HOR) continue;
            if (!p.night() && y > 4 && (y % 4 == 1 || (y > 12 && y % 3 == 0))) continue;
            int w = (int) Math.floor(Math.sqrt(sr * sr - y * y));
            f.rect(sx - w, yy, w * 2 + 1, 1, mix(p.glow(), p.glowEdge(), (y + sr) / (2.0 * sr)), 1);
        }
        if (p.night()) {
            f.rect(sx - 5, cy - 4, 3, 2, 0x9fb4dd, 1);
            f.rect(sx + 3, cy + 2, 4, 3, 0x9fb4dd, 1);
            f.rect(sx - 2, cy + 6, 2, 2, 0x9fb4dd, 1);
        }

        // birds
        for (int b = 0; b < 3; b++) {
            double bx = ((t * (9 + b * 2.5) + b * 97) % (iw + 40)) - 20 - f.ox;
            double by = 30 + b * 9 + Math.sin(t * 1.3 + b) * 2;
            boolean flap = Math.sin(t * 9 + b * 2) > 0;
            f.dot(bx, by, p.bird(), 1);
            f.dot(bx - 1, by + (flap ? -1 : 0), p.bird(), 1);
            f.dot(bx + 1, by + (flap ? -1 : 0), p.bird(), 1);
            f.dot(bx - 2, by + (flap ? -1 : 1), p.bird(), 1);
            f.dot(bx + 2, by + (flap ? -1 : 1), p.bird(), 1);
        }

        // hills, far then near, and pines on the near hill
        int horY = HOR + f.oy;
        for (int x = 0; x < iw; x++) {
            f.rawRect(x, bd.far()[x], 1, horY - bd.far()[x], p.far(), 1);
            if (bd.near()[x] < horY) f.rawRect(x, bd.near()[x], 1, horY - bd.near()[x], p.near(), 1);
        }
        for (int i = 0; i < 6; i++) {
            int tx = 6 + i * 13 + (i % 2) * 4, bx = Math.min(iw - 1, Math.max(0, tx + f.ox));
            int base = bd.near()[bx] + 1 - f.oy, th = 9 + (i % 3) * 3;
            for (int k = 0; k < th; k++) {
                int w = (th - k) / 3;
                f.rect(tx - w, base - k, w * 2 + 1, 1, p.near(), 1);
            }
        }

        // the horizon line
        f.rawRect(0, horY, iw, 1, p.horizon(), 0.55);

        // the sun's (moon's) reflection, broken and swaying
        for (int y = HOR + 1; y < HOR + 52; y++) {
            double d = (y - HOR) / 52.0;
            int w = Math.max(1, (int) Math.round((p.night() ? 10 : 22) * (1 - d) * (0.55 + 0.45 * Math.sin(y * 1.7 + t * 3.1))));
            if ((y + (int) Math.floor(t * 6)) % 3 == 0) continue;
            int off = (int) Math.round(Math.sin(y * 0.9 + t * 2.4) * 3 * (0.4 + d));
            f.rect(sx - w + off, y, w * 2, 1, y % 2 == 0 ? p.refl0() : p.refl1(), 0.9 - d * 0.6);
        }

        // shimmer on the water, drifting
        java.util.function.DoubleSupplier r = rng(42);
        int lines = Math.round(46 * iw / (float) SW);
        for (int i = 0; i < lines; i++) {
            int y = HOR + 3 + (int) (r.getAsDouble() * (SH - HOR - 6 + f.oy));
            double speed = 4 + (y - HOR) * 0.25;
            int len = 2 + (int) (r.getAsDouble() * 7);
            double x = (((r.getAsDouble() * iw) - t * speed) % (iw + 20) + (iw + 20)) % (iw + 20) - 10;
            f.rawRect((int) Math.round(x), y + f.oy, len, 1, p.shimmer(), 0.35 + 0.35 * Math.sin(t * 2 + i));
        }

        // a fish jumping now and then
        double jumpEvery = 4.6;
        int cyc = (int) Math.floor(t / jumpEvery);
        double jt = (t % jumpEvery) / 1.15;
        java.util.function.DoubleSupplier jr = rng(cyc * 13 + 5);
        int fx0 = 236 + (int) (jr.getAsDouble() * 56);
        int dir = jr.getAsDouble() > 0.5 ? 1 : -1;
        if (jt < 1) {
            double fx = fx0 + dir * 22 * jt, fy = HOR + 24 - Math.sin(Math.PI * jt) * 30;
            boolean up = jt < 0.5;
            String[] body = dir > 0
                    ? (up ? new String[]{"....##", ".####.", "####..", ".#...."} : new String[]{".#....", "####..", ".####.", "....##"})
                    : (up ? new String[]{"##....", ".####.", "..####", "....#."} : new String[]{"....#.", "..####", ".####.", "##...."});
            f.sprite(body, (int) Math.round(fx) - 3, (int) Math.round(fy) - 2, '#', p.fish(), '\0', 0);
        }
        for (int st = 0; st < 2; st++) {
            double sp = st == 0 ? jt : jt - 1;
            if (sp >= 0 && sp < 0.45) {
                int sxp = st == 0 ? fx0 : fx0 + dir * 22;
                for (int k = 0; k < 7; k++) {
                    double a = k / 6.0 * Math.PI;
                    double dx = Math.cos(a) * (2 + sp * 18), dy = -Math.sin(a) * (sp * 22) + sp * sp * 40;
                    f.dot(sxp + dx, HOR + 24 + dy, p.splash(), 1);
                }
                f.ellipse(sxp, HOR + 25, 3 + sp * 16, 1 + sp * 4, p.ring(), 1);
            }
        }

        // the dock (out to the left edge), its posts and their reflections
        int dockY = 118, dockW = 78, left = -f.ox;
        for (int post = 74; post >= left - 3; post -= (post > 6 ? (post == 74 ? 16 : post == 58 ? 28 : 24) : 24)) {
            f.rect(post, dockY + 4, 3, 26, p.dockDark(), 1);
            for (int k = 0; k < 14; k += 2) f.rect(post + Math.round(Math.sin(t * 2 + k)), dockY + 31 + k, 3, 1, p.dockDark(), 0.35);
        }
        f.rect(left, dockY, dockW - left, 4, p.dock(), 1);
        f.rect(left, dockY, dockW - left, 1, p.dockTop(), 1);
        for (int x = 7; x < dockW; x += 9) f.rect(x, dockY + 1, 1, 3, p.dockDark(), 1);
        for (int x = -2; x >= left; x -= 9) f.rect(x, dockY + 1, 1, 3, p.dockDark(), 1);
        f.rect(left, dockY + 4, dockW - left, 1, p.dockDark(), 1);

        // a bucket, and a lantern with a soft round glow that flickers
        f.sprite(new String[]{".####.", "#....#", "#oooo#", ".####.", ".####."}, 18, dockY - 5, '#', p.dockDark(), 'o', p.bucket());
        lantern(f, p, 6, dockY - 7, t);

        // the angler, rim-lit
        int ax = 44, ay = dockY - 17;
        f.sprite(ANGLER, ax, ay, '#', 0x120a18, 'r', p.rim());

        // the rod, bending when the fish bites
        double hx = ax + 10, hy = ay + 7;
        double tipX = hx + 34 - bite * 4, tipY = hy - 26 + bite * 8;
        long last = Long.MIN_VALUE;
        for (int i = 0; i <= 40; i++) {
            double s = i / 40.0;
            double bx = hx + (tipX - hx) * s + Math.sin(s * Math.PI) * (2 + bite * 5) * 0.6;
            double by = hy + (tipY - hy) * s + s * s * (2 + bite * 6);
            long k = (Math.round(bx) << 32) ^ (Math.round(by) & 0xFFFFFFFFL);
            if (k == last) continue;
            last = k;
            f.dot(bx, by, s > 0.85 ? p.rim() : 0x2a1424, 1);
        }

        // the float bobbing, and the line sagging to it; pulled under when the fish bites
        int bob = (int) Math.round(Math.sin(t * 2.3) * 1.2);
        int dip = (int) Math.round(bite * 4);
        int bX = 156 + (int) Math.round(Math.sin(t * 0.6)), bY = 140 + bob + dip;
        last = Long.MIN_VALUE;
        for (int i = 0; i <= 80; i++) {
            double s = i / 80.0;
            double lx = tipX + (bX + 1 - tipX) * s;
            double ly = tipY + (bY - tipY) * s + Math.sin(s * Math.PI) * (12 - bite * 10);
            long k = (Math.round(lx) << 32) ^ (Math.round(ly) & 0xFFFFFFFFL);
            if (k == last) continue;
            last = k;
            f.dot(lx, ly, p.line(), 0.75);
        }
        if (dip < 4) {
            String[] fl = {".w.", "rrr", "rrr", "www"};
            String[] shown = java.util.Arrays.copyOf(fl, 4 - dip);
            f.sprite(shown, bX, bY - 3, 'w', 0xfff6ea, 'r', 0xe8434c);
        }
        for (int k = 0; k < 3; k++) {
            double rp = (t * 0.8 + k / 3.0) % 1;
            f.ellipse(bX + 1, bY + 1, 3 + rp * (12 + bite * 10), 1 + rp * (3 + bite * 2), p.ring(), (1 - rp) * (0.55 + bite * 0.4));
        }

        // fireflies over the water by the dock
        for (int i = 0; i < (p.night() ? 9 : 5); i++) {
            double fxp = 20 + i * 23 + Math.sin(t * 0.7 + i * 1.9) * 9;
            double fyp = 96 + Math.sin(t * 1.1 + i * 2.7) * 8 + (i % 3) * 5;
            double on = 0.5 + 0.5 * Math.sin(t * 3 + i * 1.3);
            if (on < 0.3) continue;
            // a soft plus of light around each, not a box
            f.dot(fxp - 1, fyp, p.fly(), on * 0.3);
            f.dot(fxp + 1, fyp, p.fly(), on * 0.3);
            f.dot(fxp, fyp - 1, p.fly(), on * 0.3);
            f.dot(fxp, fyp + 1, p.fly(), on * 0.3);
            f.dot(fxp, fyp, p.fly(), on);
        }
    }

    //endregion

    //region The text: the pixel font, made the exact size it's shown

    private record Pic(Identifier id, int w, int h) {}

    /// One set of text pictures, for one screen size.
    /// {@code padTitle}, {@code padSmall}: the empty edge around each picture of that size (room for its shadow).
    private record Texts(String key, int unit, Map<String, Pic> pics, int padTitle, int padSmall) {}

    private static volatile Texts texts = null;
    private static CompletableFuture<Map<String, BufferedImage>> pending = null;
    private static String pendingKey = null;
    private static int pendingUnit = 0;
    private static int generation = 0;
    private static boolean broken = false;
    private static Font regular, bold;

    private static synchronized Font font(boolean isBold) throws Exception {
        if (regular == null) {
            regular = load("/assets/foeplus/loading/Silkscreen-Regular.ttf");
            bold = load("/assets/foeplus/loading/Silkscreen-Bold.ttf");
        }
        return isBold ? bold : regular;
    }

    private static Font load(String path) throws Exception {
        try (InputStream in = FoeLoadingScreen.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            return Font.createFont(Font.TRUETYPE_FONT, in);
        }
    }

    /// Text in the pixel font: sharp-edged, with the scene's dark shadow (down-right) and outline.
    private static BufferedImage text(String s, Font font, int color, int shadow, int outline, int tracking) {
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        pg.setFont(font);
        FontMetrics fm = pg.getFontMetrics();
        int width = 0;
        for (int i = 0; i < s.length(); i++) width += fm.charWidth(s.charAt(i)) + (i < s.length() - 1 ? tracking : 0);
        int asc = fm.getAscent(), h = fm.getAscent() + fm.getDescent();
        pg.dispose();
        int pad = pad(shadow, outline);
        BufferedImage img = new BufferedImage(Math.max(1, width + pad * 2), Math.max(1, h + pad * 2), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setFont(font);
        int[][] behind = outline > 0
                ? new int[][]{{shadow, shadow}, {-outline, -outline}, {outline, -outline}, {-outline, outline}}
                : new int[][]{{shadow, shadow}};
        for (int[] o : behind) {
            g.setColor(new java.awt.Color(SHADOW));
            draw(g, fm, s, pad + o[0], pad + asc + o[1], tracking);
        }
        g.setColor(new java.awt.Color(color));
        draw(g, fm, s, pad, pad + asc, tracking);
        g.dispose();
        return img;
    }

    private static int pad(int shadow, int outline) {
        return Math.max(shadow, outline) + 1;
    }

    private static void draw(Graphics2D g, FontMetrics fm, String s, int x, int y, int tracking) {
        for (int i = 0; i < s.length(); i++) {
            g.drawString(String.valueOf(s.charAt(i)), x, y);
            x += fm.charWidth(s.charAt(i)) + tracking;
        }
    }

    /// A soft glow behind the "+".
    private static BufferedImage glow(int size, int rgb) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        float c = (size - 1) / 2f;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            float d = (float) Math.hypot(x - c, y - c) / c;
            float a = d >= 1 ? 0 : (float) Math.exp(-d * d * 3.5) * (1 - d) * 0.75f;
            out.setRGB(x, y, (Math.round(Math.min(1, a) * 255) << 24) | rgb);
        }
        return out;
    }

    /// Every text picture for a screen {@code h} pixels tall, {@code unit}: its pixel size (an 8th of the font's).
    /// The title's and the small text's sizes and shadows, for a text pixel size {@code unit} (an 8th of a font size).
    private static int titleSize(int unit) { return 16 * unit; }
    private static int smallSize(int unit) { return 2 * unit; }
    private static int titleShadow(int unit) { return Math.max(1, titleSize(unit) * 6 / 128); }
    private static int titleOutline(int unit) { return Math.max(1, titleSize(unit) * 2 / 128); }
    private static int smallShadow(int unit) { return Math.max(1, smallSize(unit) * 3 / 16); }

    private static Map<String, BufferedImage> makeTexts(int unit) throws Exception {
        Map<String, BufferedImage> out = new LinkedHashMap<>();
        Font title = font(true).deriveFont((float) titleSize(unit)), body = font(false).deriveFont((float) smallSize(unit));
        int ts = titleShadow(unit), to = titleOutline(unit), ss = smallShadow(unit);
        int track = Math.max(1, unit / 2);
        out.put("FOE", text("FOE", title, CREAM, ts, to, track));
        out.put("+", text("+", title, ACCENT, ts, to, 0));
        out.put("glow", glow(64, ACCENT));
        out.put("sub", text("FOR FISHONMC", body, 0xffe2b0, ss, 0, unit / 2));
        for (int i = 0; i < MESSAGES.length; i++) out.put("m" + i, text(MESSAGES[i], body, CREAM, ss, 0, 0));
        out.put("done", text(DONE, body, CREAM, ss, 0, 0));
        out.put("_", text("_", body, ACCENT, ss, 0, 0));
        for (int pct = 0; pct <= 100; pct++) out.put("p" + pct, text(pct + "%", body, 0xffd38a, ss, 0, 0));
        return out;
    }

    private static Texts texts(int unit) {
        String key = String.valueOf(unit);
        if (pending != null && pending.isDone()) {
            CompletableFuture<Map<String, BufferedImage>> done = pending;
            int madeFor = pendingUnit;
            pending = null;
            pendingKey = null;
            try {
                swapIn(madeFor, done.join());
            } catch (Throwable e) {
                broken = true;
                dannypx.foe.handler.logic.LoggerHandler.error(e instanceof Exception ex ? ex : new RuntimeException(e));
            }
        }
        Texts now = texts;
        if ((now == null || !now.key().equals(key)) && pending == null && !key.equals(pendingKey)) {
            pendingKey = key;
            pendingUnit = unit;
            pending = CompletableFuture.supplyAsync(() -> {
                try {
                    return makeTexts(unit);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }
        return texts;
    }

    private static void swapIn(int unit, Map<String, BufferedImage> images) {
        var tm = Minecraft.getInstance().getTextureManager();
        int gen = ++generation;
        Map<String, Pic> pics = new HashMap<>();
        for (var e : images.entrySet()) {
            String name = e.getKey();
            Identifier id = Identifier.fromNamespaceAndPath("foeplus", "loading/" + gen + "/" + Integer.toHexString(name.hashCode()));
            BufferedImage img = e.getValue();
            tm.register(id, new DynamicTexture(() -> "FOE+ loading " + name, toNative(img)));
            pics.put(name, new Pic(id, img.getWidth(), img.getHeight()));
        }
        Texts old = texts;
        texts = new Texts(String.valueOf(unit), unit, pics, pad(titleShadow(unit), titleOutline(unit)), pad(smallShadow(unit), 0));
        if (old != null) for (Pic p : old.pics().values()) tm.release(p.id());
    }

    private static NativeImage toNative(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        NativeImage ni = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) ni.setPixel(x, y, px[y * w + x]);
        return ni;
    }

    //endregion

    //region The scene's texture, filled each frame

    private static DynamicTexture sceneTexture = null;
    private static Identifier sceneId = null;
    private static int sceneW = 0, sceneH = 0;
    private static int[] scenePixels = null;

    private static void ensureScene(int iw, int ih) {
        if (sceneTexture != null && sceneW == iw && sceneH == ih) return;
        var tm = Minecraft.getInstance().getTextureManager();
        if (sceneId != null) tm.release(sceneId);
        sceneTexture = new DynamicTexture(() -> "FOE+ loading scene", new NativeImage(iw, ih, false));
        sceneId = Identifier.fromNamespaceAndPath("foeplus", "loading/scene/" + (++generation));
        tm.register(sceneId, sceneTexture);
        sceneW = iw;
        sceneH = ih;
        scenePixels = new int[iw * ih];
    }

    //endregion

    private static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int white(float alpha) {
        return (Math.round(clamp01(alpha) * 255) << 24) | 0xFFFFFF;
    }

    /// Draws it all. {@code t}: seconds into the animation (a clock that doesn't jump, see LoadingOverlayMixin);
    /// {@code progress}: 0-1; {@code done}: seconds since loading finished (-1 before); {@code night}: which scene;
    /// {@code screen}: how much the scene shows; {@code content}: how much the text and bar show (they fade first).
    public static void render(GuiGraphics g, float t, float progress, float done, boolean night, float screen, float content) {
        if (broken) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            int gs = Math.max(1, mc.getWindow().getGuiScale());
            int W = g.guiWidth() * gs, H = g.guiHeight() * gs;
            // the scene: a whole number of screen pixels per scene pixel, a little more picture to fill the screen's shape
            int s = Math.max(1, Math.round(H / (float) SH));
            int iw = (W + s - 1) / s, ih = (H + s - 1) / s;
            iw = Math.max(iw, 8);
            ih = Math.max(ih, 8);
            ensureScene(iw, ih);
            Palette p = night ? NIGHT : DUSK;
            int ox = (iw - SW) / 2, oy = (ih - SH) / 2;
            Backdrop bd = backdrop(p, iw, ih, ox, oy);
            // the float goes under once loading is done
            double bite = done < 0 ? 0 : Math.min(1, done / 0.35);
            Frame f = new Frame(scenePixels, iw, ih, ox, oy);
            scene(f, p, bd, t, bite);
            NativeImage ni = sceneTexture.getPixels();
            for (int y = 0; y < ih; y++) for (int x = 0; x < iw; x++) ni.setPixel(x, y, 0xFF000000 | scenePixels[y * iw + x]);
            sceneTexture.upload();

            g.pose().pushMatrix();
            g.pose().scale(1f / gs, 1f / gs);
            try {
                int x0 = (W - iw * s) / 2, y0 = (H - ih * s) / 2;
                g.pose().pushMatrix();
                g.pose().translate(x0, y0);
                g.pose().scale(s, s);
                g.blit(RenderPipelines.GUI_TEXTURED, sceneId, 0, 0, 0f, 0f, iw, ih, iw, ih, iw, ih, white(screen));
                g.pose().popMatrix();
                overlay(g, W, H, t, progress, done, content);
            } finally {
                g.pose().popMatrix();
            }
        } catch (Throwable e) {
            broken = true;
            dannypx.foe.handler.logic.LoggerHandler.error(e instanceof Exception ex ? ex : new RuntimeException(e));
        }
    }

    private static void blit(GuiGraphics g, Pic p, float x, float y, float alpha) {
        if (p == null || alpha <= 0.004f) return;
        g.blit(RenderPipelines.GUI_TEXTURED, p.id(), Math.round(x), Math.round(y), 0f, 0f, p.w(), p.h(), p.w(), p.h(), p.w(), p.h(), white(alpha));
    }

    /// The title, the loading bar and its message.
    private static void overlay(GuiGraphics g, int W, int H, float t, float progress, float done, float a) {
        if (a <= 0.004f) return;
        // the text's pixel size: an 8th of the font size, kept whole so the letters stay sharp
        int unit = Math.max(4, Math.round(H / 720f * 8f));
        Texts tx = texts(unit);
        float u = unit / 8f;
        int cx = W / 2;

        // the bar first (it needs no text)
        int barW = Math.min(Math.round(560 * u), Math.round(W * 0.8f)), barH = Math.round(32 * u), border = Math.max(2, Math.round(4 * u));
        int barX = cx - barW / 2, barY = H - Math.round(56 * u) - barH;
        int sh = Math.max(2, Math.round(4 * u));
        g.fill(barX + sh, barY + sh, barX + barW + sh, barY + barH + sh, argb(SHADOW, a));
        g.fill(barX, barY, barX + barW, barY + barH, argb(CREAM, a));
        g.fill(barX + border, barY + border, barX + barW - border, barY + barH - border, argb(SHADOW, a));
        g.fill(barX + border * 2, barY + border * 2, barX + barW - border * 2, barY + barH - border * 2, argb(0x140c26, a));
        int inX0 = barX + border * 2, inX1 = barX + barW - border * 2, inY0 = barY + border * 2, inY1 = barY + barH - border * 2;
        int fillTo = inX0 + Math.round((inX1 - inX0) * clamp01(progress));
        if (fillTo > inX0) {
            // pixel segments, moving
            int seg = Math.max(4, Math.round(16 * u)), gap = Math.max(1, Math.round(4 * u));
            int shift = (int) ((t * seg / 0.6f) % seg);
            for (int x = inX0 - seg + shift; x < fillTo; x += seg) {
                int a0 = Math.max(inX0, x), a1 = Math.min(fillTo, x + seg - gap);
                if (a1 > a0) g.fill(a0, inY0, a1, inY1, argb(ACCENT, a));
                int d0 = Math.max(inX0, x + seg - gap), d1 = Math.min(fillTo, x + seg);
                if (d1 > d0) g.fill(d0, inY0, d1, inY1, argb(mix(ACCENT, 0, 0.38), a));
            }
        }
        if (tx == null) return;   // the text is still being made (a moment)
        Map<String, Pic> pics = tx.pics();

        // the title and the line under it (each picture has an empty edge around it for its shadow: the letters sit
        // inside that, so pictures side by side overlap by those edges)
        int pt = tx.padTitle(), ps = tx.padSmall(), track = Math.max(1, tx.unit() / 2);
        Pic foe = pics.get("FOE"), plus = pics.get("+"), sub = pics.get("sub"), glow = pics.get("glow");
        if (foe != null && plus != null) {
            int titleW = (foe.w() - 2 * pt) + track + (plus.w() - 2 * pt);
            float tx0 = cx - titleW / 2f - pt, ty0 = Math.round(70 * u);
            blit(g, foe, tx0, ty0, a);
            float px = tx0 + foe.w() - 2 * pt + track;
            float pulse = 0.5f - 0.5f * (float) Math.cos(t / 2.4f * Math.PI * 2);
            if (glow != null) {
                float gsz = plus.h() * (1.1f + 0.25f * pulse);
                g.pose().pushMatrix();
                g.pose().translate(px + plus.w() / 2f - gsz / 2f, ty0 + plus.h() / 2f - gsz / 2f);
                g.pose().scale(gsz / glow.w(), gsz / glow.h());
                blit(g, glow, 0, 0, a * (0.45f + 0.4f * pulse));
                g.pose().popMatrix();
            }
            blit(g, plus, px, ty0, a);
            if (sub != null) blit(g, sub, cx - sub.w() / 2f, ty0 + foe.h() - pt - Math.round(14 * u), a);
        }

        // the message and the percentage over the bar
        boolean finished = done >= 0 || progress >= 0.999f;
        Pic msg = pics.get(finished ? "done" : "m" + ((int) (t / 1.6f) % MESSAGES.length));
        int rowY = barY - Math.round(12 * u) - (msg == null ? 0 : msg.h());
        if (msg != null) {
            blit(g, msg, barX - ps, rowY, a);
            if (((int) (t * 2)) % 2 == 0) blit(g, pics.get("_"), barX - ps + msg.w() - 2 * ps, rowY, a);
        }
        Pic pct = pics.get("p" + Math.min(100, Math.round(clamp01(progress) * 100)));
        if (pct != null) blit(g, pct, barX + barW - pct.w() + ps, rowY, a);
    }

    private static int argb(int rgb, float alpha) {
        return (Math.round(clamp01(alpha) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
