package dannypx.foe.mixin.inject;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.handler.renderer.HoldingFishRenderHandler;
import dannypx.foe.item.FishTagObject;
import dannypx.foe.item.ValidateItem;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Shadow
    protected abstract void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, float equipProgress, float swingProgress, HumanoidArm arm);

    @Shadow
    public abstract void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight);

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void injectRenderArmWithItem(AbstractClientPlayer abstractClientPlayer, float f, float g, InteractionHand interactionHand, float h, ItemStack itemStack, float equipProgress, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.rendererConfig.useBothHandsHoldingFishFirstPerson.get()
                    && Configs.mixinConfig.avatarRenderMixinExtractRenderState.get()
            ) {
                if (interactionHand != InteractionHand.MAIN_HAND || itemStack.isEmpty()) return;

                Pair<Boolean, FishTagObject> validatedFish = ValidateItem.isFish(itemStack);
                if (!validatedFish.value1()) return;

                float totalScale = HoldingFishRenderHandler.getTotalScale(validatedFish);

                if (!abstractClientPlayer.isInvisible()) {
                    for (HumanoidArm arm : HumanoidArm.values()) {
                        float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
                        poseStack.pushPose();
                        poseStack.translate(
                                -side * HoldingFishRenderHandler.FISH_ARM_FIRST_PERSON_INWARD,
                                HoldingFishRenderHandler.FISH_ARM_FIRST_PERSON_UP,
                                -HoldingFishRenderHandler.FISH_ARM_FIRST_PERSON_FORWARD
                        );
                        this.renderPlayerArm(poseStack, submitNodeCollector, packedLight, equipProgress, 0.0F, arm);
                        poseStack.popPose();
                    }
                }

                poseStack.pushPose();
                poseStack.translate(
                        HoldingFishRenderHandler.FISH_FIRST_PERSON_X,
                        HoldingFishRenderHandler.FISH_FIRST_PERSON_Y - equipProgress * HoldingFishRenderHandler.FIRST_PERSON_FISH_EQUIP_DROP,
                        HoldingFishRenderHandler.FISH_FIRST_PERSON_Z
                );
                poseStack.mulPose(Axis.ZP.rotationDegrees(HoldingFishRenderHandler.FISH_ROLL));
                poseStack.scale(totalScale, totalScale, totalScale);
                this.renderItem(
                        abstractClientPlayer,
                        itemStack,
                        ItemDisplayContext.GUI,
                        poseStack,
                        submitNodeCollector,
                        packedLight
                );
                poseStack.popPose();

                ci.cancel();
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("ItemInHandRendererMixin.injectRenderArmWithItem", foe$t);
        }
    }
}
