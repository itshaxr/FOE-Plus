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

    static final List<String> EVENT_BOOSTERS = List.of("money", "xp");

    /// "⚡ Money Booster 2x · 59:12": a global booster and its time left, counting down.
    static Component booster(String kind, double multiplier, long msLeft) {
        String name = switch (kind) {
            case "luck" -> "Luck";
            case "scale" -> "Scale";
            case "xp" -> "XP";
            default -> "Money";
        };
        // The colours /active gives them.
        int color = switch (kind) {
            case "luck" -> 0x00AAAA;
            case "scale" -> 0x5555FF;
            case "xp" -> 0x55FFFF;
            default -> 0x55FF55;
        };
        String times = multiplier <= 0 ? "" : " " + (multiplier == Math.rint(multiplier)
                ? String.valueOf((long) multiplier) : String.valueOf(multiplier)) + "x";
        return join(Component.literal("⚡ " + name + " Booster").withColor(color),
                Component.literal(times).withStyle(ChatFormatting.WHITE),
                Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY),
                Component.literal(StoreTimerHandler.clock(msLeft)).withStyle(ChatFormatting.GOLD));
    }

    /// " · Shards 1 in 25": what the event does, unless descriptions are turned off.
    private static Component description(String text) {
        if (!Configs.hudConfig.eventsShowDescriptions.get()) return Component.empty();
        return join(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY),
                Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    /// "Fish Frenzy at Hawaii 33:37"
    static Component frenzy(String location, long msLeft) {
        return join(Component.literal("Fish Frenzy").withColor(FRENZY_COLOR),
                Component.literal(" at ").withStyle(ChatFormatting.DARK_GRAY),
                FoeLocations.styledName(location),
                Component.literal(" " + StoreTimerHandler.clock(msLeft)).withStyle(ChatFormatting.GOLD));
    }

    /// "♣ Blooming Oasis · +100% Pet Luck · 14:30" (the description can be turned off)
    static Component oasis(long msLeft, boolean exact) {
        return join(Component.literal("♣ Blooming Oasis").withColor(OASIS_COLOR),
                description("+100% Pet Luck"),
                msLeft > 0 ? Component.literal(" · " + StoreTimerHandler.clock(msLeft)).withStyle(ChatFormatting.GOLD)
                        : Component.empty());
    }

    /// "⚠ Gold Rush · Shards 1 in 25 · 14:30" (the description can be turned off)
    static Component goldRush(long msLeft, boolean exact) {
        return join(Component.literal("⚠ Gold Rush").withColor(GOLD_RUSH_COLOR),
                description("Shards 1 in 25"),
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
        StoreTimerHandler boosters = StoreTimerHandler.instance();
        // Money and XP boosters here; Luck and Scale ones show next to their numbers on the
        // Luck & Scale panel.
        for (String kind : EVENT_BOOSTERS) {
            long left = boosters.boosterRemainingMs(kind);
            if (left > 0) lines.add(booster(kind, boosters.boosterMultiplier(kind), left));
        }
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
                booster("money", 2, 59 * 60_000L + 12_000L),
                fabled("Danube River", 12 * 60_000L + 3_000L));
    }
}
