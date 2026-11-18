package dannypx.foe.screens;

import dannypx.foe.handler.logic.FindHandler;
import dannypx.foe.handler.logic.FriendsHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static dannypx.foe.screens.FoeMainScreen.*;

/**
 * /foe friend (or FOE+ menu → Social → Friends): your friends in one window, in the FOE+ menu's look. Add someone by
 * name (online players come up as you type), see who's on, and click a friend for their skin, the instance they're
 * on and their ping, with buttons to message, find or remove them.
 */
public class FriendsScreen extends Screen {
    private final Screen parent;
    private EditBox nameBox;
    private int wx, wy, ww, wh;
    private int scroll = 0;
    private Component status = Component.empty();
    private long statusAt = 0L;
    /// The friend shown on the right; null picks the first.
    private UUID selected = null;
    /// The friend whose Remove was clicked once (a second click removes).
    private UUID confirmRemove = null;
    private static boolean onlineOnly = false;
    /// Skins seen this session, so a friend who goes offline keeps theirs.
    private static final Map<UUID, PlayerSkin> skins = new HashMap<>();
    static { dannypx.foe.handler.logic.MemoryGuard.register("friendSkins", skins::size, skins::clear); }

    /// Who's on, and their ping (-1 unknown): the game's player list (swappable for screenshots).
    static Predicate<UUID> ONLINE = FriendsHandler::isOnline;
    static java.util.function.ToIntFunction<UUID> PING = id -> {
        Minecraft mc = Minecraft.getInstance();
        PlayerInfo i = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(id);
        return i == null ? -1 : i.getLatency();
    };

    static final int ROW = 26, HEAD = 38, FOOT = 28, P = 12;

