package dannypx.foe.helper;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dannypx.foe.config.Configs;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * Your own fishing line in your colour (O menu → Line), only on your screen. Every point of the line the game draws
 * keeps its see-through but takes your colour. (After tacklebox's line colour, by landofif, MIT.)
 */
public final class LineColour {
    private LineColour() {}

    /// Set while a bobber is drawn: whether it's yours (only your line is recoloured).
    public static boolean drawingOwn = false;

    /// Your line's colour (0xRRGGBB), or -1 for the game's own.
    public static int colour() {
        if (!Configs.mainConfig.enableMod.get()) return -1;
        return dannypx.foe.screens.element.hud.GameXpBar.parse(Configs.rendererConfig.fishingLineColor.get());
    }

    /// The line's drawing, in your colour when it's yours and you've picked one.
    public static SubmitNodeCollector.CustomGeometryRenderer wrap(SubmitNodeCollector.CustomGeometryRenderer line) {
        int rgb = colour();
        if (!drawingOwn || rgb < 0 || line == null) return line;
        return (pose, consumer) -> line.render(pose, new Recolour(consumer, rgb));
    }

    /// Passes every point through, its colour swapped for `rgb` (its see-through kept).
    private record Recolour(VertexConsumer inner, int rgb) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { inner.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) {
            inner.setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, a);
            return this;
        }
        @Override public VertexConsumer setColor(int argb) { return setColor(0, 0, 0, (argb >>> 24) & 0xFF); }
        @Override public VertexConsumer setUv(float u, float v) { inner.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { inner.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { inner.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { inner.setNormal(x, y, z); return this; }
        @Override public VertexConsumer setLineWidth(float w) { inner.setLineWidth(w); return this; }
    }
}
