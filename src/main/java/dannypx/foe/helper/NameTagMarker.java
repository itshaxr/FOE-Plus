package dannypx.foe.helper;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Optional;
import java.util.regex.Pattern;

/// The gold + shown after the names of players who use FOE+ (only on the name tag above them).
public final class NameTagMarker {
    private NameTagMarker() {}

    private static final Component FOE_PLUS_MARKER = Component.literal("+").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD);

    /// Puts a + right after the first FOE+ user's name in the name tag, keeping every other part
    /// (colours, fonts, the line below) exactly as the server sent it. 
    public static Component insertAfterName(Component original, Pattern names) {
        java.util.regex.Matcher m = names.matcher(original.getString());
        if (!m.find()) return original;
        int end = m.end();
        MutableComponent out = Component.empty();
        int[] offset = {0};
        boolean[] done = {false};
        original.visit((style, text) -> {
            int start = offset[0];
            int stop = start + text.length();
            if (!done[0] && end > start && end <= stop) {
                out.append(Component.literal(text.substring(0, end - start)).withStyle(style));
                out.append(FOE_PLUS_MARKER);
                if (end < stop) out.append(Component.literal(text.substring(end - start)).withStyle(style));
                done[0] = true;
            } else {
                out.append(Component.literal(text).withStyle(style));
            }
            offset[0] = stop;
            return Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }

}
