package dannypx.foe.handler.store;

import com.mojang.blaze3d.platform.NativeImage;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.logic.LoggerHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Your own pictures for the HUD panels (HUD Editor → Image): PNG or GIF files you put in
 * config/fishonmcextras/hud_images/. A GIF plays. Each file is loaded once, the first time it's shown.
 */
public final class HudImages {
    private HudImages() {}

    /// A loaded picture: one texture per frame, how long each frame shows, and its size.
    public record Loaded(Identifier[] frames, int[] delaysMs, int width, int height, int totalMs) {
        public Identifier frameAt(long now) {
            if (frames.length == 1 || totalMs <= 0) return frames[0];
            long t = now % totalMs;
            for (int i = 0; i < frames.length; i++) {
                t -= delaysMs[i];
                if (t < 0) return frames[i];
            }
            return frames[frames.length - 1];
        }
    }

    private static final Map<String, Loaded> loaded = new HashMap<>();
    /// Files that failed to load, so they're not tried every frame.
    private static final Map<String, Long> failed = new HashMap<>();
    static { dannypx.foe.handler.logic.MemoryGuard.register("hudImages", () -> { int n = 0; for (Loaded l : loaded.values()) n += l.frames().length; return n; }, HudImages::releaseAll); }

    /// Lets go of every loaded picture's textures (they load again when next shown).
    public static void releaseAll() {
        for (Loaded l : loaded.values()) release(l);
        loaded.clear();
    }

