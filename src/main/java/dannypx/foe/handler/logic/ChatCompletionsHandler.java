package dannypx.foe.handler.logic;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket;

import java.util.List;

/**
 * Tab completes the server's chat tags: typing "[it" and pressing Tab gives "[item]" (the server shows the
 * item you're holding in its place). They're added to the game's own list of chat completions, the one
 * player names come from; put back if the server ever replaces that list.
 */
public final class ChatCompletionsHandler {
    private ChatCompletionsHandler() {}

    static final List<String> TAGS = List.of("[item]");
    private static int ticks = 0;

    public static void tick(Minecraft mc) {
        if (mc.getConnection() == null || ++ticks % 40 != 0) return;
        var provider = mc.getConnection().getSuggestionsProvider();
        if (provider.getCustomTabSugggestions().containsAll(TAGS)) return;
        List<String> missing = TAGS.stream().filter(t -> !provider.getCustomTabSugggestions().contains(t)).toList();
        provider.modifyCustomCompletions(ClientboundCustomChatCompletionsPacket.Action.ADD, missing);
    }
}
