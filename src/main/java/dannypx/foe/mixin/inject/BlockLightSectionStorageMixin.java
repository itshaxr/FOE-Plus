package dannypx.foe.mixin.inject;

import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.handler.logic.LightHandler;
import net.minecraft.world.level.lighting.BlockLightSectionStorage;
import dannypx.foe.config.Configs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockLightSectionStorage.class)
public abstract class BlockLightSectionStorageMixin {
    @Inject(method = "getLightValue", at = @At("RETURN"), cancellable = true)
    private void injectGetLightValue(long blockPos, CallbackInfoReturnable<Integer> cir) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mixinConfig.blockLightSectionStorageMixinGetLightValue.get()
            ) {
                cir.setReturnValue(LightHandler.instance().calculateFishingHookLight(blockPos, cir.getReturnValue()));
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("BlockLightSectionStorageMixin.injectGetLightValue", foe$t);
        }
    }
}
