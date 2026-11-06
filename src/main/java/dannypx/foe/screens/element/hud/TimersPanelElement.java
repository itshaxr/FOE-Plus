package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.ScoreboardHandler;
import dannypx.foe.handler.logic.CrewHandler;
import dannypx.foe.handler.logic.FriendsHandler;
import dannypx.foe.handler.logic.MoonTimerHandler;
import dannypx.foe.handler.logic.StoreTimerHandler;
import dannypx.foe.handler.store.ProfileDataHandler;
import dannypx.foe.type.Alignment;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Timers & Crew: the next moon, the Tackle Shop restock and the Cosmetic Store reset, then your
 * crew and friends: whether a crew member is in range (the crew bonus), how many are online,
 * and who (online first, crew in orange, friends in blue with a star).
 */
public class TimersPanelElement extends PanelElement {
    static final int CREW_COLOR = 0xFFAA00;
    static final int FRIEND_COLOR = 0x55AAFF;
    private static final int MOON_COLOR = 0x55FFFF;
    private static final int TACKLE_COLOR = 0xFFFF55;
    private static final int COSMETIC_COLOR = 0xFF55FF;

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

    /// "○ Moon 1:00:09": a coloured icon and name, then the value in white.
    static Component timer(String icon, String name, int color, String value) {
        return Component.empty()
                .append(Component.literal(icon + " " + name + " ").withColor(color))
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    /// One person on the list: a green (online) or grey (offline) square, then the name in the
    /// crew / friend colour, with a star for friends.
    static Component person(String name, boolean online, boolean crew, boolean friend) {
        int color = !online ? 0xAAAAAA : crew ? CREW_COLOR : FRIEND_COLOR;
        return Component.empty()
                .append(Component.literal("■ ").withStyle(online ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
                .append(Component.literal((friend ? "☆" : "") + name).withColor(color));
    }

    @Override
    protected List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        var hud = Configs.hudConfig;

        if (hud.timersPanelMoon.get()) {
            MoonTimerHandler moon = MoonTimerHandler.instance();
            MoonTimerHandler.Moon up = moon.getCurrentMoon();
            if (up != null) {
                lines.add(Component.literal("○ " + up.displayName + " is up").withColor(up.color));
            } else if (moon.isKnown()) {
                lines.add(timer("○", "Moon", MOON_COLOR, StoreTimerHandler.clock(moon.getTimeUntilNextMoon())));
            } else {
                lines.add(Component.empty().append(Component.literal("○ Moon ").withColor(MOON_COLOR))
                        .append(Component.literal("after the next moonrise").withStyle(ChatFormatting.GRAY)));
            }
        }
        StoreTimerHandler stores = StoreTimerHandler.instance();
        if (hud.timersPanelTackleShop.get()) {
            lines.add(timer("⚓", "Tackle Shop", TACKLE_COLOR, StoreTimerHandler.clock(stores.tackleShopRemainingMs())));
        }
        if (hud.timersPanelCosmeticStore.get()) {
            lines.add(timer("♦", "Cosmetic Store", COSMETIC_COLOR, StoreTimerHandler.clock(stores.cosmeticStoreRemainingMs())));
        }

        if (hud.timersPanelCrew.get()) addCrew(lines);
        return lines;
    }

    private static void addCrew(List<Component> lines) {
        Minecraft mc = Minecraft.getInstance();
        UUID me = mc.player == null ? null : mc.player.getUUID();
        boolean inCrew = !ScoreboardHandler.instance().getCrew().getString().isBlank();
        boolean crewLoaded = inCrew && ProfileDataHandler.instance().getProfileData().hasImportedCrew;
        Map<UUID, String> friends = FriendsHandler.instance().all();
        if (!inCrew && friends.isEmpty()) return;

        if (!lines.isEmpty()) lines.add(Component.empty());
        if (inCrew && !crewLoaded) {
            lines.add(join(label("ᴏᴘᴇɴ "), Component.literal("/crew").withStyle(ChatFormatting.AQUA), label(" ᴏɴᴄᴇ ᴛᴏ ʟᴏᴀᴅ ʏᴏᴜʀ ᴄʀᴇᴡ")));
        }
        if (crewLoaded) {
            lines.add(CrewHandler.instance().isCrewNearby()
                    ? Component.literal("✔ Member in Range").withStyle(ChatFormatting.GREEN)
                    : Component.literal("✖ No Member in Range").withStyle(ChatFormatting.RED));
        }

        // Everyone: crew first (as the crew list has them), then friends who aren't in the crew.
        LinkedHashMap<UUID, String> crew = new LinkedHashMap<>();
        if (crewLoaded) {
            for (Pair<UUID, String> m : CrewHandler.instance().getCrewListOrdered()) {
                if (!m.value1().equals(me)) crew.put(m.value1(), m.value2());
            }
        }
        List<Component> online = new ArrayList<>();
        List<Component> offline = new ArrayList<>();
        int total = 0, on = 0;
        for (Map.Entry<UUID, String> m : crew.entrySet()) {
            boolean isOn = FriendsHandler.isOnline(m.getKey());
            boolean friend = friends.containsKey(m.getKey());
            total++;
            if (isOn) { on++; online.add(person(m.getValue(), true, true, friend)); }
            else offline.add(person(m.getValue(), false, true, friend));
        }
        for (Map.Entry<UUID, String> f : friends.entrySet()) {
            if (crew.containsKey(f.getKey()) || f.getKey().equals(me)) continue;
            boolean isOn = FriendsHandler.isOnline(f.getKey());
            total++;
            if (isOn) { on++; online.add(person(f.getValue(), true, false, true)); }
            else offline.add(person(f.getValue(), false, false, true));
        }
        if (total == 0) return;

        lines.add(Component.literal(on + "/" + total + " Online").withStyle(ChatFormatting.WHITE));
        lines.add(Component.empty()
                .append(Component.literal("■").withColor(CREW_COLOR)).append(Component.literal("Crew ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("■").withColor(FRIEND_COLOR)).append(Component.literal("Friends").withStyle(ChatFormatting.GRAY)));
        lines.addAll(online);
        if (Configs.hudConfig.timersPanelOffline.get()) lines.addAll(offline);
    }

    @Override
    protected List<Component> previewLines() {
        return List.of(
                timer("○", "Moon", MOON_COLOR, "1:00:09"),
                timer("⚓", "Tackle Shop", TACKLE_COLOR, "57:09"),
                timer("♦", "Cosmetic Store", COSMETIC_COLOR, "1:57:10"),
                Component.empty(),
                Component.literal("✖ No Member in Range").withStyle(ChatFormatting.RED),
                Component.literal("3/7 Online").withStyle(ChatFormatting.WHITE),
                Component.empty()
                        .append(Component.literal("■").withColor(CREW_COLOR)).append(Component.literal("Crew ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("■").withColor(FRIEND_COLOR)).append(Component.literal("Friends").withStyle(ChatFormatting.GRAY)),
                person("Ewiksen", true, true, false),
                person("Aidanqt", true, false, true),
                person("Realstranden", true, false, true),
                person("Rigbi2", false, true, false),
                person("frogmarty889", false, true, false));
    }
}