    private enum Hit { ROW, SUGGEST, FILTER_ALL, FILTER_ON, MSG, JOIN, REMOVE }
    private record Spot(int x, int y, int w, int h, Hit what, int index) {
        boolean has(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private final List<Spot> spots = new ArrayList<>();
    private final List<UUID> rowIds = new ArrayList<>();
    private final List<String> suggestions = new ArrayList<>();

    public FriendsScreen(Screen parent) {
        super(Component.literal("Friends"));
        this.parent = parent;
    }

    /// Package-private for tests: the window {x, y, w, h}, centred.
    static int[] windowAt(int screenW, int screenH) {
        int w = Math.max(340, Math.min(440, screenW - 24)), h = Math.max(220, Math.min(280, screenH - 24));
        w = Math.min(w, screenW - 4); h = Math.min(h, screenH - 4);
        return new int[]{(screenW - w) / 2, (screenH - h) / 2, w, h};
    }

    private int listW() { return (ww - P * 3) * 56 / 100; }
    private int cardX() { return wx + P * 2 + listW(); }
    private int cardW() { return wx + ww - P - cardX(); }

    @Override
    protected void init() {
        int[] win = windowAt(width, height);
        wx = win[0]; wy = win[1]; ww = win[2]; wh = win[3];
        String typed = nameBox == null ? "" : nameBox.getValue();
        int lx = wx + P, lw = listW();
        nameBox = new EditBox(font, lx + 6, wy + HEAD + 11, lw - 60, 10, Component.literal("Name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.literal("Add a friend by name...").withColor(OFF));
        nameBox.setValue(typed);
        nameBox.setTextColor(TITLE);
        addRenderableWidget(nameBox);
        addRenderableWidget(new Flat(lx + lw - 44, wy + HEAD + 6, 44, 18, Component.literal("+ Add"), b -> add(nameBox.getValue())));
        addRenderableWidget(new Flat(wx + ww - P - 64, wy + wh - FOOT + 5, 64, 18, Component.literal("Done"), b -> onClose()));
        setInitialFocus(nameBox);
    }

    //region What it does
    private void add(String name) {
        name = name == null ? "" : name.trim();
        if (name.isEmpty()) return;
        for (Map.Entry<UUID, String> f : FriendsHandler.instance().all().entrySet()) {
            if (f.getValue().equalsIgnoreCase(name)) {
                say(Component.literal(f.getValue() + " is already a friend").withColor(0xFFE0C060));
                selected = f.getKey();
                return;
            }
        }
        String added = FriendsHandler.instance().add(name);
        if (added == null) {
            say(Component.literal(name + " isn't online, add them while they're on").withColor(0xFFFF6B6B));
            return;
        }
        say(Component.literal("Added " + added).withColor(0xFF6BE37A));
        nameBox.setValue("");
        for (Map.Entry<UUID, String> f : FriendsHandler.instance().all().entrySet())
            if (f.getValue().equals(added)) selected = f.getKey();
    }

    private void remove(UUID id) {
        String name = FriendsHandler.instance().all().get(id);
        if (name == null) return;
        FriendsHandler.instance().remove(name);
        say(Component.literal("Removed " + name).withColor(DIM));
        confirmRemove = null;
        if (id.equals(selected)) selected = null;
    }

    private void say(Component c) {
        status = c;
        statusAt = System.currentTimeMillis();
    }
    //endregion

    //region Pure helpers (tested)
    record Friend(UUID id, String name, boolean online) {}

    /// Package-private for tests: friends, online first, then by name; only the online ones when asked.
    static List<Friend> sorted(Map<UUID, String> all, Predicate<UUID> online, boolean onlineOnly) {
        List<Friend> out = new ArrayList<>();
        all.forEach((id, name) -> {
            boolean on = online.test(id);
            if (!onlineOnly || on) out.add(new Friend(id, name, on));
        });
        out.sort((a, b) -> a.online() != b.online() ? (a.online() ? -1 : 1) : a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    /// Package-private for tests: online players matching what's typed (those starting with it first), who aren't
    /// friends yet, at most `max`.
    static List<String> suggest(String typed, List<String> online, Collection<String> friends, int max) {
        List<String> out = new ArrayList<>();
        String t = typed == null ? "" : typed.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return out;
        Set<String> have = new HashSet<>();
        for (String f : friends) have.add(f.toLowerCase(Locale.ROOT));
        for (int pass = 0; pass < 2; pass++)
            for (String n : online) {
                if (out.size() >= max) return out;
                String l = n.toLowerCase(Locale.ROOT);
                boolean fits = pass == 0 ? l.startsWith(t) : !l.startsWith(t) && l.contains(t);
                if (fits && !have.contains(l) && !out.contains(n)) out.add(n);
            }
        return out;
    }

    /// Package-private for tests: the ping's colour, green when good to red when bad.
    static int pingColor(int ms) {
        return ms < 0 ? 0xFF7F95AB : ms < 80 ? 0xFF6BE37A : ms < 160 ? 0xFFB9E36B : ms < 300 ? 0xFFE0C060 : 0xFFFF6B6B;
    }
    //endregion

    //region Drawing
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
    }

    /// Everything but the widgets, drawn under them (the widgets draw in super.render, after this).
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);
        spots.clear();
        rowIds.clear();
        Minecraft mc = Minecraft.getInstance();
        Map<UUID, String> all = FriendsHandler.instance().all();
        List<Friend> everyone = sorted(all, ONLINE, false);
        List<Friend> shown = onlineOnly ? sorted(all, ONLINE, true) : everyone;
        long online = everyone.stream().filter(Friend::online).count();
        for (Friend f : everyone) {
            PlayerInfo info = info(f.id());
            if (info != null) skins.put(f.id(), info.getSkin());
        }
        if (selected != null && !all.containsKey(selected)) selected = null;
        if (selected == null && !shown.isEmpty()) selected = shown.getFirst().id();

        // the window, as the FOE+ menu draws it
        g.fill(0, 0, width, height, 0x50000000);
        for (int i = 1; i <= 6; i++) box(g, wx - 2 - i, wy - i, ww + 4 + 2 * i, wh + 4 + 2 * i, ((int) (0x30 * (1 - i / 7f))) << 24, 0);
        box(g, wx - 2, wy - 2, ww + 4, wh + 4, EDGE, RIM);
        box(g, wx - 1, wy - 1, ww + 2, wh + 2, RIM, 0xFF0D1A28);
        g.fill(wx, wy, wx + ww, wy + 1, RIM_LIGHT);

        // heading
        int hx = wx + P, hr = wx + ww - P;
        g.renderItem(new ItemStack(Items.PLAYER_HEAD), hx, wy + 11);
        g.drawString(font, Component.literal("Friends").withStyle(ChatFormatting.BOLD), hx + 22, wy + 11, TITLE, true);
        Component sub = Component.literal(online + " online").withColor(online > 0 ? 0xFF6BE37A : DIM)
                .append(Component.literal("  ·  " + all.size() + (all.size() == 1 ? " friend" : " friends")).withColor(DIM));
        g.drawString(font, sub, hx + 22, wy + 22, DIM, false);
        // All / Online filter, right of the heading
        int fx = pill(g, hr, wy + 13, "Online", onlineOnly, mouseX, mouseY, Hit.FILTER_ON);
        pill(g, fx - 3, wy + 13, "All", !onlineOnly, mouseX, mouseY, Hit.FILTER_ALL);
        g.fill(hx, wy + HEAD - 2, hr, wy + HEAD - 1, RULE);
        g.fill(hx, wy + HEAD - 2, hx + 32, wy + HEAD - 1, ACCENT);

        int lx = wx + P, lw = listW();
        // the name box's well, lit while you type in it
        boolean typing = nameBox.isFocused();
        box(g, lx, wy + HEAD + 6, lw - 48, 18, typing ? ACCENT : TILE_EDGE, 0xFF08121C);

        // suggestions, or what just happened
        int sy = wy + HEAD + 28;
        suggestions.clear();
        List<String> onlineNames = new ArrayList<>();
        if (mc.getConnection() != null) for (PlayerInfo p : mc.getConnection().getOnlinePlayers()) onlineNames.add(p.getProfile().name());
        if (mc.player != null) onlineNames.remove(mc.player.getGameProfile().name());
        onlineNames.sort(String.CASE_INSENSITIVE_ORDER);
        int sx = lx;
        for (String n : suggest(nameBox.getValue(), onlineNames, all.values(), 6)) {
            int w = font.width(n) + 16;
            if (sx + w > lx + lw) break;
            boolean hot = mouseX >= sx && mouseX < sx + w && mouseY >= sy && mouseY < sy + 12;
            box(g, sx, sy, w, 12, hot ? 0xFF6BE37A : 0xFF2F6B45, hot ? 0xFF24563A : 0xFF173526);
            g.drawString(font, "+", sx + 4, sy + 2, 0xFF6BE37A, false);
            g.drawString(font, n, sx + 11, sy + 2, TITLE, false);
            spots.add(new Spot(sx, sy, w, 12, Hit.SUGGEST, suggestions.size()));
            suggestions.add(n);
            sx += w + 3;
        }
        if (suggestions.isEmpty()) {
            if (System.currentTimeMillis() - statusAt < 4000) g.drawString(font, status, lx + 1, sy + 2, TITLE, false);
            else if (nameBox.getValue().isBlank()) g.drawString(font, Component.literal("Online players come up as you type"), lx + 1, sy + 2, OFF, false);
        }

        // the list
        int top = sy + 17, bottom = wy + wh - FOOT - 4;
        int visible = Math.max(1, (bottom - top) / ROW);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, shown.size() - visible)));
        if (shown.isEmpty()) {
            int cy = top + (bottom - top) / 2 - 10;
            g.drawCenteredString(font, Component.literal(all.isEmpty() ? "No friends yet" : "Nobody's online"), lx + lw / 2, cy, TEXT);
            g.drawCenteredString(font, Component.literal(all.isEmpty() ? "Add someone who's on, above" : "Show everyone with All"), lx + lw / 2, cy + 12, OFF);
        }
        boolean bar = shown.size() > visible;
        g.enableScissor(lx, top, lx + lw, bottom);
        for (int i = 0; i < visible + 1 && i + scroll < shown.size(); i++) {
            Friend f = shown.get(i + scroll);
            row(g, f, lx, top + i * ROW, lw - (bar ? 5 : 0), bottom, mouseX, mouseY, rowIds.size());
            rowIds.add(f.id());
        }
        g.disableScissor();
        if (bar) {
            int track = bottom - top, barH = Math.max(14, track * visible / shown.size());
            int barY = top + (track - barH) * scroll / Math.max(1, shown.size() - visible);
            g.fill(lx + lw - 2, top, lx + lw, bottom, RULE);
            g.fill(lx + lw - 2, barY, lx + lw, barY + barH, ACCENT_DIM);
        }

