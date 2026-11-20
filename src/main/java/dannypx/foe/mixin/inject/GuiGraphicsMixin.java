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
    /// A tooltip about to be drawn, with its lines as they'll show (however it was made): for a pet wearing a skin,
    /// the server's frame (pictures in its font) recoloured in the skin's colours (SkinTooltip).
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V",
            at = @org.spongepowered.asm.mixin.injection.At("HEAD"), argsOnly = true)
    private java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> foe$skinFrame(
            java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> lines) {
        if (!dannypx.foe.handler.logic.ConnectionHandler.instance().isOnServer()) return lines;
        long _p = dannypx.foe.handler.logic.FoeProfiler.mark();
        var out = dannypx.foe.handler.renderer.SkinTooltip.recolour(lines);
        dannypx.foe.helper.Safe.run("pet tooltip price", () -> dannypx.foe.handler.renderer.PetTooltipPrice.prepare(out,
                dannypx.foe.handler.renderer.SkinTooltip.hovered(), net.minecraft.client.Minecraft.getInstance().font));
        dannypx.foe.handler.logic.FoeProfiler.end("items: tooltip skin", _p);
        return out;
    }

    /// The tooltip drawn: a pet's price on its name's row (PetTooltipPrice), over the text.
    @Inject(method = "renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V",
            at = @At("TAIL"))
    private void foe$petPrice(Font font, java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> lines,
                              int x, int y, net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner positioner,
                              net.minecraft.resources.Identifier style, CallbackInfo ci) {
        GuiGraphics g = (GuiGraphics) (Object) this;
        dannypx.foe.helper.Safe.run("pet tooltip price", () -> dannypx.foe.handler.renderer.PetTooltipPrice.draw(g, font));
    }

    @Shadow public abstract Matrix3x2fStack pose();

    @Shadow public abstract void drawString(Font font, Component component, int x, int y, int color, boolean shadow);

    @Inject(method = "renderItemCount", at = @At("HEAD"), cancellable = true)
    private void renderItemCountInject(Font font, ItemStack stack, int x, int y, String stackCountText, CallbackInfo ci) {
        if(ConnectionHandler.instance().isOnServer()
                && Configs.mainConfig.enableMod.get()
                && Configs.mixinConfig.guiGraphicsMixinRenderItemCount.get()
        ) {
            long _p = dannypx.foe.handler.logic.FoeProfiler.mark();
            GuiGraphics g = (GuiGraphics) (Object) this;
            int depth = dannypx.foe.helper.Safe.depth(g.pose());
            try {
                if(Configs.rendererConfig.useSmallStackCountNumber.get()) {
                    ItemRendererHandler.instance().drawStackCount(g, font, stack, x, y);
                } else {
                    ItemRendererHandler.instance().drawStackCount(g, font, stack, x, y, false);
                }
            } catch (Throwable t) {
                dannypx.foe.helper.Safe.recover("items: stack counts", t, g, depth);
            }
            dannypx.foe.handler.logic.FoeProfiler.end("items: stack counts", _p);
            ci.cancel();
        }
    }

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;pushMatrix()Lorg/joml/Matrix3x2fStack;"))
    private void renderItemDecorationsInject(Font font, ItemStack stack, int x, int y, String stackCountString, CallbackInfo ci) {
        if(ConnectionHandler.instance().isOnServer()
                && Configs.mainConfig.enableMod.get()
                && Configs.mixinConfig.guiGraphicsMixinRenderItemDecorations.get()
        ) {
            long _p = dannypx.foe.handler.logic.FoeProfiler.mark();
            GuiGraphics g = (GuiGraphics) (Object) this;
            // a marker that can't read an item (the server changed its lore) is skipped, never a crash
            int depth = dannypx.foe.helper.Safe.depth(g.pose());
            try {
                ItemRendererHandler.instance().drawRarityMarker(g, font, stack, x, y);
                ItemRendererHandler.instance().drawSearchItem(g, stack, x, y);
                ItemRendererHandler.instance().drawPetItemEquipped(g, stack, x, y);
                ItemRendererHandler.instance().drawMaxPetStats(g, stack, x, y);
                ItemRendererHandler.instance().drawMaxRodPart(g, stack, x, y);
                ItemRendererHandler.instance().drawBaitStackerMarker(g, font, stack, x, y);
                KeyBindHandler keys = KeyBindHandler.instance();
                if(keys.isInspecting(dannypx.foe.type.InspectAlways.Kind.OTHER)) {
                    ItemRendererHandler.instance().drawFishSize(g, font, stack, x, y);
                }
                if(keys.isInspecting(dannypx.foe.type.InspectAlways.Kind.PET)) {
                    ItemRendererHandler.instance().drawPetRating(g, font, stack, x, y);
                } else {
                    ItemRendererHandler.instance().drawPetRatingLetter(g, font, stack, x, y);
                }
                if(keys.isInspecting(dannypx.foe.type.InspectAlways.Kind.ARMOR)) {
                    ItemRendererHandler.instance().drawArmorQuality(g, font, stack, x, y);
                }
            } catch (Throwable t) {
                dannypx.foe.helper.Safe.recover("items: markers", t, g, depth);
            }
            dannypx.foe.handler.logic.FoeProfiler.end("items: markers on each item", _p);
        }
    }
}
