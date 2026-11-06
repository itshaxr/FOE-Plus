package dannypx.foe.handler.logic;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * This session's catches, for the Session Stats panel: the same things All-Time Stats counts,
 * from the same catch hooks (so imports, edits and the server's stats sync never count here),
 * kept in memory and cleared when you log out.
 */
public final class SessionStatsHandler {
    private static SessionStatsHandler INSTANCE;

    public static SessionStatsHandler instance() {
        if (INSTANCE == null) INSTANCE = new SessionStatsHandler();
        return INSTANCE;
    }

    private int fish = 0, pets = 0, shards = 0, quests = 0;
    private final Map<String, Integer> rarities = new HashMap<>();
    private final Map<String, Integer> variants = new HashMap<>();
    private final Map<String, Integer> sizes = new HashMap<>();
    private final Map<String, Integer> rare = new HashMap<>();
    /// Fish count (this session) at the last catch of each thing, for "fish since".
    private final Map<String, Integer> lastAt = new HashMap<>();
    /// Catches during each rare pull's event this session (Blood Moon for capsules, thunderstorm
    /// or supercell for bottles), and that count at the last pull: their "since" only counts
    /// those catches, like All-Time Stats.
    private final Map<String, Integer> eventFish = new HashMap<>();
    private final Map<String, Integer> eventFishAtLast = new HashMap<>();
    /// FOE+'s fishing timer when the session began (-1 = not yet read).
    private long activeAtStart = -1L;

    private SessionStatsHandler() {}

    /// Logged out: start over.
    public void reset() {
        fish = pets = shards = quests = 0;
        rarities.clear();
        variants.clear();
        sizes.clear();
        rare.clear();
        lastAt.clear();
        eventFish.clear();
        eventFishAtLast.clear();
        activeAtStart = -1L;
    }

    public void onFish(String rarity, String variant, String size) {
        onFish(rarity, variant, size, RareCatchHandler::eventOn);
    }

    /// Package-private for tests: {@code eventOn} says whether a rare pull's event is on now.
    void onFish(String rarity, String variant, String size, java.util.function.Predicate<String> eventOn) {
        for (String key : RareCatchHandler.EVENT_PULLS) {
            if (eventOn.test(key)) eventFish.merge(key, 1, Integer::sum);
        }
        fish++;
        add(rarities, key(rarity), "rarity:");
        add(variants, key(variant), "variant:");
        add(sizes, key(size), "size:");
    }

    public void onPet() {
        pets++;
        lastAt.put("pet", fish);
    }

    public void onItem(String type, int count) {
        if (type == null || !type.toLowerCase(Locale.ROOT).contains("shard")) return;
        shards += Math.max(1, count);
        lastAt.put("shard", fish);
    }

    public void onRare(String key, int count) {
        rare.merge(key, Math.max(1, count), Integer::sum);
        lastAt.put("rare:" + key, fish);
        eventFishAtLast.put(key, eventFish.getOrDefault(key, 0));
    }

    public void onQuest() {
        quests++;
    }

    private void add(Map<String, Integer> map, String k, String prefix) {
        if (k.isEmpty()) return;
        map.merge(k, 1, Integer::sum);
        lastAt.put(prefix + k, fish);
    }

    private static String key(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }

    public int fish() { return fish; }
    public int pets() { return pets; }
    public int shards() { return shards; }
    public int quests() { return quests; }
    public int rarity(String r) { return rarities.getOrDefault(key(r), 0); }
    public int variant(String v) { return variants.getOrDefault(key(v), 0); }
    public int size(String s) { return sizes.getOrDefault(key(s), 0); }
    public int rare(String k) { return rare.getOrDefault(k, 0); }

    /// Fish caught this session since the last pet / shard / rare pull / variant ("variant:albino")
    /// or size ("size:gigantic"); all of this session's fish when there's been none yet.
    public int since(String what) {
        if (what.startsWith("rare:") && RareCatchHandler.isEventPull(what.substring(5))) {
            String key = what.substring(5);
            return eventFish.getOrDefault(key, 0) - eventFishAtLast.getOrDefault(key, 0);
        }
        Integer at = lastAt.get(what);
        return at == null ? fish : fish - at;
    }

    /// How long you've fished this session (FOE+'s fishing timer since you logged in).
    public long fishTimeMs() {
        long active = FishSessionHandler.instance().getActiveTimeMs();
        if (activeAtStart < 0 || active < activeAtStart) activeAtStart = active; // start, or Reset Fish Timer
        return Math.max(0L, active - activeAtStart);
    }

    public double fishPerHour() {
        long ms = fishTimeMs();
        return ms <= 0 ? 0.0 : fish / (ms / 3_600_000.0);
    }

    /// Note FOE+'s fishing timer as the session's start (first tick after logging in).
    public void tick() {
        if (activeAtStart < 0) activeAtStart = FishSessionHandler.instance().getActiveTimeMs();
    }
}
