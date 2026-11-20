package dannypx.foe.nearby_dispatcher.types;

import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.handler.logic.TextDisplayHandler;
import dannypx.foe.nearby_dispatcher.NearbyDispatcher;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public class ModUserType {
    private static final int PACKET_TYPE = 4;
    /// FOE+ (this merged build) announces itself on its own type too, so it can be told apart from
    /// plain FOE-R. Older FOE-R clients ignore types they don't know.
    private static final int FOE_PLUS_TYPE = 43;
    private static final byte[] FOE_PLUS_PAYLOAD = {'F', '+'};
    /// The colour you picked for your + ({'C', index}). Its own type, so older FOE+ versions,
    /// which only know type 43, still see you as a FOE+ user.
    private static final int COLOR_TYPE = 44;
    /// Your 3D bobber preset ({'B', id}), so FOE+ users see each other's bobbers. A preset an
    /// older version doesn't have shows as Classic there.
    private static final int BOBBER_TYPE = 45;
    /// Your + in a colour of your own: 'R', 1 (or 0 for none), red, green, blue. FOE+ versions before it only read
    /// the list colour sent with it ({@link #COLOR_TYPE}), the nearest one.
    private static final int CUSTOM_COLOR_TYPE = 46;
    private static final Map<UUID, dannypx.foe.type.BobberStyle> bobberStyles = new java.util.concurrent.ConcurrentHashMap<>();

    private static final double NEARBY_RANGE = 24.0;
    private static final int NEWCOMER_DELAY_TICKS = 10;

    private static final Set<UUID> announcedTo = new HashSet<>();
    private static final Map<UUID, Integer> ticksInRange = new HashMap<>();

    /// Remembered FOE+ users whose FOE+ has answered this session, and how long each one that hasn't has
    /// been near you since you told it you're here. Near you this long with no answer: they don't use FOE+
    /// now, and their + goes.
    private static final Set<UUID> confirmed = new HashSet<>();
    private static final Map<UUID, Integer> unansweredTicks = new HashMap<>();
    /// Counted only while you stand still (FOE+ messages only go out while you do) and they're near you:
    /// a minute of that with no answer.
    static final int FORGET_AFTER_TICKS = 60 * 20;
    private static net.minecraft.core.BlockPos lastPos = null;
    /// When we last answered each FOE+ user's hello (we answer once a minute at most).
    private static final Map<UUID, Long> answeredAt = new java.util.concurrent.ConcurrentHashMap<>();

    public static void init() {
        NearbyDispatcher.register(PACKET_TYPE, ModUserType::onReceive);
        NearbyDispatcher.register(FOE_PLUS_TYPE, ModUserType::onReceiveFoePlus);
        NearbyDispatcher.register(COLOR_TYPE, ModUserType::onReceiveColor);
        NearbyDispatcher.register(BOBBER_TYPE, ModUserType::onReceiveBobber);
        NearbyDispatcher.register(CUSTOM_COLOR_TYPE, ModUserType::onReceiveCustomColor);
        ClientTickEvents.END_CLIENT_TICK.register(ModUserType::tick);
    }

    private static void tick(Minecraft minecraft) {
        LocalPlayer self = minecraft.player;
        if (self == null || minecraft.level == null || !ConnectionHandler.instance().isOnServer()) {
            announcedTo.clear();
            ticksInRange.clear();
            bobberStyles.clear();
            confirmed.clear();
            unansweredTicks.clear();
            answeredAt.clear();
            return;
        }

        // a remembered FOE+ user near you who you've said hello to, and who hasn't answered in a minute
        boolean still = self.blockPosition().equals(lastPos);
        lastPos = self.blockPosition();
        for (Player other : still ? minecraft.level.players() : List.<Player>of()) {
            UUID id = other.getUUID();
            if (other == self || confirmed.contains(id) || !announcedTo.contains(id)
                    || !TextDisplayHandler.instance().isFoePlusUser(id)
                    || other.distanceToSqr(self) >= NEARBY_RANGE * NEARBY_RANGE) continue;
            if (unansweredTicks.merge(id, 1, Integer::sum) >= FORGET_AFTER_TICKS) {
                unansweredTicks.remove(id);
                TextDisplayHandler.instance().forgetFoePlusUser(id);
            }
        }

        Set<UUID> loaded = new HashSet<>();
        Set<UUID> readyNewcomers = new HashSet<>();
        for (Player other : minecraft.level.players()) {
            if (other == self) continue;
            UUID otherUuid = other.getUUID();
            loaded.add(otherUuid);
            if (announcedTo.contains(otherUuid)) continue;

            if (other.distanceToSqr(self) < NEARBY_RANGE * NEARBY_RANGE) {
                if (ticksInRange.merge(otherUuid, 1, Integer::sum) >= NEWCOMER_DELAY_TICKS) {
                    readyNewcomers.add(otherUuid);
                }
            } else {
                ticksInRange.remove(otherUuid);
            }
        }

        ticksInRange.keySet().retainAll(loaded);
        if (readyNewcomers.isEmpty()) return;

        announcedTo.addAll(readyNewcomers);
        readyNewcomers.forEach(ticksInRange::remove);

        NearbyDispatcher.queue(PACKET_TYPE, new byte[]{1});
        NearbyDispatcher.queue(FOE_PLUS_TYPE, FOE_PLUS_PAYLOAD);
        NearbyDispatcher.queue(COLOR_TYPE, colorPayload());
        NearbyDispatcher.queue(CUSTOM_COLOR_TYPE, customColorPayload());
        NearbyDispatcher.queue(BOBBER_TYPE, bobberPayload());
    }

    private static byte[] customColorPayload() {
        int rgb = dannypx.foe.screens.element.hud.GameXpBar.parse(dannypx.foe.config.Configs.rendererConfig.foePlusCustomColor.get());
        return rgb < 0 ? new byte[]{'R', 0, 0, 0, 0} : new byte[]{'R', 1, (byte) (rgb >> 16), (byte) (rgb >> 8), (byte) rgb};
    }

    private static void onReceiveCustomColor(UUID sender, byte[] payload) {
        if (payload.length != 5 || payload[0] != 'R') return;
        int rgb = payload[1] == 0 ? -1 : (payload[2] & 0xFF) << 16 | (payload[3] & 0xFF) << 8 | (payload[4] & 0xFF);
        TextDisplayHandler.instance().setFoePlusRgb(sender, rgb);
    }

    private static byte[] bobberPayload() {
        return new byte[]{'B', (byte) dannypx.foe.config.Configs.rendererConfig.bobberStyle.get().id()};
    }

    /// You picked a new bobber: tell the players around you (new arrivals get it anyway).
    public static void announceBobber() {
        if (!ConnectionHandler.instance().isOnServer()) return;
        NearbyDispatcher.queue(BOBBER_TYPE, bobberPayload());
    }

    private static void onReceiveBobber(UUID sender, byte[] payload) {
        if (payload.length != 2 || payload[0] != 'B') return;
        dannypx.foe.type.BobberStyle style = dannypx.foe.type.BobberStyle.byId(payload[1] & 0xFF);
        if (style == null) bobberStyles.remove(sender);
        else bobberStyles.put(sender, style);
    }

    /// The bobber preset another FOE+ user picked, or null if they haven't told us one.
    public static dannypx.foe.type.BobberStyle bobberStyleOf(UUID player) {
        return player == null ? null : bobberStyles.get(player);
    }

    private static byte[] colorPayload() {
        return new byte[]{'C', (byte) (int) dannypx.foe.config.Configs.rendererConfig.foePlusColor.get()};
    }

    /// You picked a new colour: tell the players around you (new arrivals get it anyway).
    public static void announceColor() {
        if (!ConnectionHandler.instance().isOnServer()) return;
        NearbyDispatcher.queue(COLOR_TYPE, colorPayload());
        NearbyDispatcher.queue(CUSTOM_COLOR_TYPE, customColorPayload());
    }

    private static void onReceiveColor(UUID sender, byte[] payload) {
        if (payload.length != 2 || payload[0] != 'C') return;
        TextDisplayHandler.instance().setFoePlusColor(sender, payload[1] & 0xFF);
    }

    private static void onReceiveFoePlus(UUID sender, byte[] payload) {
        if (!java.util.Arrays.equals(payload, FOE_PLUS_PAYLOAD)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Player player = minecraft.level.getPlayerByUUID(sender);
        if (player == null) return;
        String name = player.getName().getString();
        confirmed.add(sender);
        unansweredTicks.remove(sender);
        TextDisplayHandler.instance().addModUser(sender, name);
        TextDisplayHandler.instance().addFoePlusUser(sender, name);
        // Answer, so a FOE+ that already said hello to us earlier in its session (before we relogged) hears
        // from us and answers in turn. Once a minute at most, so two FOE+ users never keep answering each other.
        long now = System.currentTimeMillis();
        Long last = answeredAt.get(sender);
        if (last == null || now - last > 60_000L) {
            answeredAt.put(sender, now);
            NearbyDispatcher.queue(PACKET_TYPE, new byte[]{1});
            NearbyDispatcher.queue(FOE_PLUS_TYPE, FOE_PLUS_PAYLOAD);
            NearbyDispatcher.queue(COLOR_TYPE, colorPayload());
            NearbyDispatcher.queue(CUSTOM_COLOR_TYPE, customColorPayload());
            NearbyDispatcher.queue(BOBBER_TYPE, bobberPayload());
        }
    }

    private static void onReceive(UUID sender, byte[] payload) {
        if (payload.length != 1 || payload[0] != 1) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        Player player = minecraft.level.getPlayerByUUID(sender);
        if (player == null) return;

        TextDisplayHandler.instance().addModUser(sender, player.getName().getString());
    }

    public static void onPlayerLeave(UUID playerUuid) {
        Minecraft.getInstance().execute(() -> {
            announcedTo.remove(playerUuid);
            ticksInRange.remove(playerUuid);
            bobberStyles.remove(playerUuid);
            TextDisplayHandler.instance().removeModUser(playerUuid);
        });
    }
}
