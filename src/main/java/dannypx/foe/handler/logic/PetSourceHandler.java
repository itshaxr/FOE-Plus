package dannypx.foe.handler.logic;

import net.minecraft.ChatFormatting;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Where a new pet came from. The server says "PET DROP! You pulled a COMMON Lynx Pet" when you fish one up; a pet
 * from a contest or an event comes with the server's own words about that contest or event instead. Only a pet
 * fished up counts in your stats (and its dry streaks); a reward pet gets its own popup and isn't counted.
 */
public final class PetSourceHandler {
    private static final PetSourceHandler INSTANCE = new PetSourceHandler();
    public static PetSourceHandler instance() { return INSTANCE; }

    public enum Source { FISHED, CONTEST, EVENT }

    /// "PET DROP! You pulled a ...": a pet fished up.
    static final Pattern PET_DROP = Pattern.compile("^\\s*PET DROP!", Pattern.CASE_INSENSITIVE);
    /// A contest's results or prizes ("CONTEST » You placed #2", "Contest rewards", "won the contest").
    static final Pattern CONTEST = Pattern.compile("\\bcontest\\b.*\\b(reward|rewards|prize|prizes|won|winner|winners|placed|place|results?|#\\d)\\b"
            + "|\\b(reward|rewards|prize|won|placed)\\b.*\\bcontest\\b", Pattern.CASE_INSENSITIVE);
    /// An event's reward ("COMMUNITY EVENT » Reward", "MINI EVENT » MISSION COMPLETE", "event milestone reached").
    static final Pattern EVENT = Pattern.compile("\\bevent\\b.*\\b(reward|rewards|prize|milestone|claimed|complete|completed|unlocked|received)\\b"
            + "|\\b(reward|rewards|prize|milestone|claimed)\\b.*\\bevent\\b", Pattern.CASE_INSENSITIVE);

    /// A "PET DROP!" this close before a new pet: it was fished.
    static final long DROP_MS = 8_000L;
    /// A contest or event reward line this close before a new pet (with no "PET DROP!"): it was that reward.
    static final long REWARD_MS = 60_000L;

    private long dropAt = -1, contestAt = -1, eventAt = -1;

    /// Every game message (never hides any).
    public boolean onGameMessage(String raw) {
        note(raw, System.currentTimeMillis());
        return true;
    }

    /// Package-private for tests.
    void note(String raw, long now) {
        String text = ChatFormatting.stripFormatting(raw == null ? "" : raw);
        if (text == null || text.isBlank()) return;
        text = text.replaceAll("[\\uE000-\\uF8FF]", " ").trim();
        if (PET_DROP.matcher(text).find()) { dropAt = now; return; }
        // a player's chat ("[Rank] Name: I won a contest...") never counts as the server's words; the server's own
        // contest and event lines have no "Name: " in them
        if (text.contains(": ")) return;
        String lower = text.toLowerCase(Locale.ROOT);
        if (CONTEST.matcher(lower).find()) contestAt = now;
        else if (EVENT.matcher(lower).find()) eventAt = now;
    }

    /// Where a pet that just turned up came from.
    public Source sourceOfNewPet() {
        return sourceAt(System.currentTimeMillis());
    }

    /// Package-private for tests: the server's "PET DROP!" wins; then the latest contest or event reward line, if
    /// recent; else it was fished (a pet right after a catch, with nothing to say otherwise).
    Source sourceAt(long now) {
        if (dropAt >= 0 && now - dropAt <= DROP_MS) return Source.FISHED;
        boolean contest = contestAt >= 0 && now - contestAt <= REWARD_MS, event = eventAt >= 0 && now - eventAt <= REWARD_MS;
        if (contest && (!event || contestAt >= eventAt)) return Source.CONTEST;
        if (event) return Source.EVENT;
        return Source.FISHED;
    }

    /// A contest or event reward line came in recently (so a pet turning up now, outside a catch, is a reward).
    public boolean rewardRecently() {
        Source s = sourceOfNewPet();
        return s != Source.FISHED;
    }

    /// The pet's been dealt with: the next one needs its own "PET DROP!" or reward line.
    public void used(Source s) {
        if (s == Source.FISHED) dropAt = -1;
    }
}
