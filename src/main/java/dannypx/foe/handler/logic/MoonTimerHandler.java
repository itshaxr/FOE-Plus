package dannypx.foe.handler.logic;

import net.minecraft.network.chat.Component;

/**
 * Moon cycle timer, ported from FOECollab's TimerHandler (AGPL-3.0).
 * <p>
 * A moon rises every 160 minutes, anchored at 00:03:01 UTC (a day is exactly 9 cycles, so the
 * anchor holds every day). The server doesn't say in advance which moon it will be, so the type
 * is read from chat when it rises ("BLOOD MOON » A Blood Moon has risen!") and cleared when it sets.
 */
public class MoonTimerHandler {
    private static MoonTimerHandler INSTANCE = new MoonTimerHandler();

    public static final long CYCLE_MS = 160L * 60L * 1000L;
    private static final long ANCHOR_MS = (3L * 60L + 1L) * 1000L;

    /** The moon that is currently up, or null. */
    private Moon currentMoon = null;

    public static MoonTimerHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new MoonTimerHandler();
        }
        return INSTANCE;
    }

    /// Milliseconds until the next moonrise.
    public long getTimeUntilNextMoon() {
        long elapsed = Math.floorMod(System.currentTimeMillis() - ANCHOR_MS, CYCLE_MS);
        return CYCLE_MS - elapsed;
    }

    public Moon getCurrentMoon() {
        return currentMoon;
    }

    public void onReceiveMessage(Component message) {
        String text = message.getString();
        for (Moon moon : Moon.values()) {
            if (text.startsWith(moon.chatPrefix)) {
                this.currentMoon = text.contains(" has set") ? null : moon;
                return;
            }
        }
    }

    public void onLeaveServer() {
        this.currentMoon = null;
    }

    public enum Moon {
        FULL("FULL MOON »", "Full Moon", 0xFFFFFF),
        BLUE("BLUE MOON »", "Blue Moon", 0x60C2E9),
        SUPER("SUPER MOON »", "Super Moon", 0xB0F9D5),
        BLOOD("BLOOD MOON »", "Blood Moon", 0xFF0000);

        public final String chatPrefix;
        public final String displayName;
        public final int color;

        Moon(String chatPrefix, String displayName, int color) {
            this.chatPrefix = chatPrefix;
            this.displayName = displayName;
            this.color = color;
        }
    }
}
