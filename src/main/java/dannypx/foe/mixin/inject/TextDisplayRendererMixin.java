package dannypx.foe.mixin.inject;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.handler.logic.TextDisplayHandler;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public abstract class TextDisplayRendererMixin {
    @Shadow
    protected abstract Display.TextDisplay.CachedInfo splitLines(Component text, int maxWidth);

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;F)V", at = @At("TAIL"))
    private void injectExtractRenderState(Display.TextDisplay textDisplay, TextDisplayEntityRenderState textDisplayEntityRenderState, float f, CallbackInfo ci) {
        if(ConnectionHandler.instance().isOnServer()
                && Configs.mainConfig.enableMod.get()
                && Configs.mixinConfig.textDisplayRendererMixinExtractRenderState.get()
        ) {
            Display.TextDisplay.TextRenderState data = textDisplayEntityRenderState.textRenderState;
            if (data == null) return;

            TextDisplayHandler.Entry entry = TextDisplayHandler.instance().get(textDisplay, data.text(), data.lineWidth(), this::splitLines);
            if (entry.info() == null) return;

            textDisplayEntityRenderState.textRenderState = new Display.TextDisplay.TextRenderState(
                    entry.prefixed(),
                    data.lineWidth(),
                    data.textOpacity(),
                    data.backgroundColor(),
                    data.flags()
            );
            textDisplayEntityRenderState.cachedInfo = entry.info();
        }
    }

    /// Hides the background box behind floating text when asked to (AUTO: only with a shader pack
    /// on, since some packs such as Photon draw it as a solid black box). Clears the "default
    /// background" flag too, as that one ignores the colour.
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;F)V", at = @At("TAIL"))
    private void foe$hideBackground(Display.TextDisplay textDisplay, TextDisplayEntityRenderState state, float f, CallbackInfo ci) {
        if (!Configs.mainConfig.enableMod.get()) return;
        dannypx.foe.type.TextBackgroundMode mode = Configs.rendererConfig.textDisplayBackground.get();
        boolean hide = mode == dannypx.foe.type.TextBackgroundMode.HIDE
                || (mode == dannypx.foe.type.TextBackgroundMode.AUTO && dannypx.foe.helper.ShaderHelper.isShaderPackInUse());
        Display.TextDisplay.TextRenderState data = state.textRenderState;
        if (!hide || data == null) return;
        state.textRenderState = new Display.TextDisplay.TextRenderState(
                data.text(),
                data.lineWidth(),
                data.textOpacity(),
                Display.IntInterpolator.constant(0),
                (byte) (data.flags() & ~Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND)
        );
    }
}