        // the chosen friend, on the right
        Friend chosen = null;
        for (Friend f : everyone) if (f.id().equals(selected)) chosen = f;
        card(g, chosen, mouseX, mouseY);

        // footer
        g.fill(wx + P, wy + wh - FOOT, wx + ww - P, wy + wh - FOOT + 1, RULE);
        g.drawString(font, Component.literal("Click a friend for their instance, crew and balance"), wx + P, wy + wh - FOOT + 10, OFF, false);
    }

    private int pill(GuiGraphics g, int right, int y, String text, boolean on, int mx, int my, Hit what) {
        int w = font.width(text) + 12, x = right - w;
        boolean hot = mx >= x && mx < right && my >= y && my < y + 14;
        box(g, x, y, w, 14, on ? ACCENT : hot ? 0xFF34506C : TILE_EDGE, on ? 0xFF24496C : hot ? TILE_HOT : TILE);
        g.drawString(font, text, x + 6, y + 3, on ? TITLE : hot ? TEXT : DIM, false);
        spots.add(new Spot(x, y, w, 14, what, 0));
        return x;
    }

    private void row(GuiGraphics g, Friend f, int x, int y, int w, int bottom, int mx, int my, int index) {
        int h = ROW - 3;
        boolean sel = f.id().equals(selected), hot = my < bottom && mx >= x && mx < x + w && my >= y && my < y + h;
        box(g, x, y, w, h, sel ? ACCENT : hot ? 0xFF34506C : TILE_EDGE, sel ? 0xFF1B3450 : hot ? TILE_HOT : TILE);
        if (sel) g.fill(x + 1, y + 3, x + 3, y + h - 3, ACCENT);
        face(g, f, x + 6, y + 3, 17);
        g.drawString(font, font.plainSubstrByWidth(f.name(), w - 40), x + 29, y + 3, f.online() ? TITLE : DIM, f.online());
        Component line;
        if (f.online()) {
            String where = FindHandler.instance().instanceOf(f.name());
            line = Component.literal(where == null ? "Online" : where).withColor(0xFF6BE37A);
        } else line = Component.literal("Offline").withColor(OFF);
        g.drawString(font, line, x + 29, y + 13, TEXT, false);
        if (y + h <= bottom) spots.add(new Spot(x, y, w, Math.min(h, bottom - y), Hit.ROW, index));
    }

