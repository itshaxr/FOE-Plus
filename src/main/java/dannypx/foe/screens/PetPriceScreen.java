package dannypx.foe.screens;

import dannypx.foe.handler.logic.InventoryHandler;
import dannypx.foe.handler.logic.PetPriceSheetHandler;
import dannypx.foe.item.PetTagObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Pet price menu (U): the price sheet as a table — rating bands down the side, rarities across —
 * with a fixed header, alternating row shading, a scrollbar, and your equipped pet's row and
 * price cell highlighted. Type a rating in the box to jump to (and highlight) that row.
 */
public class PetPriceScreen extends Screen {
    private static final int ROW_HEIGHT = 14;
    private static final int HEADER_HEIGHT = 16;
    private static final int SCROLLBAR_WIDTH = 6;

    private final Screen parent;
    private EditBox ratingBox;
    private double scroll = 0;
    private boolean draggingScrollbar = false;
    private int highlightRow = -1;
    private int highlightColumn = -1;

    public PetPriceScreen(Screen parent) {
        super(Component.literal("Pet Prices"));
        this.parent = parent;
    }

    //region Layout
    private int tableLeft() { return Math.max(10, this.width / 2 - tableWidth() / 2); }
    private int tableTop() { return 58; }
    private int tableBottom() { return this.height - 34; }
    private int visibleHeight() { return tableBottom() - tableTop() - HEADER_HEIGHT; }

    private static final int CELL_PADDING = 12;

    /// Each column's natural width: its widest cell or bold header, plus padding.
    private int[] naturalWidths() {
        PetPriceSheetHandler.Table table = PetPriceSheetHandler.instance().getTable();
        int count = table.headers().size();
        int[] widths = new int[count];
        for (int c = 0; c < count; c++) {
            if (c == table.notesColumn()) {
                widths[c] = 24; // notes show as an "ⓘ" with a hover tooltip
                continue;
            }
            int w = this.font.width(Component.literal(table.headers().get(c)).withStyle(ChatFormatting.BOLD));
            for (List<String> row : table.rows()) {
                if (c < row.size()) w = Math.max(w, this.font.width(row.get(c)));
            }
            widths[c] = w + CELL_PADDING;
        }
        return widths;
    }

    private int tableWidth() {
        int natural = SCROLLBAR_WIDTH + 4;
        for (int w : naturalWidths()) natural += w;
        return Math.min(this.width - 20, Math.max(natural, 360));
    }

    /// Natural widths, spread out to fill the table, or shrunk (widest columns first) on small screens.
    private int[] columnWidths() {
        int[] widths = naturalWidths();
        if (widths.length == 0) return widths;
        int notes = PetPriceSheetHandler.instance().getTable().notesColumn();
        int available = tableWidth() - SCROLLBAR_WIDTH - 4;
        int total = 0;
        for (int w : widths) total += w;

        if (total < available) {
            int extra = available - total;
            int flexible = widths.length - (notes >= 0 ? 1 : 0);
            for (int c = 0; c < widths.length; c++) {
                if (c != notes) widths[c] += extra / flexible;
            }
        } else {
            while (total > available) {
                int widest = 0;
                for (int c = 1; c < widths.length; c++) if (widths[c] > widths[widest]) widest = c;
                if (widths[widest] <= 30) break;
                widths[widest]--;
                total--;
            }
        }
        return widths;
    }

    private int contentHeight() {
        return PetPriceSheetHandler.instance().getTable().rows().size() * ROW_HEIGHT;
    }

    private double maxScroll() {
        return Math.max(0, contentHeight() - visibleHeight());
    }
    //endregion

