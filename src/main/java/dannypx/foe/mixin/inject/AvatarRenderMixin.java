package dannypx.foe.mixin.inject;

import com.mojang.blaze3d.vertex.PoseStack;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.handler.renderer.HoldingFishRenderHandler;
import dannypx.foe.interfaces.IFishPoseState;
import dannypx.foe.item.ValidateItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRenderMixin<AvatarlikeEntity extends Avatar & ClientAvatarEntity> {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void injectExtractRenderState(AvatarlikeEntity avatar, AvatarRenderState avatarRenderState, float f, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.rendererConfig.useBothHandsHoldingFishThirdPerson.get()
                    && Configs.mixinConfig.avatarRenderMixinExtractRenderState.get()
            ) {
                boolean holdingFish = false;

                if (avatar instanceof LivingEntity living) {
                    ItemStack itemStack = living.getMainHandItem();
                    holdingFish = !itemStack.isEmpty() && ValidateItem.isFish(itemStack).value1();
                }

                ((IFishPoseState) avatarRenderState).foer$setHoldingFish(holdingFish);
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("AvatarRenderMixin.injectExtractRenderState", foe$t);
        }
    }

    @Inject(method = "renderHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V", shift = At.Shift.BEFORE))
    private void injectRenderHand(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, Identifier identifier, ModelPart modelPart, boolean bl, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.rendererConfig.useBothHandsHoldingFishThirdPerson.get()
                    && Configs.mixinConfig.avatarRenderMixinRenderHand.get()
            ) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player == null) return;

                ItemStack mainHandItem = minecraft.player.getMainHandItem();
                if (mainHandItem.isEmpty() || !ValidateItem.isFish(mainHandItem).value1()) return;

                PlayerModel playerModel = ((AvatarRenderer<?>) (Object) this).getModel();
                float side = modelPart == playerModel.rightArm ? 1.0f : -1.0f;

                modelPart.yRot = (float) Math.toRadians(side * HoldingFishRenderHandler.FISH_ARM_ROLL);
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("AvatarRenderMixin.injectRenderHand", foe$t);
        }
    }
}
