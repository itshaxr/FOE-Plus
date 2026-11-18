package dannypx.foe.nearby_dispatcher;

import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.nearby_dispatcher.types.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public class NearbyDispatcher {
    public static final int MAX_PAYLOAD = 64;
    public static final int BUNDLE_TYPE = 254;

    private static final int FRAME_MARKER = 0xAC;
    public static final int PROTOCOL_VERSION = 1;
    private static final int EXPECTED_TABLE_CHECKSUM = -1918344551;

    private static final int BUNDLE_ENTRY_HEADER_SIZE = 2;

    private static final long FRAME_TIMEOUT_MILLIS = 2000;
    private static final int STABLE_TICKS_REQUIRED = 5;
    private static final long MIN_FRAME_GAP_MILLIS = 1000;

    public static final double NEARBY_RANGE = 24.0;
    public static final int NEWCOMER_DELAY_TICKS = 8;

    public interface Handler {
        void handle(UUID sender, byte[] payload);
    }

    private record Outgoing(int type, byte[] payload) {}

    private static final Map<Integer, Handler> HANDLERS = new HashMap<>();
    private static final Map<UUID, FrameAssembler> ASSEMBLERS = new HashMap<>();
    private static final ArrayDeque<Outgoing> OUTBOX = new ArrayDeque<>();
    static { dannypx.foe.handler.logic.MemoryGuard.register("nearbyFrames", () -> ASSEMBLERS.size() + OUTBOX.size(), ASSEMBLERS::clear); }

    private static BlockPos lastBlockPosition;
    private static int stableTicks;
    private static long nextSendTimeMillis;

    private static boolean initialized;

    public static void init() {
        if (initialized) return;
        initialized = true;

        if (OffsetCodec.tableChecksum() != EXPECTED_TABLE_CHECKSUM) {
            LoggerHandler.error(new IllegalStateException("OffsetCodec table changed (checksum "
                    + OffsetCodec.tableChecksum() + "). If intended, bump PROTOCOL_VERSION and update EXPECTED_TABLE_CHECKSUM."));
        }

        ClientTickEvents.END_CLIENT_TICK.register(NearbyDispatcher::tick);

        NearbyBigType.init();
        HatType.init();
        RodType.init();
        RippleType.init();
        ModUserType.init();
    }

    private static void requireValidType(int type) {
        if (type < 0 || type > 255) throw new IllegalArgumentException("type must be 0-255");
        if (type == BUNDLE_TYPE) throw new IllegalArgumentException("type " + BUNDLE_TYPE + " is reserved for bundles");
    }

    public static void register(int type, Handler handler) {
        requireValidType(type);
        HANDLERS.put(type, handler);
    }

    public static void queue(int type, byte[] payload) {
        requireValidType(type);
        if (payload.length > MAX_PAYLOAD) throw new IllegalArgumentException("payload too large");
        OUTBOX.removeIf(outgoing -> outgoing.type() == type);
        OUTBOX.addLast(new Outgoing(type, payload.clone()));
    }

    public static void queueAppend(int type, byte[] payload) {
        requireValidType(type);
        if (payload.length > MAX_PAYLOAD) throw new IllegalArgumentException("payload too large");
        OUTBOX.addLast(new Outgoing(type, payload.clone()));
    }

    private static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || !ConnectionHandler.instance().isOnServer()) {
            OUTBOX.clear();
            ASSEMBLERS.clear();
            lastBlockPosition = null;
            stableTicks = 0;
            return;
        }

        BlockPos currentBlockPosition = minecraft.player.blockPosition();
        stableTicks = currentBlockPosition.equals(lastBlockPosition) ? stableTicks + 1 : 0;
        lastBlockPosition = currentBlockPosition;

        if (OUTBOX.isEmpty() || stableTicks < STABLE_TICKS_REQUIRED) return;
        long nowMillis = System.currentTimeMillis();
        if (nowMillis < nextSendTimeMillis || !canSend(minecraft)) return;

        sendNextFrame(minecraft);
        nextSendTimeMillis = nowMillis + MIN_FRAME_GAP_MILLIS;
    }

    private static boolean canSend(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.getConnection() == null) return false;
        if (player.getPose() != Pose.STANDING || player.isPassenger() || player.isSpectator()) return false;

        int playerBlockY = player.blockPosition().getY();
        return playerBlockY + OffsetCodec.MIN_OFFSET_Y > level.getMinY() + 2
                && playerBlockY + OffsetCodec.MAX_OFFSET_Y < level.getMaxY() - 2;
    }

    private static void sendNextFrame(Minecraft minecraft) {
        List<Outgoing> batch = new ArrayList<>();
        batch.add(OUTBOX.pollFirst());
        int bundleSize = BUNDLE_ENTRY_HEADER_SIZE + batch.get(0).payload().length;

        while (!OUTBOX.isEmpty()) {
            int entrySize = BUNDLE_ENTRY_HEADER_SIZE + OUTBOX.peekFirst().payload().length;
            if (bundleSize + entrySize > MAX_PAYLOAD) break;
            batch.add(OUTBOX.pollFirst());
            bundleSize += entrySize;
        }

        if (batch.size() == 1) {
            sendFrame(minecraft, batch.get(0).type(), batch.get(0).payload());
            return;
        }

        byte[] bundlePayload = new byte[bundleSize];
        int writePosition = 0;
        for (Outgoing outgoing : batch) {
            bundlePayload[writePosition++] = (byte) outgoing.type();
            bundlePayload[writePosition++] = (byte) outgoing.payload().length;
            System.arraycopy(outgoing.payload(), 0, bundlePayload, writePosition, outgoing.payload().length);
            writePosition += outgoing.payload().length;
        }
        sendFrame(minecraft, BUNDLE_TYPE, bundlePayload);
    }

    private static void sendFrame(Minecraft minecraft, int type, byte[] payload) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || minecraft.player == null) return;

        byte[] frame = new byte[payload.length + 5];
        frame[0] = (byte) FRAME_MARKER;
        frame[1] = (byte) PROTOCOL_VERSION;
        frame[2] = (byte) type;
        frame[3] = (byte) payload.length;
        System.arraycopy(payload, 0, frame, 4, payload.length);
        frame[frame.length - 1] = (byte) calculateCrc8(frame, 2, payload.length + 2);

        BlockPos origin = minecraft.player.blockPosition();
        for (byte frameByte : frame) {
            BlockPos encodedBlockPosition = OffsetCodec.encode(origin, frameByte & 0xFF);
            connection.send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, encodedBlockPosition, Direction.DOWN));
        }

        LoggerHandler._debug("Send type " + type + " (" + payload.length + " payload bytes, " + frame.length + " packets)");
    }

    public static void onBlockDestruction(int breakerEntityId, BlockPos blockPosition, int progress) {
        if ((progress & 0xFF) != 0xFF) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || breakerEntityId == minecraft.player.getId()) return;
        if (!(minecraft.level.getEntity(breakerEntityId) instanceof Player sender)) return;

        int decodedByte = OffsetCodec.decode(sender.blockPosition(), blockPosition);
        if (decodedByte < 0) return;

        ASSEMBLERS.computeIfAbsent(sender.getUUID(), senderUuid -> new FrameAssembler())
                .accept(decodedByte, sender.getUUID());
    }

    private static final class FrameAssembler {
        private final byte[] frameBuffer = new byte[MAX_PAYLOAD + 5];
        private int bufferedByteCount;
        private long lastByteTimeMillis;

        void accept(int decodedByte, UUID sender) {
            long nowMillis = System.currentTimeMillis();
            if (bufferedByteCount > 0 && nowMillis - lastByteTimeMillis > FRAME_TIMEOUT_MILLIS) bufferedByteCount = 0;
            lastByteTimeMillis = nowMillis;

            if (bufferedByteCount == 0) {
                if (decodedByte != FRAME_MARKER) return;
            } else if (bufferedByteCount == 1 && decodedByte != PROTOCOL_VERSION) {
                bufferedByteCount = decodedByte == FRAME_MARKER ? 1 : 0;
                return;
            }

            frameBuffer[bufferedByteCount++] = (byte) decodedByte;
            if (bufferedByteCount < 4) return;

            int payloadLength = frameBuffer[3] & 0xFF;
            if (payloadLength > MAX_PAYLOAD) {
                bufferedByteCount = 0;
                return;
            }
            if (bufferedByteCount < payloadLength + 5) return;

            int expectedChecksum = calculateCrc8(frameBuffer, 2, payloadLength + 2);
            int actualChecksum = frameBuffer[payloadLength + 4] & 0xFF;
            if (expectedChecksum == actualChecksum) {
                dispatch(sender, frameBuffer[2] & 0xFF, Arrays.copyOfRange(frameBuffer, 4, 4 + payloadLength));
            }
            bufferedByteCount = 0;
        }
    }

    private static void dispatch(UUID sender, int type, byte[] payload) {
        if (type != BUNDLE_TYPE) {
            dispatchToHandler(sender, type, payload);
            return;
        }

        int readPosition = 0;
        while (readPosition + BUNDLE_ENTRY_HEADER_SIZE <= payload.length) {
            int entryType = payload[readPosition] & 0xFF;
            int entryLength = payload[readPosition + 1] & 0xFF;
            readPosition += BUNDLE_ENTRY_HEADER_SIZE;
            if (readPosition + entryLength > payload.length) return;

            if (entryType != BUNDLE_TYPE) {
                dispatchToHandler(sender, entryType,
                        Arrays.copyOfRange(payload, readPosition, readPosition + entryLength));
            }
            readPosition += entryLength;
        }
    }

    private static void dispatchToHandler(UUID sender, int type, byte[] payload) {
        Handler handler = HANDLERS.get(type);
        if (handler == null) return;
        try {
            handler.handle(sender, payload);
        } catch (Exception exception) {
            LoggerHandler.error(exception);
        }
    }

    private static int calculateCrc8(byte[] data, int offset, int length) {
        int checksum = 0;
        for (int dataIndex = offset; dataIndex < offset + length; dataIndex++) {
            checksum ^= data[dataIndex] & 0xFF;
            for (int bitIndex = 0; bitIndex < 8; bitIndex++) {
                checksum = (checksum & 0x80) != 0 ? ((checksum << 1) ^ 0x07) & 0xFF : (checksum << 1) & 0xFF;
            }
        }
        return checksum;
    }
}