    /// A friend's face with a little status dot on its corner; greyed when they're offline.
    private void face(GuiGraphics g, Friend f, int x, int y, int size) {
        PlayerFaceRenderer.draw(g, skinOf(f.id()), x, y, size, f.online() ? 0xFFFFFFFF : 0xFF6A6A6A);
        int d = Math.max(5, size / 3);
        int dx = x + size - d + 2, dy = y + size - d + 2;
        g.fill(dx - 1, dy - 1, dx + d + 1, dy + d + 1, 0xFF0D1A28);
        g.fill(dx, dy, dx + d, dy + d, f.online() ? 0xFF4CD964 : 0xFF55687B);
    }

    /// The right-hand card: the friend's skin, front on, and what they're up to, with Message / Find / Remove.
    private void card(GuiGraphics g, Friend f, int mx, int my) {
        int x = cardX(), y = wy + HEAD + 6, w = cardW(), h = wy + wh - FOOT - 4 - y;
        box(g, x, y, w, h, TILE_EDGE, 0xC00B1724);
        if (f == null) {
            g.drawCenteredString(font, Component.literal("Your friends show here"), x + w / 2, y + h / 2 - 10, TEXT);
            g.drawCenteredString(font, Component.literal("with their skin and instance"), x + w / 2, y + h / 2 + 2, OFF);
            return;
        }
        PlayerSkin skin = skinOf(f.id());

        // the skin, standing on a soft shadow
        int k = h - 30 >= 32 * 3 + 8 ? 3 : 2;
        int dw = 16 * k, dh = 32 * k;
        int dx = x + 12, dy = y + (h - 26 - dh) / 2 + 2;
        for (int i = 0; i < 3; i++) g.fill(dx - 4 + i * 3, dy + dh + 1 + i, dx + dw + 4 - i * 3, dy + dh + 2 + i, (0x50 - i * 0x14) << 24);
        doll(g, skin, dx, dy, k, f.online() ? 0xFFFFFFFF : 0xFF7A7A7A);

        // what we know of them
        int tx = dx + dw + 12, tw = x + w - 8 - tx, ty = y + 10;
        g.drawString(font, Component.literal(font.plainSubstrByWidth(f.name(), tw)).withStyle(ChatFormatting.BOLD), tx, ty, TITLE, true);
        ty += 13;
        String state = f.online() ? "Online" : "Offline";
        int pw = font.width(state) + 15;
        box(g, tx, ty, pw, 12, f.online() ? 0xFF2F8A4A : 0xFF3A4B5C, f.online() ? 0xFF173D25 : 0xFF17222E);
        g.fill(tx + 4, ty + 4, tx + 8, ty + 8, f.online() ? 0xFF4CD964 : 0xFF7F95AB);
        g.drawString(font, state, tx + 11, ty + 2, f.online() ? 0xFF8EF0A0 : DIM, false);
        ty += 18;
        if (f.online()) {
            String where = FindHandler.instance().instanceOf(f.name());
            boolean here = where != null && where.equalsIgnoreCase(myInstance());
            ty = stat(g, tx, ty, tw, "Instance", where == null ? "Checking..." : here ? where + " (you too)" : where, where == null ? OFF : 0xFF7FD4FF);
            int ping = PING.applyAsInt(f.id());
            ty = stat(g, tx, ty, tw, "Ping", ping < 0 ? "?" : ping + " ms", pingColor(ping));
        } else {
            ty = stat(g, tx, ty, tw, "Instance", "Not on", OFF);
        }
        // their crew and balance, asked quietly (/crew info, /bal) while their card is open
        dannypx.foe.handler.logic.FriendInfoHandler.instance().want(f.name());
        var known = dannypx.foe.handler.logic.FriendInfoHandler.instance().of(f.name());
        String crew = known == null || known.crew() == null ? null : known.crew();
        String bal = known == null || known.balance() == null ? null : known.balance();
        ty = stat(g, tx, ty, tw, "Crew", crew == null ? "Checking..." : crew, crew == null ? OFF : crew.equals("No crew") ? DIM : 0xFFE0C060);
        stat(g, tx, ty, tw, "Balance", bal == null ? "Checking..." : bal, bal == null ? OFF : 0xFF6BE37A);

        // buttons along the bottom of the card
        int by = y + h - 22, bx = x + 6, bw = w - 12;
        boolean confirming = f.id().equals(confirmRemove);
        if (f.online()) {
            int each = (bw - 8) / 3;
            button(g, bx, by, each, "Message", 0xFF2A78B8, mx, my, Hit.MSG);
            button(g, bx + each + 4, by, each, "Join", 0xFF2A8A5E, mx, my, Hit.JOIN);
            button(g, bx + (each + 4) * 2, by, bw - (each + 4) * 2, confirming ? "Sure?" : "Remove", 0xFF9E2A2A, mx, my, Hit.REMOVE);
        } else {
            button(g, bx, by, bw, confirming ? "Click again to remove" : "Remove", 0xFF9E2A2A, mx, my, Hit.REMOVE);
        }
    }

