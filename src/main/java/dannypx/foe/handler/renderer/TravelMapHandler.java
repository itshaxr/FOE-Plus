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

    /// A place on the map: the start of the menu item's name (lower case), and where it sits (0-1).
    record Spot(String key, String label, float x, float y) {}

    static final List<Spot> SPOTS = List.of(
            new Spot("kenai river", "Kenai River", 0.12f, 0.20f), new Spot("lofoten islands", "Lofoten Islands", 0.50f, 0.10f),
            new Spot("great lakes", "Great Lakes", 0.335f, 0.24f), new Spot("cape cod", "Cape Cod", 0.60f, 0.215f),
            new Spot("lake biwa", "Lake Biwa", 0.835f, 0.24f), new Spot("toledo bend", "Toledo Bend", 0.19f, 0.395f),
            new Spot("danube river", "Danube River", 0.62f, 0.42f), new Spot("hawaii", "Hawaii", 0.10f, 0.555f),
            new Spot("cypress lake", "Cypress Lake", 0.365f, 0.52f), new Spot("mediterranean sea", "Mediterranean Sea", 0.795f, 0.51f),
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
        if (menu.containerId == cachedFor && now - cachedAt < 200L) return cached;
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
        return entries(chest).size() >= 3;
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

    private static final int BACK_W = 54, BACK_H = 13;
    /// Half the size of a place's picture box; the picture is drawn at three quarters size.
    private static final int BOX = 7;
    private static final float ICON = 0.75f;
    //endregion

    //region Drawing
    public void render(Screen screen, GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!isLocationsMenu(screen)) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (classic) {
            // The plain menu: a small button to come back to the map.
            boolean hover = mouseX >= 4 && mouseX < 4 + BACK_W && mouseY >= 4 && mouseY < 4 + BACK_H;
            button(g, font, 4, 4, BACK_W, BACK_H, "Map view", hover);
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
        float labelScale = Math.max(2, Math.round(pixels * 0.5f)) / (float) pixels;

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
            int frame = hot ? 0xFFFFD34A : current ? 0xFF55FF55 : 0xFF120C08;
            g.fill(cx - BOX - 1, cy - BOX - 1, cx + BOX + 1, cy + BOX + 1, frame);
            g.fill(cx - BOX, cy - BOX, cx + BOX, cy + BOX, hot ? 0xFF4A3524 : 0xFF2B1D12);
            g.pose().pushMatrix();
            g.pose().translate(cx - 8 * ICON, cy - 8 * ICON);
            g.pose().scale(ICON, ICON);
            g.renderItem(e.stack(), 0, 0);
            g.pose().popMatrix();
            if (e.spot() == null) continue;
            String label = e.spot().label();
            // The name on a small plate above the picture, kept inside the map.
            int textW = Math.round(font.width(label) * labelScale);
            int plateW = textW + 4, plateH = Math.round(8 * labelScale) + 3;
            int plateX = Math.max(PAD + 1, Math.min(PAD + MAP_W - 1 - plateW, cx - plateW / 2));
            int plateY = cy - BOX - plateH - 2;
            if (plateY < TITLE_H + 1) plateY = cy + BOX + 2; // no room above: under it
            g.fill(plateX - 1, plateY - 1, plateX + plateW + 1, plateY + plateH + 1, frame);
            g.fill(plateX, plateY, plateX + plateW, plateY + plateH, 0xF01A120C);
            g.pose().pushMatrix();
            g.pose().translate(plateX + 2, plateY + 2);
            g.pose().scale(labelScale, labelScale);
            g.drawString(font, label, 0, 0, hot ? 0xFFFFE9A0 : current ? 0xFF9CFF9C : 0xFFFFFFFF, false);
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

        if (hovered != null) g.setTooltipForNextFrame(font, hovered.stack(), mouseX, mouseY);
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
            if (event.x() >= 4 && event.x() < 4 + BACK_W && event.y() >= 4 && event.y() < 4 + BACK_H) {
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
