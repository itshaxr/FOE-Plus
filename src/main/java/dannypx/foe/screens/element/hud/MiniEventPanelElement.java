package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.MiniEventHandler;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The mini event from /minievent: when the daily missions reroll, then each mission with its
 * progress, in the server's colours. Hidden when no mini event is on.
 */
public class MiniEventPanelElement extends PanelElement {
    public MiniEventPanelElement() {
        this(false);
    }

    public MiniEventPanelElement(boolean isCopy) {
        super(Configs.hudConfig.miniEventPanelXPosition.get() / 100f,
                Configs.hudConfig.miniEventPanelYPosition.get() / 100f,
                Configs.hudConfig.miniEventPanelAlignment.get(),
                Component.literal("Mini Event"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showMiniEventPanel.get(); }
    @Override protected float configX() { return Configs.hudConfig.miniEventPanelXPosition.get() / 100f; }
    @Override protected float configY() { return Configs.hudConfig.miniEventPanelYPosition.get() / 100f; }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.miniEventPanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.miniEventPanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.miniEventPanelOpacity.get(); }

    @Override
    protected boolean alignTextToAnchor() {
        return false;
    }

    @Override
    protected Component title() {
        return Component.literal("ᴍɪɴɪ ᴇᴠᴇɴᴛ").withStyle(ChatFormatting.BOLD);
    }

    /// "Rerolls 23h 55m 2s" (when the daily missions reroll)
    static Component reroll(long msLeft) {
        return join(Component.literal("Rerolls ").withStyle(ChatFormatting.GRAY),
                Component.literal(MiniEventHandler.dhms(msLeft)).withStyle(ChatFormatting.WHITE));
    }

    /// "— Kenai River GIGANTIC  (0/2) One Timer", with a ✔ once it's done.
    static Component mission(MiniEventHandler.Mission m) {
        // Fish names in the colour the compendium gives them (their rarity), not the server's green.
        return join(dannypx.foe.handler.logic.FishColorHandler.instance().recolor(m.name()), Component.literal("  "), m.progress(),
                m.complete() ? Component.literal(" ✔").withStyle(ChatFormatting.GREEN) : Component.empty());
    }

    @Override
    protected List<Component> lines() {
        MiniEventHandler event = MiniEventHandler.instance();
        List<Component> lines = new ArrayList<>();
        if (!event.active()) return lines;
        // Just the daily reroll timer above the missions (the event's end time is left out).
        if (event.rerollInMs() > 0) lines.add(reroll(event.rerollInMs()));
        boolean hideDone = Configs.hudConfig.miniEventHideCompleted.get();
        for (MiniEventHandler.Mission m : event.missions()) {
            if (hideDone && m.complete()) continue;
            lines.add(mission(m));
        }
        return lines;
    }

    @Override
    protected List<Component> previewLines() {
        List<Component> lines = new ArrayList<>();
        lines.add(reroll(23 * 3_600_000L + 55 * 60_000L + 2_000L));
        lines.add(mission(new MiniEventHandler.Mission(
                Component.literal("— ").withStyle(ChatFormatting.RED).append(Component.literal("Kenai River").withStyle(ChatFormatting.GREEN)),
                Component.literal("(0/2) One Timer").withStyle(ChatFormatting.GRAY), 0, 2)));
        lines.add(mission(new MiniEventHandler.Mission(
                Component.literal("— ").withStyle(ChatFormatting.GOLD).append(Component.literal("Sockeye Salmon").withStyle(ChatFormatting.GREEN)),
                Component.literal("(3/10) Daily").withStyle(ChatFormatting.GRAY), 3, 10)));
        return lines;
    }
}
