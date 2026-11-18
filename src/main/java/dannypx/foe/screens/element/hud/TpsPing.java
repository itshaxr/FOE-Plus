package dannypx.foe.screens.element.hud;

import dannypx.foe.handler.logic.ServerTpsHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/// The server's TPS and your ping, as the top bar shows them: "ᴛᴘѕ 19.9  ᴘɪɴɢ 42ms", each coloured by how good it is.
public final class TpsPing {
    private TpsPing() {}

    /// Package-private for tests: "ᴛᴘѕ 19.9  ᴘɪɴɢ 42ms".
    static Component line(double tps, int ping) {
        return Component.empty()
                .append(Component.literal("ᴛᴘѕ ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(ServerTpsHandler.tpsText(tps)).withColor(colorTps(tps)))
                .append(Component.literal("  ᴘɪɴɢ ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(ping < 0 ? "?" : ping + "ms").withColor(colorPing(ping)));
    }

    /// Now, for the top bar.
    public static Component now() {
        return line(ServerTpsHandler.instance().tps(), ServerTpsHandler.ping());
    }

    static int colorTps(double tps) {
        return tps < 0 ? 0xAAAAAA : tps >= 18 ? 0x55FF55 : tps >= 15 ? 0xFFFF55 : tps >= 10 ? 0xFFAA00 : 0xFF5555;
    }

    static int colorPing(int ms) {
        return ms < 0 ? 0xAAAAAA : ms < 80 ? 0x55FF55 : ms < 160 ? 0xB9E36B : ms < 300 ? 0xFFFF55 : 0xFF5555;
    }
}
