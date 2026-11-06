package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.AlertHandler;
import dannypx.foe.handler.logic.PetLocationHandler;
import dannypx.foe.helper.FoeLocations;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/// The alert hub: FOE's Witching Hour and the cryptid windows that are on right now, each with
/// its location, odds, drop and how long it has left, plus what starts soon. Hidden when nothing
/// is on (or coming up).
public class AlertPanelElement extends PanelElement {
    public AlertPanelElement() {
        this(false);
    }

    public AlertPanelElement(boolean isCopy) {
        super(Configs.hudConfig.alertPanelXPosition.get() / 100f,
                Configs.hudConfig.alertPanelYPosition.get() / 100f,
                Configs.hudConfig.alertPanelAlignment.get(),
                Component.literal("Alerts Panel"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showAlertPanel.get(); }
    @Override protected float configX() { return Configs.hudConfig.alertPanelXPosition.get() / 100f; }
    @Override protected float configY() { return Configs.hudConfig.alertPanelYPosition.get() / 100f; }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.alertPanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.alertPanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.alertPanelOpacity.get(); }

    @Override
    protected boolean alignTextToAnchor() {
        return false;
    }

    @Override
    protected Component title() {
        return Component.literal("ᴀʟᴇʀᴛѕ").withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD);
    }

    @Override
    protected List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        AlertHandler alerts = AlertHandler.instance();
        for (AlertHandler.Active active : alerts.active()) {
            AlertHandler.Alert alert = active.alert();
            if (!lines.isEmpty()) lines.add(Component.empty());
            FoeLocations.Location where = alerts.locationOf(alert);
            boolean here = where != null && where == PetLocationHandler.currentLocation();
            lines.add(join(Component.literal(alert.displayName.toUpperCase()).withColor(alert.color).withStyle(ChatFormatting.BOLD),
                    Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY),
                    dannypx.foe.helper.FoeLocations.styledName(alerts.locationLabel(alert)),
                    here ? Component.literal(" ✔").withStyle(ChatFormatting.GREEN) : Component.empty()));
            if (alert == AlertHandler.Alert.FABLED) {
                lines.add(join(label(alert.odds), Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY),
                        active.msSince() >= 0
                                ? Component.literal("up " + AlertHandler.clock(active.msSince())).withStyle(ChatFormatting.YELLOW)
                                : Component.literal("until caught").withStyle(ChatFormatting.YELLOW)));
            } else {
                lines.add(join(label(alert.odds),
                        Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY),
                        Component.literal(AlertHandler.timeLeft(active.msLeft())).withStyle(timerColor(active.msLeft()))));
            }
        }
        int ahead = Configs.hudConfig.alertUpcomingMinutes.get();
        if (ahead > 0) {
            List<AlertHandler.Upcoming> soon = alerts.upcoming(ahead * 60_000L);
            if (!soon.isEmpty() && !lines.isEmpty()) lines.add(Component.empty());
            for (AlertHandler.Upcoming next : soon) {
                lines.add(join(label("ɴᴇxᴛ: "), Component.literal(next.what()).withStyle(ChatFormatting.WHITE),
                        label(" ɪɴ "), Component.literal(AlertHandler.clock(next.msUntil())).withStyle(ChatFormatting.YELLOW)));
            }
        }
        return lines;
    }

    private static ChatFormatting timerColor(long ms) {
        if (ms < 0) return ChatFormatting.GRAY;
        if (ms < 60_000L) return ChatFormatting.RED;
        if (ms < 3 * 60_000L) return ChatFormatting.GOLD;
        return ChatFormatting.GREEN;
    }

    @Override
    protected List<Component> previewLines() {
        AlertHandler.Alert alert = AlertHandler.Alert.WITCHING_HOUR;
        return List.of(
                join(Component.literal(alert.displayName.toUpperCase()).withColor(alert.color).withStyle(ChatFormatting.BOLD),
                        Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY),
                        dannypx.foe.helper.FoeLocations.styledName("Cypress Lake")),
                join(label(alert.odds), Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY),
                        Component.literal("2:10 left").withStyle(ChatFormatting.GREEN)),
                join(label("ɴᴇxᴛ: "), Component.literal("Titanoboa").withStyle(ChatFormatting.WHITE),
                        label(" ɪɴ "), Component.literal("1:40").withStyle(ChatFormatting.YELLOW)));
    }
}
