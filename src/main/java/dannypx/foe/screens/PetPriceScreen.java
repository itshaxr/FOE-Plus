package dannypx.foe.screens;

import dannypx.foe.handler.logic.InventoryHandler;
import dannypx.foe.handler.store.PetPrices;
import dannypx.foe.item.PetTagObject;
import dannypx.foe.screens.element.hud.PetPanelElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Pet Prices (O → Tools, or U): your price table. Rating bands down the side with a Common's price (low, and high for
 * a range), what that comes to for each rarity across, then the rarity multipliers and the Max Luck / Max Scale
 * bonuses. Click any number to change it (Enter or a click elsewhere keeps it; Esc puts it back). Your equipped pet's
 * band and price are lit up, with its price worked out at the bottom.
 */
public class PetPriceScreen extends Screen {
    /// The menu is on (the U key and the O menu's button check it).
    public static final boolean ENABLED = true;

    private static final int BG = 0xFF0A1420, PANEL = 0xE0081320, RIM = 0xFF2B4E6E, RULE = 0xFF1E3550,
            FIELD_EDGE = 0xFF22405E, FIELD = 0xFF081320, TITLE = 0xFFEAF4FF, TEXT = 0xFFC9D7E6, DIM = 0xFF7F95AB,
            GOLD = 0xFFFFD866;
    private static final String[] BADGES = {"", "", "", "", ""};
    private static final int ROW_H = 16, FIELD_W = 52;

    private final Screen parent;
    private EditBox editor;
    /// What the editor is changing: "low:3", "high:3", "mult:2", "luck", "scale"; null when it's closed.
    private String editing;
    private double scroll;
    private int petRow = -1, petRarity = -1;
    private boolean petLuck, petScale;

    public PetPriceScreen(Screen parent) {
        super(Component.literal("Pet Prices"));
        this.parent = parent;
    }

    //region Layout
    private int panelW() { return Math.min(width - 20, 720); }
    private int panelH() { return height - 20; }
    private int px() { return (width - panelW()) / 2; }
    private int py() { return 10; }
    private int tx() { return px() + 14; }
    private int bodyTop() { return py() + 60; }
    private int bodyBottom() { return py() + panelH() - 116; }
    private int colX(int rarity) {
        int start = tx() + 196;
        int each = (px() + panelW() - 14 - start) / PetPrices.RARITIES.length;
        return start + rarity * each;
    }
    private int lowX() { return tx() + 62; }
    private int highX() { return tx() + 126; }
    private double maxScroll() { return Math.max(0, PetPrices.rows().size() * ROW_H - (bodyBottom() - bodyTop())); }
    private int rowY(int i) { return bodyTop() + i * ROW_H - (int) scroll; }
    private int multY() { return bodyBottom() + 8; }
    private int bonusY() { return bodyBottom() + 28; }
    private int petY() { return bodyBottom() + 48; }
    //endregion

