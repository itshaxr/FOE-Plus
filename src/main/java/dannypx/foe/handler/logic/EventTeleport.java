package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.helper.FoeLocations;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * When chat says an event is on at a place ("FISH FRENZY » A Fish Frenzy has begun at Hawaii!", "Visit the Danube River
 * to ...", a mini event's start), FOE+ puts a [Teleport] button under it: one click opens /travel and takes you to
 * your favourite spot there (the ★ on the travel map), or its first spot when you have no favourite there.
 */
public final class EventTeleport {
    private EventTeleport() {}

    /// A line about an event: its tag ("FISH FRENZY »", "FABLED »", "MINI EVENT »", ...) or the fabled event's "Visit the".
    static final Pattern EVENT = Pattern.compile("^\\s*(?:[A-Z][A-Z' ]{2,30}\\s*[»>]|Visit the\\b)");
    /// Tags that name a place without an event to go to.
    static final Pattern NOT_EVENT = Pattern.compile("^\\s*(CONTESTS?|QUESTS?|DAILY MISSIONS|SELL|PETS|TIP|TP|DENY|CREWS?|CREW CHAT|FRIENDS?|PARTY|AUCTION|TACKLE SHOP|REACTIONS|SHOP|STORE|WARP|TRAVEL)\\b");
    static final Pattern ENDED = Pattern.compile("\\b(ended|over|finished|expired|caught by|has set|dissipated|faded)\\b", Pattern.CASE_INSENSITIVE);
    /// Only when it starts (not the "is active at" reminders /events and joining bring).
    static final Pattern STARTS = Pattern.compile("\\b(begun|began|started|starting|spawned|appeared|kicked off|now active)\\b|^\\s*Visit the\\b", Pattern.CASE_INSENSITIVE);
    static final long AGAIN_MS = 3L * 60_000L;

    private static final Map<FoeLocations.Location, Long> offered = new EnumMap<>(FoeLocations.Location.class);

    /// Package-private for tests: the place an event line sends you to, or null.
    static FoeLocations.Location placeOf(String text) {
        if (text == null || !EVENT.matcher(text).find() || NOT_EVENT.matcher(text).find() || ENDED.matcher(text).find()
                || !STARTS.matcher(text).find()) return null;
        String lower = text.toLowerCase(Locale.ROOT);
        FoeLocations.Location best = null;
        int bestLen = 0;
        for (FoeLocations.Location l : FoeLocations.Location.values()) {
            String name = FoeLocations.displayName(l);
            if (name == null) continue;
            // the full name, or its first word for the long ones ("Toledo Bend", "Lofoten")
            String n = name.toLowerCase(Locale.ROOT);
            String first = n.contains(" ") ? n.substring(0, n.indexOf(' ')) : n;
            boolean hit = lower.contains(n) || (first.length() >= 5 && !first.equals("great") && !first.equals("cape") && lower.contains(first));
            if (hit && n.length() > bestLen) { best = l; bestLen = n.length(); }
        }
        return best;
    }

    /// Every game message: a [Teleport] line under an event at a place (once a place, every few minutes).
    public static void onGameMessage(Component message) {
        if (!Configs.hudConfig.eventTeleportButton.get() || EventTimesHandler.instance().answering()) return;
        String text = ChatFormatting.stripFormatting(message.getString());
        FoeLocations.Location place = placeOf(text);
        if (place == null) return;
        long now = System.currentTimeMillis();
        Long last = offered.get(place);
        if (last != null && now - last < AGAIN_MS) return;
        offered.put(place, now);
        // after the server's line, not before it
        CodeExecuterHandler.runLater(1, () -> dannypx.foe.helper.FoeChat.send(line(place)));
    }

    static MutableComponent line(FoeLocations.Location place) {
        return dannypx.foe.helper.FoeChat.prefix().append(button(place, "[Teleport to " + FoeLocations.displayName(place) + "]"));
    }

    /// A [Teleport] button for FOE+'s own event lines (a cryptid that's out), or empty when the buttons are off. Counts
    /// as offered, so the server's line about the same event adds no second one.
    public static Component inline(FoeLocations.Location place) {
        if (place == null || !Configs.hudConfig.eventTeleportButton.get()) return Component.empty();
        offered.put(place, System.currentTimeMillis());
        return Component.literal(" ").append(button(place, "[Teleport]"));
    }

    private static Component button(FoeLocations.Location place, String label) {
        String fav = null;
        try {
            fav = dannypx.foe.handler.renderer.TravelMapHandler.favouriteAt(place);
        } catch (RuntimeException ignored) {}
        String where = fav == null || fav.isBlank() ? "its first spot" : "your favourite, " + fav;
        return Component.literal(label).withStyle(st -> st.withColor(0x55FF55).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("foe travel " + place.id))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Opens /travel and takes you to "
                        + FoeLocations.displayName(place) + ", " + where))));
    }
}
