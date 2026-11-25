package dannypx.foe.mixin.inject;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Lets FOE+ hear which sounds play with your catches, and stop the server's catch sound when you turned it off.
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void foe$play(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        try {
            if (sound == null || !dannypx.foe.handler.logic.ConnectionHandler.instance().isOnServer()) return;
            if (dannypx.foe.handler.logic.CatchSound.onPlay(sound, sound.getIdentifier().toString())) cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("SoundManagerMixin.play", foe$t);
        }
    }

    /// A sound played a moment late (one far away, as a bobber can be): heard and stopped the same way.
    @Inject(method = "playDelayed", at = @At("HEAD"), cancellable = true)
    private void foe$playDelayed(SoundInstance sound, int delay, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        try {
            if (sound == null || !dannypx.foe.handler.logic.ConnectionHandler.instance().isOnServer()) return;
            if (dannypx.foe.handler.logic.CatchSound.onPlay(sound, sound.getIdentifier().toString())) ci.cancel();
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("SoundManagerMixin.playDelayed", foe$t);
        }
    }
}
