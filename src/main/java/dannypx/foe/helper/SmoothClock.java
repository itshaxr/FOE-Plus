package dannypx.foe.helper;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The in-game clock on the top bar, ticking minute by minute. The server only sends the time now and then, so its
 * clock jumps (1:04, 1:06...): FOE+ counts the minutes in between at the pace the server's clock goes, and if it
 * falls behind it counts faster (every minute still shown) until it's caught up.
 */
public final class SmoothClock {
    static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})(\\s*)([AaPp][Mm])?");
    /// A Minecraft day: 24 game hours in 20 minutes, 1.2 game minutes a second.
    static final double DEFAULT_RATE = 1440.0 / 1_200_000.0;
    /// How far past the server's last time it may count on its own (the server may have stopped the clock).
    static final double MAX_AHEAD = 4.0;

    private double server = -1;       // the server's last time, minutes (counting on past midnight)
    private long serverAt = 0L;
    private double rate = DEFAULT_RATE; // game minutes a millisecond, as measured
    private double shown = -1;
    private long shownAt = 0L;
    private boolean twelveHour = true;
    private String gap = "";
    private boolean lowerCase = false;

    private static final SmoothClock TOP_BAR = new SmoothClock();

    /// The server's time component, ticking minute by minute (same colours).
    public static Component topBar(Component serverTime) {
        return TOP_BAR.text(serverTime, System.currentTimeMillis());
    }

    /// Package-private for tests: minutes past midnight in "1:04AM" / "13:04", or -1.
    static int parse(String text) {
        Matcher m = TIME.matcher(text == null ? "" : text);
        if (!m.find()) return -1;
        int h = Integer.parseInt(m.group(1)), min = Integer.parseInt(m.group(2));
        if (min > 59) return -1;
        String ap = m.group(4);
        if (ap != null) {
            if (h < 1 || h > 12) return -1;
            h = h % 12 + (ap.equalsIgnoreCase("PM") ? 12 : 0);
        } else if (h > 23) return -1;
        return h * 60 + min;
    }

    /// Package-private for tests: the time to show now, minutes past midnight.
    int minuteAt(String serverText, long now) {
        int got = parse(serverText);
        if (got < 0) return -1;
        Matcher m = TIME.matcher(serverText);
        if (m.find()) {
            twelveHour = m.group(4) != null;
            gap = m.group(3);
            lowerCase = m.group(4) != null && Character.isLowerCase(m.group(4).charAt(0));
        }
        if (server < 0) {
            server = got;
            serverAt = now;
            shown = got;
            shownAt = now;
        } else {
            int last = (int) Math.floorMod((long) Math.floor(server), 1440L);
            if (got != last) {
                double step = got - last;
                if (step < -720) step += 1440;           // past midnight
                if (step < 0 || step > 120) {
                    // the clock was set back or jumped far: show it as it is
                    server = got;
                    shown = got;
                } else {
                    double newServer = server - (server - Math.floor(server)) + step;
                    long dt = now - serverAt;
                    if (dt > 200 && step > 0) {
                        double measured = step / dt;
                        rate = Math.max(DEFAULT_RATE / 20, Math.min(DEFAULT_RATE * 20, rate * 0.7 + measured * 0.3));
                    }
                    server = newServer;
                }
                serverAt = now;
            }
        }
        // where the clock should be now: the server's time plus what's passed since, not too far ahead
        double target = server + Math.min(MAX_AHEAD, (now - serverAt) * rate);
        long dt = Math.max(0, now - shownAt);
        shownAt = now;
        if (shown < target) {
            // at the clock's pace, faster when behind (it catches up in about a second), never past it
            double behind = target - shown;
            shown = Math.min(target, shown + Math.max(dt * rate, behind * Math.min(1.0, dt / 1000.0) * 3));
        }
        return (int) Math.floorMod((long) Math.floor(shown + 1e-9), 1440L);
    }

    /// Package-private for tests: "1:04AM" (or "13:04"), in the server's way of writing it.
    String format(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        if (!twelveHour) return h + ":" + (m < 10 ? "0" : "") + m;
        String ap = h < 12 ? "AM" : "PM";
        if (lowerCase) ap = ap.toLowerCase(java.util.Locale.ROOT);
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + ":" + (m < 10 ? "0" : "") + m + gap + ap;
    }

    Component text(Component serverTime, long now) {
        if (serverTime == null) return Component.empty();
        String s = serverTime.getString();
        int minute = minuteAt(s, now);
        if (minute < 0) return serverTime;
        Matcher m = TIME.matcher(s);
        if (!m.find()) return serverTime;
        // the same text, the time swapped, in the time's own style
        Style[] style = {Style.EMPTY};
        int[] pos = {0};
        serverTime.visit((st, part) -> {
            if (pos[0] <= m.start() && m.start() < pos[0] + part.length()) style[0] = st;
            pos[0] += part.length();
            return Optional.empty();
        }, Style.EMPTY);
        MutableComponent out = Component.empty();
        if (m.start() > 0) out.append(Component.literal(s.substring(0, m.start())).setStyle(style[0]));
        out.append(Component.literal(format(minute)).setStyle(style[0]));
        if (m.end() < s.length()) out.append(Component.literal(s.substring(m.end())).setStyle(style[0]));
        return out;
    }
}
