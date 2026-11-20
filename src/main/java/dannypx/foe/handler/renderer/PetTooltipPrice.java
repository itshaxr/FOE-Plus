package dannypx.foe.handler.renderer;

import dannypx.foe.config.Configs;
import dannypx.foe.item.PetTagObject;
import dannypx.foe.item.ValidateItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A pet's price on your pet price sheet, at the top right of its tooltip, on the row with its name ("Shark Pet ...
 * $200K"). Drawn over the tooltip as the game draws it, like the skin frames ({@link SkinTooltip}): the server's pet
 * tooltips are built so that lines added to an item's tooltip never show, and every row is one of its frame pictures
 * with the text over it. The price ends as far in from the frame's right side as the name starts from its left.
 */
public final class PetTooltipPrice {
    private PetTooltipPrice() {}

    /// The tooltip about to be drawn: the price, the row it goes on, how far down that row is from the tooltip's top,
    /// and how far in its name starts; null when it isn't a pet's (or the price is off).
    private static Component price = null;
    private static int rowY = 0, inset = 0;
    /// Where the game drew the tooltip's box (its content: x, y, width), from its background.
    private static int bx, by, bw;
    private static boolean placed = false;

    /// A tooltip's rows, as the game is about to draw them, for the pet under the mouse (`hovered`).
    public static void prepare(List<ClientTooltipComponent> rows, ItemStack hovered, Font font) {
        price = null;
        placed = false;
        if (rows == null || rows.isEmpty() || hovered == null || hovered.isEmpty()) return;
        if (!Configs.mainConfig.petTooltipPrice.get()) return;
        var pet = ValidateItem.isPet(hovered);
        if (!pet.value1()) return;
        String text = priceText(pet.value2());
        if (text == null) return;
        prepareFor(rows, name(pet.value2()), text, font);
    }

    /// The price `text` for the pet named `name`, on the row with its name (also for the test renders).
    public static void prepareFor(List<ClientTooltipComponent> rows, String name, String text, Font font) {
        price = null;
        placed = false;
        if (text == null) return;
        // the row with the pet's name: found by its name when that's real words (the item's own name can be the
        // frame's top, all pictures), else the first row with words on it (the frame rows are only pictures)
        boolean words = name != null && hasLetters(name);
        // only the rows the server draws inside its frame (each holds a middle piece of it): the first row holds
        // the frame's top piece, drawn well above it, with a copy of the name that isn't what you see
        boolean framed = false;
        for (ClientTooltipComponent row : rows)
            if (row instanceof dannypx.foe.mixin.accessor.ClientTextTooltipAccessor t && hasMiddlePiece(t.foe$text())) { framed = true; break; }
        if (words && place(rows, name.trim(), text, font, framed)) return;
        place(rows, null, text, font, framed);
    }

    /// The middle pieces of the server's tooltip frames (in its pack's default font: common, rare, epic, legendary,
    /// mythical), one on each row inside the frame.
    static final int[] MIDDLE_PIECES = {0xD003, 0xD007, 0xD011, 0xD015, 0xD019};

    static boolean hasMiddlePiece(FormattedCharSequence seq) {
        boolean[] found = {false};
        seq.accept((index, style, cp) -> {
            for (int m : MIDDLE_PIECES) if (cp == m) { found[0] = true; return false; }
            return true;
        });
        return found[0];
    }

