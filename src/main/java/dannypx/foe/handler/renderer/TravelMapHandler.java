package dannypx.foe.handler.renderer;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.BossEventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * FOE+'s travel map: the server's Locations menu (/tt) shown as a world map. Each place on the
 * map is the menu's own item for it, so hovering shows the server's tooltip and clicking is the
 * same click on that item (left: teleport, right: view species). Places the map doesn't know are
 * listed under it, and the plain menu is one click away.
 */
public final class TravelMapHandler {
    private static TravelMapHandler INSTANCE;

    public static TravelMapHandler instance() {
        if (INSTANCE == null) INSTANCE = new TravelMapHandler();
        return INSTANCE;
    }

    private static final Identifier MAP = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "textures/gui/travel_map.png");
    private static final int TEX_W = 1280, TEX_H = 880;
    /// The map and the panel round it, in the panel's own units (scaled up to fit the window).
    static final int MAP_W = 320, MAP_H = 220, PAD = 6, TITLE_H = 14, FOOT_H = 22;
    static final int PANEL_W = MAP_W + PAD * 2, PANEL_H = TITLE_H + MAP_H + FOOT_H + PAD;

    /// A place on the map: the start of the menu item's name (lower case), the name shown on the map
    /// (the start of the full name, cut short where its plate would run into a neighbour's), and where it sits (0-1).
    record Spot(String key, String label, float x, float y) {}

    static final List<Spot> SPOTS = List.of(
            new Spot("kenai river", "Kenai River", 0.12f, 0.20f), new Spot("lofoten islands", "Lofoten Islands", 0.50f, 0.10f),
            new Spot("great lakes", "Great Lakes", 0.335f, 0.24f), new Spot("cape cod", "Cape Cod", 0.60f, 0.215f),
            new Spot("lake biwa", "Lake Biwa", 0.835f, 0.24f), new Spot("toledo bend", "Toledo Bend", 0.19f, 0.395f),
            new Spot("danube river", "Danube River", 0.62f, 0.42f), new Spot("hawaii", "Hawaii", 0.10f, 0.555f),
            new Spot("cypress lake", "Cypress Lake", 0.365f, 0.52f), new Spot("mediterranean sea", "Mediterranean", 0.795f, 0.51f),
            new Spot("cairns", "Cairns", 0.935f, 0.50f), new Spot("amazon river", "Amazon River", 0.17f, 0.68f),
            new Spot("everglades", "Everglades", 0.335f, 0.76f), new Spot("nile river", "Nile River", 0.585f, 0.70f),
            new Spot("murray river", "Murray River", 0.82f, 0.79f), new Spot("key west", "Key West", 0.15f, 0.915f),
            new Spot("oil rig", "Oil Rig", 0.425f, 0.905f));

    /// One of the menu's location items: its slot, the item, its name, and its place (null = not on the map).
    record Entry(int slot, ItemStack stack, String name, Spot spot) {}

    /// You asked for the server's plain menu instead (until you switch back).
    private boolean classic = false;
    private List<Entry> cached = List.of();
    private int cachedFor = -1;
    private long cachedAt = 0L;

    private TravelMapHandler() {}

    //region What the open menu is

    /// Package-private for tests: the place for a menu item's name ("Amazon River *FRENZY!*").
    static Spot spotFor(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT).trim();
        for (Spot s : SPOTS) if (n.startsWith(s.key())) return s;
        return null;
    }

    /// Package-private for tests: the name without what the server adds after it (" *FRENZY!*").
    static String cleanName(String name) {
        String n = name == null ? "" : name.trim();
        int star = n.indexOf('*');
        if (star > 0) n = n.substring(0, star).trim();
        return n;
    }

    /// Package-private for tests: the map's label for a place, in the colours the server gives that
    /// place's name in its own menu (colour only: no bold, no special font). Plain white when the
    /// item's name doesn't start with the label.
    static Component colouredLabel(Component itemName, String label) {
        if (itemName == null) return Component.literal(label);
        String raw = itemName.getString();
        int lead = 0;
        while (lead < raw.length() && Character.isWhitespace(raw.charAt(lead))) lead++;
        if (!raw.regionMatches(true, lead, label, 0, label.length())) return Component.literal(label);
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        int[] at = {0};
        final int from = lead, to = lead + label.length();
        itemName.visit((style, text) -> {
            int start = at[0], end = start + text.length();
            at[0] = end;
            int a = Math.max(start, from), b = Math.min(end, to);
            if (a < b) {
                net.minecraft.network.chat.TextColor colour = style.getColor();
                net.minecraft.network.chat.MutableComponent part = Component.literal(label.substring(a - from, b - from));
                out.append(colour == null ? part : part.withStyle(st -> st.withColor(colour)));
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }

    /// A location item says its climate in its tooltip ("Climate: Subarctic").
    private static boolean isLocationItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;
        for (Component line : lore.lines()) {
            if (line.getString().contains("Climate:")) return true;
        }
        return false;
    }

    private List<Entry> entries(ContainerScreen screen) {
        ChestMenu menu = screen.getMenu();
        long now = System.currentTimeMillis();
        // Only a found list is kept for a moment: an empty one is looked at again at once, so the map
        // comes up the very frame the server's items arrive.
        if (menu.containerId == cachedFor && now - cachedAt < 200L && !cached.isEmpty()) return cached;
        List<Entry> out = new ArrayList<>();
        int size = menu.getRowCount() * 9;
        for (int i = 0; i < size && i < menu.slots.size(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!isLocationItem(stack)) continue;
            String name = stack.getHoverName().getString();
            out.add(new Entry(i, stack, cleanName(name), spotFor(name)));
        }
        cached = out;
        cachedFor = menu.containerId;
        cachedAt = now;
        return out;
    }

    private boolean isLocationsMenu(Screen screen) {
        if (!(screen instanceof ContainerScreen chest)) return false;
        if (!Configs.mainConfig.enableMod.get() || !Configs.mainConfig.travelMap.get()) return false;
        if (!dannypx.foe.handler.logic.ConnectionHandler.instance().isOnServer()) return false;
        long now = System.currentTimeMillis();
        int id = chest.getMenu().containerId;
        if (id != openFor) { openFor = id; openAt = now; }
        String title = chest.getTitle().getString();
        if (isLocationList(entries(chest).stream().map(Entry::name).toList())) {
            locationsTitle = title;
            return true;
        }
        return coverWhileLoading(title, locationsTitle, menuIsEmpty(chest.getMenu()), now - openAt);
    }

    /// The Locations menu's title (the server draws it with its own font; from a real /foe menu dump).
    /// Kept up to date from the menu itself, should the server ever change it.
    static final String LOCATIONS_TITLE = "\uEEE4\uD501";
    private String locationsTitle = LOCATIONS_TITLE;
    private int openFor = -1;
    private long openAt;

    private static boolean menuIsEmpty(ChestMenu menu) {
        int size = menu.getRowCount() * 9;
        for (int i = 0; i < size && i < menu.slots.size(); i++) if (!menu.getSlot(i).getItem().isEmpty()) return false;
        return true;
    }

    /// Package-private for tests: a menu opens a moment before the server fills it. A menu with the
    /// Locations title that is still empty is covered by the map already (for a second at most), so
    /// the plain menu never flashes up first.
    static boolean coverWhileLoading(String title, String knownTitle, boolean empty, long sinceOpenMs) {
        return knownTitle != null && knownTitle.equals(title) && empty && sinceOpenMs >= 0 && sinceOpenMs < 1000L;
    }

    /// Package-private for tests: true when these item names are the Locations menu's. Other menus
    /// have items with a climate line too (armor in the Presets menu), so the names must be places
    /// the map knows: at least five different ones, and most of the list.
    static boolean isLocationList(List<String> names) {
        java.util.Set<String> places = new java.util.HashSet<>();
        for (String n : names) {
            Spot s = spotFor(n);
            if (s != null) places.add(s.key());
        }
        return places.size() >= 5 && places.size() * 2 >= names.size();
    }

    /// True while the map is drawn over this screen (the menu under it is then out of reach).
    public boolean covers(Screen screen) {
        return !classic && isLocationsMenu(screen);
    }
    //endregion

    //region Layout
    private float scale, left, top;
    /// Screen pixels per panel unit: a whole number, so the map is as big as fits and stays crisp
    /// (and looks the same) at every GUI scale.
    private int pixels = 1;

    private void layout(Screen screen) {
        double gui = Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
        pixels = pixelsPerUnit(screen.width * gui, screen.height * gui);
        scale = (float) (pixels / gui);
        left = (float) (Math.round((screen.width * gui - PANEL_W * pixels) / 2.0) / gui);
        top = (float) (Math.round((screen.height * gui - PANEL_H * pixels) / 2.0) / gui);
    }

    /// Package-private for tests: the biggest whole number of screen pixels per unit that fits.
    static int pixelsPerUnit(double screenW, double screenH) {
        return (int) Math.max(1, Math.floor(Math.min((screenW - 16) / PANEL_W, (screenH - 16) / PANEL_H)));
    }

    /// The mouse in the panel's units.
    private double px(double mouseX) { return (mouseX - left) / scale; }
    private double py(double mouseY) { return (mouseY - top) / scale; }

    private static int spotX(Spot s) { return PAD + Math.round(s.x() * MAP_W); }
    private static int spotY(Spot s) { return TITLE_H + Math.round(s.y() * MAP_H); }

    private static final int CLASSIC_W = 62, CLASSIC_H = 13;
    private static int classicX() { return PANEL_W - PAD - CLASSIC_W; }
    private static int classicY() { return TITLE_H + MAP_H + 5; }

    /// Where the i-th place that isn't on the map sits (a row under the map).
    private static int otherX(int i) { return PAD + 2 + i * 18; }
    private static int otherY() { return TITLE_H + MAP_H + 3; }

    private Entry entryAt(List<Entry> entries, double x, double y) {
        int other = 0;
        Entry hit = null;
        for (Entry e : entries) {
            int cx, cy;
            if (e.spot() != null) {
                cx = spotX(e.spot());
                cy = spotY(e.spot());
            } else {
                cx = otherX(other++) + 8;
                cy = otherY() + 8;
            }
            if (Math.abs(x - cx) <= BOX + 1 && Math.abs(y - cy) <= BOX + 1) hit = e;
        }
        return hit;
    }

    private static final int BACK_W = 84, BACK_H = 16;

    private static final int PLATE_PAD_X = 3, PLATE_PAD_Y = 2;

    /// Package-private for tests: a name plate as {x, y, w, h} in screen pixels from the panel's corner.
    /// `fontWidth` is the name's width in font pixels, `k` the screen pixels per font pixel, `pixels`
    /// the screen pixels per panel unit, (cx, cy) the place in panel units.
    static int[] plateRect(int fontWidth, int k, int pixels, int cx, int cy, boolean thickFrame) {
        int w = (fontWidth - 1) * k + PLATE_PAD_X * 2;       // the last letter's spacing isn't part of the name
        int h = 8 * k + PLATE_PAD_Y * 2 - k;                 // the empty row under the letters isn't needed
        int edge = thickFrame ? 2 : 1;
        int minX = (PAD + 1) * pixels, maxX = (PAD + MAP_W - 1) * pixels - w;
        int x = Math.max(minX, Math.min(maxX, cx * pixels - w / 2));
        int y = (cy - BOX - 1) * pixels - 2 - edge - h;      // 2 pixels clear of the picture's frame
        if (y < (TITLE_H + 1) * pixels) y = (cy + BOX + 1) * pixels + 2 + edge; // no room above: under it
        return new int[]{x, y, w, h};
    }

    /// Package-private for tests: where the "Travel map" button sits on the plain menu, as {x, y}:
    /// centred just above the chest (rows of 9), or at the top of the window when there's no room.
    static int[] backButton(int screenW, int screenH, int rows) {
        int menuTop = (screenH - (114 + rows * 18)) / 2;
        return new int[]{(screenW - BACK_W) / 2, Math.max(2, menuTop - BACK_H - 6)};
    }

    private static int[] backButton(Screen screen) {
        return backButton(screen.width, screen.height, ((ContainerScreen) screen).getMenu().getRowCount());
    }
    /// Half the size of a place's picture box; the picture is drawn at 9 of its 16 pixels.
    private static final int BOX = 5;
    private static final float ICON = 0.5625f;
    //endregion

    //region Drawing
    public void render(Screen screen, GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!isLocationsMenu(screen)) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (classic) {
            // The plain menu: a small button to come back to the map.
            int[] b = backButton(screen);
            boolean hover = mouseX >= b[0] && mouseX < b[0] + BACK_W && mouseY >= b[1] && mouseY < b[1] + BACK_H;
            g.nextStratum();
            button(g, font, b[0], b[1], BACK_W, BACK_H, "Back to travel map", hover);
            return;
        }
        drawMap(g, screen, entries((ContainerScreen) screen), mouseX, mouseY);
    }

    /// The map itself, for a list of the menu's location items.
    void drawMap(GuiGraphics g, Screen screen, List<Entry> entries, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        layout(screen);
        g.nextStratum();
        g.fill(0, 0, screen.width, screen.height, 0xC0000000);
        g.pose().pushMatrix();
        g.pose().translate(left, top);
        g.pose().scale(scale, scale);

        // The panel: a dark wooden board with the title on it and the map set into it.
        g.fill(-2, -2, PANEL_W + 2, PANEL_H + 2, 0xFF120C08);
        g.fill(-1, -1, PANEL_W + 1, PANEL_H + 1, 0xFF5C4129);
        g.fill(0, 0, PANEL_W, PANEL_H, 0xFF2B1D12);
        g.fill(0, 0, PANEL_W, 1, 0xFF7A583A);
        g.fill(0, TITLE_H - 2, PANEL_W, TITLE_H - 1, 0xFF1A110A);
        g.drawCenteredString(font, Component.literal("TRAVEL MAP").withStyle(ChatFormatting.BOLD), PANEL_W / 2, 3, 0xFFF2E3C2);
        g.fill(PAD - 1, TITLE_H - 1, PAD + MAP_W + 1, TITLE_H + MAP_H + 1, 0xFF120C08);
        g.blit(RenderPipelines.GUI_TEXTURED, MAP, PAD, TITLE_H, 0f, 0f, MAP_W, MAP_H, TEX_W, TEX_H, TEX_W, TEX_H);

        double mx = px(mouseX), my = py(mouseY);
        Entry hovered = entryAt(entries, mx, my);
        String here = BossEventHandler.instance().getLocation().getString().toLowerCase(Locale.ROOT);
        // Labels at a size that lands on whole screen pixels at every GUI scale.
        float labelScale = Math.max(2, Math.round(pixels * 0.45f)) / (float) pixels;

        int other = 0;
        for (Entry e : entries) {
            boolean hot = e == hovered;
            boolean current = !here.isBlank() && e.spot() != null && here.contains(e.spot().key());
            int cx, cy;
            if (e.spot() != null) {
                cx = spotX(e.spot());
                cy = spotY(e.spot());
            } else {
                cx = otherX(other++) + 8;
                cy = otherY() + 8;
            }
            // Hovered: a light frame. Where you are: a warm gold one, a little thicker.
            int frame = hot ? 0xFFFFF4D6 : current ? 0xFFF2B33D : 0xFF120C08;
            if (current && !hot) g.fill(cx - BOX - 2, cy - BOX - 2, cx + BOX + 2, cy + BOX + 2, 0xFF7A4A12);
            g.fill(cx - BOX - 1, cy - BOX - 1, cx + BOX + 1, cy + BOX + 1, frame);
            g.fill(cx - BOX, cy - BOX, cx + BOX, cy + BOX, hot ? 0xFF4A3524 : current ? 0xFF3A2812 : 0xFF2B1D12);
            g.pose().pushMatrix();
            g.pose().translate(cx - 8 * ICON, cy - 8 * ICON);
            g.pose().scale(ICON, ICON);
            g.renderItem(e.stack(), 0, 0);
            g.pose().popMatrix();
            if (e.spot() == null) continue;
            String label = e.spot().label();
            // The name on a plate fitted to it, above the picture and kept inside the map. Laid out in
            // whole screen pixels, so the plate hugs the letters the same way at every size.
            int k = Math.max(2, Math.round(pixels * 0.45f));          // screen pixels per font pixel
            int[] r = plateRect(font.width(label), k, pixels, cx, cy, current && !hot);
            g.pose().pushMatrix();
            g.pose().scale(1f / pixels, 1f / pixels);
            int edge = current && !hot ? 2 : 1;
            g.fill(r[0] - edge, r[1] - edge, r[0] + r[2] + edge, r[1] + r[3] + edge, frame);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xF01A120C);
            g.pose().translate(r[0] + PLATE_PAD_X, r[1] + PLATE_PAD_Y);
            g.pose().scale(k, k);
            g.drawString(font, colouredLabel(e.stack().getHoverName(), label), 0, 0, 0xFFFFFFFF, false);
            g.pose().popMatrix();
        }

        // Under the map: what the clicks do, and the way back to the plain menu.
        if (other == 0) {
            g.pose().pushMatrix();
            g.pose().translate(PAD + 2, TITLE_H + MAP_H + 6);
            g.pose().scale(labelScale, labelScale);
            g.drawString(font, Component.literal("Left-click: teleport   Right-click: view species").withStyle(ChatFormatting.GRAY), 0, 0, 0xFFAAAAAA, false);
            g.pose().popMatrix();
        }
        boolean overClassic = mx >= classicX() && mx < classicX() + CLASSIC_W && my >= classicY() && my < classicY() + CLASSIC_H;
        button(g, font, classicX(), classicY(), CLASSIC_W, CLASSIC_H, "Classic menu", overClassic);
        g.pose().popMatrix();

        if (hovered != null) ItemTooltip.draw(g, font, hovered.stack(), mouseX, mouseY);
    }

    private static void button(GuiGraphics g, Font font, int x, int y, int w, int h, String text, boolean hover) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, hover ? 0xFFFFD34A : 0xFF120C08);
        g.fill(x, y, x + w, y + h, hover ? 0xFF5C4129 : 0xFF3A281A);
        g.pose().pushMatrix();
        float s = 0.75f;
        g.pose().translate(x + (w - font.width(text) * s) / 2f, y + (h - 8 * s) / 2f + 0.5f);
        g.pose().scale(s, s);
        g.drawString(font, text, 0, 0, hover ? 0xFFFFE9A0 : 0xFFE8D8B8, false);
        g.pose().popMatrix();
    }
    //endregion

    //region Clicks

    /// False when the click was the map's (the menu under it must not get it).
    public boolean allowClick(Screen screen, MouseButtonEvent event) {
        if (!isLocationsMenu(screen)) return true;
        Minecraft mc = Minecraft.getInstance();
        if (classic) {
            int[] b = backButton(screen);
            if (event.x() >= b[0] && event.x() < b[0] + BACK_W && event.y() >= b[1] && event.y() < b[1] + BACK_H) {
                classic = false;
                click(mc);
                return false;
            }
            return true;
        }
        layout(screen);
        double mx = px(event.x()), my = py(event.y());
        if (mx >= classicX() && mx < classicX() + CLASSIC_W && my >= classicY() && my < classicY() + CLASSIC_H) {
            classic = true;
            click(mc);
            return false;
        }
        ContainerScreen chest = (ContainerScreen) screen;
        Entry hit = entryAt(entries(chest), mx, my);
        int button = event.button();
        if (hit != null && (button == 0 || button == 1) && mc.gameMode != null && mc.player != null) {
            // The very click the menu would make on that item.
            mc.gameMode.handleInventoryMouseClick(chest.getMenu().containerId, hit.slot(), button, ClickType.PICKUP, mc.player);
            click(mc);
        }
        return false;
    }

    /// Releases and drags are the map's too while it's up.
    public boolean allowOther(Screen screen) {
        return !covers(screen);
    }

    private static void click(Minecraft mc) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
    //endregion
}
