package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.TabOverlayHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the server's /events answer:
 * <pre>
 * EVENT TIMES (1/1)
 * Tackle Shop Restock → 3h 11m 20s
 * Cosmetic Store → 20h 11m 20s
 * FISH FRENZY » A Fish Frenzy is active at Cape Cod (23m 44s)
 * </pre>
 * which re-syncs the Tackle Shop and Cosmetic Store timers and tells the Events panel about a
 * Fish Frenzy (they last an hour from the start). FOE+ runs /events quietly now and then (never
 * while a menu is open) and hides that answer; when you run it yourself it shows as usual.
 * A Fish Frenzy announced in chat starts the hour straight away.
 */
public class EventTimesHandler {
    private static EventTimesHandler INSTANCE = new EventTimesHandler();

    public static EventTimesHandler instance() {
        if (INSTANCE == null) INSTANCE = new EventTimesHandler();
        return INSTANCE;
    }

    static final long FRENZY_MS = 60L * 60L * 1000L;
    private static final long REFRESH_MS = 15L * 60L * 1000L;
    private static final long FIRST_CHECK_MS = 12_000L;
    private static final long HIDE_WINDOW_MS = 3_000L;

    static final Pattern HEADER = Pattern.compile("^\\s*EVENT TIMES\\s*\\(\\d+/\\d+\\)\\s*$", Pattern.CASE_INSENSITIVE);
    static final Pattern TACKLE = Pattern.compile("^\\s*Tackle Shop Restock\\s*[→>»-]+\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
    static final Pattern COSMETIC = Pattern.compile("^\\s*Cosmetic Store\\s*[→>»-]+\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
    /// "FISH FRENZY » A Fish Frenzy is active at Cape Cod (23m 44s)", or a start announcement
    /// naming the place ("... at Hawaii!").
    static final Pattern FRENZY = Pattern.compile("^\\s*FISH FRENZY\\s*[»>]\\s*(.*?)\\bat (?:the )?([A-Z][A-Za-z' ]*?[A-Za-z])\\s*(?:\\(([^)]*)\\))?\\s*[.!]?\\s*$");
    /// "BLOOMING OASIS » ..." with a time left in brackets, when the server gives one.
    static final Pattern OASIS = Pattern.compile("^\\s*BLOOMING OASIS\\s*[»>].*?(?:\\(([^)]*)\\))?\\s*[.!]?\\s*$", Pattern.CASE_INSENSITIVE);
    /// Blooming Oasis lasts 900-960 s (wiki); until we know better, 15 minutes from when it shows.
    static final long OASIS_MS = 15L * 60L * 1000L;
    /// "GOLD RUSH » A Gold Rush has begun! (Base Shard Chance → 1 in 50 25)", or with a time in
    /// brackets in /events.
    static final Pattern GOLD_RUSH = Pattern.compile("^\\s*GOLD RUSH\\s*[»>]\\s*(.*?)\\s*$", Pattern.CASE_INSENSITIVE);
    /// Gold Rush lasts 900-960 s too (wiki): ⚠ on the boss bar, base shard chance 1/50 → 1/25.
    static final long GOLD_RUSH_MS = 15L * 60L * 1000L;
    private static final Pattern BRACKETS = Pattern.compile("\\(([^)]*)\\)");
    private static final Pattern ENDED = Pattern.compile("\\b(ended|over|finished|expired)\\b", Pattern.CASE_INSENSITIVE);
    static final Pattern EVENT_LINE = Pattern.compile("^\\s*[A-Z][A-Z ]+\\s*»");
    /// Any "FABLED » ..." line: /events lists an active Fabled event as
    /// "FABLED » A Fabled Event is active at Danube River".
    static final Pattern FABLED_LINE = Pattern.compile("^\\s*FABLED\\s*[»>]", Pattern.CASE_INSENSITIVE);
    /// The whole /events answer arrives at once; anything this long after its header is something else.
    static final long ANSWER_MS = 2_000L;
    static final Pattern FRENZY_OVER = Pattern.compile("^\\s*FISH FRENZY\\s*[»>].*\\b(ended|over|finished)\\b.*$", Pattern.CASE_INSENSITIVE);

    /// An event shown by a symbol on the boss bar that lasts a set time (Blooming Oasis ♣,
    /// Gold Rush ⚠): started by chat, /events or the symbol showing up, ended by the time running
    /// out (when the server gave it) or the symbol going away.
    static final class Timed {
        final String symbol;
        final long guessMs;
        long endsAt = 0L;
        boolean exact = false;
        boolean onBossBar = false;

        Timed(String symbol, long guessMs) {
            this.symbol = symbol;
            this.guessMs = guessMs;
        }

        /// From chat: {@code ms} left (exact), or -1 for "it's on" (our guess unless already known).
        void started(long ms, long now) {
            if (ms >= 0) { endsAt = now + ms; exact = true; }
            else if (endsAt <= now) { endsAt = now + guessMs; exact = false; }
        }

        void ended() {
            endsAt = 0L;
            exact = false;
        }

        void weather(String weather, long now) {
            boolean on = weather != null && weather.contains(symbol);
            if (on && !onBossBar && endsAt <= now) { endsAt = now + guessMs; exact = false; }
            if (!on && onBossBar) endsAt = 0L; // the symbol went away: it's over
            onBossBar = on;
        }

        boolean active(long now) {
            return onBossBar || endsAt > now;
        }

        long remaining(long now) {
            return Math.max(0L, endsAt - now);
        }
    }

    private final Timed oasis = new Timed("\u2663", OASIS_MS);
    private final Timed goldRush = new Timed("\u26A0", GOLD_RUSH_MS);
    private String frenzyLocation = "";
    private long frenzyEndsAt = 0L;
    private long joinedAt = 0L, lastCheckAt = 0L, hideUntil = 0L;
    private boolean inOurAnswer = false;
    /// The /events answer being read (ours or one you typed): when its header came (0 = none),
    /// whether it was the whole list ("(1/1)") and whether it listed a Fabled event.
    private long answerAt = 0L;
    private boolean answerWhole = false, answerListsFabled = false;

    /// From chat: true when the line is part of our own quiet /events answer (hidden).
    public boolean onGameMessage(net.minecraft.network.chat.Component message) {
        String text = ChatFormatting.stripFormatting(message.getString());
        if (text == null) return false;
        long now = System.currentTimeMillis();
        noteAnswer(text, now);
        boolean ours = now < hideUntil;
        // The server pads its answer with blank lines (before the header too): inside our own
        // answer's window they're hidden, or an empty chat line shows every 15 minutes.
        if (ours && text.isBlank()) return true;
        if (HEADER.matcher(text).matches()) {
            inOurAnswer = ours;
            return ours;
        }
        Matcher m = TACKLE.matcher(text);
        if (m.matches()) {
            long ms = parseDuration(m.group(1));
            if (ms >= 0) StoreTimerHandler.instance().syncTackleShop(ms);
            return ours;
        }
        m = COSMETIC.matcher(text);
        if (m.matches()) {
            long ms = parseDuration(m.group(1));
            if (ms >= 0) StoreTimerHandler.instance().syncCosmeticStore(ms);
            return ours;
        }
        if (FRENZY_OVER.matcher(text).matches()) {
            frenzyEndsAt = 0L;
            frenzyLocation = "";
            return ours;
        }
        m = OASIS.matcher(text);
        if (m.matches()) {
            if (ENDED.matcher(text).find()) oasis.ended();
            else oasis.started(m.group(1) == null ? -1 : parseDuration(m.group(1)), now);
            return ours;
        }
        m = GOLD_RUSH.matcher(text);
        if (m.matches()) {
            onGoldRush(m.group(1), now);
            return ours;
        }
        m = FRENZY.matcher(text);
        if (m.matches()) {
            onFrenzy(m.group(2).trim(), m.group(3), now);
            return ours;
        }
        // Inside our own answer: blank lines, and other event lines ("FABLED » ..."), which are
        // handed to the alerts first since a hidden line never reaches them.
        if (ours && inOurAnswer) {
            if (text.isBlank()) return true;
            if (EVENT_LINE.matcher(text).find()) {
                AlertHandler.instance().onReceiveMessage(message);
                return true;
            }
        }
        return false;
    }

    /// A frenzy at {@code location}: with "23m 44s" left as /events says, or the full hour when
    /// it's just been announced. Package-private for tests.
    void onFrenzy(String location, String left, long now) {
        long ms = left == null ? -1 : parseDuration(left);
        boolean same = location.equalsIgnoreCase(frenzyLocation) && frenzyEndsAt > now;
        frenzyLocation = location;
        if (ms >= 0) frenzyEndsAt = now + ms;
        else if (!same) frenzyEndsAt = now + FRENZY_MS;
    }

    /// The text after "GOLD RUSH »": ended, or begun / active (a time in brackets when /events
    /// gives one; "(Base Shard Chance → 1 in 50 25)" isn't a time). Package-private for tests.
    void onGoldRush(String body, long now) {
        if (ENDED.matcher(body).find()) {
            goldRush.ended();
            return;
        }
        long ms = -1;
        Matcher b = BRACKETS.matcher(body);
        while (b.find()) {
            long t = parseDuration(b.group(1));
            if (t >= 0) ms = t;
        }
        goldRush.started(ms, now);
    }

    /// The boss bar shows ♣ during a Blooming Oasis and ⚠ during a Gold Rush. Package-private for tests.
    void onWeather(String weather, long now) {
        oasis.weather(weather, now);
        goldRush.weather(weather, now);
    }

    /// A Blooming Oasis is on: its ♣ is on the boss bar, or the server said so and its time
    /// isn't up yet.
    public boolean oasisActive() {
        return oasis.active(System.currentTimeMillis());
    }

    /// Time left; {@link #oasisExact()} says whether it came from the server or is the 15 minute guess.
    public long oasisRemainingMs() {
        return oasis.remaining(System.currentTimeMillis());
    }

    public boolean oasisExact() {
        return oasis.exact;
    }

    /// A Gold Rush is on (⚠ on the boss bar, or announced and its time isn't up yet).
    public boolean goldRushActive() {
        return goldRush.active(System.currentTimeMillis());
    }

    public long goldRushRemainingMs() {
        return goldRush.remaining(System.currentTimeMillis());
    }

    public boolean goldRushExact() {
        return goldRush.exact;
    }

    public boolean frenzyActive() {
        return frenzyEndsAt > System.currentTimeMillis();
    }

    public String frenzyLocation() {
        return frenzyLocation;
    }

    public long frenzyRemainingMs() {
        return Math.max(0L, frenzyEndsAt - System.currentTimeMillis());
    }

    private static final Pattern DURATION_PART = Pattern.compile(
            "(\\d+)\\s*(days?|d|hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\\b");

    /// "3h 11m 20s" → ms; "23m 44s", "45s", "1d 2h", "30 minutes", "1 hour 30 minutes" too.
    /// -1 when there's no number. Public for the booster timer.
    public static long parseDuration(String text) {
        Matcher m = DURATION_PART.matcher(text.toLowerCase(Locale.ROOT));
        long ms = 0;
        boolean any = false;
        while (m.find()) {
            any = true;
            long n = Long.parseLong(m.group(1));
            char unit = m.group(2).charAt(0);
            ms += switch (unit) {
                case 'd' -> n * 86_400_000L;
                case 'h' -> n * 3_600_000L;
                case 'm' -> n * 60_000L;
                default -> n * 1_000L;
            };
        }
        return any ? ms : -1;
    }

    /// Package-private for tests: follows an /events answer, whoever ran it.
    void noteAnswer(String text, long now) {
        if (HEADER.matcher(text).matches()) {
            answerAt = now;
            answerWhole = text.replaceAll("\\s", "").contains("(1/1)");
            answerListsFabled = false;
        } else if (answerAt > 0L && now - answerAt <= ANSWER_MS && FABLED_LINE.matcher(text).find()) {
            answerListsFabled = true;
        }
    }

    /// Package-private for tests: once an /events answer is over, true when it was the whole list
    /// and had no Fabled event in it (so any Fabled event FOE+ still shows is over). Each answer
    /// is only looked at once.
    boolean answerHadNoFabled(long now) {
        if (answerAt == 0L || now - answerAt <= ANSWER_MS) return false;
        boolean none = answerWhole && !answerListsFabled;
        answerAt = 0L;
        return none;
    }

    /// Quiet /events now and then (only when nothing is open and you haven't just typed).
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.player == null || mc.getConnection() == null || !TabOverlayHandler.instance().isInInstance()) {
            joinedAt = 0L;
            answerAt = 0L;
            return;
        }
        // A Fabled event caught while you were away never says so in chat: /events not listing it
        // is how we know it's over.
        long answeredAt = answerAt;
        if (answerHadNoFabled(now)) AlertHandler.instance().clearFabled(answeredAt);
        if (joinedAt == 0L) joinedAt = now;
        try {
            onWeather(dannypx.foe.handler.fetch.BossEventHandler.instance().weatherIcons(), now);
        } catch (RuntimeException e) {
            // no boss bar yet
        }
        if (!Configs.hudConfig.eventsAutoRefresh.get()) return;
        boolean due = lastCheckAt == 0L ? now - joinedAt >= FIRST_CHECK_MS : now - lastCheckAt >= REFRESH_MS;
        if (!due || !SilentMenuHandler.instance().isQuietForBackgroundCommand()) return;
        lastCheckAt = now;
        hideUntil = now + HIDE_WINDOW_MS;
        SilentMenuHandler.instance().noteBackgroundCommand("events");
        mc.getConnection().sendCommand("events");
    }

    /// The server said to wait: check again after that.
    public void retryIn(long ms) {
        lastCheckAt = System.currentTimeMillis() - REFRESH_MS + ms;
    }
}
