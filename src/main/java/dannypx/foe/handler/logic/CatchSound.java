package dannypx.foe.handler.logic;

import dannypx.foe.handler.store.NotifySounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The server's own sounds for a catch and for a bite (BITE!), so the ones you set in O → Tools → Sounds replace them
 * instead of playing over them.
 * <ul>
 * <li>Catch ("Fish caught (server's sound)"): the server pack's "success_sound". On plays it (or the sound you pick
 * instead), Off mutes it. Muted only while you fish.</li>
 * <li>Bite ("Fish bite (BITE!)"): while that's on, the server's own bite sound is stopped and yours plays alone.</li>
 * </ul>
 * In case the server changes its sounds, FOE+ also listens: a sound that plays with nearly every catch (or bite), and
 * isn't the bobber's cast or reel, is taken for that one.
 */
public final class CatchSound {
    private CatchSound() {}

    public static final String ID = "server_catch";
    public static final String BITE_ID = "fish_bite";

    /// Sounds that play anyway, or all around you: never taken for the catch or bite sound.
    static final String[] NOT_IT = {"entity.fishing_bobber.retrieve", "entity.fishing_bobber.throw", "block.", "ambient.", "weather.",
            "music", "entity.player.splash", "entity.generic.splash", "entity.generic.swim", "entity.player.swim", "item.armor", "ui.",
            "entity.item.pickup", "ambience"};
    /// For a catch the bobber's splash doesn't count either (it can splash any time); for a bite it may be the one.
    static final String[] NOT_CATCH = {"entity.fishing_bobber"};

    private record Heard(String id, long at, SoundInstance sound) {}

    private static final ArrayDeque<Heard> heard = new ArrayDeque<>();

    /// One sound to learn: the sounds heard round each of the last times it happened, and the one found.
    static final class Learner {
        final long before, after;
        final boolean catchSound;
        /// How many times it must have been heard with it (and 3 in 4 of the times) to be taken for it.
        int need = NEED;
        /// A shard's sound: what plays with every catch isn't it.
        boolean shard = false;
        final ArrayDeque<Set<String>> rounds = new ArrayDeque<>();
        final List<Long> pending = new ArrayList<>();
        String learned;
        /// Every sound that plays with nearly every one (a bite can bring the bobber's splash and a sound of the
        /// server's own together).
        Set<String> all = new HashSet<>();
        /// Sounds that repeat round a bite: never taken for it.
        final Set<String> noisy = new HashSet<>();

        Learner(long before, long after, boolean catchSound, String start) {
            this.before = before;
            this.after = after;
            this.catchSound = catchSound;
            this.learned = start;
        }

        void tick(long now) {
            for (var it = pending.iterator(); it.hasNext(); ) {
                long at = it.next();
                if (now < at + after) continue;
                it.remove();
                Map<String, Integer> times = new HashMap<>();
                for (Heard h : heard) if (h.at() >= at - before && h.at() <= at + after) times.merge(h.id(), 1, Integer::sum);
                // a sound heard again and again round one (the fish's splashes as it swims in, the reel's clicks while
                // you reel it in) is never the bite's or the catch's own sound, which plays once
                int often = catchSound ? 3 : 4;
                for (var e : times.entrySet()) if (e.getValue() >= often) noisy.add(e.getKey());
                Set<String> round = new HashSet<>(times.keySet());
                round.removeAll(noisy);
                if (shard) {
                    round.removeAll(CATCH.all);
                    if (CATCH.learned != null) round.remove(CATCH.learned);
                    // a sound that plays with ordinary catches too is never a shard's own
                    round.removeAll(commonWithCatches());
                }
                rounds.addLast(round);
                // in the log, so a bite sound FOE+ doesn't catch can be seen
                if (!catchSound) LoggerHandler.info("Bite sound: heard around this bite: " + new java.util.TreeSet<>(round)
                        + " (taken for the server's: " + new java.util.TreeSet<>(all) + ")");
                while (rounds.size() > KEEP) rounds.pollFirst();
                String found = pick(rounds, catchSound, need);
                // the server pack's own catch sound, while it's still heard with the catches, stays the one
                boolean keepSuccess = this == CATCH && SUCCESS.equals(learned)
                        && rounds.stream().filter(r -> r.contains(SUCCESS)).count() * 2 >= rounds.size();
                if (found != null && !keepSuccess && !found.equals(learned)) {
                    learned = found;
                    LoggerHandler.info((shard ? "Shard" : "Catch") + " sound: the server's is " + found);
                }
                if (learned != null && noisy.contains(learned)) learned = this == CATCH ? SUCCESS : null;
                if (shard && learned != null && commonWithCatches().contains(learned)) {
                    LoggerHandler.info("Shard sound: " + learned + " plays with ordinary catches too, so it isn't the shard's");
                    learned = null;
                }
                Set<String> every = pickAll(rounds, catchSound);
                if (!catchSound) {
                    every.removeAll(noisy);
                    // one taken before (or saved) that turns out to repeat: dropped
                    if (all.removeAll(noisy)) {
                        saveBite(all);
                        LoggerHandler.info("Bite sound: no longer taken for the server's (it repeats): " + new java.util.TreeSet<>(noisy));
                    }
                }
                if (!every.isEmpty() && !every.equals(all)) {
                    // a sound once learned stays (one bite without it doesn't let it through again)
                    if (!catchSound) every.addAll(all);
                    if (!every.equals(all)) {
                        all = every;
                        if (!catchSound) {
                            saveBite(every);
                            LoggerHandler.info("Bite sound: the server's bite sound is " + new java.util.TreeSet<>(every));
                        }
                    }
                }
            }
        }
    }

