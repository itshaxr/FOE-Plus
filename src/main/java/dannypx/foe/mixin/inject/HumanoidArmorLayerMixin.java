package dannypx.foe.mixin.inject;

import dannypx.foe.handler.logic.ConnectionHandler;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import dannypx.foe.config.Configs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin<S extends HumanoidRenderState> {
    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    public void injectRenderArmorPiece(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, ItemStack stack, EquipmentSlot slot, int light, S state, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && (slot == EquipmentSlot.CHEST
                    || slot == EquipmentSlot.LEGS
                    || slot == EquipmentSlot.FEET)
                    && Configs.rendererConfig.hideArmor.get()
                    && Configs.mixinConfig.humanoidArmorLayerMixinRenderArmorPiece.get()
            ) {
                ci.cancel();
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("HumanoidArmorLayerMixin.injectRenderArmorPiece", foe$t);
        }
    }
}
