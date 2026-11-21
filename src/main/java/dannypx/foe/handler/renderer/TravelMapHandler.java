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
import java.util.Map;

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
    /// The first painted map, for the Fantasy view.
    private static final Identifier MAP_FANTASY = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "textures/gui/travel_map_fantasy.png");
    private static final int TEX_W = 2560, TEX_H = 1760;
    /// The map and the panel round it, in the panel's own units (scaled up to fit the window).
    static final int MAP_W = 320, MAP_H = 220, PAD = 6, TITLE_H = 14, FOOT_H = 22;
    static final int PANEL_W = MAP_W + PAD * 2, PANEL_H = TITLE_H + MAP_H + FOOT_H + PAD;

    /// A place on the map: the start of the menu item's name (lower case), the name shown on the map
    /// (the start of the full name, cut short where its plate would run into a neighbour's), and where it sits (0-1).
    record Spot(String key, String label, float x, float y) {}

    static final List<Spot> SPOTS = List.of(
            new Spot("kenai river", "Kenai River", 0.363f, 0.104f), new Spot("lofoten islands", "Lofoten Islands", 0.672f, 0.326f),
            new Spot("great lakes", "Great Lakes", 0.492f, 0.279f), new Spot("cape cod", "Cape Cod", 0.686f, 0.447f),
            new Spot("lake biwa", "Lake Biwa", 0.836f, 0.614f), new Spot("toledo bend", "Toledo Bend", 0.319f, 0.253f),
            new Spot("danube river", "Danube River", 0.603f, 0.558f), new Spot("hawaii", "Hawaii", 0.181f, 0.267f),
            new Spot("cypress lake", "Cypress Lake", 0.380f, 0.427f), new Spot("mediterranean sea", "Mediterranean", 0.681f, 0.719f),
            new Spot("cairns", "Cairns", 0.790f, 0.814f), new Spot("amazon river", "Amazon River", 0.170f, 0.371f),
            new Spot("everglades", "Everglades", 0.246f, 0.520f), new Spot("nile river", "Nile River", 0.446f, 0.665f),
            new Spot("murray river", "Murray River", 0.566f, 0.867f), new Spot("key west", "Key West", 0.063f, 0.449f),
            new Spot("oil rig", "Oil Rig", 0.239f, 0.653f));

    /// Where the places sit on the Fantasy view's painted map.
    static final List<Spot> FANTASY_SPOTS = List.of(
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

    /// The view picked with "Change view" (saved; Realistic until changed).
    private static dannypx.foe.type.MapView view() {
        try {
            return Configs.mainConfig.travelMapView.get();
        } catch (Throwable t) {
            return dannypx.foe.type.MapView.REALISTIC;
        }
    }

    /// The server's plain menu instead of a map.
    private boolean classic() {
        style = view() == dannypx.foe.type.MapView.FANTASY ? FANTASY : REALISTIC;
        return view() == dannypx.foe.type.MapView.CLASSIC;
    }

    private void setView(dannypx.foe.type.MapView v) {
        if (v == view()) return;
        Configs.mainConfig.travelMapView.accept(v);
        Configs.mainConfig.save();
        style = v == dannypx.foe.type.MapView.FANTASY ? FANTASY : REALISTIC;
        // On a place's spots, the new map zooms in on it afresh.
        zoomStartAt = System.currentTimeMillis();
        cached = List.of();
    }
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
        if (id != openFor) { openFor = id; openAt = now; viewMenuOpen = false; }
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
        return !classic() && (zoomingOut(screen) || isLocationsMenu(screen) || isSpotsMenu(screen));
    }
    //endregion

    //region Layout
    private float scale, left, top;
    /// Screen pixels per panel unit: a whole number, so the map is as big as fits and stays crisp
    /// (and looks the same) at every GUI scale.
    private int pixels = 1;

    private void layout(Screen screen) { layout(screen.width, screen.height); }

    private void layout(int width, int height) {
        double gui = Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
        pixels = pixelsPerUnit(width * gui, height * gui);
        scale = (float) (pixels / gui);
        left = (float) (Math.round((width * gui - PANEL_W * pixels) / 2.0) / gui);
        top = (float) (Math.round((height * gui - PANEL_H * pixels) / 2.0) / gui);
    }

    /// Package-private for tests: the biggest whole number of screen pixels per unit that fits.
    static int pixelsPerUnit(double screenW, double screenH) {
        return (int) Math.max(1, Math.floor(Math.min((screenW - 16) / PANEL_W, (screenH - 16) / PANEL_H)));
    }

    /// The mouse in the panel's units.
    private double px(double mouseX) { return (mouseX - left) / scale; }
    private double py(double mouseY) { return (mouseY - top) / scale; }

    static int spotX(Spot s) { return PAD + Math.round(placed(s).x() * MAP_W); }
    static int spotY(Spot s) { return TITLE_H + Math.round(placed(s).y() * MAP_H); }

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
        return plateRect(fontWidth, k, pixels, cx, cy, thickFrame, 0);
    }

    /// The same with `extraW` more screen pixels on the right of the name (room for the place's pet).
    static int[] plateRect(int fontWidth, int k, int pixels, int cx, int cy, boolean thickFrame, int extraW) {
        int w = (fontWidth - 1) * k + PLATE_PAD_X * 2 + extraW; // the last letter's spacing isn't part of the name
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
    static final int BOX = 4;
    private static final float ICON = 0.4375f;
    //endregion

    //region Drawing
    public void render(Screen screen, GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (zoomingOut(screen)) {
            drawZoomOut(g, screen.width, screen.height, mouseX, mouseY);
            return;
        }
        if (!isLocationsMenu(screen)) {
            if (isSpotsMenu(screen)) renderSpots((ContainerScreen) screen, g, mouseX, mouseY);
            return;
        }
        zoomMenu = -1;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (classic()) {
            // The plain menu: a small button to change the view back to a map.
            drawClassicButton(g, font, screen, mouseX, mouseY);
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
        g.blit(RenderPipelines.GUI_TEXTURED, style.tex(), PAD, TITLE_H, 0f, 0f, MAP_W, MAP_H, style.texW(), style.texH(), style.texW(), style.texH());

        double mx = px(mouseX), my = py(mouseY);
        Entry hovered = entryAt(entries, mx, my);
        if (!viewMenuOpen && favouriteStarAt(entries, mx, my) != null) hovered = null;   // the star's own hint instead
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
            // Something on there (a Fabled fish, a frenzy, the place's own event): the square in its colour,
            // with a soft glow round it that breathes.
            // In order (a Fabled fish, a frenzy, the place's event, the mini event): two on at once share the
            // square, the first on its left half and the second on its right.
            List<PlaceEvents.Banner> on = placeEvents(e);
            if (!on.isEmpty()) {
                int c = on.get(0).color() & 0xFFFFFF;
                int c2 = on.size() > 1 ? on.get(1).color() & 0xFFFFFF : c;
                int glow = glowAlpha(System.currentTimeMillis()) << 24;
                g.fill(cx - BOX - 3, cy - BOX - 3, cx, cy + BOX + 3, glow | c);
                g.fill(cx, cy - BOX - 3, cx + BOX + 3, cy + BOX + 3, glow | c2);
                g.fill(cx - BOX - 2, cy - BOX - 2, cx, cy + BOX + 2, 0xFF000000 | (hot ? lighten(c) : c));
                g.fill(cx, cy - BOX - 2, cx + BOX + 2, cy + BOX + 2, 0xFF000000 | (hot ? lighten(c2) : c2));
            } else if (current && !hot) g.fill(cx - BOX - 2, cy - BOX - 2, cx + BOX + 2, cy + BOX + 2, 0xFF7A4A12);
            g.fill(cx - BOX - 1, cy - BOX - 1, cx + BOX + 1, cy + BOX + 1, frame);
            g.fill(cx - BOX, cy - BOX, cx + BOX, cy + BOX, hot ? 0xFF4A3524 : current ? 0xFF3A2812 : 0xFF2B1D12);
            g.pose().pushMatrix();
            g.pose().translate(cx - 8 * ICON, cy - 8 * ICON);
            g.pose().scale(ICON, ICON);
            g.renderItem(e.stack(), 0, 0);
            g.pose().popMatrix();
            if (e.spot() == null) continue;
            // a contest there: a little gold trophy on the square's top left corner (the favourite star has the
            // bottom right)
            if (PlaceEvents.contest(dannypx.foe.helper.FoeLocations.fromName(e.spot().label())) != null) {
                drawTrophy(g, cx - BOX - 1, cy - BOX - 1, PLACE_STAR);
            }
            if (dannypx.foe.handler.logic.TravelFavouritesHandler.instance().get(e.spot().key()) != null) {
                drawStar(g, cx, cy, BOX + 1, PLACE_STAR, true, onStar(cx, cy, BOX + 1, PLACE_STAR, mx, my));
            }
            drawPlate(g, font, e, cx, cy, hot, current);
        }

        // Just after zooming back out, the places fade in: the bare map over them, fading away.
        float fade = fadeIn(System.currentTimeMillis() - fadeFrom);
        if (fade < 1f) {
            g.nextStratum();
            int a = Math.round((1f - fade) * 255f);
            g.blit(RenderPipelines.GUI_TEXTURED, style.tex(), PAD, TITLE_H, 0f, 0f, MAP_W, MAP_H, style.texW(), style.texH(), style.texW(), style.texH(), (a << 24) | 0xFFFFFF);
        }

        // Under the map: what the clicks do, and the way back to the plain menu.
        if (other == 0) {
            g.pose().pushMatrix();
            g.pose().translate(PAD + 2, TITLE_H + MAP_H + 6);
            g.pose().scale(labelScale, labelScale);
            g.drawString(font, Component.literal("Left-click: travel   Right-click: species   ★: your favourite spot").withStyle(ChatFormatting.GRAY), 0, 0, 0xFFAAAAAA, false);
            g.pose().popMatrix();
        }
        drawViewButton(g, font, mx, my);
        g.pose().popMatrix();

        Entry starOver = viewMenuOpen ? null : favouriteStarAt(entries, mx, my);
        if (starOver != null) {
            g.setTooltipForNextFrame(font, Component.literal("\u2605 Go straight to " + dannypx.foe.handler.logic.TravelFavouritesHandler.instance().get(starOver.spot().key())).withStyle(ChatFormatting.GOLD), mouseX, mouseY);
        } else if (hovered != null && !viewMenuOpen) ItemTooltip.draw(g, font, hovered.stack(), mouseX, mouseY, placeEventsAndContest(hovered));
    }

    /// What's on at a map item's place right now (nothing for the items that aren't places).
    private static List<PlaceEvents.Banner> placeEvents(Entry e) {
        if (e == null || e.spot() == null) return List.of();
        return PlaceEvents.live(dannypx.foe.helper.FoeLocations.fromName(e.spot().label()), e.stack().getHoverName().getString());
    }

    /// The same, with a contest there too (for its tooltip; the contest is the trophy on the map, not a colour).
    private static List<PlaceEvents.Banner> placeEventsAndContest(Entry e) {
        List<PlaceEvents.Banner> on = placeEvents(e);
        PlaceEvents.Banner contest = e == null || e.spot() == null ? null : PlaceEvents.contest(dannypx.foe.helper.FoeLocations.fromName(e.spot().label()));
        if (contest == null) return on;
        List<PlaceEvents.Banner> all = new java.util.ArrayList<>(on);
        all.add(contest);
        return all;
    }

    /// The contest trophy (`size` panel units), centred on (cx, cy): drawn pixel by pixel like the favourite
    /// star, gold with a dark edge, so it reads on any ground.
    private static final String[] TROPHY = {
            "#########",
            "#.#####.#",
            ".#######.",
            "..#####..",
            "...###...",
            "....#....",
            "....#....",
            "..#####..",
            "..#####..",
    };

    private void drawTrophy(GuiGraphics g, int cx, int cy, float size) {
        int n = Math.max(9, Math.round(size * pixels));
        int x0 = Math.round(cx * pixels - n / 2f), y0 = Math.round(cy * pixels - n / 2f);
        boolean[][] m = new boolean[n][n];
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) m[y][x] = TROPHY[y * 9 / n].charAt(x * 9 / n) == '#';
        int edge = Math.max(1, Math.round(pixels / 2f));
        g.pose().pushMatrix();
        g.pose().scale(1f / pixels, 1f / pixels);
        for (int y = -edge; y < n + edge; y++) {
            int run = -1;
            for (int x = -edge; x <= n + edge; x++) {
                boolean o = x < n + edge && near(m, x, y, edge);
                if (o && run < 0) run = x;
                if (!o && run >= 0) { g.fill(x0 + run, y0 + y, x0 + x, y0 + y + 1, 0xFF22160A); run = -1; }
            }
        }
        for (int y = 0; y < n; y++) {
            int run = -1, col = 0;
            for (int x = 0; x <= n; x++) {
                boolean in = x < n && m[y][x];
                int c = in ? (x < n / 2 ? 0xFFFFD84A : 0xFFE09A1C) : 0;
                if (run >= 0 && c != col) { g.fill(x0 + run, y0 + y, x0 + x, y0 + y + 1, col); run = -1; }
                if (in && run < 0) { run = x; col = c; }
            }
        }
        g.pose().popMatrix();
    }

    /// Package-private for tests: the glow's opacity, breathing between faint and half over 1.6 seconds.
    static int glowAlpha(long now) {
        double t = (Math.sin(now / 1600.0 * 2 * Math.PI) + 1) / 2;
        return (int) Math.round(0x30 + t * 0x60);
    }

    /// A colour halfway to white, for the hovered square.
    static int lighten(int rgb) {
        int r = (rgb >> 16) & 255, gg = (rgb >> 8) & 255, b = rgb & 255;
        return ((r + 255) / 2 << 16) | ((gg + 255) / 2 << 8) | ((b + 255) / 2);
    }

    /// The place whose favourite star is under the mouse (map units), if any.
    private Entry favouriteStarAt(List<Entry> entries, double mx, double my) {
        for (Entry e : entries) {
            if (e.spot() == null || dannypx.foe.handler.logic.TravelFavouritesHandler.instance().get(e.spot().key()) == null) continue;
            if (onStar(spotX(e.spot()), spotY(e.spot()), BOX + 1, PLACE_STAR, mx, my)) return e;
        }
        return null;
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

    //region A place's spots (the menu after clicking a place): the map zoomed in on it

    /// How far the map zooms in on a place, and how long the move takes.
    static final float ZOOM = 2.6f;
    static final long ZOOM_MS = 450L;
    private Spot zoomSpot;

    /// The part of the map a place takes up, (x, y, w, h) as 0-1 of the picture: where the zoom goes.
    record Inset(float x, float y, float w, float h) {}

    static final Map<String, Inset> INSETS = Map.ofEntries(
            Map.entry("cypress lake", new Inset(0.29100f, 0.33700f, 0.19000f, 0.19000f)),
            Map.entry("kenai river", new Inset(0.27549f, 0.01770f, 0.17595f, 0.17403f)),
            Map.entry("lake biwa", new Inset(0.68550f, 0.46438f, 0.30186f, 0.29856f)),
            Map.entry("murray river", new Inset(0.44983f, 0.74885f, 0.23137f, 0.22885f)),
            Map.entry("everglades", new Inset(0.16196f, 0.43853f, 0.16788f, 0.16605f)),
            Map.entry("key west", new Inset(0.0f, 0.34491f, 0.17164f, 0.21501f)),
            Map.entry("toledo bend", new Inset(0.23567f, 0.16974f, 0.16573f, 0.16392f)),
            Map.entry("great lakes", new Inset(0.37665f, 0.16073f, 0.23083f, 0.22831f)),
            Map.entry("danube river", new Inset(0.50202f, 0.45476f, 0.20231f, 0.20011f)),
            Map.entry("oil rig", new Inset(0.15550f, 0.57003f, 0.16680f, 0.16498f)),
            Map.entry("amazon river", new Inset(0.09685f, 0.29924f, 0.14582f, 0.14423f)),
            Map.entry("mediterranean sea", new Inset(0.576f, 0.63f, 0.17f, 0.17f)),
            Map.entry("cape cod", new Inset(0.61501f, 0.37718f, 0.14259f, 0.14103f)),
            Map.entry("nile river", new Inset(0.32822f, 0.54526f, 0.23621f, 0.23364f)),
            Map.entry("hawaii", new Inset(0.10815f, 0.19301f, 0.14582f, 0.14423f)),
            Map.entry("lofoten islands", new Inset(0.59188f, 0.24752f, 0.15927f, 0.15753f)),
            Map.entry("cairns", new Inset(0.68000f, 0.65700f, 0.23000f, 0.23000f))           // the city and its marina, a little wider
    );

    /// Package-private for tests: how far to zoom so a place's inset fills most of the frame.
    static float zoomFor(Inset in) {
        return in == null ? ZOOM : Math.max(1.5f, Math.min(7f, 1.30f / Math.max(in.w(), in.h())));
    }

    /// Where a place's spots really are, from the player's own screenshots: (x, y) as 0-1 of the place's area
    /// on the map, and how high the ground there is drawn (0-1 of the picture), keyed by a word in the spot's name.
    static final Map<String, Map<String, float[]>> POIS = Map.ofEntries(
            Map.entry("oil rig", Map.of("platform", new float[]{0.4451f, 0.4272f, 0f}, "upper deck", new float[]{0.5146f, 0.3752f, 0f}, "vessel", new float[]{0.4199f, 0.6170f, 0f})),
            Map.entry("murray river", Map.of("red gum", new float[]{0.4282f, 0.6097f, 0f}, "winery", new float[]{0.4313f, 0.4332f, 0f}, "riverwalk", new float[]{0.5353f, 0.4703f, 0f})),
            Map.entry("amazon river", Map.of("vila", new float[]{0.7001f, 0.6328f, 0f}, "porto", new float[]{0.6317f, 0.5407f, 0f}, "camp", new float[]{0.3334f, 0.2998f, 0f})),
            Map.entry("everglades", Map.of("lodge", new float[]{0.8185f, 0.5057f, 0f}, "redfish", new float[]{0.4465f, 0.6086f, 0f}, "visitor", new float[]{0.5924f, 0.2905f, 0f})),
            Map.entry("kenai river", Map.of("bluffs", new float[]{0.2608f, 0.6984f, 0f}, "port", new float[]{0.5364f, 0.4239f, 0f}, "landing", new float[]{0.3956f, 0.5634f, 0f})),
            Map.entry("toledo bend", Map.of("marina", new float[]{0.4066f, 0.5915f, 0f}, "spillway", new float[]{0.6402f, 0.7529f, 0f}, "lodge", new float[]{0.5329f, 0.0665f, 0f})),
            Map.entry("hawaii", Map.of("beach", new float[]{0.6172f, 0.3920f, 0f}, "tiki", new float[]{0.2387f, 0.5784f, 0f}, "lava", new float[]{0.6173f, 0.5882f, 0f})),
            Map.entry("key west", Map.of("hot rod key", new float[]{0.3348f, 0.6487f, 0f}, "305 marina", new float[]{0.3566f, 0.4105f, 0f}, "key west pier", new float[]{0.3177f, 0.5274f, 0f})),
            Map.entry("lofoten islands", Map.of("harbor", new float[]{0.4779f, 0.4465f, 0f}, "coastguard station", new float[]{0.5658f, 0.7552f, 0f}, "skrei inlet", new float[]{0.5603f, 0.6332f, 0f})),
            Map.entry("great lakes", Map.of("heron harbor", new float[]{0.4308f, 0.4203f, 0f}, "superior lighthouse", new float[]{0.6616f, 0.4437f, 0f}, "copperpoint dock", new float[]{0.6229f, 0.5528f, 0f})),
            Map.entry("cypress lake", Map.of("big bear dock", new float[]{0.4759f, 0.4390f, 0f}, "thunderwood marina", new float[]{0.4307f, 0.6612f, 0f}, "spawn", new float[]{0.6073f, 0.3479f, 0f})),
            Map.entry("lake biwa", Map.of("wharf", new float[]{0.4482f, 0.3886f, 0f}, "beach", new float[]{0.5160f, 0.6813f, 0f}, "temple", new float[]{0.7119f, 0.4948f, 0f})),
            Map.entry("danube river", Map.of("main dock", new float[]{0.4520f, 0.5498f, 0f}, "bridge", new float[]{0.4447f, 0.4062f, 0f}, "northside dock", new float[]{0.5689f, 0.4641f, 0f})),
            Map.entry("cape cod", Map.of("boardwalk", new float[]{0.5244f, 0.3894f, 0f}, "beach", new float[]{0.5786f, 0.4641f, 0f}, "island", new float[]{0.5817f, 0.6460f, 0f})),
            Map.entry("cairns", Map.of("tip", new float[]{0.5221f, 0.6965f, 0f}, "beach", new float[]{0.6146f, 0.5903f, 0f}, "vessel", new float[]{0.6197f, 0.8142f, 0f})),
            Map.entry("nile river", Map.of("egyptian ruins", new float[]{0.6248f, 0.5008f, 0f}, "aswan dock", new float[]{0.6728f, 0.6195f, 0f}, "archaeologist", new float[]{0.2394f, 0.4617f, 0f}))
    );

    /// A map to show: its picture and size, where the places sit on it, the part each place takes up
    /// (where the zoom goes), and where their spots are. The Fantasy map has no areas or known spots:
    /// a place's spots are spread round it.
    /// `maxZoom`: how far in its picture stays sharp. `minRx`/`minRy`: the least room (panel units) the spots
    /// of a place are spread over, round the middle of its area.
    record Style(Identifier tex, int texW, int texH, List<Spot> spots, Map<String, Inset> insets, Map<String, Map<String, float[]>> pois,
                 float maxZoom, int minRx, int minRy) {}

    /// Each place's own area on the Fantasy map, (x, y, w, h) as 0-1 of the picture: its spots stay inside it.
    static final Map<String, Inset> FANTASY_INSETS = Map.ofEntries(
            Map.entry("kenai river", new Inset(0.05f, 0.11f, 0.20f, 0.21f)),
            Map.entry("lofoten islands", new Inset(0.43f, 0.01f, 0.14f, 0.15f)),
            Map.entry("great lakes", new Inset(0.25f, 0.14f, 0.18f, 0.20f)),
            Map.entry("cape cod", new Inset(0.53f, 0.14f, 0.14f, 0.16f)),
            Map.entry("lake biwa", new Inset(0.74f, 0.06f, 0.19f, 0.27f)),
            Map.entry("toledo bend", new Inset(0.14f, 0.33f, 0.12f, 0.12f)),
            Map.entry("danube river", new Inset(0.55f, 0.31f, 0.16f, 0.16f)),
            Map.entry("hawaii", new Inset(0.03f, 0.40f, 0.11f, 0.17f)),
            Map.entry("cypress lake", new Inset(0.29f, 0.44f, 0.15f, 0.14f)),
            Map.entry("mediterranean sea", new Inset(0.67f, 0.43f, 0.18f, 0.18f)),
            Map.entry("cairns", new Inset(0.87f, 0.38f, 0.12f, 0.22f)),
            Map.entry("amazon river", new Inset(0.11f, 0.57f, 0.18f, 0.20f)),
            Map.entry("everglades", new Inset(0.27f, 0.64f, 0.16f, 0.18f)),
            Map.entry("nile river", new Inset(0.48f, 0.55f, 0.20f, 0.27f)),
            Map.entry("murray river", new Inset(0.72f, 0.62f, 0.24f, 0.30f)),
            Map.entry("key west", new Inset(0.06f, 0.84f, 0.19f, 0.14f)),
            Map.entry("oil rig", new Inset(0.31f, 0.83f, 0.15f, 0.15f))
    );

    static final Style REALISTIC = new Style(MAP, TEX_W, TEX_H, SPOTS, INSETS, POIS, 7f, 40, 30);
    static final Style FANTASY = new Style(MAP_FANTASY, 1280, 880, FANTASY_SPOTS, FANTASY_INSETS, Map.of(), 3f, 18, 14);

    /// Package-private for tests: the room the spots of a place with area `in` are spread over at zoom z,
    /// {rx, ry} in panel units: well inside the area.
    static int[] spread(Inset in, float z) {
        if (in == null) return new int[]{74, 46};
        return new int[]{Math.max(style.minRx(), Math.round(in.w() * z * MAP_W * 0.30f)),
                Math.max(style.minRy(), Math.round(in.h() * z * MAP_H * (style == REALISTIC ? 0.28f : 0.22f)))};
    }
    /// The map being shown (Realistic in tests).
    static Style style = REALISTIC;

    /// Package-private for tests: a place as it sits on the map being shown.
    static Spot placed(Spot s) {
        if (style.spots() == SPOTS) return s;
        for (Spot f : style.spots()) if (f.key().equals(s.key())) return f;
        return s;
    }

    /// The least distance between two spots' centres (their boxes, the stars on them and a little room).
    static final int SPOT_GAP = 2 * 8 + 14;          // 2 * SPOT_BOX + 14

    /// Package-private for tests: pushes the points apart (in place) until no two are closer than `gap` on both
    /// axes, each moving as little as it can, and keeps them inside the bounds.
    static void separate(List<int[]> pts, int gap, int left, int right, int top, int bottom) {
        int n = pts.size();
        double[][] p = new double[n][2];
        for (int i = 0; i < n; i++) { p[i][0] = pts.get(i)[0]; p[i][1] = pts.get(i)[1]; }
        for (int it = 0; it < 200; it++) {
            boolean moved = false;
            for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
                double dx = p[j][0] - p[i][0], dy = p[j][1] - p[i][1];
                double ox = gap - Math.abs(dx), oy = gap - Math.abs(dy);
                if (ox <= 0.01 || oy <= 0.01) continue;
                // apart along the axis that needs the least moving
                if (ox * 1.0 < oy) {
                    double sx = dx == 0 ? (i % 2 == 0 ? -1 : 1) : Math.signum(dx);
                    p[i][0] -= sx * ox / 2; p[j][0] += sx * ox / 2;
                } else {
                    double sy = dy == 0 ? (i % 2 == 0 ? -1 : 1) : Math.signum(dy);
                    p[i][1] -= sy * oy / 2; p[j][1] += sy * oy / 2;
                }
                moved = true;
            }
            for (double[] q : p) { q[0] = Math.max(left, Math.min(right, q[0])); q[1] = Math.max(top, Math.min(bottom, q[1])); }
            if (!moved) break;
        }
        for (int i = 0; i < n; i++) { pts.get(i)[0] = (int) Math.round(p[i][0]); pts.get(i)[1] = (int) Math.round(p[i][1]); }
    }

    /// Package-private for tests: where a spot's name plate goes, as {x, y, w, h} in screen pixels: above its box,
    /// or below it, or further up or down, whichever first keeps clear of the plates already placed; kept inside
    /// the frame (left, right, top, bottom). `gap` is from the box's centre to the plate.
    static int[] labelSpot(List<int[]> placed, int cx, int cy, int w, int h, int gap, int left, int right, int top, int bottom) {
        int x = Math.max(left, Math.min(right - w, cx - w / 2));
        int[][] tries = {{cy - gap - h}, {cy + gap}, {cy - gap - h * 2 - 4}, {cy + gap + h + 4}, {cy - gap - h * 3 - 8}};
        for (int[] t : tries) {
            int y = Math.max(top, Math.min(bottom - h, t[0]));
            boolean clear = true;
            for (int[] p : placed) if (x < p[0] + p[2] + 3 && p[0] < x + w + 3 && y < p[1] + p[3] + 3 && p[1] < y + h + 3) { clear = false; break; }
            if (clear) return new int[]{x, y, w, h};
        }
        return new int[]{x, Math.max(top, cy - gap - h), w, h};
    }

    /// Package-private for tests: the panel position of a spot named `name` at zoom z, or null when its
    /// real place isn't known (it is then spread with the others).
    static int[] poiAt(Spot place, String name, float z, float[] corner) {
        Map<String, float[]> spots = style.pois().get(place.key());
        Inset in = style.insets().get(place.key());
        if (spots == null || in == null || name == null) return null;
        String n = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, float[]> e : spots.entrySet()) {
            if (!n.contains(e.getKey())) continue;
            float[] f = e.getValue();
            float mx = in.x() + f[0] * in.w(), my = in.y() + f[1] * in.h() - f[2];
            return new int[]{PAD + Math.round((mx - corner[0]) * z * MAP_W), TITLE_H + Math.round((my - corner[1]) * z * MAP_H)};
        }
        return null;
    }

    /// Package-private for tests: how far in the view is, part way (0 to 1) through the zoom on a place.
    /// Drawing and clicking both use this, so a spot is clicked where it is drawn.
    static float zoomAt(Spot place, float progress) {
        return 1f + (Math.min(style.maxZoom(), zoomFor(style.insets().get(place.key()))) - 1f) * progress;
    }

    /// Package-private for tests: the top-left corner (0-1) of the part of the map shown at zoom z.
    static float[] cornerAt(Spot place, float z) {
        float[] c = zoomCentre(place);
        return zoomCorner(c[0], c[1], z);
    }

    /// The centre the zoom goes to: the inset's middle, or the place itself when it has none.
    private static float[] zoomCentre(Spot place) {
        Inset in = style.insets().get(place.key());
        Spot at = placed(place);
        return in == null ? new float[]{at.x(), at.y()} : new float[]{in.x() + in.w() / 2f, in.y() + in.h() / 2f};
    }
    private long zoomChosenAt, zoomStartAt;
    private int zoomMenu = -1;
    /// The very menu screen the place's click opened. Menu ids come round again, so a later menu (the tackle
    /// box, a vault page) can have the same id; only this screen is the spots menu.
    private Screen zoomScreen;

    /// One thing in a menu, as much as telling a spots menu apart needs.
    record Item(String id, String name) {}

    /// Package-private for tests: the slots of the spots to pick from, when these are the items of a
    /// place's spots menu: a "Return" button (gunpowder), and one to eight spots that are all the
    /// same kind of item, with nothing else but the filler (bread) and other buttons (gunpowder).
    /// Null when it is some other menu.
    static List<Integer> spotSlots(List<Item> items) {
        boolean back = false;
        String kind = null;
        List<Integer> spots = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it == null || it.id().isEmpty() || it.id().equals("bread")) continue;
            if (it.id().equals("gunpowder")) {
                if (it.name().trim().equalsIgnoreCase("Return")) back = true;
                continue;
            }
            if (kind == null) kind = it.id();
            else if (!kind.equals(it.id())) return null;
            spots.add(i);
        }
        return back && !spots.isEmpty() && spots.size() <= 8 ? spots : null;
    }

    /// Package-private for tests: the slot of the menu's "Return" button, or -1.
    static int returnSlot(List<Item> items) {
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it != null && it.id().equals("gunpowder") && it.name().trim().equalsIgnoreCase("Return")) return i;
        }
        return -1;
    }

    private static List<Item> items(ChestMenu menu) {
        int size = menu.getRowCount() * 9;
        List<Item> out = new ArrayList<>(size);
        for (int i = 0; i < size && i < menu.slots.size(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            out.add(stack.isEmpty() ? new Item("", "") : new Item(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getHoverName().getString()));
        }
        return out;
    }

    /// True for the menu a place's click leads to (or that menu while the server still fills it).
    private boolean isSpotsMenu(Screen screen) {
        if (zoomSpot == null || !(screen instanceof ContainerScreen chest)) return false;
        if (!Configs.mainConfig.enableMod.get() || !Configs.mainConfig.travelMap.get()) return false;
        if (!dannypx.foe.handler.logic.ConnectionHandler.instance().isOnServer()) return false;
        long now = System.currentTimeMillis();
        ChestMenu menu = chest.getMenu();
        int id = menu.containerId;
        if (id == zoomMenu && screen == zoomScreen) return menuIsEmpty(menu) || spotSlots(items(menu)) != null;
        if (now - zoomChosenAt > 8000L) return false;        // not the menu that click led to
        boolean spots = spotSlots(items(menu)) != null;
        // The menu opens a moment before it is filled: the zoom starts over the empty menu already.
        boolean filling = menuIsEmpty(menu) && now - zoomChosenAt < 2000L;
        if (!spots && !filling) return false;
        zoomMenu = id;
        zoomScreen = screen;
        zoomChosenAt = 0L;          // one click, one spots menu: the next menu isn't taken for it
        zoomStartAt = now;
        return true;
    }

    /// Package-private for tests: how far the zoom has got (0 to 1), easing out.
    static float zoomProgress(long sinceStartMs) {
        float t = Math.max(0f, Math.min(1f, sinceStartMs / (float) ZOOM_MS));
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    /// Package-private for tests: the part of the map shown at zoom `z` around a place, as {x0, y0}
    /// (0-1, the top left corner; the part is 1/z wide and high), kept inside the map.
    static float[] zoomCorner(float spotX, float spotY, float z) {
        float w = 1f / z;
        return new float[]{Math.max(0f, Math.min(1f - w, spotX - w / 2f)), Math.max(0f, Math.min(1f - w, spotY - w / 2f))};
    }

    /// Package-private for tests: where the i-th of n spots sits, in panel units, around the place at
    /// (cx, cy): spread on an oval, the first one straight above, all kept inside the map.
    static int[] spotAt(int i, int n, int cx, int cy) {
        return spotAt(i, n, cx, cy, 74, 46);
    }

    static int[] spotAt(int i, int n, int cx, int cy, int rx0, int ry0) {
        double a = -Math.PI / 2 + i * 2 * Math.PI / Math.max(1, n);
        int rx = n == 1 ? 0 : rx0, ry = n == 1 ? 0 : ry0;
        int x = (int) Math.round(cx + Math.cos(a) * rx), y = (int) Math.round(cy + Math.sin(a) * ry) + (n == 1 ? 0 : 8);
        x = Math.max(PAD + 34, Math.min(PAD + MAP_W - 34, x));
        y = Math.max(TITLE_H + 26, Math.min(TITLE_H + MAP_H - 12, y));
        return new int[]{x, y};
    }

    static final int SPOT_BOX = 8, BACK2_W = 34;
    private static int back2X() { return classicX() - BACK2_W - 5; }

    record SpotView(int slot, ItemStack stack, int x, int y) {}

    //region Favourite spots: a star on each spot picks it; a place with one gets a star that goes straight there

    /// The star, 7 x 7, row by row.
    /// How big the star is: on a spot of the zoomed map, and (smaller, to suit the little markers) on a place.
    static final float SPOT_STAR = 7f, PLACE_STAR = 5f;

    /// The name on a plate fitted to it, above the place and kept inside the map. Laid out in whole screen pixels,
    /// so the plate hugs the letters the same way at every size.
    private void drawPlate(GuiGraphics g, Font font, Entry e, int cx, int cy, boolean hot, boolean current) {
        String label = e.spot().label();
        int frame = hot ? 0xFFFFF4D6 : current ? 0xFFF2B33D : 0xFF120C08;
        int k = Math.max(2, Math.round(pixels * 0.45f));          // screen pixels per font pixel
        String pet = petOf(e.spot());
        int pet_ = pet == null ? 0 : petSize(plateRect(0, k, pixels, cx, cy, false)[3]);
        int gap = pet == null ? 0 : petGap(k);
        int[] r = plateRect(font.width(label), k, pixels, cx, cy, current && !hot, gap + pet_);
        g.pose().pushMatrix();
        g.pose().scale(1f / pixels, 1f / pixels);
        int edge = current && !hot ? 2 : 1;
        g.fill(r[0] - edge, r[1] - edge, r[0] + r[2] + edge, r[1] + r[3] + edge, frame);
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xF01A120C);
        if (pet != null) {
            // the pet on the right, level with the name, the same room round it as the letters have
            int px = r[0] + r[2] - PLATE_PAD_X - pet_, py = r[1] + (r[3] - pet_ + 1) / 2;
            // a thin "|" between the name and the pet, as tall as the letters, in the name's colour but soft
            int lx = px - DIVIDER_ROOM * k - dividerW(k), ly = r[1] + PLATE_PAD_Y;
            g.fill(lx, ly, lx + dividerW(k), ly + 7 * k, 0x80F2B36A);
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(dannypx.foe.FishOnMCExtras.MOD_ID, "textures/gui/pets/" + pet + "_" + pet_ + ".png"),
                    px, py, 0, 0, pet_, pet_, pet_, pet_, pet_, pet_);
        }
        g.pose().translate(r[0] + PLATE_PAD_X, r[1] + PLATE_PAD_Y);
        g.pose().scale(k, k);
        g.drawString(font, colouredLabel(e.stack().getHoverName(), label), 0, 0, 0xFFFFFFFF, false);
        g.pose().popMatrix();
    }

    /// The pet pictures' sizes (each a square this many pixels, the pet filling it), drawn pixel for pixel so
    /// they're sharp at every map size.
    static final int[] PET_SIZES = {12, 14, 16, 18, 20, 22, 24, 26, 28, 32, 36, 40, 48};

    /// Package-private for tests: the pet's size on a name plate `plateH` screen pixels tall: the largest picture
    /// that leaves a pixel above and below it (so a little taller than the letters).
    static int petSize(int plateH) {
        int best = PET_SIZES[0];
        for (int s : PET_SIZES) if (s <= plateH - 2) best = s;
        return best;
    }

    /// The room either side of the dividing line, in letter pixels: more than a space (the most the crowded labels
    /// round Toledo Bend allow), so it reads
    /// "Kenai River  |  bear".
    static final int DIVIDER_ROOM = 5;

    /// Package-private for tests: the room between the name and its pet: the room, the dividing line, the room.
    static int petGap(int k) {
        return 2 * DIVIDER_ROOM * k + dividerW(k);
    }

    /// Package-private for tests: how thick the dividing line is: a thin line, half a letter's stroke.
    static int dividerW(int k) {
        return Math.max(1, k / 2);
    }

    /// The place's own pet's picture name, when Main settings → "Show each place's pet on the travel map" is on.
    static final boolean PETS_OFF = true;

    private static String petOf(Spot spot) {
        // Off for now: the map shows the places as they were before the pets (the labels just the name).
        if (PETS_OFF) return null;
        if (spot == null || !Configs.mainConfig.travelMapPets.get()) return null;
        dannypx.foe.helper.FoeLocations.Location loc = dannypx.foe.helper.FoeLocations.fromName(spot.label());
        return loc == null || loc.petId == null || loc.petId.isEmpty() ? null : loc.petId;
    }

    /// Package-private for tests: the top left corner of a star `size` across, centred on the bottom right
    /// corner of a box centred at (cx, cy), `half` from its centre to its edge.
    static float[] starAt(int cx, int cy, int half, float size) {
        return new float[]{cx + half - size / 2f, cy + half - size / 2f};
    }

    /// The star's area, a pixel wider all round so it's easy to hit.
    static boolean onStar(int cx, int cy, int half, float size, double x, double y) {
        return Math.abs(x - (cx + half)) <= size / 2f + 1f && Math.abs(y - (cy + half)) <= size / 2f + 1f;
    }

    private static final Map<Integer, boolean[][]> STAR_MASKS = new java.util.HashMap<>();
    static { dannypx.foe.handler.logic.MemoryGuard.register("mapStars", STAR_MASKS::size, STAR_MASKS::clear); }

    /// Package-private for tests: a five-pointed star, point up, `n` screen pixels across: which pixels it covers.
    /// Drawn in whole screen pixels at any size, so its points stay sharp.
    static boolean[][] starMask(int n) {
        return STAR_MASKS.computeIfAbsent(n, k -> {
            boolean[][] m = new boolean[k][k];
            // as big as fits: a star is 1.90 R across and 1.81 R high; chunky points (inner radius 0.44 R) so
            // they hold at small sizes
            double R = k / 1.94, r = R * 0.44, c = k / 2.0, cyc = k / 2.0 + (R - R * 0.809) / 2.0;
            double[] px = new double[10], py = new double[10];
            for (int i = 0; i < 10; i++) {
                double a = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 == 0 ? R : r;
                px[i] = c + Math.cos(a) * rr; py[i] = cyc + Math.sin(a) * rr;
            }
            // each pixel: filled when most of it is inside (sampled 4 x 4)
            for (int y = 0; y < k; y++) for (int x = 0; x < k; x++) {
                int hits = 0;
                for (int sy = 0; sy < 4; sy++) for (int sx = 0; sx < 4; sx++) {
                    double tx = x + (sx + 0.5) / 4, ty = y + (sy + 0.5) / 4;
                    boolean in = false;
                    for (int i = 0, j = 9; i < 10; j = i++)
                        if ((py[i] > ty) != (py[j] > ty) && tx < (px[j] - px[i]) * (ty - py[i]) / (py[j] - py[i]) + px[i]) in = !in;
                    if (in) hits++;
                }
                m[y][x] = hits >= 6;
            }
            return m;
        });
    }

    /// A star: gold and outlined when `on`; otherwise a muted one (brighter under the mouse). Lit from the top left.
    private void drawStar(GuiGraphics g, int cx, int cy, int half, float size, boolean on, boolean hot) {
        int n = Math.max(5, Math.round(size * pixels));
        int x0 = Math.round((cx + half) * pixels - n / 2f), y0 = Math.round((cy + half) * pixels - n / 2f);
        boolean[][] m = starMask(n);
        int light = on ? (hot ? 0xFFFFF6C0 : 0xFFFFD84A) : hot ? 0xFFF6EDD6 : 0xFFA89A80;
        int dark = on ? (hot ? 0xFFF2C64A : 0xFFE09A1C) : hot ? 0xFFD8CCB0 : 0xFF7E715A;
        int edge = Math.max(1, Math.round(pixels / 2f));
        g.pose().pushMatrix();
        g.pose().scale(1f / pixels, 1f / pixels);
        // the outline: the star grown by `edge` all round, dark so it reads on any ground
        for (int y = -edge; y < n + edge; y++) {
            int run = -1;
            for (int x = -edge; x <= n + edge; x++) {
                boolean o = x < n + edge && near(m, x, y, edge);
                if (o && run < 0) run = x;
                if (!o && run >= 0) { g.fill(x0 + run, y0 + y, x0 + x, y0 + y + 1, 0xFF22160A); run = -1; }
            }
        }
        // the face: light on the upper left, a shade deeper on the lower right
        for (int y = 0; y < n; y++) {
            int run = -1, col = 0;
            for (int x = 0; x <= n; x++) {
                boolean in = x < n && m[y][x];
                int c = in ? ((x + y < n - 1) ? light : dark) : 0;
                if (run >= 0 && c != col) { g.fill(x0 + run, y0 + y, x0 + x, y0 + y + 1, col); run = -1; }
                if (in && run < 0) { run = x; col = c; }
            }
        }
        g.pose().popMatrix();
    }

    private static boolean near(boolean[][] m, int x, int y, int d) {
        int n = m.length;
        for (int dy = -d; dy <= d; dy++) for (int dx = -d; dx <= d; dx++) {
            int xx = x + dx, yy = y + dy;
            if (xx >= 0 && yy >= 0 && xx < n && yy < n && m[yy][xx] && Math.abs(dx) + Math.abs(dy) <= d + (d > 1 ? 1 : 0)) return true;
        }
        return false;
    }

    /// The spot to pick by itself once the place's spots are up (the star on the whole map), and since when.
    private String autoSpot;
    private long autoSince;
    /// Its spot has been clicked: waiting for the teleport.
    private boolean autoClicked;

    private static String spotName(ItemStack stack) {
        return net.minecraft.ChatFormatting.stripFormatting(stack.getHoverName().getString());
    }
    //endregion

    private List<SpotView> spotViews(ChestMenu menu, float z, float[] corner) {
        List<Integer> slots = spotSlots(items(menu));
        List<SpotView> out = new ArrayList<>();
        if (slots == null) return out;
        float[] c = zoomCentre(zoomSpot);
        int cx = PAD + Math.round((c[0] - corner[0]) * z * MAP_W), cy = TITLE_H + Math.round((c[1] - corner[1]) * z * MAP_H);
        int[] r = spread(style.insets().get(zoomSpot.key()), z);
        int rx = r[0], ry = r[1];
        List<int[]> ats = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = menu.getSlot(slots.get(i)).getItem();
            int[] at = poiAt(zoomSpot, stack.getHoverName().getString(), z, corner);
            if (at == null) at = spotAt(i, slots.size(), cx, cy, rx, ry);
            ats.add(new int[]{at[0], at[1]});
        }
        // spots close together on the map are nudged apart, so their boxes and stars never overlap
        separate(ats, SPOT_GAP, PAD + SPOT_BOX + 2, PAD + MAP_W - SPOT_BOX - 6, TITLE_H + SPOT_BOX + 14, TITLE_H + MAP_H - SPOT_BOX - 6);
        for (int i = 0; i < slots.size(); i++)
            out.add(new SpotView(slots.get(i), menu.getSlot(slots.get(i)).getItem(), ats.get(i)[0], ats.get(i)[1]));
        return out;
    }

    private void renderSpots(ContainerScreen screen, GuiGraphics g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (classic()) {
            drawClassicButton(g, font, screen, mouseX, mouseY);
            return;
        }
        if (autoSpot != null) {
            // Going straight to the favourite: no zoom, it's picked as soon as the place's spots are there,
            // and the map stays as it was until the teleport.
            float z1 = zoomAt(zoomSpot, 1f);
            if (!autoClicked) autoPick(screen, spotViews(screen.getMenu(), z1, cornerAt(zoomSpot, z1)));
            if (autoClicked && System.currentTimeMillis() - autoSince > 6000L) { autoSpot = null; autoClicked = false; }
            if (autoSpot != null) {
                zoomStartAt = System.currentTimeMillis();           // if it falls through, the zoom starts from the top
                float z0 = zoomAt(zoomSpot, 0f);
                drawZoom(g, screen.width, screen.height, zoomSpot, z0, cornerAt(zoomSpot, z0), List.of(), mouseX, mouseY, false);
                return;
            }
        }
        float progress = zoomProgress(System.currentTimeMillis() - zoomStartAt);
        float z = zoomAt(zoomSpot, progress);
        float[] corner = cornerAt(zoomSpot, z);
        // The spots come up once the map has nearly arrived.
        List<SpotView> views = progress > 0.85f ? spotViews(screen.getMenu(), z, corner) : List.of();
        drawZoom(g, screen.width, screen.height, zoomSpot, z, corner, views, mouseX, mouseY, false);
    }

    /// The star on the whole map was used: once the place's spots are up, its favourite is picked. If it isn't
    /// among them (renamed, gone), the spots stay up to pick from by hand.
    private void autoPick(ContainerScreen screen, List<SpotView> views) {
        Minecraft mc = Minecraft.getInstance();
        if (System.currentTimeMillis() - autoSince > 6000L) { autoSpot = null; return; }
        if (views.isEmpty() || mc.gameMode == null || mc.player == null) return;
        for (SpotView v : views) {
            if (dannypx.foe.handler.logic.TravelFavouritesHandler.clean(spotName(v.stack())).equals(dannypx.foe.handler.logic.TravelFavouritesHandler.clean(autoSpot))) {
                String name = spotName(v.stack());
                mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, v.slot(), 0, ClickType.PICKUP, mc.player);
                dannypx.foe.handler.logic.MapScanHandler.instance().noteSpot(name);
                dannypx.foe.handler.logic.SpotHandler.instance().noteSpot(name);
                autoClicked = true;
                return;
            }
        }
        if (spotSlots(items(screen.getMenu())) != null) autoSpot = null;   // the spots are there, but not that one
    }

    /// The zoomed map itself, with the spots to pick from.
    void drawZoom(GuiGraphics g, Screen screen, Spot place, float z, float[] corner, float progress, List<SpotView> views, int mouseX, int mouseY) {
        drawZoom(g, screen.width, screen.height, place, z, corner, views, mouseX, mouseY, false);
    }

    /// `out`: on the way back out to the whole map (its title, its line under the map, no Back button).
    void drawZoom(GuiGraphics g, int width, int height, Spot place, float z, float[] corner, List<SpotView> views, int mouseX, int mouseY, boolean out) {
        Font font = Minecraft.getInstance().font;
        layout(width, height);

        g.nextStratum();
        g.fill(0, 0, width, height, 0xC0000000);
        g.pose().pushMatrix();
        g.pose().translate(left, top);
        g.pose().scale(scale, scale);
        g.fill(-2, -2, PANEL_W + 2, PANEL_H + 2, 0xFF120C08);
        g.fill(-1, -1, PANEL_W + 1, PANEL_H + 1, 0xFF5C4129);
        g.fill(0, 0, PANEL_W, PANEL_H, 0xFF2B1D12);
        g.fill(0, 0, PANEL_W, 1, 0xFF7A583A);
        g.fill(0, TITLE_H - 2, PANEL_W, TITLE_H - 1, 0xFF1A110A);
        g.drawCenteredString(font, Component.literal(out ? "TRAVEL MAP" : place.label().toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.BOLD), PANEL_W / 2, 3, 0xFFF2E3C2);
        g.fill(PAD - 1, TITLE_H - 1, PAD + MAP_W + 1, TITLE_H + MAP_H + 1, 0xFF120C08);
        // The part of the picture around the place, stretched over the whole frame.
        g.blit(RenderPipelines.GUI_TEXTURED, style.tex(), PAD, TITLE_H, corner[0] * style.texW(), corner[1] * style.texH(), MAP_W, MAP_H,
                Math.round(style.texW() / z), Math.round(style.texH() / z), style.texW(), style.texH());

        double mx = px(mouseX), my = py(mouseY);
        SpotView hovered = null, starHovered = null;
        for (SpotView v : views) {
            if (!out && onStar(v.x(), v.y(), SPOT_BOX, SPOT_STAR, mx, my)) starHovered = v;
            else if (Math.abs(mx - v.x()) <= SPOT_BOX + 1 && Math.abs(my - v.y()) <= SPOT_BOX + 1) hovered = v;
        }
        if (starHovered != null) hovered = null;
        int k = Math.max(2, Math.round(pixels * 0.45f));
        List<int[]> plates = new ArrayList<>(), blocked = new ArrayList<>();
        for (SpotView v : views) blocked.add(new int[]{(v.x() - SPOT_BOX - 1) * pixels, (v.y() - SPOT_BOX - 1) * pixels, (SPOT_BOX * 2 + 6) * pixels, (SPOT_BOX * 2 + 6) * pixels});
        for (SpotView v : views) {
            Component nm = v.stack().getHoverName();
            int w = (font.width(nm) - 1) * k + PLATE_PAD_X * 2, h = 7 * k + PLATE_PAD_Y * 2;
            List<int[]> avoid = new ArrayList<>(plates); avoid.addAll(blocked);
            plates.add(labelSpot(avoid, v.x() * pixels, v.y() * pixels, w, h, (SPOT_BOX + 1) * pixels + 3,
                    (PAD + 1) * pixels, (PAD + MAP_W - 1) * pixels, (TITLE_H + 1) * pixels, (TITLE_H + MAP_H - 1) * pixels));
        }
        int vi = 0;
        for (SpotView v : views) {
            int[] plate = plates.get(vi++);
            boolean hot = v == hovered;
            int frame = hot ? 0xFFFFF4D6 : 0xFFF2B33D;
            g.fill(v.x() - SPOT_BOX - 1, v.y() - SPOT_BOX - 1, v.x() + SPOT_BOX + 1, v.y() + SPOT_BOX + 1, frame);
            g.fill(v.x() - SPOT_BOX, v.y() - SPOT_BOX, v.x() + SPOT_BOX, v.y() + SPOT_BOX, hot ? 0xFF4A3524 : 0xFF2B1D12);
            g.renderItem(v.stack(), v.x() - 8, v.y() - 8);
            // Its star: gold on the place's favourite spot.
            g.nextStratum();
            drawStar(g, v.x(), v.y(), SPOT_BOX, SPOT_STAR, dannypx.foe.handler.logic.TravelFavouritesHandler.instance().isFavourite(place.key(), spotName(v.stack())),
                    !out && onStar(v.x(), v.y(), SPOT_BOX, SPOT_STAR, mx, my));
            // Its name on a fitted plate above it, in whole screen pixels.
            Component name = v.stack().getHoverName();
            int x = plate[0], y = plate[1], w = plate[2], h = plate[3];
            g.pose().pushMatrix();
            g.pose().scale(1f / pixels, 1f / pixels);
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, frame);
            g.fill(x, y, x + w, y + h, 0xF01A120C);
            g.pose().translate(x + PLATE_PAD_X, y + PLATE_PAD_Y);
            g.pose().scale(k, k);
            g.drawString(font, name, 0, 0, 0xFFFFFFFF, false);
            g.pose().popMatrix();
        }

        float labelScale = k / (float) pixels;
        g.pose().pushMatrix();
        g.pose().translate(PAD + 2, TITLE_H + MAP_H + 6);
        g.pose().scale(labelScale, labelScale);
        String foot = out ? "Left-click: teleport   Right-click: view species"
                : autoSpot != null ? "Going to your favourite: " + autoSpot
                : "Pick a spot to teleport to   ★ on a spot: make it your favourite";
        g.drawString(font, Component.literal(foot).withStyle(ChatFormatting.GRAY), 0, 0, 0xFFAAAAAA, false);
        g.pose().popMatrix();
        if (!out) {
            boolean overBack = mx >= back2X() && mx < back2X() + BACK2_W && my >= classicY() && my < classicY() + CLASSIC_H;
            button(g, font, back2X(), classicY(), BACK2_W, CLASSIC_H, "Back", overBack);
        }
        drawViewButton(g, font, mx, my);
        g.pose().popMatrix();

        if (hovered != null && !viewMenuOpen) ItemTooltip.draw(g, font, hovered.stack(), mouseX, mouseY);
        if (starHovered != null && !viewMenuOpen) {
            boolean fav = dannypx.foe.handler.logic.TravelFavouritesHandler.instance().isFavourite(place.key(), spotName(starHovered.stack()));
            g.setTooltipForNextFrame(font, Component.literal(fav ? "\u2605 Your favourite here: click to remove" : "\u2606 Make this your favourite spot here").withStyle(ChatFormatting.GOLD), mouseX, mouseY);
        }
    }

    /// False when the click was the zoomed map's.
    private boolean clickSpots(ContainerScreen screen, MouseButtonEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (classic()) return classicClick(screen, event, mc);
        layout(screen);
        double mx = px(event.x()), my = py(event.y());
        if (viewClick(mx, my, mc)) return false;
        if (mc.gameMode == null || mc.player == null) return false;
        ChestMenu menu = screen.getMenu();
        if (mx >= back2X() && mx < back2X() + BACK2_W && my >= classicY() && my < classicY() + CLASSIC_H) {
            int slot = returnSlot(items(menu));
            if (slot >= 0) {
                mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, mc.player);
                // The map zooms back out to the whole map at once, over this menu and the one the server opens next.
                outSpot = zoomSpot;
                outAt = System.currentTimeMillis();
                outMenu = menu.containerId;
                zoomSpot = null;
                zoomMenu = -1;
            }
            click(mc);
            return false;
        }
        float progress = zoomProgress(System.currentTimeMillis() - zoomStartAt);
        if (progress <= 0.85f) return false;
        float z = zoomAt(zoomSpot, progress);
        int button = event.button();
        List<SpotView> spotList = spotViews(menu, z, cornerAt(zoomSpot, z));
        if (button == 0) for (SpotView v : spotList) {
            if (onStar(v.x(), v.y(), SPOT_BOX, SPOT_STAR, mx, my)) {
                // The star: this spot becomes the place's favourite (or stops being it). No teleport.
                dannypx.foe.handler.logic.TravelFavouritesHandler.instance().toggle(zoomSpot.key(), spotName(v.stack()));
                click(mc);
                return false;
            }
        }
        for (SpotView v : spotList) {
            if (Math.abs(mx - v.x()) <= SPOT_BOX + 1 && Math.abs(my - v.y()) <= SPOT_BOX + 1 && (button == 0 || button == 1)) {
                // Test builds: the map scan notes where this spot lands you (its name read before the click, which
                // can empty the slot).
                String spotName = net.minecraft.ChatFormatting.stripFormatting(v.stack().getHoverName().getString());
                mc.gameMode.handleInventoryMouseClick(menu.containerId, v.slot(), button, ClickType.PICKUP, mc.player);
                dannypx.foe.handler.logic.MapScanHandler.instance().noteSpot(spotName);
                dannypx.foe.handler.logic.SpotHandler.instance().noteSpot(spotName);
                click(mc);
                break;
            }
        }
        return false;
    }
    //endregion

    //region Going back: the map zooms out to the whole map again

    /// How long the zoom back out takes, and the places' fade-in after it.
    static final long OUT_MS = 480L, FADE_MS = 220L;
    private Spot outSpot;
    private long outAt, fadeFrom;
    private int outMenu = -1;

    /// Package-private for tests: how far in the view still is (1 = on the place, 0 = the whole map),
    /// `sinceMs` after Back was clicked. Eases in and out, so it leaves and lands gently.
    static float zoomOutLeft(long sinceMs) {
        float t = Math.max(0f, Math.min(1f, sinceMs / (float) OUT_MS));
        float e = t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
        return 1f - e;
    }

    /// Package-private for tests: how far the places have faded in (0 to 1), `sinceMs` after the zoom out ended.
    static float fadeIn(long sinceMs) {
        return Math.max(0f, Math.min(1f, sinceMs / (float) FADE_MS));
    }

    /// Package-private for tests: whether the zoom back out is over and the map can take over: it has had its
    /// time, and the Locations menu is up and filled.
    static boolean zoomOutDone(long sinceMs, boolean locationsFilled) {
        return sinceMs >= OUT_MS && locationsFilled;
    }

    /// True while Back's zoom out is drawn over this screen: the spots menu it was clicked on (until the server
    /// swaps it), then the Locations menu, empty or filled, until the zoom has landed.
    private boolean zoomingOut(Screen screen) {
        if (outSpot == null) return false;
        long since = System.currentTimeMillis() - outAt;
        boolean plain = classic();
        if (plain || since > 3000L || !Configs.mainConfig.enableMod.get() || !Configs.mainConfig.travelMap.get()
                || !(screen instanceof ContainerScreen chest)) {
            if (plain || since > 3000L || screen != null) outSpot = null;
            return false;
        }
        if (chest.getMenu().containerId == outMenu) return true;
        if (!isLocationsMenu(screen)) { outSpot = null; return false; }    // the server opened something else
        if (zoomOutDone(since, !entries(chest).isEmpty())) {
            outSpot = null;
            fadeFrom = System.currentTimeMillis();
            return false;
        }
        return true;
    }

    private void drawZoomOut(GuiGraphics g, int width, int height, int mouseX, int mouseY) {
        float left = zoomOutLeft(System.currentTimeMillis() - outAt);
        float z = zoomAt(outSpot, left);
        drawZoom(g, width, height, outSpot, z, cornerAt(outSpot, z), List.of(), mouseX, mouseY, true);
    }

    /// Should the server close the menu for a moment between the two, the zoom goes on over the game.
    public void renderHud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (outSpot == null || mc.screen != null) return;
        if (System.currentTimeMillis() - outAt > 1500L) { outSpot = null; return; }
        drawZoomOut(g, g.guiWidth(), g.guiHeight(), -1, -1);
    }
    //endregion

    //region Change view: a button with a small menu of the three views

    static final dannypx.foe.type.MapView[] VIEWS = {dannypx.foe.type.MapView.CLASSIC, dannypx.foe.type.MapView.FANTASY, dannypx.foe.type.MapView.REALISTIC};
    private static final int OPTION_H = 12;
    private boolean viewMenuOpen = false;

    /// Package-private for tests: which option of the menu (0-2) is at (x, y), or -1. The menu hangs from a
    /// button at (bx, by), w by h: above it (`up`, on the map) or below it (on the plain menu).
    static int viewOption(int bx, int by, int w, int h, boolean up, double x, double y) {
        if (x < bx || x >= bx + w) return -1;
        for (int i = 0; i < VIEWS.length; i++) {
            int oy = optionY(by, h, up, i);
            if (y >= oy && y < oy + OPTION_H) return i;
        }
        return -1;
    }

    private static int optionY(int by, int h, boolean up, int i) {
        return up ? by - 2 - (VIEWS.length - i) * OPTION_H : by + h + 2 + i * OPTION_H;
    }

    private void drawViewMenu(GuiGraphics g, Font font, int bx, int by, int w, int h, boolean up, double mx, double my) {
        g.nextStratum();
        int top = optionY(by, h, up, 0), bottom = optionY(by, h, up, VIEWS.length - 1) + OPTION_H;
        g.fill(bx - 1, top - 1, bx + w + 1, bottom + 1, 0xFF120C08);
        int hot = viewOption(bx, by, w, h, up, mx, my);
        dannypx.foe.type.MapView now = view();
        for (int i = 0; i < VIEWS.length; i++) {
            int oy = optionY(by, h, up, i);
            boolean chosen = VIEWS[i] == now;
            g.fill(bx, oy, bx + w, oy + OPTION_H, i == hot ? 0xFF5C4129 : chosen ? 0xFF3F2B14 : 0xFF2B1D12);
            if (chosen) g.fill(bx, oy, bx + 2, oy + OPTION_H, 0xFFF2B33D);
            g.pose().pushMatrix();
            float s = 0.75f;
            String text = VIEWS[i].displayName();
            g.pose().translate(bx + (w - font.width(text) * s) / 2f, oy + (OPTION_H - 8 * s) / 2f + 0.5f);
            g.pose().scale(s, s);
            g.drawString(font, text, 0, 0, chosen ? 0xFFFFD873 : i == hot ? 0xFFFFE9A0 : 0xFFE8D8B8, false);
            g.pose().popMatrix();
        }
    }

    /// The "Change view" button under the map (panel units), with its menu above it when open.
    private void drawViewButton(GuiGraphics g, Font font, double mx, double my) {
        boolean over = mx >= classicX() && mx < classicX() + CLASSIC_W && my >= classicY() && my < classicY() + CLASSIC_H;
        button(g, font, classicX(), classicY(), CLASSIC_W, CLASSIC_H, "Change view", over || viewMenuOpen);
        if (viewMenuOpen) drawViewMenu(g, font, classicX(), classicY(), CLASSIC_W, CLASSIC_H, true, mx, my);
    }

    /// The "Change view" button over the server's plain menu (screen pixels), its menu below it.
    private void drawClassicButton(GuiGraphics g, Font font, Screen screen, int mouseX, int mouseY) {
        int[] b = backButton(screen);
        boolean hover = mouseX >= b[0] && mouseX < b[0] + BACK_W && mouseY >= b[1] && mouseY < b[1] + BACK_H;
        g.nextStratum();
        button(g, font, b[0], b[1], BACK_W, BACK_H, "Change view", hover || viewMenuOpen);
        if (viewMenuOpen) drawViewMenu(g, font, b[0], b[1], BACK_W, BACK_H, false, mouseX, mouseY);
    }

    /// A click on the map's panel (panel units): true when it was the view button's or its menu's.
    /// While the menu is open, any click closes it (picking the view under it, if any).
    private boolean viewClick(double mx, double my, Minecraft mc) {
        if (viewMenuOpen) {
            int pick = viewOption(classicX(), classicY(), CLASSIC_W, CLASSIC_H, true, mx, my);
            viewMenuOpen = false;
            if (pick >= 0) setView(VIEWS[pick]);
            click(mc);
            return true;
        }
        if (mx >= classicX() && mx < classicX() + CLASSIC_W && my >= classicY() && my < classicY() + CLASSIC_H) {
            viewMenuOpen = true;
            click(mc);
            return true;
        }
        return false;
    }

    /// A click on the server's plain menu: false when it was the view button's or its menu's.
    private boolean classicClick(Screen screen, MouseButtonEvent event, Minecraft mc) {
        int[] b = backButton(screen);
        if (viewMenuOpen) {
            int pick = viewOption(b[0], b[1], BACK_W, BACK_H, false, event.x(), event.y());
            viewMenuOpen = false;
            if (pick >= 0) setView(VIEWS[pick]);
            click(mc);
            return false;
        }
        if (event.x() >= b[0] && event.x() < b[0] + BACK_W && event.y() >= b[1] && event.y() < b[1] + BACK_H) {
            viewMenuOpen = true;
            click(mc);
            return false;
        }
        return true;
    }
    //endregion

    //region Clicks

    /// False when the click was the map's (the menu under it must not get it).
    public boolean allowClick(Screen screen, MouseButtonEvent event) {
        if (zoomingOut(screen)) return false;                  // a moment: the map is on its way back out
        if (!isLocationsMenu(screen)) return !isSpotsMenu(screen) || clickSpots((ContainerScreen) screen, event);
        Minecraft mc = Minecraft.getInstance();
        if (classic()) return classicClick(screen, event, mc);
        layout(screen);
        double mx = px(event.x()), my = py(event.y());
        if (viewClick(mx, my, mc)) return false;
        ContainerScreen chest = (ContainerScreen) screen;
        int button = event.button();
        if (button == 0 && mc.gameMode != null && mc.player != null) {
            Entry e = favouriteStarAt(entries(chest), mx, my);
            if (e != null) {
                // Straight to the favourite: into the place as usual, and its spot picked once it's up.
                mc.gameMode.handleInventoryMouseClick(chest.getMenu().containerId, e.slot(), 0, ClickType.PICKUP, mc.player);
                zoomSpot = e.spot(); zoomChosenAt = System.currentTimeMillis(); zoomMenu = -1;
                autoSpot = dannypx.foe.handler.logic.TravelFavouritesHandler.instance().get(e.spot().key());
                autoSince = System.currentTimeMillis(); autoClicked = false;
                click(mc);
                return false;
            }
        }
        Entry hit = entryAt(entries(chest), mx, my);
        if (hit != null && (button == 0 || button == 1) && mc.gameMode != null && mc.player != null) {
            // The very click the menu would make on that item.
            mc.gameMode.handleInventoryMouseClick(chest.getMenu().containerId, hit.slot(), button, ClickType.PICKUP, mc.player);
            // A left click on a place leads to its list of spots: the map zooms in on it for that.
            if (button == 0 && hit.spot() != null) { zoomSpot = hit.spot(); zoomChosenAt = System.currentTimeMillis(); zoomMenu = -1; autoSpot = null; autoClicked = false; }
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
