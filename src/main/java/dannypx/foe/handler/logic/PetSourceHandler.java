package dannypx.foe.handler.logic;

import net.minecraft.ChatFormatting;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Where a new pet came from. The server says "PET DROP! You pulled a COMMON Lynx Pet" when you fish one up; a pet
 * from a contest, an event or your daily missions comes with the server's own words about that contest or event instead. Only a pet
 * fished up (with its "PET DROP!") counts in your stats, all time and this session, and its dry streaks; a reward pet gets its own
 * popup and isn't counted, and a pet with neither (where it came from isn't known) isn't counted or told.
 */
public final class PetSourceHandler {
    private static final PetSourceHandler INSTANCE = new PetSourceHandler();
    public static PetSourceHandler instance() { return INSTANCE; }

    public enum Source { FISHED, CONTEST, EVENT, QUEST, UNKNOWN }

    /// "PET DROP! You pulled a ...": a pet you fished up. Only yours: the server also tells everyone when someone
    /// else pulls a big one ("PET DROP! Name pulled a MYTHICAL ..."), and that line is never hidden or counted.
    static final Pattern PET_DROP = Pattern.compile("^\\s*PET DROP!\\s*You\\b", Pattern.CASE_INSENSITIVE);
    /// A contest's results or prizes ("CONTEST » You placed #2", "Contest rewards", "won the contest").
    static final Pattern CONTEST = Pattern.compile("\\bcontest\\b.*\\b(reward|rewards|prize|prizes|won|winner|winners|placed|place|results?|#\\d)\\b"
            + "|\\b(reward|rewards|prize|won|placed)\\b.*\\bcontest\\b", Pattern.CASE_INSENSITIVE);
    /// An event's reward ("COMMUNITY EVENT » Reward", "MINI EVENT » MISSION COMPLETE", "event milestone reached").
    static final Pattern EVENT = Pattern.compile("\\bevent\\b.*\\b(reward|rewards|prize|milestone|claimed|complete|completed|unlocked|received)\\b"
            + "|\\b(reward|rewards|prize|milestone|claimed)\\b.*\\bevent\\b", Pattern.CASE_INSENSITIVE);

    /// Daily missions or quests done ("DAILY MISSIONS » You have completed all base daily missions!", "Quest complete").
    static final Pattern QUEST = Pattern.compile("\\b(missions?|quests?)\\b.*\\b(complete|completed|reward|rewards|claimed|finished)\\b"
            + "|\\b(complete|completed|reward|rewards|claimed|finished)\\b.*\\b(missions?|quests?)\\b", Pattern.CASE_INSENSITIVE);

    /// A "PET DROP!" this close before a new pet: it was fished.
    static final long DROP_MS = 15_000L;
    /// A contest or event reward line this close before a new pet (with no "PET DROP!"): it was that reward.
    static final long REWARD_MS = 60_000L;

    private long dropAt = -1, contestAt = -1, eventAt = -1, questAt = -1;

    /// The server's "PET DROP!" line can be hidden: FOE+ shows its own PET PULLED! line for the pet.
    public static boolean hideDropLine(String raw) {
        String text = ChatFormatting.stripFormatting(raw == null ? "" : raw);
        if (text == null) return false;
        text = text.replaceAll("[\\uE000-\\uF8FF]", " ").trim();
        var c = dannypx.foe.config.Configs.hudConfig;
        return PET_DROP.matcher(text).find() && !text.contains(": ") && c.showPetCatchChat.get() && c.hideServerPullLines.get();
    }

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
        if (PET_DROP.matcher(text).find()) { dropAt = now; lookSoon(); return; }
        // a player's chat ("[Rank] Name: I won a contest...") never counts as the server's words; the server's own
        // contest and event lines have no "Name: " in them
        if (text.contains(": ")) return;
        String lower = text.toLowerCase(Locale.ROOT);
        if (CONTEST.matcher(lower).find()) { contestAt = now; lookSoon(); }
        else if (EVENT.matcher(lower).find()) { eventAt = now; lookSoon(); }
        else if (QUEST.matcher(lower).find()) { questAt = now; lookSoon(); }
    }

    /// After a line saying you got a pet: look for it in your inventory a second and three seconds on (once the
    /// pet has arrived), in case it wasn't found with a catch.
    private static void lookSoon() {
        try {
            CodeExecuterHandler.runLater(20, () -> CatchingHandler.instance().lookForNewPets());
            CodeExecuterHandler.runLater(60, () -> CatchingHandler.instance().lookForNewPets());
        } catch (RuntimeException | NoClassDefFoundError ignored) {
            // (the tests run without the game)
        }
    }

    /// Where a pet that just turned up came from.
    public Source sourceOfNewPet() {
        return sourceAt(System.currentTimeMillis());
    }

    /// Package-private for tests: the server's "PET DROP!" wins; then the latest contest, event or quest reward
    /// line, if recent; else where it came from isn't known, and it's never counted as fished.
    Source sourceAt(long now) {
        if (dropAt >= 0 && now - dropAt <= DROP_MS) return Source.FISHED;
        // the most recent reward line wins
        Source best = Source.UNKNOWN;
        long bestAt = -1;
        long[] at = {contestAt, eventAt, questAt};
        Source[] kind = {Source.CONTEST, Source.EVENT, Source.QUEST};
        for (int i = 0; i < at.length; i++) {
            if (at[i] >= 0 && now - at[i] <= REWARD_MS && at[i] >= bestAt) { best = kind[i]; bestAt = at[i]; }
        }
        return best;
    }

    /// A contest or event reward line came in recently (so a pet turning up now, outside a catch, is a reward).
    public boolean rewardRecently() {
        Source s = sourceOfNewPet();
        return s != Source.FISHED && s != Source.UNKNOWN;
    }

    /// The pet's been dealt with: the next one needs its own "PET DROP!" or reward line.
    public void used(Source s) {
        if (s == Source.FISHED) dropAt = -1;
    }
}
