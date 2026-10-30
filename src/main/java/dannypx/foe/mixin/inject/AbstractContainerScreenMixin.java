package dannypx.foe.mixin.inject;

import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.handler.logic.SearchHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import dannypx.foe.config.Configs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    // FOE's gold ring behind the equipped pet (ported from FOECollab).
    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void injectRenderSlotEquippedPet(net.minecraft.client.gui.GuiGraphics guiGraphics, net.minecraft.world.inventory.Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if(ConnectionHandler.instance().isOnServer() && Configs.mainConfig.enableMod.get()) {
            dannypx.foe.handler.renderer.ItemRendererHandler.instance().drawEquippedPetHighlight(guiGraphics, slot.getItem(), slot.x, slot.y);
        }
    }

    @Inject(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;onClose()V"), cancellable = true)
    private void injectKeyPressed(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        if(ConnectionHandler.instance().isOnServer()
                && Configs.mainConfig.enableMod.get()
                && Configs.mixinConfig.abstractContainerScreenMixinKeyPressed.get()
                && SearchHandler.instance().isFocused()
        ) {
            cir.setReturnValue(true);
        }
    }
}