    private int stat(GuiGraphics g, int x, int y, int w, String label, String value, int color) {
        g.drawString(font, label, x, y, OFF, false);
        g.drawString(font, font.plainSubstrByWidth(value, w), x, y + 10, color, false);
        return y + 23;
    }

    private void button(GuiGraphics g, int x, int y, int w, String text, int base, int mx, int my, Hit what) {
        boolean hot = mx >= x && mx < x + w && my >= y && my < y + 16;
        box(g, x, y, w, 16, lighten(hot ? lighten(base) : base), hot ? lighten(base) : base);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x30FFFFFF);
        g.drawCenteredString(font, text, x + w / 2, y + 4, 0xFFFFFFFF);
        spots.add(new Spot(x, y, w, 16, what, 0));
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 40), gg = Math.min(255, ((c >> 8) & 0xFF) + 40), b = Math.min(255, (c & 0xFF) + 40);
        return 0xFF000000 | r << 16 | gg << 8 | b;
    }

    /// A player's skin drawn flat, front on: head, body, arms and legs, each with its outer layer, `k` pixels a skin
    /// pixel (16 by 32 skin pixels in all).
    static void doll(GuiGraphics g, PlayerSkin skin, int x, int y, int k, int tint) {
        Identifier tex = skin.body().texturePath();
        for (int[] p : DOLL[skin.model() == PlayerModelType.SLIM ? 1 : 0])
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x + p[4] * k, y + p[5] * k, p[0], p[1], p[2] * k, p[3] * k, p[2], p[3], 64, 64, tint);
    }

    /// Package-private for tests: the doll's parts as {u, v, w, h, at x, at y} in skin pixels, each inner layer then
    /// its outer one; [0] classic arms, [1] slim.
    static final int[][][] DOLL = {dollParts(4), dollParts(3)};

    private static int[][] dollParts(int arm) {
        return new int[][]{
                {8, 8, 8, 8, 4, 0}, {40, 8, 8, 8, 4, 0},                               // head, hat
                {20, 20, 8, 12, 4, 8}, {20, 36, 8, 12, 4, 8},                          // body, jacket
                {44, 20, arm, 12, 4 - arm, 8}, {44, 36, arm, 12, 4 - arm, 8},          // right arm (on our left)
                {36, 52, arm, 12, 12, 8}, {52, 52, arm, 12, 12, 8},                    // left arm
                {4, 20, 4, 12, 4, 20}, {4, 36, 4, 12, 4, 20},                          // right leg
                {20, 52, 4, 12, 8, 20}, {4, 52, 4, 12, 8, 20},                         // left leg
        };
    }

    /// "Instance 5" for the instance you're on, as /find would say it, or "".
    private static String myInstance() {
        String mine = dannypx.foe.handler.fetch.TabOverlayHandler.instance().getInstance();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(mine == null ? "" : mine);
        return m.find() ? "Instance " + Integer.parseInt(m.group(1)) : "";
    }

    private PlayerInfo info(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(id);
    }

    private PlayerSkin skinOf(UUID id) {
        PlayerSkin s = skins.get(id);
        return s != null ? s : DefaultPlayerSkin.get(id);
    }
    //endregion

    //region Input
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        for (Spot s : spots) {
            if (!s.has(mx, my)) continue;
            String name = selected == null ? null : FriendsHandler.instance().all().get(selected);
            if (s.what() != Hit.REMOVE) confirmRemove = null;
            switch (s.what()) {
                case ROW -> { if (s.index() < rowIds.size()) selected = rowIds.get(s.index()); }
                case SUGGEST -> { if (s.index() < suggestions.size()) add(suggestions.get(s.index())); }
                case FILTER_ALL -> { onlineOnly = false; scroll = 0; }
                case FILTER_ON -> { onlineOnly = true; scroll = 0; }
                case MSG -> { if (name != null) Minecraft.getInstance().setScreen(new ChatScreen("/msg " + name + " ", false)); }
                case JOIN -> {
                    // to their instance: /instances, and their instance clicked for you
                    String where = name == null ? null : FindHandler.instance().instanceOf(name);
                    int n = dannypx.foe.handler.logic.InstanceJoinHandler.numberOf(where);
                    if (n < 0) say(Component.literal("Still finding " + name + "'s instance, try again in a moment").withColor(0xFFE0C060));
                    else if (where.equalsIgnoreCase(myInstance())) say(Component.literal("You're on " + where + " already").withColor(DIM));
                    else dannypx.foe.handler.logic.InstanceJoinHandler.instance().join(n);
                }
                case REMOVE -> {
                    if (selected != null) {
                        if (selected.equals(confirmRemove)) remove(selected);
                        else confirmRemove = selected;
                    }
                }
            }
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        confirmRemove = null;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < cardX()) scroll -= (int) Math.signum(scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if ((key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) && nameBox.isFocused()) {
            // Enter takes the first suggestion when what's typed isn't a whole name yet
            String typed = nameBox.getValue().trim();
            boolean exact = suggestions.stream().anyMatch(s -> s.equalsIgnoreCase(typed));
            add(!exact && !suggestions.isEmpty() ? suggestions.getFirst() : typed);
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB && nameBox.isFocused() && !suggestions.isEmpty()) {
            nameBox.setValue(suggestions.getFirst());
            return true;
        }
        return super.keyPressed(event);
    }
    //endregion

    /// A flat button in the FOE+ menu's blue.
    private static final class Flat extends Button {
        Flat(int x, int y, int w, int h, Component msg, OnPress press) {
            super(x, y, w, h, msg, press, DEFAULT_NARRATION);
        }

        @Override
        protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            boolean hot = isHovered();
            box(g, getX(), getY(), width, height, hot ? 0xFF7CC8FF : ACCENT, hot ? 0xFF3A92D6 : 0xFF2A78B8);
            g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + 2, 0x40FFFFFF);
            g.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2 + 1, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
