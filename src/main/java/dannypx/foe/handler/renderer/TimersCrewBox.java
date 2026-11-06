package dannypx.foe.handler.renderer;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.ScoreboardHandler;
import dannypx.foe.handler.logic.CrewHandler;
import dannypx.foe.handler.logic.FriendsHandler;
import dannypx.foe.handler.logic.MoonTimerHandler;
import dannypx.foe.handler.logic.StoreTimerHandler;
import dannypx.foe.handler.store.ProfileDataHandler;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * The box left of your inventory: the next moon, the Tackle Shop restock (✔ once you've checked
 * it), the Cosmetic Store reset and your votes; then whether a crew member is in range (the crew
 * bonus); then your crew and friends, online first (crew in orange, friends in blue, friends in your crew in light blue with a star),
 * with a + to add a friend. The list scrolls when it's longer than the inventory.
 */
public final class TimersCrewBox {
    private TimersCrewBox() {}

    static final int CREW_COLOR = 0xFFAA00;
    static final int FRIEND_COLOR = 0x55AAFF;
    /// A friend who's also in your crew: light blue, with a star in the crew colour.
    static final int CREW_FRIEND_COLOR = 0x9FE4FF;
    private static final int STAR_OFFLINE_COLOR = 0xA8772A;
    private static final int MOON_COLOR = 0x55FFFF;
    private static final int TACKLE_COLOR = 0xFFFF55;
    private static final int COSMETIC_COLOR = 0xFF55FF;
    private static final int VOTE_COLOR = 0x55FFFF;
    private static final int BG = 0xC0101418;
    private static final int PAD = 4;
    private static final int LINE = 12;
    private static final int GAP = 3;
    private static final FontDescription ICONS = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath(dannypx.foe.FishOnMCExtras.MOD_ID, "stat_icons"));

    private static int scroll = 0;
    /// The names area last drawn (for the mouse wheel): x, y, w, h.
    private static int[] namesArea = null;

    /// "○ Moon 1:00:09": a coloured icon and name, then the value in white.
    static Component timer(String icon, String name, int color, Component value) {
        return Component.empty().append(Component.literal(icon + " " + name + " ").withColor(color)).append(value);
    }

    /// One person: a green (online) or grey (offline) square, then the name: orange for crew,
    /// blue for a friend, light blue with an orange (crew) star for a friend who's in your crew.
    static Component person(String name, boolean online, boolean crew, boolean friend) {
        int color = !online ? 0xAAAAAA : crew && friend ? CREW_FRIEND_COLOR : crew ? CREW_COLOR : FRIEND_COLOR;
        Component line = Component.empty()
                .append(Component.literal("■ ").withStyle(online ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        if (crew && friend) line = line.copy().append(Component.literal("★ ").withColor(online ? CREW_COLOR : STAR_OFFLINE_COLOR));
        return line.copy().append(Component.literal(name).withColor(color));
    }

    public static List<Component> timers() {
        var cfg = Configs.inventoryScreenConfig;
        List<Component> lines = new ArrayList<>();
        if (cfg.timersMoon.get()) {
            MoonTimerHandler moon = MoonTimerHandler.instance();
            MoonTimerHandler.Moon up = moon.getCurrentMoon();
            if (up != null) lines.add(Component.literal("○ " + up.displayName + " is up").withColor(up.color));
            else if (moon.isKnown()) lines.add(timer("○", "Moon", MOON_COLOR, white(StoreTimerHandler.clock(moon.getTimeUntilNextMoon()))));
            else lines.add(timer("○", "Moon", MOON_COLOR, Component.literal("after a moonrise").withStyle(ChatFormatting.GRAY)));
        }
        StoreTimerHandler stores = StoreTimerHandler.instance();
        if (cfg.timersTackleShop.get()) {
            Component line = timer("⚓", "Tackle Shop", TACKLE_COLOR, white(StoreTimerHandler.clock(stores.tackleShopRemainingMs())));
            lines.add(stores.tackleShopChecked() ? Component.empty().append(line).append(Component.literal(" ✔").withStyle(ChatFormatting.GREEN)) : line);
        }
        if (cfg.timersCosmeticStore.get()) {
            lines.add(timer("♦", "Cosmetic Store", COSMETIC_COLOR, white(StoreTimerHandler.clock(stores.cosmeticStoreRemainingMs()))));
        }
        if (cfg.timersVote.get()) {
            int votes = stores.votesToday();
            boolean all = votes >= StoreTimerHandler.VOTE_SITES;
            lines.add(timer("✉", "Vote", VOTE_COLOR, Component.empty()
                    .append(Component.literal(votes + "/" + StoreTimerHandler.VOTE_SITES).withStyle(all ? ChatFormatting.GREEN : ChatFormatting.WHITE))
                    .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(all ? white(StoreTimerHandler.clock(stores.nextVoteInMs())) : Component.literal("vote now").withStyle(ChatFormatting.GREEN))));
        }
        return lines;
    }

    private static Component white(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    /// The crew part: everything needed to draw it.
    public record Crew(Component range, int online, int total, List<Component> onlineNames, List<Component> offlineNames) {}

    /// "■Crew ■Friends"
    public static Component legend() {
        return Component.empty()
                .append(Component.literal("■").withColor(CREW_COLOR)).append(Component.literal("Crew ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("■").withColor(FRIEND_COLOR)).append(Component.literal("Friends").withStyle(ChatFormatting.GRAY));
    }

    public static Crew crew() {
        Minecraft mc = Minecraft.getInstance();
        UUID me = mc.player == null ? null : mc.player.getUUID();
        boolean inCrew = !ScoreboardHandler.instance().getCrew().getString().isBlank();
        boolean loaded = inCrew && ProfileDataHandler.instance().getProfileData().hasImportedCrew;
        Map<UUID, String> friends = FriendsHandler.instance().all();

        Component range = null;
        if (inCrew && !loaded) {
            range = Component.empty().append(Component.literal("Open ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("/crew").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" to load your crew").withStyle(ChatFormatting.GRAY));
        } else if (loaded) {
            range = CrewHandler.instance().isCrewNearby()
                    ? Component.literal("✔ Member in Range").withStyle(ChatFormatting.GREEN)
                    : Component.empty().append(Component.literal("").withStyle(s -> s.withFont(ICONS).withColor(0xFF5555)))
                            .append(Component.literal(" No Member in Range").withStyle(ChatFormatting.RED));
        }

        LinkedHashMap<UUID, String> crew = new LinkedHashMap<>();
        if (loaded) {
            for (Pair<UUID, String> m : CrewHandler.instance().getCrewListOrdered()) {
                if (!m.value1().equals(me)) crew.put(m.value1(), m.value2());
            }
        }
        List<Component> on = new ArrayList<>(), off = new ArrayList<>();
        for (Map.Entry<UUID, String> m : crew.entrySet()) {
            boolean isOn = FriendsHandler.isOnline(m.getKey());
            (isOn ? on : off).add(person(m.getValue(), isOn, true, friends.containsKey(m.getKey())));
        }
        for (Map.Entry<UUID, String> f : friends.entrySet()) {
            if (crew.containsKey(f.getKey()) || f.getKey().equals(me)) continue;
            boolean isOn = FriendsHandler.isOnline(f.getKey());
            (isOn ? on : off).add(person(f.getValue(), isOn, false, true));
        }
        if (!Configs.inventoryScreenConfig.timersOffline.get()) off.clear();
        return new Crew(range, on.size(), crew.size() + (int) friends.keySet().stream().filter(k -> !crew.containsKey(k) && !k.equals(me)).count(), on, off);
    }

    /// Draws the box with its right edge at {@code right}, from {@code top}, no lower than
    /// {@code maxBottom}; places the + button next to "Online".
    static void render(GuiGraphics g, Font font, int right, int top, int maxBottom, AbstractWidget plus) {
        var cfg = Configs.inventoryScreenConfig;
        List<Component> timers = timers();
        Crew crew = cfg.timersCrew.get() ? crew() : null;
        Component header = crew == null ? null : Component.literal(crew.online() + "/" + crew.total() + " Online").withStyle(ChatFormatting.WHITE);
        Component legend = legend();
        Component empty = Component.literal("Add a friend with +").withStyle(ChatFormatting.DARK_GRAY);

        int w = 0;
        for (Component c : timers) w = Math.max(w, font.width(c));
        if (crew != null) {
            if (crew.range() != null) w = Math.max(w, font.width(crew.range()));
            w = Math.max(w, font.width(header) + 14);
            w = Math.max(w, font.width(legend));
            if (crew.total() == 0) w = Math.max(w, font.width(empty));
            for (Component c : crew.onlineNames()) w = Math.max(w, font.width(c) + 4);
            for (Component c : crew.offlineNames()) w = Math.max(w, font.width(c) + 4);
        }
        int boxW = w + PAD * 2;
        int left = right - boxW;
        int y = top;

        if (!timers.isEmpty()) {
            int h = timers.size() * LINE - 3 + PAD * 2;
            g.fill(left, y, right, y + h, BG);
            g.fill(right - 1, y, right, y + h, 0xFF55FFFF);
            int ty = y + PAD;
            for (Component c : timers) { g.drawString(font, c, left + PAD, ty, 0xFFFFFFFF, true); ty += LINE; }
            y += h + GAP;
        }
        if (plus != null) plus.visible = false;
        namesArea = null;
        if (crew == null) return;

        if (crew.range() != null) {
            int h = LINE - 3 + PAD * 2;
            g.fill(left, y, right, y + h, BG);
            g.fill(left, y + h - 1, right, y + h, CrewHandler.instance().isCrewNearby() ? 0xFF55FF55 : 0xFFFF5555);
            g.drawString(font, crew.range(), left + PAD, y + PAD, 0xFFFFFFFF, true);
            y += h + GAP;
        }

        // Header, legend, then the names (online, a divider, offline), scrolling if needed.
        List<Component> names = new ArrayList<>(crew.onlineNames());
        int divider = crew.offlineNames().isEmpty() || crew.onlineNames().isEmpty() ? -1 : names.size();
        names.addAll(crew.offlineNames());
        if (crew.total() == 0) names = List.of(empty);

        int headH = PAD + LINE * 2;
        int boxBottomWanted = y + headH + names.size() * LINE + PAD - 3;
        int boxBottom = Math.min(boxBottomWanted, Math.max(y + headH + LINE + PAD, maxBottom));
        g.fill(left, y, right, boxBottom, BG);
        g.drawString(font, header, left + PAD, y + PAD, 0xFFFFFFFF, true);
        if (plus != null) {
            plus.visible = true;
            plus.setX(right - PAD - plus.getWidth() + 1);
            plus.setY(y + PAD - 2);
        }
        g.drawString(font, legend, left + PAD, y + PAD + LINE, 0xFFFFFFFF, true);

        int listTop = y + headH;
        int listH = boxBottom - PAD + 3 - listTop;
        int visible = Math.max(1, listH / LINE);
        int maxScroll = Math.max(0, names.size() - visible);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        namesArea = new int[]{left, listTop, boxW, listH};
        g.enableScissor(left, listTop, right, listTop + listH);
        int ny = listTop - scroll * LINE;
        for (int i = 0; i < names.size(); i++) {
            if (i == divider) g.fill(left + PAD, ny - 2, right - PAD - 3, ny - 1, 0x60FFFFFF);
            g.drawString(font, names.get(i), left + PAD, ny, 0xFFFFFFFF, true);
            ny += LINE;
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int barH = Math.max(8, listH * visible / names.size());
            int barY = listTop + (listH - barH) * scroll / maxScroll;
            g.fill(right - 3, listTop, right - 1, listTop + listH, 0x40FFFFFF);
            g.fill(right - 3, barY, right - 1, barY + barH, 0xFFC0C0C0);
        }
    }

    /// Mouse wheel over the names: scroll them.
    static boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int[] a = namesArea;
        if (a == null || mouseX < a[0] || mouseX > a[0] + a[2] || mouseY < a[1] || mouseY > a[1] + a[3]) return false;
        scroll += amount > 0 ? -1 : 1;
        if (scroll < 0) scroll = 0;
        return true;
    }
}
