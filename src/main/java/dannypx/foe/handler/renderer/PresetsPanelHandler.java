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
    record Target(int mode, int preset, int col, int button) {
        int page() { return preset / 5; }
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
    /// You asked for the server's plain menu instead (until you switch back).
    private boolean classic = false;

    private void want(Target t, long now) {
        pending = t;
        pendingSince = now;
    }

    /// One step, each frame: note the open page, then make the next click towards what is wanted.
    private void step(ContainerScreen screen, Page page, long now) {
        Minecraft mc = Minecraft.getInstance();
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
        if (pending == null) return;
        if (now - pendingSince > 5000L) { pending = null; return; }
        if (now < holdUntil) return;
        int slot = nextClick(page.mode(), page.page(), pending);
        if (slot < 0) { pending = null; return; }
        if (mc.gameMode == null || mc.player == null) return;
        boolean last = slot != SLOT_SWITCH && slot != SLOT_NEXT && slot != SLOT_BACK;
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
    static final int PANEL_W = 204, PANEL_H = 158;
    private static final int CELL_W = 94, CELL_H = 19, CELL_GAP_X = 4, CELL_GAP_Y = 1;
    private static final int TAB_W = 60, TAB_H = 14, CLASSIC_H = 9, CELLS_Y = 42, VIEW_W = 106, VIEW_H = 16;

    /// Package-private for tests: the panel's corner {x, y}. It covers the chest half of the menu and
    /// stops above your inventory, which stays in use.
    static int[] panelAt(int screenW, int screenH) {
        int menuTop = (screenH - 222) / 2;
        return new int[]{(screenW - PANEL_W) / 2, Math.max(2, menuTop + 134 - PANEL_H)};
    }

    private static int cellX(int px, int preset) { return px + 6 + (preset / 5) * (CELL_W + CELL_GAP_X); }
    private static int cellY(int py, int preset) { return py + CELLS_Y + (preset % 5) * (CELL_H + CELL_GAP_Y); }

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
            if (rel < 33) return new int[]{i, 0};
            return new int[]{i, 1 + Math.min(2, (rel - 33) / 19)};
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
        if (classic || !isPresets(screen)) return false;
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
        Page page = read(chest.getMenu());
        if (page == null) {
            // Not filled yet: the panel with what was last seen, so the plain menu never flashes up first.
            if (!classic && loading(chest.getMenu(), now)) drawPanel(g, font, screen.width, screen.height, lastMode, mouseX, mouseY, false);
            return;                                           // otherwise not the menu FOE+ knows: leave it alone
        }
        lastGoodAt = now;
        lastMode = view < 0 ? page.mode() : view;
        remember(chest.getMenu(), page);
        if (classic) {
            int[] b = TravelMapHandler.backButton(screen.width, screen.height, 6);
            g.nextStratum();
            button(g, font, b[0] - (VIEW_W - 84) / 2, b[1], VIEW_W, VIEW_H, "FOE+ presets view", in(mouseX, mouseY, b[0] - (VIEW_W - 84) / 2, b[1], VIEW_W, VIEW_H), false);
            return;
        }
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

    /// The panel itself, for the kind shown.
    void drawPanel(GuiGraphics g, Font font, int screenW, int screenH, int shown, int mouseX, int mouseY, boolean holding) {
        int[] p = panelAt(screenW, screenH);
        int px = p[0], py = p[1];

        g.nextStratum();
        g.fill(px - 2, py - 2, px + PANEL_W + 2, py + PANEL_H + 2, 0xFF120C08);
        g.fill(px - 1, py - 1, px + PANEL_W + 1, py + PANEL_H + 1, 0xFF5C4129);
        g.fill(px, py, px + PANEL_W, py + PANEL_H, 0xFF2B1D12);
        g.fill(px, py, px + PANEL_W, py + 1, 0xFF7A583A);
        g.drawCenteredString(font, Component.literal("FISHING PRESETS").withStyle(ChatFormatting.BOLD), px + PANEL_W / 2, py + 7, 0xFFF2E3C2);
        int tabY = py + 22, tabA = px + PANEL_W / 2 - TAB_W - 2, tabR = px + PANEL_W / 2 + 2;
        button(g, font, tabA, tabY, TAB_W, TAB_H, "Armor", in(mouseX, mouseY, tabA, tabY, TAB_W, TAB_H), shown == ARMOR);
        button(g, font, tabR, tabY, TAB_W, TAB_H, "Rods", in(mouseX, mouseY, tabR, tabY, TAB_W, TAB_H), shown == PARTS);

        ItemStack tip = ItemStack.EMPTY;
        int[] hover = in(mouseX, mouseY, px, py, PANEL_W, PANEL_H) ? cellAt(mouseX - px, mouseY - py) : null;
        for (int i = 0; i < 10; i++) {
            Preset preset = presets[shown][i];
            int cx = cellX(px, i), cy = cellY(py, i);
            boolean hot = hover != null && hover[0] == i;
            // The preset in use: a gold frame.
            if (preset.active) g.fill(cx - 1, cy - 1, cx + CELL_W + 1, cy + CELL_H + 1, 0xFFF2B33D);
            g.fill(cx, cy, cx + CELL_W, cy + CELL_H, preset.active ? 0xFF3A2812 : hot ? 0xFF3A281A : 0xFF21160D);
            small(g, font, String.valueOf(i + 1), cx + 3, cy + 7, preset.active ? 0xFFFFD873 : 0xFFB8A888);
            if (!preset.known) {
                small(g, font, "loading...", cx + 36, cy + 7, 0xFF8A7A62);
                continue;
            }
            if (hot && hover[1] == 0) g.fill(cx + 12, cy + 1, cx + 31, cy + CELL_H - 1, 0xFF5C4129);
            g.renderItem(preset.status, cx + 14, cy + 1);
            if (hot && hover[1] == 0) tip = preset.status;
            for (int c = 0; c < 3; c++) {
                int ix = cx + 34 + c * 19;
                boolean over = hot && hover[1] == c + 1;
                g.fill(ix, cy + 1, ix + 18, cy + CELL_H - 1, over ? 0xFF5C4129 : 0xFF150E08);
                g.renderItem(preset.pieces[c], ix + 1, cy + 1);
                g.renderItemDecorations(font, preset.pieces[c], ix + 1, cy + 1);
                if (over) tip = preset.pieces[c];
            }
        }

        int footY = py + PANEL_H - CLASSIC_H - 4;
        int classicW = smallButtonWidth(font, "Classic menu");
        int classicX = px + PANEL_W - classicW - 5;
        smallButton(g, font, classicX, footY, classicW, CLASSIC_H, "Classic menu", in(mouseX, mouseY, classicX, footY, classicW, CLASSIC_H));

        if (!tip.isEmpty() && !holding) ItemTooltip.draw(g, font, tip, mouseX, mouseY);
    }

    private static void small(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(0.5f, 0.5f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    /// Package-private for tests: the size small lettering is drawn at, as whole screen pixels per font
    /// pixel (about two thirds of full size). Whole pixels keep small letters sharp; a plain fraction doesn't.
    static int smallTextPixels(int guiScale) {
        return Math.max(1, Math.round(guiScale * 0.67f));
    }

    /// How wide a small button is for its words: the lettering plus 3 pixels either side, so the box fits the text.
    private static int smallButtonWidth(Font font, String text) {
        int gui = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        return fitWidth(font.width(text), smallTextPixels(gui), gui);
    }

    /// Package-private for tests: a small button's width in GUI pixels for words `fontWidth` font pixels wide.
    static int fitWidth(int fontWidth, int k, int gui) {
        return (int) Math.ceil((fontWidth - 1) * k / (double) gui) + 6;
    }

    /// A small, quiet button: small sharp lettering, placed on whole screen pixels.
    private static void smallButton(GuiGraphics g, Font font, int x, int y, int w, int h, String text, boolean hover) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, hover ? 0xFFFFF4D6 : 0xFF120C08);
        g.fill(x, y, x + w, y + h, hover ? 0xFF4A3524 : 0xFF3A281A);
        int gui = Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
        int k = smallTextPixels(gui);
        float s = k / (float) gui;
        float tx = Math.round((x + (w - (font.width(text) - 1) * s) / 2f) * gui) / (float) gui;
        float ty = Math.round((y + (h - 7 * s) / 2f) * gui) / (float) gui;
        g.pose().pushMatrix();
        g.pose().translate(tx, ty);
        g.pose().scale(s, s);
        g.drawString(font, text, 0, 0, hover ? 0xFFFFF4D6 : 0xFFE8D8B8, false);
        g.pose().popMatrix();
    }

    private static void button(GuiGraphics g, Font font, int x, int y, int w, int h, String text, boolean hover, boolean on) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, on ? 0xFFF2B33D : hover ? 0xFFFFF4D6 : 0xFF120C08);
        g.fill(x, y, x + w, y + h, on ? 0xFF5C4129 : hover ? 0xFF4A3524 : 0xFF3A281A);
        g.pose().pushMatrix();
        // Full-size letters on whole pixels: scaled-down text comes out uneven.
        g.pose().translate(x + (w - font.width(text) + 1) / 2, y + (h - 7) / 2);
        g.drawString(font, text, 0, 0, on ? 0xFFFFD873 : hover ? 0xFFFFF4D6 : 0xFFE8D8B8, false);
        g.pose().popMatrix();
    }
    //endregion

    //region Clicks
    /// False when the click was the panel's (the menu under it must not get it).
    public boolean allowClick(Screen screen, MouseButtonEvent event) {
        if (!isPresets(screen)) return true;
        ContainerScreen chest = (ContainerScreen) screen;
        Page page = read(chest.getMenu());
        if (page == null) return !covers(screen, event.x(), event.y());   // still filling: nothing to click yet
        Minecraft mc = Minecraft.getInstance();
        if (classic) {
            int[] b = TravelMapHandler.backButton(screen.width, screen.height, 6);
            if (in(event.x(), event.y(), b[0] - (VIEW_W - 84) / 2, b[1], VIEW_W, VIEW_H)) {
                classic = false;
                click(mc);
                return false;
            }
            return true;
        }
        int[] p = panelAt(screen.width, screen.height);
        int px = p[0], py = p[1];
        if (!in(event.x(), event.y(), px, py, PANEL_W, PANEL_H)) return true;     // your inventory, or outside: as usual
        long now = System.currentTimeMillis();
        autoClicks = 0;
        check = null;
        int shown = view < 0 ? page.mode() : view;
        int tabY = py + 22, tabA = px + PANEL_W / 2 - TAB_W - 2, tabR = px + PANEL_W / 2 + 2;
        int footY = py + PANEL_H - CLASSIC_H - 4, classicW = smallButtonWidth(mc.font, "Classic menu"), classicX = px + PANEL_W - classicW - 5;
        if (in(event.x(), event.y(), classicX, footY, classicW, CLASSIC_H)) {
            classic = true;
            pending = null;
            click(mc);
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
                want(new Target(shown, cell[0], cell[1], button), now);
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
