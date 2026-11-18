package dannypx.foe.mixin.inject;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.handler.renderer.HoldingFishRenderHandler;
import dannypx.foe.handler.renderer.RodRendererHandler;
import dannypx.foe.interfaces.IFishPoseState;
import dannypx.foe.interfaces.IRodSettingsHolder;
import dannypx.foe.item.FishTagObject;
import dannypx.foe.item.ValidateItem;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin<S extends ArmedEntityRenderState> {
    @Unique
    private static RodRendererHandler.RodSettings getRodSettings(ArmedEntityRenderState armedEntityRenderState, ItemStack itemStack) {
        if(!itemStack.is(Items.FISHING_ROD)) return null;
        RodRendererHandler.RodSettings settings = ((IRodSettingsHolder) armedEntityRenderState).foer$getRodSettings();
        return settings.isDefault() ? null : settings;
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void injectSubmitArmWithItemHead(S armedEntityRenderState, ItemStackRenderState itemStackRenderState, ItemStack itemStack, HumanoidArm humanoidArm, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, CallbackInfo ci) {
        try {
            if (ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && !Configs.rendererConfig.showPet.get()
                    && armedEntityRenderState instanceof ArmorStandRenderState
                    && Configs.mixinConfig.itemInHandLayerMixinSubmitArmWithItemHead.get()
                    && ValidateItem.isPet(itemStack).value1()
            ) {
                ci.cancel();
            }

            if (ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.rendererConfig.showFishingRodPositions.get()
                    && Configs.mixinConfig.itemInHandLayerMixinSubmitArmWithItemHead.get()
            ) {
                RodRendererHandler.RodSettings settings = getRodSettings(armedEntityRenderState, itemStack);
                if(settings == null) return;
                float side = humanoidArm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
                poseStack.pushPose();
                poseStack.translate(-side * settings.offsetRight(), -settings.offsetUp(), -settings.offsetForward());
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("ItemInHandLayerMixin.injectSubmitArmWithItemHead", foe$t);
        }
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void injectSubmitArmWithItemInvoke(S armedEntityRenderState, ItemStackRenderState itemStackRenderState, ItemStack itemStack, HumanoidArm humanoidArm, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.rendererConfig.useBothHandsHoldingFishThirdPerson.get()
                    && Configs.mixinConfig.itemInHandLayerMixinSubmitArmWithItemInvoke.get()
                    && armedEntityRenderState instanceof AvatarRenderState avatarRenderState
                    && ((IFishPoseState) avatarRenderState).foer$isHoldingFish()
                    && humanoidArm == avatarRenderState.mainArm
            ) {
                float fishSide = humanoidArm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
                poseStack.translate(
                        -fishSide * HoldingFishRenderHandler.getFishHandOffsetFromCenter() + HoldingFishRenderHandler.FISH_CENTER_ADJUST,
                        -HoldingFishRenderHandler.FISH_DOWN_OFFSET,
                        -HoldingFishRenderHandler.FISH_FORWARD_OFFSET
                );
                poseStack.mulPose(Axis.ZP.rotationDegrees(HoldingFishRenderHandler.FISH_ROLL));

                Pair<Boolean, FishTagObject> validatedFish = ValidateItem.isFish(itemStack);
                if (validatedFish.value1()) {
                    float fishLength = validatedFish.value2().getLength();
                    if (fishLength > 0.0f) {
                        float fishPixels = HoldingFishRenderHandler.FISH_SPRITE_PIXELS * (float) Math.pow(fishLength / HoldingFishRenderHandler.FISH_SPRITE_INCHES, Configs.rendererConfig.thirdPersonRateScaleInUnits.get());
                        float clampedPixels = Math.clamp(fishPixels,
                                Math.min(Configs.rendererConfig.thirdPersonMinimumScaleInUnits.get(), Configs.rendererConfig.thirdPersonMaximumScaleInUnits.get()),
                                Math.max(Configs.rendererConfig.thirdPersonMinimumScaleInUnits.get(), Configs.rendererConfig.thirdPersonMaximumScaleInUnits.get()));
                        float fishScale = clampedPixels / HoldingFishRenderHandler.FISH_SPRITE_PIXELS;
                        poseStack.scale(fishScale, fishScale, fishScale);
                    }
                }
            }

            if (ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.rendererConfig.showFishingRodPositions.get()
                    && Configs.mixinConfig.itemInHandLayerMixinSubmitArmWithItemHead.get()
            ) {
                RodRendererHandler.RodSettings settings = getRodSettings(armedEntityRenderState, itemStack);
                if(settings == null) return;
                poseStack.scale(settings.scale(), settings.scale(), settings.scale());
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("ItemInHandLayerMixin.injectSubmitArmWithItemInvoke", foe$t);
        }
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void injectSubmitArmWithItemReturn(S armedEntityRenderState, ItemStackRenderState itemStackRenderState, ItemStack itemStack, HumanoidArm humanoidArm, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, CallbackInfo ci) {
        try {
            if (ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.rendererConfig.showFishingRodPositions.get()
                    && Configs.mixinConfig.itemInHandLayerMixinSubmitArmWithItemReturn.get()
            ) {
                if(getRodSettings(armedEntityRenderState, itemStack) == null) return;
                poseStack.popPose();
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("ItemInHandLayerMixin.injectSubmitArmWithItemReturn", foe$t);
        }
    }
}