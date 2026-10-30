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

    private static final double NEARBY_RANGE = 24.0;
    private static final int NEWCOMER_DELAY_TICKS = 10;

    private static final Set<UUID> announcedTo = new HashSet<>();
    private static final Map<UUID, Integer> ticksInRange = new HashMap<>();

    public static void init() {
        NearbyDispatcher.register(PACKET_TYPE, ModUserType::onReceive);
        NearbyDispatcher.register(FOE_PLUS_TYPE, ModUserType::onReceiveFoePlus);
        ClientTickEvents.END_CLIENT_TICK.register(ModUserType::tick);
    }

    private static void tick(Minecraft minecraft) {
        LocalPlayer self = minecraft.player;
        if (self == null || minecraft.level == null || !ConnectionHandler.instance().isOnServer()) {
            announcedTo.clear();
            ticksInRange.clear();
            return;
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
    }

    private static void onReceiveFoePlus(UUID sender, byte[] payload) {
        if (!java.util.Arrays.equals(payload, FOE_PLUS_PAYLOAD)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Player player = minecraft.level.getPlayerByUUID(sender);
        if (player == null) return;
        String name = player.getName().getString();
        TextDisplayHandler.instance().addModUser(sender, name);
        TextDisplayHandler.instance().addFoePlusUser(sender, name);
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
            TextDisplayHandler.instance().removeModUser(playerUuid);
        });
    }
}
