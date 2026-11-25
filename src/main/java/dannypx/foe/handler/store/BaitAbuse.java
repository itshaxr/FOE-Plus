package dannypx.foe.handler.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dannypx.foe.FishOnMCExtras;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bait abuse (O → Tools → Bait Abuse): the best baits at each place for each goal (a rarity, pet leveling), in order,
 * sometimes only with a condition ("1k+ Luck", "Booster") and sometimes tied. It starts from FOE+'s own copy of the
 * community's sheet (data/bait_abuse.txt, read once) and from then on it's yours: every line can be changed, added or
 * taken away in game, and is saved in config/fishonmcextras/bait_abuse.json.
 */
public final class BaitAbuse {
    private BaitAbuse() {}

    /// One bait: where, for what, when, its place in the order, and whether it's tied with the others at that place.
    public static final class Entry {
        public String place = "";
        public String goal = "";
        public String condition = "";
        public int priority = 1;
        public String bait = "";
        public boolean tied = false;

        public Entry() {}

        public Entry(String place, String goal, String condition, int priority, String bait, boolean tied) {
            this.place = place;
            this.goal = goal;
            this.condition = condition;
            this.priority = priority;
            this.bait = bait;
            this.tied = tied;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static List<Entry> entries;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("bait_abuse.json");
    }

    /// Every entry, in the order they're kept (by place as first added, then goal, then priority).
    public static synchronized List<Entry> all() {
        load();
        return entries;
    }

    /// The places, in their order.
    public static synchronized List<String> places() {
        List<String> out = new ArrayList<>();
        for (Entry e : all()) if (out.stream().noneMatch(p -> p.equalsIgnoreCase(e.place))) out.add(e.place);
        return out;
    }

    /// The goals at a place, in their order (Rare, Epic, Legendary, Mythical first, then the rest as added).
    public static synchronized List<String> goals(String place) {
        List<String> out = new ArrayList<>();
        for (Entry e : all()) {
            if (!e.place.equalsIgnoreCase(place)) continue;
            if (out.stream().noneMatch(g -> g.equalsIgnoreCase(e.goal))) out.add(e.goal);
        }
        out.sort(Comparator.comparingInt(BaitAbuse::goalOrder));
        return out;
    }

    static int goalOrder(String goal) {
        String g = goal == null ? "" : goal.toLowerCase(Locale.ROOT);
        if (g.startsWith("common")) return 0;
        if (g.startsWith("rare")) return 1;
        if (g.startsWith("epic")) return 2;
        if (g.startsWith("legend")) return 3;
        if (g.startsWith("myth")) return 4;
        return 5;
    }

    /// The baits for a goal at a place, best first.
    public static synchronized List<Entry> of(String place, String goal) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : all()) if (e.place.equalsIgnoreCase(place) && e.goal.equalsIgnoreCase(goal)) out.add(e);
        out.sort(Comparator.comparingInt(e -> e.priority));
        return out;
    }

    public static synchronized void add(Entry e) {
        load();
        // after the last one of its place, so places keep their order
        int at = -1;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).place.equalsIgnoreCase(e.place)) at = i;
        if (at < 0) entries.add(e);
        else entries.add(at + 1, e);
        save();
    }

    public static synchronized void remove(Entry e) {
        load();
        entries.remove(e);
        save();
    }

    /// Back to FOE+'s own copy of the sheet.
    public static synchronized void reset() {
        entries = defaults();
        save();
    }

    public static synchronized void save() {
        if (entries == null) return;
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(entries));
        } catch (Exception | LinkageError ignored) {
            // kept for this session
        }
    }

    private static void load() {
        if (entries != null) return;
        try {
            Path f = file();
            if (Files.exists(f)) {
                List<Entry> read = GSON.fromJson(Files.readString(f), new TypeToken<List<Entry>>() {}.getType());
                if (read != null) {
                    read.removeIf(e -> e == null || e.place == null || e.goal == null || e.bait == null);
                    for (Entry e : read) if (e.condition == null) e.condition = "";
                    entries = new ArrayList<>(read);
                    return;
                }
            }
        } catch (Exception | LinkageError ignored) {
            // a broken file: FOE+'s copy
        }
        entries = defaults();
    }

    /// FOE+'s copy of the sheet. Package-private for tests.
    static List<Entry> defaults() {
        List<Entry> out = new ArrayList<>();
        try (InputStream in = BaitAbuse.class.getResourceAsStream("/assets/fishonmcextras/data/bait_abuse.txt")) {
            if (in == null) return out;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                Entry e = parse(line);
                if (e != null) out.add(e);
            }
        } catch (Exception ignored) {
            // none then
        }
        return out;
    }

    /// One line of the file: place|goal|condition|priority|bait|tied. Package-private for tests.
    static Entry parse(String line) {
        if (line == null || line.isBlank() || line.startsWith("#")) return null;
        String[] p = line.split("\\|", -1);
        if (p.length < 5 || p[0].isBlank() || p[4].isBlank()) return null;
        int priority;
        try {
            priority = Integer.parseInt(p[3].trim());
        } catch (NumberFormatException e) {
            priority = 1;
        }
        return new Entry(p[0].trim(), p[1].trim(), p[2].trim(), priority, p[4].trim(), p.length > 5 && p[5].trim().equalsIgnoreCase("Y"));
    }

    /// Package-private for tests: a map of place → goal → baits, best first.
    static Map<String, Map<String, List<Entry>>> grouped() {
        Map<String, Map<String, List<Entry>>> out = new LinkedHashMap<>();
        for (String place : places()) {
            Map<String, List<Entry>> goals = new LinkedHashMap<>();
            for (String goal : goals(place)) goals.put(goal, of(place, goal));
            out.put(place, goals);
        }
        return out;
    }
}
