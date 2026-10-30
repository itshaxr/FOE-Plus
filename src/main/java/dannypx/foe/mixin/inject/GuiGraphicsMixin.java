package dannypx.foe.mixin.inject;

import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.handler.logic.KeyBindHandler;
import dannypx.foe.handler.renderer.ItemRendererHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import dannypx.foe.config.Configs;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @Shadow public abstract Matrix3x2fStack pose();

    @Shadow public abstract void drawString(Font font, Component component, int x, int y, int color, boolean shadow);

    @Inject(method = "renderItemCount", at = @At("HEAD"), cancellable = true)
    private void renderItemCountInject(Font font, ItemStack stack, int x, int y, String stackCountText, CallbackInfo ci) {
        if(ConnectionHandler.instance().isOnServer()
                && Configs.mainConfig.enableMod.get()
                && Configs.mixinConfig.guiGraphicsMixinRenderItemCount.get()
        ) {
            if(Configs.rendererConfig.useSmallStackCountNumber.get()) {
                ItemRendererHandler.instance().drawStackCount((GuiGraphics) (Object) this, font, stack, x, y);
                ci.cancel();
            } else {
                ItemRendererHandler.instance().drawStackCount((GuiGraphics) (Object) this, font, stack, x, y, false);
                ci.cancel();
            }
        }
    }

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;pushMatrix()Lorg/joml/Matrix3x2fStack;"))
    private void renderItemDecorationsInject(Font font, ItemStack stack, int x, int y, String stackCountString, CallbackInfo ci) {
        if(ConnectionHandler.instance().isOnServer()
                && Configs.mainConfig.enableMod.get()
                && Configs.mixinConfig.guiGraphicsMixinRenderItemDecorations.get()
        ) {
            ItemRendererHandler.instance().drawRarityMarker((GuiGraphics) (Object) this, font, stack, x, y);
            ItemRendererHandler.instance().drawSearchItem((GuiGraphics) (Object) this, stack, x, y);
            ItemRendererHandler.instance().drawPetItemEquipped((GuiGraphics) (Object) this, stack, x, y);
            ItemRendererHandler.instance().drawMaxPetStats((GuiGraphics) (Object) this, stack, x, y);
            ItemRendererHandler.instance().drawBaitStackerMarker((GuiGraphics) (Object) this, font, stack, x, y);
            if(KeyBindHandler.instance().isPressingInspect()) {
                ItemRendererHandler.instance().drawFishSize((GuiGraphics) (Object) this, font, stack, x, y);
                ItemRendererHandler.instance().drawPetRating((GuiGraphics) (Object) this, font, stack, x, y);
                ItemRendererHandler.instance().drawArmorQuality((GuiGraphics) (Object) this, font, stack, x, y);
            }
        }
    }
}
