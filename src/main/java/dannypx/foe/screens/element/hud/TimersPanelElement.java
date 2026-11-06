package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.renderer.TimersCrewBox;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Timers & Crew box from the inventory as a HUD panel (optional, off by default): the same
 * lines, from {@link TimersCrewBox}, with what they show set in the inventory settings.
 */
public class TimersPanelElement extends PanelElement {
    public TimersPanelElement() {
        this(false);
    }

    public TimersPanelElement(boolean isCopy) {
        super(Configs.hudConfig.timersPanelXPosition.get() / 100f,
                Configs.hudConfig.timersPanelYPosition.get() / 100f,
                Configs.hudConfig.timersPanelAlignment.get(),
                Component.literal("Timers & Crew"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showTimersPanel.get(); }
    @Override protected float configX() { return Configs.hudConfig.timersPanelXPosition.get() / 100f; }
    @Override protected float configY() { return Configs.hudConfig.timersPanelYPosition.get() / 100f; }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.timersPanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.timersPanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.timersPanelOpacity.get(); }

    @Override
    protected boolean alignTextToAnchor() {
        return false;
    }

    @Override
    protected Component title() {
        return Component.literal("ᴛɪᴍᴇʀѕ").withStyle(ChatFormatting.BOLD);
    }

    @Override
    protected List<Component> lines() {
        List<Component> lines = new ArrayList<>(TimersCrewBox.timers());
        if (!Configs.inventoryScreenConfig.timersCrew.get()) return lines;
        TimersCrewBox.Crew crew = TimersCrewBox.crew();
        if (crew.range() == null && crew.total() == 0) return lines;
        if (!lines.isEmpty()) lines.add(Component.empty());
        if (crew.range() != null) lines.add(crew.range());
        if (crew.total() > 0) {
            lines.add(Component.literal(crew.online() + "/" + crew.total() + " Online").withStyle(ChatFormatting.WHITE));
            lines.add(TimersCrewBox.legend());
            lines.addAll(crew.onlineNames());
            lines.addAll(crew.offlineNames());
        }
        return lines;
    }

    @Override
    protected List<Component> previewLines() {
        return TimersCrewBox.timers();
    }
}
