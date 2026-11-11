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

    /// FOE+'s travel map covers the Locations menu: the hidden items under it show no tooltip.
    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void injectRenderTooltipUnderTravelMap(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (dannypx.foe.handler.renderer.TravelMapHandler.instance().covers((net.minecraft.client.gui.screens.Screen) (Object) this)) ci.cancel();
    }

    /// While the search bar has focus, keys are for typing: the text box ignores letters and
    /// numbers (they arrive as characters), and without this Q would drop the hovered item and
    /// 1-9 would swap it with the hotbar. Escape still closes the screen.
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void injectKeyPressedWhileSearching(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        if(ConnectionHandler.instance().isOnServer()
                && Configs.mainConfig.enableMod.get()
                && Configs.mixinConfig.abstractContainerScreenMixinKeyPressed.get()
                && SearchHandler.instance().isFocused()
                && keyEvent.key() != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
        ) {
            var focused = ((net.minecraft.client.gui.screens.Screen) (Object) this).getFocused();
            if (focused != null) focused.keyPressed(keyEvent);
            cir.setReturnValue(true);
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