    /// Puts the price on the first row holding `name` (null: the first row with words); false when there's none.
    private static boolean place(List<ClientTooltipComponent> rows, String name, String text, Font font, boolean framedOnly) {
        int y = 0;
        for (int i = 0; i < rows.size(); i++) {
            ClientTooltipComponent row = rows.get(i);
            if (row instanceof dannypx.foe.mixin.accessor.ClientTextTooltipAccessor t && (!framedOnly || hasMiddlePiece(t.foe$text()))) {
                int at = name == null ? firstLetter(t.foe$text()) : indexOf(t.foe$text(), name);
                if (at >= 0) {
                    // in the name's own font: the server draws the name in a font that sits its letters lower than
                    // the row (the row is the frame's top), so the price drops to the name's line with it
                    Style nameStyle = styleAt(t.foe$text(), at);
                    price = Component.literal(fitFont(text, nameStyle)).withStyle(st -> nameStyle.withColor(ChatFormatting.GOLD)
                            .withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false)
                            .withClickEvent(null).withHoverEvent(null).withInsertion(null));
                    rowY = y;
                    inset = Math.max(0, font.width(prefix(t.foe$text(), at)));
                    return true;
                }
            }
            // as the game lays them out: each row's height, and 2 more after the first
            y += row.getHeight(font) + (i == 0 ? 2 : 0);
        }
        return false;
    }

    /// The price as the name's font can draw it: a character it hasn't got becomes a plain one it has ("–" → "-"),
    /// or goes ("$"), so there's never a box.
    static String fitFont(String text, Style style) {
        net.minecraft.resources.Identifier font = style.getFont() instanceof net.minecraft.network.chat.FontDescription.Resource r ? r.id() : null;
        if (font == null) return text;
        StringBuilder out = new StringBuilder();
        text.codePoints().forEach(cp -> {
            if (hasGlyph(font, cp)) out.appendCodePoint(cp);
            else if (cp == '–' && hasGlyph(font, '-')) out.append('-');
        });
        return out.toString();
    }

    private static boolean hasGlyph(net.minecraft.resources.Identifier font, int cp) {
        try {
            var fm = ((dannypx.foe.mixin.accessor.MinecraftFontAccessor) net.minecraft.client.Minecraft.getInstance()).foe$fontManager();
            var set = ((dannypx.foe.mixin.accessor.FontManagerAccessor) fm).foe$fontSets().get(font);
            if (set == null) return true;      // not one FOE+ can look into: drawn as it is
            for (var provider : ((dannypx.foe.mixin.accessor.FontSetAccessor) set).foe$activeProviders())
                if (provider.getGlyph(cp) != null) return true;
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    /// The style (font and all) of a row's character number `k`.
    static Style styleAt(FormattedCharSequence seq, int k) {
        Style[] found = {Style.EMPTY};
        int[] n = {0};
        seq.accept((index, style, cp) -> {
            if (n[0]++ == k) { found[0] = style; return false; }
            return true;
        });
        return found[0];
    }

    /// An ordinary letter (not one of the server's pictures, which sit in the Hangul and private ranges).
    static boolean isWordLetter(int cp) {
        return cp < 0x3000 && Character.isLetter(cp);
    }

    static boolean hasLetters(String s) {
        return s.codePoints().anyMatch(PetTooltipPrice::isWordLetter);
    }

    /// The character number of a row's first ordinary letter, or -1.
    static int firstLetter(FormattedCharSequence seq) {
        int[] k = {0}, found = {-1};
        seq.accept((index, style, cp) -> {
            if (isWordLetter(cp)) { found[0] = k[0]; return false; }
            k[0]++;
            return true;
        });
        return found[0];
    }

    /// The tooltip's box, as the game draws its background (x, y, width of its content).
    public static void background(int x, int y, int w) {
        bx = x;
        by = y;
        bw = w;
        placed = true;
    }

    /// After the tooltip is drawn: the price on its row, ending `inset` in from the box's right side.
    public static void draw(GuiGraphics g, Font font) {
        if (price == null || !placed) return;
        int x = bx + bw - inset - font.width(price);
        g.drawString(font, price, x, by + rowY, 0xFFFFFFFF, true);
        price = null;
        placed = false;
    }

    /// "$200K", or "$45K–60K" when it depends on rolls; null when the pet can't be priced.
    static String priceText(PetTagObject pet) {
        long[] p = TradeValueBox.petPrice(pet);
        if (p == null || p[1] <= 0) return null;
        return "$" + dannypx.foe.handler.store.PetPrices.format(p[0])
                + (p[1] > p[0] ? "–" + dannypx.foe.handler.store.PetPrices.format(p[1]) : "");
    }

    private static String name(PetTagObject pet) {
        try {
            return pet.getName().getString().trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /// Where `what` starts among a row's characters, or -1.
    static int indexOf(FormattedCharSequence seq, String what) {
        StringBuilder sb = new StringBuilder();
        List<Integer> starts = new ArrayList<>();
        seq.accept((index, style, cp) -> { starts.add(sb.length()); sb.appendCodePoint(cp); return true; });
        int at = sb.indexOf(what);
        if (at < 0) return -1;
        // the character number it starts at
        for (int k = 0; k < starts.size(); k++) if (starts.get(k) == at) return k;
        return -1;
    }

    /// A row's first `count` characters, as drawn (their fonts and spacing too).
    static FormattedCharSequence prefix(FormattedCharSequence seq, int count) {
        List<Object[]> chars = new ArrayList<>();
        seq.accept((index, style, cp) -> {
            if (chars.size() >= count) return false;
            chars.add(new Object[]{style, cp});
            return true;
        });
        return sink -> {
            for (int k = 0; k < chars.size(); k++)
                if (!sink.accept(k, (Style) chars.get(k)[0], (Integer) chars.get(k)[1])) return false;
            return true;
        };
    }
}
