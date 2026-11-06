package dannypx.foe.handler.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dannypx.foe.FishOnMCExtras;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Countdowns for the server's shops: the Tackle Shop restocks every 4 hours (6pm EST is one of
 * them; FOE's timer, re-synced every time "TACKLE SHOP » Tackle items restocked!" shows in chat)
 * and the Cosmetic Store resets every day at 8pm New York time. Also your votes: every
 * "VOTE » You received ..." line is one site voted, which counts for 24 hours; and whether you've
 * opened the Tackle Shop since it last restocked (the ✓).
 */
public class StoreTimerHandler {
    private static StoreTimerHandler INSTANCE = new StoreTimerHandler();

    public static StoreTimerHandler instance() {
        if (INSTANCE == null) INSTANCE = new StoreTimerHandler();
        return INSTANCE;
    }

    static final long TACKLE_PERIOD_MS = 4L * 60 * 60 * 1000;
    /// A known restock: 5 Nov 2026, 6pm EST (23:00 UTC). Every 4 hours from there.
    static final long DEFAULT_TACKLE_ANCHOR = Instant.parse("2026-11-05T23:00:00Z").toEpochMilli();
    static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    static final int COSMETIC_HOUR = 20;

    /// Vote sites on the server.
    public static final int VOTE_SITES = 7;
    static final long VOTE_COOLDOWN_MS = 24L * 60 * 60 * 1000;

    private static final class Saved {
        long tackleAnchor = DEFAULT_TACKLE_ANCHOR;
        /// When you last opened the Tackle Shop.
        long tackleCheckedAt = 0L;
        /// When each vote in the last 24 hours went through, oldest first.
        java.util.List<Long> votes = new java.util.ArrayList<>();
    }

    private Saved saved;

    java.util.function.Supplier<Path> fileLocation =
            () -> FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("store_timers.json");

    private Saved saved() {
        if (saved == null) {
            saved = new Saved();
            try {
                Path file = fileLocation.get();
                if (Files.exists(file)) {
                    Saved read = new Gson().fromJson(Files.readString(file), Saved.class);
                    if (read != null && read.tackleAnchor > 0) {
                        if (read.votes == null) read.votes = new java.util.ArrayList<>();
                        saved = read;
                    }
                }
            } catch (Exception | LinkageError e) {
                LoggerHandler.error(e instanceof Exception ex ? ex : new RuntimeException(e));
            }
        }
        return saved;
    }

    private void save() {
        try {
            Path file = fileLocation.get();
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(saved()));
        } catch (Exception | LinkageError e) {
            LoggerHandler.error(e instanceof Exception ex ? ex : new RuntimeException(e));
        }
    }

    /// From chat: the Tackle Shop just restocked, so the next one is 4 hours from now.
    public void onGameMessage(String text) {
        if (text == null) return;
        String plain = text.replaceAll("§.", "");
        if (plain.contains("TACKLE SHOP") && plain.contains("restocked")) {
            long now = System.currentTimeMillis();
            saved().tackleAnchor = now - now % 1000L;
            save();
        } else if (isVoteReward(plain)) {
            addVote(saved().votes, System.currentTimeMillis());
            save();
        }
    }

    /// "VOTE » You received a Rare Bait Box" (sometimes "... and an Epic Chummer"): one vote.
    /// Package-private for tests.
    static boolean isVoteReward(String plain) {
        return VOTE_REWARD.matcher(plain.trim()).find();
    }

    private static final java.util.regex.Pattern VOTE_REWARD =
            java.util.regex.Pattern.compile("^VOTE\\s*[»>]\\s*You received\\b", java.util.regex.Pattern.CASE_INSENSITIVE);

    /// Records a vote and forgets the ones older than 24 hours. Package-private for tests.
    static void addVote(java.util.List<Long> votes, long now) {
        votes.removeIf(t -> now - t >= VOTE_COOLDOWN_MS);
        votes.add(now);
        votes.sort(null);
        while (votes.size() > VOTE_SITES) votes.remove(0);
    }

    /// Votes in the last 24 hours (at most 7). Package-private for tests.
    static int votesIn(java.util.List<Long> votes, long now) {
        int n = 0;
        for (long t : votes) if (now - t < VOTE_COOLDOWN_MS) n++;
        return Math.min(VOTE_SITES, n);
    }

    /// Until your oldest vote of the last 24 hours runs out (you can vote on that site again).
    static long nextVoteIn(java.util.List<Long> votes, long now) {
        long oldest = Long.MAX_VALUE;
        for (long t : votes) if (now - t < VOTE_COOLDOWN_MS) oldest = Math.min(oldest, t);
        return oldest == Long.MAX_VALUE ? 0L : oldest + VOTE_COOLDOWN_MS - now;
    }

    public int votesToday() {
        return votesIn(saved().votes, System.currentTimeMillis());
    }

    public long nextVoteInMs() {
        return nextVoteIn(saved().votes, System.currentTimeMillis());
    }

    /// A screen opened: if it's the Tackle Shop, you've checked it this restock.
    public void onScreenOpened(String title) {
        if (title == null) return;
        String plain = title.replaceAll("§.", "").replaceAll("[\\uE000-\\uF8FF]", "").toLowerCase(java.util.Locale.ROOT);
        if (plain.contains("tackle shop") || plain.contains("tackleshop")) {
            saved().tackleCheckedAt = System.currentTimeMillis();
            save();
        }
    }

    /// True when you've opened the Tackle Shop since it last restocked.
    public boolean tackleShopChecked() {
        return checkedSinceRestock(saved().tackleCheckedAt, System.currentTimeMillis(), saved().tackleAnchor);
    }

    /// Package-private for tests.
    static boolean checkedSinceRestock(long checkedAt, long now, long anchor) {
        long lastRestock = now - (TACKLE_PERIOD_MS - tackleRemaining(now, anchor));
        return checkedAt >= lastRestock && checkedAt <= now;
    }

    public long tackleShopRemainingMs() {
        return tackleRemaining(System.currentTimeMillis(), saved().tackleAnchor);
    }

    public long cosmeticStoreRemainingMs() {
        return cosmeticRemaining(System.currentTimeMillis());
    }

    /// Until the next 4-hourly restock after {@code anchor} (a known restock). Package-private for tests.
    static long tackleRemaining(long now, long anchor) {
        return TACKLE_PERIOD_MS - Math.floorMod(now - anchor, TACKLE_PERIOD_MS);
    }

    /// Until the next 8pm in New York (EST in winter, EDT in summer). Package-private for tests.
    static long cosmeticRemaining(long now) {
        ZonedDateTime here = Instant.ofEpochMilli(now).atZone(NEW_YORK);
        ZonedDateTime next = here.toLocalDate().atTime(COSMETIC_HOUR, 0).atZone(NEW_YORK);
        if (!next.isAfter(here)) next = here.toLocalDate().plusDays(1).atTime(COSMETIC_HOUR, 0).atZone(NEW_YORK);
        return next.toInstant().toEpochMilli() - now;
    }

    /// 3725000 → "1:02:05", 59000 → "0:59".
    public static String clock(long ms) {
        long s = Math.max(0L, ms) / 1000L;
        long h = s / 3600, m = (s / 60) % 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }
}
