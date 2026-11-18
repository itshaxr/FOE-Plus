package dannypx.foe.mixin.inject;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.handler.renderer.HoldingFishRenderHandler;
import dannypx.foe.interfaces.IFishPoseState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void injectSetupAnim(AvatarRenderState avatarRenderState, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.rendererConfig.useBothHandsHoldingFishThirdPerson.get()
                    && Configs.mixinConfig.playerModelMixinSetupAnim.get()
            ) {
                if(!((IFishPoseState) avatarRenderState).foer$isHoldingFish()) return;

                PlayerModel self = (PlayerModel) (Object) this;

                self.rightArm.xRot = HoldingFishRenderHandler.FIRST_PERSON_FISH_ARM_X_ROT;
                self.rightArm.yRot = 0.0F;
                self.rightArm.zRot = 0.0F;

                self.leftArm.xRot = HoldingFishRenderHandler.FIRST_PERSON_FISH_ARM_X_ROT;
                self.leftArm.yRot = 0.0F;
                self.leftArm.zRot = 0.0F;

                HoldingFishRenderHandler.bobAnimation(self.rightArm, avatarRenderState.ageInTicks, HoldingFishRenderHandler.FIRST_PERSON_FISH_SWAY_STRENGTH);
                HoldingFishRenderHandler.bobAnimation(self.leftArm, avatarRenderState.ageInTicks, -HoldingFishRenderHandler.FIRST_PERSON_FISH_SWAY_STRENGTH);
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("PlayerModelMixin.injectSetupAnim", foe$t);
        }
    }

}
