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

    private long lastRefresh = 0L;
    /// While > now, the /contest output we asked for is hidden from chat.
    private long hideUntil = 0L;
    private boolean hidingOutput = false;
    /// After the hidden output ends, separator bars are still hidden until this time.
    private long hideTailUntil = 0L;
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
        if (System.currentTimeMillis() - lastRefresh >= AUTO_REFRESH_MS) refresh(false);
    }

    private void refresh(boolean personalBestRefresh) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.getConnection() == null || now - lastRefresh < MIN_REFRESH_GAP_MS) return;
        lastRefresh = now;
        hideUntil = now + HIDE_WINDOW_MS;
        hidingOutput = false;
        announcePersonalBest = personalBestRefresh;
        mc.getConnection().sendCommand("contest");
    }

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
     * Chat filter: hides the /contest output that the mod asked for itself (from the rankings
     * header to your "You →" line), and reads it. Returns true when the message should be hidden.
     */
    public boolean hideAutoRefreshMessage(Component message) {
        long now = System.currentTimeMillis();
        String text = message.getString();

        // Separator bars right after the hidden output (the line under "You →").
        if (now <= hideTailUntil && isSeparator(text)) return true;

        if (now > hideUntil) {
            hidingOutput = false;
            return false;
        }
        if (text.contains("FISHING CONTEST RANKINGS")) hidingOutput = true;
        // The bar printed just before the rankings header.
        if (!hidingOutput) return isSeparator(text);

        this.onReceiveMessage(message);
        if (text.startsWith("You → ")) {
            hidingOutput = false;
            hideUntil = 0L;
            hideTailUntil = now + 1500L;
            if (announcePersonalBest && Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal("[Contest] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("New personal best! ").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("Rank: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(rank).withStyle(ChatFormatting.WHITE))
                        .append(rankStat.isEmpty() ? Component.empty() : Component.literal(" (" + rankStat + ")").withStyle(ChatFormatting.GRAY)), false);
            }
            announcePersonalBest = false;
        }
        return true;
    }

    /// A blank or bar-only chat line: the server draws its separators as struck-through spaces
    /// (or dashes/box lines), so the plain text is empty or only those characters.
    private static boolean isSeparator(String text) {
        return text.isBlank() || text.strip().chars().allMatch(c -> c == '-' || c == '=' || c == '_'
                || c == '─' || c == '━' || c == '▬' || c == '▭' || Character.isWhitespace(c));
    }

    public void onReceiveMessage(Component message) {
        String text = message.getString();

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
