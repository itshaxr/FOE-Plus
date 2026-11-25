package dannypx.foe.handler.renderer;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.item.FishTagObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * The big catch moment, from tacklebox by landofif (MIT, used with permission): when you catch a big one, a soft glow
 * in its colour fades in and out at the screen edges. Nothing moves, shakes or flashes. Its sounds are in
 * O → Sounds (Catches), with FOE+'s other sounds.
 * Settings: O → HUD → Big Catch Moment.
 */
public final class BigCatchMoment {
    private BigCatchMoment() {}

    /// The kinds that count, in their order when a fish fits several, with the server's colour of each.
    public enum Kind {
        FABLED(0x82171E), TROPHY(0xD5BF3B), MYTHICAL(0xC93832), GIGANTIC(0xAF3333),
        LEGENDARY(0xFFAA00), ALBINO(0xF2F2F2), MELANISTIC(0x6A6A80);

        public final int rgb;

        Kind(int rgb) { this.rgb = rgb; }
    }

    static final long GLOW_IN = 150, GLOW_HOLD = 150, GLOW_OUT = 300;
    static final Identifier EDGE_GLOW = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "textures/gui/edge_glow.png");
    static final int GLOW_TEX = 256;

    private static Kind kind;
    private static long at;

    /// The first kind this fish fits that is switched on, or null.
    static Kind kind(String rarity, String variant, String size) {
        var c = Configs.hudConfig;
        rarity = lower(rarity);
        variant = lower(variant);
        size = lower(size);
        for (Kind k : Kind.values()) {
            boolean fits = switch (k) {
                case FABLED -> variant.equals("fabled") && c.bigCatchFabled.get();
                case TROPHY -> variant.equals("trophy") && c.bigCatchTrophy.get();
                case MYTHICAL -> rarity.equals("mythical") && c.bigCatchMythical.get();
                case GIGANTIC -> size.equals("gigantic") && c.bigCatchGigantic.get();
                case LEGENDARY -> rarity.equals("legendary") && c.bigCatchLegendary.get();
                case ALBINO -> variant.equals("albino") && c.bigCatchAlbino.get();
                case MELANISTIC -> variant.equals("melanistic") && c.bigCatchMelanistic.get();
            };
            if (fits) return k;
        }
        return null;
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    /// A fish was caught: starts its moment if it's a big one.
    public static void onCatch(FishTagObject fish) {
        if (fish == null || !Configs.hudConfig.bigCatchOn.get()) return;
        Kind k = kind(fish.getRarity(), fish.getVariant(), fish.getFishSize());
        if (k == null) return;
        kind = k;
        at = System.currentTimeMillis();
    }

    /// The glow {@code t} ms into the moment, {@code peak} at most, the whole thing stretched by {@code length}.
    static float glow(long t, float peak, float length) {
        float in = GLOW_IN * length, hold = GLOW_HOLD * length, out = GLOW_OUT * length;
        if (t < 0 || t >= in + hold + out) return 0;
        if (t < in) return peak * ease(t / in);
        if (t < in + hold) return peak;
        return peak * ease(1 - (t - in - hold) / out);
    }

    private static float ease(float x) {
        return x * x * (3 - 2 * x);
    }

    /// The edge glow over the whole screen while the moment lasts.
    public static void render(GuiGraphics g) {
        var c = Configs.hudConfig;
        if (kind == null || !c.bigCatchOn.get() || !c.bigCatchGlow.get()) return;
        float a = glow(System.currentTimeMillis() - at, c.bigCatchGlowOpacity.get() / 100f, c.bigCatchGlowLength.get());
        int alpha = Math.round(a * 255);
        if (alpha <= 0) {
            if (System.currentTimeMillis() - at > 60_000) kind = null;
            return;
        }
        edgeGlow(g, alpha, kind.rgb);
    }

    /// The edge glow over the whole screen, `alpha` (0-255) strong, in `rgb`.
    static void edgeGlow(GuiGraphics g, int alpha, int rgb) {
        g.blit(RenderPipelines.GUI_TEXTURED, EDGE_GLOW, 0, 0, 0f, 0f, g.guiWidth(), g.guiHeight(),
                GLOW_TEX, GLOW_TEX, GLOW_TEX, GLOW_TEX, Math.max(0, Math.min(255, alpha)) << 24 | (rgb & 0xFFFFFF));
    }
}
