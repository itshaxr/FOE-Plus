package dannypx.foe.mixin.inject;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.ChummerHandler;
import dannypx.foe.handler.logic.ConnectionHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Display.TextDisplay.class)
public abstract class TextDisplayMixin {
    @Shadow
    public abstract Component getText();

    @Inject(method = "onSyncedDataUpdated", at = @At("TAIL"))
    private void injectOnSyncedDataUpdated(EntityDataAccessor<?> entityDataAccessor, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mixinConfig.textDisplayMixinOnSyncedDataUpdated.get()
            ) {
                Display.TextDisplay self = (Display.TextDisplay) (Object) this;
                ChummerHandler.instance().onTextUpdate(self, getText());
                dannypx.foe.handler.renderer.VariantPulse.onTextUpdate(self, getText());
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("TextDisplayMixin.injectOnSyncedDataUpdated", foe$t);
        }
    }
}
