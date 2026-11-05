package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.BossEventHandler;
import dannypx.foe.helper.FoeLocations;
import dannypx.foe.item.FishTagObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Keeps track of the current fishing contest, read from the server's /contest chat output.
 * Ported from FOECollab's ContestHandler (AGPL-3.0). The chat-suppression features were left out;
 * this only collects data for the Contest panel.
 * <p>
 * Contests run every half hour: the first 30 minutes of each hour are the contest, the second
 * 30 minutes show results until the next one starts.
 */
public class ContestTrackerHandler {
    private static ContestTrackerHandler INSTANCE = new ContestTrackerHandler();

    private static final long HALF_HOUR = 30L * 60L * 1000L;

    public long timeLeft = 0L;
    public boolean isContest = false;
    public boolean isReset = true;
    public String type = "";
    public String location = "";
    public int levelLow = 0;
    public int levelHigh = 0;
    public long lastUpdated = 0L;
    public String firstName = "";
    public String firstStat = "";
    public String secondName = "";
    public String secondStat = "";
    public String thirdName = "";
    public String thirdStat = "";
    public String rank = "Unranked";
    public String rankStat = "";
    public int totalParticipants = 0;

    //region Auto refresh (ported from FOECollab's ContestHandler / FishCatchHandler)
    private static final long AUTO_REFRESH_MS = 60_000L;
    private static final long MIN_REFRESH_GAP_MS = 5_000L;
    private static final long HIDE_WINDOW_MS = 4_000L;

    /// The first automatic refresh waits this long after a contest starts, so it never lands on
    /// top of the server's own "contest started" announcement.
    private static final long START_GRACE_MS = 15_000L;

    private long lastRefresh = 0L;
    /// While > now, the /contest output we asked for is hidden from chat.
    private long hideUntil = 0L;
    private boolean hidingOutput = false;
    /// After the hidden output ends, the one closing bar right after it is hidden until this time.
    private long hideTailUntil = 0L;
    /// A bar that arrived while waiting for our /contest output. Held back until we know whether
    /// it opens that output (then it's dropped) or belongs to some other message (then it's put
    /// back in chat, so other messages keep their own spacing).
    private Component heldSeparator = null;
    /// Area you were in last tick, to refresh the rankings when you move.
    private String lastArea = "";
    private boolean announcePersonalBest = false;
    /// Your biggest qualifying fish this contest (lb or in, depending on the contest type).
    private float personalBest = 0f;
    //endregion

