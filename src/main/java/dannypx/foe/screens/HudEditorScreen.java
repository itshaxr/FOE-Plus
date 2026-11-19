package dannypx.foe.screens;

import dannypx.foe.handler.store.HudLooks;
import dannypx.foe.screens.element.hud.*;
import dannypx.foe.type.Alignment;
import dannypx.foe.type.TextAlign;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The HUD Editor: pick one of FOE+'s own HUD panels from the drop-down, then change how it looks (line spacing,
 * padding, the box's colour and how see-through it is, the border's and title's colours, the text alignment, a
 * picture by the title) and which of its lines show. The panel is drawn beside the controls as you change it,
 * and each change is saved straight away.
 */
public class HudEditorScreen extends Screen {
    private static final int H = 20, ROW = 23, COL_W = 196, PAD = 8, SW = 14;

    /// The panels that can be edited (FOE+'s own), each made as a preview copy.
    private static List<PanelElement> panels() {
        return List.of(new ContestPanelElement(true), new QuestPanelElement(true), new DailyQuestPanelElement(true),
                new StatsPanelElement(true), new SessionStatsPanelElement(true), new TimersPanelElement(true), new dannypx.foe.screens.element.hud.CrewPanelElement(true),
                new EventsPanelElement(true), new LuckScalePanelElement(true), new AlertPanelElement(true),
                new MiniEventPanelElement(true), new PetPanelElement(true), new BaitPanelElement(true),
                new GearWarningPanelElement(true), new dannypx.foe.screens.element.hud.ChummerPanelElement(true));
    }

    static final int[] BOX_COLORS = {0x000000, 0x0B1A33, 0x0E2A1A, 0x2E0E0E, 0x25103A, 0x0A2C2E, 0x2A2A2A, 0x2E2010};
    static final int[] BORDER_COLORS = {0xFFFFFF, 0xF2B33D, 0x55FFFF, 0xFF7AD9, 0x6BE36B, 0xFF5555, 0xB98CFF, 0x9A9A9A};
    static final int[] TITLE_COLORS = {-1, 0xFFFFFF, 0xF2B33D, 0x55FFFF, 0xFF7AD9, 0x6BE36B, 0xFFFF55, 0xFF5555};
    static final Item[] PICTURES = {Items.AIR, Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH, Items.FISHING_ROD,
            Items.CLOCK, Items.COMPASS, Items.MAP, Items.GOLD_INGOT, Items.EMERALD, Items.DIAMOND, Items.NETHER_STAR,
            Items.EXPERIENCE_BOTTLE, Items.HEART_OF_THE_SEA, Items.NAUTILUS_SHELL, Items.TRIDENT, Items.BOOK, Items.BELL,
            Items.LANTERN, Items.AMETHYST_SHARD, Items.PRISMARINE_CRYSTALS, Items.TURTLE_SCUTE, Items.BONE};

