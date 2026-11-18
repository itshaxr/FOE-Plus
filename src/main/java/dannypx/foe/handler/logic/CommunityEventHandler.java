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
    /// First after logging in (before the other background reads), so the box fills straight away.
    private static final long FIRST_CHECK_MS = 1_000L;
    /// Lines arriving this soon after our own /communityevent are its answer and are hidden.
    private static final long HIDE_WINDOW_MS = 5_000L;
    /// How often to check when the Community panel is on and the boss bar isn't showing the count.
    private static final long PANEL_REFRESH_MS = 2L * 60L * 1000L;

    private String kind = "";        // "Catches"
    private String total = "";       // "88.91K"
    private String goal = "";        // "100K"
    private long contribution = -1;  // 352, or -1 when unknown
    private long lastReadAt = 0L;
    private long joinedAt = 0L;
    private long lastCheckAt = 0L;
    private long hideUntil = 0L;
    /// The last community total we saw (boss bar or /communityevent), to spot the goal being hit.
    private long lastTotal = -1L;
    /// After the goal resets, check /communityevent this soon for your exact new contribution.
    private static final long AFTER_RESET_CHECK_MS = 5_000L;
    /// How long a drop in the total must hold before it counts as the goal resetting.
    static final long RESET_CONFIRM_MS = 20_000L;
    /// When the current drop was first seen (0: none).
    private long resetSeenAt = 0L;

    //region Reading
    /// From chat: true when the line should be hidden (it's the answer to our own check).
    public boolean onGameMessage(String raw) {
        String text = ChatFormatting.stripFormatting(raw);
        if (text == null) return false;
        boolean ours = System.currentTimeMillis() < hideUntil;
        if (ours && text.isBlank()) return true; // padding around our own answer
        Matcher m = TOTAL.matcher(text);
        if (m.matches()) {
            String newKind = m.group(1).trim();
            if (!kind.isEmpty() && !newKind.equalsIgnoreCase(kind)) lastTotal = -1L; // a different event
            kind = newKind;
            total = normalize(m.group(2));
            goal = normalize(m.group(3));
            lastReadAt = System.currentTimeMillis();
            noteTotal(parse(total), lastReadAt);
            fromLastSession = false;
            saveLast();
            return ours;
        }
        m = YOURS.matcher(text);
        if (m.matches()) {
            long value = parse(m.group(2));
            if (value >= 0) contribution = value;
            lastReadAt = System.currentTimeMillis();
            saveLast();
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
        if (joinedAt == 0L) {
            joinedAt = now;
            loadLast(now);
        }
        String boss = BossEventHandler.instance().getCommunityGoalCurrent().getString().trim();
        long bossValue = parse(boss);
        if (bossValue >= 0) noteTotal(bossValue, now);
        // Checked for the top bar's readout or the Community panel, whichever is on.
        boolean wanted = Configs.hudConfig.topBarShowCommunity.get() || communityInTimers();
        if (!wanted || !Configs.hudConfig.communityAutoRefresh.get()) return;
        long every = communityInTimers() && bossValue < 0 ? PANEL_REFRESH_MS : REFRESH_MS;
        boolean due = lastCheckAt == 0L ? now - joinedAt >= FIRST_CHECK_MS : now - lastCheckAt >= every;
        if (!due || !SilentMenuHandler.instance().isQuietForBackgroundCommand()) return;
        lastCheckAt = now;
        hideUntil = now + HIDE_WINDOW_MS;
        SilentMenuHandler.instance().noteBackgroundCommand("communityevent");
        mc.getConnection().sendCommand("communityevent");
    }

    /// A new reading of the community total. It only ever goes up during a goal, so a big drop
    /// means the goal was hit and a new one began: your contribution starts again from 0 (it
    /// counts up with your catches), and /communityevent is checked shortly for the exact number.
    ///
    /// A drop has to hold for a while first: one odd reading (the boss bar mid-update, another bar
    /// read by mistake) never wipes what you've helped with. Package-private for tests.
    void noteTotal(long value, long now) {
        if (value < 0) return;
        if (lastTotal >= 0 && isReset(lastTotal, value)) {
            if (resetSeenAt == 0L) {
                resetSeenAt = now;
                // check /communityevent soon: its answer has your exact contribution either way
                if (lastCheckAt != 0L) lastCheckAt = Math.min(lastCheckAt, now - REFRESH_MS + AFTER_RESET_CHECK_MS);
            }
            if (now - resetSeenAt < RESET_CONFIRM_MS) return; // not sure yet: keep the old total to compare with
            if (contribution >= 0 && lastReadAt < resetSeenAt) contribution = 0; // no fresher answer came
            total = String.valueOf(value);
            resetSeenAt = 0L;
            lastTotal = value;
            return;
        }
        resetSeenAt = 0L; // back where it was: just a blip
        lastTotal = value;
    }

    /// True when the total fell to under half of what it was: the goal reset (to near nothing), not
    /// rounding or a misread.
    static boolean isReset(long before, long after) {
        return after < before / 2 && before - after >= 10L;
    }

    //region Last reading, kept between sessions
    /// The last /communityevent answer is saved, so after logging in the box shows it at once
    /// (until the fresh answer a second later replaces it). Older than this, it isn't used.
    static final long LAST_MAX_AGE_MS = 12L * 60L * 60L * 1000L;
    /// True while what's shown is the saved reading from last time (no fresh answer yet).
    private boolean fromLastSession = false;
    public boolean fromLastSession() { return fromLastSession; }

    private static final class Last { String kind, total, goal; long contribution = -1, savedAt; }
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();
    static java.util.function.Supplier<java.nio.file.Path> LAST_FILE = () -> net.fabricmc.loader.api.FabricLoader.getInstance()
            .getConfigDir().resolve(dannypx.foe.FishOnMCExtras.MOD_ID).resolve("community_last.json");

    private void saveLast() {
        try {
            java.nio.file.Path f = LAST_FILE.get();
            if (f == null || total.isEmpty()) return;
            Last l = new Last();
            l.kind = kind; l.total = total; l.goal = goal; l.contribution = contribution; l.savedAt = System.currentTimeMillis();
            java.nio.file.Files.createDirectories(f.getParent());
            java.nio.file.Files.writeString(f, GSON.toJson(l));
        } catch (Throwable ignored) {
            // shown from the next answer instead
        }
    }

    /// Package-private for tests.
    void loadLast(long now) {
        if (!total.isEmpty()) return;
        try {
            java.nio.file.Path f = LAST_FILE.get();
            if (f == null || !java.nio.file.Files.exists(f)) return;
            Last l = GSON.fromJson(java.nio.file.Files.readString(f), Last.class);
            if (l == null || l.total == null || l.total.isEmpty() || now - l.savedAt > LAST_MAX_AGE_MS || l.savedAt > now) return;
            kind = l.kind == null ? "" : l.kind;
            total = l.total;
            goal = l.goal == null ? "" : l.goal;
            contribution = l.contribution;
            fromLastSession = true;
            // not lastTotal: a goal hit while you were away mustn't look like one hit just now
        } catch (Throwable ignored) {
            // nothing saved
        }
    }
    //endregion

    /// The server said to wait: check again after that.
    public void retryIn(long ms) {
        lastCheckAt = System.currentTimeMillis() - REFRESH_MS + ms;
    }
    //endregion

    //region For the top bar
    public boolean hasData() {
        return !total.isEmpty() || contribution >= 0 || parse(BossEventHandler.instance().getCommunityGoalCurrent().getString().trim()) >= 0;
    }

    /// The event total: the boss bar's live number when shown, else the last /communityevent.
    public String total() {
        String boss = BossEventHandler.instance().getCommunityGoalCurrent().getString().trim();
        return parse(boss) < 0 ? total : boss;
    }

    public String goal() {
        String boss = BossEventHandler.instance().getCommunityGoalMax().getString().trim();
        return parse(boss) <= 0 ? goal : boss;
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

    /// The Timers panel shows the community catches.
    public static boolean communityInTimers() {
        return Configs.hudConfig.showTimersPanel.get() && Configs.hudConfig.timersShowCommunity.get();
    }
}
