package dannypx.foe.mixin.inject;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.ConnectionHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin<T extends Entity, S extends EntityRenderState> {
    @Shadow
    public abstract Component getName();

    @Inject(method = "isCustomNameVisible", at = @At("RETURN"), cancellable = true)
    private void injectIsCustomNameVisible(CallbackInfoReturnable<Boolean> cir) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mixinConfig.entityMixinIsCustomNameVisible.get()
            ) {
                if(!Configs.rendererConfig.showPetName.get()
                        && this.getName().getString().contains("'s")
                        && this.getName().getString().contains("Pet")
                ) {
                    cir.setReturnValue(false);
                } else if (Minecraft.getInstance().options.hideGui) {
                    cir.setReturnValue(false);
                }
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("EntityMixin.injectIsCustomNameVisible", foe$t);
        }
    }
}
