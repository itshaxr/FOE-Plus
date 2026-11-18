package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.renderer.TimersCrewBox;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The timers from the inventory's Timers & Crew box as a HUD panel: Moon, Tackle Shop, Cosmetic Store and Vote (what
 * they show is set in the inventory settings). Your crew and friends are in {@link CrewPanelElement}.
 */
public class TimersPanelElement extends PanelElement {
    public TimersPanelElement() {
        this(false);
    }

    public TimersPanelElement(boolean isCopy) {
        super(Configs.hudConfig.timersPanelXPosition.get() / 100f,
                Configs.hudConfig.timersPanelYPosition.get() / 100f,
                Configs.hudConfig.timersPanelAlignment.get(),
                Component.literal("Timers"),
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

    /// Made when first needed: the panel's constructor already asks for its lines.
    private CommunityCatches community;

    private CommunityCatches community() {
        if (community == null) community = new CommunityCatches();
        return community;
    }

    @Override
    protected List<Component> lines() {
        // the timers, then the community catches under a line; your crew and friends have their own panel
        CrewPanelElement.splitOnce();
        return withCommunity(TimersCrewBox.timers(), Configs.hudConfig.timersShowCommunity.get() ? community().lines() : List.of());
    }

    /// Package-private for tests: the timers, a line, then the community catches (no line when either is missing).
    static List<Component> withCommunity(List<Component> timers, List<Component> community) {
        if (community.isEmpty()) return timers;
        List<Component> out = new ArrayList<>(timers);
        if (!out.isEmpty()) out.add(titledRule("ᴄᴏᴍᴍᴜɴɪᴛʏ ᴄᴀᴛᴄʜᴇѕ"));
        out.addAll(community);
        return out;
    }

    @Override
    protected void beforeLine(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font, Component line, int x, int y) {
        community().beforeLine(g, font, line, x, y);
    }

    @Override
    protected List<Component> previewLines() {
        return withCommunity(TimersCrewBox.timers(), Configs.hudConfig.timersShowCommunity.get() ? community().previewLines() : List.of());
    }
}
