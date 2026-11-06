package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.AlertHandler;
import dannypx.foe.handler.logic.EventTimesHandler;
import dannypx.foe.handler.logic.StoreTimerHandler;
import dannypx.foe.helper.FoeLocations;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Events: a Fish Frenzy (where, and how long it has left of its hour, from chat and /events)
 * and an active Fabled event (until someone catches it). Hidden when nothing is on.
 */
public class EventsPanelElement extends PanelElement {
    private static final int FRENZY_COLOR = 0x55FF55;
    private static final int FABLED_COLOR = 0xFC3E35;
    private static final int OASIS_COLOR = 0xFC54FC;
    private static final int GOLD_RUSH_COLOR = 0xFFAA00;
    private static final FontDescription ICONS = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath(dannypx.foe.FishOnMCExtras.MOD_ID, "stat_icons"));

    public EventsPanelElement() {
        this(false);
    }

    public EventsPanelElement(boolean isCopy) {
        super(Configs.hudConfig.eventsPanelXPosition.get() / 100f,
                Configs.hudConfig.eventsPanelYPosition.get() / 100f,
                Configs.hudConfig.eventsPanelAlignment.get(),
                Component.literal("Events"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showEventsPanel.get(); }
    @Override protected float configX() { return Configs.hudConfig.eventsPanelXPosition.get() / 100f; }
    @Override protected float configY() { return Configs.hudConfig.eventsPanelYPosition.get() / 100f; }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.eventsPanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.eventsPanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.eventsPanelOpacity.get(); }

    @Override
    protected boolean alignTextToAnchor() {
        return false;
    }

    @Override
    protected Component title() {
        return Component.literal("ᴇᴠᴇɴᴛѕ").withStyle(ChatFormatting.BOLD);
    }

    /// "Fish Frenzy at Hawaii 33:37"
    static Component frenzy(String location, long msLeft) {
        return join(Component.literal("Fish Frenzy").withColor(FRENZY_COLOR),
                Component.literal(" at ").withStyle(ChatFormatting.DARK_GRAY),
                FoeLocations.styledName(location),
                Component.literal(" " + StoreTimerHandler.clock(msLeft)).withStyle(ChatFormatting.GOLD));
    }

    /// "♣ Blooming Oasis · +100% Pet Luck · ~14:30" (~ while the time is FOE+'s estimate)
    static Component oasis(long msLeft, boolean exact) {
        return join(Component.literal("♣ Blooming Oasis").withColor(OASIS_COLOR),
                Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY),
                Component.literal("+100% Pet Luck").withStyle(ChatFormatting.GRAY),
                msLeft > 0 ? Component.literal(" · " + StoreTimerHandler.clock(msLeft)).withStyle(ChatFormatting.GOLD)
                        : Component.empty());
    }

    /// "⚠ Gold Rush · Shards 1 in 25 · ~14:30" (~ while the time is FOE+'s estimate)
    static Component goldRush(long msLeft, boolean exact) {
        return join(Component.literal("⚠ Gold Rush").withColor(GOLD_RUSH_COLOR),
                Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY),
                Component.literal("Shards 1 in 25").withStyle(ChatFormatting.GRAY),
                msLeft > 0 ? Component.literal(" · " + StoreTimerHandler.clock(msLeft)).withStyle(ChatFormatting.GOLD)
                        : Component.empty());
    }

    /// "★ Fabled Fish at Danube River · up 12:03"
    static Component fabled(String location, long msSince) {
        return join(Component.literal("★ Fabled Fish").withColor(FABLED_COLOR),
                Component.literal(" at ").withStyle(ChatFormatting.DARK_GRAY),
                FoeLocations.styledName(location.isEmpty() ? "Unknown location" : location),
                msSince >= 0
                        ? Component.literal(" · up " + StoreTimerHandler.clock(msSince)).withStyle(ChatFormatting.GOLD)
                        : Component.literal(" · until caught").withStyle(ChatFormatting.GOLD));
    }

    @Override
    protected List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        EventTimesHandler events = EventTimesHandler.instance();
        if (events.frenzyActive()) lines.add(frenzy(events.frenzyLocation(), events.frenzyRemainingMs()));
        if (events.oasisActive()) lines.add(oasis(events.oasisRemainingMs(), events.oasisExact()));
        if (events.goldRushActive()) lines.add(goldRush(events.goldRushRemainingMs(), events.goldRushExact()));
        AlertHandler alerts = AlertHandler.instance();
        if (alerts.isFabledActive()) {
            long since = -1;
            for (AlertHandler.Active a : alerts.active()) if (a.alert() == AlertHandler.Alert.FABLED) since = a.msSince();
            lines.add(fabled(alerts.fabledLocationName(), since));
        }
        return lines;
    }

    @Override
    protected List<Component> previewLines() {
        return List.of(frenzy("Hawaii", 33 * 60_000L + 37_000L), oasis(14 * 60_000L + 30_000L, false),
                goldRush(11 * 60_000L + 12_000L, false),
                fabled("Danube River", 12 * 60_000L + 3_000L));
    }
}
