package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.TabOverlayHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which instance your friends and crew are on, for the Timers &amp; Crew list ("InfernalShadow (i5)"). The
 * server only says so in answer to /find:
 * <pre>
 * FIND » InfernalShadow is currently on [instance5]
 * </pre>
 * so FOE+ asks quietly, one person every couple of seconds (never just after you typed something or while
 * one of its own silent menus is on the way): everyone as soon as you join an instance, anyone who comes
 * online straight away, then online people every 45 seconds and the rest every 2 minutes. The answers to
 * its own asks are hidden; a /find you run yourself shows as usual, and what it says is used too.
 */
public class FindHandler {
    private static FindHandler INSTANCE = new FindHandler();

    public static FindHandler instance() {
        if (INSTANCE == null) INSTANCE = new FindHandler();
        return INSTANCE;
    }

    /// "FIND » InfernalShadow is currently on [instance5]"
    static final Pattern ON = Pattern.compile("^\\s*FIND\\s*[»>]\\s*(\\S+) is currently on \\[?([^\\]]+?)\\]?\\s*\\.?\\s*$", Pattern.CASE_INSENSITIVE);
    /// Any other FIND answer.
    static final Pattern ANY = Pattern.compile("^\\s*FIND\\s*[»>]\\s*(\\S+)\\b.*$", Pattern.CASE_INSENSITIVE);
    /// Any line from FIND.
    static final Pattern FIND_LINE = Pattern.compile("^\\s*FIND\\s*[»>]", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTANCE_N = Pattern.compile("^instance\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);

    /// How often each person is asked about: online ones often (they hop instances), the rest less.
    static final long ONLINE_EVERY_MS = 45_000L, OFFLINE_EVERY_MS = 2L * 60_000L;
    /// Between our own asks (a /find opens no menu, so these can come quicker than other background commands).
    static final long ASK_GAP_MS = 2_500L;
    /// What an answer is good for before it's too old to show.
    static final long FRESH_MS = 4L * 60_000L;
    /// Our own ask's answer comes within this.
    private static final long ANSWER_MS = 4_000L;

    record Seen(String where, long at) {}

    private final Map<String, Seen> seen = new HashMap<>();
    private final Map<String, Long> askedAt = new HashMap<>();
    { dannypx.foe.handler.logic.MemoryGuard.register("findInstances", () -> seen.size() + askedAt.size(), () -> { seen.clear(); askedAt.clear(); }); }
    private String pending = null;
    private long pendingAt = 0L;
    private long nextAskAt = 0L;

    /// "Instance 5" for someone /find put on instance5 (whatever else it said, as it said it), or null when unknown,
    /// not on, or the answer is too old.
    public String instanceOf(String name) {
        if (name == null) return null;
        Seen s = seen.get(name.toLowerCase(Locale.ROOT));
        if (s == null || s.where() == null || System.currentTimeMillis() - s.at() > FRESH_MS) return null;
        return s.where();
    }

    /// Package-private for tests: "instance5" → "Instance 5"; anything else as it is.
    static String shortName(String where) {
        String w = where == null ? "" : where.trim();
        Matcher m = INSTANCE_N.matcher(w);
        return m.matches() ? "Instance " + m.group(1) : w;
    }

    /// From chat: true when the line is the answer to our own quiet /find (hidden).
    public boolean onGameMessage(net.minecraft.network.chat.Component message) {
        String text = ChatFormatting.stripFormatting(message.getString());
        return text != null && onLine(text, System.currentTimeMillis());
    }

    /// Package-private for tests.
    boolean onLine(String text, long now) {
        Matcher m = ON.matcher(text);
        String who;
        if (m.matches()) {
            who = m.group(1);
            seen.put(who.toLowerCase(Locale.ROOT), new Seen(shortName(m.group(2)), now));
        } else {
            // any other FIND answer naming the one we asked about ("... is not online", "Couldn't find ..."): not on
            if (!FIND_LINE.matcher(text).find() || pending == null) return false;
            if (!text.toLowerCase(Locale.ROOT).contains(pending.toLowerCase(Locale.ROOT))) {
                // an answer that names nobody ("FIND » Error. Unable to obtain server!"), just after our own ask:
                // ours too, hidden; it says nothing about where they are
                if (now - pendingAt > ANSWER_MS) return false;
                pending = null;
                return true;
            }
            who = pending;
            seen.put(who.toLowerCase(Locale.ROOT), new Seen(null, now));
        }
        boolean ours = pending != null && who.equalsIgnoreCase(pending) && now - pendingAt <= ANSWER_MS;
        if (ours) pending = null;
        return ours;
    }

    /// Package-private for tests: who to ask about next (null = nobody due): never asked first, then the
    /// longest ago, online people every couple of minutes and the rest less often.
    String next(List<String> online, List<String> offline, long now) {
        String best = null;
        long bestAt = Long.MAX_VALUE;
        for (int pass = 0; pass < 2; pass++) {
            for (String n : pass == 0 ? online : offline) {
                long at = askedAt.getOrDefault(n.toLowerCase(Locale.ROOT), 0L);
                long every = pass == 0 ? ONLINE_EVERY_MS : OFFLINE_EVERY_MS;
                if (at != 0L && now - at < every) continue;
                if (at < bestAt) { best = n; bestAt = at; }
            }
            if (best != null) return best;
        }
        return null;
    }

    void noteAsked(String name, long now) {
        askedAt.put(name.toLowerCase(Locale.ROOT), now);
        pending = name;
        pendingAt = now;
    }

    /// The server said to wait: no ask until then.
    public void retryIn(long ms) {
        nextAskAt = System.currentTimeMillis() + ms;
    }

    /// Everyone is asked about again right away (on joining, or moving to another instance).
    void askEveryoneAgain() {
        askedAt.clear();
    }

    /// Package-private for tests: people who've just come online are asked about at once.
    void noteOnline(List<String> online) {
        java.util.Set<String> nowOn = new java.util.HashSet<>();
        for (String n : online) nowOn.add(n.toLowerCase(Locale.ROOT));
        for (String n : nowOn) if (!wasOnline.contains(n)) askedAt.remove(n);
        wasOnline = nowOn;
    }

    private java.util.Set<String> wasOnline = new java.util.HashSet<>();
    private String lastInstance = null;

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.player == null || mc.getConnection() == null || !TabOverlayHandler.instance().isInInstance()) {
            lastInstance = null;      // joining again asks about everyone straight away
            return;
        }
        String here = TabOverlayHandler.instance().getInstance();
        if (!here.equals(lastInstance)) {
            lastInstance = here;
            askEveryoneAgain();
            nextAskAt = Math.max(nextAskAt, now + 1_500L);     // a moment to settle in first
        }
        // the friends screen shows instances too, so it asks while open even with the timers' instances off
        boolean wanted = Configs.inventoryScreenConfig.timersInstances.get() || mc.screen instanceof dannypx.foe.screens.FriendsScreen;
        if (!wanted || now < nextAskAt) return;
        List<String>[] people = dannypx.foe.handler.renderer.TimersCrewBox.peopleByStatus();
        noteOnline(people[0]);
        if (!SilentMenuHandler.instance().isQuietForChatCommand(ASK_GAP_MS)) return;
        String who = next(people[0], people[1], now);
        if (who == null || !who.matches("[A-Za-z0-9_]{1,16}")) return;
        noteAsked(who, now);
        SilentMenuHandler.instance().noteBackgroundCommand("find " + who);
        mc.getConnection().sendCommand("find " + who);
    }
}