    static final int KEEP = 8, NEED = 3;
    static final String SUCCESS = "minecraft:success_sound";
    static final Learner CATCH = new Learner(2500L, 1500L, true, SUCCESS);
    /// The server's sound for a climate shard (it comes with the shard's chat line).
    static final Learner SHARD = new Learner(2000L, 1500L, true, null);
    static {
        SHARD.shard = true;
        SHARD.need = 2;
    }
    public static final String SHARD_ID = "shard_caught";

    /// Package-private for tests: the sounds heard round at least 1 in 4 of your recent catches (once 4 have been
    /// heard): those play with ordinary fish, so none of them is a shard's own sound.
    static Set<String> commonWithCatches() {
        Set<String> out = new HashSet<>();
        if (CATCH.rounds.size() < 4) return out;
        Map<String, Integer> count = new HashMap<>();
        for (Set<String> r : CATCH.rounds) for (String id : r) count.merge(id, 1, Integer::sum);
        for (var e : count.entrySet()) if (e.getValue() * 4 >= CATCH.rounds.size()) out.add(e.getKey());
        return out;
    }
    /// The server's bite sound can come well before the BITE! title (the title comes a moment later), or a little after
    /// it (a far bobber's sound comes late).
    static final Learner BITE = new Learner(1500L, 1200L, false, null);

    private static long fishingUntil = 0L, biteAt = 0L;
    /// The bobber that has had its bite (its BITE! came): until it's reeled in, the reeling's own sounds play.
    private static int bitHook = -1;
    private static long loggedAt = 0L;
    /// True while FOE+ plays its own sound (not one to listen to).
    public static boolean own = false;

    /// The sound FOE+ takes for the server's catch sound, or null.
    public static String learned() {
        return CATCH.learned;
    }

