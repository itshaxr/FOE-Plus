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
        return insertAt(original, m.end(), FOE_PLUS_MARKER);
    }

    /// The developer's + : bold, cycling through the rainbow (one full turn every 3 seconds).
    public static Component devMarker(long nowMs) {
        float hue = (nowMs % 3000L) / 3000f;
        int rgb = net.minecraft.util.Mth.hsvToRgb(hue, 0.85f, 1f);
        return Component.literal("+").withStyle(style -> style.withColor(rgb).withBold(true));
    }

    /// Puts the developer's rainbow + right after their name (a whole-word match), or returns the
    /// tag unchanged when their name isn't in it.
    public static Component insertDevMarker(Component original, String devName, long nowMs) {
        return insertAfter(original, devName, devMarker(nowMs));
    }

    /// A beta tester's + : bold green.
    public static final Component BETA_MARKER = Component.literal("+").withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.BOLD);

    /// Puts {@code marker} right after {@code name} (a whole-word match), or returns the tag
    /// unchanged when the name isn't in it.
    public static Component insertAfter(Component original, String name, Component marker) {
        if (name == null || name.isEmpty()) return original;
        java.util.regex.Matcher m = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])")
                .matcher(original.getString());
        if (!m.find()) return original;
        return insertAt(original, m.end(), marker);
    }

    private static Component insertAt(Component original, int end, Component marker) {
        MutableComponent out = Component.empty();
        int[] offset = {0};
        boolean[] done = {false};
        original.visit((style, text) -> {
            int start = offset[0];
            int stop = start + text.length();
            if (!done[0] && end > start && end <= stop) {
                out.append(Component.literal(text.substring(0, end - start)).withStyle(style));
                out.append(marker);
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
