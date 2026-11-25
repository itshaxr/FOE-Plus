package dannypx.foe.helper.wiki;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lays a wiki page (its HTML tree) out for the wiki panel, like a browser does: headings, paragraphs that wrap,
 * bold/italic/coloured text, links, lists, tables with their columns sized to fit, pictures, rules and code. The result
 * is a list of things to draw (each at its place on the page), where the links are, and where each anchor is.
 */
public final class WikiLayout {
    public sealed interface Op permits Text, Fill, Pic {}

    /// Text at (x, y), `scale` times the font's size.
    public record Text(int x, int y, Component text, float scale) implements Op {}

    public record Fill(int x, int y, int w, int h, int argb) implements Op {}

    /// A picture (its address), drawn at this size.
    public record Pic(int x, int y, int w, int h, String url) implements Op {}

    /// Where a link is (and the words the website shows when you point at it, if any).
    public record Hit(int x, int y, int w, int h, String href, String tip) {}

    public final List<Op> ops = new ArrayList<>();
    public final List<Hit> hits = new ArrayList<>();
    public final Map<String, Integer> anchors = new HashMap<>();
    public int height;
    /// How many pictures were laid out at a guessed size (still on their way): the page is laid out again when they come.
    public int guessed;

    public static final int TEXT = 0xDDDDDD, LINK = 0x55AAFF, LINK_MISSING = 0xFF6666, LINK_OUT = 0x88CCFF, CODE = 0xC8E6FF;
    /// Pictures are shown at this many GUI pixels per website pixel.
    static final float PICTURE_SCALE = 0.5f;
    static final int LINE_GAP = 2, BLOCK_GAP = 5, CELL_PAD = 3;

    private final Font font;
    /// The hover words of the part being laid out (the wiki's "tooltip" boxes), for its links.
    private String tip;

    private WikiLayout(Font font) {
        this.font = font;
    }

    /// The page laid out `width` GUI pixels wide.
    public static WikiLayout of(Html.Node root, Font font, int width) {
        WikiLayout l = new WikiLayout(font);
        Flow f = l.new Flow(0, width, 0, l.ops, l.hits);
        l.walk(root, f, Style.EMPTY.withColor(TEXT), 1f, null, false);
        f.endLine();
        f.clearFloats();
        l.height = f.y + 4;
        return l;
    }

    /// One column of text being filled: words go along the current line, onto the next when it's full.
    final class Flow {
        final int x0, width;
        int y, x;
        int lineH;
        boolean space;
        int maxRight;
        boolean lineEmpty = true;
        int lastGapY = -1;
        /// Where its content ends (below the last line, before any gap after it).
        int contentEnd;
        final int startY;
        /// Words longer than the column are cut (not while measuring a table's columns).
        boolean split = true;
        final List<Op> out;
        final List<Hit> hits;
        final List<Op> line = new ArrayList<>();
        final List<Hit> lineHits = new ArrayList<>();
        int align = 0;   // 0 left, 1 centre, 2 right

        Flow(int x0, int width, int y, List<Op> out, List<Hit> hits) {
            this.x0 = x0;
            this.width = Math.max(10, width);
            this.y = y;
            this.startY = y;
            this.contentEnd = y;
            this.out = out;
            this.hits = hits;
        }

        int textH(float scale) {
            return Math.round(font.lineHeight * scale);
        }

        /// Boxes side by side (the wiki's tiles and columns, a picture on the left): where they are now.
        boolean inFloats;
        int floatTop, floatBottom, floatL, floatR;
        /// Where the current row of boxes starts in what's drawn and in the links (to centre the row when it's done).
        int rowOps, rowHits;

        /// A finished row of boxes on the left: put in the middle (the tiles don't hug the left side).
        void centreRow() {
            if (floatR != width || floatL <= 4) return;   // a box on the right: left as the page has it
            int dx = (width - (floatL - 4)) / 2;
            if (dx <= 0) return;
            for (int i = rowOps; i < out.size(); i++) out.set(i, shift(out.get(i), dx, 0));
            for (int i = rowHits; i < hits.size(); i++) {
                Hit h = hits.get(i);
                hits.set(i, new Hit(h.x() + dx, h.y(), h.w(), h.h(), h.href(), h.tip()));
            }
        }

