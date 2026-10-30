package dannypx.foe.screens;

import dannypx.foe.config.AutotipConfig;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.AutotipHandler;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Auto Tip settings: master switch, an on/off toggle and amount for reactions and each chummer
 * rarity, a session spending limit, players never to tip, and a list of recent tips.
 * Changes save immediately. Opened from O → Auto Tip.
 */
public class AutotipScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int LABEL_WIDTH = 130;
    private static final int TOGGLE_WIDTH = 40;
    private static final int AMOUNT_WIDTH = 80;

    private final Screen parent;

    private record Row(String label, ValidatedBoolean toggle, ValidatedInt amount) {}

    public AutotipScreen(Screen parent) {
        super(Component.literal("Auto Tip"));
        this.parent = parent;
    }

    private static List<Row> rows() {
        AutotipConfig c = Configs.autotipConfig;
        return List.of(
                new Row("Reaction winners", c.tipReactions, c.reactionAmount),
                new Row("Common chummer", c.tipCommonChummer, c.commonChummerAmount),
                new Row("Rare chummer", c.tipRareChummer, c.rareChummerAmount),
                new Row("Epic chummer", c.tipEpicChummer, c.epicChummerAmount),
                new Row("Legendary chummer", c.tipLegendaryChummer, c.legendaryChummerAmount),
                new Row("Mythical chummer", c.tipMythicalChummer, c.mythicalChummerAmount));
    }

    private int left() {
        return this.width / 2 - 190;
    }

    private int top() {
        return Math.max(30, this.height / 2 - 120);
    }

    @Override
    protected void init() {
        AutotipConfig config = Configs.autotipConfig;
        int left = left();
        int y = top();

        // Master switch
        this.addRenderableWidget(Button.builder(masterLabel(), b -> {
            config.enabled.accept(!config.enabled.get());
            save();
            b.setMessage(masterLabel());
        }).bounds(left, y, LABEL_WIDTH + TOGGLE_WIDTH + AMOUNT_WIDTH + 8, 20).build());
        y += ROW_HEIGHT + 8;

        for (Row row : rows()) {
            this.addRenderableWidget(Button.builder(onOff(row.toggle().get()), b -> {
                row.toggle().accept(!row.toggle().get());
                save();
                b.setMessage(onOff(row.toggle().get()));
            }).bounds(left + LABEL_WIDTH, y, TOGGLE_WIDTH, 20).build());

            this.addRenderableWidget(amountBox(left + LABEL_WIDTH + TOGGLE_WIDTH + 8, y, row.amount(), 100));
            y += ROW_HEIGHT;
        }

        y += 6;
        this.addRenderableWidget(amountBox(left + LABEL_WIDTH + TOGGLE_WIDTH + 8, y, config.sessionLimit, 0));
        y += ROW_HEIGHT;

        EditBox ignored = new EditBox(this.font, left + LABEL_WIDTH, y, TOGGLE_WIDTH + AMOUNT_WIDTH + 8, 20, Component.literal("Never tip"));
        ignored.setMaxLength(512);
        ignored.setValue(config.ignoredPlayers.get());
        ignored.setHint(Component.literal("name1, name2").withStyle(ChatFormatting.DARK_GRAY));
        ignored.setResponder(value -> {
            config.ignoredPlayers.accept(value);
            save();
        });
        this.addRenderableWidget(ignored);
        y += ROW_HEIGHT;

        this.addRenderableWidget(Button.builder(onOff(config.announceTips.get()), b -> {
            config.announceTips.accept(!config.announceTips.get());
            save();
            b.setMessage(onOff(config.announceTips.get()));
        }).bounds(left + LABEL_WIDTH, y, TOGGLE_WIDTH, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    private EditBox amountBox(int x, int y, ValidatedInt setting, int minimum) {
        EditBox box = new EditBox(this.font, x, y, AMOUNT_WIDTH, 20, Component.literal("Amount"));
        box.setMaxLength(9);
        box.setFilter(text -> text.matches("\\d*"));
        box.setValue(String.valueOf(setting.get()));
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

        int left = left();
        int y = top();
        guiGraphics.drawCenteredString(this.font, Component.literal("Auto Tip").withStyle(ChatFormatting.BOLD), this.width / 2, y - 18, 0xFFFFFFFF);

        // Column headings and row labels
        y += ROW_HEIGHT + 8;
        int labelY = y + 6;
        guiGraphics.drawString(this.font, Component.literal("Tip").withStyle(ChatFormatting.GRAY), left + LABEL_WIDTH + 8, y - 10, 0xFFFFFFFF, true);
        guiGraphics.drawString(this.font, Component.literal("Amount ($, min 100)").withStyle(ChatFormatting.GRAY), left + LABEL_WIDTH + TOGGLE_WIDTH + 8, y - 10, 0xFFFFFFFF, true);
        for (Row row : rows()) {
            guiGraphics.drawString(this.font, Component.literal(row.label()), left, labelY, 0xFFFFFFFF, true);
            labelY += ROW_HEIGHT;
        }
        labelY += 6;
        guiGraphics.drawString(this.font, Component.literal("Session limit ($, 0 = none)"), left, labelY, 0xFFFFFFFF, true);
        labelY += ROW_HEIGHT;
        guiGraphics.drawString(this.font, Component.literal("Never tip"), left, labelY, 0xFFFFFFFF, true);
        labelY += ROW_HEIGHT;
        guiGraphics.drawString(this.font, Component.literal("Chat message on tip"), left, labelY, 0xFFFFFFFF, true);

        // Session summary and recent tips
        int rightX = left + LABEL_WIDTH + TOGGLE_WIDTH + AMOUNT_WIDTH + 30;
        int ry = top();
        AutotipHandler handler = AutotipHandler.instance();
        guiGraphics.drawString(this.font, Component.literal("This session").withStyle(ChatFormatting.BOLD), rightX, ry, 0xFFFFFFFF, true);
        ry += 14;
        guiGraphics.drawString(this.font, Component.literal("Tipped: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("$" + handler.getSessionTotal()).withStyle(ChatFormatting.GREEN)), rightX, ry, 0xFFFFFFFF, true);
        ry += 18;
        guiGraphics.drawString(this.font, Component.literal("Recent tips").withStyle(ChatFormatting.BOLD), rightX, ry, 0xFFFFFFFF, true);
        ry += 14;
        List<AutotipHandler.Tip> tips = handler.getLog();
        if (tips.isEmpty()) {
            guiGraphics.drawString(this.font, Component.literal("None yet").withStyle(ChatFormatting.DARK_GRAY), rightX, ry, 0xFFFFFFFF, true);
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < Math.min(8, tips.size()); i++) {
            AutotipHandler.Tip tip = tips.get(i);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(now - tip.time());
            guiGraphics.drawString(this.font, Component.literal(tip.player() + " ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal("$" + tip.amount()).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" " + (minutes == 0 ? "now" : minutes + "m ago")).withStyle(ChatFormatting.DARK_GRAY)), rightX, ry, 0xFFFFFFFF, true);
            ry += 11;
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
