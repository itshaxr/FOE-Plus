package dannypx.foe.screens.element.hud;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.LoadingHandler;
import dannypx.foe.helper.HudLayout;
import dannypx.foe.screens.element.Element;
import dannypx.foe.type.Alignment;
import dannypx.foe.type.PanelTheme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Text panel drawn in the FishOnMC-Extras-Collab (FOE) style: a translucent box with a themed
 * border and the title set into the top edge. Layout and border pieces ported from FOECollab's
 * QuestHud / Theming (AGPL-3.0).
 * <p>
 * Subclasses supply the title, the lines, and their config values; this class handles anchoring,
 * scaling and drawing, and keeps {@link #width}/{@link #height} current for the Move Elements screen.
 */
public abstract class PanelElement extends Element {
    private static final int PADDING = 8;
    private static final int LINE_SPACING = 2;

    protected PanelElement(float xPos, float yPos, Alignment alignment, Component message, boolean isCopy) {
        super(120, 60, xPos, yPos, alignment, message, isCopy);
        // Give the editor a sensible starting size. The HUD panels are created during client
        // init, before Minecraft has a font, so only measure when one exists (render() keeps the
        // size current afterwards).
        Font font = Minecraft.getInstance().font;
        if (font != null) this.updateSize(this.buildLines(), font);
    }

    /// This panel's look from the HUD Editor (spacing, colours, picture, hidden lines).
    public String lookId() {
        return this.getClass().getSimpleName();
    }

    protected dannypx.foe.handler.store.HudLooks.Look look() {
        return dannypx.foe.handler.store.HudLooks.get(lookId());
    }

    /// How tall the panel is with its fuller sample text (or its live text, if longer), at its size: for laying out.
    public int measuredHeight() {
        Font font = Minecraft.getInstance().font;
        List<Component> sample = new ArrayList<>(this.previewLines());
        int lines = Math.max(sample.size(), this.lines().size());
        int room = Math.max(picRoom(sample), picRoom(this.lines()));
        return Math.round(((Math.max(lines, 1) - 1) * lineHeight(font) + PADDING * 5 + pad() * 2 + room) * dannypx.foe.utils.HudScale.crisp(scale()));
    }

    /// The panel's own see-through setting (0-100), for the HUD Editor.
    public int defaultOpacity() {
        return opacityPercent();
    }

    /// The panel's title in its own words (for the HUD Editor's list).
    public Component titleText() {
        return this.title();
    }

    /// Package-private for tests: the lines without the ones hidden in the HUD Editor.
    static List<Component> withoutHidden(List<Component> lines, List<String> hidden) {
        if (hidden == null || hidden.isEmpty()) return lines;
        List<Component> out = new ArrayList<>();
        for (Component l : lines) if (l.getString().isBlank() || !hidden.contains(dannypx.foe.handler.store.HudLooks.key(l.getString()))) out.add(l);
        // No two blank lines in a row where something was hidden between them.
        List<Component> tidy = new ArrayList<>();
        for (Component l : out) {
            if (l.getString().isBlank() && !tidy.isEmpty() && tidy.getLast().getString().isBlank()) continue;
            tidy.add(l);
        }
        return tidy;
    }

    /// The lines as they'd show (before hiding), for the HUD Editor's list of lines.
    public List<Component> allLinesForEditor() {
        List<Component> lines = new ArrayList<>(this.lines());
        if (lines.isEmpty()) lines.addAll(this.previewLines());
        return lines;
    }

    //region Subclass hooks
    protected abstract Component title();

    /// The panel's lines; return an empty list to hide the panel.
    protected abstract List<Component> lines();

    /// Shown in the Move Elements screen when there is no live data yet.
    protected abstract List<Component> previewLines();

    protected abstract boolean enabled();

    protected abstract float configX();

    protected abstract float configY();

    protected abstract Alignment configAlignment();

    protected abstract float scale();

    protected abstract int opacityPercent();

    /// Right-aligned text when anchored on the right, like FOE.
    protected boolean alignTextToAnchor() {
        return true;
    }

    /// Every line centred in the panel (the warning box uses this).
    protected boolean centerText() {
        return false;
    }

    /// The lines' alignment the player picked (AUTO: the panel's own way).
    protected dannypx.foe.type.TextAlign textAlign() {
        return dannypx.foe.type.TextAlign.AUTO;
    }

    /// Package-private for tests: where a line `w` wide starts between `left` and `right`.
    static int lineX(dannypx.foe.type.TextAlign align, boolean center, boolean anchorRight, int left, int right, int w) {
        return switch (align) {
            case LEFT -> left;
            case RIGHT -> right - w;
            case CENTER -> left + (right - left - w) / 2;
            case AUTO -> center ? left + (right - left - w) / 2 : anchorRight ? right - w : left;
        };
    }

    /// An item box drawn beside the text. An empty stack draws an empty box (the "nothing here" state).
    public record PanelIcon(net.minecraft.world.item.ItemStack stack, int borderColor) {}

    /// Optional item boxes beside the text, side by side (the pet HUD shows the pet and its held item).
    protected List<PanelIcon> icons() {
        return List.of();
    }

    /// Pictures put into lines with {@link #inlineItem}, by their key.
    private static final java.util.Map<String, net.minecraft.world.item.ItemStack> INLINE = new java.util.HashMap<>();
    static { dannypx.foe.handler.logic.MemoryGuard.register("panelPictures", INLINE::size, INLINE::clear); }
    private static final String INLINE_KEY = "foe:item:";

    /// A small item picture in a line of text (the bait by its name): room in the line, with the picture drawn
    /// in it, the size of the letters. Nothing for an empty stack.
    public static Component inlineItem(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Component.empty();
        if (INLINE.size() > 256) INLINE.clear();
        String key = INLINE_KEY + System.identityHashCode(stack);
        INLINE.put(key, stack);
        return Component.literal(INLINE_ROOM).withStyle(s -> s.withInsertion(key));
    }

    /// The room a picture takes in a line: four spaces (16 pixels), the picture centred in it.
    static final String INLINE_ROOM = "    ";

    /// Package-private for tests: the picture's size and where it goes in its room {x offset, y offset, size}, for
    /// a room `roomW` pixels wide on a line of letters `letterH` pixels tall.
    static int[] inlinePlace(int roomW, int letterH) {
        // full size (16 pixels) by normal text, its line given a little more room; smaller by tiny text
        int size = letterH >= 8 ? Math.min(16, roomW) : Math.max(4, Math.min(letterH + letterH / 2, roomW - 2));
        return new int[]{(roomW - size) / 2, (letterH - size) / 2, size};
    }

    /// Draws the pictures in a piece of a line drawn at (x, y).
    private void drawInline(GuiGraphics g, Font font, Component part, int x, int y) {
        int[] at = {x};
        int letterH = tiny ? 5 : 8;
        part.visit((style, str) -> {
            int w = font.width(Component.literal(str).withStyle(style));
            String ins = style.getInsertion();
            if (ins != null && ins.startsWith(INLINE_KEY)) {
                net.minecraft.world.item.ItemStack stack = INLINE.get(ins);
                if (stack != null && !stack.isEmpty()) {
                    int[] p = inlinePlace(w, letterH);
                    // drawn as it is: no tile behind it
                    g.pose().pushMatrix();
                    g.pose().translate(at[0] + p[0], y + p[1]);
                    g.pose().scale(p[2] / 16f, p[2] / 16f);
                    g.renderItem(stack, 0, 0);
                    g.pose().popMatrix();
                }
            }
            at[0] += w;
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
    }

    /// Called just before a line is drawn, so a panel can draw something behind it.
    /// The server's rarity and variant badges (COMMON ... MYTHICAL, SPECIAL, ALBINO ... FABLED): drawn a pixel
    /// higher than the text around them, where they sit level with it. Package-private for tests.
    static boolean isBadge(int cp) {
        return (cp >= 0xF033 && cp <= 0xF037) || cp == 0xF092 || (cp >= 0xF041 && cp <= 0xF044);
    }

    /// A line of a panel, its badges a pixel higher (see {@link #isBadge}); every other piece where it was.
    static void drawText(GuiGraphics g, Font font, net.minecraft.util.FormattedCharSequence seq, int x, int y) {
        boolean[] any = {false};
        seq.accept((i, style, cp) -> {
            if (isBadge(cp)) { any[0] = true; return false; }
            return true;
        });
        if (!any[0]) {
            g.drawString(font, seq, x, y, 0xFFFFFFFF, true);
            return;
        }
        // split into runs of badges and runs of the rest, drawn one after another
        List<net.minecraft.network.chat.Style> styles = new ArrayList<>();
        List<Integer> cps = new ArrayList<>();
        seq.accept((i, style, cp) -> { styles.add(style); cps.add(cp); return true; });
        int cx = x, start = 0;
        for (int k = 1; k <= cps.size(); k++) {
            if (k < cps.size() && isBadge(cps.get(k)) == isBadge(cps.get(start))) continue;
            final int from = start, to = k;
            net.minecraft.util.FormattedCharSequence part = sink -> {
                for (int j = from; j < to; j++) if (!sink.accept(j - from, styles.get(j), cps.get(j))) return false;
                return true;
            };
            g.drawString(font, part, cx, isBadge(cps.get(from)) ? y - 1 : y, 0xFFFFFFFF, true);
            cx += font.width(part);
            start = k;
        }
    }

    /// Where the text may reach on the right (before the item boxes), for {@link #beforeLine}.
    protected int lineRightEdge = 0;

    /// A box's frame drawn on the screen's own pixels: the same thickness on all four sides at any panel or GUI
    /// scale (a plain outline at an in-between scale comes out thicker on some sides than others), and thin: half
    /// a panel pixel, never less than one screen pixel.
    static void crispOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        var pose = g.pose();
        org.joml.Vector2f a = pose.transformPosition(x, y, new org.joml.Vector2f());
        org.joml.Vector2f b = pose.transformPosition(x + w, y + h, new org.joml.Vector2f());
        double gui = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        int ax = (int) Math.round(a.x * gui), ay = (int) Math.round(a.y * gui);
        int bx = (int) Math.round(b.x * gui), by = (int) Math.round(b.y * gui);
        double perPixel = (b.x - a.x) * gui / Math.max(1, w);
        int t = Math.max(1, (int) Math.floor(perPixel * 0.5 + 0.0001));
        pose.pushMatrix();
        pose.identity();
        pose.scale((float) (1.0 / gui), (float) (1.0 / gui));
        g.fill(ax, ay, bx, ay + t, color);
        g.fill(ax, by - t, bx, by, color);
        g.fill(ax, ay + t, ax + t, by - t, color);
        g.fill(bx - t, ay + t, bx, by - t, color);
        pose.popMatrix();
    }

    protected void beforeLine(GuiGraphics guiGraphics, Font font, Component line, int x, int y) {
    }

    /// The scale the panel is being drawn at (for where its hover lines are on screen).
    private float drawScale = 1f;

    private static final long LINES_REFRESH_NS = 50_000_000L;
    private List<Component> cachedLines = null;
    private long cachedAtNs = 0L;
    private int cachedOuterWidth = 0, cachedOuterHeight = 0;

    /// The item boxes' size and the gap between them (the pet panel's are bigger).
    protected int iconSize() { return 18; }
    protected int iconGap() { return 3; }

    /// A short line drawn just above the item boxes, centred on them (the pet's "Max Luck"), or null.
    protected Component iconCaption() { return null; }

    private int iconSpace() {
        int n = this.icons().size();
        if (n == 0) return 0;
        int w = n * iconSize() + (n - 1) * iconGap();
        Component caption = iconCaption();
        if (caption != null) w = Math.max(w, net.minecraft.client.Minecraft.getInstance().font.width(caption));
        return w + PADDING;
    }

    /// How tall the text part has to be for the item boxes and their caption to fit inside the box.
    private int iconInnerHeight(Font font) {
        // the usual 18-pixel boxes with no caption always fitted: those panels keep their height
        if (this.icons().isEmpty() || (iconCaption() == null && iconSize() <= 18)) return 0;
        int caption = iconCaption() == null ? 0 : font.lineHeight + 2;
        return caption + iconSize() + 4 - PADDING;
    }
    //endregion

    private List<Component> buildLines() {
        List<Component> lines = new ArrayList<>();
        if (isCopy || LoadingHandler.instance().isLoadingDone()) lines.addAll(this.lines());
        if (lines.isEmpty() && isCopy) lines.addAll(this.previewLines());
        lines = new ArrayList<>(withoutHidden(lines, look().hidden));
        if (tiny) lines.replaceAll(l -> isRule(l) ? l : tinyText(l));
        // Trim blank lines at the ends.
        while (!lines.isEmpty() && lines.getFirst().getString().isBlank()) lines.removeFirst();
        while (!lines.isEmpty() && lines.getLast().getString().isBlank()) lines.removeLast();
        if (!lines.isEmpty() && foeR()) {
            lines.addFirst(foeRTitle(shownTitle()));
        } else if (!lines.isEmpty() && (Configs.hudConfig.panelTheme.get() == PanelTheme.OFF || !look().border)) {
            lines.addFirst(look().titleColor < 0 ? shownTitle().copy().withStyle(ChatFormatting.GRAY) : shownTitle());
        }
        return lines;
    }

    private int lineHeight(Font font) {
        return (tiny ? 6 : font.lineHeight) + Math.max(0, Math.min(12, look().lineSpacing));
    }

    /// Set smaller than one screen pixel per text pixel: drawn at the smallest crisp size with FOE+'s tiny pixel
    /// font, so it shrinks further without letters losing pixels.
    private boolean tiny = false;
    static final net.minecraft.network.chat.FontDescription TINY = new net.minecraft.network.chat.FontDescription.Resource(
            Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "tiny"));

    /// Package-private for tests: the text in the tiny font, where it was in the game's own font (icons and the
    /// server's badges keep theirs).
    static Component tinyText(Component line) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        line.visit((style, str) -> {
            net.minecraft.network.chat.Style st = style.getFont() == null || style.getFont().equals(net.minecraft.network.chat.FontDescription.DEFAULT)
                    ? style.withFont(TINY).withBold(false) : style;
            out.append(Component.literal(str).withStyle(st));
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }

    private int pad() {
        return Math.max(0, Math.min(16, look().padding));
    }

    /// The title, in the colour picked in the HUD Editor.
    private Component shownTitle() {
        dannypx.foe.handler.store.HudLooks.Look l = look();
        Component t = this.title();
        if (l.title != null && !l.title.isBlank()) {
            net.minecraft.network.chat.Style st = t.getStyle();
            if (st.isEmpty() && !t.getSiblings().isEmpty()) st = t.getSiblings().getFirst().getStyle();
            // in small capitals like FOE+'s own titles, so a name sits in the middle of its plate, clear of its edges
            t = Component.literal(dannypx.foe.helper.TextHelper.smallLetter(l.title)).withStyle(st);
        }
        t = l.titleColor < 0 ? t : t.copy().withColor(l.titleColor);
        return tiny ? tinyText(t) : t;
    }

    /// Put between two parts of a line, it puts the second part at the panel's right edge
    /// ("Lofoten Islands ⇥ 17:57").
    public static final Component TAB = Component.literal("\t");

    /// Package-private for tests: a two-part line as one ("Lofoten Islands  17:57"), or just the first part when the
    /// second is empty.
    static Component joinTab(Component[] parts) {
        if (parts[1].getString().isBlank()) return parts[0];
        if (parts[0].getString().isBlank()) return parts[1];
        return Component.empty().append(parts[0]).append(Component.literal("  ")).append(parts[1]);
    }

    /// The parts of a line on either side of its TAB, or null when it has none.
    static Component[] tabParts(Component line) {
        String t = line.getString();
        int i = t.indexOf('\t');
        return i < 0 ? null : new Component[]{slice(line, 0, i), slice(line, i + 1, t.length())};
    }

    private static int lineWidth(Component line, Font font) {
        Component[] parts = tabParts(line);
        return parts == null ? font.width(line) : font.width(parts[0]) + 12 + font.width(parts[1]);
    }

    /// A line cut into its name, its number(s), and what's in brackets after ("Total Shards ◆ ", "24128", "(43)"),
    /// for the column layout; null for a line with no number (a heading), which keeps the whole width.
    record Cols(Component label, Component value, Component extra) {}

    /// Package-private for tests.
    static Cols split(Component line) {
        String t = line.getString();
        int start = -1;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isDigit(c) || ((c == '$' || c == '+' || c == '-') && i + 1 < t.length() && Character.isDigit(t.charAt(i + 1)))) { start = i; break; }
        }
        if (start < 0) return null;
        int bracket = t.indexOf(" (", start);
        int end = bracket < 0 ? t.length() : bracket;
        return new Cols(slice(line, 0, start), slice(line, start, end), bracket < 0 ? Component.empty() : slice(line, bracket + 1, t.length()));
    }

    /// The characters from..to of a line, keeping their colours.
    static Component slice(Component line, int from, int to) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        int[] at = {0};
        line.visit((style, str) -> {
            int a = at[0], b = a + str.length();
            int s0 = Math.max(a, from), s1 = Math.min(b, to);
            if (s0 < s1) out.append(Component.literal(str.substring(s0 - a, s1 - a)).withStyle(style));
            at[0] = b;
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }

    /// The widest name, number and bracket among the lines, for the column layout.
    private static int[] colWidths(List<Component> lines, Font font) {
        int la = 0, va = 0, ea = 0;
        for (Component l : lines) {
            Cols c = split(l);
            if (c == null) continue;
            la = Math.max(la, font.width(c.label()));
            va = Math.max(va, font.width(c.value()));
            ea = Math.max(ea, font.width(c.extra()));
        }
        return new int[]{la, va, ea};
    }

    private static int colsWidth(int[] w) {
        return w[0] + 10 + w[1] + (w[2] > 0 ? 6 + w[2] : 0);
    }

    /// The picture picked for the title, or empty.
    private net.minecraft.world.item.ItemStack picture() {
        String id = look().picture;
        if (id == null || id.isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
        Identifier key = Identifier.tryParse(id);
        if (key == null) return net.minecraft.world.item.ItemStack.EMPTY;
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(key)
                .map(net.minecraft.world.item.ItemStack::new).orElse(net.minecraft.world.item.ItemStack.EMPTY);
    }

    /// Outer size of the panel including the border, before scaling.
    private int outerWidth(List<Component> lines, Font font) {
        int max = look().columns
                ? Math.max(colsWidth(colWidths(lines, font)), lines.stream().filter(l -> split(l) == null).mapToInt(l -> lineWidth(l, font)).max().orElse(0))
                : lines.stream().mapToInt(l -> lineWidth(l, font)).max().orElse(0);
        int pic = picture().isEmpty() ? 0 : 11;
        max = Math.max(max + iconSpace(), font.width(shownTitle()) + 8 + pic);
        return max + PADDING * 4 + pad() * 2;
    }

    /// A panel's width only grows straight away; it shrinks once it has been narrower for a few
    /// seconds. Countdowns ("2:10 left" → "59 left") change the width every second, which made
    /// centred or right-anchored panels jump sideways.
    private static final long SHRINK_AFTER_MS = 4_000L;
    /// Narrower by this little, the box keeps its width (clocks and counters change width as their digits change).
    static final int SMALL_SHRINK = 12;

    /// Package-private for tests: a box `shown` wide wanting to be `wanted` stays as it is.
    static boolean ignorable(int shown, int wanted) {
        return wanted < shown && shown - wanted <= SMALL_SHRINK;
    }
    private int shownWidth = -1;
    private long narrowerSince = -1L;

    /// Package-private for tests.
    int steadyWidth(int wanted, long nowMs) {
        if (shownWidth < 0 || wanted >= shownWidth) {
            shownWidth = wanted;
            narrowerSince = -1L;
        } else if (ignorable(shownWidth, wanted)) {
            // a digit narrower ("1:10" after "0:59", "1m" after "59s"): never worth moving the box for
            narrowerSince = -1L;
        } else if (narrowerSince < 0) {
            narrowerSince = nowMs;
        } else if (nowMs - narrowerSince >= SHRINK_AFTER_MS) {
            shownWidth = wanted;
            narrowerSince = -1L;
        }
        return shownWidth;
    }

    private int outerHeight(List<Component> lines, Font font) {
        int inner = (Math.max(lines.size(), 1) - 1) * lineHeight(font) + pad() * 2 + picRoom(lines);
        return Math.max(inner, iconInnerHeight(font)) + PADDING * 5;
    }

    /// A line with a picture gets this many more pixels (half above, half below), so its full-size picture
    /// doesn't run into the lines next to it.
    static final int PIC_ROOM = 4;

    private int picRoom(Component line) {
        return !tiny && hasInline(line) ? PIC_ROOM : 0;
    }

    /// The extra room the lines need: their pictures, and titled rules' badges.
    private int picRoom(List<Component> lines) {
        int n = 0;
        for (Component l : lines) n += isRule(l) ? (ruleTitle(l).isEmpty() ? 0 : RULE_TITLE_ROOM) : picRoom(l);
        return n;
    }

    private void updateSize(List<Component> lines, Font font) {
        this.width = Math.round(outerWidth(lines, font) * dannypx.foe.utils.HudScale.crisp(scale()));
        this.height = Math.round(outerHeight(lines, font) * dannypx.foe.utils.HudScale.crisp(scale()));
    }

    @Override
    public void render(GuiGraphics guiGraphics) {
        if (!enabled()) return;

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        if (!isCopy) {
            xPos = configX();
            yPos = configY();
            alignment = configAlignment();
        }

        // The text is rebuilt 20 times a second (as often as the game updates), not every frame:
        // at high frame rates building and measuring every panel's lines each frame cost a lot.
        float scale = dannypx.foe.utils.HudScale.crisp(scale());
        boolean nowTiny = scale() < scale - 0.001f;
        if (nowTiny != tiny) { tiny = nowTiny; cachedLines = null; }
        long nowNs = System.nanoTime();
        if (cachedLines == null || nowNs - cachedAtNs >= LINES_REFRESH_NS) {
            long p = dannypx.foe.handler.logic.FoeProfiler.mark();
            List<Component> built = this.buildLines();
            dannypx.foe.handler.logic.FoeProfiler.end("panel text: " + lookId(), p);
            p = dannypx.foe.handler.logic.FoeProfiler.mark();
            cachedLines = built;
            prepared.clear();
            preparedCols = null;
            preparedPicRoom = -1;
            cachedOuterWidth = outerWidth(cachedLines, font);
            cachedOuterHeight = outerHeight(cachedLines, font);
            cachedAtNs = nowNs;
            dannypx.foe.handler.logic.FoeProfiler.end("panel measure: " + lookId(), p);
        }
        List<Component> lines = cachedLines;
        int steadyWidth = steadyWidth(cachedOuterWidth, System.currentTimeMillis());
        this.width = Math.round(steadyWidth * scale);
        this.height = Math.round(cachedOuterHeight * scale);
        if (lines.isEmpty()) return;

        int screenWidth = (int) (minecraft.getWindow().getGuiScaledWidth() / scale);
        int screenHeight = (int) (minecraft.getWindow().getGuiScaledHeight() / scale);
        int outerWidth = steadyWidth;
        int outerHeight = cachedOuterHeight;

        boolean right = alignment == Alignment.TOP_RIGHT || alignment == Alignment.BOTTOM_RIGHT || alignment == Alignment.RIGHT;
        boolean bottom = alignment == Alignment.BOTTOM_LEFT || alignment == Alignment.BOTTOM_RIGHT || alignment == Alignment.BOTTOM;

        int anchorX = right ? screenWidth - Math.round(screenWidth * xPos) : Math.round(screenWidth * xPos);
        int anchorY = bottom ? screenHeight - Math.round(screenHeight * yPos) : Math.round(screenHeight * yPos);
        // Top/bottom-centre anchors keep the box centred on its spot, so it grows evenly both ways.
        boolean centered = alignment == Alignment.TOP || alignment == Alignment.BOTTOM || alignment == Alignment.CENTER;
        int outerX = centered ? anchorX - outerWidth / 2 : right ? anchorX - outerWidth : anchorX;
        int outerY = bottom ? anchorY - outerHeight : anchorY;

        // The panel stays exactly where you put it (only kept on screen). Panels don't push each
        // other around any more: they may overlap if that's how you place them.
        HudLayout.Rect wanted = new HudLayout.Rect(Math.round(outerX * scale), Math.round(outerY * scale),
                Math.round(outerWidth * scale), Math.round(outerHeight * scale));
        HudLayout.Rect placed = HudLayout.clamp(wanted, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        outerX = Math.round(placed.x() / scale);
        outerY = Math.round(placed.y() / scale);

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(scale, scale);
        drawScale = scale;
        long dp = dannypx.foe.handler.logic.FoeProfiler.mark();
        this.drawPanel(guiGraphics, font, lines, outerX, outerY, outerWidth, right);
        dannypx.foe.handler.logic.FoeProfiler.end("panel draw: " + lookId(), dp);
        guiGraphics.pose().popMatrix();
    }

    /// A line laid out once (its parts, widths and the glyph sequences the font draws), reused every frame until the
    /// panel's text is rebuilt (20 times a second at most): drawing it afresh each frame made a lot of garbage.
    private static final class Prep {
        Component[] tab;
        Component joined;
        boolean inline, hover;
        Cols cols;
        int width, tab0W, tab1W, labelW, valueW, extraW;
        net.minecraft.util.FormattedCharSequence seq, tab0, tab1, label, value, extra;
    }

    private final java.util.IdentityHashMap<Component, Prep> prepared = new java.util.IdentityHashMap<>();
    private int[] preparedCols = null;
    private int preparedPicRoom = -1;

    private Prep prep(Component line, Font font, boolean columns, boolean autoAlign) {
        Prep p = prepared.get(line);
        if (p != null) return p;
        p = new Prep();
        p.tab = tabParts(line);
        Component shown = line;
        if (p.tab != null && !autoAlign) {
            p.joined = joinTab(p.tab);
            shown = p.joined;
            p.tab = null;
        }
        p.inline = hasInline(shown);
        p.hover = HudHover.hasHover(shown);
        if (p.tab != null) {
            p.tab0 = p.tab[0].getVisualOrderText();
            p.tab1 = p.tab[1].getVisualOrderText();
            p.tab0W = font.width(p.tab0);
            p.tab1W = font.width(p.tab1);
        } else {
            p.cols = columns && !p.inline ? split(shown) : null;
            if (p.cols != null) {
                p.label = p.cols.label().getVisualOrderText();
                p.value = p.cols.value().getVisualOrderText();
                p.extra = p.cols.extra().getVisualOrderText();
                p.labelW = font.width(p.label);
                p.valueW = font.width(p.value);
                p.extraW = font.width(p.extra);
            }
            p.seq = shown.getVisualOrderText();
            p.width = font.width(p.seq);
        }
        if (prepared.size() > 256) prepared.clear();
        prepared.put(line, p);
        return p;
    }

    private void drawPanel(GuiGraphics guiGraphics, Font font, List<Component> lines, int outerX, int outerY, int outerWidth, boolean right) {
        int lineHeight = lineHeight(font);
        dannypx.foe.handler.store.HudLooks.Look look = look();
        int pad = pad();
        int contentWidth = outerWidth - PADDING * 4 - pad * 2;

        // Same geometry as FOE: the border pieces sit PADDING outside the filled box.
        int top = outerY + PADDING;
        int contentLeft = outerX + PADDING * 2;
        int contentRight = contentLeft + contentWidth;
        int boxLeft = contentLeft - PADDING;
        int boxRight = contentRight + PADDING + pad * 2;
        contentLeft += pad;
        contentRight += pad;
        if (preparedPicRoom < 0) preparedPicRoom = picRoom(lines);
        int innerHeight = Math.max((lines.size() - 1) * lineHeight + pad * 2 + preparedPicRoom, iconInnerHeight(font));

        int opacity = look.opacity >= 0 ? look.opacity : opacityPercent();
        int bgAlpha = (Math.round(opacity / 100f * 255f) << 24) | (look.boxColor & 0xFFFFFF);
        boolean foeR = foeR();
        boolean border = look.border;
        if (foeR && border) drawFoeRBox(guiGraphics, boxLeft, top, boxRight - boxLeft, innerHeight + PADDING * 3, bgAlpha);
        else guiGraphics.fill(boxLeft, top, boxRight, top + innerHeight + PADDING * 3, bgAlpha);
        if (!look.imageInFront) drawImage(guiGraphics, look, boxLeft, top, boxRight - boxLeft, innerHeight + PADDING * 3);

        PanelTheme theme = Configs.hudConfig.panelTheme.get();
        if (!foeR && border && theme != PanelTheme.OFF) {
            int white = 0xFF000000 | (look.borderColor & 0xFFFFFF);
            int bottomY = top + PADDING * 2 + innerHeight;
            Component title = shownTitle();
            net.minecraft.world.item.ItemStack pic = picture();
            int picW = pic.isEmpty() ? 0 : 11;
            int titleWidth = font.width(title) + picW;
            int titleX = boxLeft + (boxRight - boxLeft - titleWidth) / 2;

            // The frame is drawn on the screen's own pixels: at a size like 0.8 its one-pixel lines
            // fell between screen pixels, so some edges came out twice as thick as others and the
            // pieces didn't meet. Each frame pixel is now a whole number of screen pixels (the
            // nearest to the panel's size), and every piece's position is rounded to that grid.
            double gui = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
            int px = Math.max(1, Math.round((float) (drawScale * gui)));      // screen pixels per frame pixel
            float unit = (float) (px / gui);                                  // GUI units per frame pixel
            // below one screen pixel per frame pixel (a HUD made very small) it shrinks with the panel, unsnapped
            float f = drawScale * gui < 1.0 ? 1f : drawScale / unit;          // panel units -> frame pixels
            // each piece placed so its middle (where FOE's frame lines run, PADDING in) lands on the
            // box's edge, as it did unrounded
            int l = Math.round(boxLeft * f) - PADDING, r = Math.round(boxRight * f) - PADDING;
            int t = Math.round(top * f) - PADDING, b = Math.round((bottomY + PADDING) * f) - PADDING;
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().scale(1f / f, 1f / f);
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "top_left"), l, t, 16, 16, white);
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "top_right"), r, t, 16, 16, white);
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "bottom_left"), l, b, 16, 16, white);
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "bottom_right"), r, b, 16, 16, white);
            if (b - t - 16 > 0) {
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "left"), l, t + 16, 16, b - t - 16, white);
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "right"), r, t + 16, 16, b - t - 16, white);
            }
            if (r - l - 16 > 0) {
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "top"), l + 16, t, r - l - 16, 16, white);
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "bottom"), l + 16, b, r - l - 16, 16, white);
            }
            guiGraphics.pose().popMatrix();
            // the title's plate stays with its text, at the panel's own size (snapped, the two
            // could end up a pixel apart and the text ran off the plate)
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "text_left"), titleX - 16, top - PADDING, 16, 16, white);
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "text_middle"), titleX, top - PADDING, titleWidth, 16, white);
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "text_right"), titleX + titleWidth, top - PADDING, 16, 16, white);
            if (picW > 0) {
                guiGraphics.pose().pushMatrix();
                guiGraphics.pose().translate(titleX, top - font.lineHeight / 2f - 1.5f);
                guiGraphics.pose().scale(0.625f, 0.625f);
                guiGraphics.renderItem(pic, 0, 0);
                guiGraphics.pose().popMatrix();
            }
            guiGraphics.drawString(font, title, titleX + picW, top - font.lineHeight / 2 - 1, 0xFFFFFFFF, false);
        }

        // Icon boxes on the anchored side, text beside them.
        List<PanelIcon> icons = this.icons();
        int iconSpace = iconSpace();
        final int ICON_SIZE = iconSize(), ICON_GAP = iconGap();
        int textLeft = contentLeft;
        int textRightEdge = contentRight;
        if (iconSpace > 0) {
            int iconsWidth = iconSpace - PADDING;
            // the caption (if any) and the boxes under it, together in the middle of the box's height
            Component caption = iconCaption();
            int captionH = caption == null ? 0 : font.lineHeight + 2;
            int iconBoxY = top + PADDING - 1 + (innerHeight + lineHeight - ICON_SIZE - captionH) / 2 - 2 + captionH;
            if (caption != null) iconBoxY = Math.max(iconBoxY, top + PADDING + 1 + captionH);
            int x = right ? contentRight - iconsWidth : contentLeft;
            if (caption != null) {
                guiGraphics.drawString(font, caption, x + (iconsWidth - font.width(caption)) / 2, iconBoxY - captionH, 0xFFFFFFFF, true);
            }
            // the boxes centred under a wider caption
            x += (iconsWidth - (icons.size() * ICON_SIZE + (icons.size() - 1) * ICON_GAP)) / 2;
            // The frames are drawn again over the items afterwards: a big item model (some pets)
            // can otherwise cover the frame's edge.
            List<int[]> frames = new java.util.ArrayList<>();
            for (PanelIcon icon : icons) {
                crispOutline(guiGraphics, x, iconBoxY, ICON_SIZE, ICON_SIZE, icon.borderColor());
                if (icon.stack() == null || icon.stack().isEmpty()) {
                    // Empty state: darker inside and a dash.
                    guiGraphics.fill(x + 1, iconBoxY + 1, x + ICON_SIZE - 1, iconBoxY + ICON_SIZE - 1, 0x55000000);
                    guiGraphics.drawString(font, "-", x + (ICON_SIZE - font.width("-")) / 2, iconBoxY + (ICON_SIZE - font.lineHeight) / 2 + 1, 0xFF777777, false);
                } else {
                    if (ICON_SIZE == 18) {
                        guiGraphics.renderItem(icon.stack(), x + 1, iconBoxY + 1);
                    } else {
                        // a bigger box: the item scaled up, a pixel clear of the frame all round
                        float itemScale = (ICON_SIZE - 4) / 16f;
                        guiGraphics.pose().pushMatrix();
                        guiGraphics.pose().translate(x + 2, iconBoxY + 2);
                        guiGraphics.pose().scale(itemScale, itemScale);
                        guiGraphics.renderItem(icon.stack(), 0, 0);
                        guiGraphics.pose().popMatrix();
                    }
                }
                frames.add(new int[]{x, iconBoxY, icon.borderColor()});
                x += ICON_SIZE + ICON_GAP;
            }
            if (!frames.isEmpty()) {
                guiGraphics.nextStratum();
                for (int[] f : frames) crispOutline(guiGraphics, f[0], f[1], ICON_SIZE, ICON_SIZE, f[2]);
            }
            if (right) textRightEdge -= iconSpace;
            else textLeft += iconSpace;
        }

        boolean textRight = right && alignTextToAnchor();
        int y = top + PADDING + pad;
        boolean first = true;
        dannypx.foe.type.TextAlign align = look.textAlign() != dannypx.foe.type.TextAlign.AUTO ? look.textAlign() : textAlign();
        for (Component line : lines) {
            if (isRule(line)) {
                // a line across the box between two parts of a panel, with the second part's title in the middle,
                // in a badge like the panel's own title (the timers, then the community catches)
                String ruleTitle = ruleTitle(line);
                // a titled line sits a little lower, with room above its badge
                int ry = y + lineHeight / 2 - 1 + (ruleTitle.isEmpty() ? 0 : RULE_TITLE_ABOVE);
                if (ruleTitle.isEmpty()) {
                    guiGraphics.fill(textLeft, ry, textRightEdge, ry + 1, 0x66FFFFFF);
                } else {
                    Component t = Component.literal(ruleTitle).withStyle(ChatFormatting.BOLD);
                    int tw = font.width(t);
                    int tx = boxLeft + (boxRight - boxLeft - tw) / 2;
                    if (!foeR && border && theme != PanelTheme.OFF) {
                        int white = 0xFF000000 | (look.borderColor & 0xFFFFFF);
                        guiGraphics.fill(textLeft, ry, tx - 10, ry + 1, 0x66FFFFFF);
                        guiGraphics.fill(tx + tw + 10, ry, textRightEdge, ry + 1, 0x66FFFFFF);
                        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "text_left"), tx - 16, ry - 7, 16, 16, white);
                        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "text_middle"), tx, ry - 7, tw, 16, white);
                        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(theme, "text_right"), tx + tw, ry - 7, 16, 16, white);
                        guiGraphics.drawString(font, t, tx, ry + 1 - font.lineHeight / 2, 0xFFFFFFFF, false);
                    } else {
                        guiGraphics.fill(textLeft, ry, tx - 4, ry + 1, 0x66FFFFFF);
                        guiGraphics.fill(tx + tw + 4, ry, textRightEdge, ry + 1, 0x66FFFFFF);
                        guiGraphics.drawString(font, t, tx, ry + 1 - font.lineHeight / 2, 0xFFAAAAAA, true);
                    }
                }
                first = false;
                y += ruleTitle.isEmpty() ? lineHeight : lineHeight + RULE_TITLE_ROOM;
                continue;
            }
            if (foeR && first) {
                // FOE-R's heading: "- Title -", bold small caps, centred over the panel
                int w = dannypx.foe.helper.TextHelper.getWidth(font, line, true);
                dannypx.foe.helper.GuiGraphicsHelper.drawString(guiGraphics, font, line, boxLeft + (boxRight - boxLeft - w) / 2, y,
                        dannypx.foe.type.StringStyle.SHADOW, dannypx.foe.type.StringStyle.SMALL_CAPS);
                first = false;
                y += lineHeight;
                continue;
            }
            first = false;
            Prep pr = prep(line, font, look.columns, look.textAlign() == dannypx.foe.type.TextAlign.AUTO);
            if (pr.joined != null) line = pr.joined;
            int room = !tiny && pr.inline ? PIC_ROOM : 0;
            y += room / 2;
            if (pr.tab != null) {
                if (pr.inline) {
                    drawInline(guiGraphics, font, pr.tab[0], textLeft, y);
                    drawInline(guiGraphics, font, pr.tab[1], textRightEdge - pr.tab1W, y);
                }
                drawText(guiGraphics, font, pr.tab0, textLeft, y);
                drawText(guiGraphics, font, pr.tab1, textRightEdge - pr.tab1W, y);
                y += lineHeight + room - room / 2;
                continue;
            }
            if (pr.cols != null) {
                // Columns: the name on the left, the numbers lined up on the right, brackets after them.
                if (preparedCols == null) preparedCols = colWidths(lines, font);
                int[] cw = preparedCols;
                int extraRight = textRightEdge;
                int valueRight = cw[2] > 0 ? extraRight - cw[2] - 6 : extraRight;
                drawText(guiGraphics, font, pr.label, textLeft, y);
                drawText(guiGraphics, font, pr.value, valueRight - pr.valueW, y);
                if (cw[2] > 0) drawText(guiGraphics, font, pr.extra, extraRight - pr.extraW, y);
                y += lineHeight;
                continue;
            }
            int x = lineX(align, centerText(), textRight, textLeft, textRightEdge, pr.width);
            this.lineRightEdge = textRightEdge;
            this.beforeLine(guiGraphics, font, line, x, y);
            if (pr.inline) drawInline(guiGraphics, font, line, x, y);
            drawText(guiGraphics, font, pr.seq, x, y);
            if (pr.hover) HudHover.note(x, y, pr.width, lineHeight, drawScale, line);
            y += lineHeight + room - room / 2;
        }
        if (look.imageInFront) {
            guiGraphics.nextStratum();
            drawImage(guiGraphics, look, boxLeft, top, boxRight - boxLeft, innerHeight + PADDING * 3);
        }
    }

    private static final String RULE_KEY = "foe:rule";
    /// A thin line across the panel between two parts of it; takes a line's room, no width.
    protected static final Component RULE = Component.literal("").withStyle(st -> st.withInsertion(RULE_KEY));

    static boolean isRule(Component line) {
        String ins = line.getStyle().getInsertion();
        return ins != null && ins.startsWith(RULE_KEY) && line.getString().isEmpty();
    }

    /// A line across the panel with a title in the middle of it ("ᴄᴏᴍᴍᴜɴɪᴛʏ ᴄᴀᴛᴄʜᴇѕ"), in a badge like the panel's.
    protected static Component titledRule(String title) {
        return Component.literal("").withStyle(st -> st.withInsertion(RULE_KEY + ":" + title));
    }

    /// Package-private for tests: the title on a rule line, "" for a plain one.
    static String ruleTitle(Component line) {
        String ins = line.getStyle().getInsertion();
        return ins != null && ins.startsWith(RULE_KEY + ":") ? ins.substring(RULE_KEY.length() + 1) : "";
    }

    /// The titled line is given this much more room, so its badge clears the lines above and below.
    static final int RULE_TITLE_ROOM = 7, RULE_TITLE_ABOVE = 3;

    /// This HUD has the FOE-R look: its own style from the HUD Editor, or else the one for every HUD (Customize →
    /// HUD Style).
    boolean foeR() {
        return style(look().style) == dannypx.foe.type.HudStyle.FOE_R;
    }

    /// Package-private for tests: the style a HUD set to `own` ("DEFAULT", "FOE_PLUS", "FOE_R") is drawn in.
    static dannypx.foe.type.HudStyle style(String own) {
        if ("FOE_R".equals(own)) return dannypx.foe.type.HudStyle.FOE_R;
        if ("FOE_PLUS".equals(own)) return dannypx.foe.type.HudStyle.FOE_PLUS;
        return Configs.hudConfig.hudStyle.get();
    }

    /// Package-private for tests: the line holds a picture (kept out of the columns layout, which would split it).
    static boolean hasInline(Component line) {
        return line.visit((style, str) -> style.getInsertion() != null && style.getInsertion().startsWith(INLINE_KEY)
                ? java.util.Optional.of(Boolean.TRUE) : java.util.Optional.empty(), net.minecraft.network.chat.Style.EMPTY).isPresent();
    }

    /// Your own picture in the box (HUD Editor → Image), kept inside the box.
    private static void drawImage(GuiGraphics g, dannypx.foe.handler.store.HudLooks.Look look, int x, int y, int w, int h) {
        if (look.image == null || look.image.isEmpty()) return;
        dannypx.foe.handler.store.HudImages.Loaded img = dannypx.foe.handler.store.HudImages.get(look.image);
        if (img == null) return;
        int[] at = dannypx.foe.handler.store.HudImages.place(x, y, w, h, img.width(), img.height(), look.imageSize, look.imageX, look.imageY, look.imageFill);
        int alpha = Math.round(255 * Math.max(0, Math.min(100, look.imageOpacity)) / 100f);
        // no clipping: the picture is placed inside the box already, and clipping a box drawn at a size between whole
        // pixels rounded its edge inwards, leaving the last column on the right empty
        g.blit(RenderPipelines.GUI_TEXTURED, img.frameAt(System.currentTimeMillis()), at[0], at[1], 0f, 0f, at[2], at[3],
                img.width(), img.height(), img.width(), img.height(), alpha << 24 | 0xFFFFFF);
    }

    /// FOE-R's heading line: "- Title -" (grey dashes, white title, both bold).
    static Component foeRTitle(Component title) {
        return Component.empty()
                .append(Component.literal("- ").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD))
                .append(Component.literal(title.getString()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(" -").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD));
    }

    private static final Identifier FOE_R_BARS = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "textures/gui/sprites/elements/sidebar_atlas.png");

    /// FOE-R's box, as its custom HUDs draw it: the see-through middle, and a dark bar (with its little nibs)
    /// along the top and bottom.
    private static void drawFoeRBox(GuiGraphics g, int x, int y, int w, int h, int bgAlpha) {
        final int TW = 17, TH = 11, CORNER = 8, BAR_W = 1, BAR_H = 5, NIB = 3, PAD = 5;
        g.fill(x + PAD, y + 1, x + w - PAD, y + h - 3, bgAlpha);
        g.blit(RenderPipelines.GUI_TEXTURED, FOE_R_BARS, x, y, 0, NIB, CORNER, CORNER, CORNER, CORNER, TW, TH);
        g.blit(RenderPipelines.GUI_TEXTURED, FOE_R_BARS, x + CORNER, y, CORNER, NIB, w - CORNER * 2, BAR_H, BAR_W, BAR_H, TW, TH);
        g.blit(RenderPipelines.GUI_TEXTURED, FOE_R_BARS, x + w - CORNER, y, CORNER + BAR_W, NIB, CORNER, CORNER, CORNER, CORNER, TW, TH);
        g.blit(RenderPipelines.GUI_TEXTURED, FOE_R_BARS, x, y + h - CORNER, 0, 0, CORNER, CORNER, CORNER, CORNER, TW, TH);
        g.blit(RenderPipelines.GUI_TEXTURED, FOE_R_BARS, x + CORNER, y + h - CORNER + NIB, CORNER, NIB, w - CORNER * 2, BAR_H, BAR_W, BAR_H, TW, TH);
        g.blit(RenderPipelines.GUI_TEXTURED, FOE_R_BARS, x + w - CORNER, y + h - CORNER, CORNER + BAR_W, 0, CORNER, CORNER, CORNER, CORNER, TW, TH);
    }

    private static Identifier sprite(PanelTheme theme, String piece) {
        return Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "themes/" + theme.id + "/container/" + piece);
    }

    //region Shared text helpers
    protected static Component label(String smallCapsLabel) {
        return Component.literal(smallCapsLabel).withStyle(ChatFormatting.GRAY);
    }

    protected static Component join(Component... parts) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        for (Component part : parts) out.append(part);
        return out;
    }

    protected static String formatTime(long ms) {
        long totalSeconds = ms / 1000L;
        return String.format("%02d:%02d:%02d", totalSeconds / 3600, (totalSeconds / 60) % 60, totalSeconds % 60);
    }

    protected static String formatMinSec(long ms) {
        long totalSeconds = ms / 1000L;
        return String.format("%02d:%02d", (totalSeconds / 60) % 60, totalSeconds % 60);
    }
    //endregion
}
