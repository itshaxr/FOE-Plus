package dannypx.foe.mixin.inject;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// A trip FOE+ started (an event's [Teleport] button): the /travel menus it goes through aren't drawn at all, so
/// nothing flashes up before the teleport (FOE+ still works them, after the screen would have been drawn).
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "renderWithTooltipAndSubtitles", at = @At("HEAD"), cancellable = true)
    private void foe$hideTripMenus(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            if (dannypx.foe.handler.renderer.TravelMapHandler.instance().silent((Screen) (Object) this)) ci.cancel();
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("ScreenMixin.renderWithTooltipAndSubtitles", foe$t);
        }
    }
}
