package dannypx.foe.handler.logic;

import net.minecraft.network.chat.Component;

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

    public static ContestTrackerHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new ContestTrackerHandler();
        }
        return INSTANCE;
    }

    public void tick() {
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
        this.isReset = true;
    }
}
