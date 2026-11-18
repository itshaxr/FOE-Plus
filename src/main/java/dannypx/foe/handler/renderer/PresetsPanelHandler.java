package dannypx.foe.handler.renderer;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.GenericContainerScreenHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FOE+'s presets screen: the server's Fishing Presets menu with all ten presets on one panel
 * (no paging) and an Armor / Rods switch. Every cell is the server's own item, so hovering shows
 * the server's tooltip and a click is the same click on that item: the tick wears a preset, a
 * piece goes back to your inventory. When what you click is on the server's other page, FOE+
 * turns the page first. Your own inventory under the panel is left alone, so pieces are added
 * the way the server has it. The plain menu is one click away.
 */
public final class PresetsPanelHandler {
    private static PresetsPanelHandler INSTANCE;

    public static PresetsPanelHandler instance() {
        if (INSTANCE == null) INSTANCE = new PresetsPanelHandler();
        return INSTANCE;
    }

    private PresetsPanelHandler() {}

    //region The server's menu, as FOE+ reads it
    public static final int ARMOR = 0, PARTS = 1;
    /// Server slots: the page arrows, the armor/parts switch.
    static final int SLOT_BACK = 46, SLOT_SWITCH = 48, SLOT_NEXT = 52;
    private static final Pattern PRESET_NUMBER = Pattern.compile("Preset #(\\d+)");

    /// What FOE+ needs to know about one slot (plain values, so the reading can be tested).
    record SlotInfo(String item, String name, float model, String lore) {
        static final SlotInfo EMPTY = new SlotInfo("air", "", 0f, "");
    }

    /// One page of the server's menu: which kind, which page (0 or 1), and per row whether the preset
    /// is the one in use and which of its three places are empty.
    record Page(int mode, int page, boolean[] active, boolean[][] empty) {}

    /// The status item of row `r` (0-4) and its three pieces sit side by side, a row of nine apart.
    static int slotOf(int row, int col) { return 2 + row * 9 + col; }

    /// Package-private for tests: reads a page from its 54 slots, or null when this isn't the presets menu
    /// as FOE+ knows it (then the plain menu is shown untouched).
    static Page parse(SlotInfo[] slots) {
        if (slots == null || slots.length < 54) return null;
        String sw = slots[SLOT_SWITCH].name();
        int mode;
        if (sw.contains("Switch to Part Presets")) mode = ARMOR;
        else if (sw.contains("Switch to Armor Presets")) mode = PARTS;
        else return null;
        boolean[] active = new boolean[5];
        boolean[][] empty = new boolean[5][3];
        int first = -1;
        for (int r = 0; r < 5; r++) {
            SlotInfo status = slots[slotOf(r, 0)];
            Matcher m = PRESET_NUMBER.matcher(status.lore());
            if (!m.find()) return null;
            int number = Integer.parseInt(m.group(1));
            if (r == 0) first = number;
            else if (number != first + r) return null;
            // The star: model 48, and its tooltip offers to deactivate ("ᴅᴇᴀᴄᴛɪᴠᴀᴛᴇ").
            active[r] = Math.round(status.model()) == 48 || status.lore().contains("ᴅᴇᴀᴄᴛɪᴠᴀᴛᴇ");
            for (int c = 0; c < 3; c++) {
                SlotInfo piece = slots[slotOf(r, c + 1)];
                empty[r][c] = piece.item().equals("air") || (piece.item().equals("gunpowder") && piece.name().endsWith("Slot"));
            }
        }
        if (first != 1 && first != 6) return null;
        return new Page(mode, first == 1 ? 0 : 1, active, empty);
    }

