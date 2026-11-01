package dannypx.foe.screens;

import dannypx.foe.handler.store.StatsDataHandler;
import dannypx.foe.screens.element.hud.StatsPanelElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * Set your dry streaks by hand: one box per rarity, variant and Gigantic, showing the current
 * streak. Typing a number sets it straight away (your counts stay as they are). Streaks you set
 * here count as newer than any old FOE/FOE-R file, so an import won't undo them.
 */
public class DryStreakEditScreen extends Screen {
    private static final int ROW = 22;
    private static final int H = 20;
    private static final int PAD = 6;
    private static final int LABEL_W = 96;
    private static final int BOX_W = 70;
    private static final int PANEL_W = LABEL_W + 4 + BOX_W;
    private static final int GAP = 14;

    private final Screen parent;
    private int leftX, rightX, top, tableY, panelBottom;

    /// One editable streak: where it lives in the stats and how its label looks.
    private record Entry(String category, String field, Component label) {}

    private static final List<Entry> RARITIES = List.of(
            new Entry("rarity", "common", badge("")),
            new Entry("rarity", "rare", badge("")),
            new Entry("rarity", "epic", badge("")),
            new Entry("rarity", "legendary", badge("")),
            new Entry("rarity", "mythical", badge("")));

    private static final List<Entry> SPECIAL = List.of(
            new Entry("variant", "albino", badge("")),
            new Entry("variant", "melanistic", badge("")),
            new Entry("variant", "trophy", badge("")),
            new Entry("variant", "fabled", badge("")),
            new Entry("size", "gigantic", StatsPanelElement.GIGANTIC_BADGE),
            new Entry("rare", "infusion_capsule", Component.literal("Infusion Capsule").withStyle(ChatFormatting.AQUA)));

    /// Rows in the taller of the two panels.
    private static final int ROWS = Math.max(5, Math.max(RARITIES.size(), SPECIAL.size()));

    private static Component badge(String glyph) {
        return Component.literal(glyph).withStyle(ChatFormatting.WHITE);
    }

    public DryStreakEditScreen(Screen parent) {
        super(Component.literal("Dry Streaks"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int contentW = (PANEL_W + PAD * 2) * 2 + GAP;
        int contentH = 14 + 10 + 14 + PAD + ROWS * ROW + PAD + 8 + H;
        leftX = (width - contentW) / 2 + PAD;
        rightX = leftX + PANEL_W + PAD + GAP + PAD;
        top = Math.max(6, (height - contentH) / 2);
        tableY = top + 14 + 10 + 14 + PAD;
        panelBottom = tableY + ROWS * ROW - 2 + PAD;

        int y = tableY;
        for (Entry entry : RARITIES) {
            this.addRenderableWidget(streakBox(leftX + LABEL_W + 4, y, entry));
            y += ROW;
        }
        y = tableY;
        for (Entry entry : SPECIAL) {
            this.addRenderableWidget(streakBox(rightX + LABEL_W + 4, y, entry));
            y += ROW;
        }

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(width / 2 - 50, panelBottom + 8, 100, H).build());
    }

    private static int currentStreak(Entry entry) {
        if (entry.category().equals("rare")) return StatsDataHandler.instance().rareCatchStreak(entry.field());
        StatsDataHandler.StatsDataModel data = StatsDataHandler.instance().getStatsData();
        StatsDataHandler.Stat<Integer, Integer> stat = data.fishData.getOrDefault(entry.category(), Map.of()).get(entry.field());
        if (stat == null || stat.caughtOn() == null) return data.fishTotal;
        return Math.max(0, data.fishTotal - stat.caughtOn());
    }

    private EditBox streakBox(int x, int y, Entry entry) {
        EditBox box = new EditBox(this.font, x, y, BOX_W, H, Component.literal("Dry streak"));
        box.setMaxLength(9);
        box.setFilter(text -> text.matches("\\d*"));
        int current = currentStreak(entry);
        box.setValue(current < 0 ? "" : String.valueOf(current));
        if (current < 0) box.setHint(Component.literal("not tracked yet").withStyle(ChatFormatting.DARK_GRAY));
        box.setTooltip(Tooltip.create(Component.literal("Fish since your last one. Type a number to set it.")));
        box.setResponder(text -> {
            if (text.isEmpty()) return;
            try {
                int value = Integer.parseInt(text);
                StatsDataHandler.instance().setStreakManually(entry.category().equals("rare") ? "rare" : "fish", entry.category(), entry.field(), value);
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
        frame(guiGraphics, leftX - PAD, panelTop, PANEL_W + PAD * 2, panelBottom - panelTop);
        frame(guiGraphics, rightX - PAD, panelTop, PANEL_W + PAD * 2, panelBottom - panelTop);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);
        int panelTop = tableY - PAD - 14;

        guiGraphics.drawCenteredString(font, Component.literal("DRY STREAKS").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), width / 2, top + 2, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(font, Component.literal("Fish since your last catch of each. Type to change.").withStyle(ChatFormatting.GRAY),
                width / 2, top + 14, 0xFFFFFFFF);

        header(guiGraphics, "ʀᴀʀɪᴛʏ", leftX, panelTop);
        header(guiGraphics, "ᴠᴀʀɪᴀɴᴛ & ѕɪᴢᴇ", rightX, panelTop);

        int y = tableY;
        for (Entry entry : RARITIES) {
            guiGraphics.drawString(font, entry.label(), leftX, y + (H - font.lineHeight) / 2 + 1, 0xFFFFFFFF, true);
            y += ROW;
        }
        y = tableY;
        for (Entry entry : SPECIAL) {
            guiGraphics.drawString(font, entry.label(), rightX, y + (H - font.lineHeight) / 2 + 1, 0xFFFFFFFF, true);
            y += ROW;
        }
    }

    private static void frame(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        guiGraphics.fill(x, y, x + w, y + h, 0x55000000);
        guiGraphics.renderOutline(x, y, w, h, 0x40FFFFFF);
    }

    private void header(GuiGraphics guiGraphics, String text, int x, int panelTop) {
        guiGraphics.drawString(font, Component.literal(text).withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD), x, panelTop + 4, 0xFFFFFFFF, true);
        guiGraphics.fill(x, panelTop + 4 + font.lineHeight + 1, x + PANEL_W, panelTop + 4 + font.lineHeight + 2, 0x40FFFFFF);
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
