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
        super(dannypx.foe.helper.HudFine.get(Configs.hudConfig.eventsPanelXPosition),
                dannypx.foe.helper.HudFine.get(Configs.hudConfig.eventsPanelYPosition),
                Configs.hudConfig.eventsPanelAlignment.get(),
                Component.literal("Events"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showEventsPanel.get(); }
    @Override protected float configX() { return dannypx.foe.helper.HudFine.get(Configs.hudConfig.eventsPanelXPosition); }
    @Override protected float configY() { return dannypx.foe.helper.HudFine.get(Configs.hudConfig.eventsPanelYPosition); }
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

    static final List<String> EVENT_BOOSTERS = List.of("money", "xp", "luck", "scale");

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
        if (!Configs.hudConfig.eventsDescriptions.get()) return Component.empty();
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

    /// "🦶 Witching Hour at Cypress Lake · 2:10 left"
    static Component cryptid(AlertHandler.Alert alert, String location, long msLeft) {
        return join(alert.iconText(), Component.literal(alert.displayName).withColor(alert.color),
                Component.literal(" at ").withStyle(ChatFormatting.DARK_GRAY),
                FoeLocations.styledName(location),
                msLeft > 0 ? Component.literal(" · " + AlertHandler.timeLeft(msLeft)).withStyle(ChatFormatting.GOLD) : Component.empty());
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

    /// "⚡ Thunderstorm · +100 Bite Speed · 4:12 left" (or "· up 3:12" while how long it lasts isn't
    /// known yet). The weather is the same everywhere, so no place. The description can be turned off.
    static Component weather(dannypx.foe.handler.logic.WeatherEventHandler.Kind kind, long msLeft, long msSince) {
        return join(Component.literal(kind.icon + " " + kind.displayName).withColor(kind.color),
                description(kind.description),
                msLeft >= 0 ? Component.literal(" · " + StoreTimerHandler.clock(msLeft) + " left").withStyle(ChatFormatting.GOLD)
                        : Component.literal(" · up " + StoreTimerHandler.clock(Math.max(0L, msSince))).withStyle(ChatFormatting.GOLD));
    }

    /// "○ Blood Moon · 5:10 left" (or just the name while how long moons last isn't known).
    static Component moon(dannypx.foe.handler.logic.MoonTimerHandler.Moon moon, long msLeft) {
        return join(Component.literal("○ " + moon.displayName).withColor(moon.color),
                msLeft >= 0 ? Component.literal(" · " + StoreTimerHandler.clock(msLeft) + " left").withStyle(ChatFormatting.GOLD) : Component.empty());
    }

    @Override
    protected List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        EventTimesHandler events = EventTimesHandler.instance();
        var c = Configs.hudConfig;
        if (c.eventsShowFrenzy.get() && events.frenzyActive()) lines.add(frenzy(events.frenzyLocation(), events.frenzyRemainingMs()));
        if (c.eventsShowOasis.get() && events.oasisActive()) lines.add(oasis(events.oasisRemainingMs(), events.oasisExact()));
        if (c.eventsShowGoldRush.get() && events.goldRushActive()) lines.add(goldRush(events.goldRushRemainingMs(), events.goldRushExact()));
        // rain, thunderstorms and supercells (where you are, or still going where you left them)
        if (Configs.hudConfig.eventsShowWeather.get()) {
            long now = System.currentTimeMillis();
            var weather = dannypx.foe.handler.logic.WeatherEventHandler.instance();
            for (var w : weather.active()) {
                boolean on = switch (w.kind) {
                    case RAIN -> c.eventsShowRain.get();
                    case THUNDERSTORM -> c.eventsShowThunderstorm.get();
                    case SUPERCELL -> c.eventsShowSupercell.get();
                };
                if (on) lines.add(weather(w.kind, weather.msLeft(w, now), now - w.startedAt));
            }
            var moon = dannypx.foe.handler.logic.MoonTimerHandler.instance().getCurrentMoon();
            if (!c.eventsShowMoon.get()) { /* moons off */ }
            else if (moon != null) lines.add(moon(moon, AlertHandler.instance().moonMsLeft()));
            // up by the schedule, chat not in yet (which moon it is comes with that line)
            else if (dannypx.foe.handler.logic.MoonTimerHandler.instance().scheduledUp())
                lines.add(join(Component.literal("○ Moon").withColor(0xDDDDDD),
                        Component.literal(" · " + StoreTimerHandler.clock(AlertHandler.instance().moonMsLeft()) + " left").withStyle(ChatFormatting.GOLD)));
        }
        StoreTimerHandler boosters = StoreTimerHandler.instance();
        // Every global booster that's on: Money, XP, Luck and Scale (Luck and Scale also show next to
        // their numbers on the Luck & Scale panel).
        for (String kind : EVENT_BOOSTERS) {
            if (!c.eventsShowBoosters.get()) break;
            long left = boosters.boosterRemainingMs(kind);
            if (left > 0) lines.add(booster(kind, boosters.boosterMultiplier(kind), left));
        }
        AlertHandler alerts = AlertHandler.instance();
        // cryptids that are out (the Alerts panel counts down to them)
        for (AlertHandler.Active a : alerts.active()) {
            if (a.alert() == AlertHandler.Alert.FABLED || !AlertHandler.enabled(a.alert()) || !c.eventsShowCryptids.get()) continue;
            lines.add(cryptid(a.alert(), alerts.locationLabel(a.alert()), a.msLeft()));
        }
        if (c.eventsShowFabled.get() && alerts.isFabledActive()) {
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
                weather(dannypx.foe.handler.logic.WeatherEventHandler.Kind.THUNDERSTORM, 4 * 60_000L + 12_000L, 0L),
                moon(dannypx.foe.handler.logic.MoonTimerHandler.Moon.BLOOD, 5 * 60_000L + 10_000L),
                booster("money", 2, 59 * 60_000L + 12_000L),
                booster("luck", 2, 41 * 60_000L + 8_000L),
                fabled("Danube River", 12 * 60_000L + 3_000L));
    }
}