        /// Where the next box `w` wide goes (beside the last ones, or on a new row of them).
        int[] floatAt(int w, boolean right) {
            endLine();
            w = Math.min(w, width);
            if (!inFloats) {
                inFloats = true;
                floatTop = y;
                floatBottom = y;
                floatL = 0;
                floatR = width;
                rowOps = out.size();
                rowHits = hits.size();
            } else if (floatR - floatL < w) {
                centreRow();
                floatTop = floatBottom + 4;
                floatL = 0;
                floatR = width;
                rowOps = out.size();
                rowHits = hits.size();
            }
            return new int[]{x0 + (right ? floatR - w : floatL), floatTop};
        }

        void floatDone(int w, int h, boolean right) {
            if (right) floatR -= w + 4;
            else floatL += w + 4;
            floatBottom = Math.max(floatBottom, floatTop + h);
            maxRight = Math.max(maxRight, right ? width : floatL - 4);
        }

        /// What comes next goes below the boxes.
        void clearFloats() {
            if (!inFloats) return;
            centreRow();
            inFloats = false;
            y = Math.max(y, floatBottom + LINE_GAP);
            contentEnd = Math.max(contentEnd, y);
            lastGapY = -1;
        }

        void word(String w, Style st, float scale, String href) {
            if (w.isEmpty()) return;
            if (inFloats && lineEmpty) clearFloats();
            Component c = Component.literal(w).withStyle(st);
            int ww = Math.round(font.width(c) * scale);
            int sw = space && !lineEmpty ? Math.round(font.width(Component.literal(" ").withStyle(st)) * scale) : 0;
            if (!lineEmpty && x + sw + ww > width) {
                endLine();
                sw = 0;
            }
            if (split && ww > width && lineEmpty) {
                // longer than the column: split it
                int cut = Math.max(1, font.plainSubstrByWidth(w, Math.max(1, (int) (width / scale))).length());
                if (cut < w.length()) {
                    word(w.substring(0, cut), st, scale, href);
                    endLine();
                    word(w.substring(cut), st, scale, href);
                    return;
                }
            }
            if (sw > 0 && href != null && !lineHits.isEmpty()) {
                // the space inside a link is part of it
                Hit last = lineHits.get(lineHits.size() - 1);
                if (last.href().equals(href) && last.x() + last.w() == x0 + x) lineHits.set(lineHits.size() - 1, new Hit(last.x(), last.y(), last.w() + sw, last.h(), href, last.tip()));
            }
            x += sw;
            line.add(new Text(x0 + x, y, c, scale));
            if (href != null) lineHits.add(new Hit(x0 + x, y, ww, textH(scale), href, tip));
            x += ww;
            lineH = Math.max(lineH, textH(scale));
            space = false;
            lineEmpty = false;
            maxRight = Math.max(maxRight, x);
        }

        void box(int w, int h, Op op, String href) {
            if (inFloats && lineEmpty) clearFloats();
            if (!lineEmpty && x + (space ? 4 : 0) + w > width) endLine();
            if (space && !lineEmpty) x += 4;
            line.add(shift(op, x0 + x, y));
            if (href != null) lineHits.add(new Hit(x0 + x, y, w, h, href, tip));
            x += w;
            lineH = Math.max(lineH, h);
            space = false;
            lineEmpty = false;
            maxRight = Math.max(maxRight, x);
        }

        void endLine() {
            if (lineEmpty) {
                line.clear();
                lineHits.clear();
                return;
            }
            int dx = align == 1 ? (width - x) / 2 : align == 2 ? width - x : 0;
            for (Op op : line) {
                // everything on a line sits on its bottom (text beside a picture, as on the website)
                int dy = op instanceof Text t ? lineH - textH(t.scale()) : op instanceof Pic p ? lineH - p.h() : 0;
                out.add(shift(op, dx, dy));
            }
            for (Hit h : lineHits) hits.add(new Hit(h.x() + dx, h.y() + lineH - h.h(), h.w(), h.h(), h.href(), h.tip()));
            line.clear();
            lineHits.clear();
            y += lineH + LINE_GAP;
            contentEnd = y;
            x = 0;
            lineH = 0;
            space = false;
            lineEmpty = true;
        }

        /// A new block starts: the line ends, and a little gap (only one, however many blocks start here).
        void block(int gap) {
            endLine();
            clearFloats();
            contentEnd = Math.max(contentEnd, y);
            if (lastGapY != y && y > startY) {
                y += gap;
                lastGapY = y;
            }
        }

        void lineBreak(float scale) {
            if (lineEmpty) y += textH(scale) + LINE_GAP;
            else endLine();
        }
    }

