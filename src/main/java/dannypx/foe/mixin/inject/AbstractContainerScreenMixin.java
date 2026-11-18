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
    @org.spongepowered.asm.mixin.Shadow protected net.minecraft.world.inventory.Slot hoveredSlot;

    // FOE's gold ring behind the equipped pet (ported from FOECollab).
    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void injectRenderSlotEquippedPet(net.minecraft.client.gui.GuiGraphics guiGraphics, net.minecraft.world.inventory.Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer() && Configs.mainConfig.enableMod.get()) {
                dannypx.foe.handler.renderer.ItemRendererHandler.instance().drawEquippedPetHighlight(guiGraphics, slot.getItem(), slot.x, slot.y);
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("AbstractContainerScreenMixin.injectRenderSlotEquippedPet", foe$t);
        }
    }

    /// FOE+'s travel map covers the Locations menu: the hidden items under it show no tooltip.
    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void injectRenderTooltipUnderTravelMap(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        try {
            dannypx.foe.handler.renderer.SkinTooltip.hover(hoveredSlot == null ? null : hoveredSlot.getItem());
            if (dannypx.foe.handler.renderer.TravelMapHandler.instance().covers((net.minecraft.client.gui.screens.Screen) (Object) this)) {
                ci.cancel();
                return;
            }
            // FOE+'s value boxes beside the menu, drawn here (just before the tooltip) so a tooltip goes over them.
            if (ConnectionHandler.instance().isOnServer())
                dannypx.foe.handler.renderer.ContainerBoxes.draw((AbstractContainerScreen<?>) (Object) this, guiGraphics);
            // The presets panel hides the tooltips under it too, where the mouse is on it.
            if (dannypx.foe.handler.renderer.PresetsPanelHandler.instance().covers((net.minecraft.client.gui.screens.Screen) (Object) this, mouseX, mouseY)) ci.cancel();
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("AbstractContainerScreenMixin.injectRenderTooltipUnderTravelMap", foe$t);
        }
    }

    /// While the search bar has focus, keys are for typing: the text box ignores letters and
    /// numbers (they arrive as characters), and without this Q would drop the hovered item and
    /// 1-9 would swap it with the hotbar. Escape still closes the screen.
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void injectKeyPressedWhileSearching(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        try {
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
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("AbstractContainerScreenMixin.injectKeyPressedWhileSearching", foe$t);
        }
    }

    @Inject(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;onClose()V"), cancellable = true)
    private void injectKeyPressed(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mixinConfig.abstractContainerScreenMixinKeyPressed.get()
                    && SearchHandler.instance().isFocused()
            ) {
                cir.setReturnValue(true);
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("AbstractContainerScreenMixin.injectKeyPressed", foe$t);
        }
    }
}
