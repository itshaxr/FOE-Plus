package dannypx.foe.screens;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.helper.KeyBindHelper;
import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The FOE+ menu (opened with O): a window of its own over a deep-sea backdrop. Down the left, the logo, a welcome
 * and the sections (Home, Settings, HUD, Customize, Stats, Tools); on the right, the chosen section's tiles, each
 * with an icon, a name and a line saying what it does. The section last open is the one that opens next time.
 */
public class FoeMainScreen extends Screen {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "textures/gui/sprites/title/icon.png");
    private static final Identifier BACKDROP = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "textures/gui/menu_backdrop.png");
    private static final Identifier PANEL = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "textures/gui/menu_panel.png");
    // Where "FOE+" sits inside the 500x500 logo image.
    private static final int LOGO_U = 30, LOGO_V = 184, LOGO_W = 440, LOGO_H = 132, LOGO_TEX = 500;

    // Colours: deep slate, with the logo's blue for what is chosen.
    static final int EDGE = 0xFF050B12, RIM = 0xFF2B4E6E, RIM_LIGHT = 0xFF3E6C94, SIDEBAR = 0xC0081320, RULE = 0xFF1E3550,
            ACCENT = 0xFF3FA9F5, ACCENT_DIM = 0xFF2A6E9E, TILE = 0xFF14263A, TILE_EDGE = 0xFF22405E, TILE_HOT = 0xFF1B3450,
            TITLE = 0xFFEAF4FF, TEXT = 0xFFC9D7E6, DIM = 0xFF7F95AB, OFF = 0xFF55687B;

    static final int SIDEBAR_W = 118, TAB_H = 22, GAP = 6, FOOTER_H = 28, PAD = 12, HEAD_H = 38;

    /// The sections, in order down the sidebar.
    public enum Section {
        HOME("Home", Items.OAK_BOAT, "Pick a section to get started"),
        SETTINGS("Settings", Items.COMMAND_BLOCK, "Every option, and the ones you change most"),
        HUD("HUD", Items.ITEM_FRAME, "Where your panels sit, and your own HUDs"),
        CUSTOMIZE("Customize", Items.MAGENTA_DYE, "How you and your items look"),
        STATS("Stats", Items.CLOCK, "Dry streaks, all-time stats and imports"),
        SOCIAL("Social", Items.PLAYER_HEAD, "Your crew and friends: who's on and where"),
        TOOLS("Tools", Items.CHEST, "Auto tip, prices and the full tools menu");

        final String name; final Item icon; final String blurb;
        Section(String name, Item icon, String blurb) { this.name = name; this.icon = icon; this.blurb = blurb; }
    }

    /// The section shown, kept while the game runs.
    public static Section section = Section.HOME;

    private final Screen parent;
    private int wx, wy, ww, wh;

    public FoeMainScreen(Screen parent) {
        super(Component.literal("FOE+"));
        this.parent = parent;
    }

    /// Package-private for tests: the window {x, y, w, h}: as big as suits the screen, within limits, centred.
    static int[] windowAt(int screenW, int screenH) {
        int w = Math.max(320, Math.min(500, screenW - 24)), h = Math.max(220, Math.min(300, screenH - 24));
        w = Math.min(w, screenW - 4); h = Math.min(h, screenH - 4);
        return new int[]{(screenW - w) / 2, (screenH - h) / 2, w, h};
    }

    @Override
    protected void init() {
        int[] win = windowAt(width, height);
        wx = win[0]; wy = win[1]; ww = win[2]; wh = win[3];

        // The sections, under the logo and welcome.
        int tabY = wy + sidebarTop();
        int tabH = tabH(wh - sidebarTop() - 18, Section.values().length);
        for (Section s : Section.values()) {
            final Section to = s;
            addRenderableWidget(new Tab(wx + 4, tabY, SIDEBAR_W - 8, tabH, s, b -> { section = to; rebuildWidgets(); }));
            tabY += tabH + 2;
        }

        // The section's tiles, two to a row, three rows filling the space between the heading and the footer.
        int cx = wx + SIDEBAR_W + PAD, cw = ww - SIDEBAR_W - PAD * 2;
        int tw = (cw - GAP) / 2;
        int ty = wy + HEAD_H + 8;
        int TILE_H = Math.max(40, Math.min(56, (wy + wh - FOOTER_H - 8 - ty - 2 * GAP) / 3));
        List<TileSpec> tiles = tiles(section);
        for (int i = 0; i < tiles.size(); i++) {
            TileSpec t = tiles.get(i);
            int x = cx + (i % 2) * (tw + GAP), y = ty + (i / 2) * (TILE_H + GAP);
            Tile tile = new Tile(x, y, tw, TILE_H, t);
            tile.active = t.enabled;
            addRenderableWidget(tile);
        }

        // Done, bottom right of the window.
        addRenderableWidget(new FlatButton(wx + ww - PAD - 64, wy + wh - FOOTER_H + 5, 64, 18, Component.literal("Done"), b -> onClose()));
    }

    /// Package-private for tests: each tab's height, so all of them fit in `room` (above the version line).
    static int tabH(int room, int tabs) {
        return Math.max(16, Math.min(TAB_H, room / Math.max(1, tabs) - 2));
    }

    private int sidebarTop() {
        int logoH = logoH();
        return 10 + (logoH > 0 ? logoH + 6 : 0) + 30;
    }

    private int logoH() {
        int h = (SIDEBAR_W - 24) * LOGO_H / LOGO_W;
        return wh >= 240 ? h : 0;
    }

    //region What each section holds
    record TileSpec(String title, Supplier<Component> label, Item icon, String desc, boolean enabled, Button.OnPress press) {}

    private static TileSpec tile(String title, Item icon, String desc, Button.OnPress press) {
        return new TileSpec(title, null, icon, desc, true, press);
    }

    private List<TileSpec> tiles(Section s) {
        List<TileSpec> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        switch (s) {
            case HOME -> {
                // a way into each section: every tool lives in just one of them
                for (Section to : Section.values()) {
                    if (to == Section.HOME) continue;
                    out.add(tile(to.name, to.icon, to.blurb, b -> { section = to; rebuildWidgets(); }));
                }
            }
            case SETTINGS -> {
                out.add(tile("All Settings", Items.COMMAND_BLOCK, "Every FOE+ option, by category", b -> mc.setScreen(new FoeSettingsScreen(this))));
                out.add(new TileSpec("Inspect", FoeMainScreen::inspectLabel, Items.SPYGLASS,
                        "When pet %, fish and armor details show", true,
                        b -> dannypx.foe.handler.logic.KeyBindHandler.cycleInspectAlways()));
                out.add(tile("General", Items.REPEATER, "Presets, travel map and chat", b -> openConfig("main_config")));
                out.add(tile("Items & Visuals", Items.GLOW_ITEM_FRAME, "Markers, tooltips and how things look", b -> openConfig("renderer_config")));
                out.add(tile("Keybinds", Items.TRIPWIRE_HOOK, "Inspect, menus, presets and quests", b -> openConfig("keybinding_config")));
            }
            case HUD -> {
                out.add(tile("Move HUD Elements", Items.ITEM_FRAME, "Drag, anchor and resize your panels", b -> mc.setScreen(new MoveElementScreen(this))));
                out.add(tile("Custom HUDs", Items.OAK_SIGN, "Make your own HUD text and icons", b -> mc.setScreen(new CustomHudMakerScreen(this))));
                out.add(tile("HUD Settings", Items.COMPARATOR, "Which panels show, and what's on them", b -> openConfig("hud_config")));
                out.add(tile("HUD Editor", Items.PAINTING, "Spacing, colours, pictures and lines of each HUD", b -> mc.setScreen(new HudEditorScreen(this))));
                out.add(new TileSpec("Catch Ring", FoeMainScreen::catchRingLabel, Items.LIME_DYE, "A ring that fills as you reel a fish in: click to turn on or off (move and size it in Move HUD Elements)", true,
                        b -> {
                            Configs.hudConfig.catchRingOn.accept(!Configs.hudConfig.catchRingOn.get());
                            Configs.hudConfig.save();
                        }));
                out.add(tile("Top Bar", Items.SPYGLASS, "Turn each part of the top bar on or off: name, level, location, spot, weather, time, balance, credits, catches...",
                        b -> openConfig("hud_config.topBarGroup")));
            }
            case CUSTOMIZE -> {
                out.add(new TileSpec("+ Colour", this::plusLabel, Items.MAGENTA_DYE, "The + after your name", true,
                        b -> mc.setScreen(new PlusColorScreen(this))));
                out.add(new TileSpec("Bobber", FoeMainScreen::bobberLabel, Items.FISHING_ROD, "Your 3D bobber's colours", true,
                        b -> mc.setScreen(new BobberScreen(this))));
                out.add(new TileSpec("Item Icons", FoeMainScreen::iconStyleLabel, Items.AMETHYST_SHARD,
                        "FOE+'s markers on items, or FOE's", true,
                        b -> {
                            Configs.rendererConfig.iconStyle.accept(Configs.rendererConfig.iconStyle.get().next());
                            Configs.rendererConfig.save();
                        }));
                out.add(new TileSpec("HUD Style", FoeMainScreen::hudStyleLabel, Items.PAINTING,
                        "FOE+'s panels and top bar, or FOE-R's look with profile, pet and location boxes", true,
                        b -> {
                            Configs.hudConfig.hudStyle.accept(Configs.hudConfig.hudStyle.get().next());
                            Configs.hudConfig.save();
                            // the FOE-R boxes take the top corners: the panels there move down to make room
                            if (dannypx.foe.screens.element.hud.FoeRBoxes.on()) dannypx.foe.screens.element.hud.FoeRBoxes.makeRoom();
                        }));
            }
            case STATS -> {
                out.add(tile("Import Dry Streaks", Items.CLOCK, "From your newest FOE or FOE-R install", b -> dannypx.foe.handler.io.DryStreakImporter.importNow()));
                out.add(tile("Edit Dry Streaks", Items.NAME_TAG, "Set your dry streaks by hand", b -> mc.setScreen(new DryStreakEditScreen(this))));
                out.add(tile("Edit All-Time Stats", Items.KNOWLEDGE_BOOK, "Fix any total that counted wrong", b -> mc.setScreen(new StatsEditScreen(this))));
                // one button for both: FOE's dry streaks, fish time and fish/hour, and FOE-R's stats (if it has more fish)
                out.add(tile("Import previous FOE/FOE-R stats", Items.BOOK, "From FOE and FOE-R. Shift-click: pick a FOE-R install's folder yourself",
                        b -> {
                            if (net.minecraft.client.Minecraft.getInstance().hasShiftDown() && net.minecraft.client.Minecraft.getInstance().player != null) {
                                dannypx.foe.handler.io.FoerStatsImporter.chooseFolder(net.minecraft.client.Minecraft.getInstance().player.getUUID());
                            } else {
                                // FOE's when it has a file; FOE-R's always (it says what it found, and offers the folder
                                // window when neither turns up)
                                boolean foe = dannypx.foe.handler.io.FoeStatsImporter.available();
                                if (foe) dannypx.foe.handler.io.FoeStatsImporter.importNow(false);
                                dannypx.foe.handler.io.FoerStatsImporter.importNow(!foe);
                            }
                        }));
                out.add(tile("Reset Fish Timer", Items.COMPASS, "Start fish/hour from zero", b -> dannypx.foe.handler.logic.FishSessionHandler.instance().reset()));
            }
            case SOCIAL -> {
                out.add(new TileSpec("Friends", () -> {
                    var all = dannypx.foe.handler.logic.FriendsHandler.instance().all();
                    long on = all.keySet().stream().filter(dannypx.foe.handler.logic.FriendsHandler::isOnline).count();
                    return Component.literal("Friends ").append(Component.literal(on + "/" + all.size() + " on").withColor(on > 0 ? 0xFF6BE37A : DIM));
                }, Items.PLAYER_HEAD, "Add, remove and see who's on, and where", true,
                        b -> mc.setScreen(new FriendsScreen(this))));
                out.add(new TileSpec("Social HUD", FoeMainScreen::crewPanelLabel, Items.LEATHER_HELMET, "Click to show or hide it on your screen", true,
                        b -> {
                            Configs.hudConfig.showCrewPanel.accept(!Configs.hudConfig.showCrewPanel.get());
                            Configs.hudConfig.save();
                        }));
                // the Social HUD and the inventory's box share it
                out.add(new TileSpec("Hide offline friends", FoeMainScreen::hideOfflineLabel, Items.GRAY_DYE,
                        "In the Social HUD and the box beside your inventory", true,
                        b -> {
                            Configs.inventoryScreenConfig.timersOffline.accept(!Configs.inventoryScreenConfig.timersOffline.get());
                            Configs.inventoryScreenConfig.save();
                        }));
            }
            case TOOLS -> {
                out.add(tile("Auto Tip", Items.GOLD_NUGGET, "Tip reaction winners and chummers (M)", b -> mc.setScreen(new AutotipScreen(this))));
                out.add(new TileSpec("Pet Prices", null, Items.EMERALD,
                        PetPriceScreen.ENABLED ? "What pets sell for, and yours to edit (U)" : "Being reworked: back in a later update", PetPriceScreen.ENABLED,
                        b -> { if (PetPriceScreen.ENABLED) mc.setScreen(new PetPriceScreen(this)); }));
                out.add(tile("Bait Prices", Items.WHEAT_SEEDS, "What your baitboxes are worth, in chat", b -> mc.setScreen(new BaitPricesScreen(this))));
                out.add(tile("Sounds", Items.NOTE_BLOCK, "Every FOE+ sound, and your own", b -> mc.setScreen(new SoundsScreen(this))));
                out.add(tile("More Tools", Items.CHEST, "Triggers, timers, notifications, snippets", b -> mc.setScreen(new MainScreen(this))));
            }
        }
        return out;
    }
    //endregion

    /// One part of the settings, opened straight to it (all of them if that can't be done).
    private static void openConfig(String part) {
        Minecraft mc = Minecraft.getInstance();
        Object config = switch (part.contains(".") ? part.substring(0, part.indexOf('.')) : part) {
            case "hud_config" -> Configs.hudConfig;
            case "renderer_config" -> Configs.rendererConfig;
            case "keybinding_config" -> Configs.keyBindConfig;
            case "inventory_config" -> Configs.inventoryScreenConfig;
            case "autotip_config" -> Configs.autotipConfig;
            default -> Configs.mainConfig;
        };
        FoeSettingsScreen s = FoeSettingsScreen.at(mc.screen, config);
        if (part.contains(".")) s.group((me.fzzyhmstrs.fzzy_config.config.Config) config, part.substring(part.indexOf('.') + 1));
        mc.setScreen(s);
    }

    //region Labels that show a setting's value
    private static Component iconStyleLabel() {
        return Component.literal("Icons: ").append(Component.literal(Configs.rendererConfig.iconStyle.get().displayName()).withStyle(ChatFormatting.AQUA));
    }

    private static Component hideOfflineLabel() {
        boolean hidden = !Configs.inventoryScreenConfig.timersOffline.get();
        return Component.literal("Hide offline friends: ").append(Component.literal(hidden ? "On" : "Off").withStyle(hidden ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static Component crewPanelLabel() {
        boolean on = Configs.hudConfig.showCrewPanel.get();
        return Component.literal("Social HUD: ").append(Component.literal(on ? "On" : "Off").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static Component catchRingLabel() {
        boolean on = Configs.hudConfig.catchRingOn.get();
        return Component.literal("Catch Ring: ").append(Component.literal(on ? "On" : "Off").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static Component tpsPingLabel() {
        boolean on = Configs.hudConfig.topBarShowTpsPing.get();
        return Component.literal("TPS & Ping: ").append(Component.literal(on ? "On" : "Off").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static Component hudStyleLabel() {
        return Component.literal("HUD Style: ").append(Component.literal(Configs.hudConfig.hudStyle.get().displayName()).withStyle(ChatFormatting.AQUA));
    }

    private static Component bobberLabel() {
        return Component.literal("Bobber: ").append(Component.literal(Configs.rendererConfig.bobberStyle.get().displayName()).withStyle(ChatFormatting.AQUA));
    }

    private static Component inspectLabel() {
        dannypx.foe.type.InspectAlways mode = dannypx.foe.handler.logic.KeyBindHandler.inspectAlways();
        String key = KeyBindHelper.getKeyString(Configs.keyBindConfig.inspectKeybind);
        return Component.literal("Inspect: ").append(mode == dannypx.foe.type.InspectAlways.OFF
                ? Component.literal("Hold " + capitalize(key.replaceFirst("(?i)^(left|right) ", ""))).withStyle(ChatFormatting.AQUA)
                : Component.literal(mode.toString()).withStyle(ChatFormatting.GREEN));
    }

    /// "+ Colour: + Gold" with the + in your colour (Rainbow / Green for the developer and beta testers until they
    /// pick a normal colour).
    private Component plusLabel() {
        java.util.UUID me = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
        boolean dev = me != null && me.equals(dannypx.foe.handler.logic.TextDisplayHandler.DEV_UUID);
        boolean beta = me != null && dannypx.foe.handler.logic.TextDisplayHandler.BETA_TESTERS.contains(me);
        int value = Configs.rendererConfig.foePlusColor.get();
        Component sign;
        String name;
        if (dannypx.foe.helper.NameTagMarker.isListColor(value) || (!dev && !beta)) {
            sign = dannypx.foe.helper.NameTagMarker.colorMarker(value);
            name = dannypx.foe.helper.NameTagMarker.plusColor(value).name();
        } else if (dev) {
            sign = dannypx.foe.helper.NameTagMarker.devMarker(System.currentTimeMillis());
            name = "Rainbow";
        } else {
            sign = dannypx.foe.helper.NameTagMarker.BETA_MARKER;
            name = "Green";
        }
        return Component.literal("+ Colour: ").append(sign).append(Component.literal(" " + name).withStyle(ChatFormatting.AQUA));
    }

    private static String capitalize(String text) {
        StringBuilder out = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
    //endregion

    //region Drawing
    /// A box with its corners cut one pixel in, so it reads softly rounded: an edge colour, then the fill.
    static void box(GuiGraphics g, int x, int y, int w, int h, int edge, int fill) {
        g.fill(x + 1, y, x + w - 1, y + 1, edge);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, edge);
        g.fill(x, y + 1, x + 1, y + h - 1, edge);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Over the blurred game: a soft shadow round the window, then the window.
        g.fill(0, 0, width, height, 0x50000000);
        for (int i = 1; i <= 6; i++) {
            int a = (int) (0x30 * (1 - i / 7f));
            box(g, wx - 2 - i, wy - 2 - i + 2, ww + 4 + 2 * i, wh + 4 + 2 * i, a << 24, 0);
        }
        box(g, wx - 2, wy - 2, ww + 4, wh + 4, EDGE, RIM);
        box(g, wx - 1, wy - 1, ww + 2, wh + 2, RIM, 0xFF0D1A28);
        for (int y = 0; y < wh; y += 128)
            for (int x = 0; x < ww; x += 128)
                g.blit(RenderPipelines.GUI_TEXTURED, PANEL, wx + x, wy + y, 0f, 0f, Math.min(128, ww - x), Math.min(128, wh - y), 128, 128);
        g.fill(wx, wy, wx + ww, wy + 1, RIM_LIGHT);                       // light along the top
        // The sidebar, darker, ruled off from the rest.
        g.fill(wx, wy + 1, wx + SIDEBAR_W, wy + wh, SIDEBAR);
        g.fill(wx + SIDEBAR_W, wy + 1, wx + SIDEBAR_W + 1, wy + wh, RULE);

        // Logo, and a welcome under it.
        int lh = logoH(), y = wy + 10;
        if (lh > 0) {
            int lw = SIDEBAR_W - 24;
            g.blit(RenderPipelines.GUI_TEXTURED, LOGO, wx + 12, y, LOGO_U, LOGO_V, lw, lh, LOGO_W, LOGO_H, LOGO_TEX, LOGO_TEX);
            y += lh + 6;
        }
        String name = this.minecraft.player != null ? this.minecraft.player.getName().getString() : this.minecraft.getUser().getName();
        g.drawCenteredString(font, Component.literal("Welcome back,"), wx + SIDEBAR_W / 2, y, DIM);
        g.drawCenteredString(font, Component.literal(font.plainSubstrByWidth(name, SIDEBAR_W - 12)), wx + SIDEBAR_W / 2, y + 11, TITLE);
        g.fill(wx + 12, y + 24, wx + SIDEBAR_W - 12, y + 25, RULE);

        // The version, at the foot of the sidebar.
        String ver = "v" + FishOnMCExtras.VERSION.replaceFirst("\\+.*$", "");
        g.drawCenteredString(font, Component.literal(font.plainSubstrByWidth(ver, SIDEBAR_W - 8)), wx + SIDEBAR_W / 2, wy + wh - 14, OFF);

        // The section's heading: its icon, its name and what's in it, over an accent line.
        int cx = wx + SIDEBAR_W + PAD, cr = wx + ww - PAD;
        g.renderItem(new ItemStack(section.icon), cx, wy + 11);
        g.drawString(font, Component.literal(section.name).withStyle(ChatFormatting.BOLD), cx + 22, wy + 11, TITLE, true);
        g.drawString(font, Component.literal(font.plainSubstrByWidth(section.blurb, cr - cx - 22)), cx + 22, wy + 22, DIM, false);
        g.fill(cx, wy + HEAD_H - 2, cr, wy + HEAD_H - 1, RULE);
        g.fill(cx, wy + HEAD_H - 2, cx + 32, wy + HEAD_H - 1, ACCENT);

        // The footer: a rule, a hint on the left, Done on the right.
        g.fill(cx, wy + wh - FOOTER_H, cr, wy + wh - FOOTER_H + 1, RULE);
        g.drawString(font, Component.literal("Press Esc to close"), cx, wy + wh - FOOTER_H + 10, OFF, false);

        super.render(g, mouseX, mouseY, delta);
    }
    //endregion

    /// A section in the sidebar: its icon and name, the chosen one lit with a blue bar.
    private static final class Tab extends Button {
        final Section s;

        Tab(int x, int y, int w, int h, Section s, OnPress press) {
            super(x, y, w, h, Component.literal(s.name), press, DEFAULT_NARRATION);
            this.s = s;
        }

        @Override
        protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            boolean on = s == section, hot = isHovered();
            if (on) box(g, getX(), getY(), width, height, 0xFF24496C, 0xFF17304A);
            else if (hot) box(g, getX(), getY(), width, height, 0x6024496C, 0x6017304A);
            if (on) g.fill(getX() + 1, getY() + 3, getX() + 3, getY() + height - 3, ACCENT);
            Minecraft mc = Minecraft.getInstance();
            g.renderItem(new ItemStack(s.icon), getX() + 7, getY() + (height - 16) / 2);
            g.drawString(mc.font, getMessage(), getX() + 29, getY() + (height - 8) / 2, on ? TITLE : hot ? TEXT : DIM, on);
        }
    }

    /// A tile: icon, name (or the setting and its value) and what it does, on a slate card.
    private static final class Tile extends Button {
        final TileSpec spec;

        Tile(int x, int y, int w, int h, TileSpec spec) {
            super(x, y, w, h, Component.literal(spec.title), spec.press, DEFAULT_NARRATION);
            this.spec = spec;
        }

        @Override
        protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            boolean hot = active && isHovered();
            int x = getX(), y = getY(), w = width, h = height;
            box(g, x, y, w, h, hot ? ACCENT : TILE_EDGE, hot ? TILE_HOT : TILE);
            if (hot) g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x603FA9F5);
            // the icon on its own dark badge
            int bx = x + 7, by = y + (h - 24) / 2;
            box(g, bx, by, 24, 24, hot ? 0xFF2F6894 : 0xFF203A55, 0xFF0B1724);
            g.renderItem(new ItemStack(spec.icon), bx + 4, by + 4);
            if (!active) g.fill(bx + 1, by + 1, bx + 23, by + 23, 0xA00B1724);
            Font font = Minecraft.getInstance().font;
            int tx = x + 38, tw = w - 38 - 14;
            Component title = spec.label != null ? spec.label.get() : Component.literal(spec.title);
            List<FormattedCharSequence> lines = font.split(Component.literal(spec.desc), tw);
            int textH = 9 + 3 + Math.min(2, lines.size()) * 10 - 1;
            int ty = y + (h - textH) / 2;
            if (font.width(title) <= tw) g.drawString(font, title, tx, ty, active ? TITLE : OFF, true);
            else g.drawString(font, font.plainSubstrByWidth(title.getString(), tw - 6) + "…", tx, ty, active ? TITLE : OFF, true);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                FormattedCharSequence line = lines.get(i);
                if (i == 1 && lines.size() > 2) {
                    StringBuilder sb = new StringBuilder();
                    line.accept((idx, st, cp) -> { sb.appendCodePoint(cp); return true; });
                    line = Component.literal(font.plainSubstrByWidth(sb.toString(), tw - 8) + "…").getVisualOrderText();
                }
                g.drawString(font, line, tx, ty + 12 + i * 10, active ? DIM : OFF, false);
            }
            // a chevron, brighter on hover
            if (active) {
                int ax = x + w - 10, ay = y + h / 2 - 3, c = hot ? ACCENT : 0xFF34506C;
                for (int i = 0; i < 4; i++) { g.fill(ax + i, ay + i, ax + i + 1, ay + i + 1, c); g.fill(ax + i, ay + 6 - i, ax + i + 1, ay + 7 - i, c); }
            }
        }
    }

    /// A plain flat button in the window's colours.
    private static final class FlatButton extends Button {
        FlatButton(int x, int y, int w, int h, Component msg, OnPress press) {
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
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
