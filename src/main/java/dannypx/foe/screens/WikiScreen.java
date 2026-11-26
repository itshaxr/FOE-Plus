package dannypx.foe.screens;

import dannypx.foe.helper.wiki.Html;
import dannypx.foe.helper.wiki.WikiLayout;
import dannypx.foe.helper.wiki.WikiNet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * The FishOnMC wiki, in game (O → Tools → Wiki): FOE+ reads its own copy of the wiki's pages, inside the mod (nothing
 * is fetched from the internet), and lays them out itself (headings, text, tables, pictures, lists), so nothing opens in
 * your web browser. Links go to their page, ← and → go back and forward
 * (so do the mouse's side buttons), ⌂ goes to the start page, and the search box searches the whole wiki. A click on a
 * picture shows it big. Links that leave the wiki are copied for you instead of opened.
 */
public class WikiScreen extends Screen {
    private static final int H = 16, BAR = 20;

    private final Screen parent;
    private int left, top, right, bottom;
    private int viewLeft, viewTop, viewRight, viewBottom;

    /// What's shown: a page ("start", "fishing", ...) or a search ("?words").
    private String current;
    private String fragment = "";
    private final Deque<Entry> back = new ArrayDeque<>(), forward = new ArrayDeque<>();
    private record Entry(String target, int scroll) {}

    private Html.Node page;
    private String title = "";
    private String status = "";
    private int request = 0;

    private WikiLayout layout;
    private int layoutWidth = -1, layoutVersion = -1;
    private long laidOutAt = 0;
    private int scroll = 0;
    private boolean draggingBar = false;
    /// Kept at the link's anchor while pictures above it arrive (until you scroll yourself).
    private boolean pinned = false;
    private int searchLeft;

    private String search = "";
    private String viewing;         // a picture shown big
    private String note = "";
    private long noteUntil = 0;

    public WikiScreen(Screen parent) {
        this(parent, "start");
    }

    public WikiScreen(Screen parent, String target) {
        super(Component.literal("FishOnMC Wiki"));
        this.parent = parent;
        this.current = target;
    }

    @Override
    protected void init() {
        int panelW = Math.min(width - 16, 560);
        left = width / 2 - panelW / 2;
        right = left + panelW;
        top = 6;
        bottom = height - 6;

        // the bar: back, forward, start page, search
        int x = left + 8, y = top + 6;
        Button backB = addRenderableWidget(Button.builder(Component.literal("←"), b -> goBack()).bounds(x, y, 20, BAR - 2)
                .tooltip(Tooltip.create(Component.literal("Back"))).build());
        backB.active = !back.isEmpty();
        x += 22;
        Button fwdB = addRenderableWidget(Button.builder(Component.literal("→"), b -> goForward()).bounds(x, y, 20, BAR - 2)
                .tooltip(Tooltip.create(Component.literal("Forward"))).build());
        fwdB.active = !forward.isEmpty();
        x += 22;
        addRenderableWidget(Button.builder(Component.literal("⌂"), b -> go("start")).bounds(x, y, 20, BAR - 2)
                .tooltip(Tooltip.create(Component.literal("The wiki's start page"))).build());
        x += 26;
        int searchW = Math.max(60, Math.min(180, right - 8 - 40 - x - 100));
        int sx = right - 8 - 36 - 2 - searchW;
        searchLeft = sx;
        EditBox find = new EditBox(font, sx, y + 1, searchW, H, Component.literal("Search the wiki"));
        find.setMaxLength(80);
        find.setHint(Component.literal("Search the wiki...").withStyle(ChatFormatting.DARK_GRAY));
        find.setValue(search);
        find.setResponder(t -> search = t);
        addRenderableWidget(find);
        addRenderableWidget(Button.builder(Component.literal("Go"), b -> doSearch()).bounds(right - 8 - 36, y, 36, BAR - 2).build());

        viewLeft = left + 10;
        viewTop = top + 6 + BAR + 16;
        viewRight = right - 16;
        viewBottom = bottom - 26;

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 - 50, bottom - 22, 100, 18).build());

        if (page == null && status.isEmpty()) load(current, fragment, false);
        layout = null;   // laid out again at the new size
    }

    // ---- going places ----

    /// Opens a link (from the page, or a page's id).
    private void go(String target) {
        go(target, "");
    }

    private void go(String target, String frag) {
        if (target.equals(current) && page != null) {
            // the same page: only to its anchor
            fragment = frag;
            scrollToFragment();
            return;
        }
        back.push(new Entry(current + (fragment.isEmpty() ? "" : "#" + fragment), scroll));
        forward.clear();
        load(target, frag, true);
    }

    private void goBack() {
        if (back.isEmpty()) return;
        forward.push(new Entry(current + (fragment.isEmpty() ? "" : "#" + fragment), scroll));
        Entry e = back.pop();
        loadEntry(e);
    }

    private void goForward() {
        if (forward.isEmpty()) return;
        back.push(new Entry(current + (fragment.isEmpty() ? "" : "#" + fragment), scroll));
        Entry e = forward.pop();
        loadEntry(e);
    }

    private int restoreScroll = -1;

    private void loadEntry(Entry e) {
        String t = e.target();
        String frag = "";
        int hash = t.indexOf('#');
        if (hash >= 0 && !t.startsWith("?")) {
            frag = t.substring(hash + 1);
            t = t.substring(0, hash);
        }
        restoreScroll = e.scroll();
        load(t, frag, true);
    }

    private void load(String target, String frag, boolean rebuild) {
        current = target;
        fragment = frag == null ? "" : frag;
        page = null;
        layout = null;
        scroll = 0;
        viewing = null;
        status = "Loading...";
        title = target.startsWith("?") ? "Search: " + target.substring(1) : pretty(target);
        int mine = ++request;
        pinned = false;
        // the back and forward buttons as they are now
        if (rebuild && minecraft != null) rebuildWidgets();
        if (target.startsWith("?")) {
            // searched in FOE+'s own copy of the wiki: titles first, then the pages' words
            String q = target.substring(1);
            WikiNet.search(q).thenApply(html -> {
                Html.Node root = Html.parse(html);
                if (root.text().isBlank()) root = Html.parse("<p>Nothing on the wiki is called that. Try fewer or other words.</p>");
                return root;
            }).whenComplete((n, t) -> done(mine, n, t));
        } else {
            // the page's article, from FOE+'s copy of the wiki
            WikiNet.text(WikiNet.BASE + "/doku.php?id=" + enc(target)).thenApply(html -> {
                Html.Node content = content(Html.parse(html));
                if (content == null) throw new IllegalStateException("the wiki sent something else (try again in a moment)");
                return content;
            }).whenComplete((n, t) -> done(mine, n, t));
        }
    }

    /// The page's own part of the website's page (its article), or null when there's none.
    static Html.Node content(Html.Node root) {
        Html.Node c = root.byId("main-content");
        if (c != null) return c;
        c = root.byId("dokuwiki__content");
        if (c != null) {
            Html.Node p = c.byClass("page");
            return p != null ? p : c;
        }
        Html.Node p = root.byClass("page");
        if (p != null) return p;
        return root.byTag("article");
    }

    private void done(int mine, Html.Node n, Throwable t) {
        Minecraft.getInstance().execute(() -> {
            if (mine != request) return;   // another page was asked for since
            if (t != null || n == null) {
                Throwable cause = t;
                while (cause != null && cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
                String why = cause == null || cause.getMessage() == null ? "no answer" : cause.getMessage();
                status = why.contains("404") ? "This page isn't in FOE+'s copy of the wiki" : "Couldn't open this page: " + why;
                return;
            }
            page = n;
            status = "";
            if (!current.startsWith("?")) {
                Html.Node h1 = n.byTag("h1");
                if (h1 != null && !h1.text().isBlank()) title = h1.text();
            }
            layout = null;
        });
    }

    private void doSearch() {
        String q = search.trim();
        if (q.isEmpty()) return;
        go("?" + q);
    }

    /// Where a link goes: a wiki page (its id, and anchor), a picture, the current page's anchor, or outside the wiki.
    private void follow(String href) {
        if (href == null || href.isBlank()) return;
        if (href.startsWith("#")) {
            fragment = href.substring(1);
            scrollToFragment();
            return;
        }
        String url = WikiNet.absolute(href);
        URI uri;
        try {
            uri = URI.create(url.replace(" ", "%20"));
        } catch (IllegalArgumentException e) {
            return;
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!host.equals("wiki.fishonmc.net")) {
            outside(url);
            return;
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String id = param(uri.getRawQuery(), "id");
        String media = param(uri.getRawQuery(), "media");
        String frag = uri.getRawFragment() == null ? "" : dec(uri.getRawFragment());
        if (path.contains("/lib/exe/detail.php") || path.contains("/lib/exe/fetch.php") || path.startsWith("/_media/") || path.startsWith("/_detail/")) {
            // a picture: shown big
            if (media == null && (path.startsWith("/_media/") || path.startsWith("/_detail/"))) media = dec(path.substring(path.indexOf('/', 1) + 1));
            if (media != null) {
                viewing = WikiNet.BASE + "/lib/exe/fetch.php?media=" + enc(media);
                WikiNet.picture(viewing);
            }
            return;
        }
        String q = param(uri.getRawQuery(), "q");
        if ("search".equals(param(uri.getRawQuery(), "do")) && q != null) {
            go("?" + q);
            return;
        }
        if (id == null) {
            if (path.startsWith("/doku.php/")) id = dec(path.substring("/doku.php/".length()));
            else if (path.isEmpty() || path.equals("/") || path.equals("/doku.php")) id = "start";
            else if (!path.startsWith("/lib/")) id = dec(path.substring(1)).replace('/', ':');
        }
        if (id == null || id.isBlank()) {
            outside(url);
            return;
        }
        go(id, frag);
    }

    private void outside(String url) {
        if (minecraft != null) minecraft.keyboardHandler.setClipboard(url);
        note = "That link leaves the wiki: copied, so you can paste it where you like";
        noteUntil = System.currentTimeMillis() + 4000;
    }

    private static String param(String query, String name) {
        if (query == null) return null;
        for (String kv : query.split("&")) {
            int eq = kv.indexOf('=');
            String k = eq < 0 ? kv : kv.substring(0, eq);
            if (k.equals(name)) return eq < 0 ? "" : dec(kv.substring(eq + 1));
        }
        return null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String dec(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    /// "fish_groups" → "Fish Groups", "locations:cypress" → "Cypress".
    private static String pretty(String id) {
        String last = id.substring(id.lastIndexOf(':') + 1).replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String w : last.split(" ")) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.isEmpty() ? id : sb.toString();
    }

    // ---- the page ----

    private void ensureLayout() {
        if (page == null) return;
        int w = viewRight - viewLeft;
        if (layout != null && layoutWidth == w) {
            // a picture came: only laid out again when one of its pictures had a guessed size, and not more than a few times a second
            if (layoutVersion == WikiNet.version || layout.guessed == 0) return;
            if (System.currentTimeMillis() - laidOutAt < 400) return;
        }
        boolean first = layout == null;
        laidOutAt = System.currentTimeMillis();
        layoutVersion = WikiNet.version;
        layoutWidth = w;
        layout = WikiLayout.of(page, font, w);
        if (pinned) {
            scrollToFragment();
        } else if (first) {
            if (restoreScroll >= 0) {
                scroll = restoreScroll;
                restoreScroll = -1;
            } else if (!fragment.isEmpty()) {
                scrollToFragment();
            }
        }
        clampScroll();
    }

    private void scrollToFragment() {
        if (layout == null || fragment.isEmpty()) return;
        Integer y = layout.anchors.get(fragment);
        if (y != null) {
            scroll = Math.max(0, y - 4);
            pinned = true;
        }
        clampScroll();
    }

    private int maxScroll() {
        return layout == null ? 0 : Math.max(0, layout.height - (viewBottom - viewTop));
    }

    /// You scrolled: the page stays where you put it.
    private void scrollBy(int dy) {
        pinned = false;
        scroll += dy;
        clampScroll();
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private WikiLayout.Hit hitAt(double mx, double my) {
        if (layout == null || viewing != null) return null;
        if (mx < viewLeft || mx >= viewRight || my < viewTop || my >= viewBottom) return null;
        double lx = mx - viewLeft, ly = my - viewTop + scroll;
        for (WikiLayout.Hit h : layout.hits) {
            if (lx >= h.x() && lx < h.x() + h.w() && ly >= h.y() && ly < h.y() + h.h()) return h;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (viewing != null) {
            viewing = null;
            return true;
        }
        if (event.button() == 3) { goBack(); return true; }
        if (event.button() == 4) { goForward(); return true; }
        if (event.button() == 0) {
            int barX = viewRight + 4;
            if (maxScroll() > 0 && event.x() >= barX && event.x() < barX + 6 && event.y() >= viewTop && event.y() < viewBottom) {
                draggingBar = true;
                scrollFromBar(event.y());
                return true;
            }
            WikiLayout.Hit h = hitAt(event.x(), event.y());
            if (h != null) {
                follow(h.href());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingBar) {
            scrollFromBar(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingBar = false;
        return super.mouseReleased(event);
    }

    private void scrollFromBar(double my) {
        double f = (my - viewTop) / (double) Math.max(1, viewBottom - viewTop);
        pinned = false;
        scroll = (int) Math.round(f * maxScroll());
        clampScroll();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (viewing == null && vertical != 0) {
            scrollBy(-(int) Math.round(vertical * 30));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean typing = getFocused() instanceof EditBox box && box.isFocused();
        if (viewing != null && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            viewing = null;
            return true;
        }
        if (typing && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            doSearch();
            return true;
        }
        if (!typing) {
            int view = viewBottom - viewTop;
            switch (event.key()) {
                case GLFW.GLFW_KEY_BACKSPACE -> { goBack(); return true; }
                case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_SPACE -> { scrollBy(view - 20); return true; }
                case GLFW.GLFW_KEY_PAGE_UP -> { scrollBy(-(view - 20)); return true; }
                case GLFW.GLFW_KEY_DOWN -> { scrollBy(20); return true; }
                case GLFW.GLFW_KEY_UP -> { scrollBy(-20); return true; }
                case GLFW.GLFW_KEY_HOME -> { scrollBy(-scroll); return true; }
                case GLFW.GLFW_KEY_END -> { scrollBy(maxScroll() - scroll); return true; }
                default -> { }
            }
        }
        if (event.hasAltDown() && event.key() == GLFW.GLFW_KEY_LEFT) { goBack(); return true; }
        if (event.hasAltDown() && event.key() == GLFW.GLFW_KEY_RIGHT) { goForward(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);
        g.fill(left, top, right, bottom, 0xE0101418);
        g.renderOutline(left, top, right - left, bottom - top, 0xFF5A6770);
        g.fill(left + 1, top + 1, right - 1, top + 6 + BAR + 2, 0x40000000);
        g.fill(viewLeft - 4, viewTop - 2, viewRight + 12, viewBottom + 2, 0x50000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        ensureLayout();

        // the page's title, and where it is
        int tx = left + 8 + 22 * 3 + 4;
        String t = font.plainSubstrByWidth(title, Math.max(20, searchLeft - tx - 8));
        g.drawString(font, Component.literal(t).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), tx, top + 6 + 5, 0xFFFFFFFF, true);
        String where = current.startsWith("?") ? "wiki.fishonmc.net  ·  search" : "wiki.fishonmc.net  ·  " + current;
        g.drawString(font, Component.literal(where).withStyle(ChatFormatting.DARK_GRAY), viewLeft, top + 6 + BAR + 3, 0xFFFFFFFF, false);

        if (layout == null) {
            String s = status.isEmpty() ? "Loading..." : status;
            g.drawCenteredString(font, Component.literal(s).withStyle(status.startsWith("Couldn't") ? ChatFormatting.RED : ChatFormatting.GRAY),
                    (viewLeft + viewRight) / 2, (viewTop + viewBottom) / 2, 0xFFFFFFFF);
        } else {
            drawPage(g, mouseX, mouseY);
        }

        if (System.currentTimeMillis() < noteUntil) {
            int w = font.width(note) + 12;
            int nx = width / 2 - w / 2, ny = viewBottom - 20;
            g.fill(nx, ny, nx + w, ny + 14, 0xE0202830);
            g.renderOutline(nx, ny, w, 14, 0xFF5A6770);
            g.drawString(font, Component.literal(note).withStyle(ChatFormatting.YELLOW), nx + 6, ny + 3, 0xFFFFFFFF, false);
        }

        if (viewing != null) drawViewer(g);
    }

    private void drawPage(GuiGraphics g, int mouseX, int mouseY) {
        int ox = viewLeft, oy = viewTop - scroll;
        WikiLayout.Hit hover = hitAt(mouseX, mouseY);
        g.enableScissor(viewLeft - 2, viewTop, viewRight + 2, viewBottom);
        for (WikiLayout.Op op : layout.ops) {
            switch (op) {
                case WikiLayout.Fill f -> {
                    if (oy + f.y() > viewBottom || oy + f.y() + f.h() < viewTop) continue;
                    g.fill(ox + f.x(), oy + f.y(), ox + f.x() + f.w(), oy + f.y() + f.h(), f.argb());
                }
                case WikiLayout.Text tx -> {
                    int th = Math.round(font.lineHeight * tx.scale());
                    if (oy + tx.y() > viewBottom || oy + tx.y() + th < viewTop) continue;
                    if (tx.scale() == 1f) {
                        g.drawString(font, tx.text(), ox + tx.x(), oy + tx.y(), 0xFFFFFFFF, false);
                    } else {
                        g.pose().pushMatrix();
                        g.pose().translate(ox + tx.x(), oy + tx.y());
                        g.pose().scale(tx.scale(), tx.scale());
                        g.drawString(font, tx.text(), 0, 0, 0xFFFFFFFF, true);
                        g.pose().popMatrix();
                    }
                }
                case WikiLayout.Pic p -> {
                    if (oy + p.y() > viewBottom || oy + p.y() + p.h() < viewTop) continue;
                    WikiNet.Picture pic = WikiNet.picture(p.url());
                    int px = ox + p.x(), py = oy + p.y();
                    if (pic != null) {
                        g.blit(RenderPipelines.GUI_TEXTURED, pic.id(), px, py, 0f, 0f, p.w(), p.h(), pic.width(), pic.height(), pic.width(), pic.height());
                    } else {
                        g.fill(px, py, px + p.w(), py + p.h(), 0x30FFFFFF);
                        g.renderOutline(px, py, p.w(), p.h(), 0x40FFFFFF);
                    }
                }
            }
        }
        if (hover != null) {
            // the link under the mouse: lit up, and where it goes
            for (WikiLayout.Hit h : layout.hits) {
                if (!h.href().equals(hover.href())) continue;
                if (Math.abs(h.y() - hover.y()) > 40) continue;
                g.fill(ox + h.x() - 1, oy + h.y() + h.h() - 1, ox + h.x() + h.w() + 1, oy + h.y() + h.h(), 0xC0FFFFFF);
            }
        }
        g.disableScissor();

        // the scrollbar
        int max = maxScroll();
        if (max > 0) {
            int barX = viewRight + 4, viewH = viewBottom - viewTop;
            int knobH = Math.max(16, viewH * viewH / Math.max(1, layout.height));
            int knobY = viewTop + (int) ((viewH - knobH) * (scroll / (double) max));
            g.fill(barX, viewTop, barX + 6, viewBottom, 0x40FFFFFF);
            g.fill(barX, knobY, barX + 6, knobY + knobH, draggingBar ? 0xFFFFFFFF : 0xFFB0B0B0);
        }

        if (hover != null) {
            // the website's own words for it (its tooltip), then where it goes
            java.util.List<net.minecraft.util.FormattedCharSequence> tip = new java.util.ArrayList<>();
            if (hover.tip() != null && !hover.tip().isBlank()) tip.addAll(font.split(Component.literal(hover.tip()).withStyle(ChatFormatting.WHITE), 220));
            tip.add(Component.literal(describe(hover.href())).withStyle(ChatFormatting.GRAY).getVisualOrderText());
            g.setTooltipForNextFrame(font, tip, mouseX, mouseY);
        }
    }

    private String describe(String href) {
        if (href.startsWith("#")) return "Jump to this part of the page";
        String url = WikiNet.absolute(href);
        if (!url.startsWith(WikiNet.BASE)) return "Leaves the wiki (click to copy): " + url;
        if (url.contains("/lib/exe/detail.php") || url.contains("/lib/exe/fetch.php") || url.contains("/_media/") || url.contains("/_detail/")) return "Click to see the picture big";
        try {
            URI uri = URI.create(url.replace(" ", "%20"));
            String id = param(uri.getRawQuery(), "id");
            if (id != null) return "Open: " + pretty(id);
        } catch (IllegalArgumentException ignored) {}
        return "Open";
    }

    private void drawViewer(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xC0000000);
        WikiNet.Picture pic = WikiNet.picture(viewing);
        if (pic == null) {
            g.drawCenteredString(font, Component.literal("Loading the picture...").withStyle(ChatFormatting.GRAY), width / 2, height / 2, 0xFFFFFFFF);
        } else {
            float k = Math.min((width - 40) / (float) pic.width(), (height - 50) / (float) pic.height());
            k = Math.min(k, 3f);
            int w = Math.max(1, Math.round(pic.width() * k)), h = Math.max(1, Math.round(pic.height() * k));
            int x = width / 2 - w / 2, y = height / 2 - h / 2 - 6;
            g.blit(RenderPipelines.GUI_TEXTURED, pic.id(), x, y, 0f, 0f, w, h, pic.width(), pic.height(), pic.width(), pic.height());
            g.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xFF5A6770);
        }
        g.drawCenteredString(font, Component.literal("Click anywhere to close").withStyle(ChatFormatting.DARK_GRAY), width / 2, height - 16, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
