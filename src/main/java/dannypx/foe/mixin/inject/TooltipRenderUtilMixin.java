package dannypx.foe.mixin.inject;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// A pet wearing a skin: its tooltip framed in the skin's colours (SkinTooltip).
@Mixin(TooltipRenderUtil.class)
public abstract class TooltipRenderUtilMixin {
    @Inject(method = "renderTooltipBackground", at = @At("HEAD"), cancellable = true)
    private static void foe$skinFrame(GuiGraphics g, int x, int y, int w, int h, Identifier style, CallbackInfo ci) {
        dannypx.foe.handler.renderer.PetTooltipPrice.background(x, y, w);
        boolean drew = dannypx.foe.handler.logic.ConnectionHandler.instance().isOnServer()
                && dannypx.foe.handler.renderer.SkinTooltip.draw(g, x, y, w, h);
        dannypx.foe.handler.renderer.SkinTooltip.noteBackground(style, drew);
        if (drew) ci.cancel();
    }
}