    static Op shift(Op op, int dx, int dy) {
        if (dx == 0 && dy == 0) return op;
        if (op instanceof Text t) return new Text(t.x() + dx, t.y() + dy, t.text(), t.scale());
        if (op instanceof Fill f) return new Fill(f.x() + dx, f.y() + dy, f.w(), f.h(), f.argb());
        Pic p = (Pic) op;
        return new Pic(p.x() + dx, p.y() + dy, p.w(), p.h(), p.url());
    }

    private static final Pattern COLOR = Pattern.compile("(?i)(?:^|;)\\s*color\\s*:\\s*(#*[0-9a-f]{6}\\b|#+[0-9a-f]{3}\\b|rgb\\(([^)]*)\\)|[a-z]+)");
    private static final Pattern BG = Pattern.compile("(?i)background(?:-color)?\\s*:\\s*(#[0-9a-f]{3,6})");
    private static final Pattern FONT_SIZE = Pattern.compile("(?i)font-size\\s*:\\s*(\\d+(?:\\.\\d+)?)\\s*(px|%|em)");
    private static final Pattern WIDTH = Pattern.compile("(?i)(?:^|;)\\s*width\\s*:\\s*(\\d+)\\s*(px|%)");

    static int parseColour(String v) {
        if (v == null) return -1;
        v = v.trim().toLowerCase(Locale.ROOT);
        try {
            if (v.startsWith("#") || v.matches("[0-9a-f]{6}")) {
                String h = v.replaceAll("^#+", "");
                if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
                return Integer.parseInt(h, 16);
            }
            if (v.startsWith("rgb(")) {
                String[] p = v.substring(4, v.length() - 1).split(",");
                return (Integer.parseInt(p[0].trim()) & 0xFF) << 16 | (Integer.parseInt(p[1].trim()) & 0xFF) << 8 | (Integer.parseInt(p[2].trim()) & 0xFF);
            }
        } catch (RuntimeException ignored) {
            return -1;
        }
        ChatFormatting f = ChatFormatting.getByName(v.replace("grey", "gray"));
        if (f != null && f.getColor() != null) return f.getColor();
        return switch (v) {
            case "orange" -> 0xFFA500;
            case "purple" -> 0x800080;
            case "brown" -> 0xA52A2A;
            case "pink" -> 0xFFC0CB;
            default -> -1;
        };
    }

    /// Bright enough to read on the panel's dark background (a dark colour the website shows on white is lightened).
    static int readable(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        double lum = 0.299 * r + 0.587 * g + 0.114 * b;
        if (lum >= 90) return rgb;
        double k = (90 - lum) / 255.0 + 0.35;
        r = (int) Math.min(255, r + (255 - r) * k);
        g = (int) Math.min(255, g + (255 - g) * k);
        b = (int) Math.min(255, b + (255 - b) * k);
        return r << 16 | g << 8 | b;
    }

