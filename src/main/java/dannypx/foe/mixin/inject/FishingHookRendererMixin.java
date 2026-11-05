package dannypx.foe.mixin.inject;

import dannypx.foe.entity.FishingHookEntityModel;
import dannypx.foe.handler.logic.ConnectionHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.renderer.RodRendererHandler;
import dannypx.foe.interfaces.IFishingHookEntity;
import dannypx.foe.interfaces.IFishingHookRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererMixin {
    @Unique
    private Model<FishingHookRenderState> fishingHookModel;

    @Unique
    private static final float TIP_OUTWARD = 0.0f;
    @Unique
    private static final float TIP_UP = 0.37f;
    @Unique
    private static final float TIP_FORWARD = 0.6f;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void injectInit(EntityRendererProvider.Context context, CallbackInfo ci) {
        this.fishingHookModel = new FishingHookEntityModel<>(context.bakeLayer(FishingHookEntityModel.MODEL_LAYER));
    }

    @Inject(method = "vertex", at = @At("HEAD"), cancellable = true)
    private static void injectVertex(CallbackInfo ci) {
        if(ConnectionHandler.instance().isOnServer()
                && Configs.mainConfig.enableMod.get()
                && Configs.rendererConfig.showNewFishingHook.get()
                && Configs.mixinConfig.fishingHookRendererMixinVertex.get()
        ) {
            ci.cancel();
        }
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("RETURN"))
    private void injectRender(FishingHookRenderState fishingHookRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        if(ConnectionHandler.instance().isOnServer()
                && Configs.mainConfig.enableMod.get()
                && Configs.rendererConfig.showNewFishingHook.get()
                && Configs.mixinConfig.fishingHookRendererMixinSubmit.get()
        ) {
            // Your preset on your bobber, other FOE+ users' presets on theirs, Classic otherwise.
            IFishingHookRenderState hookState = (IFishingHookRenderState) fishingHookRenderState;
            dannypx.foe.type.BobberStyle style = dannypx.foe.type.BobberStyle.CLASSIC;
            if (hookState.foe$isOwn()) {
                style = Configs.rendererConfig.bobberStyle.get();
            } else if (Configs.rendererConfig.showOtherBobberStyles.get()) {
                dannypx.foe.type.BobberStyle theirs = dannypx.foe.nearby_dispatcher.types.ModUserType.bobberStyleOf(hookState.foe$getOwner());
                if (theirs != null) style = theirs;
            }
            poseStack.pushPose();
            poseStack.translate(0f, -0.0075f, 0f);
            submitNodeCollector.submitModel(this.fishingHookModel, fishingHookRenderState, poseStack, style.renderType(), fishingHookRenderState.lightCoords, OverlayTexture.NO_OVERLAY, fishingHookRenderState.outlineColor, null);
            poseStack.popPose();
        }

        ItemStack bait = ((IFishingHookRenderState) fishingHookRenderState).foer$getBaitStack();
        if(ConnectionHandler.instance().isOnServer()
                && Configs.mainConfig.enableMod.get()
                && Configs.rendererConfig.showBaitOnFishingHook.get()
                && Configs.mixinConfig.fishingHookRendererMixinSubmit.get()
                && bait != null
                && !bait.isEmpty()
                && !((IFishingHookRenderState) fishingHookRenderState).foer$isDisabledBait()
        ) {
            Minecraft minecraft = Minecraft.getInstance();
            if(minecraft.getEntityRenderDispatcher().camera != null) {
                poseStack.pushPose();
                poseStack.translate(0, -0.4, 0);

                poseStack.mulPose(Axis.YP.rotationDegrees(-minecraft.getEntityRenderDispatcher().camera.yRot()));

                ItemModelResolver itemModelManager = minecraft.getItemModelResolver();
                TrackingItemStackRenderState itemRenderState = new TrackingItemStackRenderState();

                itemModelManager.updateForTopItem(itemRenderState, bait, ItemDisplayContext.GROUND, minecraft.level, null, 0);
                itemRenderState.submit(poseStack, submitNodeCollector, fishingHookRenderState.lightCoords, OverlayTexture.NO_OVERLAY, 0);

                poseStack.popPose();
            }
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/projectile/FishingHook;Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;F)V", at = @At("TAIL"))
    private void injectUpdateRenderState(FishingHook fishingHookEntity, FishingHookRenderState fishingHookEntityState, float f, CallbackInfo ci) {
        Player owner = fishingHookEntity.getPlayerOwner();
        ((IFishingHookRenderState) fishingHookEntityState).foe$setOwn(owner != null && owner == Minecraft.getInstance().player);
        ((IFishingHookRenderState) fishingHookEntityState).foe$setOwner(owner != null ? owner.getUUID() : null);
        if(ConnectionHandler.instance().isOnServer()
                && Configs.mainConfig.enableMod.get()
                && Configs.rendererConfig.showFishingRodPositions.get()
                && Configs.mixinConfig.fishingHookRendererMixinExtractRenderState.get()
        ) {
            ((IFishingHookRenderState) fishingHookEntityState).foer$setBaitStack(((IFishingHookEntity) fishingHookEntity).foer$getBaitStack());
            ((IFishingHookRenderState) fishingHookEntityState).foer$setDisabledBait(((IFishingHookEntity) fishingHookEntity).foer$isDisabledBait());
        }
    }

    @Inject(method = "getPlayerHandPos", at = @At("RETURN"), cancellable = true)
    private void injectGetPlayerHandPos(Player player, float f, float g, CallbackInfoReturnable<Vec3> cir) {
        if(ConnectionHandler.instance().isOnServer()
                && Configs.mainConfig.enableMod.get()
                && Configs.rendererConfig.showFishingRodPositions.get()
                && Configs.mixinConfig.fishingHookRendererMixinGetPlayerHandPos.get()
        ) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.options.getCameraType().isFirstPerson() && player == minecraft.player) return;

            RodRendererHandler.RodSettings settings = RodRendererHandler.instance().get(player.getUUID());
            if (settings.isDefault()) return;

            HumanoidArm holdingArm = player.getMainHandItem().is(Items.FISHING_ROD)
                    ? player.getMainArm() : player.getMainArm().getOpposite();
            float side = holdingArm == HumanoidArm.RIGHT ? 1.0f : -1.0f;

            float scaleChange = settings.scale() - 1.0f;
            float outward = settings.offsetRight() + settings.lineOffsetRight() + TIP_OUTWARD * scaleChange;
            float up = settings.offsetUp() + settings.lineOffsetUp() + TIP_UP * scaleChange;
            float forward = settings.offsetForward() + settings.lineOffsetForward() + TIP_FORWARD * scaleChange;

            float bodyYawRadians = Mth.lerp(g, player.yBodyRotO, player.yBodyRot) * (float) (Math.PI / 180.0);
            double sine = Math.sin(bodyYawRadians);
            double cosine = Math.cos(bodyYawRadians);
            float playerScale = player.getScale();

            double worldX = (-cosine * side * outward - sine * forward) * playerScale;
            double worldY = up * playerScale;
            double worldZ = (-sine * side * outward + cosine * forward) * playerScale;

            cir.setReturnValue(cir.getReturnValue().add(worldX, worldY, worldZ));
        }
    }
}
