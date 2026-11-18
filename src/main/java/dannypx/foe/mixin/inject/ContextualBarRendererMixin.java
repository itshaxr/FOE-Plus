package dannypx.foe.mixin.inject;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.ConnectionHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.contextualbar.ContextualBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContextualBarRenderer.class)
public interface ContextualBarRendererMixin {
    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
    private static void injectDrawExperienceLevel(GuiGraphics guiGraphics, Font font, int level, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mixinConfig.contextualBarRendererMixinRenderExperienceLevel.get()
            ) {
                ci.cancel();
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("ContextualBarRendererMixin.injectDrawExperienceLevel", foe$t);
        }
    }
}
