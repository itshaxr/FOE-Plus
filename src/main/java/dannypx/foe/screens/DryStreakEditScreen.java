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
 * Set your dry streaks by hand: one box per rarity, variant, Gigantic, pets, shards and rare pull, showing the current
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
    record Entry(String category, String field, Component label) {}

    static final List<Entry> RARITIES = List.of(
            new Entry("rarity", "common", badge("")),
            new Entry("rarity", "rare", badge("")),
            new Entry("rarity", "epic", badge("")),
            new Entry("rarity", "legendary", badge("")),
            new Entry("rarity", "mythical", badge("")));

    static final List<Entry> SPECIAL = List.of(
            new Entry("variant", "albino", badge("")),
            new Entry("variant", "melanistic", badge("")),
            new Entry("variant", "trophy", badge("")),
            new Entry("variant", "fabled", badge("")),
            new Entry("size", "gigantic", StatsPanelElement.GIGANTIC_BADGE));

    /// Second row: pets and shards on the left, the rare pulls on the right.
    private static final List<Entry> PETS = List.of(
            new Entry("pet", "any", Component.literal("Pets").withColor(0xFF55FF)),
            new Entry("item", "shard", Component.literal("Shards").withColor(0xFFAA00)));
    private static final List<Entry> RARE = List.of(
            new Entry("rare", dannypx.foe.handler.logic.RareCatchHandler.INFUSION_CAPSULE, Component.literal("Infusion Capsule").withColor(0xFF5555)),
            new Entry("rare", dannypx.foe.handler.logic.RareCatchHandler.LIGHTNING_BOTTLE, Component.literal("Lightning Bottle").withColor(0xFFFF55)));

    /// Rows in the taller of the two panels, per row of panels.
    private static final int ROWS = Math.max(5, Math.max(RARITIES.size(), SPECIAL.size()));
    private static final int ROWS2 = Math.max(PETS.size(), RARE.size());
    /// Between the two rows of panels.
    private static final int ROW_GAP = 8;
    private int table2Y, panel2Bottom;

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
        int contentH = 14 + 10 + 14 + PAD + ROWS * ROW + PAD + ROW_GAP + 14 + PAD + ROWS2 * ROW + PAD + 8 + H;
        leftX = (width - contentW) / 2 + PAD;
        rightX = leftX + PANEL_W + PAD + GAP + PAD;
        top = Math.max(6, (height - contentH) / 2);
        tableY = top + 14 + 10 + 14 + PAD;
        panelBottom = tableY + ROWS * ROW - 2 + PAD;
        table2Y = panelBottom + ROW_GAP + 14 + PAD;
        panel2Bottom = table2Y + ROWS2 * ROW - 2 + PAD;

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
        y = table2Y;
        for (Entry entry : PETS) {
            this.addRenderableWidget(streakBox(leftX + LABEL_W + 4, y, entry));
            y += ROW;
        }
        y = table2Y;
        for (Entry entry : RARE) {
            this.addRenderableWidget(streakBox(rightX + LABEL_W + 4, y, entry));
            y += ROW;
        }

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(width / 2 - 50, panel2Bottom + 8, 100, H).build());
    }

    private static int currentStreak(Entry entry) {
        if (entry.category().equals("rare")) return StatsDataHandler.instance().rareCatchStreak(entry.field());
        if (entry.category().equals("pet")) return StatsDataHandler.instance().petDryStreak();
        if (entry.category().equals("item")) return StatsDataHandler.instance().shardDryStreak();
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
        box.setTooltip(Tooltip.create(Component.literal(switch (entry.field()) {
            case dannypx.foe.handler.logic.RareCatchHandler.INFUSION_CAPSULE -> "Fish caught during a Blood Moon since your last one. Type a number to set it.";
            case dannypx.foe.handler.logic.RareCatchHandler.LIGHTNING_BOTTLE -> "Fish caught in a thunderstorm or supercell since your last one. Type a number to set it.";
            default -> "Fish since your last one. Type a number to set it.";
        })));
        box.setResponder(text -> {
            if (text.isEmpty()) return;
            try {
                int value = Integer.parseInt(text);
                String set = switch (entry.category()) {
                    case "rare", "pet", "item" -> entry.category();
                    default -> "fish";
                };
                StatsDataHandler.instance().setStreakManually(set, entry.category(), entry.field(), value);
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
        int panel2Top = table2Y - PAD - 14;
        frame(guiGraphics, leftX - PAD, panel2Top, PANEL_W + PAD * 2, panel2Bottom - panel2Top);
        frame(guiGraphics, rightX - PAD, panel2Top, PANEL_W + PAD * 2, panel2Bottom - panel2Top);
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
        int panel2Top = table2Y - PAD - 14;
        header(guiGraphics, "ᴘᴇᴛѕ & ѕʜᴀʀᴅѕ", leftX, panel2Top);
        header(guiGraphics, "ʀᴀʀᴇ ᴄᴀᴛᴄʜᴇѕ", rightX, panel2Top);

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
        y = table2Y;
        for (Entry entry : PETS) {
            guiGraphics.drawString(font, entry.label(), leftX, y + (H - font.lineHeight) / 2 + 1, 0xFFFFFFFF, true);
            y += ROW;
        }
        y = table2Y;
        for (Entry entry : RARE) {
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
