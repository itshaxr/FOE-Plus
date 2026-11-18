package dannypx.foe.mixin.inject;

import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.handler.renderer.InventoryScreenRenderHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import dannypx.foe.config.Configs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {
    @Inject(method = "initButton", at = @At("HEAD"), cancellable = true)
    private void injectAddRecipeBook(CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mixinConfig.abstractRecipeBookScreenMixinAddRecipeBook.get()
            ) {
                ci.cancel();
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("AbstractRecipeBookScreenMixin.injectAddRecipeBook", foe$t);
        }
    }
}
