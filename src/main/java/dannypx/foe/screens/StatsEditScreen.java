package dannypx.foe.screens;

import dannypx.foe.handler.logic.RareCatchHandler;
import dannypx.foe.handler.store.StatsDataHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Set your All-Time Stats by hand, in case something counted wrong: the totals, and how many of
 * each rarity, variant and Gigantic. Typing a number sets it straight away. Dry streaks don't
 * change (they're in Edit Dry Streaks); changing Fish Caught keeps every streak as it is.
 */
public class StatsEditScreen extends Screen {
    private static final int ROW = 22;
    private static final int H = 20;
    private static final int PAD = 6;
    private static final int LABEL_W = 66;
    private static final int BOX_W = 58;
    private static final int PANEL_W = LABEL_W + 4 + BOX_W;
    private static final int GAP = 8;
    private static final int COLUMNS = 3;

    private final Screen parent;
    private int top, tableY, panelBottom;
    private final int[] columnX = new int[COLUMNS];

    /// One editable total: its label, how to read it and how to set it.
    private record Field(Component label, IntSupplier get, IntConsumer set) {}

    private static StatsDataHandler stats() {
        return StatsDataHandler.instance();
    }

    private static Field total(String label, int color, String key, IntSupplier get) {
        return new Field(Component.literal(label).withColor(color), get, v -> stats().setTotalManually(key, v));
    }

    private static Field fish(DryStreakEditScreen.Entry entry) {
        return new Field(entry.label(), () -> {
            Map<String, StatsDataHandler.Stat<Integer, Integer>> m = stats().getStatsData().fishData.getOrDefault(entry.category(), Map.of());
            StatsDataHandler.Stat<Integer, Integer> s = m.get(entry.field());
            return s == null || s.amount() == null ? 0 : s.amount();
        }, v -> stats().setFishCountManually(entry.category(), entry.field(), v));
    }

    private final List<List<Field>> columns = List.of(
            List.of(
                    total("Fish Caught", 0x55FF55, "fish", () -> stats().getStatsData().fishTotal),
                    total("Pets", 0xFF55FF, "pets", () -> stats().getStatsData().petTotal),
                    total("Shards", 0xFFAA00, "shards", () -> stats().shardTotal()),
                    total("Capsules", 0xFF5555, RareCatchHandler.INFUSION_CAPSULE, () -> stats().rareCatchAmount(RareCatchHandler.INFUSION_CAPSULE)),
                    total("Bottles", 0xFFFF55, RareCatchHandler.LIGHTNING_BOTTLE, () -> stats().rareCatchAmount(RareCatchHandler.LIGHTNING_BOTTLE)),
                    total("Quests", 0xAAAAAA, "quests", () -> stats().getStatsData().questsCompleted)),
            DryStreakEditScreen.RARITIES.stream().map(StatsEditScreen::fish).toList(),
            DryStreakEditScreen.SPECIAL.stream().map(StatsEditScreen::fish).toList());
    private static final List<String> HEADERS = List.of("ᴛᴏᴛᴀʟѕ", "ʀᴀʀɪᴛʏ", "ᴠᴀʀɪᴀɴᴛ & ѕɪᴢᴇ");

    public StatsEditScreen(Screen parent) {
        super(Component.literal("All-Time Stats"));
        this.parent = parent;
    }

    private int rows() {
        int rows = 0;
        for (List<Field> c : columns) rows = Math.max(rows, c.size());
        return rows;
    }

    @Override
    protected void init() {
        int contentW = COLUMNS * (PANEL_W + PAD * 2) + (COLUMNS - 1) * GAP;
        int contentH = 14 + 10 + 14 + PAD + rows() * ROW + PAD + 8 + H;
        int left = (width - contentW) / 2 + PAD;
        for (int c = 0; c < COLUMNS; c++) columnX[c] = left + c * (PANEL_W + PAD * 2 + GAP);
        top = Math.max(6, (height - contentH) / 2);
        tableY = top + 14 + 10 + 14 + PAD;
        panelBottom = tableY + rows() * ROW - 2 + PAD;

        for (int c = 0; c < COLUMNS; c++) {
            int y = tableY;
            for (Field field : columns.get(c)) {
                this.addRenderableWidget(box(columnX[c] + LABEL_W + 4, y, field));
                y += ROW;
            }
        }
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(width / 2 - 50, panelBottom + 8, 100, H).build());
    }

    private EditBox box(int x, int y, Field field) {
        EditBox box = new EditBox(this.font, x, y, BOX_W, H, Component.literal("Amount"));
        box.setMaxLength(9);
        box.setFilter(text -> text.matches("\\d*"));
        box.setValue(String.valueOf(field.get().getAsInt()));
        box.setTooltip(Tooltip.create(Component.literal("Type a number to set it. Dry streaks stay as they are.")));
        box.setResponder(text -> {
            if (text.isEmpty()) return;
            try {
                field.set().accept(Integer.parseInt(text));
                box.setTextColor(0xFFE0E0E0);
            } catch (NumberFormatException e) {
                box.setTextColor(0xFFFF5555);
            }
        });
        return box;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(guiGraphics, mouseX, mouseY, delta);
        int panelTop = tableY - PAD - 14;
        for (int c = 0; c < COLUMNS; c++) {
            guiGraphics.fill(columnX[c] - PAD, panelTop, columnX[c] + PANEL_W + PAD, panelBottom, 0x55000000);
            guiGraphics.renderOutline(columnX[c] - PAD, panelTop, PANEL_W + PAD * 2, panelBottom - panelTop, 0x40FFFFFF);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);
        int panelTop = tableY - PAD - 14;
        guiGraphics.drawCenteredString(font, Component.literal("ALL-TIME STATS").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), width / 2, top + 2, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(font, Component.literal("Fix any total that counted wrong. Type to change.").withStyle(ChatFormatting.GRAY),
                width / 2, top + 14, 0xFFFFFFFF);
        for (int c = 0; c < COLUMNS; c++) {
            guiGraphics.drawString(font, Component.literal(HEADERS.get(c)).withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD), columnX[c], panelTop + 4, 0xFFFFFFFF, true);
            guiGraphics.fill(columnX[c], panelTop + 4 + font.lineHeight + 1, columnX[c] + PANEL_W, panelTop + 4 + font.lineHeight + 2, 0x40FFFFFF);
            int y = tableY;
            for (Field field : columns.get(c)) {
                guiGraphics.drawString(font, field.label(), columnX[c], y + (H - font.lineHeight) / 2 + 1, 0xFFFFFFFF, true);
                y += ROW;
            }
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
