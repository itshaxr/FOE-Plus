package dannypx.foe.screens;

import dannypx.foe.config.Configs;
import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.config.ConfigGroup;
import me.fzzyhmstrs.fzzy_config.util.Translatable;
import me.fzzyhmstrs.fzzy_config.validation.ValidatedField;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedChoice;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedKeybind;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedNumber;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FOE+'s settings: every option of every FOE+ config, laid out by category (General, HUD, Inventory, Visuals,
 * Controls, Auto Tip, and the developer pages behind a link), in the O menu's look. The HUD page lists every HUD on
 * the left and shows the one picked on the right. Each option gets the control that fits it: a switch, a ‹ › picker,
 * a slider with its value, a text box, a key. The settings themselves are still FOE+'s Fzzy Config ones (read from
 * them as they are, saved the same way), so nothing is lost; the classic screen is one click away for anything else.
 */
public class FoeSettingsScreen extends Screen {
    //region Model
    /// One option, or a group of them.
    static final class Node {
        final String name, desc;
        final Object entry;          // the setting (a ValidatedField), or null for a group
        final Config config;
        final String fieldName;
        final List<Node> children = new ArrayList<>();
        Node parent;

        Node(String name, String desc, Object entry, Config config, String fieldName) {
            this.name = name;
            this.desc = desc;
            this.entry = entry;
            this.config = config;
            this.fieldName = fieldName;
        }

        boolean group() {
            return entry == null;
        }

        /// Every option under it.
        void options(List<Node> out) {
            for (Node c : children) {
                if (c.group()) c.options(out);
                else out.add(c);
            }
        }
    }

    /// A page of the sidebar.
    record Category(String name, Node root, boolean list, boolean dev) {}

    static String clean(String s) {
        if (s == null) return "";
        String t = ChatFormatting.stripFormatting(s);
        if (t == null) t = s;
        t = t.replaceAll("§.", "").trim();
        if (t.startsWith("Dev: ")) t = t.substring(5);
        return t;
    }

    static boolean devName(String raw) {
        return raw != null && clean(raw.replaceAll("§.", "")).length() != raw.replaceAll("§.", "").trim().length();
    }

