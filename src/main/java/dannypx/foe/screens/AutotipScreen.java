package dannypx.foe.screens;

import dannypx.foe.config.AutotipConfig;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.AutotipHandler;
import dannypx.foe.helper.FoeLocations;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Auto Tip settings, laid out like the FOE+ menu: a title and the master switch on top, then two
 * panels. TIPS (left): who gets tipped and how much, one row each for reactions and every
 * chummer rarity. SETTINGS (right): the spending limit, players never to tip, the chat message
 * toggle, and what was tipped this session. Changes save immediately (M or O → Auto Tip).
 */
public class AutotipScreen extends Screen {
    private static final int ROW = 22;
    private static final int H = 20;
    private static final int PAD = 6;

    // Left panel
    private static final int LABEL_W = 104;
    private static final int TOGGLE_W = 34;
    private static final int AMOUNT_W = 62;
    private static final int LEFT_W = LABEL_W + 4 + TOGGLE_W + 4 + AMOUNT_W;
    // Right panel
    private static final int RIGHT_W = 150;
    private static final int GAP = 14;

    private final Screen parent;
    private int leftX, rightX, top, tableY, panelBottom;

    private record Row(Component label, ValidatedBoolean toggle, ValidatedInt amount) {}

    public AutotipScreen(Screen parent) {
        super(Component.literal("Auto Tip"));
        this.parent = parent;
    }