    /// Where the bite sounds learned are kept, so yours replaces the server's from the first bite next time too.
    private static java.nio.file.Path biteFile() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve(dannypx.foe.FishOnMCExtras.MOD_ID).resolve("bite_sound.txt");
    }

    static void saveBite(Set<String> ids) {
        try {
            java.nio.file.Files.createDirectories(biteFile().getParent());
            java.nio.file.Files.writeString(biteFile(), String.join("\n", new java.util.TreeSet<>(ids)));
        } catch (Exception | LinkageError e) {
            // learned again next time
        }
    }

    private static boolean biteLoaded = false;

    private static void loadBite() {
        if (biteLoaded) return;
        biteLoaded = true;
        try {
            if (!java.nio.file.Files.exists(biteFile())) return;
            Set<String> ids = new HashSet<>();
            for (String l : java.nio.file.Files.readAllLines(biteFile())) if (!l.isBlank()) ids.add(l.trim());
            if (BITE.all.isEmpty()) BITE.all = ids;
        } catch (Exception | LinkageError e) {
            // learned again
        }
    }

    /// The sound FOE+ takes for the server's bite sound, or null (not heard enough bites yet).
    public static Set<String> learnedBite() {
        return BITE.all;
    }

    /// Package-private for tests: the sound among these rounds' sounds that plays in at least 3 of them and 3 in 4 of
    /// them, the most often; null when none does yet.
    static String pick(java.util.Collection<Set<String>> rounds, boolean catchSound) {
        return pick(rounds, catchSound, NEED);
    }

    static String pick(java.util.Collection<Set<String>> rounds, boolean catchSound, int need) {
        if (rounds.size() < need) return null;
        Map<String, Integer> count = new HashMap<>();
        for (Set<String> r : rounds) for (String id : r) if (!excluded(id, catchSound)) count.merge(id, 1, Integer::sum);
        String best = null;
        int bestN = 0;
        for (var e : count.entrySet()) {
            if (e.getValue() > bestN || (e.getValue() == bestN && best != null && e.getKey().compareTo(best) < 0)) {
                best = e.getKey();
                bestN = e.getValue();
            }
        }
        if (best == null || bestN < need || bestN * 4 < rounds.size() * 3) return null;
        return best;
    }

    /// Package-private for tests: every sound that passes {@link #pick}'s test.
    static Set<String> pickAll(java.util.Collection<Set<String>> rounds, boolean catchSound) {
        Set<String> out = new HashSet<>();
        // a bite's sound is learned from 2 bites (each one missed is your sound and the server's both playing)
        int need = catchSound ? NEED : 2;
        if (rounds.size() < need) return out;
        Map<String, Integer> count = new HashMap<>();
        for (Set<String> r : rounds) for (String id : r) if (!excluded(id, catchSound)) count.merge(id, 1, Integer::sum);
        for (var e : count.entrySet()) if (e.getValue() >= need && e.getValue() * 4 >= rounds.size() * 3) out.add(e.getKey());
        return out;
    }

    static String pick(java.util.Collection<Set<String>> rounds) {
        return pick(rounds, true);
    }

    /// What a server may play for a bite that the list above would otherwise pass over (a note block's ding, a splash).
    static final String[] BITE_MAY_BE = {"block.note_block", "entity.player.splash", "entity.generic.splash", "entity.experience_orb", "entity.player.levelup"};

    static boolean excluded(String id, boolean catchSound) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (!catchSound) for (String n : BITE_MAY_BE) if (path.startsWith(n)) return false;
        for (String n : NOT_IT) if (path.startsWith(n)) return true;
        if (catchSound) for (String n : NOT_CATCH) if (path.startsWith(n)) return true;
        return false;
    }

    /// The bite sound is replaced while "Fish bite (BITE!)" is on (with your sound, or none).
    private static boolean replacingBite() {
        try {
            return NotifySounds.get(BITE_ID).on;
        } catch (Throwable t) {
            return false;
        }
    }

    /// A sound is about to play: true to stop it.
    public static boolean onPlay(SoundInstance sound, String id) {
        if (own || id == null) return false;
        loadBite();
        long now = System.currentTimeMillis();
        heard.addLast(new Heard(id, now, sound));
        while (!heard.isEmpty() && now - heard.peekFirst().at() > 6000L) heard.pollFirst();
        // the server's bite sound: never heard while yours is on. It can come before the BITE! title, so it's stopped
        // whenever your line is out and waiting for a bite, and just after a BITE! (yours plays with the title)
        if (BITE.all.contains(id) && replacingBite()) {
            if (waitingForBite() || now - biteAt <= BITE.after) {
                if (now - loggedAt > 2000L) {
                    loggedAt = now;
                    LoggerHandler.info("Bite sound: the server's " + id + " muted (yours plays instead)");
                }
                return true;
            }
        }
        if (!fishing(now)) return false;
        // the server's shard sound: muted when you've picked your own (it plays with the shard's chat line) or turned
        // it off; your sound is never played from here, only from the shard's line, so it can't go off with other fish
        if (SHARD.learned != null && SHARD.learned.equals(id) && !commonWithCatches().contains(id)) {
            NotifySounds.Setting s = NotifySounds.get(SHARD_ID);
            return !s.on || (s.sound != null && !s.sound.isBlank());
        }
        // the server's catch sound is left alone for now ("Fish caught" is out of the Sounds menu, to come back later);
        // it's still listened for, so a shard's own sound can be told from it
        return false;
    }

    private static long shardPlayedAt = 0L;

    /// A shard was caught (its chat line came): your shard sound plays now (once), and FOE+ listens for the server's.
    public static void onShard() {
        long now = System.currentTimeMillis();
        SHARD.pending.add(now);
        NotifySounds.Setting s = NotifySounds.get(SHARD_ID);
        if (s.on && s.sound != null && !s.sound.isBlank() && now - shardPlayedAt > 1500L) {
            shardPlayedAt = now;
            own = true;
            try { NotifySounds.play(s, false); } finally { own = false; }
        }
    }

    /// Your line is out and hasn't had its bite yet.
    private static boolean waitingForBite() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.fishing != null && mc.player.fishing.getId() != bitHook;
    }

    private static boolean fishing(long now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.fishing != null) fishingUntil = now + 3000L;
        return now <= fishingUntil;
    }

    /// A fish was caught (found in your inventory).
    public static void onCatch() {
        CATCH.pending.add(System.currentTimeMillis());
    }

    /// BITE! came up (call before FOE+'s own bite sound plays): the server's bite sound that has just started stops.
    public static void onBite() {
        loadBite();
        long now = System.currentTimeMillis();
        biteAt = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.fishing != null) bitHook = mc.player.fishing.getId();
        BITE.pending.add(now);
        if (BITE.all.isEmpty() || !replacingBite()) return;
        var sm = Minecraft.getInstance().getSoundManager();
        for (Heard h : heard) if (now - h.at() <= BITE.before && BITE.all.contains(h.id()) && h.sound() != null) sm.stop(h.sound());
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        fishing(now);
        CATCH.tick(now);
        BITE.tick(now);
        SHARD.tick(now);
    }
}