    public static ContestTrackerHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new ContestTrackerHandler();
        }
        return INSTANCE;
    }

    public void tick() {
        this.flushHeldSeparator(false);
        if (refreshDueAt != 0L && System.currentTimeMillis() >= refreshDueAt) {
            long sinceLast = System.currentTimeMillis() - lastRefresh;
            if (sinceLast >= MIN_REFRESH_GAP_MS) {
                refreshDueAt = 0L;
                refresh(false);
            }
        }
        this.refreshOnAreaChange();
        this.autoRefresh();
        long hourMs = System.currentTimeMillis() % (2 * HALF_HOUR);
        this.timeLeft = HALF_HOUR - (hourMs % HALF_HOUR);

        if (hourMs < HALF_HOUR) {
            this.isContest = true;
            this.isReset = false;
        } else {
            this.isContest = false;
            // Clear the old results a while after the contest ended.
            if (this.lastUpdated != 0L && System.currentTimeMillis() - this.lastUpdated > 15000L
                    && hourMs > HALF_HOUR + 15000L) {
                this.reset();
            }
        }
    }

    /// Every minute during a contest, quietly run /contest so the panel stays current.
    private void autoRefresh() {
        if (!isContest || !Configs.hudConfig.contestAutoRefresh.get()) return;
        long sinceStart = System.currentTimeMillis() % (2 * HALF_HOUR);
        if (sinceStart < START_GRACE_MS) return;
        if (System.currentTimeMillis() - lastRefresh >= AUTO_REFRESH_MS) refresh(false);
    }

    /// Moving to another area refreshes the rankings (quietly), so the panel is current wherever
    /// you go. The rankings themselves stay on the panel the whole time.
    private void refreshOnAreaChange() {
        String area = BossEventHandler.instance().getLocation().getString().trim();
        if (area.equals(lastArea)) return;
        boolean firstReading = lastArea.isEmpty();
        lastArea = area;
        if (firstReading || area.isEmpty() || !isContest || !Configs.hudConfig.contestAutoRefresh.get()) return;
        if (System.currentTimeMillis() % (2 * HALF_HOUR) < START_GRACE_MS) return;
        refresh(false);
    }

    private void refresh(boolean personalBestRefresh) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.getConnection() == null || now - lastRefresh < MIN_REFRESH_GAP_MS) return;
        // Never while a menu (inventory, chest, Auction House...) is open or right after you typed
        // something: wait and try again once you're back in the game.
        if (!SilentMenuHandler.instance().isQuietForBackgroundCommand()) {
            pendingPersonalBest |= personalBestRefresh;
            if (refreshDueAt == 0L) refreshDueAt = now + 1_000L;
            return;
        }
        lastRefresh = now;
        hideUntil = now + HIDE_WINDOW_MS;
        hidingOutput = false;
        announcePersonalBest = personalBestRefresh || pendingPersonalBest;
        pendingPersonalBest = false;
        SilentMenuHandler.instance().noteBackgroundCommand("contest");
        mc.getConnection().sendCommand("contest");
    }

    /// The server said to wait: try the refresh again after that (keeping a personal best to announce).
    public void retryRefreshIn(long ms) {
        pendingPersonalBest |= announcePersonalBest;
        lastRefresh = 0L;
        refreshDueAt = System.currentTimeMillis() + ms;
    }

    /// A personal-best refresh that had to wait still announces the new best when it runs.
    private boolean pendingPersonalBest = false;

    /// Called on every catch: refreshes the contest when this fish beats your best for it.
    public void onFishCaught(FishTagObject fish) {
        if (!isContest || type.isEmpty() || !Configs.hudConfig.contestRefreshOnBest.get()) return;

        // Must be caught where the contest is.
        FoeLocations.Location contestLocation = FoeLocations.fromName(location);
        FoeLocations.Location here = FoeLocations.fromName(BossEventHandler.instance().getLocation().getString());
        if (contestLocation == null || contestLocation != here) return;

        // Must be the contest's fish, e.g. "Heaviest Perches" and a fish whose group is "perch".
        String lowerType = type.toLowerCase(Locale.ROOT);
        String target = lowerType.replaceFirst("^(heaviest|longest|biggest|largest)\\s+", "").replaceAll("[^a-z]", "");
        String group = fish.getString("group").toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (target.isEmpty() || group.isEmpty() || !(target.contains(group) || group.contains(singular(target)))) return;

        float value = lowerType.startsWith("longest") ? fish.getLength() : fish.getWeight();
        float best = Math.max(personalBest, parseNumber(rankStat));
        if (value > best) {
            personalBest = value;
            refresh(true);
        }
    }

    private static String singular(String word) {
        if (word.endsWith("ches") || word.endsWith("shes") || word.endsWith("sses")) return word.substring(0, word.length() - 2);
        if (word.endsWith("s") && word.length() > 3) return word.substring(0, word.length() - 1);
        return word;
    }

    private static float parseNumber(String text) {
        String digits = text.replaceAll("[^0-9.]", "");
        if (digits.isEmpty()) return 0f;
        try {
            return Float.parseFloat(digits);
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    /**
     * Chat filter for every game message (ALLOW_GAME). Hides the /contest output the mod asked
     * for itself (from the rankings header to your "You →" line, plus the bars right around it)
     * and reads it, and keeps fishing contest messages visually apart from the rest of chat.
     * Returns true when the message should be shown.
     */
    public boolean filterGameMessage(Component message) {
        String text = message.getString();
        long now = clock.getAsLong();

        if (this.hideAutoRefreshMessage(message, text, now)) return false;

        boolean separator = isSeparator(text);
        // Lines arriving right after a contest line belong to the same block (the server sends a
        // block in one go), even when they don't look like contest lines themselves.
        boolean contestLine = isContestLine(text) || (inContestBlock && now - lastContestLineAt <= BLOCK_GAP_MS);
        // A blank line before a contest message and after the contest block, unless chat
        // already has one there (the server's own bars count).
        if (contestLine && !inContestBlock && !lastShownWasSeparator) addSpacer();
        else if (!contestLine && !separator && inContestBlock && !lastShownWasSeparator) addSpacer();
        if (contestLine) lastContestLineAt = now;
        if (!separator) inContestBlock = contestLine;
        lastShownWasSeparator = separator;
        return true;
    }

    /// A block's lines arrive together (same tick); anything later is a new message.
    private static final long BLOCK_GAP_MS = 150L;
    java.util.function.LongSupplier clock = System::currentTimeMillis;
    private long lastContestLineAt = 0L;
    /// Where extra chat lines go (the chat window; swapped out in tests).
    java.util.function.Consumer<Component> chatSink = line -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gui != null) mc.gui.getChat().addMessage(line);
    };

    /// A player chat message was shown: it ends any contest block.
    public void onChatMessageShown() {
        if (inContestBlock && !lastShownWasSeparator) addSpacer();
        inContestBlock = false;
        lastShownWasSeparator = false;
    }

    private boolean inContestBlock = false;
    private boolean lastShownWasSeparator = true;

    private void addSpacer() {
        chatSink.accept(Component.empty());
        lastShownWasSeparator = true;
    }

    /// Server lines that belong to a fishing contest: announcements ("FISHING CONTEST", "CONTEST »")
    /// and the lines of a /contest you ran yourself.
    static boolean isContestLine(String text) {
        String t = text.strip();
        return t.contains("CONTEST") || t.startsWith("Type: ") || t.startsWith("Location: ") || t.startsWith("Level: ")
                || t.startsWith("You → ") || t.startsWith("\uF060") || t.startsWith("\uF061") || t.startsWith("\uF062")
                || t.startsWith("[Contest]");
    }

    /// True when the message is part of the mod's own /contest output and must be hidden.
    private boolean hideAutoRefreshMessage(Component message, String text, long now) {
        // The closing bar right under the hidden output ("You →" line). Only the very next line.
        if (hideTailUntil != 0L) {
            boolean closingBar = now <= hideTailUntil && isSeparator(text);
            hideTailUntil = 0L;
            if (closingBar) return true;
        }

        if (now > hideUntil) {
            hidingOutput = false;
            flushHeldSeparator(true);
            return false;
        }

        if (text.contains("FISHING CONTEST RANKINGS")) {
            hidingOutput = true;
            heldSeparator = null; // it was the bar opening our output
        }
        if (!hidingOutput) {
            if (isSeparator(text)) {
                // Might be the bar opening our output; hold it until the next line says.
                flushHeldSeparator(true);
                heldSeparator = message;
                return true;
            }
            // Something else arrived first: give back the bar we held, in order.
            flushHeldSeparator(true);
            return false;
        }

        this.onReceiveMessage(message);
        if (text.startsWith("You → ")) {
            hidingOutput = false;
            hideUntil = 0L;
            hideTailUntil = now + 1500L;
            if (announcePersonalBest && Minecraft.getInstance().player != null) {
                // [FOE+] CONTEST New personal best · Rank #4 · 8.0 lb
                dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.line("CONTEST", dannypx.foe.helper.FoeChat.GOLD)
                        .append(dannypx.foe.helper.FoeChat.white("New personal best"))
                        .append(dannypx.foe.helper.FoeChat.sep())
                        .append(dannypx.foe.helper.FoeChat.text("Rank "))
                        .append(dannypx.foe.helper.FoeChat.highlight(rank))
                        .append(rankStat.isEmpty() ? Component.empty() : Component.empty()
                                .append(dannypx.foe.helper.FoeChat.sep())
                                .append(dannypx.foe.helper.FoeChat.white(rankStat))));
            }
            announcePersonalBest = false;
        }
        return true;
    }

    /// Puts a held-back bar into chat. With {@code force} false it only does so once the wait for
    /// our /contest output is over (called every tick, so a lone bar is never lost).
    private void flushHeldSeparator(boolean force) {
        if (heldSeparator == null) return;
        if (!force && System.currentTimeMillis() <= hideUntil) return;
        Component bar = heldSeparator;
        heldSeparator = null;
        chatSink.accept(bar);
        lastShownWasSeparator = true;
    }

    /// A blank or bar-only chat line: the server draws its separators as struck-through spaces
    /// (or dashes/box lines), so the plain text is empty or only those characters.
    private static boolean isSeparator(String text) {
        return text.isBlank() || text.strip().chars().allMatch(c -> c == '-' || c == '=' || c == '_'
                || c == '─' || c == '━' || c == '▬' || c == '▭' || Character.isWhitespace(c));
    }

    /// "CONTESTS » 26.85lb → 27.45lb (+0.6lb)": the server telling you your contest catch improved.
    private static final java.util.regex.Pattern IMPROVED = java.util.regex.Pattern.compile("^CONTESTS?\\s*».*?→\\s*([0-9][0-9.,]*\\s*[A-Za-z\"]*)");
    /// A refresh we owe after an improvement that came too soon after the last one.
    private long refreshDueAt = 0L;

    public void onReceiveMessage(Component message) {
        String text = message.getString();

        // Your contest catch improved: show the new number right away, then quietly re-read the
        // rankings (your rank may have changed too).
        java.util.regex.Matcher improved = IMPROVED.matcher(text.strip());
        if (improved.find()) {
            String value = improved.group(1).trim();
            this.rankStat = value;
            this.personalBest = Math.max(this.personalBest, parseNumber(value));
            if (this.isContest) this.refreshDueAt = System.currentTimeMillis() + 1_000L;
            return;
        }

        if (text.contains("FISHING CONTEST RANKINGS")) {
            this.lastUpdated = System.currentTimeMillis();
            this.isReset = false;
            return;
        }

        if (text.startsWith("You → ")) {
            int arrow = text.indexOf(" → ") + 3;
            int paren = text.indexOf("(", arrow);
            this.rank = (paren > arrow ? text.substring(arrow, paren) : text.substring(arrow)).trim();
            this.rankStat = "";
            if (!this.rank.contains("Unranked") && paren > 0) {
                int close = text.indexOf(")", paren);
                if (close > paren) this.rankStat = text.substring(paren + 1, close);
            }
            int outOf = text.indexOf("(out of ");
            if (outOf >= 0) {
                int start = outOf + "(out of ".length();
                int end = text.indexOf(")", start);
                if (end > start) {
                    try {
                        this.totalParticipants = Integer.parseInt(text.substring(start, end).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return;
        }

        if (text.startsWith("Type: ")) {
            this.type = text.substring(text.indexOf(": ") + 2).trim();
        } else if (text.startsWith("Location: ")) {
            this.location = text.substring(text.indexOf(": ") + 2).trim();
        } else if (text.startsWith("Level: ")) {
            String levels = text.substring(text.indexOf(": ") + 2);
            String[] parts = levels.split("-");
            if (parts.length == 2) {
                try {
                    this.levelLow = Integer.parseInt(parts[0].trim());
                    this.levelHigh = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ignored) {
                }
            }
        } else if (text.startsWith("")) {
            String[] place = parsePlace(text, "");
            if (place != null) { this.firstName = place[0]; this.firstStat = place[1]; }
        } else if (text.startsWith("")) {
            String[] place = parsePlace(text, "");
            if (place != null) { this.secondName = place[0]; this.secondStat = place[1]; }
        } else if (text.startsWith("")) {
            String[] place = parsePlace(text, "");
            if (place != null) { this.thirdName = place[0]; this.thirdStat = place[1]; }
        }
    }

    /// "<glyph> Name → stat" to {name, stat}, or null when the line doesn't match.
    private static String[] parsePlace(String text, String glyph) {
        int nameStart = text.indexOf(glyph) + glyph.length();
        int arrow = text.indexOf(" →");
        int statStart = text.indexOf("→ ");
        if (arrow <= nameStart || statStart < 0) return null;
        return new String[]{text.substring(nameStart, arrow).trim(), text.substring(statStart + 2).trim()};
    }

    public boolean hasData() {
        return !this.type.isEmpty();
    }

    private void reset() {
        this.type = "";
        this.location = "";
        this.levelLow = 0;
        this.levelHigh = 0;
        this.lastUpdated = 0L;
        this.firstName = "";
        this.firstStat = "";
        this.secondName = "";
        this.secondStat = "";
        this.thirdName = "";
        this.thirdStat = "";
        this.rank = "Unranked";
        this.rankStat = "";
        this.totalParticipants = 0;
        this.personalBest = 0f;
        this.isReset = true;
    }
}
