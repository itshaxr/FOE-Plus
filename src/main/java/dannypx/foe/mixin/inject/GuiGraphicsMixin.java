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

    /// Hovering something in chat: an item there (a shared item's [name]) gets what a menu's tooltip gets: a pet
    /// its price, a bait its worth (that one through the item's tooltip lines). A bait the server sends as text
    /// only (its name and lines, no item) gets its worth here, at your price for it.
    @Inject(method = "renderComponentHoverEffect", at = @At("HEAD"), cancellable = true)
    private void foe$chatHover(Font font, net.minecraft.network.chat.Style style, int x, int y, CallbackInfo ci) {
        if (style == null || !ConnectionHandler.instance().isOnServer() || !Configs.mainConfig.enableMod.get()) return;
        try {
            if (style.getHoverEvent() instanceof net.minecraft.network.chat.HoverEvent.ShowItem(ItemStack item)) {
                // only on the item's name, not anywhere on its message
                if (dannypx.foe.helper.ChatHoverSpot.onName(style.getHoverEvent(), item.getHoverName().getString(), x, y) == Boolean.FALSE) {
                    ci.cancel();
                    return;
                }
                dannypx.foe.handler.renderer.SkinTooltip.hoverInChat(item);
                dannypx.foe.handler.fetch.TooltipHandler.noteChatHover("item", item.getHoverName().getString()
                        + " | pet " + dannypx.foe.item.ValidateItem.isPet(item).value1() + " | data " + (item.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA) != null));
            } else if (style.getHoverEvent() instanceof net.minecraft.network.chat.HoverEvent.ShowText(Component text)) {
                Component worth = Configs.mainConfig.baitTooltipValue.get() ? dannypx.foe.handler.fetch.TooltipHandler.baitTextWithValue(text) : null;
                // a pet shared as text: its price from your Pet Prices sheet
                if (worth == null && Configs.mainConfig.petTooltipPrice.get()) worth = dannypx.foe.handler.fetch.TooltipHandler.petTextWithPrice(text);
                // a shared bait or pet (its lines, then the server's "Sent at"): only on its name, not anywhere on its
                // message; elsewhere only when it was sent
                String sent = dannypx.foe.helper.ChatHoverSpot.sentAt(text);
                boolean item = worth != null || (sent != null && text.getString().split("\n").length > 2);
                if (item && dannypx.foe.helper.ChatHoverSpot.onName(style.getHoverEvent(), dannypx.foe.helper.ChatHoverSpot.firstLine(text), x, y) == Boolean.FALSE) {
                    if (sent != null) {
                        GuiGraphics g = (GuiGraphics) (Object) this;
                        g.setTooltipForNextFrame(font, font.split(Component.literal(sent).withStyle(net.minecraft.ChatFormatting.GRAY), 300), x, y);
                    }
                    ci.cancel();
                    return;
                }
                if (worth == null) dannypx.foe.handler.fetch.TooltipHandler.noteChatHover("text", text.getString());
                if (worth != null) {
                    GuiGraphics g = (GuiGraphics) (Object) this;
                    g.setTooltipForNextFrame(font, font.split(worth, Math.max(this.foe$width() / 2, 200)), x, y);
                    ci.cancel();
                }
            }
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError e) throw e;
            dannypx.foe.helper.Safe.report("GuiGraphicsMixin.chatHover", t);
        }
    }

    private int foe$width() {
        return ((GuiGraphics) (Object) this).guiWidth();
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
