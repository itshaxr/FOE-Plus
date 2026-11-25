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
        super(dannypx.foe.helper.HudFine.get(Configs.hudConfig.timersPanelXPosition),
                dannypx.foe.helper.HudFine.get(Configs.hudConfig.timersPanelYPosition),
                Configs.hudConfig.timersPanelAlignment.get(),
                Component.literal("Timers"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showTimersPanel.get(); }
    @Override protected float configX() { return dannypx.foe.helper.HudFine.get(Configs.hudConfig.timersPanelXPosition); }
    @Override protected float configY() { return dannypx.foe.helper.HudFine.get(Configs.hudConfig.timersPanelYPosition); }
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
        // the community catches are their own HUD now (inside this one to start with: see HudCombine)
        return TimersCrewBox.timers();
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
    protected List<Component> previewLines() {
        return TimersCrewBox.timers();
    }
}
