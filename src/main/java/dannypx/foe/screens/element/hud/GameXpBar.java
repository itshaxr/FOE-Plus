package dannypx.foe.screens.element.hud;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * XP bars the way the game draws them (a pet's tooltip):
 * <pre>
 * ─────────────────────── (99.98M/MAX)
 * </pre>
 * a struck-through row of spaces, the filled part in the game's teal and the rest dark grey, then
 * the numbers in brackets. Panels use these unless "Use FOE+'s XP bars" is on.
 */
public final class GameXpBar {
    private GameXpBar() {}

    /// The game's bar colour (a pet's XP bar).
    public static final int FILL = 0x00ECB0;
    /// How many spaces wide a bar is, as in the game.
    public static final int CHARS = 25;

    /// Whether FOE+'s own drawn bars are used instead. Swappable for tests.
    public static BooleanSupplier FOE_BARS = () -> dannypx.foe.config.Configs.hudConfig.foeXpBars.get();

    /// A HUD's "XP bar colour" setting ("#FF77CC") as RGB, or -1 when it's left at Default (or isn't a colour).
    public static int colour(java.util.function.Supplier<String> setting) {
        try {
            return parse(setting.get());
        } catch (Throwable e) {
            return -1;
        }
    }

    /// Package-private for tests: "#FF77CC" or "FF77CC" → 0xFF77CC; anything else -1.
    public static int parse(String hex) {
        if (hex == null) return -1;
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (!h.matches("[0-9A-Fa-f]{6}")) return -1;
        return Integer.parseInt(h, 16);
    }

    /// The game-look bar's filled colour: the one picked (`c`), or the game's teal.
    public static int fill(int c) {
        return c < 0 ? FILL : c;
    }

    /// A FOE+ bar's light and dark ends (ARGB): from the colour picked (`c`), or the bar's own.
    public static int foeLight(int own, int c) {
        return c < 0 ? own : 0xFF000000 | c;
    }

    public static int foeDark(int own, int c) {
        if (c < 0) return own;
        int r = (c >> 16 & 0xFF) * 55 / 100, g = (c >> 8 & 0xFF) * 55 / 100, b = (c & 0xFF) * 55 / 100;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    public static boolean gameStyle() {
        try {
            return !FOE_BARS.getAsBoolean();
        } catch (Throwable e) {
            return true; // config not loaded yet: the game's look
        }
    }

    /// The bar alone, `chars` spaces wide, filled to `fraction` (in whole spaces, as the game does).
    public static MutableComponent bar(float fraction, int chars) {
        return bar(fraction, chars, -1);
    }

    /// The same, filled in colour `c` (-1: the game's teal).
    public static MutableComponent bar(float fraction, int chars, int c) {
        float f = Math.max(0f, Math.min(1f, Float.isNaN(fraction) ? 0f : fraction));
        int filled = Math.round(f * chars);
        MutableComponent out = Component.empty();
        if (filled > 0) out.append(Component.literal(" ".repeat(filled)).withStyle(s -> s.withColor(fill(c)).withStrikethrough(true)));
        if (filled < chars) out.append(Component.literal(" ".repeat(chars - filled)).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH));
        return out;
    }

    /// "─────── (99.98M/MAX)": the bar and the numbers after it.
    public static Component line(float fraction, int chars, String current, String needed) {
        return Component.empty().append(bar(fraction, chars))
                .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(current).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("/").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(needed).withStyle(ChatFormatting.BLUE))
                .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
    }

    /// 950 → "950", 23500 → "23.50K", 99_980_000 → "99.98M", as the game writes XP.
    public static String num(double v) {
        double a = Math.abs(v);
        if (a < 1_000) return String.valueOf(Math.round(v));
        String[] units = {"K", "M", "B", "T"};
        int u = -1;
        while (a >= 1_000 && u < units.length - 1) { a /= 1_000; v /= 1_000; u++; }
        // cut, never rounded up: 99.999M is still "99.99M", not "100.00M"
        double cut = Math.floor(Math.abs(v) * 100) / 100 * Math.signum(v);
        return String.format(Locale.ROOT, "%.2f", cut) + units[u];
    }
}
