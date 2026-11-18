package dannypx.foe.handler.renderer;

import dannypx.foe.handler.logic.AlertHandler;
import dannypx.foe.handler.logic.EventTimesHandler;
import dannypx.foe.handler.logic.StoreTimerHandler;
import dannypx.foe.helper.FoeLocations;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What's going on at a place right now, for the travel map: a Fabled fish waiting to be caught, a Fish
 * Frenzy and its time left, or one of the place's own events (Titanoboa, Bessie, the Witching Hour...).
 * Worked out afresh every frame, so a frenzy's clock runs down on the map and the mark goes as soon as
 * the frenzy ends or the Fabled fish is caught.
 */
final class PlaceEvents {
    private PlaceEvents() {}

    static final int FABLED_COLOR = 0xFC3E35, FRENZY_COLOR = 0x55FF55;

    /// One line of the box over a place's tooltip: a title in the event's colour, and its time on the right.
    record Banner(net.minecraft.network.chat.Component icon, String title, String detail, int color) {}

    /// What's on at the place a map item stands for, from what FOE+ has heard in chat and /events.
    static List<Banner> live(FoeLocations.Location place, String itemName) {
        EventTimesHandler ev = EventTimesHandler.instance();
        AlertHandler al = AlertHandler.instance();
        return at(place, itemName,
                ev.frenzyActive() ? ev.frenzyLocation() : "", ev.frenzyRemainingMs(),
                al.isFabledActive(), al.fabledLocationName(), al.active());
    }

    /// Package-private for tests: the same, from the facts given. `itemName` is the menu item's name: the
    /// server adds " *FRENZY!*" to it during a frenzy, which counts even when FOE+ missed the announcement.
    private static net.minecraft.network.chat.Component sym(String s, int color) {
        return net.minecraft.network.chat.Component.literal(s.endsWith(" ") ? s : s + " ").withColor(color);
    }

    static List<Banner> at(FoeLocations.Location place, String itemName, String frenzyAt, long frenzyMsLeft,
                           boolean fabledOn, String fabledAt, List<AlertHandler.Active> alerts) {
        List<Banner> out = new ArrayList<>();
        if (place == null) return out;
        String name = itemName == null ? "" : itemName.toUpperCase(Locale.ROOT);
        if ((fabledOn && fabledAt != null && FoeLocations.fromName(fabledAt) == place) || name.contains("FABLED")) {
            out.add(new Banner(sym("\u2605", FABLED_COLOR), "Fabled Fish!", "until caught", FABLED_COLOR));
        }
        boolean frenzyKnown = frenzyAt != null && !frenzyAt.isBlank() && FoeLocations.fromName(frenzyAt) == place && frenzyMsLeft > 0;
        if (frenzyKnown) out.add(new Banner(sym("⚡", FRENZY_COLOR), "Frenzy!", StoreTimerHandler.clock(frenzyMsLeft) + " left", FRENZY_COLOR));
        else if (name.contains("FRENZY")) out.add(new Banner(sym("⚡", FRENZY_COLOR), "Frenzy!", "on now", FRENZY_COLOR));
        if (alerts != null) {
            for (AlertHandler.Active a : alerts) {
                if (a.alert().kind == AlertHandler.Kind.FABLED || a.alert().location != place) continue;
                out.add(new Banner(a.alert().icon() != null ? a.alert().iconText() : sym("\u00BB ", a.alert().color), a.alert().displayName + "!", a.msLeft() >= 0 ? AlertHandler.clock(a.msLeft()) + " left" : "on now", a.alert().color));
            }
        }
        return out;
    }
}
