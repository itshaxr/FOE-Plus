package dannypx.foe.handler.renderer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A pet wearing a skin gets its tooltip framed in the skin's own colours: the colours its name has on the
 * "Equipped Skin:" line (a Werewolf's gold all round; a Vacation Koala from pink at the top through yellow to green
 * at the bottom), in place of the server's frame, on a dark tooltip. Read from the tooltip's lines as they're drawn,
 * so it works in every menu, for every skin, however the tooltip was made.
 */
public final class SkinTooltip {
    private SkinTooltip() {}

    private static int[] pending = null;
    private static long pendingAt = 0L;

    private static net.minecraft.world.item.ItemStack hovered = null;

    /// The item under the mouse in a menu (for the dump).
    public static void hover(net.minecraft.world.item.ItemStack stack) {
        hovered = stack;
    }

    private static String itemInfo(net.minecraft.world.item.ItemStack s) {
        if (s == null || s.isEmpty()) return "ITEM none\n";
        StringBuilder b = new StringBuilder("ITEM ").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem())).append('\n');
        b.append("  model ").append(s.get(net.minecraft.core.component.DataComponents.ITEM_MODEL)).append('\n');
        b.append("  modeldata ").append(s.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA)).append('\n');
        var data = s.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        b.append("  data ").append(data == null ? "none" : data.copyTag().toString()).append('\n');
        return b.toString();
    }

    /// The item models loaded whose name mentions bait (the server's pack's), to see how they're named.
    private static String baitModels() {
        StringBuilder b = new StringBuilder("BAIT MODELS\n");
        try {
            var models = ((dannypx.foe.mixin.accessor.ModelManagerAccessor) net.minecraft.client.Minecraft.getInstance().getModelManager()).foe$itemModels();
            int n = 0;
            for (var id : models.keySet()) {
                if (id.getNamespace().equals("minecraft") && !id.getPath().contains("bait")) continue;
                if (!id.toString().contains("bait") && n > 300) continue;
                b.append("  ").append(id).append('\n');
                if (++n > 800) break;
            }
        } catch (RuntimeException e) {
            b.append("  ?").append(e).append('\n');
        }
        return b.toString();
    }

    /// /foe tooltip dump: the next tooltip drawn is written down, line by line, with what FOE+ made of it.
    private static boolean dumpArmed = false;
    private static StringBuilder dump = null;

    private static int dumpNo = 0;

    public static void armDump() {
        dumpArmed = true;
        dumpNo++;
        dump = null;
    }

    /// The frame drawing ran for the tooltip being dumped: note its frame style and whether FOE+ drew its own.
    public static void noteBackground(net.minecraft.resources.Identifier style, boolean drew) {
        if (dump == null) return;
        dump.append("\nFRAME style=").append(style).append(" foeDrew=").append(drew).append('\n');
        writeDump();
        dump = null;
    }

    private static void writeDump() {
        java.nio.file.Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("fishonmcextras").resolve("tooltip_dump_" + dumpNo + ".txt");
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, dump.toString());
            dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.line("TOOLTIP", dannypx.foe.helper.FoeChat.GREEN)
                    .append(dannypx.foe.helper.FoeChat.text("Saved to config/fishonmcextras/tooltip_dump_" + dumpNo + ".txt.")));
        } catch (java.io.IOException e) {
            dannypx.foe.handler.logic.LoggerHandler.error(e);
        }
    }

    /// An item tooltip's lines, as the game asks for it to be drawn (this frame's tooltip).
    public static void lines(List<Component> lines) {
        if (dumpArmed && lines != null && !lines.isEmpty()) {
            dumpArmed = false;
            dump = new StringBuilder("TOOLTIP " + lines.size() + " lines\n");
            for (Component l : lines) dump.append("LINE ").append(dannypx.foe.handler.logic.MinigameDumpHandler.describe(l)).append('\n');
            int[] c = colours(lines);
            for (Component l : lines) l.visit((style, str) -> {
                str.codePoints().filter(cp -> cp >= 0xD000 && cp <= 0xD7FF).forEach(cp -> {
                    dannypx.foe.handler.logic.GlyphDump.saveGlyph(DEFAULT_FONT, cp);
                    if (isFramePieceCode(cp)) {
                        int twin = twinOf(cp);
                        dump.append("FRAME PIECE ").append(Integer.toHexString(cp)).append(" white twin ").append(twin < 0 ? "none" : Integer.toHexString(twin)).append('\n');
                        if (twin >= 0) dannypx.foe.handler.logic.GlyphDump.saveGlyph(DEFAULT_FONT, twin);
                    }
                });
                return java.util.Optional.empty();
            }, Style.EMPTY);
            dump.append("SKIN COLOURS ").append(c == null ? "none" : java.util.Arrays.toString(c)).append('\n');
            dump.append(itemInfo(hovered)).append(baitModels());
            writeDump();     // saved now too, in case the game's frame drawing never runs for it
        }
        pending = lines == null || !dannypx.foe.config.Configs.rendererConfig.skinTooltipFrames.get() ? null : colours(lines);
        pendingAt = System.currentTimeMillis();
    }

    //region The server's own frame (pictures in its font), recoloured
    private static final net.minecraft.resources.Identifier DEFAULT_FONT = net.minecraft.resources.Identifier.withDefaultNamespace("default");
    /// Per frame piece: the same piece in white (another code in the server's font), or -1 when it isn't a frame
    /// piece or has no white twin.
    private static final java.util.Map<Integer, Integer> twins = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Boolean> pieces = new java.util.HashMap<>();
    static { dannypx.foe.handler.logic.MemoryGuard.register("skinTooltip", () -> twins.size() + pieces.size(), () -> { twins.clear(); pieces.clear(); }); }

    private static boolean isFramePieceCode(int cp) {
        return pieces.computeIfAbsent(cp, k -> isFramePiece(dannypx.foe.handler.logic.GlyphDump.pixels(DEFAULT_FONT, k)));
    }

    /// A frame piece: a picture at least 100 pixels wide in the default font (the server draws each tooltip row's
    /// border as one such picture).
    static boolean isFramePiece(int[] px) {
        return px != null && px[0] >= 100;
    }

    /// Package-private for tests: how well `b` is a white version of `a` (0-1): where `a` has coloured border
    /// pixels, `b` has pale grey or white ones; elsewhere they're alike in see-through-ness.
    static float whiteTwinScore(int[] a, int[] b) {
        if (a == null || b == null || a[0] != b[0] || a[1] != b[1]) return 0f;
        int border = 0, pale = 0, mask = 0, same = 0;
        for (int i = 2; i < a.length; i++) {
            int ca = a[i], cb = b[i];
            boolean oa = (ca >>> 24) > 0x40, ob = (cb >>> 24) > 0x40;
            mask++;
            if (oa == ob) same++;
            if (!oa) continue;
            int ra = ca >> 16 & 255, ga = ca >> 8 & 255, ba = ca & 255;
            if (Math.max(ra, Math.max(ga, ba)) - Math.min(ra, Math.min(ga, ba)) < 60) continue;   // not a coloured pixel
            border++;
            int rb = cb >> 16 & 255, gb = cb >> 8 & 255, bb = cb & 255;
            int hi = Math.max(rb, Math.max(gb, bb)), lo = Math.min(rb, Math.min(gb, bb));
            if (ob && hi - lo < 40 && hi > 120) pale++;
        }
        if (border == 0 || same < mask * 0.95f) return 0f;
        return pale / (float) border;
    }

    private static int twinOf(int cp) {
        Integer known = twins.get(cp);
        if (known != null) return known;
        int found = -1;
        int[] mine = dannypx.foe.handler.logic.GlyphDump.pixels(DEFAULT_FONT, cp);
        if (isFramePiece(mine)) {
            float best = 0.8f;
            for (int c = Math.max(0xD000, cp - 0x80); c <= Math.min(0xD7FF, cp + 0x80); c++) {
                if (c == cp) continue;
                float sc = whiteTwinScore(mine, dannypx.foe.handler.logic.GlyphDump.pixels(DEFAULT_FONT, c));
                if (sc > best) { best = sc; found = c; }
            }
        }
        twins.put(cp, found);
        return found;
    }

    /// A tooltip about to be drawn: a pet wearing a skin has the server's frame pieces swapped for their white
    /// versions, tinted row by row in the skin's colours, top to bottom. Anything else is left as it is.
    public static java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> recolour(
            java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> lines) {
        if (lines == null) return lines;
        java.util.List<Component> text = new java.util.ArrayList<>();
        for (var line : lines) if (line instanceof dannypx.foe.mixin.accessor.ClientTextTooltipAccessor t) text.add(toComponent(t.foe$text()));
        lines(text);
        int[] c = current();
        if (c == null) return lines;
        java.util.List<Integer> frameRows = new java.util.ArrayList<>();
        for (int i = 0; i < lines.size(); i++)
            if (lines.get(i) instanceof dannypx.foe.mixin.accessor.ClientTextTooltipAccessor t && framePieceIn(t.foe$text()) >= 0) frameRows.add(i);
        if (frameRows.isEmpty()) return lines;
        java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> out = new java.util.ArrayList<>(lines);
        for (int n = 0; n < frameRows.size(); n++) {
            int i = frameRows.get(n);
            var seq = ((dannypx.foe.mixin.accessor.ClientTextTooltipAccessor) lines.get(i)).foe$text();
            int colour = along(c, frameRows.size() == 1 ? 0f : n / (float) (frameRows.size() - 1));
            java.util.List<Object[]> chars = new java.util.ArrayList<>();
            boolean[] changed = {false};
            int[] spaces = {0};
            seq.accept((index, style, cp) -> { if (cp == ' ') spaces[0]++; return true; });
            boolean divider = spaces[0] >= 20;        // a row of spaces drawn as a line
            seq.accept((index, style, cp) -> {
                int twin = cp >= 0xD000 && cp <= 0xD7FF && isDefaultFont(style) && isFramePieceCode(cp) ? twinOf(cp) : -1;
                String badge = isDefaultFont(style) ? badgeWord(cp) : null;
                if (twin >= 0) {
                    chars.add(new Object[]{style.withColor(colour), twin});
                    changed[0] = true;
                } else if (badge != null) {
                    // the rarity box: its word instead, bold, in the skin's first colour
                    Style word = style.withColor(c[0]).withBold(true);
                    for (char ch : badge.toCharArray()) chars.add(new Object[]{word, (int) ch});
                    changed[0] = true;
                } else if (divider && cp == ' ' && style.getColor() != null && coloured(style.getColor().getValue())) {
                    // a coloured divider: in the skin's colour for where it is
                    chars.add(new Object[]{style.withColor(colour), cp});
                    changed[0] = true;
                } else chars.add(new Object[]{style, cp});
                return true;
            });
            // the pet's own numbers and the discoverer, in the skin's colours too
            StringBuilder sb = new StringBuilder();
            int[] was = new int[chars.size()];
            for (int k = 0; k < chars.size(); k++) {
                sb.appendCodePoint((Integer) chars.get(k)[1]);
                net.minecraft.network.chat.TextColor tc = ((Style) chars.get(k)[0]).getColor();
                was[k] = tc == null ? -1 : tc.getValue() & 0xFFFFFF;
            }
            int[] now = textColours(cpList(sb), was, colour, c);
            for (int k = 0; k < chars.size(); k++)
                if (now[k] >= 0) { chars.set(k, new Object[]{((Style) chars.get(k)[0]).withColor(now[k]), chars.get(k)[1]}); changed[0] = true; }
            if (!changed[0]) continue;
            out.set(i, new net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip(sink -> {
                for (int k = 0; k < chars.size(); k++)
                    if (!sink.accept(k, (Style) chars.get(k)[0], (Integer) chars.get(k)[1])) return false;
                return true;
            }));
        }
        return out;
    }

    private static int[] cpList(CharSequence s) {
        return s.codePoints().toArray();
    }

    private static int indexOf(int[] cps, String what) {
        int[] w = what.codePoints().toArray();
        outer:
        for (int i = 0; i + w.length <= cps.length; i++) {
            for (int j = 0; j < w.length; j++) if (cps[i + j] != w[j]) continue outer;
            return i;
        }
        return -1;
    }

    /// Package-private for tests: new colours for one tooltip row's characters (`cps`, their colours `was`, -1 for
    /// none), or -1 where one stays: the pet level and its XP numbers in the row's skin colour, the Luck / Scale lines'
    /// names and values (up to their first ")") in it too (dark parts a darker shade of it), and the discoverer's
    /// name in all the skin's colours along it.
    static int[] textColours(int[] cps, int[] was, int row, int[] stops) {
        int[] out = new int[cps.length];
        java.util.Arrays.fill(out, -1);
        int dim = mix(row, 0x000000, 0.55f);
        int at;
        if ((at = indexOf(cps, "ᴘᴇᴛ ʟᴇᴠᴇʟ:")) >= 0) {
            for (int k = at + (int) "ᴘᴇᴛ ʟᴇᴠᴇʟ:".codePoints().count(); k < cps.length; k++) if (cps[k] != ' ') out[k] = row;
            return out;
        }
        if ((at = indexOf(cps, "Discoverer:")) >= 0) {
            int from = at + 11;
            while (from < cps.length && cps[from] == ' ') from++;
            int to = cps.length;
            while (to > from && cps[to - 1] == ' ') to--;
            for (int k = from; k < to; k++) out[k] = along(stops, to - from <= 1 ? 0f : (k - from) / (float) (to - from - 1));
            return out;
        }
        for (String label : new String[]{"ʟᴜᴄᴋ:", "sᴄᴀʟᴇ:"}) {
            if ((at = indexOf(cps, label)) < 0) continue;
            int end = at;
            while (end < cps.length && cps[end] != ')') end++;
            if (end >= cps.length) end = at + label.length() - 1;
            for (int k = at; k <= end && k < cps.length; k++) {
                if (cps[k] == ' ') continue;
                out[k] = was[k] >= 0 && dark(was[k]) && cps[k] != '(' && cps[k] != ')' ? dim : row;
            }
            return out;
        }
        // the XP numbers under the level: "(165.91M/MAX)" at the end of a row of spaces
        int close = cps.length - 1;
        while (close >= 0 && cps[close] == ' ') close--;
        if (close > 0 && cps[close] == ')') {
            int open = close;
            while (open >= 0 && cps[open] != '(' && cps[open] != ' ') open--;
            if (open >= 0 && cps[open] == '(' && open + 1 < close) {
                String inside = new String(cps, open + 1, close - open - 1);
                if (inside.matches("[0-9.,]+[KMBT]?/(MAX|[0-9.,]+[KMBT]?)"))
                    for (int k = open; k <= close; k++) out[k] = row;
            }
        }
        return out;
    }

    /// Dark enough to be a value's dim half ("/+171" in #31544B, or dark grey).
    private static boolean dark(int rgb) {
        return Math.max(rgb >> 16 & 255, Math.max(rgb >> 8 & 255, rgb & 255)) < 0x70;
    }

    /// Package-private for tests: the word for one of the server's rarity boxes (\uF033 common ... \uF037 mythical),
    /// or null.
    static String badgeWord(int cp) {
        return switch (cp) {
            case 0xF033 -> "COMMON";
            case 0xF034 -> "RARE";
            case 0xF035 -> "EPIC";
            case 0xF036 -> "LEGENDARY";
            case 0xF037 -> "MYTHICAL";
            default -> null;
        };
    }

    /// A colour that's a colour, not a grey.
    static boolean coloured(int rgb) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 40;
    }

    private static boolean isDefaultFont(Style style) {
        return style.getFont() == null || style.getFont().equals(net.minecraft.network.chat.FontDescription.DEFAULT);
    }

    /// The first frame piece in a drawn line, or -1.
    private static int framePieceIn(net.minecraft.util.FormattedCharSequence seq) {
        int[] found = {-1};
        seq.accept((index, style, cp) -> {
            if (cp >= 0xD000 && cp <= 0xD7FF && isDefaultFont(style) && isFramePieceCode(cp)) { found[0] = cp; return false; }
            return true;
        });
        return found[0];
    }
    //endregion

    /// A drawn tooltip line back as text with its colours.
    public static Component toComponent(net.minecraft.util.FormattedCharSequence seq) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        if (seq == null) return out;
        seq.accept((index, style, cp) -> {
            out.append(Component.literal(new String(Character.toChars(cp))).withStyle(style));
            return true;
        });
        return out;
    }

    /// The frame's colours, top to bottom, for the tooltip being drawn, or null to leave it as it is.
    static int[] current() {
        if (pending == null || System.currentTimeMillis() - pendingAt > 250) return null;
        return pending;
    }

    /// Package-private for tests: the skin's colours from the "Equipped Skin: Name" line, in order, a colour once
    /// for each run of letters in it (so a gradient name gives its stops), or null when there's no skin.
    static int[] colours(List<Component> lore) {
        for (Component line : lore) {
            String text = line.getString();
            int at = text.indexOf("Equipped Skin:");
            if (at < 0) continue;
            int nameFrom = at + "Equipped Skin:".length();
            String rest = text.substring(nameFrom).trim();
            if (rest.isEmpty() || rest.equalsIgnoreCase("default") || rest.equalsIgnoreCase("none") || rest.equalsIgnoreCase("empty")) return null;
            List<Integer> cols = new ArrayList<>();
            int[] pos = {0};
            line.visit((style, s) -> {
                for (int i = 0; i < s.length(); i++) {
                    int p = pos[0]++;
                    if (p < nameFrom || Character.isWhitespace(s.charAt(i))) continue;
                    if (style.getColor() != null) {
                        int c = style.getColor().getValue() & 0xFFFFFF;
                        if (cols.isEmpty() || cols.getLast() != c) cols.add(c);
                    }
                }
                return Optional.empty();
            }, Style.EMPTY);
            if (cols.isEmpty()) return null;
            int[] out = new int[cols.size()];
            for (int i = 0; i < out.length; i++) out[i] = cols.get(i);
            return out;
        }
        return null;
    }

    /// Package-private for tests: the colour `t` (0-1) of the way from `a` to `b`.
    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /// Package-private for tests: the colour `t` (0-1) along all the stops.
    static int along(int[] stops, float t) {
        if (stops.length == 1) return stops[0];
        float f = Math.max(0f, Math.min(1f, t)) * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) f);
        return mix(stops[i], stops[i + 1], f - i);
    }

    /// Draws the tooltip's box round the text at (x, y), w by h, framed in the skin's colours. True when drawn.
    public static boolean draw(GuiGraphics g, int x, int y, int w, int h) {
        int[] c = current();
        if (c == null) return false;
        int x0 = x - 3, y0 = y - 3, x1 = x + w + 3, y1 = y + h + 3;
        int bg = 0xF0121318;
        g.fill(x0, y0 - 1, x1, y0, bg);
        g.fill(x0, y1, x1, y1 + 1, bg);
        g.fill(x0 - 1, y0, x1 + 1, y1, bg);
        int rows = Math.max(1, y1 - y0 - 1);
        int top = c[0], bottom = c[c.length - 1];
        // The frame a pixel in from the edge: the top in the name's first colour, down the sides through its
        // colours, the bottom in its last.
        g.fill(x0, y0, x1, y0 + 1, 0xFF000000 | top);
        g.fill(x0, y1 - 1, x1, y1, 0xFF000000 | bottom);
        for (int i = 1; i < rows; i++) {
            int col = 0xFF000000 | along(c, i / (float) rows);
            g.fill(x0, y0 + i, x0 + 1, y0 + i + 1, col);
            g.fill(x1 - 1, y0 + i, x1, y0 + i + 1, col);
        }
        // A small accent in each corner, in the gap before the text (never over it).
        int ct = 0xFF000000 | top, cb = 0xFF000000 | bottom;
        g.fill(x0 + 1, y0 + 1, x0 + 2, y0 + 2, ct); g.fill(x1 - 2, y0 + 1, x1 - 1, y0 + 2, ct);
        g.fill(x0 + 1, y1 - 2, x0 + 2, y1 - 1, cb); g.fill(x1 - 2, y1 - 2, x1 - 1, y1 - 1, cb);
        return true;
    }
}
