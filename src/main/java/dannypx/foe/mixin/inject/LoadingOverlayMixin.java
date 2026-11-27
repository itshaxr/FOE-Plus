package dannypx.foe.mixin.inject;

import dannypx.foe.screens.FoeLoadingScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * FOE+'s loading screen in place of Mojang's (Settings → FOE+ loading screen): a pixel-art lake at dusk or at night,
 * one or the other at random each time. It's drawn over the game's own once that
 * is done (its colour, logo and bar covered), so it changes nothing in how the game draws or loads, and works alongside
 * mods that change the loading screen themselves (Puzzle, in Fabulously Optimized: FOE+ used to replace the game's
 * colour, which only one mod can do, and the game wouldn't start). It fades in and out as the game's does.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow private float currentProgress;
    @Shadow private long fadeInStart;
    @Shadow private long fadeOutStart;
    @Shadow @Final private boolean fadeIn;

    /// The animation's own clock (seconds), and when it last moved (ms). It moves with real time, but never more than
    /// a 30th of a second a frame: while the game is too busy loading to draw often, the animation slows a moment
    /// instead of skipping ahead.
    @Unique private float foeplus$anim = 0f;
    @Unique private long foeplus$last = -1L;
    /// Which scene this loading screen shows: dusk or night, at random each time.
    @Unique private final boolean foeplus$night = java.util.concurrent.ThreadLocalRandom.current().nextBoolean();
    /// When (on the animation's clock) loading finished: the float is pulled under then ("Fish on!").
    @Unique private float foeplus$doneAt = -1f;

    @Unique
    private static boolean foeplus$on() {
        try {
            return dannypx.foe.config.Configs.mainConfig == null || dannypx.foe.config.Configs.mainConfig.foeLoadingScreen.get();
        } catch (Throwable t) {
            return true;
        }
    }

    /// The Mojang logo isn't drawn under FOE+'s (it showed through as everything faded out). Optional: when another mod
    /// has changed how the logo is drawn, this simply doesn't apply, and nothing breaks.
    @WrapOperation(method = "render", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
    private void foeplus$noLogo(GuiGraphics g, com.mojang.blaze3d.pipeline.RenderPipeline pipeline, net.minecraft.resources.Identifier id,
                                int x, int y, float u, float v, int w, int h, int rw, int rh, int tw, int th, int color, Operation<Void> original) {
        if (!foeplus$on()) original.call(g, pipeline, id, x, y, u, v, w, h, rw, rh, tw, th, color);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void foeplus$draw(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!foeplus$on()) return;
        try {
            long now = Util.getMillis();
            if (foeplus$last >= 0) foeplus$anim += Math.min(now - foeplus$last, 33L) / 1000f;
            foeplus$last = now;
            // as the game: its screen fades in (a reload in game), and when loading is done the logo and bar fade over a
            // second, then the screen over the next
            float out = fadeOutStart > -1L ? (now - fadeOutStart) / 1000f : -1f;
            float in = fadeIn && fadeInStart > -1L ? (now - fadeInStart) / 500f : -1f;
            float screen = out >= 1f ? 1f - clamp(out - 1f) : fadeIn ? clamp(Math.max(in, 0.15f)) : 1f;
            float content = screen * (out < 0f ? 1f : 1f - clamp(out));
            if (screen <= 0.004f) return;
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((Math.round(screen * 255) & 255) << 24) | (FoeLoadingScreen.BACKGROUND & 0xFFFFFF));
            if (foeplus$doneAt < 0f && (currentProgress >= 0.995f || out >= 0f)) foeplus$doneAt = foeplus$anim;
            float done = foeplus$doneAt < 0f ? -1f : foeplus$anim - foeplus$doneAt;
            FoeLoadingScreen.render(g, foeplus$anim, currentProgress, done, foeplus$night, screen, content);
        } catch (Throwable t) {
            // never stop the game loading over a picture
        }
    }

    @Unique
    private static float clamp(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }
}