    private static void release(Loaded l) {
        if (l == null) return;
        for (Identifier id : l.frames()) {
            try { Minecraft.getInstance().getTextureManager().release(id); } catch (RuntimeException ignored) {}
        }
    }
    static final int MAX_FRAMES = 200, MAX_SIDE = 512;

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("hud_images");
    }

    /// The picture files in the folder, by name.
    public static List<String> files() {
        List<String> out = new ArrayList<>();
        try {
            Files.createDirectories(folder());
            try (var s = Files.list(folder())) {
                s.map(p -> p.getFileName().toString())
                        .filter(n -> { String l = n.toLowerCase(Locale.ROOT); return l.endsWith(".png") || l.endsWith(".gif"); })
                        .sorted(String.CASE_INSENSITIVE_ORDER).forEach(out::add);
            }
        } catch (Exception e) {
            LoggerHandler.error(e);
        }
        return out;
    }

    /// Copies a picture from anywhere into the folder (another name when one of that name is already there and
    /// different) and gives its name there.
    public static String copyIn(Path source) throws java.io.IOException {
        Files.createDirectories(folder());
        String name = source.getFileName().toString();
        Path dest = folder().resolve(name);
        if (Files.exists(dest) && Files.mismatch(dest, source) == -1) return name;   // the same picture again
        String stem = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
        for (int i = 2; Files.exists(dest); i++) dest = folder().resolve(name = stem + "_" + i + ext);
        Files.copy(source, dest);
        release(loaded.remove(name));
        failed.remove(name);
        return name;
    }

    /// The picture of this name, loaded on first use; null when there's no such file or it can't be read.
    public static Loaded get(String name) {
        if (name == null || name.isBlank()) return null;
        Loaded l = loaded.get(name);
        if (l != null) return l;
        Long f = failed.get(name);
        if (f != null && System.currentTimeMillis() - f < 10_000L) return null;
        try {
            File file = folder().resolve(name).toFile();
            if (!file.isFile()) { failed.put(name, System.currentTimeMillis()); return null; }
            List<BufferedImage> frames = new ArrayList<>();
            List<Integer> delays = new ArrayList<>();
            if (name.toLowerCase(Locale.ROOT).endsWith(".gif")) readGif(file, frames, delays);
            else { BufferedImage img = ImageIO.read(file); if (img != null) { frames.add(img); delays.add(0); } }
            if (frames.isEmpty()) { failed.put(name, System.currentTimeMillis()); return null; }
            thin(frames, delays);
            trim(frames);
            Identifier[] ids = new Identifier[frames.size()];
            int[] ds = new int[frames.size()];
            int total = 0;
            String base = "hud_image/" + Integer.toHexString(name.hashCode()) + "_" + System.nanoTime();
            for (int i = 0; i < frames.size(); i++) {
                BufferedImage img = shrink(frames.get(i));
                NativeImage ni = new NativeImage(img.getWidth(), img.getHeight(), true);
                for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) ni.setPixel(x, y, img.getRGB(x, y));
                ids[i] = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, base + "/" + i);
                Minecraft.getInstance().getTextureManager().register(ids[i], new DynamicTexture(() -> "FOE+ HUD image " + name, ni));
                ds[i] = Math.max(20, delays.get(i));
                total += ds[i];
            }
            l = new Loaded(ids, ds, frames.getFirst().getWidth(), frames.getFirst().getHeight(), frames.size() > 1 ? total : 0);
            loaded.put(name, l);
            return l;
        } catch (Exception e) {
            LoggerHandler.error(e);
            failed.put(name, System.currentTimeMillis());
            return null;
        }
    }

    /// Cuts away the see-through edges every frame shares (a GIF on a bigger, empty canvas), so a picture that fills a
    /// box reaches all its sides.
    static void trim(List<BufferedImage> frames) {
        BufferedImage first = frames.getFirst();
        // a huge GIF: look at its first frames only (enough to find a shared empty edge), so loading stays quick
        List<BufferedImage> look = frames;
        long px = (long) first.getWidth() * first.getHeight();
        if (px * frames.size() > 40_000_000L) look = frames.subList(0, (int) Math.max(1, Math.min(frames.size(), 40_000_000L / Math.max(1, px))));
        int[] r = opaqueBounds(look);
        if (r == null) return;
        BufferedImage f = frames.getFirst();
        if (r[0] == 0 && r[1] == 0 && r[2] == f.getWidth() && r[3] == f.getHeight()) return;
        for (int i = 0; i < frames.size(); i++) {
            BufferedImage src = frames.get(i);
            BufferedImage out = new BufferedImage(r[2] - r[0], r[3] - r[1], BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.drawImage(src, -r[0], -r[1], null);
            g.dispose();
            frames.set(i, out);
        }
    }

    /// Package-private for tests: {left, top, right, bottom} (right and bottom exclusive) of the pixels that aren't
    /// see-through in any frame; null when every pixel is.
    static int[] opaqueBounds(List<BufferedImage> frames) {
        int l = Integer.MAX_VALUE, t = Integer.MAX_VALUE, rt = -1, b = -1;
        for (BufferedImage f : frames) {
            for (int y = 0; y < f.getHeight(); y++) for (int x = 0; x < f.getWidth(); x++) {
                if ((f.getRGB(x, y) >>> 24) < 8) continue;
                if (x < l) l = x;
                if (y < t) t = y;
                if (x > rt) rt = x;
                if (y > b) b = y;
            }
        }
        return rt < 0 ? null : new int[]{l, t, rt + 1, b + 1};
    }

    /// Package-private for tests: at most this many bytes of frames for one picture.
    static final long MAX_BYTES = 48L * 1024 * 1024;

    /// A GIF too big to keep whole loses every other frame (the kept ones shown for both) until it fits.
    static void thin(List<BufferedImage> frames, List<Integer> delays) {
        while (frames.size() > 1) {
            BufferedImage f = frames.getFirst();
            long w = Math.min(MAX_SIDE, f.getWidth()), h = Math.min(MAX_SIDE, f.getHeight());
            if (w * h * 4L * frames.size() <= MAX_BYTES) return;
            for (int i = frames.size() - 1; i >= 1; i -= 2) {
                delays.set(i - 1, delays.get(i - 1) + delays.get(i));
                frames.remove(i);
                delays.remove(i);
            }
        }
    }

    /// Big pictures are scaled down to at most 512 pixels a side (a HUD never shows more).
    private static BufferedImage shrink(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        if (w <= MAX_SIDE && h <= MAX_SIDE) return img;
        float k = MAX_SIDE / (float) Math.max(w, h);
        int nw = Math.max(1, Math.round(w * k)), nh = Math.max(1, Math.round(h * k));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    /// A GIF's frames as they show (each drawn over the last, as the GIF says), and their delays in ms.
    static void readGif(File file, List<BufferedImage> frames, List<Integer> delays) throws Exception {
        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
            var readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) return;
            ImageReader reader = readers.next();
            reader.setInput(in, false);
            int count = Math.min(MAX_FRAMES, reader.getNumImages(true));
            BufferedImage canvas = null;
            for (int i = 0; i < count; i++) {
                BufferedImage frame = reader.read(i);
                IIOMetadata meta = reader.getImageMetadata(i);
                org.w3c.dom.Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
                int left = 0, top = 0, delay = 100;
                String disposal = "none";
                for (org.w3c.dom.Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
                    var attrs = n.getAttributes();
                    if (n.getNodeName().equals("ImageDescriptor")) {
                        left = Integer.parseInt(attrs.getNamedItem("imageLeftPosition").getNodeValue());
                        top = Integer.parseInt(attrs.getNamedItem("imageTopPosition").getNodeValue());
                    } else if (n.getNodeName().equals("GraphicControlExtension")) {
                        delay = Integer.parseInt(attrs.getNamedItem("delayTime").getNodeValue()) * 10;
                        disposal = attrs.getNamedItem("disposalMethod").getNodeValue();
                    }
                }
                if (canvas == null) {
                    int w = Math.max(frame.getWidth() + left, 1), h = Math.max(frame.getHeight() + top, 1);
                    try {
                        org.w3c.dom.Node stream = reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0");
                        for (org.w3c.dom.Node n = stream.getFirstChild(); n != null; n = n.getNextSibling())
                            if (n.getNodeName().equals("LogicalScreenDescriptor")) {
                                w = Integer.parseInt(n.getAttributes().getNamedItem("logicalScreenWidth").getNodeValue());
                                h = Integer.parseInt(n.getAttributes().getNamedItem("logicalScreenHeight").getNodeValue());
                            }
                    } catch (Exception ignored) {
                    }
                    canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                }
                BufferedImage before = disposal.equals("restoreToPrevious") ? copy(canvas) : null;
                Graphics2D g = canvas.createGraphics();
                g.drawImage(frame, left, top, null);
                g.dispose();
                frames.add(copy(canvas));
                delays.add(delay <= 0 ? 100 : delay);
                if (disposal.equals("restoreToBackgroundColor")) {
                    Graphics2D c = canvas.createGraphics();
                    c.setComposite(java.awt.AlphaComposite.Clear);
                    c.fillRect(left, top, frame.getWidth(), frame.getHeight());
                    c.dispose();
                } else if (before != null) {
                    canvas = before;
                }
            }
            reader.dispose();
        }
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /// Package-private for tests: where a picture goes in a box {x, y, w, h}: `size` percent of the box's width (its
    /// shape kept), at `xPct` / `yPct` (0 left / top, 100 right / bottom) of the room left; `fill` stretches it over
    /// the whole box.
    public static int[] place(int boxX, int boxY, int boxW, int boxH, int imgW, int imgH, int size, int xPct, int yPct, boolean fill) {
        if (fill) return new int[]{boxX, boxY, boxW, boxH};
        int w = Math.max(4, Math.round(boxW * Math.max(5, Math.min(100, size)) / 100f));
        int h = Math.max(4, Math.round(w * imgH / (float) Math.max(1, imgW)));
        if (h > boxH) { h = boxH; w = Math.max(4, Math.round(h * imgW / (float) Math.max(1, imgH))); }
        int x = boxX + Math.round((boxW - w) * Math.max(0, Math.min(100, xPct)) / 100f);
        int y = boxY + Math.round((boxH - h) * Math.max(0, Math.min(100, yPct)) / 100f);
        return new int[]{x, y, w, h};
    }
}
