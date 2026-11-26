package dannypx.foe.mixin.inject;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dannypx.foe.screens.FoeLoadingScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.IntSupplier;

/**
 * FOE+'s loading screen in place of Mojang's (Settings → FOE+ loading screen): the game still loads, fades in and out
 * and shows the screen behind as always; only its colour, logo and bar are FOE+'s ({@link FoeLoadingScreen}).
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow private float currentProgress;
    @Shadow private long fadeInStart;
    @Shadow @Final private boolean fadeIn;

    @Shadow
    protected abstract void drawProgressBar(GuiGraphics graphics, int x0, int y0, int x1, int y1, float alpha);

    /// When this loading screen first drew (ms), for its animation.
    @Unique private long foeplus$start = -1L;

    @Unique
    private static boolean foeplus$on() {
        try {
            return dannypx.foe.config.Configs.mainConfig == null || dannypx.foe.config.Configs.mainConfig.foeLoadingScreen.get();
        } catch (Throwable t) {
            return true;
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
    private int foeplus$background(IntSupplier brand) {
        return foeplus$on() ? FoeLoadingScreen.BACKGROUND : brand.getAsInt();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
    private void foeplus$logo(GuiGraphics g, RenderPipeline pipeline, Identifier id, int x, int y, float u, float v, int w, int h,
                              int rw, int rh, int tw, int th, int color) {
        if (!foeplus$on()) g.blit(pipeline, id, x, y, u, v, w, h, rw, rh, tw, th, color);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;drawProgressBar(Lnet/minecraft/client/gui/GuiGraphics;IIIIF)V"))
    private void foeplus$bar(LoadingOverlay self, GuiGraphics g, int x0, int y0, int x1, int y1, float alpha) {
        if (foeplus$on()) {
            long now = Util.getMillis();
            if (foeplus$start < 0) foeplus$start = now;
            float in = fadeIn && fadeInStart > 0 ? Math.min(1f, (now - fadeInStart) / 500f) : 1f;
            if (FoeLoadingScreen.render(g, foeplus$start, currentProgress, alpha * Math.max(0f, in))) return;
        }
        drawProgressBar(g, x0, y0, x1, y1, alpha);
    }
}
