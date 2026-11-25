package dannypx.foe.helper.wiki;

import com.mojang.blaze3d.platform.NativeImage;
import dannypx.foe.handler.logic.LoggerHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The wiki panel's connection: pages (kept for the session, so going back is instant) and pictures (made into
 * textures once, then kept). Everything is fetched in the background; nothing opens a browser.
 */
public final class WikiNet {
    private WikiNet() {}

    public static final String BASE = "https://wiki.fishonmc.net";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Map<String, String> pages = new ConcurrentHashMap<>();

    /// A full address for a link or picture on the wiki ("/doku.php?id=x" → "https://wiki.fishonmc.net/doku.php?id=x").
    public static String absolute(String href) {
        if (href == null || href.isBlank()) return "";
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        if (href.startsWith("//")) return "https:" + href;
        if (href.startsWith("/")) return BASE + href;
        return BASE + "/" + href;
    }

    /// The text at this address (from the session's copy when there is one).
    public static CompletableFuture<String> text(String url) {
        String have = pages.get(url);
        if (have != null) return CompletableFuture.completedFuture(have);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("User-Agent", "FOE+ (Minecraft mod) wiki reader").GET().build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(r -> {
            if (r.statusCode() >= 400) throw new IllegalStateException("The wiki answered " + r.statusCode());
            pages.put(url, r.body());
            return r.body();
        });
    }

    /// A POST (the wiki's quick search).
    public static CompletableFuture<String> post(String url, String form) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("User-Agent", "FOE+ (Minecraft mod) wiki reader")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(r -> {
            if (r.statusCode() >= 400) throw new IllegalStateException("The wiki answered " + r.statusCode());
            return r.body();
        });
    }

    /// A picture: its texture and size.
    public record Picture(Identifier id, int width, int height) {}

    private static final Map<String, Picture> pictures = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> loading = new ConcurrentHashMap<>();
    private static final Map<String, Long> failed = new ConcurrentHashMap<>();
    /// Goes up each time a picture arrives (the page is laid out again then, with its real size).
    public static volatile int version = 0;

    /// The picture at this address, or null while it's on its way (it's asked for then) or when it can't be had.
    public static Picture picture(String url) {
        if (url == null || url.isBlank()) return null;
        Picture p = pictures.get(url);
        if (p != null) return p;
        Long f = failed.get(url);
        if (f != null && System.currentTimeMillis() - f < 60_000L) return null;
        if (loading.putIfAbsent(url, Boolean.TRUE) != null) return null;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "FOE+ (Minecraft mod) wiki reader").GET().build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(r -> {
            try {
                if (r.statusCode() >= 400) throw new IllegalStateException("status " + r.statusCode());
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(r.body()));
                if (img == null) throw new IllegalStateException("not a picture");
                // big pictures are made smaller first (the panel never shows them bigger than this)
                img = shrink(img, 512);
                BufferedImage done = img;
                Minecraft.getInstance().execute(() -> {
                    try {
                        NativeImage ni = new NativeImage(done.getWidth(), done.getHeight(), true);
                        for (int y = 0; y < done.getHeight(); y++) for (int x = 0; x < done.getWidth(); x++) ni.setPixel(x, y, done.getRGB(x, y));
                        Identifier id = Identifier.fromNamespaceAndPath("foeplus", "wiki/" + Integer.toHexString(url.hashCode()) + "_" + pictures.size());
                        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "FOE+ wiki picture", ni));
                        pictures.put(url, new Picture(id, done.getWidth(), done.getHeight()));
                        version++;
                    } catch (Exception e) {
                        failed.put(url, System.currentTimeMillis());
                    } finally {
                        loading.remove(url);
                    }
                });
            } catch (Exception e) {
                failed.put(url, System.currentTimeMillis());
                loading.remove(url);
            }
        }).exceptionally(t -> {
            failed.put(url, System.currentTimeMillis());
            loading.remove(url);
            return null;
        });
        return null;
    }

    private static BufferedImage shrink(BufferedImage img, int max) {
        int w = img.getWidth(), h = img.getHeight();
        if (w <= max && h <= max) {
            if (img.getType() == BufferedImage.TYPE_INT_ARGB) return img;
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            out.getGraphics().drawImage(img, 0, 0, null);
            return out;
        }
        double s = Math.min(max / (double) w, max / (double) h);
        int nw = Math.max(1, (int) Math.round(w * s)), nh = Math.max(1, (int) Math.round(h * s));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        var g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    static void log(String s) {
        LoggerHandler.info("Wiki: " + s);
    }
}