    /// The server's rarity badge (same glyphs as on items and in chat), then "Chummer".
    private static Component chummer(String rarity, String name) {
        String glyph = switch (rarity) {
            case "common" -> "\uF033";
            case "rare" -> "\uF034";
            case "epic" -> "\uF035";
            case "legendary" -> "\uF036";
            default -> "\uF037";
        };
        return Component.literal(glyph).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" Chummer").withStyle(ChatFormatting.GRAY));
    }

    private static List<Row> rows() {
        AutotipConfig c = Configs.autotipConfig;
        return List.of(
                new Row(Component.literal("Reaction Winners").withStyle(ChatFormatting.WHITE), c.tipReactions, c.reactionAmount),
                new Row(chummer("common", "Common"), c.tipCommonChummer, c.commonChummerAmount),
                new Row(chummer("rare", "Rare"), c.tipRareChummer, c.rareChummerAmount),
                new Row(chummer("epic", "Epic"), c.tipEpicChummer, c.epicChummerAmount),
                new Row(chummer("legendary", "Legendary"), c.tipLegendaryChummer, c.legendaryChummerAmount),
                new Row(chummer("mythical", "Mythical"), c.tipMythicalChummer, c.mythicalChummerAmount));
    }

    @Override
    protected void init() {
        AutotipConfig config = Configs.autotipConfig;
        int contentW = LEFT_W + PAD * 2 + GAP + RIGHT_W + PAD * 2;
        int contentH = 14 /*title*/ + 6 + H /*master*/ + 10 + 14 /*panel header*/ + 6 * ROW + PAD + 8 + H /*done*/;
        leftX = (width - contentW) / 2 + PAD;
        rightX = leftX + LEFT_W + PAD + GAP + PAD;
        top = Math.max(6, (height - contentH) / 2);
        int masterY = top + 14 + 6;
        tableY = masterY + H + 10 + 14 + PAD;
        panelBottom = tableY + 6 * ROW - 2 + PAD;

        // Master switch, full width
        this.addRenderableWidget(Button.builder(masterLabel(), b -> {
            config.enabled.accept(!config.enabled.get());
            save();
            b.setMessage(masterLabel());
        }).bounds(leftX - PAD, masterY, contentW, H)
                .tooltip(Tooltip.create(Component.literal("Turn all automatic tipping on or off"))).build());

        // TIPS: toggle + amount per row
        int y = tableY;
        for (Row row : rows()) {
            this.addRenderableWidget(Button.builder(onOff(row.toggle().get()), b -> {
                row.toggle().accept(!row.toggle().get());
                save();
                b.setMessage(onOff(row.toggle().get()));
            }).bounds(leftX + LABEL_W + 4, y, TOGGLE_W, H).build());
            this.addRenderableWidget(amountBox(leftX + LABEL_W + 4 + TOGGLE_W + 4, y, AMOUNT_W, row.amount(), 100,
                    "Amount to tip ($, at least 100)"));
            y += ROW;
        }

        // SETTINGS
        int controlW = 70;
        int controlX = rightX + RIGHT_W - controlW;
        int sy = tableY;
        this.addRenderableWidget(amountBox(controlX, sy, controlW, config.sessionLimit, 0,
                "Stop tipping after this much this session ($, 0 = no limit)"));
        sy += ROW;
        EditBox ignored = new EditBox(this.font, controlX, sy, controlW, H, Component.literal("Never tip"));
        ignored.setMaxLength(512);
        ignored.setValue(config.ignoredPlayers.get());
        ignored.setHint(Component.literal("name, name").withStyle(ChatFormatting.DARK_GRAY));
        ignored.setTooltip(Tooltip.create(Component.literal("Players never to tip, separated by commas")));
        ignored.setResponder(value -> {
            config.ignoredPlayers.accept(value);
            save();
        });
        this.addRenderableWidget(ignored);
        sy += ROW;
        this.addRenderableWidget(Button.builder(onOff(config.announceTips.get()), b -> {
            config.announceTips.accept(!config.announceTips.get());
            save();
            b.setMessage(onOff(config.announceTips.get()));
        }).bounds(controlX, sy, controlW, H)
                .tooltip(Tooltip.create(Component.literal("Post a [FOE+] AUTO TIP line in chat for every tip"))).build());

        // Done
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(width / 2 - 50, panelBottom + 8, 100, H).build());
    }

    private EditBox amountBox(int x, int y, int w, ValidatedInt setting, int minimum, String tooltip) {
        EditBox box = new EditBox(this.font, x, y, w, H, Component.literal("Amount"));
        box.setMaxLength(9);
        box.setFilter(text -> text.matches("\\d*"));
        box.setValue(String.valueOf(setting.get()));
        box.setTooltip(Tooltip.create(Component.literal(tooltip)));
        box.setResponder(text -> {
            if (text.isEmpty()) return;
            try {
                int value = Integer.parseInt(text);
                if (value >= minimum) {
                    setting.accept(value);
                    save();
                    box.setTextColor(0xFFE0E0E0);
                } else {
                    box.setTextColor(0xFFFF5555);
                }
            } catch (NumberFormatException e) {
                box.setTextColor(0xFFFF5555);
            }
        });
        return box;
    }

    private static Component masterLabel() {
        boolean on = Configs.autotipConfig.enabled.get();
        return Component.literal("Auto Tip: ")
                .append(Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD));
    }

    private static Component onOff(boolean on) {
        return Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static void save() {
        Configs.autotipConfig.save();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);

        int panelTop = tableY - PAD - 14;

        // Title
        guiGraphics.drawCenteredString(font, Component.literal("AUTO TIP").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), width / 2, top + 2, 0xFFFFFFFF);

        // TIPS
        header(guiGraphics, "ᴛɪᴘѕ", leftX, LEFT_W, panelTop);
        column(guiGraphics, "ᴛɪᴘ", leftX + LABEL_W + 4 + TOGGLE_W / 2, panelTop + 4);
        column(guiGraphics, "ᴀᴍᴏᴜɴᴛ ($)", leftX + LABEL_W + 4 + TOGGLE_W + 4 + AMOUNT_W / 2, panelTop + 4);
        int y = tableY;
        for (Row row : rows()) {
            guiGraphics.drawString(font, row.label(), leftX, y + (H - font.lineHeight) / 2 + 1, 0xFFFFFFFF, true);
            y += ROW;
        }

        // SETTINGS
        header(guiGraphics, "ѕᴇᴛᴛɪɴɢѕ", rightX, RIGHT_W, panelTop);
        int sy = tableY + (H - font.lineHeight) / 2 + 1;
        guiGraphics.drawString(font, Component.literal("Session limit"), rightX, sy, 0xFFFFFFFF, true);
        guiGraphics.drawString(font, Component.literal("Never tip"), rightX, sy + ROW, 0xFFFFFFFF, true);
        guiGraphics.drawString(font, Component.literal("Chat message"), rightX, sy + ROW * 2, 0xFFFFFFFF, true);

        // This session (same panel, under a thin rule)
        int sessionTop = tableY + ROW * 3 + 2;
        guiGraphics.fill(rightX, sessionTop, rightX + RIGHT_W, sessionTop + 1, 0x40FFFFFF);
        AutotipHandler handler = AutotipHandler.instance();
        List<AutotipHandler.Tip> tips = handler.getLog();
        int ly = sessionTop + 5;
        Component total = Component.literal("$" + String.format("%,d", handler.getSessionTotal())).withStyle(ChatFormatting.GREEN);
        guiGraphics.drawString(font, Component.literal("ᴛʜɪѕ ѕᴇѕѕɪᴏɴ").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD), rightX, ly, 0xFFFFFFFF, true);
        guiGraphics.drawString(font, total, rightX + RIGHT_W - font.width(total), ly, 0xFFFFFFFF, true);
        ly += 12;
        if (tips.isEmpty()) {
            guiGraphics.drawString(font, Component.literal("No tips yet").withStyle(ChatFormatting.DARK_GRAY), rightX, ly, 0xFFFFFFFF, false);
        }
        long now = System.currentTimeMillis();
        int maxLines = Math.max(0, (panelBottom - PAD + 2 - ly) / 10);
        for (int i = 0; i < Math.min(maxLines, tips.size()); i++) {
            AutotipHandler.Tip tip = tips.get(i);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(now - tip.time());
            Component amount = Component.literal("$" + String.format("%,d", tip.amount())).withStyle(ChatFormatting.GREEN);
            Component when = Component.literal(minutes == 0 ? "now" : minutes + "m ago").withStyle(ChatFormatting.DARK_GRAY);
            int rightEdge = rightX + RIGHT_W;
            guiGraphics.drawString(font, Component.literal(tip.player()).withStyle(ChatFormatting.WHITE), rightX, ly, 0xFFFFFFFF, false);
            guiGraphics.drawString(font, when, rightEdge - font.width(when), ly, 0xFFFFFFFF, false);
            guiGraphics.drawString(font, amount, rightEdge - font.width(when) - 6 - font.width(amount), ly, 0xFFFFFFFF, false);
            ly += 10;
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(guiGraphics, mouseX, mouseY, delta);
        // Panel frames go under the widgets.
        int panelTop = tableY - PAD - 14;
        frame(guiGraphics, leftX - PAD, panelTop, LEFT_W + PAD * 2, panelBottom - panelTop);
        frame(guiGraphics, rightX - PAD, panelTop, RIGHT_W + PAD * 2, panelBottom - panelTop);
    }

    /// A thin frame around a panel (the screen's own blur is the background).
    private static void frame(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        guiGraphics.fill(x, y, x + w, y + h, 0x55000000);
        guiGraphics.renderOutline(x, y, w, h, 0x40FFFFFF);
    }

    private void header(GuiGraphics guiGraphics, String text, int x, int w, int panelTop) {
        guiGraphics.drawString(font, Component.literal(text).withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD), x, panelTop + 4, 0xFFFFFFFF, true);
        guiGraphics.fill(x, panelTop + 4 + font.lineHeight + 1, x + w, panelTop + 4 + font.lineHeight + 2, 0x40FFFFFF);
    }

    /// Small column label on the header line, centred over its column.
    private void column(GuiGraphics guiGraphics, String text, int centerX, int y) {
        Component c = Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
        guiGraphics.drawString(font, c, centerX - font.width(c) / 2, y, 0xFFFFFFFF, false);
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
