package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.BossEventHandler;
import dannypx.foe.handler.fetch.TabOverlayHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The community event (always running on FishOnMC) for the top bar. /communityevent answers with
 * "Community Catches: 88.91K/100k" and "Your Catches Contribution: 352"; FOE+ runs it quietly
 * now and then (never while a menu is open, see SilentMenuHandler) and hides its own answer.
 * Between checks your contribution goes up with every catch (for a catches event), and the
 * total follows the boss bar's "Community Goal" when the server shows it.
 */
public class CommunityEventHandler {
    private static CommunityEventHandler INSTANCE = new CommunityEventHandler();

    public static CommunityEventHandler instance() {
        if (INSTANCE == null) INSTANCE = new CommunityEventHandler();
        return INSTANCE;
    }

    static final Pattern TOTAL = Pattern.compile("^\\s*Community ([A-Za-z ]+?):\\s*([0-9.,]+\\s*[KkMmBb]?)\\s*/\\s*([0-9.,]+\\s*[KkMmBb]?)\\s*$");
    static final Pattern YOURS = Pattern.compile("^\\s*Your ([A-Za-z ]+?) Contribution:\\s*([0-9.,]+\\s*[KkMmBb]?)\\s*$");

    /// How often to check while an event is on.
    private static final long REFRESH_MS = 10L * 60L * 1000L;
    /// First check after joining.
    private static final long FIRST_CHECK_MS = 30_000L;
    /// Lines arriving this soon after our own /communityevent are its answer and are hidden.
    private static final long HIDE_WINDOW_MS = 3_000L;

    private String kind = "";        // "Catches"
    private String total = "";       // "88.91K"
    private String goal = "";        // "100K"
    private long contribution = -1;  // 352, or -1 when unknown
    private long lastReadAt = 0L;
    private long joinedAt = 0L;
    private long lastCheckAt = 0L;
    private long hideUntil = 0L;

    //region Reading
    /// From chat: true when the line should be hidden (it's the answer to our own check).
    public boolean onGameMessage(String raw) {
        String text = ChatFormatting.stripFormatting(raw);
        if (text == null) return false;
        boolean ours = System.currentTimeMillis() < hideUntil;
        if (ours && text.isBlank()) return true; // padding around our own answer
        Matcher m = TOTAL.matcher(text);
        if (m.matches()) {
            kind = m.group(1).trim();
            total = normalize(m.group(2));
            goal = normalize(m.group(3));
            lastReadAt = System.currentTimeMillis();
            return ours;
        }
        m = YOURS.matcher(text);
        if (m.matches()) {
            long value = parse(m.group(2));
            if (value >= 0) contribution = value;
            lastReadAt = System.currentTimeMillis();
            return ours;
        }
        // Any other line about the community event in our own answer.
        if (ours && text.toLowerCase(Locale.ROOT).contains("community")) return true;
        return false;
    }

    /// A catch counts towards a catches event straight away.
    public void onFishCaught() {
        if (contribution >= 0 && kind.toLowerCase(Locale.ROOT).contains("catch")) contribution++;
    }
    //endregion

    //region Background check
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.player == null || !TabOverlayHandler.instance().isInInstance()) {
            joinedAt = 0L;
            return;
        }
        if (joinedAt == 0L) joinedAt = now;
        if (!Configs.hudConfig.topBarShowCommunity.get() || !Configs.hudConfig.communityAutoRefresh.get()) return;
        boolean due = lastCheckAt == 0L ? now - joinedAt >= FIRST_CHECK_MS : now - lastCheckAt >= REFRESH_MS;
        if (!due || !SilentMenuHandler.instance().isQuietForBackgroundCommand()) return;
        lastCheckAt = now;
        hideUntil = now + HIDE_WINDOW_MS;
        SilentMenuHandler.instance().noteBackgroundCommand("communityevent");
        mc.getConnection().sendCommand("communityevent");
    }

    /// The server said to wait: check again after that.
    public void retryIn(long ms) {
        lastCheckAt = System.currentTimeMillis() - REFRESH_MS + ms;
    }
    //endregion

    //region For the top bar
    public boolean hasData() {
        return !total.isEmpty() || contribution >= 0 || !BossEventHandler.instance().getCommunityGoalCurrent().getString().isBlank();
    }

    /// The event total: the boss bar's live number when shown, else the last /communityevent.
    public String total() {
        String boss = BossEventHandler.instance().getCommunityGoalCurrent().getString().trim();
        return boss.isEmpty() ? total : boss;
    }

    public String goal() {
        String boss = BossEventHandler.instance().getCommunityGoalMax().getString().trim();
        return boss.isEmpty() ? goal : boss;
    }

    public long contribution() {
        return contribution;
    }
    //endregion

    //region Numbers
    /// "100k" -> "100K", "88.91 K" -> "88.91K".
    static String normalize(String value) {
        String v = value.replace(" ", "");
        if (!v.isEmpty() && Character.isLetter(v.charAt(v.length() - 1))) {
            v = v.substring(0, v.length() - 1) + Character.toUpperCase(v.charAt(v.length() - 1));
        }
        return v;
    }

    /// Short form for the top bar: "88.91K" -> "88.9K", "100k" -> "100K", "1204" -> "1.2K",
    /// "352" -> "352". Unreadable values are shown as they are.
    public static String shortNumber(String value) {
        long n = parse(value);
        if (n < 0) return value;
        if (n < 1_000) return String.valueOf(n);
        String[] units = {"K", "M", "B"};
        double v = n;
        int u = -1;
        while (v >= 1_000 && u < units.length - 1) { v /= 1_000; u++; }
        String s = v >= 100 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s + units[u];
    }

    /// "352" -> 352, "1,204" -> 1204, "1.2K" -> 1200; -1 when unreadable.
    static long parse(String value) {
        String v = value.replace(",", "").replace(" ", "").toUpperCase(Locale.ROOT);
        double mult = 1;
        if (v.endsWith("K")) mult = 1e3;
        else if (v.endsWith("M")) mult = 1e6;
        else if (v.endsWith("B")) mult = 1e9;
        if (mult != 1) v = v.substring(0, v.length() - 1);
        try {
            return Math.round(Double.parseDouble(v) * mult);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
    //endregion
}
