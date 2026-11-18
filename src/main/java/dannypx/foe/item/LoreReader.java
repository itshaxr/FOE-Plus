package dannypx.foe.item;

import dannypx.foe.helper.TextHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an item's tooltip by what it says, not by how its parts are nested.
 *
 * <p>The server's tooltips start every line with its frame (" 퀑    ": frame glyphs, a piece number
 * and a few spaces), then the text. How that text is split into parts changes from item to item
 * and update to update (one flat list on fish; the frame as the line itself with the text nested
 * under it on pets and armour), so FOE+ reading "part 2 of line 15" broke. Here a line is flattened
 * into runs of text with their style, the frame is taken off the front, and things are found by
 * their label ("ʀᴀᴛɪɴɢ:", "ꞯᴜᴀʟɪᴛʏ:") or what they are (the rarity badge glyph).
 */
public final class LoreReader {
    private LoreReader() {}

    /// A piece of text in one style.
    public record Run(String text, Style style) {}

    /// The frame at the start of a line: two glyphs, a space, the piece, a space, four glyphs and up
    /// to three spaces.
    static final Pattern FRAME = Pattern.compile("^[\\uE000-\\uF8FF]{2} \\S [\\uE000-\\uF8FF]{4} {0,3}");

    /// Every piece of text on the line, in order, with its style (however it's nested).
    public static List<Run> runs(Component line) {
        List<Run> out = new ArrayList<>();
        if (line == null) return out;
        line.visit((style, text) -> {
            if (!text.isEmpty()) out.add(new Run(text, style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /// How many characters at the start of the line are its frame (0 when it has none).
    public static int frameLength(String text) {
        Matcher m = FRAME.matcher(text);
        return m.find() ? m.end() : 0;
    }

    /// The line's runs without its frame.
    public static List<Run> content(Component line) {
        List<Run> all = runs(line);
        int skip = frameLength(line == null ? "" : line.getString());
        List<Run> out = new ArrayList<>();
        for (Run r : all) {
            if (skip >= r.text().length()) { skip -= r.text().length(); continue; }
            out.add(skip > 0 ? new Run(r.text().substring(skip), r.style()) : r);
            skip = 0;
        }
        return out;
    }

    /// The line's frame, as drawn (empty when it has none).
    public static MutableComponent frame(Component line) {
        MutableComponent out = Component.empty();
        int left = frameLength(line == null ? "" : line.getString());
        for (Run r : runs(line)) {
            if (left <= 0) break;
            String part = r.text().length() <= left ? r.text() : r.text().substring(0, left);
            out.append(Component.literal(part).withStyle(r.style()));
            left -= part.length();
        }
        return out;
    }

    static boolean isGlyph(int cp) {
        return cp >= 0xE000 && cp <= 0xF8FF;
    }

    /// The badge glyphs on the line (a rarity), each in its own style; empty when there are none.
    public static MutableComponent glyphs(Component line) {
        MutableComponent out = Component.empty();
        for (Run r : content(line)) {
            StringBuilder g = new StringBuilder();
            r.text().codePoints().filter(LoreReader::isGlyph).forEach(g::appendCodePoint);
            if (!g.isEmpty()) out.append(Component.literal(g.toString()).withStyle(r.style()));
        }
        return out;
    }

    /// The line's text after its badge glyphs (a fish's variant on the rarity line), in its style.
    public static Component afterGlyphs(Component line) {
        MutableComponent out = Component.empty();
        boolean seen = false;
        for (Run r : content(line)) {
            String t = r.text();
            int last = -1;
            for (int i = 0; i < t.length(); i++) if (isGlyph(t.charAt(i))) last = i;
            if (last >= 0) { seen = true; t = t.substring(last + 1); }
            if (!seen) continue;
            if (!t.isBlank()) out.append(Component.literal(t).withStyle(r.style()));
        }
        return TextHelper.trim(out);
    }

    /// The first line whose text has `label` (small caps or not, any case), or null.
    public static Component findLine(List<Component> lore, String label) {
        String want = norm(label);
        for (Component line : lore) if (norm(line.getString()).contains(want)) return line;
        return null;
    }

    static String norm(String s) {
        return TextHelper.normalLetter(s).toLowerCase(Locale.ROOT).replace('ꞯ', 'q');
    }

    /// What follows `label` on the line: the first run after it with something in it, not a "[..]"
    /// or "(..)" (so "ʀᴀᴛɪɴɢ: ɢʀᴇᴀᴛ [75%]" gives "ɢʀᴇᴀᴛ"), in its own colour. Empty if none.
    public static Component valueAfter(Component line, String label) {
        if (line == null) return Component.empty();
        String want = norm(label);
        StringBuilder sofar = new StringBuilder();
        boolean found = false;
        for (Run r : content(line)) {
            String t = r.text();
            if (!found) {
                int before = sofar.length();
                sofar.append(norm(t));
                int at = sofar.indexOf(want);
                if (at < 0) continue;
                found = true;
                int cut = at + want.length() - before; // where the label ends in this run
                t = cut >= 0 && cut <= t.length() ? t.substring(cut) : "";
            }
            String v = t.trim();
            if (v.isEmpty() || v.equals(":")) continue;
            if (v.startsWith("[") || v.startsWith("(")) continue;
            int bracket = v.indexOf('[');
            if (bracket > 0) v = v.substring(0, bracket).trim();
            return Component.literal(v).withStyle(r.style());
        }
        return Component.empty();
    }

    /// The first run on the line with a "%" in it ("96%"), in its colour. Empty if none.
    public static Component percent(Component line) {
        if (line == null) return Component.empty();
        for (Run r : content(line)) {
            String t = r.text().trim();
            if (t.contains("%")) return Component.literal(t.replace("[", "").replace("]", "").trim()).withStyle(r.style());
        }
        return Component.empty();
    }
}
