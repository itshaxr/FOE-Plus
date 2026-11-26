package dannypx.foe.helper.wiki;

import com.mojang.blaze3d.platform.NativeImage;
import dannypx.foe.handler.logic.LoggerHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The wiki panel's pages and pictures, all from the copy of the FishOnMC wiki inside FOE+ (assets/foeplus/wiki, made
 * by tools/wiki_snapshot.py): nothing is fetched from the internet and nothing opens a browser. Pages and pictures are
 * read in the background; pictures are made into textures once, then kept.
 */
public final class WikiNet {
    private WikiNet() {}

    public static final String BASE = "https://wiki.fishonmc.net";
    private static final String ROOT = "/assets/foeplus/wiki/";

    /// A full address for a link or picture on the wiki ("/doku.php?id=x" → "https://wiki.fishonmc.net/doku.php?id=x").
    public static String absolute(String href) {
        if (href == null || href.isBlank()) return "";
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        if (href.startsWith("//")) return "https:" + href;
        if (href.startsWith("/")) return BASE + href;
        return BASE + "/" + href;
    }

    //region The copy's index

    private record Page(String title, String text) {}

    private static volatile Map<String, Page> pageIndex = null;
    private static volatile Map<String, String> mediaIndex = null;
    /// The day the copy was made ("2026-11-26"), or "".
    public static volatile String made = "";

    private static synchronized void index() {
        if (pageIndex != null) return;
        Map<String, Page> pages = new java.util.LinkedHashMap<>();
        Map<String, String> media = new java.util.HashMap<>();
        try (InputStream in = WikiNet.class.getResourceAsStream(ROOT + "index.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("made")) made = root.get("made").getAsString();
                for (var e : root.getAsJsonObject("pages").entrySet()) {
                    JsonObject p = e.getValue().getAsJsonObject();
                    pages.put(e.getKey(), new Page(p.get("title").getAsString(), p.get("text").getAsString()));
                }
                for (var e : root.getAsJsonObject("media").entrySet()) media.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception e) {
            LoggerHandler.error(e);
        }
        mediaIndex = media;
        pageIndex = pages;
    }

    /// Package-private for tests: a page's file name in the copy ("fish:bass" → "fish__bass").
    static String fileName(String id) {
        return id.toLowerCase(Locale.ROOT).replace(":", "__").replaceAll("[^a-z0-9_.-]", "_");
    }

    /// Package-private for tests: the page an address is for ("…/doku.php?id=pets" → "pets"), or null.
    static String pageOf(String url) {
        try {
            URI u = URI.create(absolute(url).replace(" ", "%20"));
            String id = param(u.getRawQuery(), "id");
            if (id == null) {
                String path = u.getRawPath() == null ? "" : u.getRawPath();
                if (path.startsWith("/doku.php/")) id = decode(path.substring("/doku.php/".length()));
                else if (path.isEmpty() || path.equals("/") || path.equals("/doku.php")) id = "start";
            }
            return id == null || id.isBlank() ? null : id.trim().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /// Package-private for tests: what a picture is known by in the copy, as tools/wiki_snapshot.py names it: the
    /// wiki's own by their media name ("media:fish:bass.png"), others by their address.
    static String mediaKey(String url) {
        String abs = absolute(url);
        try {
            URI u = URI.create(abs.replace(" ", "%20"));
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("wiki.fishonmc.net")) {
                String m = param(u.getRawQuery(), "media");
                if (m != null) return "media:" + m.toLowerCase(Locale.ROOT);
                String path = u.getRawPath() == null ? "" : u.getRawPath();
                for (String pre : new String[]{"/_media/", "/_detail/"})
                    if (path.startsWith(pre)) return "media:" + decode(path.substring(pre.length())).replace('/', ':').toLowerCase(Locale.ROOT);
            }
        } catch (IllegalArgumentException ignored) {}
        return "url:" + abs;
    }

    private static String param(String query, String name) {
        if (query == null) return null;
        for (String kv : query.split("&")) {
            int eq = kv.indexOf('=');
            String k = eq < 0 ? kv : kv.substring(0, eq);
            if (k.equals(name)) return eq < 0 ? "" : decode(kv.substring(eq + 1));
        }
        return null;
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }
    //endregion

    /// A page's article, from the copy ("404" when the copy hasn't got it).
    public static CompletableFuture<String> text(String url) {
        return CompletableFuture.supplyAsync(() -> {
            index();
            String id = pageOf(url);
            if (id != null) {
                try (InputStream in = WikiNet.class.getResourceAsStream(ROOT + "pages/" + fileName(id) + ".html")) {
                    if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e.getMessage());
                }
            }
            throw new IllegalStateException("404");
        });
    }

    /// The copy's search: pages whose title has every word first, then pages with every word in them, as a list of
    /// links (at most 60).
    public static CompletableFuture<String> search(String query) {
        return CompletableFuture.supplyAsync(() -> {
            index();
            String[] words = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
            List<String> byTitle = new ArrayList<>(), byText = new ArrayList<>();
            for (var e : pageIndex.entrySet()) {
                String title = e.getValue().title().toLowerCase(Locale.ROOT) + " " + e.getKey().replace('_', ' ');
                String text = e.getValue().text().toLowerCase(Locale.ROOT);
                if (all(title, words)) byTitle.add(e.getKey());
                else if (all(text, words)) byText.add(e.getKey());
            }
            byTitle.addAll(byText);
            StringBuilder html = new StringBuilder("<ul>");
            for (String id : byTitle.subList(0, Math.min(60, byTitle.size())))
                html.append("<li><a href=\"/doku.php?id=").append(escape(id)).append("\">").append(escape(pageIndex.get(id).title())).append("</a></li>");
            return html.append("</ul>").toString();
        });
    }

    private static boolean all(String in, String[] words) {
        for (String w : words) if (!w.isEmpty() && !in.contains(w)) return false;
        return true;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /// A picture: its texture and size.
    public record Picture(Identifier id, int width, int height) {}

    private static final Map<String, Picture> pictures = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> loading = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> missing = new ConcurrentHashMap<>();
    /// Goes up each time a picture arrives (the page is laid out again then, with its real size).
    public static volatile int version = 0;

    /// The picture at this address, or null while it's being read (it's asked for then) or when the copy hasn't it.
    public static Picture picture(String url) {
        if (url == null || url.isBlank()) return null;
        Picture p = pictures.get(url);
        if (p != null) return p;
        if (missing.containsKey(url) || loading.putIfAbsent(url, Boolean.TRUE) != null) return null;
        CompletableFuture.runAsync(() -> {
            try {
                index();
                String file = mediaIndex.get(mediaKey(url));
                if (file == null) throw new IllegalStateException("not in the copy");
                BufferedImage img;
                try (InputStream in = WikiNet.class.getResourceAsStream(ROOT + "media/" + file)) {
                    if (in == null) throw new IllegalStateException("not in the copy");
                    img = ImageIO.read(in);
                }
                if (img == null) throw new IllegalStateException("not a picture");
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
                        missing.put(url, Boolean.TRUE);
                    } finally {
                        loading.remove(url);
                    }
                });
            } catch (Exception e) {
                missing.put(url, Boolean.TRUE);
                loading.remove(url);
            }
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
