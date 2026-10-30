package dannypx.foe.helper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Minimal Minecraft server list ping, run on its own thread: the same handshake + status request
 * the multiplayer screen sends, then a ping/pong to time the round trip. Uses Minecraft's own
 * address resolver, so SRV records work like in the server list.
 */
public final class ServerPing {
    private ServerPing() {}

    public record Result(boolean reachable, int online, int max, long pingMs) {
        public static final Result UNREACHABLE = new Result(false, 0, 0, -1);
    }

    public static Result ping(String address, int timeoutMs) {
        try {
            ServerAddress parsed = ServerAddress.parseString(address);
            Optional<ResolvedServerAddress> resolved = ServerNameResolver.DEFAULT.resolveAddress(parsed);
            if (resolved.isEmpty()) return Result.UNREACHABLE;
            InetSocketAddress target = resolved.get().asInetSocketAddress();

            try (Socket socket = new Socket()) {
                socket.connect(target, timeoutMs);
                socket.setSoTimeout(timeoutMs);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());

                // Handshake: protocol, host, port, next state = status (1)
                ByteArrayOutputStream handshake = new ByteArrayOutputStream();
                writeVarInt(handshake, 0x00);
                writeVarInt(handshake, SharedConstants.getProtocolVersion());
                writeString(handshake, parsed.getHost());
                handshake.write((parsed.getPort() >> 8) & 0xFF);
                handshake.write(parsed.getPort() & 0xFF);
                writeVarInt(handshake, 1);
                writePacket(out, handshake.toByteArray());

                // Status request
                writePacket(out, new byte[]{0x00});

                readVarInt(in); // packet length
                if (readVarInt(in) != 0x00) return Result.UNREACHABLE;
                byte[] json = new byte[readVarInt(in)];
                in.readFully(json);

                JsonObject status = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
                int online = 0;
                int max = 0;
                if (status.has("players")) {
                    JsonObject players = status.getAsJsonObject("players");
                    online = players.has("online") ? players.get("online").getAsInt() : 0;
                    max = players.has("max") ? players.get("max").getAsInt() : 0;
                }

                // Ping/pong for latency
                long start = System.currentTimeMillis();
                ByteArrayOutputStream pingPacket = new ByteArrayOutputStream();
                writeVarInt(pingPacket, 0x01);
                new DataOutputStream(pingPacket).writeLong(start);
                writePacket(out, pingPacket.toByteArray());
                long pingMs;
                try {
                    readVarInt(in);
                    readVarInt(in);
                    in.readLong();
                    pingMs = System.currentTimeMillis() - start;
                } catch (IOException e) {
                    pingMs = System.currentTimeMillis() - start; // some proxies close instead of answering
                }
                return new Result(true, online, max, pingMs);
            }
        } catch (Exception e) {
            return Result.UNREACHABLE;
        }
    }

    private static void writePacket(DataOutputStream out, byte[] body) throws IOException {
        ByteArrayOutputStream framed = new ByteArrayOutputStream();
        writeVarInt(framed, body.length);
        framed.write(body);
        out.write(framed.toByteArray());
        out.flush();
    }

    private static void writeString(OutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writeVarInt(OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        int position = 0;
        while (true) {
            int b = in.read();
            if (b < 0) throw new IOException("End of stream");
            value |= (b & 0x7F) << position;
            if ((b & 0x80) == 0) return value;
            position += 7;
            if (position >= 32) throw new IOException("VarInt too big");
        }
    }
}
