package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.BossEventHandler;
import dannypx.foe.handler.store.ConstantDataHandler;
import dannypx.foe.handler.store.QuestDataHandler;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/// Quests for your current location, in FOE's panel style. Uses FOE-R's own quest data
/// (open the quest menu once per location to fill it).
public class QuestPanelElement extends PanelElement {
    public QuestPanelElement() {
        this(false);
    }

    public QuestPanelElement(boolean isCopy) {
        super(Configs.hudConfig.questPanelXPosition.get() / 100f,
                Configs.hudConfig.questPanelYPosition.get() / 100f,
                Configs.hudConfig.questPanelAlignment.get(),
                Component.literal("Quests Panel"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showQuestPanel.get(); }
    @Override protected float configX() { return Configs.hudConfig.questPanelXPosition.get() / 100f; }
    @Override protected float configY() { return Configs.hudConfig.questPanelYPosition.get() / 100f; }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.questPanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.questPanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.questPanelOpacity.get(); }

    @Override
    protected Component title() {
        return Component.literal("ꞯᴜᴇѕᴛѕ").withStyle(ChatFormatting.BOLD);
    }

    @Override
    protected List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        Component location = BossEventHandler.instance().getLocation();
        List<QuestDataHandler.Quest> quests = QuestDataHandler.instance().getQuestData().questList
                .getOrDefault(location.getString(), List.of());
        if (quests.isEmpty()) return lines;

        lines.add(join(label("ʟᴏᴄ: "), location));
        int index = 1;
        for (QuestDataHandler.Quest quest : quests) {
            Component prefix = label("#" + index++ + ": ");
            if (quest.isDone()) {
                lines.add(join(prefix, Component.literal("ꞯᴜᴇѕᴛ ᴅᴏɴᴇ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
            } else {
                lines.add(join(prefix,
                        ConstantDataHandler.instance().getConstantFishComponent(quest.goal),
                        Component.literal(" " + quest.current).withStyle(ChatFormatting.YELLOW),
                        label("/"),
                        Component.literal(String.valueOf(quest.max)).withStyle(ChatFormatting.WHITE)));
            }
        }
        return lines;
    }

    @Override
    protected List<Component> previewLines() {
        return List.of(
                join(label("ʟᴏᴄ: "), Component.literal("Open /quests").withStyle(ChatFormatting.WHITE)),
                join(label("#1: "), Component.literal("0").withStyle(ChatFormatting.YELLOW), label("/"), Component.literal("20")));
    }
}
