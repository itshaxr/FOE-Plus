package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.mixin.accessor.GuiAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.projectile.FishingHook;

import java.util.Locale;

/**
 * The bobber timer (ported from FOECollab's bobber tracker, AGPL-3.0): how long your bobber has
 * been out, in seconds, shown over your own bobber. It hides once a fish is on: the reel-in
 * minigame runs in the action bar, so a fresh action bar message means the wait is over. The
 * last catch's message (still up for ~3 s after a quick recast) doesn't count: its time left
 * is noted at the cast and counted down.
 */
public final class BobberTimerHandler {
    private static BobberTimerHandler INSTANCE;

    public static BobberTimerHandler instance() {
        if (INSTANCE == null) INSTANCE = new BobberTimerHandler();
        return INSTANCE;
    }

    private boolean hadBobberOut = false;
    private int staleOverlayTicks = 0;

    private BobberTimerHandler() {}

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean out = mc.player != null && mc.player.fishing != null;
        if (out && !hadBobberOut) staleOverlayTicks = overlayTime(mc);
        else if (staleOverlayTicks > 0) staleOverlayTicks--;
        hadBobberOut = out;
    }

    /// The timer text for your own bobber, or null when it shouldn't show.
    public Component label(FishingHook hook, float partialTick) {
        if (!Configs.rendererConfig.showBobberTimer.get()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.fishing != hook) return null;
        if (overlayTime(mc) > staleOverlayTicks) return null; // a fish is on
        return text((hook.tickCount + partialTick) / 20f, Configs.rendererConfig.bobberTimerSecondsSuffix.get());
    }

    /// "12.4s": bold white seconds, a grey "s". Package-private for tests.
    static Component text(float seconds, boolean suffix) {
        Component time = Component.literal(String.format(Locale.ROOT, "%.1f", seconds)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
        return suffix ? Component.empty().append(time).append(Component.literal("s").withStyle(ChatFormatting.GRAY)) : time;
    }

    private static int overlayTime(Minecraft mc) {
        try {
            return ((GuiAccessor) mc.gui).getOverlayMessageTime();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
