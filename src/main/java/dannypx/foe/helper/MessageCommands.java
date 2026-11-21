package dannypx.foe.helper;

import java.util.Locale;
import java.util.Set;

/// The private message commands ("/msg Steve hi", "/r hi"), and where the message in one starts.
public final class MessageCommands {
    private MessageCommands() {}

    /// Followed by a player, then the message.
    private static final Set<String> TO_PLAYER = Set.of("msg", "tell", "w", "whisper", "m", "pm", "dm", "message");
    /// Followed by the message (a reply to whoever last messaged you).
    private static final Set<String> REPLY = Set.of("r", "reply");

    /// Where the message starts in the typed text, or -1 when it isn't a private message (or there's no message yet).
    public static int messageStart(String typed) {
        if (typed == null || !typed.startsWith("/")) return -1;
        int space = typed.indexOf(' ');
        if (space < 0) return -1;
        String command = typed.substring(1, space).toLowerCase(Locale.ROOT);
        if (REPLY.contains(command)) return space + 1;
        if (!TO_PLAYER.contains(command)) return -1;
        int afterName = typed.indexOf(' ', space + 1);
        return afterName < 0 ? -1 : afterName + 1;
    }
}