    @Override
    protected void init() {
        editor = new EditBox(font, 0, 0, FIELD_W, 13, Component.literal("Price"));
        editor.setMaxLength(12);
        editor.setBordered(false);
        editor.setFilter(t -> t.matches("[0-9.,kKmMbB]*"));
        editor.visible = false;
        addRenderableWidget(editor);
        addRenderableWidget(Button.builder(Component.literal("Reset to defaults"), b -> {
            close(false);
            PetPrices.reset();
        }).bounds(px() + panelW() - 236, py() + panelH() - 26, 106, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(px() + panelW() - 124, py() + panelH() - 26, 110, 18).build());
        readPet();
        if (petRow >= 0) scroll = Math.max(0, Math.min(maxScroll(), petRow * ROW_H - (bodyBottom() - bodyTop()) / 2.0));
    }

    private void readPet() {
        petRow = petRarity = -1;
        petLuck = petScale = false;
        try {
            if (!InventoryHandler.instance().hasPet()) return;
            PetTagObject pet = InventoryHandler.instance().getCurrentPet();
            petRow = PetPrices.rowFor(pet.getTotalPercent() * 100.0);
            petRarity = PetPrices.rarityIndex(PetPanelElement.rarityOf(pet));
            boolean[] max = PetPanelElement.maxedLuckScale(pet);
            petLuck = max[0];
            petScale = max[1];
        } catch (RuntimeException ignored) {
            // no pet to read
        }
    }

    //region Editing
    /// The value a cell holds, as typed.
    private String valueOf(String key) {
        String[] k = key.split(":");
        return switch (k[0]) {
            case "low" -> PetPrices.format(PetPrices.rows().get(Integer.parseInt(k[1])).low);
            case "high" -> {
                long h = PetPrices.rows().get(Integer.parseInt(k[1])).high;
                yield h > 0 ? PetPrices.format(h) : "";
            }
            case "mult" -> String.valueOf(PetPrices.multiplier(Integer.parseInt(k[1])));
            case "luck" -> PetPrices.format(PetPrices.maxLuckBonus());
            case "scale" -> PetPrices.format(PetPrices.maxScaleBonus());
            default -> "";
        };
    }

    private void open(String key, int x, int y) {
        close(true);
        editing = key;
        editor.setX(x + 4);
        editor.setY(y + 3);
        editor.setValue(valueOf(key));
        editor.visible = true;
        setFocused(editor);
        editor.setFocused(true);
        editor.moveCursorToEnd(false);
    }

    /// Keeps (or, `keep` false, drops) what's typed and closes the editor.
    private void close(boolean keep) {
        if (editing == null) return;
        String key = editing;
        editing = null;
        editor.visible = false;
        editor.setFocused(false);
        if (!keep) return;
        String typed = editor.getValue().trim();
        long v = PetPrices.parse(typed);
        String[] k = key.split(":");
        switch (k[0]) {
            case "low" -> { if (v >= 0) PetPrices.rows().get(Integer.parseInt(k[1])).low = v; }
            case "high" -> PetPrices.rows().get(Integer.parseInt(k[1])).high = typed.isEmpty() ? 0 : Math.max(0, v);
            case "mult" -> { if (v > 0) PetPrices.setMultiplier(Integer.parseInt(k[1]), v); }
            case "luck" -> { if (v >= 0) PetPrices.setMaxLuckBonus(v); }
            case "scale" -> { if (v >= 0) PetPrices.setMaxScaleBonus(v); }
            default -> { }
        }
        PetPrices.save();
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
        if (editing != null && editor.isMouseOver(mx, my)) return super.mouseClicked(event, doubled);
        List<PetPrices.Row> rows = PetPrices.rows();
        if (my >= bodyTop() - 2 && my < bodyBottom()) {
            for (int i = 0; i < rows.size(); i++) {
                int y = rowY(i);
                if (in(mx, my, lowX(), y - 1, FIELD_W, 13)) { open("low:" + i, lowX(), y - 1); return true; }
                if (in(mx, my, highX(), y - 1, FIELD_W, 13)) { open("high:" + i, highX(), y - 1); return true; }
            }
        }
        for (int r = 0; r < PetPrices.RARITIES.length; r++) {
            if (in(mx, my, colX(r), multY() - 1, 40, 13)) { open("mult:" + r, colX(r), multY() - 1); return true; }
        }
        if (in(mx, my, tx() + 92, bonusY() - 1, FIELD_W, 13)) { open("luck", tx() + 92, bonusY() - 1); return true; }
        if (in(mx, my, tx() + 344, bonusY() - 1, FIELD_W, 13)) { open("scale", tx() + 344, bonusY() - 1); return true; }
        close(true);
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (editing != null) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) { close(true); return true; }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { close(false); return true; }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (my >= bodyTop() && my < bodyBottom()) {
            close(true);
            scroll = Math.max(0, Math.min(maxScroll(), scroll - v * ROW_H * 2));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }
    //endregion

    private static void box(GuiGraphics g, int x, int y, int w, int h, int edge, int fill) {
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private void field(GuiGraphics g, String key, String text, int x, int y, int w) {
        boolean open = key.equals(editing);
        box(g, x, y, w, 13, open ? GOLD : FIELD_EDGE, FIELD);
        if (!open) g.drawString(font, text, x + 4, y + 3, TITLE, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, BG);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        int X = px(), Y = py(), W = panelW(), H = panelH(), tx = tx();
        box(g, X, Y, W, H, RIM, PANEL);
        g.drawString(font, Component.literal("Pet Prices").withStyle(ChatFormatting.BOLD), X + 12, Y + 10, TITLE, true);
        g.drawString(font, "Roughly what a pet sells for, by its rating and rarity (an estimate). Click any number to change it.", X + 12, Y + 24, DIM, false);
        g.fill(X + 10, Y + 36, X + W - 10, Y + 37, RULE);

        // the header
        int hy = Y + 44;
        g.drawString(font, "ʀᴀᴛɪɴɢ", tx, hy, DIM, false);
        g.drawString(font, "ᴄᴏᴍᴍᴏɴ ᴘʀɪᴄᴇ (ʟᴏᴡ – ʜɪɢʜ)", lowX(), hy, DIM, false);
        for (int r = 0; r < BADGES.length; r++) g.drawString(font, BADGES[r], colX(r), hy, 0xFFFFFFFF, false);

        // the bands (scrolling)
        List<PetPrices.Row> rows = PetPrices.rows();
        int top = bodyTop(), bottom = bodyBottom();
        g.enableScissor(X + 2, top - 2, X + W - 2, bottom);
        for (int i = 0; i < rows.size(); i++) {
            int y = rowY(i);
            if (y + ROW_H < top - 2 || y > bottom) continue;
            PetPrices.Row row = rows.get(i);
            if (i % 2 == 0) g.fill(tx - 4, y - 2, X + W - 18, y + ROW_H - 2, 0x18FFFFFF);
            if (i == petRow) g.renderOutline(tx - 4, y - 2, X + W - 14 - tx, ROW_H, GOLD);
            g.drawString(font, row.label(), tx, y + 2, 0xFFFFFFFF, true);
            field(g, "low:" + i, PetPrices.format(row.low), lowX(), y - 1, FIELD_W);
            g.drawString(font, "–", lowX() + FIELD_W + 3, y + 2, DIM, false);
            field(g, "high:" + i, row.high > 0 ? PetPrices.format(row.high) : "", highX(), y - 1, FIELD_W);
            for (int r = 0; r < PetPrices.RARITIES.length; r++) {
                String v = PetPrices.format(PetPrices.price(i, r, false, false));
                boolean yours = i == petRow && r == petRarity;
                g.drawString(font, v, colX(r), y + 2, yours ? GOLD : TEXT, false);
            }
        }
        g.disableScissor();
        // the scrollbar
        double max = maxScroll();
        if (max > 0) {
            int trackX = X + W - 10, trackH = bottom - top;
            int thumbH = Math.max(16, (int) (trackH * (trackH / (double) (rows.size() * ROW_H))));
            int thumbY = top + (int) ((trackH - thumbH) * (scroll / max));
            g.fill(trackX, top, trackX + 4, bottom, 0x30FFFFFF);
            g.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, 0xA0FFFFFF);
        }
        g.fill(X + 10, bottom + 2, X + W - 10, bottom + 3, RULE);

        // multipliers and bonuses
        g.drawString(font, "ʀᴀʀɪᴛʏ ×", tx, multY() + 2, DIM, false);
        for (int r = 0; r < PetPrices.RARITIES.length; r++) field(g, "mult:" + r, "×" + PetPrices.multiplier(r), colX(r), multY() - 1, 40);
        g.drawString(font, Component.literal("♣ ").withColor(0x55FF55).append(Component.literal("Max Luck adds").withColor(TEXT & 0xFFFFFF)), tx, bonusY() + 2, -1, false);
        field(g, "luck", PetPrices.format(PetPrices.maxLuckBonus()), tx + 92, bonusY() - 1, FIELD_W);
        g.drawString(font, Component.literal("⚓ ").withColor(0x4C88F1).append(Component.literal("Max Scale adds").withColor(TEXT & 0xFFFFFF)), tx + 250, bonusY() + 2, -1, false);
        field(g, "scale", PetPrices.format(PetPrices.maxScaleBonus()), tx + 344, bonusY() - 1, FIELD_W);

        // your pet
        int y = petY();
        boolean priced = petRow >= 0 && petRarity >= 0;
        box(g, tx - 4, y, W - 20, 22, priced ? GOLD : FIELD_EDGE, priced ? 0x30FFD866 : 0x20FFFFFF);
        g.drawString(font, yourPet(), tx + 2, y + 7, 0xFFFFFFFF, true);

        // an estimate, not a fact: said plainly, in yellow, where every price is
        g.drawString(font, Component.literal("⚠ ").withColor(0xFFC93C).append(Component.literal("Estimates only, not exact: real prices change with events and what people will pay.").withColor(0xE8D08A)),
                X + 14, petY() + 27, -1, false);
        super.render(g, mouseX, mouseY, delta);
    }

    /// "Your pet: [LEGENDARY] Duck Pet  82%  ♣ max luck  →  850K–3.65M".
    private Component yourPet() {
        try {
            if (!InventoryHandler.instance().hasPet()) return Component.literal("Equip a pet to see its price here.").withColor(DIM & 0xFFFFFF);
            PetTagObject pet = InventoryHandler.instance().getCurrentPet();
            var line = Component.literal("Your pet: ").withStyle(ChatFormatting.GRAY)
                    .append(pet.getRarityComponent()).append(Component.literal(" "))
                    .append(pet.getName())
                    .append(Component.literal("  " + (int) Math.floor(pet.getTotalPercent() * 100 + 1e-6) + "%").withStyle(ChatFormatting.WHITE));
            if (petLuck) line.append(Component.literal("  ♣ max luck").withColor(0x55FF55));
            if (petScale) line.append(Component.literal("  ⚓ max scale").withColor(0x4C88F1));
            if (petRow < 0 || petRarity < 0) return line.append(Component.literal("  (no price for it in the table)").withStyle(ChatFormatting.GRAY));
            return line.append(Component.literal("  →  ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(PetPrices.format(PetPrices.price(petRow, petRarity, petLuck, petScale))).withStyle(ChatFormatting.GREEN));
        } catch (RuntimeException e) {
            return Component.literal("Equip a pet to see its price here.").withColor(DIM & 0xFFFFFF);
        }
    }

    @Override
    public void onClose() {
        close(true);
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