    private static SlotInfo info(ItemStack stack) {
        if (stack.isEmpty()) return SlotInfo.EMPTY;
        float model = 0f;
        var cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd != null && !cmd.floats().isEmpty()) model = cmd.floats().get(0);
        StringBuilder lore = new StringBuilder();
        var lines = stack.get(DataComponents.LORE);
        if (lines != null) lines.lines().forEach(l -> lore.append(l.getString()).append('\n'));
        return new SlotInfo(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getHoverName().getString(), model, lore.toString());
    }

    private static Page read(ChestMenu menu) {
        if (menu.getRowCount() != 6 || menu.slots.size() < 54) return null;
        SlotInfo[] slots = new SlotInfo[54];
        for (int i = 0; i < 54; i++) slots[i] = info(menu.getSlot(i).getItem());
        return parse(slots);
    }
    //endregion

    //region What FOE+ remembers of the ten presets of each kind
    static final class Preset {
        boolean known, active;
        ItemStack status = ItemStack.EMPTY;
        final ItemStack[] pieces = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        final boolean[] empty = new boolean[3];
    }

    final Preset[][] presets = new Preset[2][10];
    { reset(); }
    /// The kind last seen open, shown while a newly opened menu is still empty.
    private int lastMode = ARMOR;
    private long openedAt;
    private int openedFor = -1;
    private long lastGoodAt;

    /// Package-private for tests: how long after the last readable page the panel stays up over an unreadable one.
    static boolean holdOver(long sinceGoodMs) { return sinceGoodMs >= 0 && sinceGoodMs < 1500L; }

    private void reset() {
        for (int m = 0; m < 2; m++) for (int i = 0; i < 10; i++) presets[m][i] = new Preset();
        reopened();
    }

    /// The menu was opened again: what was last seen is shown at once, and every page is looked at afresh.
    private void reopened() {
        visited = new boolean[2][2];
        autoClicks = 0;
        pending = null;
        check = null;
        holdUntil = 0L;
        holdSignature = -1;
        view = -1;
        styleMenuOpen = false;
        wentToHere = false;
    }

    private void remember(ChestMenu menu, Page page) {
        for (int r = 0; r < 5; r++) {
            Preset p = presets[page.mode()][page.page() * 5 + r];
            p.known = true;
            p.active = page.active()[r];
            p.status = menu.getSlot(slotOf(r, 0)).getItem().copy();
            for (int c = 0; c < 3; c++) {
                p.pieces[c] = menu.getSlot(slotOf(r, c + 1)).getItem().copy();
                p.empty[c] = page.empty()[r][c];
            }
        }
        // Only one preset of a kind is in use: one on this page means none on the other, so a star
        // remembered there turns back into a tick (this page's own tick item, as the server draws it).
        int activeRow = -1, tickRow = -1;
        for (int r = 0; r < 5; r++) {
            if (page.active()[r]) activeRow = r;
            else if (tickRow < 0) tickRow = r;
        }
        boolean[] wasActive = new boolean[5];
        for (int r = 0; r < 5; r++) wasActive[r] = presets[page.mode()][(1 - page.page()) * 5 + r].active;
        boolean[] swap = staleStars(activeRow >= 0, wasActive);
        for (int r = 0; r < 5; r++) {
            if (!swap[r]) continue;
            Preset other = presets[page.mode()][(1 - page.page()) * 5 + r];
            other.active = false;
            if (tickRow >= 0) other.status = menu.getSlot(slotOf(tickRow, 0)).getItem().copy();
        }
    }

    /// Package-private for tests: which remembered presets of the other page lose their star, given
    /// whether the open page has the preset in use.
    static boolean[] staleStars(boolean openPageHasActive, boolean[] otherPageActive) {
        boolean[] out = new boolean[otherPageActive.length];
        if (openPageHasActive) for (int i = 0; i < out.length; i++) out[i] = otherPageActive[i];
        return out;
    }
    //endregion

    //region Getting the server to the right page
    /// Something to reach: a page of a kind, and (col >= 0) a click to make there with a mouse button.
    record Target(int mode, int preset, int col, int button, int pickup) {
        Target(int mode, int preset, int col, int button) { this(mode, preset, col, button, -1); }
        int page() { return preset / 5; }
        /// The same click, with the piece you were holding already picked up again.
        Target lifted() { return new Target(mode, preset, col, button, -1); }
    }

    /// Package-private for tests: a menu slot in your own inventory (54-89) that's empty, to set a held piece
    /// down in while the server turns the page; -1 when there's none.
    static int freeInventorySlot(boolean[] emptyAt) {
        // the main inventory first (the hotbar is where your rod is)
        for (int i = 54; i < 81 && i < emptyAt.length; i++) if (emptyAt[i]) return i;
        for (int i = 81; i < 90 && i < emptyAt.length; i++) if (emptyAt[i]) return i;
        return -1;
    }

    /// Package-private for tests: the slot to click next to get from the open page to the target:
    /// the switch, an arrow, the target's own slot, or -1 when there is nothing (more) to click.
    static int nextClick(int mode, int page, Target t) {
        if (mode != t.mode()) return SLOT_SWITCH;
        if (page != t.page()) return t.page() == 1 ? SLOT_NEXT : SLOT_BACK;
        return t.col() < 0 ? -1 : slotOf(t.preset() % 5, t.col());
    }

    private Target pending;
    /// A tick just clicked that should turn into the star: looked at again shortly, and clicked once more if the server missed it.
    private Target check;
    private long checkAt;
    private int checkTries;

    /// Package-private for tests: after a page turn the server needs a moment before it takes the next click.
    static final long SETTLE_MS = 250L, RECHECK_MS = 900L;

    /// Package-private for tests: click the tick again? Only when its page is open, it still isn't in use
    /// after a good wait, and it has been tried once only.
    static boolean clickAgain(boolean pageOpen, boolean inUse, long sinceClickMs, int tries) {
        return pageOpen && !inUse && sinceClickMs >= RECHECK_MS && tries < 1;
    }
    private long pendingSince, holdUntil, lastSeen;
    private int holdSignature = -1;
    private boolean[][] visited = new boolean[2][2];
    private int autoClicks;
    /// Which kind the panel shows (-1: whatever the server has open).
    private int view = -1;

    /// Opened this time: whether the panel has gone to the page with the preset for where you are.
    private boolean wentToHere;

    /// The preset (0-9) of a kind made for where you are: the one whose pieces speak most of your location or its
    /// climate ("While at Cairns", "While in any Monsoon Climate"); -1 when none does.
    int presetForHere(int mode) {
        String here = dannypx.foe.handler.fetch.BossEventHandler.instance().getLocation().getString().replaceAll("[\\uE000-\\uF8FF]", "").trim();
        dannypx.foe.helper.FoeLocations.Location loc = dannypx.foe.helper.FoeLocations.fromName(here);
        String climate = loc == null ? "" : loc.climate.name;
        String[] lore = new String[10];
        for (int i = 0; i < 10; i++) {
            Preset p = presets[mode][i];
            StringBuilder sb = new StringBuilder();
            if (p.known) for (ItemStack piece : p.pieces) {
                var lines = piece.get(DataComponents.LORE);
                if (lines != null) lines.lines().forEach(l -> sb.append(l.getString()).append('\n'));
            }
            lore[i] = sb.toString();
        }
        return bestFor(here, climate, lore);
    }

    /// Package-private for tests: which of these presets' piece texts mention the place (or else its climate) the
    /// most; -1 when none does.
    static int bestFor(String place, String climate, String[] lore) {
        int best = -1, bestScore = 0;
        String pl = place == null ? "" : place.toLowerCase(java.util.Locale.ROOT).trim();
        String cl = climate == null ? "" : climate.toLowerCase(java.util.Locale.ROOT).trim();
        for (int i = 0; i < lore.length; i++) {
            String t = lore[i] == null ? "" : lore[i].toLowerCase(java.util.Locale.ROOT);
            int score = 0;
            if (!pl.isEmpty()) score += 3 * count(t, pl);
            if (!cl.isEmpty()) score += count(t, cl + " climate");
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    private static int count(String text, String what) {
        int n = 0;
        for (int at = text.indexOf(what); at >= 0; at = text.indexOf(what, at + what.length())) n++;
        return n;
    }

    private void want(Target t, long now) {
        pending = t;
        pendingSince = now;
    }

    /// True while your bobber is in the water: the server turns down every change to presets then (and
    /// says so in chat, once for each click), so the panel makes no clicks at all and says why instead.
    static boolean fishingNow() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.player.fishing != null;
    }

    /// One step, each frame: note the open page, then make the next click towards what is wanted.
    private void step(ContainerScreen screen, Page page, long now) {
        Minecraft mc = Minecraft.getInstance();
        if (fishingNow()) {
            // Nothing is clicked while the bobber is out; the pages not seen yet are looked at once it's reeled in.
            pending = null;
            check = null;
            visited[page.mode()][page.page()] = true;
            if (view < 0) view = page.mode();
            return;
        }
        int signature = page.mode() * 2 + page.page();
        if (signature != holdSignature && holdSignature >= 0) {
            // The server answered with the new page: a short settle, then the next click (one sent in the very
            // same moment gets dropped by the server, which is what made a second click necessary).
            holdSignature = -1;
            holdUntil = now + SETTLE_MS;
        }
        if (check != null) {
            boolean open = page.mode() == check.mode() && page.page() == check.page();
            boolean inUse = open && page.active()[check.preset() % 5];
            if (inUse || now - checkAt > 4000L) check = null;
            else if (pending == null && clickAgain(open, false, now - checkAt, checkTries)) {
                checkTries++;
                checkAt = now;
                want(check, now);
            }
        }
        if (view < 0) view = page.mode();
        visited[page.mode()][page.page()] = true;
        // The server's menu follows the kind shown, so a piece clicked in your inventory goes to the right presets.
        if (pending == null && page.mode() != view && autoClicks < 8) want(new Target(view, 0, -1, 0), now);
        // Nothing asked for: have a look at any page of the shown kind FOE+ hasn't seen yet (once each).
        if (pending == null) {
            for (int p = 0; p < 2; p++) {
                if (!visited[view][p] && autoClicks < 8) {
                    visited[view][p] = true;
                    want(new Target(view, p * 5, -1, 0), now);
                    break;
                }
            }
        }
        // Then the server is left on the page with the preset for where you are, so wearing it is one click.
        if (pending == null && !wentToHere && visited[view][0] && visited[view][1]) {
            wentToHere = true;
            int here = presetForHere(view);
            if (here >= 0 && (here / 5 != page.page() || page.mode() != view)) want(new Target(view, here, -1, 0), now);
        }
        if (pending == null) return;
        if (now - pendingSince > 5000L) { pending = null; return; }
        if (now < holdUntil) return;
        int slot = nextClick(page.mode(), page.page(), pending);
        if (slot < 0) { pending = null; return; }
        if (mc.gameMode == null || mc.player == null) return;
        boolean last = slot != SLOT_SWITCH && slot != SLOT_NEXT && slot != SLOT_BACK;
        if (last && pending.pickup() >= 0) {
            // On the right page now: pick the piece you were holding back up, then (after a moment) put it in
            if (screen.getMenu().getCarried().isEmpty() && pending.pickup() < screen.getMenu().slots.size())
                mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, pending.pickup(), 0, ClickType.PICKUP, mc.player);
            pending = pending.lifted();
            holdUntil = now + SETTLE_MS;
            return;
        }
        if (!last && screen.getMenu().getSlot(slot).getItem().isEmpty()) { pending = null; return; }   // no such button here
        mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot, last ? pending.button() : 0, ClickType.PICKUP, mc.player);
        if (last) {
            // A tick clicked to wear a preset: make sure the star follows.
            if (pending.col() == 0 && !page.active()[pending.preset() % 5]) {
                if (check == null || !check.equals(pending)) checkTries = 0;
                check = pending;
                checkAt = now;
            } else check = null;
            pending = null;
        }
        else {
            autoClicks++;
            holdSignature = signature;
            holdUntil = now + 2500L;                           // give the server time to turn the page
        }
    }
    //endregion

    //region Where things are (in GUI pixels)
    static final int PANEL_W = 236, PANEL_H = 158;
    /// The presets: two columns of five cards. Each card: its number, the tick/star in a slot, a divider,
    /// then the three pieces in slots (18 x 18, 2 apart), all on whole pixels.
    static final int CELL_W = 106, CELL_H = 20, CELL_GAP_X = 8, CELL_GAP_Y = 2, CELLS_X = 8, CELLS_Y = 45;
    static final int STATUS_X = 16, PIECES_X = 44, SLOT = 18, SLOT_GAP = 2;
    /// Where a card's parts change: the number and tick/star up to here, then a piece every 20.
    static final int PARTS_SPLIT = 41;
    private static final int TAB_W = 64, TAB_H = 19, TAB_Y = 21, TAB_GAP = 2;
    private static final int HEADER_H = 40;

    /// Package-private for tests: the panel's corner {x, y}. It covers the chest half of the menu and
    /// stops above your inventory, which stays in use.
    static int[] panelAt(int screenW, int screenH) {
        int menuTop = (screenH - 222) / 2;
        return new int[]{(screenW - PANEL_W) / 2, Math.max(2, menuTop + 134 - PANEL_H)};
    }

    static int cellX(int px, int preset) { return px + CELLS_X + (preset / 5) * (CELL_W + CELL_GAP_X); }
    static int cellY(int py, int preset) { return py + CELLS_Y + (preset % 5) * (CELL_H + CELL_GAP_Y); }

    /// Package-private for tests: what is under a point of the panel (relative to its corner):
    /// {preset 0-9, col 0-3} for a cell part (col 0: the tick/star and number), or null.
    static int[] cellAt(int x, int y) {
        for (int i = 0; i < 10; i++) {
            int cx = cellX(0, i), cy = cellY(0, i);
            // No dead pixels between the parts of a row or between rows: the mouse is always on exactly one
            // thing, so a tooltip doesn't blink off and on while moving from one piece to the next.
            int below = i % 5 < 4 ? CELL_GAP_Y : 0;
            if (x < cx || x >= cx + CELL_W || y < cy || y >= cy + CELL_H + below) continue;
            int rel = x - cx;
            if (rel < PARTS_SPLIT) return new int[]{i, 0};
            // Each piece's part is its slot and half the gaps either side of it.
            return new int[]{i, 1 + Math.max(0, Math.min(2, Math.floorDiv(rel - PIECES_X + SLOT_GAP / 2 + 1, SLOT + SLOT_GAP)))};
        }
        return null;
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    //endregion

    //region When the panel is up
    private boolean isPresets(Screen screen) {
        if (!(screen instanceof ContainerScreen chest)) return false;
        if (!Configs.mainConfig.enableMod.get() || !Configs.mainConfig.presetsPanel.get()) return false;
        if (!dannypx.foe.handler.logic.ConnectionHandler.instance().isOnServer()) return false;
        return GenericContainerScreenHandler.PRESETS_SCREEN_CONTAINER.equals(chest.getTitle().getString());
    }

    /// True when the point is on the panel (the menu slots under it are then out of reach).
    public boolean covers(Screen screen, double mouseX, double mouseY) {
        if (!isPresets(screen)) return false;
        if (look() == CLASSIC) {
            int[] lk = classicLookAt(lookW(Minecraft.getInstance().font), screen.width, screen.height);
            return in(mouseX, mouseY, lk[0], lk[1], lk[2], STYLE_H) || (styleMenuOpen && styleOption(lk, mouseX, mouseY) >= 0);
        }
        ChestMenu menu = ((ContainerScreen) screen).getMenu();
        if (read(menu) == null && !loading(menu, System.currentTimeMillis())) return false;
        int[] p = panelAt(screen.width, screen.height);
        return in(mouseX, mouseY, p[0], p[1], PANEL_W, PANEL_H);
    }
    //endregion

    /// A newly opened menu the server hasn't filled yet (for a second at most): the panel is up already.
    private boolean loading(ChestMenu menu, long now) {
        // A click lifts the clicked item onto the mouse until the server answers, and for those few frames
        // the page can't be read: the panel stays up with what was last seen.
        if (holdOver(now - lastGoodAt)) return true;
        // Turning a page opens a new menu too (the server sends a fresh one), so each one gets its own second.
        if (menu.containerId != openedFor) { openedFor = menu.containerId; openedAt = now; }
        if (now - openedAt >= 1000L) return false;
        for (int i = 0; i < 54 && i < menu.slots.size(); i++) if (!menu.getSlot(i).getItem().isEmpty()) return false;
        return true;
    }

    //region Drawing
    public void render(Screen screen, GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!isPresets(screen)) return;
        ContainerScreen chest = (ContainerScreen) screen;
        long now = System.currentTimeMillis();
        if (now - lastSeen > 500L) { reopened(); openedAt = now; openedFor = chest.getMenu().containerId; }   // the menu was closed and opened again
        lastSeen = now;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (look() == CLASSIC) {
            // The server's own menu, as it is: only the Style button over it, to come back to a FOE+ look.
            int[] lk = classicLookAt(lookW(font), screen.width, screen.height);
            g.nextStratum();
            styleButton(g, font, lk, CLASSIC, mouseX, mouseY);
            if (styleMenuOpen) { g.nextStratum(); styleMenu(g, font, lk, CLASSIC, mouseX, mouseY); }
            return;
        }
        Page page = read(chest.getMenu());
        if (page == null) {
            // Not filled yet: the panel with what was last seen, so the plain menu never flashes up first.
            if (loading(chest.getMenu(), now)) drawPanel(g, font, screen.width, screen.height, lastMode, mouseX, mouseY, false);
            return;                                           // otherwise not the menu FOE+ knows: leave it alone
        }
        lastGoodAt = now;
        lastMode = view < 0 ? page.mode() : view;
        remember(chest.getMenu(), page);
        step(chest, page, now);
        ItemStack carried = chest.getMenu().getCarried();
        drawPanel(g, font, screen.width, screen.height, view < 0 ? page.mode() : view, mouseX, mouseY, !carried.isEmpty());
        // What you're holding on the mouse stays on top of the panel (a piece on its way into a preset).
        if (!carried.isEmpty()) {
            g.nextStratum();
            g.renderItem(carried, mouseX - 8, mouseY - 8);
            g.renderItemDecorations(font, carried, mouseX - 8, mouseY - 8);
        }
    }

    // The panel's colours: warm dark wood, with gold for what is chosen.
    private static final int EDGE = 0xFF0D0805, RIM = 0xFF5C4129, BODY = 0xFF261A10, HEAD = 0xFF2F2014, RULE = 0xFF4A3524,
            CARD = 0xFF1F150D, CARD_EDGE = 0xFF3A2918, CARD_HOT = 0xFF2A1C11, CARD_HOT_EDGE = 0xFF6B4D30,
            CARD_ON = 0xFF33230F, GOLD = 0xFFE0A630, GOLD_TEXT = 0xFFFFD873, TEXT = 0xFFE8D8B8, DIM = 0xFF8C7A5E,
            HERE = 0xFF7CF07C,
            SLOT_EDGE = 0xFF0B0704, SLOT_BODY = 0xFF17100A, SLOT_SHADE = 0xFF100B06, SLOT_LIGHT = 0xFF35261A, SLOT_HOT = 0xFF4A3524;

    private static final net.minecraft.resources.Identifier WOOD =
            net.minecraft.resources.Identifier.fromNamespaceAndPath(dannypx.foe.FishOnMCExtras.MOD_ID, "textures/gui/presets_wood.png");

    /// The two tabs' corners: {Armor x, Rods x, y}. They line up with the cards' left edge.
    private static int[] tabsAt(int px, int py) {
        return new int[]{px + CELLS_X, px + CELLS_X + TAB_W + TAB_GAP, py + TAB_Y};
    }

    /// The panel itself, for the kind shown.
    void drawPanel(GuiGraphics g, Font font, int screenW, int screenH, int shown, int mouseX, int mouseY, boolean holding) {
        int[] p = panelAt(screenW, screenH);
        int px = p[0], py = p[1];
        final int realX = mouseX, realY = mouseY;

        g.nextStratum();
        // The frame: a dark edge, a wooden rim, the body, and the header band, closed by a rule the chosen
        // tab opens into.
        g.fill(px - 2, py - 2, px + PANEL_W + 2, py + PANEL_H + 2, EDGE);
        g.fill(px - 1, py - 1, px + PANEL_W + 1, py + PANEL_H + 1, RIM);
        g.fill(px, py, px + PANEL_W, py + PANEL_H, BODY);
        // wooden boards behind everything, the header band a shade lighter
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, WOOD, px, py, 0f, 0f, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
        g.fill(px, py, px + PANEL_W, py + HEADER_H, 0x30FFD9A8);
        g.fill(px, py, px + PANEL_W, py + 1, 0xFF7A583A);
        g.fill(px, py + HEADER_H, px + PANEL_W, py + HEADER_H + 1, RIM);

        // The title, centred, between two fine gold lines ending in small diamonds.
        Component title = Component.literal("FISHING PRESETS").withStyle(ChatFormatting.BOLD);
        int tw = font.width(title), tx = px + (PANEL_W - tw) / 2, ty = py + 6, ly = ty + 3;
        g.drawString(font, title, tx, ty, 0xFFF2E3C2, true);
        ornament(g, px + 12, tx - 9, ly, true);
        ornament(g, tx + tw + 8, px + PANEL_W - 12, ly, false);

        // The tabs, like folder tabs: the chosen one opens into the presets below it.
        int[] t = tabsAt(px, py);
        folderTab(g, font, t[0], t[2], "Armor", in(mouseX, mouseY, t[0], t[2], TAB_W, TAB_H), shown == ARMOR);
        folderTab(g, font, t[1], t[2], "Rods", in(mouseX, mouseY, t[1], t[2], TAB_W, TAB_H), shown == PARTS);
        // The Style menu's button: the look in use, and a small arrow.
        int look = look();
        int[] lk = lookAt(font, px, py);
        styleButton(g, font, lk, look, mouseX, mouseY);
        if (styleMenuOpen) { mouseX = -999; mouseY = -999; }      // the open menu has the mouse: nothing under it lights up

        ItemStack tip = ItemStack.EMPTY;
        int[] hover = in(mouseX, mouseY, px, py, PANEL_W, PANEL_H) ? cellAt(mouseX - px, mouseY - py) : null;
        int forHere = presetForHere(shown);
        for (int i = 0; i < 10; i++) {
            Preset preset = presets[shown][i];
            int cx = cellX(px, i), cy = cellY(py, i);
            boolean hot = hover != null && hover[0] == i && !fishingNow();
            String n = String.valueOf(i + 1);
            int sy = cy + 1;
            boolean overStatus = hot && hover[1] == 0;
            boolean drawStatusItem = true;
            if (look == 1) {
                // Tray cards: a number tab on the card's left, the three pieces on one shared tray.
                g.fill(cx, cy, cx + CELL_W, cy + CELL_H, preset.active ? GOLD : hot ? CARD_HOT_EDGE : 0xFF17100A);
                g.fill(cx + 1, cy + 1, cx + CELL_W - 1, cy + CELL_H - 1, hot ? CARD_HOT : CARD);
                g.fill(cx + 1, cy + 1, cx + 14, cy + CELL_H - 1, preset.active ? GOLD : 0xFF33241A);
                g.drawString(font, n, cx + 1 + (13 - font.width(n) + 1) / 2, cy + (CELL_H - 7) / 2, preset.active ? 0xFF2B1D12 : i == forHere ? HERE : 0xFFB8A888, false);
                if (overStatus) g.fill(cx + STATUS_X + 1, sy + 1, cx + STATUS_X + SLOT - 1, sy + SLOT - 1, SLOT_HOT);
                int tx0 = cx + PIECES_X - 1, tx1 = cx + PIECES_X + 3 * SLOT + 2 * SLOT_GAP + 1;
                g.fill(tx0, sy, tx1, sy + SLOT, SLOT_EDGE);
                g.fill(tx0 + 1, sy + 1, tx1 - 1, sy + SLOT - 1, SLOT_BODY);
                g.fill(tx0 + 1, sy + SLOT - 2, tx1 - 1, sy + SLOT - 1, SLOT_LIGHT);
                for (int c = 0; c < 3; c++) {
                    int ix = cx + PIECES_X + c * (SLOT + SLOT_GAP);
                    if (hot && hover[1] == c + 1) g.fill(ix, sy + 1, ix + SLOT, sy + SLOT - 1, SLOT_HOT);
                    if (c > 0) g.fill(ix - 1, sy + 4, ix, sy + SLOT - 4, 0xFF2A1D12);
                }
            } else if (look == 2) {
                // Light slots: a line under each row, warm lighter slots the pieces stand out on, and a
                // round marker for the preset in use (gold when worn) in place of the tick.
                if (preset.active) g.fill(cx, cy, cx + CELL_W, cy + CELL_H, 0x66E0A630);
                else if (hot) g.fill(cx, cy, cx + CELL_W, cy + CELL_H, 0x28FFD9A8);
                if (i % 5 < 4) g.fill(cx, cy + CELL_H, cx + CELL_W, cy + CELL_H + 1, 0x40FFD9A8);
                g.drawString(font, n, cx + 2 + (13 - font.width(n) + 1) / 2, cy + (CELL_H - 7) / 2, preset.active ? GOLD_TEXT : i == forHere ? HERE : 0xFFB8A888, false);
                int rcx = cx + STATUS_X + 9, rcy = cy + CELL_H / 2;
                disc(g, rcx, rcy, 6, preset.active ? GOLD : overStatus ? 0xFFB8A888 : 0xFF6B5A44);
                disc(g, rcx, rcy, 5, preset.active ? 0xFF33230F : hot ? 0xFF2D2015 : 0xFF21160D);
                if (preset.active) disc(g, rcx, rcy, 3, GOLD_TEXT);
                else if (overStatus) disc(g, rcx, rcy, 3, 0xFF8C7A5E);
                drawStatusItem = false;
                for (int c = 0; c < 3; c++) {
                    int ix = cx + PIECES_X + c * (SLOT + SLOT_GAP);
                    boolean over = hot && hover[1] == c + 1;
                    g.fill(ix + 1, sy, ix + SLOT - 1, sy + SLOT, over ? 0xFF7A5E40 : 0xFF4A3828);
                    g.fill(ix, sy + 1, ix + SLOT, sy + SLOT - 1, over ? 0xFF7A5E40 : 0xFF4A3828);
                    g.fill(ix + 1, sy + 1, ix + SLOT - 1, sy + 2, over ? 0xFF8C6E4C : 0xFF574332);
                }
            } else {
                // Clean rows: no boxes. Soft bands, a gold bar and glow for the preset in use, and the pieces
                // standing free; only what the mouse is on lights up.
                if (preset.active) {
                    g.fill(cx, cy, cx + CELL_W, cy + CELL_H, 0x55E0A630);
                    g.fill(cx, cy, cx + 2, cy + CELL_H, GOLD);
                } else if (hot) g.fill(cx, cy, cx + CELL_W, cy + CELL_H, 0x28FFD9A8);
                else if ((i % 5) % 2 == 0) g.fill(cx, cy, cx + CELL_W, cy + CELL_H, 0x30000000);
                g.drawString(font, n, cx + 3 + (13 - font.width(n) + 1) / 2, cy + (CELL_H - 7) / 2, preset.active ? GOLD_TEXT : i == forHere ? HERE : 0xFFA89878, false);
                if (overStatus) g.fill(cx + STATUS_X, sy, cx + STATUS_X + SLOT, sy + SLOT, SLOT_HOT);
                for (int c = 0; c < 3; c++) {
                    int ix = cx + PIECES_X + c * (SLOT + SLOT_GAP);
                    if (hot && hover[1] == c + 1) g.fill(ix, sy, ix + SLOT, sy + SLOT, SLOT_HOT);
                }
            }
            if (!preset.known) {
                smallText(g, font, "loading...", cx + PIECES_X + 2, cy + (CELL_H - 5) / 2, DIM);
                continue;
            }
            if (drawStatusItem) g.renderItem(preset.status, cx + STATUS_X + 1, sy + 1);
            if (hot && hover[1] == 0) tip = preset.status;
            for (int c = 0; c < 3; c++) {
                int ix = cx + PIECES_X + c * (SLOT + SLOT_GAP) + 1;
                g.renderItem(preset.pieces[c], ix, sy + 1);
                g.renderItemDecorations(font, preset.pieces[c], ix, sy + 1);
                if (hot && hover[1] == c + 1) tip = preset.pieces[c];
            }
        }

        if (look != 1) {
            // A fine line between the two columns.
            int mid = px + PANEL_W / 2;
            g.fill(mid, cellY(py, 0) + 1, mid + 1, cellY(py, 4) + CELL_H - 1, 0xFF3A2918);
        }

        if (fishingNow()) {
            // The server won't change presets while you're fishing: say so here, not in chat.
            int top = py + CELLS_Y - 2, bottom = cellY(py, 4) + CELL_H + 2;
            int bw = 176, bh = 46, bx = px + (PANEL_W - bw) / 2, by = top + (bottom - top - bh) / 2;
            g.nextStratum();
            g.fill(px + 1, top, px + PANEL_W - 1, bottom, 0xC00D0805);
            g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, GOLD);
            g.fill(bx, by, bx + bw, by + bh, HEAD);
            g.drawCenteredString(font, Component.literal("Reel in your rod").withStyle(ChatFormatting.BOLD), px + PANEL_W / 2, by + 9, GOLD_TEXT);
            g.drawCenteredString(font, Component.literal("Presets can't be used"), px + PANEL_W / 2, by + 23, TEXT);
            g.drawCenteredString(font, Component.literal("while your bobber is out."), px + PANEL_W / 2, by + 33, TEXT);
            tip = ItemStack.EMPTY;
        }

        if (styleMenuOpen) {
            // The Style menu, over the rows: every look, the one in use marked in gold.
            g.nextStratum();
            styleMenu(g, font, lk, look, realX, realY);
        } else if (!tip.isEmpty() && !holding) ItemTooltip.draw(g, font, tip, mouseX, mouseY);
    }

    /// The Style menu's button: the look in use, and a small arrow.
    private void styleButton(GuiGraphics g, Font font, int[] lk, int look, int mouseX, int mouseY) {
        boolean overLook = in(mouseX, mouseY, lk[0], lk[1], lk[2], STYLE_H);
        g.fill(lk[0], lk[1], lk[0] + lk[2], lk[1] + STYLE_H, overLook || styleMenuOpen ? 0xFF9A7236 : RULE);
        g.fill(lk[0] + 1, lk[1] + 1, lk[0] + lk[2] - 1, lk[1] + STYLE_H - 1, overLook || styleMenuOpen ? 0xFF3A281A : HEAD);
        g.drawString(font, lookLabel(look), lk[0] + 5, lk[1] + 3, overLook || styleMenuOpen ? 0xFFFFF4D6 : 0xFFB8A888, false);
        int ax = lk[0] + lk[2] - 9, ay = lk[1] + 5, ac = overLook || styleMenuOpen ? 0xFFFFF4D6 : 0xFFB8A888;
        g.fill(ax, ay, ax + 5, ay + 1, ac); g.fill(ax + 1, ay + 1, ax + 4, ay + 2, ac); g.fill(ax + 2, ay + 2, ax + 3, ay + 3, ac);
    }

    /// Package-private for tests: in Classic, where the Style button sits over the server's menu {x, y, w}: just
    /// above the chest's top right corner (inside its top edge when the screen is too short for that).
    static int[] classicLookAt(int lookW, int screenW, int screenH) {
        int chestTop = (screenH - 222) / 2, chestRight = (screenW + 176) / 2;
        int y = chestTop - STYLE_H - 2;
        if (y < 2) y = chestTop + 3;
        return new int[]{chestRight - lookW, y, lookW};
    }

    /// The Style menu, open under its button: every look, the one in use marked in gold.
    private void styleMenu(GuiGraphics g, Font font, int[] lk, int look, int realX, int realY) {
        {
            int hotOpt = styleOption(lk, realX, realY);
            g.fill(lk[0] - 1, lk[1] + STYLE_H, lk[0] + lk[2] + 1, lk[1] + STYLE_H + LOOKS * STYLE_H + 1, EDGE);
            for (int o = 0; o < LOOKS; o++) {
                int oy = lk[1] + STYLE_H + o * STYLE_H;
                boolean chosen = o == look;
                g.fill(lk[0], oy, lk[0] + lk[2], oy + STYLE_H, o == hotOpt ? 0xFF5C4129 : chosen ? 0xFF3F2B14 : 0xFF2B1D12);
                if (chosen) g.fill(lk[0], oy, lk[0] + 2, oy + STYLE_H, GOLD);
                g.drawString(font, lookLabel(o), lk[0] + 5, oy + 3, chosen ? GOLD_TEXT : o == hotOpt ? 0xFFFFF4D6 : TEXT, false);
            }
        }
    }

    /// Which look the preset rows have: 0 clean rows, 1 tray cards, 2 light slots (the player's choice, kept).
    static int look() {
        return Configs.mainConfig.presetsStyle.get().ordinal();
    }

    private static void setLook(int look) {
        Configs.mainConfig.presetsStyle.accept(dannypx.foe.type.PresetsStyle.values()[look]);
        Configs.mainConfig.save();
    }

    private static String lookLabel(int look) { return look == CLASSIC ? "Classic" : "Style " + "ABC".charAt(look); }

    /// How many looks the menu offers, and which one is the server's own menu.
    static final int LOOKS = 4, CLASSIC = 3;

    static final int STYLE_H = 13;
    /// The Style menu is open under its button.
    private boolean styleMenuOpen;

    /// Where the Style button sits, {x, y, w}.
    static int[] lookAt(Font font, int px, int py) {
        int w = lookW(font);
        return new int[]{px + PANEL_W - CELLS_X - w, py + TAB_Y + 4, w};
    }

    static int lookW(Font font) { return Math.max(font.width("Style A"), font.width("Classic")) + 18; }

    /// Package-private for tests: which of the open menu's three options is at (x, y), or -1. They sit under the button.
    static int styleOption(int[] lk, double x, double y) {
        if (x < lk[0] || x >= lk[0] + lk[2]) return -1;
        int o = (int) Math.floor((y - (lk[1] + STYLE_H)) / STYLE_H);
        return o >= 0 && o < LOOKS ? o : -1;
    }

    /// A filled circle, on whole pixels.
    private static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.round(Math.sqrt(r * r - dy * dy + 0.5));
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
    }

    /// A slot like the game's own, in the panel's wood: dark edge, shaded top and left, lit bottom and right.
    private static void slot(GuiGraphics g, int x, int y, boolean hot) {
        g.fill(x, y, x + SLOT, y + SLOT, SLOT_EDGE);
        g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, hot ? SLOT_HOT : SLOT_BODY);
        if (hot) return;
        g.fill(x + 1, y + 1, x + SLOT - 1, y + 2, SLOT_SHADE);
        g.fill(x + 1, y + 1, x + 2, y + SLOT - 1, SLOT_SHADE);
        g.fill(x + 1, y + SLOT - 2, x + SLOT - 1, y + SLOT - 1, SLOT_LIGHT);
        g.fill(x + SLOT - 2, y + 1, x + SLOT - 1, y + SLOT - 1, SLOT_LIGHT);
    }

    /// A fine gold line from x0 to x1 at y, brighter towards the title, with a small diamond at the title's end.
    private static void ornament(GuiGraphics g, int x0, int x1, int y, boolean titleOnRight) {
        if (x1 - x0 < 8) return;
        int len = x1 - x0;
        int[] shades = {0xFF4A3524, 0xFF6B4D30, 0xFF9A7236, 0xFFC08A34};
        for (int k = 0; k < 4; k++) {
            // Four lengths of line, each a shade brighter towards the title.
            int a = x0 + len * k / 4, b = x0 + len * (k + 1) / 4;
            int shade = shades[titleOnRight ? k : 3 - k];
            g.fill(a, y, b, y + 1, shade);
        }
        int dx = titleOnRight ? x1 + 3 : x0 - 4;
        g.fill(dx, y - 1, dx + 1, y + 2, GOLD);
        g.fill(dx - 1, y, dx + 2, y + 1, GOLD);
    }

    /// A folder tab. The chosen one: rimmed, a gold line along its top, its body running on into the panel's.
    /// The others: set back a little, darker.
    private static void folderTab(GuiGraphics g, Font font, int x, int y, String text, boolean hover, boolean on) {
        int tx = x + (TAB_W - font.width(text) + 1) / 2;
        if (on) {
            g.fill(x, y, x + TAB_W, y + TAB_H + 1, RIM);
            g.fill(x + 1, y + 1, x + TAB_W - 1, y + TAB_H + 1, BODY);
            g.fill(x + 1, y, x + TAB_W - 1, y + 1, GOLD);
            g.drawString(font, text, tx, y + 6, GOLD_TEXT, true);
        } else {
            g.fill(x, y + 2, x + TAB_W, y + TAB_H, EDGE);
            g.fill(x + 1, y + 3, x + TAB_W - 1, y + TAB_H, hover ? 0xFF33241A : 0xFF1C130B);
            g.drawString(font, text, tx, y + 7, hover ? 0xFFFFF4D6 : 0xFF9C8A6C, false);
        }
    }

    /// Small, sharp lettering (whole screen pixels), like the small buttons'.
    private static void smallText(GuiGraphics g, Font font, String text, int x, int y, int color) {
        int gui = Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
        float sc = smallTextPixels(gui) / (float) gui;
        g.pose().pushMatrix();
        g.pose().translate(Math.round(x * gui) / (float) gui, Math.round(y * gui) / (float) gui);
        g.pose().scale(sc, sc);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    /// Package-private for tests: the size small lettering is drawn at, as whole screen pixels per font
    /// pixel (about two thirds of full size). Whole pixels keep small letters sharp; a plain fraction doesn't.
    static int smallTextPixels(int guiScale) {
        return Math.max(1, Math.round(guiScale * 0.67f));
    }

    /// Package-private for tests: a small button's width in GUI pixels for words `fontWidth` font pixels wide.
    static int fitWidth(int fontWidth, int k, int gui) {
        return (int) Math.ceil((fontWidth - 1) * k / (double) gui) + 6;
    }

    //endregion

    //region Clicks
    /// False when the click was the panel's (the menu under it must not get it).
    public boolean allowClick(Screen screen, MouseButtonEvent event) {
        if (!isPresets(screen)) return true;
        ContainerScreen chest = (ContainerScreen) screen;
        if (look() == CLASSIC) {
            Minecraft mcc = Minecraft.getInstance();
            int[] lk = classicLookAt(lookW(mcc.font), screen.width, screen.height);
            boolean onButton = in(event.x(), event.y(), lk[0], lk[1], lk[2], STYLE_H);
            if (styleMenuOpen) {
                int o = styleOption(lk, event.x(), event.y());
                styleMenuOpen = false;
                if (o >= 0) { if (o != look()) setLook(o); click(mcc); return false; }
                if (onButton) { click(mcc); return false; }
                return true;                                  // a click elsewhere closes the menu and goes to the menu below
            }
            if (onButton) { styleMenuOpen = true; click(mcc); return false; }
            return true;                                      // everything else: the server's own menu, as usual
        }
        Page page = read(chest.getMenu());
        if (page == null) return !covers(screen, event.x(), event.y());   // still filling: nothing to click yet
        Minecraft mc = Minecraft.getInstance();
        int[] p = panelAt(screen.width, screen.height);
        int px = p[0], py = p[1];
        if (!in(event.x(), event.y(), px, py, PANEL_W, PANEL_H)) return true;     // your inventory, or outside: as usual
        long now = System.currentTimeMillis();
        autoClicks = 0;
        check = null;
        int shown = view < 0 ? page.mode() : view;
        int[] t = tabsAt(px, py);
        int tabY = t[2], tabA = t[0], tabR = t[1];
        int[] lk = lookAt(mc.font, px, py);
        if (styleMenuOpen) {
            // An option picks that look; a click anywhere else just closes the menu.
            int o = styleOption(lk, event.x(), event.y());
            styleMenuOpen = false;
            if (o >= 0) { if (o != look()) setLook(o); click(mc); }
            else if (in(event.x(), event.y(), lk[0], lk[1], lk[2], STYLE_H)) click(mc);
        } else if (in(event.x(), event.y(), lk[0], lk[1], lk[2], STYLE_H)) {
            styleMenuOpen = true;
            click(mc);
        } else if (fishingNow()) {
            // Every click would be turned down by the server while the bobber is out: the panel says why instead.
        } else if (in(event.x(), event.y(), tabA, tabY, TAB_W, TAB_H) || in(event.x(), event.y(), tabR, tabY, TAB_W, TAB_H)) {
            int to = in(event.x(), event.y(), tabA, tabY, TAB_W, TAB_H) ? ARMOR : PARTS;
            if (to != shown) {
                view = to;
                pending = null;
                click(mc);
            }
        } else {
            int[] cell = cellAt((int) event.x() - px, (int) event.y() - py);
            int button = event.button();
            if (cell != null && (button == 0 || button == 1) && presets[shown][cell[0]].known) {
                Target t2 = new Target(shown, cell[0], cell[1], button);
                ItemStack held = chest.getMenu().getCarried();
                boolean otherPage = page.mode() != shown || page.page() != t2.page();
                if (!held.isEmpty() && otherPage && cell[1] > 0 && mc.gameMode != null && mc.player != null) {
                    // Holding a piece for a preset on the server's other page: turning the page with it on the
                    // mouse loses it (the server sends it back to your inventory). So it's set down in an empty
                    // slot of yours first, the page is turned, and it's picked up and put in place.
                    boolean[] emptyAt = new boolean[chest.getMenu().slots.size()];
                    for (int i = 54; i < emptyAt.length; i++) emptyAt[i] = chest.getMenu().getSlot(i).getItem().isEmpty();
                    int free = freeInventorySlot(emptyAt);
                    if (free >= 0) {
                        mc.gameMode.handleInventoryMouseClick(chest.getMenu().containerId, free, 0, ClickType.PICKUP, mc.player);
                        t2 = new Target(shown, cell[0], cell[1], button, free);
                    }
                }
                want(t2, now);
                click(mc);
            }
        }
        return false;
    }

    /// Releases and drags on the panel are the panel's too.
    public boolean allowOther(Screen screen, double mouseX, double mouseY) {
        return !covers(screen, mouseX, mouseY);
    }

    private static void click(Minecraft mc) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
    //endregion
}