    /// The options of a config as a tree, in the order they're written (a group runs until the option that closes it).
    static Node read(Config config, String title) {
        Node root = new Node(title, "", null, config, "");
        Node cur = root;
        for (Field f : config.getClass().getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) || !Modifier.isPublic(m)) continue;
            Object value;
            try {
                value = f.get(config);
            } catch (IllegalAccessException e) {
                continue;
            }
            Translatable.Name n = f.getAnnotation(Translatable.Name.class);
            Translatable.Desc d = f.getAnnotation(Translatable.Desc.class);
            String rawName = n == null ? null : n.value();
            String desc = d == null ? "" : clean(d.value());
            if (value instanceof ConfigGroup) {
                Node g = new Node(rawName == null ? f.getName() : clean(rawName), desc, null, config, f.getName());
                g.parent = cur;
                cur.children.add(g);
                cur = g;
            } else if (value instanceof ValidatedField<?> && rawName != null && !HIDDEN.contains(f.getName())) {
                Node o = new Node(clean(rawName), desc, value, config, f.getName());
                o.parent = cur;
                if (devName(rawName)) o.parent = null;      // set aside below
                if (o.parent != null) cur.children.add(o);
                else DEV_MAIN.add(o);
            }
            int pops = f.getAnnotationsByType(ConfigGroup.Pop.class).length;
            for (int i = 0; i < pops && cur.parent != null; i++) cur = cur.parent;
        }
        return root;
    }

    /// Options no longer used (kept in the file so old settings load).
    static final java.util.Set<String> HIDDEN = java.util.Set.of("petPriceSheetUrl");
    /// The developer options from the general page.
    static final List<Node> DEV_MAIN = new ArrayList<>();

    static List<Category> categories() {
        DEV_MAIN.clear();
        List<Category> out = new ArrayList<>();
        out.add(new Category("General", read(Configs.mainConfig, "General"), false, false));
        out.add(new Category("HUD", read(Configs.hudConfig, "HUD"), true, false));
        out.add(new Category("Inventory", read(Configs.inventoryScreenConfig, "Inventory"), false, false));
        out.add(new Category("Visuals", read(Configs.rendererConfig, "Visuals"), false, false));
        out.add(new Category("Controls", read(Configs.keyBindConfig, "Controls"), false, false));
        out.add(new Category("Auto Tip", read(Configs.autotipConfig, "Auto Tip"), false, false));
        Node debug = read(Configs.debugConfig, "Debug");
        Node mixins = read(Configs.mixinConfig, "Mixins");
        Node handlers = read(Configs.handlerConfig, "Handlers");
        List<Node> devMain = new ArrayList<>(DEV_MAIN);
        for (int i = devMain.size() - 1; i >= 0; i--) {
            devMain.get(i).parent = debug;
            debug.children.addFirst(devMain.get(i));
        }
        out.add(new Category("Debug", debug, false, true));
        out.add(new Category("Mixins", mixins, false, true));
        out.add(new Category("Handlers", handlers, false, true));
        return out;
    }
    //endregion

    //region Reading and changing a setting
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object get(Object entry) {
        return ((ValidatedField) entry).get();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void set(Node n, Object value) {
        try {
            ((ValidatedField) n.entry).accept(value);
            n.config.save();
        } catch (Throwable t) {
            dannypx.foe.handler.logic.LoggerHandler.error(String.valueOf(t));
        }
    }

    /// The same without saving (the colour wheel, many times a second while dragged; saved when it's closed).
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void setQuiet(Node n, Object value) {
        try {
            ((ValidatedField) n.entry).accept(value);
        } catch (Throwable t) {
            dannypx.foe.handler.logic.LoggerHandler.error(String.valueOf(t));
        }
    }

    private static final Map<String, Method> METHODS = new HashMap<>();

    private static Object call(Class<?> owner, String method, Object on) {
        try {
            Method m = METHODS.computeIfAbsent(owner.getName() + "#" + method, k -> {
                try {
                    Method x = owner.getDeclaredMethod(method);
                    x.setAccessible(true);
                    return x;
                } catch (NoSuchMethodException e) {
                    return null;
                }
            });
            return m == null ? null : m.invoke(on);
        } catch (Throwable t) {
            return null;
        }
    }

    static Object defaultOf(Object entry) {
        return call(ValidatedField.class, "getDefaultValue", entry);
    }

    static double min(Object entry) {
        Object v = call(ValidatedNumber.class, "getMinValue", entry);
        return v instanceof Number n ? n.doubleValue() : Double.NaN;
    }

    static double max(Object entry) {
        Object v = call(ValidatedNumber.class, "getMaxValue", entry);
        return v instanceof Number n ? n.doubleValue() : Double.NaN;
    }

    static double step(Object entry) {
        Object v = call(entry.getClass(), "getIncrement", entry);
        if (v instanceof Number n && n.doubleValue() > 0) return n.doubleValue();
        Object cur = get(entry);
        return cur instanceof Float || cur instanceof Double ? 0.05 : 1;
    }

    @SuppressWarnings("unchecked")
    static List<Object> choices(Object entry) {
        if (entry instanceof ValidatedChoice<?>) {
            try {
                Field f = ValidatedChoice.class.getDeclaredField("choices");
                f.setAccessible(true);
                return new ArrayList<>((List<Object>) f.get(entry));
            } catch (Throwable ignored) {
                // below
            }
        }
        Object v = get(entry);
        if (v instanceof Enum<?> e) return new ArrayList<>(List.of(e.getDeclaringClass().getEnumConstants()));
        return List.of(v);
    }

    /// A choice as it reads: its translation when it has one, else its own name ("TOP_LEFT" → "Top left").
    static String choiceName(Object v) {
        if (v == null) return "";
        if (v instanceof me.fzzyhmstrs.fzzy_config.util.EnumTranslatable t && v instanceof Enum<?> e) {
            String key = t.prefix() + "." + e.name();
            if (I18n.exists(key)) return I18n.get(key);
        }
        String s = v.toString();
        if (v instanceof Enum<?> e && s.equals(e.name())) {
            String w = s.replace('_', ' ').toLowerCase(Locale.ROOT);
            return Character.toUpperCase(w.charAt(0)) + w.substring(1);
        }
        return s;
    }

    static String number(Object v, Node n) {
        String s;
        if (v instanceof Float || v instanceof Double) {
            s = String.format(Locale.ROOT, "%.2f", ((Number) v).doubleValue()).replaceAll("\\.?0+$", "");
        } else s = String.valueOf(v);
        return n.name.contains("%") ? s + "%" : s;
    }

    enum Kind { TOGGLE, SLIDER, CYCLE, TEXT, KEY, OPEN, COLOR }

    /// The colour settings (every HUD setting kept as "#RRGGBB" text: XP bars, borders, boxes, the catch ring...),
    /// which get a colour wheel.
    private static java.util.Set<Object> colourEntries;

    static boolean colourEntry(Object entry) {
        var c = dannypx.foe.config.Configs.hudConfig;
        if (c == null || !(entry instanceof me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString)) return false;
        if (colourEntries == null) {
            java.util.Set<Object> found = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (java.lang.reflect.Field f : c.getClass().getFields()) {
                if (!f.getName().endsWith("Color") || f.getType() != me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString.class) continue;
                try {
                    found.add(f.get(c));
                } catch (IllegalAccessException ignored) {
                    // not one then
                }
            }
            colourEntries = found;
        }
        return colourEntries.contains(entry);
    }

    /// The colour a colour setting has when it's left at Default (the part's own).
    static int ownColour(Object entry) {
        var c = dannypx.foe.config.Configs.hudConfig;
        if (entry == c.catchPopupBorderColor) return 0xFFD866;
        if (entry == c.catchRingComingColor) return 0x7CD8FF;
        if (entry == c.catchRingReelColor) return 0x7CF07C;
        if (entry == c.profileElementBorderColor) return 0xE8B54A;
        if (entry == c.petElementBorderColor) return 0x55FFFF;
        if (entry == c.locationElementBorderColor) return 0x55AAFF;
        if (entry == c.foeRHotbarBorderColor) return 0x6B4A2B;
        if (entry == c.profileElementBoxColor || entry == c.petElementBoxColor || entry == c.locationElementBoxColor
                || entry == c.foeRHotbarBoxColor) return 0x101418;
        if (dannypx.foe.screens.element.hud.GameXpBar.gameStyle()) return dannypx.foe.screens.element.hud.GameXpBar.FILL;
        return entry == c.profileElementXpBarColor ? 0xFFD866 : 0x7CF07C;
    }

    static Kind kind(Object entry) {
        if (entry instanceof ValidatedBoolean) return Kind.TOGGLE;
        if (colourEntry(entry)) return Kind.COLOR;
        if (entry instanceof ValidatedEnum<?> || entry instanceof ValidatedChoice<?>) return Kind.CYCLE;
        if (entry instanceof ValidatedKeybind) return Kind.KEY;
        if (entry instanceof ValidatedString) return Kind.TEXT;
        if (entry instanceof ValidatedNumber<?>) {
            Object v = get(entry);
            double lo = min(entry), hi = max(entry);
            if (v instanceof Long || Double.isNaN(lo) || Double.isNaN(hi)) return Kind.TEXT;
            if ((v instanceof Integer || v instanceof Short) && hi - lo > 1000) return Kind.TEXT;
            if (hi - lo > 100000) return Kind.TEXT;
            return Kind.SLIDER;
        }
        return Kind.OPEN;
    }
    //endregion

    //region Look (the O menu's)
    static final int RIM = 0xFF2B4E6E, RIM_LIGHT = 0xFF3E6C94, SIDEBAR = 0xC0081320, RULE = 0xFF1E3550,
            ACCENT = 0xFF3FA9F5, TILE = 0xFF14263A, TILE_EDGE = 0xFF22405E, TILE_HOT = 0xFF1B3450,
            TITLE = 0xFFEAF4FF, TEXT = 0xFFC9D7E6, DIM = 0xFF7F95AB, OFF = 0xFF55687B, GOLD = 0xFFFFC23A, GREEN = 0xFF6BE37A,
            SEL_EDGE = 0xFF24496C, SEL_FILL = 0xFF17304A, WELL = 0xFF0B1724;

    static void box(GuiGraphics g, int x, int y, int w, int h, int edge, int fill) {
        FoeMainScreen.box(g, x, y, w, h, edge, fill);
    }
    //endregion

    private static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(dannypx.foe.FishOnMCExtras.MOD_ID)
            .map(m -> "v" + m.getMetadata().getVersion().getFriendlyString().replaceAll("\\+.*", "")).orElse("");
    private final Screen parent;
    private final List<Category> cats = categories();
    private int cat = 0;
    private Node hud;              // the HUD picked on the HUD page
    private boolean dev = false;
    private int scroll = 0, listScroll = 0;
    private String search = "";
    private EditBox find;
    private boolean resetArmed = false;
    /// Set to jump to (and flash) one option once laid out.
    private Node jumpTo;
    private long flashUntil = 0L;
    private Node flash;

    // layout
    private int wx, wy, ww, wh, side, listW, cx, cr, top, bottom, paneX;

    /// One line of the right pane.
    private record Item(int type, Node node, int y, int h, String text) {}

    private static final int HEAD = 0, SUB = 1, OPTION = 2, DESC = 3, POSITION = 4, EMPTY = 5;
    private final List<Item> items = new ArrayList<>();
    private int contentH = 0;
    private final Map<Node, EditBox> boxes = new HashMap<>();
    private Node dragging;

    public FoeSettingsScreen(Screen parent) {
        super(Component.literal("FOE+ Settings"));
        this.parent = parent;
    }

    /// The settings, opened on the option (or group) holding `entry`: a setting, a config, or null for the start.
    public static FoeSettingsScreen at(Screen parent, Object entry) {
        FoeSettingsScreen s = new FoeSettingsScreen(parent);
        if (entry != null) s.select(entry);
        return s;
    }

    public static void open(Object entry) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(at(mc.screen, entry));
    }

    /// Opens on the category `name` ("HUD", "Inventory"...), or the first page.
    public static void openCategory(String name) {
        Minecraft mc = Minecraft.getInstance();
        FoeSettingsScreen s = new FoeSettingsScreen(mc.screen);
        for (int i = 0; i < s.cats.size(); i++) if (s.cats.get(i).name().equalsIgnoreCase(name)) s.cat = i;
        mc.setScreen(s);
    }

    /// Opens on the group `fieldName` of `config` (a HUD on the HUD page).
    public void group(Config config, String fieldName) {
        for (int i = 0; i < cats.size(); i++) {
            Category c = cats.get(i);
            if (c.root().config != config) continue;
            cat = i;
            for (Node n : c.list() ? hudList() : c.root().children) {
                if (n.group() && n.fieldName.equals(fieldName)) {
                    if (c.list()) hud = n;
                    else jumpTo = n;
                }
            }
        }
    }

    private void select(Object entry) {
        for (int i = 0; i < cats.size(); i++) {
            Category c = cats.get(i);
            if (c.root().config == entry) { cat = i; if (c.dev()) dev = true; return; }
            Node n = find(c.root(), entry);
            if (n != null) {
                cat = i;
                if (c.dev()) dev = true;
                if (c.list()) hud = topGroup(n);
                jumpTo = n;
                return;
            }
        }
    }

    private static Node find(Node in, Object entry) {
        for (Node c : in.children) {
            if (c.entry == entry) return c;
            Node f = find(c, entry);
            if (f != null) return f;
        }
        return null;
    }

    private static Node topGroup(Node n) {
        Node g = n;
        while (g.parent != null && g.parent.parent != null) g = g.parent;
        return g.group() ? g : null;
    }

    private Category category() {
        return cats.get(cat);
    }

    /// The HUDs of the HUD page: "General" (the options outside any HUD) first, then each HUD.
    private List<Node> huds() {
        List<Node> out = new ArrayList<>();
        Node root = cats.get(1).root();
        Node general = new Node("General", "The look of every HUD, and options for them all", null, root.config, "");
        for (Node c : root.children) if (!c.group()) general.children.add(c);
        out.add(general);
        for (Node c : root.children) if (c.group()) out.add(c);
        // the colour round the pet is the pet panel's option, and the FOE-R pet box's too: shown on both
        Node frame = null, petBox = null;
        for (Node g : out) {
            if (g.fieldName.equals("petElementGroup")) petBox = g;
            for (Node c : g.children) if (c.fieldName.equals("petPanelFrameColor")) frame = c;
        }
        if (frame != null && petBox != null) {
            Node copy = new Node(frame.name, frame.desc, frame.entry, frame.config, frame.fieldName);
            copy.parent = petBox;
            int at = petBox.children.size();
            for (int i = 0; i < petBox.children.size(); i++) if (petBox.children.get(i).group()) { at = i; break; }
            petBox.children.add(at, copy);
        }
        return out;
    }

    private List<Node> hudsCache;

    private List<Node> hudList() {
        if (hudsCache == null) hudsCache = huds();
        return hudsCache;
    }

    /// Whether a HUD is on: its first "Show…" switch.
    private static Boolean hudOn(Node g) {
        for (Node c : g.children) {
            if (!c.group() && c.entry instanceof ValidatedBoolean b && c.name.startsWith("Show")) return b.get();
            if (!c.group()) break;
        }
        return null;
    }

    @Override
    protected void init() {
        ww = Math.min(width - 16, 640);
        wh = Math.min(height - 16, 420);
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;
        side = ww < 480 ? 86 : 104;
        listW = ww < 480 ? 96 : 124;
        cx = wx + side + 12;
        cr = wx + ww - 12;
        top = wy + 38;
        bottom = wy + wh - 30;
        int searchW = Math.min(140, (cr - cx) / 3);
        String keep = find == null ? search : find.getValue();
        find = new EditBox(font, cr - searchW, wy + 8, searchW, 16, Component.literal("Search"));
        find.setHint(Component.literal("Search settings…").withColor(OFF));
        find.setValue(keep);
        find.setResponder(t -> {
            if (t.equals(search)) return;
            search = t;
            scroll = 0;
            layout();
        });
        addRenderableWidget(find);
        if (category().list() && hud == null) hud = hudList().getFirst();
        layout();
    }

    private boolean searching() {
        return !search.isBlank();
    }

    private boolean listPage() {
        return category().list() && !searching();
    }

    //region Layout
    private void layout() {
        for (EditBox b : boxes.values()) removeWidget(b);
        boxes.clear();
        items.clear();
        paneX = listPage() ? cx + listW + 14 : cx;
        int y = 0;
        if (searching()) {
            String q = search.trim().toLowerCase(Locale.ROOT);
            int found = 0;
            for (Category c : cats) {
                if (c.dev() && !dev) continue;
                List<Node> all = new ArrayList<>();
                c.root().options(all);
                String last = null;
                for (Node n : all) {
                    String where = n.parent == null || n.parent == c.root() ? c.name() : c.name() + "  ›  " + path(n.parent, c.root());
                    if (!(n.name.toLowerCase(Locale.ROOT).contains(q) || n.desc.toLowerCase(Locale.ROOT).contains(q)
                            || where.toLowerCase(Locale.ROOT).contains(q))) continue;
                    if (!where.equals(last)) {
                        items.add(new Item(HEAD, null, y, 15, where.toUpperCase(Locale.ROOT)));
                        y += 15;
                        last = where;
                    }
                    y = option(n, y);
                    found++;
                }
            }
            if (found == 0) items.add(new Item(EMPTY, null, y, 20, "Nothing matches \"" + search.trim() + "\""));
        } else if (listPage()) {
            Node g = hud;
            items.add(new Item(HEAD, g, y, 13, hudName(g).toUpperCase(Locale.ROOT)));
            y += 13;
            if (!g.desc.isEmpty()) {
                for (FormattedCharSequence line : font.split(Component.literal(g.desc), cr - paneX)) {
                    items.add(new Item(DESC, null, y, 10, null));
                    descLines.put(items.size() - 1, line);
                    y += 10;
                }
            }
            y += 6;
            y = groupBody(g, y, true);
        } else {
            Node root = category().root();
            boolean loose = false;
            for (Node c : root.children) if (!c.group()) { loose = true; break; }
            if (loose && hasGroups(root)) {
                items.add(new Item(HEAD, null, y, 15, "GENERAL"));
                y += 15;
            }
            for (Node c : root.children) if (!c.group()) y = option(c, y);
            for (Node c : root.children) {
                if (!c.group()) continue;
                y += 6;
                items.add(new Item(HEAD, c, y, 15, c.name.toUpperCase(Locale.ROOT)));
                y += 15;
                y = groupBody(c, y, false);
            }
            if (root.children.isEmpty()) items.add(new Item(EMPTY, null, y, 20, "Nothing to set here"));
        }
        contentH = y;
        int view = bottom - top;
        if (jumpTo != null) {
            for (Item it : items) if (it.node() == jumpTo) { scroll = Math.max(0, it.y() - view / 3); flash = jumpTo; flashUntil = System.currentTimeMillis() + 1600L; break; }
            jumpTo = null;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - view)));
        placeBoxes();
    }

    private final Map<Integer, FormattedCharSequence> descLines = new HashMap<>();

    private static boolean hasGroups(Node n) {
        for (Node c : n.children) if (c.group()) return true;
        return false;
    }

    private static String path(Node g, Node root) {
        List<String> parts = new ArrayList<>();
        for (Node x = g; x != null && x != root; x = x.parent) parts.addFirst(x.name);
        return String.join("  ›  ", parts);
    }

    /// A group's options, its own groups under subheadings. On the HUD page its X and Y become one Position line.
    private int groupBody(Node g, int y, boolean hudPage) {
        Node xPos = null, yPos = null;
        if (hudPage) for (Node c : g.children) {
            if (c.group()) continue;
            if (c.fieldName.endsWith("XPosition")) xPos = c;
            if (c.fieldName.endsWith("YPosition")) yPos = c;
        }
        boolean placed = false;
        for (Node c : g.children) {
            if (c.group()) continue;
            if (xPos != null && yPos != null && (c == xPos || c == yPos)) {
                if (!placed) {
                    items.add(new Item(POSITION, c, y, 25, xPos.fieldName + "|" + yPos.fieldName));
                    positions.put(c, new Node[]{xPos, yPos});
                    y += 25;
                    placed = true;
                }
                continue;
            }
            y = option(c, y);
        }
        for (Node c : g.children) {
            if (!c.group()) continue;
            y += 4;
            items.add(new Item(SUB, c, y, 14, c.name));
            y += 14;
            y = groupBody(c, y, false);
        }
        return y;
    }

    private final Map<Node, Node[]> positions = new HashMap<>();

    private int option(Node n, int y) {
        items.add(new Item(OPTION, n, y, 25, null));
        if (kind(n.entry) == Kind.TEXT) {
            EditBox b = new EditBox(font, 0, 0, 120, 14, Component.literal(n.name));
            b.setMaxLength(4096);
            Object v = get(n.entry);
            b.setValue(v == null ? "" : String.valueOf(v));
            b.moveCursorToStart(false);
            b.setResponder(t -> typed(n, b, t));
            boxes.put(n, b);
            addRenderableWidget(b);
        }
        return y + 25;
    }

    private void typed(Node n, EditBox b, String t) {
        Object cur = get(n.entry);
        Object value = t;
        try {
            if (cur instanceof Long) value = Long.parseLong(t.trim().replace(",", ""));
            else if (cur instanceof Integer) value = Integer.parseInt(t.trim().replace(",", ""));
            else if (cur instanceof Float) value = Float.parseFloat(t.trim());
            else if (cur instanceof Double) value = Double.parseDouble(t.trim());
        } catch (NumberFormatException e) {
            b.setTextColor(0xFFFF6B6B);
            return;
        }
        if (n.entry instanceof ValidatedNumber<?>) {
            double lo = min(n.entry), hi = max(n.entry), d = ((Number) value).doubleValue();
            if ((!Double.isNaN(lo) && d < lo) || (!Double.isNaN(hi) && d > hi)) {
                b.setTextColor(0xFFFF6B6B);
                return;
            }
        }
        b.setTextColor(0xFFE0E0E0);
        set(n, value);
    }

    /// Puts each text box on its line (hidden when its line is scrolled out of view).
    private void placeBoxes() {
        for (Item it : items) {
            if (it.type() != OPTION) continue;
            EditBox b = boxes.get(it.node());
            if (b == null) continue;
            int y = top + it.y() - scroll;
            b.setX(cr - 136);
            b.setY(y + 4);
            b.setWidth(128);
            b.visible = y + 2 >= top && y + 20 <= bottom;
        }
    }
    //endregion

    //region Drawing
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);
        g.fill(0, 0, width, height, 0x50000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        box(g, wx - 1, wy - 1, ww + 2, wh + 2, RIM, 0xFF0D1A28);
        g.fill(wx, wy, wx + ww, wy + 1, RIM_LIGHT);
        g.fill(wx, wy + 1, wx + side, wy + wh, SIDEBAR);
        g.fill(wx + side, wy + 1, wx + side + 1, wy + wh, RULE);

        // sidebar
        g.drawString(font, "FOE+", wx + 12, wy + 12, GOLD, true);
        g.drawString(font, "Settings", wx + 12 + font.width("FOE+ "), wy + 12, TITLE, true);
        g.fill(wx + 10, wy + 26, wx + side - 10, wy + 27, RULE);
        int y = wy + 34;
        for (int i = 0; i < cats.size(); i++) {
            Category c = cats.get(i);
            if (c.dev()) continue;
            y = drawCategory(g, i, y, mouseX, mouseY);
        }
        int devY = wy + wh - 44 - (dev ? 3 * 20 : 0);
        g.fill(wx + 10, devY, wx + side - 10, devY + 1, RULE);
        boolean devHot = mouseX >= wx + 6 && mouseX < wx + side - 6 && mouseY >= devY + 4 && mouseY < devY + 18;
        g.drawString(font, "Developer " + (dev ? "-" : "+"), wx + 16, devY + 8, devHot ? TEXT : OFF, false);
        if (dev) {
            int dy = devY + 24;
            for (int i = 0; i < cats.size(); i++) if (cats.get(i).dev()) dy = drawCategory(g, i, dy, mouseX, mouseY);
        }
        g.drawString(font, VERSION, wx + 16, wy + wh - 16, OFF, false);

        // header
        String crumb = searching() ? "Search" : listPage() ? "HUD  ›  " + hudName(hud) : category().name();
        g.drawString(font, font.plainSubstrByWidth(crumb, Math.max(40, find.getX() - cx - 8)), cx, wy + 12, TITLE, true);
        g.fill(cx, wy + 30, cr, wy + 31, RULE);
        g.fill(cx, wy + 30, cx + 32, wy + 31, ACCENT);

        // the HUD list
        if (listPage()) drawHudList(g, mouseX, mouseY);

        // the options
        g.enableScissor(paneX - 2, top - 4, cr + 2, bottom);
        Node hotNode = null;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            int iy = top + it.y() - scroll;
            if (iy + it.h() < top - 4 || iy > bottom) continue;
            switch (it.type()) {
                case HEAD -> g.drawString(font, font.plainSubstrByWidth(it.text(), cr - paneX), paneX, iy + 2, GOLD, true);
                case SUB -> {
                    g.drawString(font, it.text(), paneX + 2, iy + 3, TITLE, false);
                    g.fill(paneX + 4 + font.width(it.text()), iy + 7, cr, iy + 8, RULE);
                }
                case DESC -> {
                    FormattedCharSequence line = descLines.get(i);
                    if (line != null) g.drawString(font, line, paneX, iy, DIM, false);
                }
                case EMPTY -> g.drawString(font, it.text(), paneX, iy + 6, DIM, false);
                case POSITION -> drawPosition(g, it, iy, mouseX, mouseY);
                default -> {
                    if (drawOption(g, it.node(), iy, mouseX, mouseY)) hotNode = it.node();
                }
            }
        }
        g.disableScissor();
        // scroll bar
        int view = bottom - top;
        if (contentH > view) {
            int bh = Math.max(16, view * view / contentH);
            int by = top + (view - bh) * scroll / Math.max(1, contentH - view);
            g.fill(cr + 4, top, cr + 6, bottom, WELL);
            g.fill(cr + 4, by, cr + 6, by + bh, RIM_LIGHT);
        }

        // footer
        g.fill(cx, bottom + 2, cr, bottom + 3, RULE);
        int fx = cx;
        fx = footerButton(g, fx, resetArmed ? "Click again to reset" : (listPage() ? "Reset " + hudName(hud) : "Reset page"), mouseX, mouseY, false) + 6;
        if (category().list() || searching()) fx = footerButton(g, fx, "HUD Editor", mouseX, mouseY, false) + 6;
        footerButton(g, fx, "Classic view", mouseX, mouseY, false);
        drawDone(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, delta);   // the search and text boxes

        if (hotNode != null && !hotNode.desc.isEmpty() && mouseX < cr - 140)
            g.setTooltipForNextFrame(font, font.split(Component.literal(hotNode.desc), 240), mouseX, mouseY);
    }

    private int drawCategory(GuiGraphics g, int i, int y, int mouseX, int mouseY) {
        Category c = cats.get(i);
        boolean sel = i == cat && !searching();
        boolean hot = mouseX >= wx + 6 && mouseX < wx + side - 6 && mouseY >= y - 4 && mouseY < y + 12;
        if (sel) {
            box(g, wx + 6, y - 4, side - 12, 16, SEL_EDGE, SEL_FILL);
            g.fill(wx + 7, y - 1, wx + 9, y + 9, ACCENT);
        } else if (hot) g.fill(wx + 7, y - 3, wx + side - 7, y + 11, 0x301B3450);
        g.drawString(font, c.name(), wx + 16, y, sel ? TITLE : hot ? TITLE : TEXT, false);
        return y + 20;
    }

    private void drawHudList(GuiGraphics g, int mouseX, int mouseY) {
        List<Node> list = hudList();
        g.enableScissor(cx - 4, top - 4, cx + listW + 2, bottom);
        int y = top - listScroll;
        for (Node n : list) {
            boolean sel = n == hud;
            boolean hot = mouseX >= cx - 2 && mouseX < cx + listW && mouseY >= y - 3 && mouseY < y + 12 && mouseY >= top && mouseY < bottom;
            if (sel) box(g, cx - 2, y - 3, listW, 15, SEL_EDGE, SEL_FILL);
            else if (hot) g.fill(cx - 1, y - 2, cx + listW - 3, y + 11, 0x301B3450);
            Boolean on = hudOn(n);
            if (on != null) g.fill(cx + 3, y + 2, cx + 7, y + 6, on ? GREEN : OFF);
            String name = hudName(n);
            g.drawString(font, font.plainSubstrByWidth(name, listW - 16), cx + 12, y, sel ? TITLE : on == Boolean.FALSE ? DIM : TEXT, false);
            y += 17;
        }
        g.disableScissor();
        g.fill(cx + listW + 4, top - 4, cx + listW + 5, bottom, RULE);
    }

    /// A HUD's name in the list: "Pet Panel" → "Pet", "Pet Element" (the FOE-R box) → "Pet box".
    static String hudName(Node n) {
        String s = n.name.replaceAll(" Panel$", "");
        if (s.endsWith(" Element")) {
            s = s.substring(0, s.length() - " Element".length());
            if (s.equals("Profile") || s.equals("Location") || s.equals("Pet")) s += " box";
        }
        return s;
    }

    private int listHeight() {
        return hudList().size() * 17;
    }

    private String label(Node n) {
        // on the HUD page, a HUD's own on/off switch reads "Shown"
        if (listPage() && n.entry instanceof ValidatedBoolean && n.parent == hud && n.name.startsWith("Show ") && hud.children.indexOf(n) == 0)
            return "Shown";
        if (n.name.equals("Anchor point")) return "Anchor";
        return n.name;
    }

    /// One option's line; true when the mouse is over its name.
    private boolean drawOption(GuiGraphics g, Node n, int y, int mouseX, int mouseY) {
        boolean hot = mouseX >= paneX && mouseX < cr && mouseY >= y && mouseY < y + 22 && mouseY >= top && mouseY < bottom;
        boolean flashing = n == flash && System.currentTimeMillis() < flashUntil;
        box(g, paneX, y, cr - paneX, 22, flashing ? ACCENT : hot ? 0xFF2F5A80 : TILE_EDGE, hot ? TILE_HOT : TILE);
        Kind k = kind(n.entry);
        int controlX = cr - 136;
        int labelW = (k == Kind.TOGGLE ? cr - 36 : controlX - 6) - (paneX + 8);
        String text = label(n);
        String shown = font.width(text) > labelW ? font.plainSubstrByWidth(text, labelW - font.width("…")) + "…" : text;
        g.drawString(font, shown, paneX + 8, y + 7, TEXT, false);
        Object v = get(n.entry);
        switch (k) {
            case TOGGLE -> toggle(g, cr - 30, y + 6, Boolean.TRUE.equals(v));
            case CYCLE -> cycler(g, controlX, y + 7, 128, choiceName(v), mouseX, mouseY);
            case SLIDER -> {
                double lo = min(n.entry), hi = max(n.entry), d = ((Number) v).doubleValue();
                float frac = hi > lo ? (float) ((d - lo) / (hi - lo)) : 0f;
                slider(g, controlX, y + 6, 86, frac, number(v, n));
            }
            case KEY -> {
                Component key = v instanceof me.fzzyhmstrs.fzzy_config.screen.context.FzzyKeybind kb ? kb.keybind() : Component.literal("?");
                boolean kh = mouseX >= controlX && mouseX < controlX + 128 && mouseY >= y + 4 && mouseY < y + 18;
                box(g, controlX, y + 4, 128, 14, kh ? ACCENT : TILE_EDGE, WELL);
                g.drawCenteredString(font, key, controlX + 64, y + 7, TITLE);
            }
            case OPEN -> smallButton(g, cr - 70, y + 3, 64, "Open…", mouseX, mouseY);
            case COLOR -> {
                // the colour (or the bar's own, marked Default) and a button for the wheel
                int rgb = dannypx.foe.screens.element.hud.GameXpBar.parse(String.valueOf(v));
                boolean own = rgb < 0;
                if (own) rgb = ownColour(n.entry);
                boolean ch = mouseX >= controlX && mouseX < cr - 6 && mouseY >= y + 3 && mouseY < y + 19;
                box(g, controlX, y + 4, 28, 14, ch ? ACCENT : TILE_EDGE, 0xFF000000 | rgb);
                g.drawString(font, own ? (n.entry == dannypx.foe.config.Configs.hudConfig.catchPopupBorderColor ? "No box" : "Default") : String.format(java.util.Locale.ROOT, "#%06X", rgb), controlX + 34, y + 7, own ? DIM : TEXT, false);
                smallButton(g, cr - 50, y + 3, 44, "Pick…", mouseX, mouseY);
            }
            default -> { }
        }
        return hot && mouseX < controlX;
    }

    private void drawPosition(GuiGraphics g, Item it, int y, int mouseX, int mouseY) {
        Node[] xy = positions.get(it.node());
        boolean hot = mouseX >= paneX && mouseX < cr && mouseY >= y && mouseY < y + 22;
        box(g, paneX, y, cr - paneX, 22, hot ? 0xFF2F5A80 : TILE_EDGE, hot ? TILE_HOT : TILE);
        g.drawString(font, "Position", paneX + 8, y + 7, TEXT, false);
        g.drawString(font, get(xy[0].entry) + "%, " + get(xy[1].entry) + "%", cr - 136, y + 7, DIM, false);
        smallButton(g, cr - 70, y + 3, 64, "Move…", mouseX, mouseY);
    }

    private void toggle(GuiGraphics g, int x, int y, boolean on) {
        box(g, x, y, 22, 10, on ? 0xFF2F8A4A : 0xFF34506C, on ? 0xFF1F6A36 : WELL);
        int k = on ? x + 12 : x + 2;
        box(g, k, y + 2, 8, 6, on ? 0xFFB8F5C8 : DIM, on ? GREEN : OFF);
    }

    private void cycler(GuiGraphics g, int x, int y, int w, String val, int mouseX, int mouseY) {
        boolean hot = mouseX >= x && mouseX < x + w && mouseY >= y - 3 && mouseY < y + 11;
        box(g, x, y - 3, w, 14, hot ? ACCENT : TILE_EDGE, WELL);
        g.drawString(font, "‹", x + 4, y, hot && mouseX < x + w / 2 ? TITLE : DIM, false);
        g.drawString(font, "›", x + w - 8, y, hot && mouseX >= x + w / 2 ? TITLE : DIM, false);
        g.drawCenteredString(font, font.plainSubstrByWidth(val, w - 22), x + w / 2, y, TITLE);
    }

    private void slider(GuiGraphics g, int x, int y, int w, float frac, String val) {
        frac = Math.max(0f, Math.min(1f, frac));
        g.fill(x, y + 4, x + w, y + 6, WELL);
        g.fill(x, y + 4, x + Math.round(w * frac), y + 6, ACCENT);
        int k = x + Math.round(w * frac);
        box(g, k - 2, y + 1, 5, 8, 0xFF7CC8FF, 0xFF3A92D6);
        g.drawString(font, val, cr - 8 - font.width(val), y + 1, TITLE, false);
    }

    private void smallButton(GuiGraphics g, int x, int y, int w, String s, int mouseX, int mouseY) {
        boolean hot = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
        box(g, x, y, w, 16, hot ? ACCENT : TILE_EDGE, hot ? TILE_HOT : TILE);
        g.drawCenteredString(font, s, x + w / 2, y + 4, hot ? TITLE : TEXT);
    }

    private int footerW(String s) {
        return Math.max(64, font.width(s) + 16);
    }

    private int footerButton(GuiGraphics g, int x, String s, int mouseX, int mouseY, boolean accent) {
        int w = footerW(s);
        smallButton(g, x, bottom + 8, w, s, mouseX, mouseY);
        return x + w;
    }

    private void drawDone(GuiGraphics g, int mouseX, int mouseY) {
        int x = cr - 70, y = bottom + 8;
        boolean hot = mouseX >= x && mouseX < cr && mouseY >= y && mouseY < y + 16;
        box(g, x, y, 70, 16, hot ? 0xFF7CC8FF : ACCENT, hot ? 0xFF3A92D6 : 0xFF2A78B8);
        g.fill(x + 1, y + 1, x + 69, y + 2, 0x40FFFFFF);
        g.drawCenteredString(font, "Done", x + 35, y + 4, 0xFFFFFFFF);
    }
    //endregion

    //region Input
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        // the text boxes and the search first
        if (super.mouseClicked(event, doubleClick)) {
            resetArmed = false;
            return true;
        }
        if (getFocused() instanceof EditBox e && e != find) e.setFocused(false);
        setFocused(null);
        // sidebar
        int y = wy + 34;
        for (int i = 0; i < cats.size(); i++) {
            if (cats.get(i).dev()) continue;
            if (mx >= wx + 6 && mx < wx + side - 6 && my >= y - 4 && my < y + 12) { pick(i); return true; }
            y += 20;
        }
        int devY = wy + wh - 44 - (dev ? 3 * 20 : 0);
        if (mx >= wx + 6 && mx < wx + side - 6 && my >= devY + 4 && my < devY + 18) {
            dev = !dev;
            if (!dev && category().dev()) pick(0);
            return true;
        }
        if (dev) {
            int dy = devY + 24;
            for (int i = 0; i < cats.size(); i++) {
                if (!cats.get(i).dev()) continue;
                if (mx >= wx + 6 && mx < wx + side - 6 && my >= dy - 4 && my < dy + 12) { pick(i); return true; }
                dy += 20;
            }
        }
        // footer
        int fy = bottom + 8;
        if (my >= fy && my < fy + 16) {
            if (mx >= cr - 70 && mx < cr) { onClose(); return true; }
            int fx = cx;
            String reset = resetArmed ? "Click again to reset" : (listPage() ? "Reset " + hudName(hud) : "Reset page");
            if (mx >= fx && mx < fx + footerW(reset)) {
                if (resetArmed) { resetShown(); resetArmed = false; } else resetArmed = true;
                return true;
            }
            fx += footerW(reset) + 6;
            if (category().list() || searching()) {
                if (mx >= fx && mx < fx + footerW("HUD Editor")) { minecraft.setScreen(new HudEditorScreen(this)); return true; }
                fx += footerW("HUD Editor") + 6;
            }
            if (mx >= fx && mx < fx + footerW("Classic view")) {
                ConfigApiJava.INSTANCE.openScreen(category().root().config.translationKey());
                return true;
            }
        }
        resetArmed = false;
        // HUD list
        if (listPage() && mx >= cx - 2 && mx < cx + listW && my >= top && my < bottom) {
            int ly = top - listScroll;
            for (Node n : hudList()) {
                if (my >= ly - 3 && my < ly + 14) {
                    if (n != hud) { hud = n; scroll = 0; layout(); }
                    return true;
                }
                ly += 17;
            }
            return true;
        }
        // options
        if (my >= top && my < bottom && mx >= paneX && mx < cr) {
            for (Item it : items) {
                int iy = top + it.y() - scroll;
                if (my < iy || my >= iy + 22) continue;
                if (it.type() == POSITION) {
                    if (mx >= cr - 70) minecraft.setScreen(new MoveElementScreen(this));
                    return true;
                }
                if (it.type() != OPTION) continue;
                return clickOption(it.node(), mx, my, iy, button);
            }
        }
        return false;
    }

    private boolean clickOption(Node n, double mx, double my, int y, int button) {
        int controlX = cr - 136;
        switch (kind(n.entry)) {
            case TOGGLE -> set(n, !Boolean.TRUE.equals(get(n.entry)));
            case CYCLE -> {
                List<Object> all = choices(n.entry);
                if (all.isEmpty()) return true;
                int i = Math.max(0, all.indexOf(get(n.entry)));
                boolean back = button == 1 || (mx >= controlX && mx < controlX + 64);
                set(n, all.get(Math.floorMod(i + (back ? -1 : 1), all.size())));
            }
            case SLIDER -> {
                if (mx >= controlX - 4 && mx < controlX + 92) {
                    dragging = n;
                    slide(n, mx);
                }
            }
            case KEY -> {
                if (mx >= controlX) ConfigApiJava.INSTANCE.openScreen(n.config.translationKey());
            }
            case OPEN -> {
                if (mx >= cr - 70) ConfigApiJava.INSTANCE.openScreen(n.config.translationKey());
            }
            case COLOR -> {
                if (mx >= controlX) {
                    String hudTitle = n.parent != null ? hudName(n.parent) : "";
                    minecraft.setScreen(new ColorPickerScreen(this, (hudTitle.isEmpty() ? "" : hudTitle + ": ") + label(n),
                            String.valueOf(get(n.entry)), ownColour(n.entry), c -> setQuiet(n, c), () -> n.config.save()));
                }
            }
            default -> { }
        }
        return true;
    }

    private void slide(Node n, double mx) {
        int controlX = cr - 136;
        double lo = min(n.entry), hi = max(n.entry);
        double frac = Math.max(0, Math.min(1, (mx - controlX) / 86.0));
        double step = step(n.entry);
        double v = lo + Math.round((hi - lo) * frac / step) * step;
        v = Math.max(lo, Math.min(hi, v));
        Object cur = get(n.entry);
        Object value;
        if (cur instanceof Integer) value = (int) Math.round(v);
        else if (cur instanceof Short) value = (short) Math.round(v);
        else if (cur instanceof Float) value = (float) (Math.round(v * 1000) / 1000.0);
        else value = Math.round(v * 1000) / 1000.0;
        if (!value.equals(cur)) {
            try {
                ((ValidatedField) n.entry).accept(value);
            } catch (Throwable ignored) {
                // a bad value: left as it was
            }
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            slide(dragging, event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging.config.save();
            dragging = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (listPage() && mx >= cx - 2 && mx < cx + listW && my >= top && my < bottom) {
            listScroll = (int) Math.max(0, Math.min(Math.max(0, listHeight() - (bottom - top)), listScroll - v * 17));
            return true;
        }
        int view = bottom - top;
        int next = (int) Math.max(0, Math.min(Math.max(0, contentH - view), scroll - v * 25));
        if (next != scroll) {
            scroll = next;
            placeBoxes();
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_F && event.hasControlDown()) {
            setFocused(find);
            find.setFocused(true);
            return true;
        }
        return super.keyPressed(event);
    }

    private void pick(int i) {
        cat = i;
        scroll = 0;
        resetArmed = false;
        if (searching()) {
            search = "";
            find.setValue("");
        }
        if (category().list() && hud == null) hud = hudList().getFirst();
        layout();
    }

    /// Every option shown puts back its starting value.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void resetShown() {
        java.util.Set<Config> touched = new java.util.HashSet<>();
        for (Item it : items) {
            List<Node> ns = new ArrayList<>();
            if (it.type() == OPTION) ns.add(it.node());
            if (it.type() == POSITION) ns.addAll(List.of(positions.get(it.node())));
            for (Node n : ns) {
                Object d = defaultOf(n.entry);
                if (d == null) continue;
                try {
                    ((ValidatedField) n.entry).accept(d);
                    touched.add(n.config);
                } catch (Throwable ignored) {
                    // kept
                }
            }
        }
        for (Config c : touched) c.save();
        layout();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
    //endregion
}
