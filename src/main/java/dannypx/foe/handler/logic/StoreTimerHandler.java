package dannypx.foe.handler.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dannypx.foe.FishOnMCExtras;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Countdowns for the server's shops: the Tackle Shop restocks every 4 hours (6pm EST is one of
 * them; FOE's timer, re-synced every time "TACKLE SHOP » Tackle items restocked!" shows in chat)
 * and the Cosmetic Store resets every day at midnight UTC (7pm EST, 8pm EDT). Both re-sync
 * from the server's /events answer ("Tackle Shop Restock → 3h 11m 20s"). Also your votes: every
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
    static final long DAY_MS = 24L * 60 * 60 * 1000;
    /// A known Cosmetic Store reset: midnight UTC (7pm EST), every 24 hours from there.
    static final long DEFAULT_COSMETIC_ANCHOR = Instant.parse("2026-11-06T00:00:00Z").toEpochMilli();

    /// Vote sites on the server.
    public static final int VOTE_SITES = 7;
    static final long VOTE_COOLDOWN_MS = 24L * 60 * 60 * 1000;

    private static final class Saved {
        long tackleAnchor = DEFAULT_TACKLE_ANCHOR;
        long cosmeticAnchor = DEFAULT_COSMETIC_ANCHOR;
        /// When you last opened the Tackle Shop.
        long tackleCheckedAt = 0L;
        /// When the global Luck / Scale boosters run out (0 = none).
        long luckBoosterEnds = 0L;
        long scaleBoosterEnds = 0L;
        /// The XP and Money boosters (for the Events panel), and each booster's multiplier.
        long xpBoosterEnds = 0L;
        long moneyBoosterEnds = 0L;
        java.util.Map<String, Double> boosterMultipliers = new java.util.HashMap<>();
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
                        if (read.cosmeticAnchor <= 0) read.cosmeticAnchor = DEFAULT_COSMETIC_ANCHOR;
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
        } else {
            Booster booster = parseBooster(plain);
            if (booster != null) {
                long now = System.currentTimeMillis();
                Saved s = saved();
                switch (booster.kind()) {
                    case "luck" -> s.luckBoosterEnds = boosterEnd(s.luckBoosterEnds, now, booster.ms());
                    case "scale" -> s.scaleBoosterEnds = boosterEnd(s.scaleBoosterEnds, now, booster.ms());
                    case "xp" -> s.xpBoosterEnds = boosterEnd(s.xpBoosterEnds, now, booster.ms());
                    default -> s.moneyBoosterEnds = boosterEnd(s.moneyBoosterEnds, now, booster.ms());
                }
                if (s.boosterMultipliers == null) s.boosterMultipliers = new java.util.HashMap<>();
                if (booster.multiplier() > 0) s.boosterMultipliers.put(booster.kind(), booster.multiplier());
                save();
                // Luck & Scale come from /menu: read them again once the booster has kicked in.
                if (booster.luck() || booster.kind().equals("scale")) SilentMenuHandler.instance().request(BOOSTER_REFRESH_DELAY_MS);
            }
        }
    }

    /// "BOOSTER » makophobia_ has activated a Global Scale Booster » 2x for 60m! (/tipall)":
    /// which kind and how long.
    record Booster(String kind, double multiplier, long ms) {
        /// "luck", "scale", "xp" or "money".
        public boolean luck() {
            return kind.equals("luck");
        }
    }

    /// The global boosters, in the order the Events panel lists them.
    public static final java.util.List<String> BOOSTER_KINDS = java.util.List.of("luck", "scale", "money", "xp");

    static final long BOOSTER_REFRESH_DELAY_MS = 3_000L;

    private static final java.util.regex.Pattern BOOSTER = java.util.regex.Pattern.compile(
            "has activated an? global (luck|scale|xp|money) booster\\b(?:\\s*[»>]?\\s*([\\d.]+)\\s*x)?\\s*for\\s+([^!(]+?)\\s*(?:[!(]|\\.?\\s*$)",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /// Package-private for tests.
    static Booster parseBooster(String plain) {
        java.util.regex.Matcher m = BOOSTER.matcher(plain.trim());
        if (!m.find()) return null;
        long ms = EventTimesHandler.parseDuration(m.group(3));
        if (ms <= 0) return null;
        double multiplier = 0;
        try {
            if (m.group(2) != null) multiplier = Double.parseDouble(m.group(2));
        } catch (NumberFormatException e) {
            multiplier = 0;
        }
        return new Booster(m.group(1).toLowerCase(java.util.Locale.ROOT), multiplier, ms);
    }

    /// A new booster while one is running adds its time on top (they queue up).
    static long boosterEnd(long currentEnd, long now, long ms) {
        return Math.max(currentEnd, now) + ms;
    }

    /// Time left on the global Luck (or Scale) booster, 0 when none is on.
    public long boosterRemainingMs(boolean luck) {
        return boosterRemainingMs(luck ? "luck" : "scale");
    }

    /// Time left on a global booster ("luck", "scale", "xp", "money"), 0 when none is on.
    public long boosterRemainingMs(String kind) {
        Saved s = saved();
        long end = switch (kind) {
            case "luck" -> s.luckBoosterEnds;
            case "scale" -> s.scaleBoosterEnds;
            case "xp" -> s.xpBoosterEnds;
            default -> s.moneyBoosterEnds;
        };
        return Math.max(0L, end - System.currentTimeMillis());
    }

    /// /active says how long a booster has left: set it to exactly that.
    public void syncBooster(String kind, double multiplier, long msLeft) {
        Saved s = saved();
        long end = System.currentTimeMillis() + Math.max(0L, msLeft);
        switch (kind) {
            case "luck" -> s.luckBoosterEnds = end;
            case "scale" -> s.scaleBoosterEnds = end;
            case "xp" -> s.xpBoosterEnds = end;
            default -> s.moneyBoosterEnds = end;
        }
        if (s.boosterMultipliers == null) s.boosterMultipliers = new java.util.HashMap<>();
        if (multiplier > 0) s.boosterMultipliers.put(kind, multiplier);
        save();
    }

    /// /active doesn't list it: that booster isn't on.
    public void clearBooster(String kind) {
        if (boosterRemainingMs(kind) <= 0) return;
        Saved s = saved();
        switch (kind) {
            case "luck" -> s.luckBoosterEnds = 0L;
            case "scale" -> s.scaleBoosterEnds = 0L;
            case "xp" -> s.xpBoosterEnds = 0L;
            default -> s.moneyBoosterEnds = 0L;
        }
        save();
    }

    /// The booster's multiplier (2 for "2x"), 0 when unknown.
    public double boosterMultiplier(String kind) {
        Saved s = saved();
        if (s.boosterMultipliers == null) return 0;
        return s.boosterMultipliers.getOrDefault(kind, 0.0);
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
        return cosmeticRemaining(System.currentTimeMillis(), saved().cosmeticAnchor);
    }

    /// From /events: the Tackle Shop restocks in {@code remainingMs}.
    public void syncTackleShop(long remainingMs) {
        long now = System.currentTimeMillis();
        long next = now + Math.max(0L, remainingMs);
        saved().tackleAnchor = next - next % 1000L;
        save();
    }

    /// From /events: the Cosmetic Store resets in {@code remainingMs}.
    public void syncCosmeticStore(long remainingMs) {
        long now = System.currentTimeMillis();
        long next = now + Math.max(0L, remainingMs);
        saved().cosmeticAnchor = next - next % 1000L;
        save();
    }

    /// Until the next 4-hourly restock after {@code anchor} (a known restock). Package-private for tests.
    static long tackleRemaining(long now, long anchor) {
        return TACKLE_PERIOD_MS - Math.floorMod(now - anchor, TACKLE_PERIOD_MS);
    }

    /// Until the next daily reset after {@code anchor} (a known reset). Package-private for tests.
    static long cosmeticRemaining(long now, long anchor) {
        return DAY_MS - Math.floorMod(now - anchor, DAY_MS);
    }

    /// 3725000 → "1:02:05", 59000 → "0:59".
    public static String clock(long ms) {
        long s = Math.max(0L, ms) / 1000L;
        long h = s / 3600, m = (s / 60) % 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }
}
