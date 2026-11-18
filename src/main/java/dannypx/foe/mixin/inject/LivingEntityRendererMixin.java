package dannypx.foe.mixin.inject;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.handler.renderer.PlayerHatRenderHandler;
import dannypx.foe.handler.renderer.RodRendererHandler;
import dannypx.foe.interfaces.IHatSettingsHolder;
import dannypx.foe.interfaces.IRodSettingsHolder;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void injectExtractRenderState(T livingEntity, S livingEntityRenderState, float f, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.rendererConfig.showFishingRodPositions.get()
                    && Configs.mixinConfig.livingEntityRendererMixinExtractRenderState.get()
            ) {
                RodRendererHandler.RodSettings rodSettings = RodRendererHandler.RodSettings.DEFAULT;
                if (livingEntity instanceof Player player) {
                    rodSettings = RodRendererHandler.instance().get(player.getUUID());
                }
                ((IRodSettingsHolder) livingEntityRenderState).foer$setRodSettings(rodSettings);
            }

            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.rendererConfig.showHatPositions.get()
                    && Configs.mixinConfig.livingEntityRendererMixinExtractRenderState.get()
            ) {
                float height = PlayerHatRenderHandler.DEFAULT_HEIGHT;
                float scale = PlayerHatRenderHandler.DEFAULT_SCALE;
                if (livingEntity instanceof Player player) {
                    height = PlayerHatRenderHandler.instance().getHeight(player.getUUID());
                    scale = PlayerHatRenderHandler.instance().getScale(player.getUUID());
                }
                IHatSettingsHolder holder = (IHatSettingsHolder) livingEntityRenderState;
                holder.foer$setHatHeight(height);
                holder.foer$setHatScale(scale);
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("LivingEntityRendererMixin.injectExtractRenderState", foe$t);
        }
    }
}
