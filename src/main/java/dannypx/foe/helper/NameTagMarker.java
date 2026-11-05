package dannypx.foe.helper;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Optional;
import java.util.regex.Pattern;

/// The gold + shown after the names of players who use FOE+ (only on the name tag above them).
public final class NameTagMarker {
    private NameTagMarker() {}

    /// A 1-pixel space (the smallest there is) between a name and its +, so they never touch.
    /// It lives in FOE+'s own "gap" font and is never bold, which would make it wider.
    public static final Component GAP = Component.literal("\uE001").withStyle(style -> style
            .withFont(new net.minecraft.network.chat.FontDescription.Resource(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(dannypx.foe.FishOnMCExtras.MOD_ID, "gap"))));

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

    /// Colours players can pick for their + (O menu). Green is the beta testers' and rainbow the
    /// developer's, so neither is in the list. Index 0 (gold) is the default.
    public record PlusColor(String name, int rgb) {}
    public static final java.util.List<PlusColor> PLUS_COLORS = java.util.List.of(
            new PlusColor("Gold", 0xFFAA00), new PlusColor("Yellow", 0xFFFF55), new PlusColor("Orange", 0xFF8A1F),
            new PlusColor("Red", 0xFF5555), new PlusColor("Dark Red", 0xAA0000), new PlusColor("Pink", 0xFF77CC),
            new PlusColor("Light Purple", 0xFF55FF), new PlusColor("Dark Purple", 0xAA00AA), new PlusColor("Blue", 0x5555FF),
            new PlusColor("Dark Blue", 0x0000AA), new PlusColor("Aqua", 0x55FFFF), new PlusColor("Dark Aqua", 0x00AAAA),
            new PlusColor("Dark Green", 0x00AA00), new PlusColor("White", 0xFFFFFF), new PlusColor("Gray", 0xAAAAAA),
            new PlusColor("Dark Gray", 0x555555));

    /// Colour value meaning "my special +" (rainbow for the developer, green for beta testers).
    /// -1 means nothing picked yet, which is the special + for them and gold for everyone else.
    public static final int SPECIAL = 16;

    public static boolean isListColor(int index) {
        return index >= 0 && index < PLUS_COLORS.size();
    }

    public static PlusColor plusColor(int index) {
        return PLUS_COLORS.get(index >= 0 && index < PLUS_COLORS.size() ? index : 0);
    }

    /// A FOE+ user's + in their chosen colour.
    public static Component colorMarker(int index) {
        int rgb = plusColor(index).rgb();
        return Component.literal("+").withStyle(style -> style.withColor(rgb).withBold(true));
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
                out.append(GAP);
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
