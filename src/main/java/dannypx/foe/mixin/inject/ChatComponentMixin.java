package dannypx.foe.mixin.inject;

import dannypx.foe.config.Configs;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Short badges in chat (HUD settings → Short badges in chat): every line that reaches the chat, the server's, other
 * players' and FOE+'s own, has its rank, rarity and variant badges drawn short (a letter on the badge's colour).
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), argsOnly = true)
    private Component foeplus$shortBadges(Component message, Component same, MessageSignature signature, GuiMessageTag tag) {
        if (message == null || !Configs.hudConfig.chatShortRarityBadges.get()) return message;
        return dannypx.foe.helper.Safe.get("chat short badges", () -> dannypx.foe.screens.element.hud.PanelElement.shortBadges(
                dannypx.foe.helper.SimpleTagFont.apply(message, true, true)), message);
    }
}
