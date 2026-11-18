package dannypx.foe.mixin.inject;

import dannypx.foe.handler.logic.ConnectionHandler;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import dannypx.foe.config.Configs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookWidgetMixin {
    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void injectIsOpen(CallbackInfoReturnable<Boolean> cir) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mixinConfig.recipeBookWidgetIsOpen.get()
            ) {
                cir.setReturnValue(false);
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("RecipeBookWidgetMixin.injectIsOpen", foe$t);
        }
    }
}