    @Override
    protected void init() {
        PetPriceSheetHandler.instance().tick();

        ratingBox = new EditBox(this.font, tableLeft(), 32, 90, 18, Component.literal("Rating"));
        ratingBox.setMaxLength(6);
        ratingBox.setHint(Component.literal("Rating %").withStyle(ChatFormatting.DARK_GRAY));
        ratingBox.setFilter(text -> text.matches("[0-9.]*"));
        ratingBox.setResponder(text -> this.jumpToRating(text));
        this.addRenderableWidget(ratingBox);

        this.addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> PetPriceSheetHandler.instance().refreshNow())
                .bounds(tableLeft() + tableWidth() - 60, 31, 60, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 26, 100, 20).build());

        this.highlightEquippedPet();
    }

    /// Highlights (and scrolls to) the row and rarity cell for your equipped pet.
    private void highlightEquippedPet() {
        if (!InventoryHandler.instance().hasPet()) return;
        PetTagObject pet = InventoryHandler.instance().getCurrentPet();
        PetPriceSheetHandler sheet = PetPriceSheetHandler.instance();
        highlightRow = sheet.rowFor(pet.getTotalPercent() * 100.0);
        highlightColumn = sheet.columnFor(pet.getRarity());
        scrollToRow(highlightRow);
    }

    private void jumpToRating(String text) {
        if (text.isEmpty()) {
            highlightEquippedPet();
            return;
        }
        try {
            int row = PetPriceSheetHandler.instance().rowFor(Double.parseDouble(text));
            if (row >= 0) {
                highlightRow = row;
                scrollToRow(row);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void scrollToRow(int row) {
        if (row < 0) return;
        double target = row * ROW_HEIGHT - visibleHeight() / 2.0 + ROW_HEIGHT / 2.0;
        scroll = Math.max(0, Math.min(maxScroll(), target));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);

        PetPriceSheetHandler sheet = PetPriceSheetHandler.instance();
        PetPriceSheetHandler.Table table = sheet.getTable();
        int left = tableLeft();
        int width = tableWidth();

        guiGraphics.drawCenteredString(this.font, Component.literal("Pet Prices").withStyle(ChatFormatting.BOLD), this.width / 2, 10, 0xFFFFFFFF);
        guiGraphics.drawString(this.font, statusLine(sheet), left + 96, 37, 0xFFFFFFFF, true);

        if (table.headers().isEmpty()) {
            Component message = sheet.getLastError().isEmpty()
                    ? Component.literal("Loading price sheet...").withStyle(ChatFormatting.GRAY)
                    : Component.literal(sheet.getLastError()).withStyle(ChatFormatting.RED);
            guiGraphics.drawCenteredString(this.font, message, this.width / 2, this.height / 2, 0xFFFFFFFF);
            return;
        }

        int[] widths = columnWidths();
        int top = tableTop();
        int bodyTop = top + HEADER_HEIGHT;
        int bottom = tableBottom();

        // Background
        guiGraphics.fill(left, top, left + width, bottom, 0xC0101010);

        // Header
        guiGraphics.fill(left, top, left + width, bodyTop, 0xFF2A2A2A);
        int x = left + 2;
        for (int c = 0; c < widths.length; c++) {
            String header = c == table.notesColumn() ? "ⓘ" : table.headers().get(c);
            Component text = Component.literal(fit(header, widths[c] - 4)).withStyle(ChatFormatting.BOLD).withColor(headerColor(c, table));
            drawCell(guiGraphics, text, x, top + 4, widths[c], c == 0);
            x += widths[c];
        }

        // Rows (clipped to the table body)
        guiGraphics.enableScissor(left, bodyTop, left + width, bottom);
        List<List<String>> rows = table.rows();
        int hoveredRow = -1;
        String hoveredFullText = null;
        for (int r = 0; r < rows.size(); r++) {
            int y = bodyTop + r * ROW_HEIGHT - (int) scroll;
            if (y + ROW_HEIGHT < bodyTop || y > bottom) continue;

            boolean hovered = mouseY >= Math.max(y, bodyTop) && mouseY < Math.min(y + ROW_HEIGHT, bottom)
                    && mouseX >= left && mouseX < left + width - SCROLLBAR_WIDTH;
            if (hovered) hoveredRow = r;

            int shade = r == highlightRow ? 0x60FFAA00 : hovered ? 0x40FFFFFF : (r % 2 == 0 ? 0x20FFFFFF : 0x00000000);
            if (shade != 0) guiGraphics.fill(left, y, left + width - SCROLLBAR_WIDTH, y + ROW_HEIGHT, shade);

            List<String> row = rows.get(r);
            x = left + 2;
            for (int c = 0; c < widths.length; c++) {
                String value = c < row.size() ? row.get(c) : "";
                Component text;
                if (c == table.notesColumn()) {
                    text = Component.literal(value.isBlank() ? "" : "ⓘ").withStyle(ChatFormatting.AQUA);
                } else if (c == 0) {
                    text = Component.literal(value).withStyle(ChatFormatting.WHITE);
                } else {
                    boolean cellHighlight = r == highlightRow && c == highlightColumn;
                    String shown = fit(value, widths[c] - 4);
                    text = Component.literal(shown)
                            .withStyle(cellHighlight ? ChatFormatting.GOLD : ChatFormatting.WHITE);
                    if (hovered && !shown.equals(value) && mouseX >= x - 2 && mouseX < x - 2 + widths[c]) {
                        hoveredFullText = value;
                    }
                    if (cellHighlight) guiGraphics.fill(x - 2, y, x - 2 + widths[c], y + ROW_HEIGHT, 0x80FFAA00);
                }
                drawCell(guiGraphics, text, x, y + 3, widths[c], c == 0);
                x += widths[c];
            }
        }
        guiGraphics.disableScissor();

        // Scrollbar
        if (maxScroll() > 0) {
            int trackX = left + width - SCROLLBAR_WIDTH;
            int trackHeight = bottom - bodyTop;
            int thumbHeight = Math.max(16, (int) (trackHeight * (visibleHeight() / (double) contentHeight())));
            int thumbY = bodyTop + (int) ((trackHeight - thumbHeight) * (scroll / maxScroll()));
            guiGraphics.fill(trackX, bodyTop, trackX + SCROLLBAR_WIDTH, bottom, 0xFF202020);
            guiGraphics.fill(trackX + 1, thumbY, trackX + SCROLLBAR_WIDTH - 1, thumbY + thumbHeight, 0xFF909090);
        }

        // Hover: full text of a shortened cell, otherwise the row's note
        if (hoveredFullText != null) {
            guiGraphics.setTooltipForNextFrame(this.font, Component.literal(hoveredFullText), mouseX, mouseY);
        } else if (hoveredRow >= 0 && table.notesColumn() >= 0) {
            String note = rows.get(hoveredRow).size() > table.notesColumn() ? rows.get(hoveredRow).get(table.notesColumn()) : "";
            if (!note.isBlank()) {
                guiGraphics.setTooltipForNextFrame(this.font, this.font.split(Component.literal(note), 220), mouseX, mouseY);
            }
        }
    }

    private Component statusLine(PetPriceSheetHandler sheet) {
        if (InventoryHandler.instance().hasPet()) {
            PetTagObject pet = InventoryHandler.instance().getCurrentPet();
            String price = sheet.priceFor(pet);
            return Component.literal("Your pet: ").withStyle(ChatFormatting.GRAY)
                    .append(pet.getName())
                    .append(Component.literal(String.format(Locale.ROOT, " %.1f%%", pet.getTotalPercent() * 100)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(price == null ? "no price" : price).withStyle(ChatFormatting.GOLD));
        }
        if (sheet.getLastLoaded() > 0) {
            long minutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - sheet.getLastLoaded());
            return Component.literal("Updated " + (minutes == 0 ? "just now" : minutes + "m ago")).withStyle(ChatFormatting.DARK_GRAY);
        }
        return Component.empty();
    }

    private static int headerColor(int column, PetPriceSheetHandler.Table table) {
        String header = table.headers().get(column).toLowerCase(Locale.ROOT);
        if (header.startsWith("common")) return 0xFFFFFF;
        if (header.startsWith("rare")) return 0x2B85C4;
        if (header.startsWith("epic")) return 0x1CD832;
        if (header.startsWith("legendary")) return 0xD98103;
        if (header.startsWith("mythic")) return 0xC93832;
        return 0xDDDDDD;
    }

    private void drawCell(GuiGraphics guiGraphics, Component text, int x, int y, int width, boolean leftAlign) {
        int textX = leftAlign ? x + 2 : x + (width - 4) / 2 - this.font.width(text) / 2;
        guiGraphics.drawString(this.font, text, textX, y, 0xFFFFFFFF, true);
    }

    /// Shortens text with "…" to fit a column.
    private String fit(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) return text;
        String out = text;
        while (!out.isEmpty() && this.font.width(out + "…") > maxWidth) out = out.substring(0, out.length() - 1);
        return out + "…";
    }

    //region Scrolling
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - verticalAmount * ROW_HEIGHT * 2));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int trackX = tableLeft() + tableWidth() - SCROLLBAR_WIDTH;
        if (maxScroll() > 0 && event.x() >= trackX && event.x() < trackX + SCROLLBAR_WIDTH
                && event.y() >= tableTop() + HEADER_HEIGHT && event.y() < tableBottom()) {
            draggingScrollbar = true;
            dragTo(event.y());
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar) {
            dragTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    private void dragTo(double mouseY) {
        int bodyTop = tableTop() + HEADER_HEIGHT;
        double fraction = (mouseY - bodyTop) / (double) (tableBottom() - bodyTop);
        scroll = Math.max(0, Math.min(maxScroll(), fraction * maxScroll()));
    }
    //endregion

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