    private final Screen parent;
    private final List<PanelElement> panels = panels();
    private static int selected = 0;
    private static boolean linesTab = false;
    /// The Image tab: your own picture in the box.
    private static boolean imageTab = false;
    private boolean listOpen = false;
    private int lineScroll = 0;
    private int left, top, previewX, previewY, titleRowY = -1;
    /// Clickable colour squares: {x, y, which row (0 box, 1 border, 2 title), index}.
    private final List<int[]> swatches = new ArrayList<>();
    /// The line rows in the Lines tab: {x, y, w} and their labels.
    private final List<int[]> lineRows = new ArrayList<>();
    private final List<String> lineKeys = new ArrayList<>();
    private final List<Component> lineTexts = new ArrayList<>();

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
    }

    /// Package-private for tests: a HUD's name in the editor ("TPS & Ping Panel" → "TPS & Ping").
    static String nameOf(String message) {
        String n = message == null ? "" : message.trim();
        return n.endsWith(" Panel") ? n.substring(0, n.length() - " Panel".length()) : n;
    }

    private static String nameOf(PanelElement p) {
        return nameOf(p.message == null ? "" : p.message.getString());
    }

    private PanelElement panel() {
        return panels.get(Math.floorMod(selected, panels.size()));
    }

    private HudLooks.Look look() {
        return HudLooks.edit(panel().lookId());
    }

    @Override
    protected void init() {
        swatches.clear();
        lineRows.clear();
        left = Math.max(PAD, width / 2 - COL_W - 40);
        top = Math.max(26, height / 2 - 120);
        previewX = left + COL_W + 24;
        previewY = top;

        this.addRenderableWidget(Button.builder(Component.literal("HUD: ").append(Component.literal(nameOf(panel())).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(listOpen ? "  ^" : "  v").withStyle(ChatFormatting.GRAY)), b -> { listOpen = !listOpen; rebuildWidgets(); })
                .bounds(left, top, COL_W, H).tooltip(Tooltip.create(Component.literal("Pick the HUD to edit"))).build());
        int y = top + ROW;
        if (listOpen) {
            // two columns, by name, so every HUD fits on screen
            int half = (panels.size() + 1) / 2, colW = (COL_W - 2) / 2;
            for (int i = 0; i < panels.size(); i++) {
                int idx = i;
                Component label = Component.literal(nameOf(panels.get(i)));
                if (idx == Math.floorMod(selected, panels.size())) label = label.copy().withStyle(ChatFormatting.AQUA);
                this.addRenderableWidget(Button.builder(label, b -> { selected = idx; listOpen = false; lineScroll = 0; rebuildWidgets(); })
                        .bounds(left + (i < half ? 0 : colW + 2), y + (i % half) * 17, colW, 16).build());
            }
            y += half * 17;
            done(y + 6);
            return;
        }

        int tabW = (COL_W - 4) / 3;
        boolean lookTab = !linesTab && !imageTab;
        this.addRenderableWidget(Button.builder(Component.literal(lookTab ? "§lLook" : "Look"), b -> { linesTab = false; imageTab = false; rebuildWidgets(); })
                .bounds(left, y, tabW, H).build());
        this.addRenderableWidget(Button.builder(Component.literal(linesTab ? "§lLines" : "Lines"), b -> { linesTab = true; imageTab = false; rebuildWidgets(); })
                .bounds(left + tabW + 2, y, tabW, H).build());
        this.addRenderableWidget(Button.builder(Component.literal(imageTab ? "§lImage" : "Image"), b -> { imageTab = true; linesTab = false; rebuildWidgets(); })
                .bounds(left + (tabW + 2) * 2, y, COL_W - (tabW + 2) * 2, H).build());
        y += ROW + 2;
        if (imageTab) {
            buildImage(y);
            return;
        }

        titleRowY = -1;
        if (linesTab) {
            y += 12;
            buildLines(y);
            done(Math.max(y + 9 * 14 + 8, y + 8));
            return;
        }
        HudLooks.Look l = look();
        net.minecraft.client.gui.components.EditBox name = new net.minecraft.client.gui.components.EditBox(font, left + 64, y, COL_W - 64, H, Component.literal("Title"));
        name.setMaxLength(32);
        name.setValue(l.title == null ? "" : l.title);
        name.setHint(Component.literal(panel().titleText().getString()).withStyle(ChatFormatting.DARK_GRAY));
        name.setResponder(t -> { l.title = t.strip(); changed(); });
        name.setTooltip(Tooltip.create(Component.literal("Call this HUD anything you like (empty: its own name)")));
        this.addRenderableWidget(name);
        titleRowY = y;
        y += ROW;
        me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean shown = showSetting(panel().lookId());
        if (shown != null) {
            this.addRenderableWidget(Button.builder(Component.literal("Shown: ").append(Component.literal(shown.get() ? "On" : "Off").withStyle(shown.get() ? ChatFormatting.GREEN : ChatFormatting.RED)),
                    b -> { shown.accept(!shown.get()); dannypx.foe.config.Configs.hudConfig.save(); rebuildWidgets(); }).bounds(left + COL_W / 2 + 1, y, COL_W / 2 - 1, H)
                    .tooltip(Tooltip.create(Component.literal("Turn this HUD on or off"))).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("Layout: ").append(Component.literal(l.columns ? "Columns" : "Lines").withStyle(ChatFormatting.AQUA)),
                b -> { l.columns = !l.columns; changed(); rebuildWidgets(); }).bounds(left, y, shown != null ? COL_W / 2 - 1 : COL_W, H)
                .tooltip(Tooltip.create(Component.literal("Columns: names on the left, numbers lined up on the right"))).build());
        y += ROW;
        this.addRenderableWidget(Button.builder(Component.literal("Style: ").append(Component.literal(styleName(l.style)).withStyle(ChatFormatting.AQUA)),
                b -> { l.style = nextStyle(l.style); changed(); rebuildWidgets(); }).bounds(left, y, COL_W / 2 - 1, H)
                .tooltip(Tooltip.create(Component.literal("This HUD's own style. Default: the one picked for every HUD in Customize → HUD Style (now " + dannypx.foe.config.Configs.hudConfig.hudStyle.get().displayName() + ")"))).build());
        this.addRenderableWidget(Button.builder(Component.literal("Border: ").append(Component.literal(l.border ? "On" : "Off").withStyle(l.border ? ChatFormatting.GREEN : ChatFormatting.GRAY)),
                b -> { l.border = !l.border; changed(); rebuildWidgets(); }).bounds(left + COL_W / 2 + 1, y, COL_W / 2 - 1, H)
                .tooltip(Tooltip.create(Component.literal("Off: just the box, with its title as the first line"))).build());
        y += ROW;
        slider(left, y, "Line spacing", 0, 8, () -> l.lineSpacing, v -> l.lineSpacing = v, "");
        y += ROW;
        slider(left, y, "Padding", 0, 10, () -> l.padding, v -> l.padding = v, "");
        y += ROW;
        int panelOpacity = panel().defaultOpacity();
        slider(left, y, "Box opacity", 0, 100, () -> l.opacity >= 0 ? l.opacity : panelOpacity, v -> l.opacity = v, "%");
        y += ROW;
        for (int row = 0; row < 3; row++) {
            int[] colors = row == 0 ? BOX_COLORS : row == 1 ? BORDER_COLORS : TITLE_COLORS;
            for (int i = 0; i < colors.length; i++) swatches.add(new int[]{left + 64 + i * (SW + 2), y + 3, row, i});
            y += ROW - 4;
        }
        y += 4;
        this.addRenderableWidget(Button.builder(Component.literal("Text: ").append(Component.literal(l.textAlign().displayName()).withStyle(ChatFormatting.AQUA)),
                b -> { l.align = l.textAlign().next().name(); changed(); rebuildWidgets(); }).bounds(left, y, COL_W, H).build());
        y += ROW;
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> { stepPicture(-1); rebuildWidgets(); }).bounds(left, y, 20, H).build());
        this.addRenderableWidget(Button.builder(Component.literal("Title icon: ").append(Component.literal(pictureName()).withStyle(ChatFormatting.AQUA)),
                b -> { stepPicture(1); rebuildWidgets(); }).bounds(left + 22, y, COL_W - 44, H)
                .tooltip(Tooltip.create(Component.literal("A small item icon by the title. For your own picture or GIF, use the Image tab"))).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> { stepPicture(1); rebuildWidgets(); }).bounds(left + COL_W - 20, y, 20, H).build());
        y += ROW;
        // your own picture or GIF: straight to your files, then the Image tab to size and place it
        String img = l.image == null || l.image.isEmpty() ? null : l.image;
        this.addRenderableWidget(Button.builder(Component.literal(img == null ? "§a+ Add your own image / GIF..." : "§aYour image: §f" + shorten(img, 16) + " §7(edit)"),
                b -> { imageTab = true; linesTab = false; rebuildWidgets(); if (img == null) choosePicture(); }).bounds(left, y, COL_W, H)
                .tooltip(Tooltip.create(Component.literal("Pick a PNG or GIF from anywhere on your computer, then size it, place it anywhere in the box, or fill the whole box with it"))).build());
        y += ROW;
        done(y + 6);
    }

    /// The Image tab: pick your own picture or GIF, then size it, place it, and choose how solid it is.
    private void buildImage(int y) {
        HudLooks.Look l = look();
        this.addRenderableWidget(Button.builder(Component.literal("§a+ Choose an image or GIF..."), b -> choosePicture())
                .bounds(left, y, COL_W, H).tooltip(Tooltip.create(Component.literal("Opens your files: pick a PNG or GIF from anywhere on your computer"))).build());
        y += ROW;
        java.util.List<String> files = dannypx.foe.handler.store.HudImages.files();
        String now = l.image == null || l.image.isEmpty() ? "None" : l.image;
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> { stepImage(-1); rebuildWidgets(); }).bounds(left, y, 20, H).build());
        this.addRenderableWidget(Button.builder(Component.literal("Image: ").append(Component.literal(shorten(now, 18)).withStyle(ChatFormatting.AQUA)),
                b -> { stepImage(1); rebuildWidgets(); }).bounds(left + 22, y, COL_W - 44, H)
                .tooltip(Tooltip.create(Component.literal(files.isEmpty() ? "No pictures yet: choose one above" : files.size() + " picture(s) chosen before"))).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> { stepImage(1); rebuildWidgets(); }).bounds(left + COL_W - 20, y, 20, H).build());
        y += ROW;
        this.addRenderableWidget(Button.builder(Component.literal("Fill the box: ").append(Component.literal(l.imageFill ? "On" : "Off").withStyle(l.imageFill ? ChatFormatting.GREEN : ChatFormatting.GRAY)),
                b -> { l.imageFill = !l.imageFill; changed(); rebuildWidgets(); }).bounds(left, y, COL_W, H)
                .tooltip(Tooltip.create(Component.literal("On: the picture covers the whole box, as its background"))).build());
        y += ROW;
        this.addRenderableWidget(Button.builder(Component.literal("Layer: ").append(Component.literal(l.imageInFront ? "Over the text" : "Behind the text").withStyle(ChatFormatting.AQUA)),
                b -> { l.imageInFront = !l.imageInFront; changed(); rebuildWidgets(); }).bounds(left, y, COL_W, H).build());
        y += ROW;
        slider(left, y, "Size", 5, 100, () -> l.imageSize, v -> l.imageSize = v, "%");
        y += ROW;
        slider(left, y, "Across", 0, 100, () -> l.imageX, v -> l.imageX = v, "%");
        y += ROW;
        slider(left, y, "Down", 0, 100, () -> l.imageY, v -> l.imageY = v, "%");
        y += ROW;
        slider(left, y, "Opacity", 0, 100, () -> l.imageOpacity, v -> l.imageOpacity = v, "%");
        y += ROW;
        this.addRenderableWidget(Button.builder(Component.literal("Open the pictures folder"), b -> {
                    try {
                        java.nio.file.Files.createDirectories(dannypx.foe.handler.store.HudImages.folder());
                        net.minecraft.util.Util.getPlatform().openPath(dannypx.foe.handler.store.HudImages.folder());
                    } catch (Exception ignored) {
                    }
                }).bounds(left, y, COL_W, H).build());
        y += ROW;
        done(y + 6);
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private void stepImage(int d) {
        HudLooks.Look l = look();
        java.util.List<String> options = new java.util.ArrayList<>();
        options.add("");
        options.addAll(dannypx.foe.handler.store.HudImages.files());
        int at = Math.max(0, options.indexOf(l.image == null ? "" : l.image));
        l.image = options.get(Math.floorMod(at + d, options.size()));
        changed();
    }

    /// Opens your computer's own file window (PNG and GIF), then copies the picture you pick into FOE+'s pictures
    /// folder (so it keeps working wherever the original goes) and puts it in this HUD.
    private void choosePicture() {
        String panelId = panel().lookId();
        Thread t = new Thread(() -> {
            String picked;
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                org.lwjgl.PointerBuffer filters = stack.mallocPointer(4);
                filters.put(stack.UTF8("*.png")).put(stack.UTF8("*.gif")).put(stack.UTF8("*.PNG")).put(stack.UTF8("*.GIF")).flip();
                picked = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("Choose a picture or GIF for the HUD", null, filters, "Pictures (PNG, GIF)", false);
            } catch (Throwable e) {
                picked = null;
            }
            if (picked == null || picked.isBlank()) return;
            String name;
            try {
                name = dannypx.foe.handler.store.HudImages.copyIn(java.nio.file.Path.of(picked));
            } catch (Exception e) {
                dannypx.foe.handler.logic.LoggerHandler.error(e);
                return;
            }
            String chosen = name;
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                HudLooks.Look look = HudLooks.edit(panelId);
                look.image = chosen;
                HudLooks.save();
                if (net.minecraft.client.Minecraft.getInstance().screen == this) rebuildWidgets();
            });
        }, "FOE+ picture chooser");
        t.setDaemon(true);
        t.start();
    }

    /// The on/off setting of the HUD with this look ("ContestPanelElement" → showContestPanel), or null.
    /// Package-private for tests.
    static me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean showSetting(String lookId) {
        String name = "GearWarningPanelElement".equals(lookId) ? "showGearWarning"
                : "show" + lookId.replaceFirst("Element$", "");
        try {
            Object f = dannypx.foe.config.Configs.hudConfig.getClass().getField(name).get(dannypx.foe.config.Configs.hudConfig);
            return f instanceof me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean v ? v : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /// "Default (FOE+)", "FOE+" or "FOE-R".
    static String styleName(String style) {
        if ("FOE_R".equals(style)) return "FOE-R";
        if ("FOE_PLUS".equals(style)) return "FOE+";
        return "Default";
    }

    /// Default → FOE+ → FOE-R → Default.
    static String nextStyle(String style) {
        if ("FOE_PLUS".equals(style)) return "FOE_R";
        if ("FOE_R".equals(style)) return "DEFAULT";
        return "FOE_PLUS";
    }

    private void done(int y) {
        this.addRenderableWidget(Button.builder(Component.literal("Reset this HUD"), b -> { HudLooks.reset(panel().lookId()); rebuildWidgets(); })
                .bounds(left, y, COL_W / 2 - 1, H).tooltip(Tooltip.create(Component.literal("Back to how it looked before you edited it"))).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(left + COL_W / 2 + 1, y, COL_W / 2 - 1, H).build());
    }

    private void buildLines(int y) {
        lineKeys.clear();
        lineTexts.clear();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Component c : panel().allLinesForEditor()) {
            if (c.getString().isBlank()) continue;
            String k = HudLooks.key(c.getString());
            if (seen.add(k)) { lineKeys.add(k); lineTexts.add(c); }
        }
        for (String k : look().hidden) if (seen.add(k)) { lineKeys.add(k); lineTexts.add(Component.literal(k)); }
        int shown = Math.min(9, lineKeys.size());
        lineScroll = Math.max(0, Math.min(lineScroll, lineKeys.size() - shown));
        for (int i = 0; i < shown; i++) lineRows.add(new int[]{left, y + i * 14, COL_W});
    }

    private void slider(int x, int y, String label, int min, int max, Supplier<Integer> get, IntConsumer set, String unit) {
        int value = Math.max(min, Math.min(max, get.get()));
        this.addRenderableWidget(new AbstractSliderButton(x, y, COL_W, H, Component.empty(), (value - min) / (double) (max - min)) {
            { updateMessage(); }
            private int v() { return (int) Math.round(min + this.value * (max - min)); }
            @Override protected void updateMessage() { setMessage(Component.literal(label + ": ").append(Component.literal(v() + unit).withStyle(ChatFormatting.AQUA))); }
            @Override protected void applyValue() { set.accept(v()); changed(); }
        });
    }

    private void changed() {
        HudLooks.save();
    }

    private String pictureName() {
        String id = look().picture;
        if (id == null || id.isEmpty()) return "None";
        Identifier key = Identifier.tryParse(id);
        return key == null ? "None" : BuiltInRegistries.ITEM.getOptional(key).map(i -> new ItemStack(i).getHoverName().getString()).orElse("None");
    }

    private void stepPicture(int d) {
        HudLooks.Look l = look();
        int at = 0;
        for (int i = 0; i < PICTURES.length; i++) {
            if (PICTURES[i] != Items.AIR && BuiltInRegistries.ITEM.getKey(PICTURES[i]).toString().equals(l.picture)) at = i;
        }
        Item next = PICTURES[Math.floorMod(at + d, PICTURES.length)];
        l.picture = next == Items.AIR ? "" : BuiltInRegistries.ITEM.getKey(next).toString();
        changed();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        for (int[] s : swatches) {
            if (mx >= s[0] && mx < s[0] + SW && my >= s[1] && my < s[1] + SW) {
                HudLooks.Look l = look();
                if (s[2] == 0) l.boxColor = BOX_COLORS[s[3]];
                else if (s[2] == 1) l.borderColor = BORDER_COLORS[s[3]];
                else l.titleColor = TITLE_COLORS[s[3]];
                changed();
                return true;
            }
        }
        for (int i = 0; i < lineRows.size(); i++) {
            int[] r = lineRows.get(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + 13) {
                String k = lineKeys.get(i + lineScroll);
                List<String> hidden = look().hidden;
                if (!hidden.remove(k)) hidden.add(k);
                changed();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (linesTab && !listOpen && lineKeys.size() > 9) {
            lineScroll = Math.max(0, Math.min(lineKeys.size() - 9, lineScroll + (v > 0 ? -1 : 1)));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, Component.literal("HUD EDITOR").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), width / 2, 8, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.literal("Only FOE+'s own HUDs. Changes save as you make them.").withStyle(ChatFormatting.GRAY), width / 2, 18, 0xFFFFFFFF);
        if (listOpen) return;

        HudLooks.Look l = look();
        if (!linesTab) {
            if (titleRowY >= 0) g.drawString(font, Component.literal("Name").withStyle(ChatFormatting.GRAY), left, titleRowY + 6, 0xFFFFFFFF, true);
            String[] names = {"Box colour", "Border", "Title"};
            for (int[] s : swatches) {
                if (s[3] == 0) g.drawString(font, Component.literal(names[s[2]]).withStyle(ChatFormatting.GRAY), left, s[1] + 3, 0xFFFFFFFF, true);
                int c = s[2] == 0 ? BOX_COLORS[s[3]] : s[2] == 1 ? BORDER_COLORS[s[3]] : TITLE_COLORS[s[3]];
                boolean on = s[2] == 0 ? l.boxColor == c : s[2] == 1 ? l.borderColor == c : l.titleColor == c;
                g.fill(s[0] - 1, s[1] - 1, s[0] + SW + 1, s[1] + SW + 1, on ? 0xFFFFFFFF : 0xFF3A3A3A);
                if (c < 0) {
                    g.fill(s[0], s[1], s[0] + SW, s[1] + SW, 0xFF202020);
                    g.drawCenteredString(font, "A", s[0] + SW / 2, s[1] + 3, 0xFFB0B0B0);
                } else g.fill(s[0], s[1], s[0] + SW, s[1] + SW, 0xFF000000 | c);
                if (s[2] == 1 && s[3] == 0 || s[2] == 2 && s[3] == 0) {
                    if (mouseX >= s[0] && mouseX < s[0] + SW && mouseY >= s[1] && mouseY < s[1] + SW)
                        g.setTooltipForNextFrame(font, Component.literal(s[2] == 1 ? "The border's own colours" : "The title's own colour"), mouseX, mouseY);
                }
            }
        } else {
            g.drawString(font, Component.literal("Click a line to show or hide it").withStyle(ChatFormatting.GRAY), left, lineRows.isEmpty() ? top + ROW * 2 + 4 : lineRows.getFirst()[1] - 12, 0xFFFFFFFF, true);
            for (int i = 0; i < lineRows.size(); i++) {
                int[] r = lineRows.get(i);
                String k = lineKeys.get(i + lineScroll);
                boolean hidden = l.hidden.contains(k);
                boolean hot = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + 13;
                g.fill(r[0], r[1], r[0] + r[2], r[1] + 13, hot ? 0x40FFFFFF : 0x20FFFFFF);
                g.drawString(font, hidden ? "☐" : "☑", r[0] + 3, r[1] + 3, hidden ? 0xFF777777 : 0xFF6BE36B, false);
                Component text = lineTexts.get(i + lineScroll);
                String plain = font.plainSubstrByWidth(text.getString(), r[2] - 18);
                g.drawString(font, hidden ? Component.literal(plain).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH)
                        : (plain.length() < text.getString().length() ? Component.literal(plain) : text), r[0] + 14, r[1] + 3, 0xFFFFFFFF, true);
            }
            if (lineKeys.size() > 9) g.drawString(font, Component.literal("Scroll for more").withStyle(ChatFormatting.DARK_GRAY), left, lineRows.getLast()[1] + 15, 0xFFFFFFFF, false);
        }

        // The panel as it looks now
        g.drawString(font, Component.literal("Preview").withStyle(ChatFormatting.GRAY), previewX, previewY - 2, 0xFFFFFFFF, true);
        PanelElement p = panel();
        p.alignment = Alignment.TOP_LEFT;
        p.xPos = (previewX - 4) / (float) width;
        p.yPos = (previewY + 10) / (float) height;
        p.render(g);
    }

    @Override
    public void onClose() {
        HudLooks.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
