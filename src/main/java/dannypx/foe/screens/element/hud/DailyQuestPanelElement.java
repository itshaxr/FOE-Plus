package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.DailyQuestHandler;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/// Unfinished daily missions, in FOE's panel style. Ported from FOECollab's
/// DailyQuestTrackerHudHandler (AGPL-3.0). Open the main menu once per session to fill it.
public class DailyQuestPanelElement extends PanelElement {
    public DailyQuestPanelElement() {
        this(false);
    }

    public DailyQuestPanelElement(boolean isCopy) {
        super(Configs.hudConfig.dailyQuestPanelXPosition.get() / 100f,
                Configs.hudConfig.dailyQuestPanelYPosition.get() / 100f,
                Configs.hudConfig.dailyQuestPanelAlignment.get(),
                Component.literal("Daily Quests Panel"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showDailyQuestPanel.get(); }
    @Override protected float configX() { return Configs.hudConfig.dailyQuestPanelXPosition.get() / 100f; }
    @Override protected float configY() { return Configs.hudConfig.dailyQuestPanelYPosition.get() / 100f; }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.dailyQuestPanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.dailyQuestPanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.dailyQuestPanelOpacity.get(); }

    @Override
    protected boolean alignTextToAnchor() {
        return false;
    }

    @Override
    protected Component title() {
        return Component.literal("ᴅᴀɪʟʏ ꞯᴜᴇѕᴛѕ").withStyle(ChatFormatting.BOLD);
    }

    /// The goal text in green, but the server's badge glyphs in it ("Catch 3 MYTHICAL") in
    /// white: they carry their own colours, and green would tint them.
    static Component goal(String text) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        StringBuilder run = new StringBuilder();
        boolean glyphRun = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean glyph = c >= '\uE000' && c <= '\uF8FF';
            if (glyph != glyphRun && run.length() > 0) {
                out.append(glyphRun ? Component.literal(run.toString()).withStyle(ChatFormatting.WHITE)
                        : Component.literal(run.toString()).withStyle(ChatFormatting.GREEN));
                run.setLength(0);
            }
            glyphRun = glyph;
            run.append(c);
        }
        if (run.length() > 0) {
            out.append(glyphRun ? Component.literal(run.toString()).withStyle(ChatFormatting.WHITE)
                    : Component.literal(run.toString()).withStyle(ChatFormatting.GREEN));
        }
        return out;
    }

    @Override
    protected List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        if (!DailyQuestHandler.instance().isInitialized()) {
            lines.add(label("ᴏᴘᴇɴ ᴛʜᴇ ᴍᴀɪɴ ᴍᴇɴᴜ ᴛᴏ ʟᴏᴀᴅ"));
            return lines;
        }
        String lastSection = "";
        for (DailyQuestHandler.Quest quest : DailyQuestHandler.instance().quests) {
            if (!quest.section.isEmpty() && !quest.section.equals(lastSection)) {
                lines.add(Component.literal(quest.section).withStyle(ChatFormatting.GRAY));
                lastSection = quest.section;
            }
            Component prefix = label("#" + quest.slot + ": ");
            if (quest.isDone()) {
                lines.add(join(prefix, Component.literal("ꞯᴜᴇѕᴛ ᴅᴏɴᴇ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
            } else {
                lines.add(join(prefix,
                        goal(quest.goal),
                        label(" ("),
                        Component.literal(String.valueOf(quest.progress)).withStyle(ChatFormatting.YELLOW),
                        label("/"),
                        Component.literal(String.valueOf(quest.needed)).withColor(0x7FB8FF),
                        label(")")));
            }
        }
        return lines;
    }

    @Override
    protected List<Component> previewLines() {
        return List.of(
                label("Base Missions"),
                join(label("#1: "), Component.literal("Open the main menu").withStyle(ChatFormatting.GREEN)));
    }
}