    private void walk(Html.Node n, Flow f, Style st, float scale, String href, boolean pre) {
        if (n.text != null) {
            text(n.text, f, st, scale, href, pre);
            return;
        }
        String tag = n.tag;
        String id = n.attrs.get("id");
        if (id != null && !id.isEmpty()) anchors.put(id, f.y);
        // inline styles: colour
        Style s = st;
        Matcher cm = COLOR.matcher(n.attr("style"));
        if (cm.find()) {
            int c = parseColour(cm.group(1));
            if (c >= 0) s = s.withColor(TextColor.fromRgb(readable(c)));
        }
        Matcher fs = FONT_SIZE.matcher(n.attr("style"));
        if (fs.find()) {
            // the website's sizes (16 px is ordinary text): big words stay big, a little smaller is left as it is
            float v = Float.parseFloat(fs.group(1));
            float k = switch (fs.group(2).toLowerCase(Locale.ROOT)) {
                case "px" -> v / 16f;
                case "%" -> v / 100f;
                default -> v;
            };
            if (k >= 1.1f || k <= 0.85f) scale = Math.max(0.75f, Math.min(2f, scale * k));
        }
        if (n.attr("style").replace(" ", "").toLowerCase(Locale.ROOT).contains("font-weight:bold")) s = s.withBold(true);
        if ("font".equals(tag) && !n.attr("color").isEmpty()) {
            int c = parseColour(n.attr("color"));
            if (c >= 0) s = s.withColor(TextColor.fromRgb(readable(c)));
        }
        switch (tag) {
            case "#root", "html", "body", "tbody", "thead", "tfoot" -> kids(n, f, s, scale, href, pre);
            case "br" -> f.lineBreak(scale);
            case "b", "strong" -> kids(n, f, s.withBold(true), scale, href, pre);
            case "i", "em", "cite" -> kids(n, f, s.withItalic(true), scale, href, pre);
            case "u", "ins" -> kids(n, f, s.withUnderlined(true), scale, href, pre);
            case "del", "s", "strike" -> kids(n, f, s.withStrikethrough(true), scale, href, pre);
            case "code", "kbd", "tt", "samp" -> kids(n, f, s.withColor(CODE), scale, href, pre);
            case "small", "sup", "sub" -> kids(n, f, s, scale, href, pre);
            case "a" -> {
                String h = n.attr("href");
                if (h.isEmpty()) {
                    kids(n, f, s, scale, href, pre);
                } else {
                    boolean out = n.hasClass("urlextern") || n.hasClass("interwiki") || (h.startsWith("http") && !h.contains("wiki.fishonmc.net"));
                    int colour = out ? LINK_OUT : n.hasClass("wikilink2") ? LINK_MISSING : LINK;
                    // pictures in a link keep their own look
                    Style ls = n.byTag("img") != null && n.text().isEmpty() ? s : s.withColor(colour);
                    kids(n, f, ls, scale, h, pre);
                }
            }
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                int level = tag.charAt(1) - '0';
                float hs = switch (level) {
                    case 1 -> 1.6f;
                    case 2 -> 1.35f;
                    case 3 -> 1.15f;
                    default -> 1f;
                };
                int colour = switch (level) {
                    case 1 -> 0xFFD866;
                    case 2 -> 0xFFAA00;
                    case 3 -> 0x7FDBFF;
                    default -> 0xFFFFFF;
                };
                f.block(level <= 2 ? BLOCK_GAP + 4 : BLOCK_GAP + 1);
                if (id != null && !id.isEmpty()) anchors.put(id, f.y);
                kids(n, f, Style.EMPTY.withColor(colour).withBold(true), hs, href, false);
                f.endLine();
                if (level <= 2) {
                    f.out.add(new Fill(f.x0, f.y - 1, f.width, 1, 0x50FFFFFF));
                    f.y += 3;
                }
                f.lastGapY = -1;
                f.block(2);
            }
            case "div" -> {
                if (n.hasClass("li")) kids(n, f, s, scale, href, pre);
                else blockOf(n, f, s, scale, href, pre);
            }
            case "p", "section", "article", "main", "header", "footer", "center", "figure", "figcaption", "dt", "address", "details", "summary", "nav", "aside" -> {
                blockOf(n, f, s, scale, href, pre);
            }
            case "dd", "blockquote" -> {
                f.block(BLOCK_GAP);
                Flow sub = new Flow(f.x0 + 12, f.width - 12, f.y, f.out, f.hits);
                int startY = f.y;
                kids(n, sub, s, scale, href, pre);
                sub.endLine();
                if ("blockquote".equals(tag)) f.out.add(new Fill(f.x0 + 4, startY, 2, Math.max(4, sub.y - startY), 0x80AAAAAA));
                f.y = sub.y;
                f.maxRight = Math.max(f.maxRight, sub.maxRight + 12);
                f.block(BLOCK_GAP);
            }
            case "ul", "ol", "dl", "menu" -> {
                // a list inside a list item sits close
                boolean nested = n.parent != null && ("li".equals(n.parent.tag) || n.parent.hasClass("li"));
                f.block(nested ? 1 : BLOCK_GAP);
                int i = 0;
                for (Html.Node c : n.children) {
                    if (c.text != null) {
                        if (!c.text.isBlank()) text(c.text, f, s, scale, href, pre);
                        continue;
                    }
                    if (!"li".equals(c.tag)) {
                        walk(c, f, s, scale, href, pre);
                        continue;
                    }
                    i++;
                    f.endLine();
                    String bullet = "ol".equals(tag) ? i + "." : "•";
                    f.out.add(new Text(f.x0 + 2, f.y, Component.literal(bullet).withStyle(s.withColor(0xAAAAAA)), scale));
                    Flow sub = new Flow(f.x0 + 14, f.width - 14, f.y, f.out, f.hits);
                    kids(c, sub, s, scale, href, pre);
                    sub.endLine();
                    f.y = Math.max(sub.y, f.y + f.textH(scale) + LINE_GAP);
                    f.maxRight = Math.max(f.maxRight, sub.maxRight + 14);
                }
                f.block(nested ? 1 : BLOCK_GAP);
            }
            case "li" -> {
                f.block(2);
                f.out.add(new Text(f.x0 + 2, f.y, Component.literal("•").withStyle(s.withColor(0xAAAAAA)), scale));
                Flow sub = new Flow(f.x0 + 14, f.width - 14, f.y, f.out, f.hits);
                kids(n, sub, s, scale, href, pre);
                sub.endLine();
                f.y = sub.y;
            }
            case "hr" -> {
                f.block(BLOCK_GAP);
                f.out.add(new Fill(f.x0, f.y, f.width, 1, 0x60FFFFFF));
                f.y += 3;
                f.block(BLOCK_GAP);
            }
            case "pre" -> {
                f.block(BLOCK_GAP);
                int startY = f.y, at = f.out.size();
                Flow sub = new Flow(f.x0 + 4, f.width - 8, f.y + 3, f.out, f.hits);
                kids(n, sub, s.withColor(CODE), scale, href, true);
                sub.endLine();
                f.out.add(at, new Fill(f.x0, startY, f.width, sub.y - startY + 2, 0x60000000));
                f.y = sub.y + 4;
                f.block(BLOCK_GAP);
            }
            case "img" -> image(n, f, href);
            case "table" -> table(n, f, s, scale);
            case "input", "label", "option" -> { }
            default -> kids(n, f, s, scale, href, pre);
        }
    }

    /// A block (a paragraph, a div): on lines of its own, maybe centred, maybe in a box, maybe beside the next ones
    /// (the wiki's tiles and columns).
    private void blockOf(Html.Node n, Flow f, Style s, float scale, String href, boolean pre) {
        // DokuWiki's section edit buttons and the like, and the words shown only when you point at something
        if (n.hasClass("secedit") || n.hasClass("editbutton_section") || n.hasClass("docInfo") || n.hasClass("wrap_tooltiptext")) return;
        String style = n.attr("style").replace(" ", "").toLowerCase(Locale.ROOT);
        String oldTip = tip;
        if (n.hasClass("wrap_tooltip")) {
            Html.Node t = n.byClass("wrap_tooltiptext");
            if (t != null && !t.text().isBlank()) tip = t.text();
        }
        boolean box = n.hasClass("wrap_box") || n.hasClass("infobox") || style.contains("border:");
        int bgc = -1;
        Matcher bg = BG.matcher(n.attr("style"));
        if (bg.find()) bgc = parseColour(bg.group(1));
        boolean infobox = n.hasClass("infobox");
        boolean right = !infobox && (n.hasClass("wrap_right") || style.contains("float:right"));
        boolean floating = !infobox && (right || n.hasClass("wrap_left") || style.contains("float:left"));
        int cssW = -1;
        Matcher wm = WIDTH.matcher(n.attr("style"));
        if (wm.find()) {
            int v = Integer.parseInt(wm.group(1));
            cssW = "%".equals(wm.group(2)) ? f.width * Math.min(100, v) / 100 : Math.round(v * PICTURE_SCALE);
        }
        int pad = box ? 5 : 0;
        if (floating) {
            // beside the others: its own width, or as wide as what's in it
            int w = cssW > 0 ? cssW : measure(n, s, scale, 4000) + pad * 2;
            w = Math.max(16, Math.min(w, f.width));
            int[] at = f.floatAt(w, right);
            int idx = f.out.size();
            Flow sub = new Flow(at[0] + pad, w - pad * 2, at[1] + pad, f.out, f.hits);
            sub.align = alignOf(n, style, 0);
            kids(n, sub, s, scale, href, pre);
            sub.endLine();
            sub.clearFloats();
            int h = sub.contentEnd - at[1] + pad;
            decorate(f.out, idx, at[0], at[1], w, h, box, bgc);
            f.floatDone(w, h, right);
            tip = oldTip;
            return;
        }
        f.block(BLOCK_GAP);
        boolean center = n.hasClass("wrap_center") || infobox;
        if (box || bgc >= 0 || cssW > 0 || center) {
            // a box: narrower when the page says so, centred when it says so (an info box: a card in the middle, wide
            // enough for its picture, with nothing squeezed beside it)
            int w = infobox ? Math.min(f.width, 280) : cssW > 0 ? Math.min(cssW, f.width) : center ? Math.max(60, f.width * 3 / 5) : f.width;
            if (infobox) pad = 7;
            int x = f.x0 + (center || (f.align == 1 && w < f.width) ? (f.width - w) / 2 : 0);
            int top = f.y;
            int idx = f.out.size();
            Flow sub = new Flow(x + pad, w - pad * 2, top + pad, f.out, f.hits);
            sub.align = infobox ? 1 : alignOf(n, style, f.align);
            kids(n, sub, s, scale, href, pre);
            sub.endLine();
            sub.clearFloats();
            int h = sub.contentEnd - top + pad;
            decorate(f.out, idx, x, top, w, h, box, bgc);
            f.y = top + h;
            f.contentEnd = f.y;
            f.maxRight = Math.max(f.maxRight, x - f.x0 + w);
            f.block(BLOCK_GAP);
            tip = oldTip;
            return;
        }
        int oldAlign = f.align;
        f.align = alignOf(n, style, f.align);
        kids(n, f, s, scale, href, pre);
        f.endLine();
        f.align = oldAlign;
        f.block(BLOCK_GAP);
        tip = oldTip;
    }

    private static int alignOf(Html.Node n, String style, int inherited) {
        if ("center".equals(n.tag) || style.contains("text-align:center") || n.hasClass("mediacenter") || n.hasClass("centeralign")
                || n.hasClass("wrap_centeralign")) return 1;
        if (style.contains("text-align:right") || n.hasClass("rightalign") || n.hasClass("wrap_rightalign")) return 2;
        if (style.contains("text-align:left") || n.hasClass("leftalign") || n.hasClass("wrap_leftalign")) return 0;
        return inherited;
    }

    /// A box's background and edge, under what's in it.
    private static void decorate(List<Op> out, int idx, int x, int y, int w, int h, boolean box, int bgc) {
        if (!box && bgc < 0) return;
        List<Op> under = new ArrayList<>();
        under.add(new Fill(x, y, w, h, bgc >= 0 ? 0xC0000000 | (bgc & 0xFFFFFF) : 0x24FFFFFF));
        if (box) {
            int edge = 0x38FFFFFF;
            under.add(new Fill(x + 1, y, w - 2, 1, edge));
            under.add(new Fill(x + 1, y + h - 1, w - 2, 1, edge));
            under.add(new Fill(x, y + 1, 1, h - 2, edge));
            under.add(new Fill(x + w - 1, y + 1, 1, h - 2, edge));
        }
        out.addAll(idx, under);
    }

    private void kids(Html.Node n, Flow f, Style st, float scale, String href, boolean pre) {
        for (Html.Node c : n.children) walk(c, f, st, scale, href, pre);
    }

    private void text(String t, Flow f, Style st, float scale, String href, boolean pre) {
        if (pre) {
            String[] lines = t.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) f.lineBreak(scale);
                String l = lines[i].replace("\t", "    ");
                if (!l.isEmpty()) {
                    // keep the spaces: one word for the whole line (cut where it's too long)
                    f.word(l.replace(' ', ' '), st, scale, href);
                }
            }
            return;
        }
        int i = 0, n = t.length();
        while (i < n) {
            char c = t.charAt(i);
            if (Character.isWhitespace(c) && c != ' ') {
                f.space = true;
                i++;
                continue;
            }
            int j = i;
            while (j < n && !(Character.isWhitespace(t.charAt(j)) && t.charAt(j) != ' ')) j++;
            f.word(t.substring(i, j), st, scale, href);
            i = j;
        }
    }

    private void image(Html.Node n, Flow f, String href) {
        String url = WikiNet.absolute(n.attr("src"));
        if (url.isEmpty()) return;
        int w = intAttr(n, "width"), h = intAttr(n, "height");
        WikiNet.Picture p = WikiNet.picture(url);
        if (p != null) {
            if (w <= 0 && h <= 0) { w = p.width(); h = p.height(); }
            else if (w <= 0) w = Math.round(h * p.width() / (float) p.height());
            else if (h <= 0) h = Math.round(w * p.height() / (float) p.width());
        } else {
            if (w <= 0 || h <= 0) guessed++;
            if (w <= 0 && h <= 0) { w = 64; h = 64; }
            else if (w <= 0) w = h;
            else if (h <= 0) h = w;
        }
        // at half the website's size, like its text (a 32 px icon at 16, the size of an item); never below 16
        int big = Math.max(w, h);
        float k = big <= 16 ? 1f : Math.max(16f / big, PICTURE_SCALE);
        String imgStyle = n.attr("style").replace(" ", "").toLowerCase(Locale.ROOT);
        if (imgStyle.contains("max-width:100%") && intAttr(n, "width") <= 0 && p != null) k = Math.min(1f, f.width / (float) w);
        int dw = Math.max(1, Math.round(w * k)), dh = Math.max(1, Math.round(h * k));
        if (f.split && dw > f.width) {
            dh = Math.max(1, Math.round(dh * f.width / (float) dw));
            dw = f.width;
        }
        String link = href;
        boolean center = n.hasClass("mediacenter"), right = n.hasClass("mediaright");
        if (n.hasClass("medialeft") || right) {
            // beside what comes next, as on the website
            int[] at = f.floatAt(dw, right);
            f.out.add(new Pic(at[0], at[1], dw, dh, url));
            if (link != null) f.hits.add(new Hit(at[0], at[1], dw, dh, link, tip));
            f.floatDone(dw, dh, right);
            return;
        }
        if (center) {
            f.endLine();
            int oldAlign = f.align;
            f.align = 1;
            f.box(dw, dh, new Pic(0, 0, dw, dh, url), link);
            f.endLine();
            f.align = oldAlign;
        } else {
            f.box(dw, dh, new Pic(0, 0, dw, dh, url), link);
        }
    }

    private static int intAttr(Html.Node n, String name) {
        String v = n.attr(name).replaceAll("[^0-9]", "");
        if (v.isEmpty()) {
            Matcher m = Pattern.compile("(?<![-a-z])" + name + "\\s*:\\s*(\\d+)\\s*(?:px)?\\s*(?:;|$)").matcher(n.attr("style"));
            if (m.find()) v = m.group(1);
        }
        try {
            return v.isEmpty() ? -1 : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private record Cell(Html.Node node, boolean head, int span, int col) {}

    private void table(Html.Node table, Flow f, Style st, float scale) {
        f.block(BLOCK_GAP);
        List<List<Cell>> rows = new ArrayList<>();
        collectRows(table, rows);
        if (rows.isEmpty()) return;
        int cols = 0;
        for (List<Cell> r : rows) {
            int c = 0;
            for (Cell cell : r) c += cell.span();
            cols = Math.max(cols, c);
        }
        if (cols == 0) return;
        // each column's widest (one line) and narrowest (its longest word) need
        int[] nat = new int[cols], min = new int[cols];
        for (List<Cell> r : rows) {
            for (Cell cell : r) {
                if (cell.span() != 1) continue;
                Style cs = cell.head() ? st.withBold(true) : st;
                nat[cell.col()] = Math.max(nat[cell.col()], measure(cell.node(), cs, scale, 4000));
                min[cell.col()] = Math.max(min[cell.col()], measure(cell.node(), cs, scale, 1));
            }
        }
        int avail = f.width - (cols + 1) - cols * CELL_PAD * 2;
        int[] w = new int[cols];
        int sumNat = 0, sumMin = 0;
        for (int i = 0; i < cols; i++) {
            nat[i] = Math.max(nat[i], 8);
            min[i] = Math.max(Math.min(min[i], nat[i]), 8);
            sumNat += nat[i];
            sumMin += min[i];
        }
        if (sumNat <= avail) {
            System.arraycopy(nat, 0, w, 0, cols);
        } else if (sumMin >= avail) {
            for (int i = 0; i < cols; i++) w[i] = Math.max(8, min[i] * avail / Math.max(1, sumMin));
        } else {
            int extra = avail - sumMin, span = Math.max(1, sumNat - sumMin);
            for (int i = 0; i < cols; i++) w[i] = min[i] + (int) ((long) extra * (nat[i] - min[i]) / span);
        }
        int tableW = 1;
        for (int i = 0; i < cols; i++) tableW += w[i] + CELL_PAD * 2 + 1;
        // centred when what it's in is (the wiki centres most of its tables)
        int left = f.x0 + (f.align == 1 ? Math.max(0, (f.width - tableW) / 2) : f.align == 2 ? Math.max(0, f.width - tableW) : 0);
        int[] colX = new int[cols + 1];
        colX[0] = left;
        for (int i = 0; i < cols; i++) colX[i + 1] = colX[i] + w[i] + CELL_PAD * 2 + 1;
        int y = f.y;
        int border = 0xFF3A4A58;
        int rowIndex = 0;
        for (List<Cell> r : rows) {
            List<Op> rowOps = new ArrayList<>();
            List<Hit> rowHits = new ArrayList<>();
            int rowH = CELL_PAD * 2 + font.lineHeight;
            List<int[]> cellBoxes = new ArrayList<>();
            for (Cell cell : r) {
                int x = colX[cell.col()] + 1 + CELL_PAD;
                int end = Math.min(cols, cell.col() + cell.span());
                int cw = colX[end] - colX[cell.col()] - 1 - CELL_PAD * 2;
                Flow cf = new Flow(x, cw, y + CELL_PAD, rowOps, rowHits);
                String al = cell.node().attr("class") + " " + cell.node().attr("style");
                if (al.contains("centeralign") || al.contains("text-align:center") || al.contains("text-align: center")) cf.align = 1;
                else if (al.contains("rightalign") || al.contains("text-align:right")) cf.align = 2;
                int opsAt = rowOps.size(), hitsAt = rowHits.size();
                kids(cell.node(), cf, cell.head() ? st.withBold(true).withColor(0xFFFFFF) : st, scale, null, false);
                cf.endLine();
                cf.clearFloats();
                // its content, and the padding around it
                int contentH = Math.max(0, cf.contentEnd - (y + CELL_PAD) - LINE_GAP);
                rowH = Math.max(rowH, contentH + CELL_PAD * 2);
                cellBoxes.add(new int[]{colX[cell.col()], colX[end], cell.head() ? 1 : 0, opsAt, rowOps.size(), hitsAt, rowHits.size(), contentH});
            }
            // each cell's content in the middle of its row's height, as on the website
            for (int[] b : cellBoxes) {
                int dy = (rowH - CELL_PAD * 2 - b[7]) / 2;
                if (dy <= 0) continue;
                for (int i = b[3]; i < b[4]; i++) rowOps.set(i, shift(rowOps.get(i), 0, dy));
                for (int i = b[5]; i < b[6]; i++) {
                    Hit h = rowHits.get(i);
                    rowHits.set(i, new Hit(h.x(), h.y() + dy, h.w(), h.h(), h.href(), h.tip()));
                }
            }
            // the cells' backgrounds, then their contents, then the lines between them
            for (int[] b : cellBoxes) {
                int bgc = b[2] == 1 ? 0xFF2A3540 : (rowIndex & 1) == 1 ? 0x18FFFFFF : 0x08FFFFFF;
                f.out.add(new Fill(b[0] + 1, y + 1, b[1] - b[0] - 1, rowH, bgc));
            }
            f.out.addAll(rowOps);
            f.hits.addAll(rowHits);
            f.out.add(new Fill(left, y, tableW, 1, border));
            for (int[] b : cellBoxes) f.out.add(new Fill(b[0], y, 1, rowH + 1, border));
            f.out.add(new Fill(colX[cols], y, 1, rowH + 1, border));
            y += rowH + 1;
            rowIndex++;
        }
        f.out.add(new Fill(left, y, tableW, 1, border));
        f.maxRight = Math.max(f.maxRight, left - f.x0 + tableW);
        f.y = y + 2;
        f.block(BLOCK_GAP);
    }

    /// How wide this cell's content is laid out `width` wide (its widest line).
    private int measure(Html.Node cell, Style st, float scale, int width) {
        WikiLayout tmp = new WikiLayout(font);
        Flow mf = tmp.new Flow(0, width, 0, tmp.ops, tmp.hits);
        mf.split = false;
        tmp.kids(cell, mf, st, scale, null, false);
        mf.endLine();
        return mf.maxRight;
    }

    private static void collectRows(Html.Node n, List<List<Cell>> rows) {
        for (Html.Node c : n.children) {
            if (c.tag == null) continue;
            if (c.tag.equals("tr")) {
                List<Cell> row = new ArrayList<>();
                int col = 0;
                for (Html.Node td : c.children) {
                    if (!"td".equals(td.tag) && !"th".equals(td.tag)) continue;
                    int span = 1;
                    try {
                        span = Math.max(1, Math.min(20, Integer.parseInt(td.attr("colspan").trim())));
                    } catch (NumberFormatException ignored) {}
                    row.add(new Cell(td, "th".equals(td.tag), span, col));
                    col += span;
                }
                if (!row.isEmpty()) rows.add(row);
            } else if (!c.tag.equals("table")) {
                collectRows(c, rows);
            }
        }
    }
}
