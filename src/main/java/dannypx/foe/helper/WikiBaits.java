package dannypx.foe.helper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Every bait and lure as the FishOnMC wiki's Tacklebox page lists it: its name, rarity and picture's file name
 * ("Bonito Live Bait", legendary, "bait_bonito_livebait"), in assets/fishonmcextras/data/baits.txt. The picture's
 * name is what the server's own model for it is called, so FOE+ can show a bait it hasn't seen yet, and colour it.
 */
public final class WikiBaits {
    private WikiBaits() {}

    public record Bait(String name, String rarity, String texture) {}

    private static List<Bait> all = null;

    public static List<Bait> all() {
        if (all != null) return all;
        List<Bait> out = new ArrayList<>();
        try (InputStream in = WikiBaits.class.getResourceAsStream("/assets/fishonmcextras/data/baits.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = r.readLine()) != null; ) {
                    String[] p = line.split("\\|", -1);
                    if (p.length == 3 && !p[0].isBlank()) out.add(new Bait(p[0].trim(), p[1].trim(), p[2].trim()));
                }
            }
        } catch (Exception ignored) {
        }
        all = out;
        return all;
    }

    /// "Bonito Live Bait!" → "bonito live bait".
    static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static final Set<String> LOOSE = Set.of("bait", "live", "cutbait", "of", "the");

    /// The name without the words that only say it's bait ("Cownose Ray Bait" and "Cownose Ray Cutbait" → "cownose ray").
    static String loose(String name) {
        StringBuilder b = new StringBuilder();
        for (String w : key(name).split(" ")) if (!w.isEmpty() && !LOOSE.contains(w)) b.append(b.isEmpty() ? "" : " ").append(w);
        return b.toString();
    }

    /// Package-private for tests: the bait among these that a name means: the same name, else the only one that's the
    /// same once the bait words are dropped; null when none or it's unclear.
    static Bait find(String name, List<Bait> baits) {
        String k = key(name);
        if (k.isEmpty()) return null;
        for (Bait b : baits) if (key(b.name()).equals(k)) return b;
        String l = loose(name);
        Bait found = null;
        for (Bait b : baits) {
            if (!loose(b.name()).equals(l)) continue;
            if (found != null) return null;
            found = b;
        }
        if (found != null) return found;
        return closest(name, baits);
    }

    /// Words the bait sheet spells its own way.
    private static final java.util.Map<String, String> SPELLING = java.util.Map.of(
            "muscles", "mussels", "muscle", "mussel", "manhaden", "menhaden", "fries", "fry");

    private static List<String> words(String name) {
        List<String> out = new ArrayList<>();
        for (String w : key(name).split(" ")) {
            if (w.isEmpty() || w.equals("og") || LOOSE.contains(w)) continue;
            out.add(SPELLING.getOrDefault(w, w));
        }
        return out;
    }

    private static String single(String w) {
        return w.length() > 3 && w.endsWith("s") ? w.substring(0, w.length() - 1) : w;
    }

    private static boolean sameWord(String sheet, String wiki) {
        String a = single(sheet), b = single(wiki);
        return a.equals(b) || (a.length() >= 3 && b.startsWith(a));
    }

    /// Last try, for names the sheet writes its own way: "Ghost Bucktail Jig" for "Bucktail Jig (Ghost)", "Zebra
    /// Muscles", "Leopard Frogs", "OG Menhaden Live". Every word of it must be in the bait's name or its picture's
    /// name ("Pink Roostertail": lure_roostertail_pinkfluorescent); of those, the one with the fewest other words,
    /// else the first listed.
    static Bait closest(String name, List<Bait> baits) {
        List<String> want = words(name);
        if (want.isEmpty()) return null;
        Bait best = null;
        int bestExtra = Integer.MAX_VALUE;
        for (Bait b : baits) {
            List<String> have = words(b.name());
            for (String t : b.texture().split("_")) if (!t.isEmpty() && !t.equals("bait") && !t.equals("lure")) have.add(t);
            boolean all = true;
            for (String w : want) {
                boolean hit = false;
                for (String h : have) if (sameWord(w, h)) { hit = true; break; }
                if (!hit) { all = false; break; }
            }
            if (!all) continue;
            int extra = Math.max(0, words(b.name()).size() - want.size());
            if (extra < bestExtra) { best = b; bestExtra = extra; }
        }
        return best;
    }

    public static Bait find(String name) {
        return find(name, all());
    }
}
