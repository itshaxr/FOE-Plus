package dannypx.foe.mixin.inject;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.ConnectionHandler;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.contextualbar.JumpableVehicleBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JumpableVehicleBarRenderer.class)
public abstract class JumpableVehicleBarMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void injectRenderBackground(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mixinConfig.jumpableVehicleBarMixinRenderBackground.get()
            ) {
                ci.cancel();
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("JumpableVehicleBarMixin.injectRenderBackground", foe$t);
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void injectRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mixinConfig.jumpableVehicleBarMixinRender.get()
            ) {
                ci.cancel();
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("JumpableVehicleBarMixin.injectRender", foe$t);
        }
    }
}
