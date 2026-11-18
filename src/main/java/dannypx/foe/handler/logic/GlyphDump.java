package dannypx.foe.handler.logic;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * For /foe minigame dump: saves the picture of every glyph the minigame's bar is drawn with (from the server's
 * resource pack fonts, already loaded in your game) to config/fishonmcextras/minigame_glyphs/, one PNG each, named
 * by font and code ("fishing_2_e998.png"). The bar's codes change every frame, so the pictures are what tell how
 * far along the catch is.
 */
public final class GlyphDump {
    private GlyphDump() {}

    private static final Set<String> saved = new HashSet<>();
    static final int MAX = 6000;

    public static Path folder() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("fishonmcextras").resolve("minigame_glyphs");
    }

    public static int count() {
        return saved.size();
    }

    public static void reset() {
        saved.clear();
    }

    /// Saves the glyphs in a line of text that aren't saved yet.
    public static void save(Component line) {
        if (line == null) return;
        line.visit((style, text) -> {
            text.codePoints().forEach(cp -> saveGlyph(fontOf(style), cp));
            return Optional.empty();
        }, Style.EMPTY);
    }

    private static Identifier fontOf(Style style) {
        FontDescription f = style.getFont();
        if (f instanceof FontDescription.Resource r) return r.id();
        return Identifier.withDefaultNamespace("default");
    }

    /// A glyph's picture from a resource pack font: {width, height, then its pixels (ARGB) row by row}, or null
    /// when the font has no picture for it.
    public static int[] pixels(Identifier font, int cp) {
        try {
            var fm = ((dannypx.foe.mixin.accessor.MinecraftFontAccessor) Minecraft.getInstance()).foe$fontManager();
            var set = ((dannypx.foe.mixin.accessor.FontManagerAccessor) fm).foe$fontSets().get(font);
            if (set == null) return null;
            for (var provider : ((dannypx.foe.mixin.accessor.FontSetAccessor) set).foe$activeProviders()) {
                var glyph = provider.getGlyph(cp);
                if (glyph == null) continue;
                if (!(glyph instanceof dannypx.foe.mixin.accessor.BitmapGlyphAccessor g)) return null;
                int w = g.foe$width(), h = g.foe$height();
                if (w <= 0 || h <= 0) return null;
                int[] out = new int[2 + w * h];
                out[0] = w; out[1] = h;
                NativeImage src = g.foe$image();
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out[2 + y * w + x] = src.getPixel(g.foe$offsetX() + x, g.foe$offsetY() + y);
                return out;
            }
        } catch (Exception e) {
            LoggerHandler.error(e);
        }
        return null;
    }

    public static void saveGlyph(Identifier font, int cp) {
        if (cp == ' ' || saved.size() >= MAX) return;
        String name = font.getPath().replace('/', '_') + "_" + Integer.toHexString(cp) + ".png";
        if (!saved.add(name)) return;
        try {
            var fm = ((dannypx.foe.mixin.accessor.MinecraftFontAccessor) Minecraft.getInstance()).foe$fontManager();
            var set = ((dannypx.foe.mixin.accessor.FontManagerAccessor) fm).foe$fontSets().get(font);
            if (set == null) return;
            for (var provider : ((dannypx.foe.mixin.accessor.FontSetAccessor) set).foe$activeProviders()) {
                var glyph = provider.getGlyph(cp);
                if (glyph == null) continue;
                if (!(glyph instanceof dannypx.foe.mixin.accessor.BitmapGlyphAccessor g)) return;
                int w = g.foe$width(), h = g.foe$height();
                if (w <= 0 || h <= 0) return;
                try (NativeImage out = new NativeImage(w, h, true)) {
                    NativeImage src = g.foe$image();
                    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
                        out.setPixel(x, y, src.getPixel(g.foe$offsetX() + x, g.foe$offsetY() + y));
                    Path file = folder().resolve(name);
                    Files.createDirectories(file.getParent());
                    out.writeToFile(file);
                }
                return;
            }
        } catch (Exception e) {
            LoggerHandler.error(e);
        }
    }
}
