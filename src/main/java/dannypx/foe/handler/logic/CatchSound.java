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
        final ArrayDeque<Set<String>> rounds = new ArrayDeque<>();
        final List<Long> pending = new ArrayList<>();
        String learned;
        /// Every sound that plays with nearly every one (a bite can bring the bobber's splash and a sound of the
        /// server's own together).
        Set<String> all = new HashSet<>();

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
                Set<String> round = new HashSet<>();
                for (Heard h : heard) if (h.at() >= at - before && h.at() <= at + after) round.add(h.id());
                rounds.addLast(round);
                while (rounds.size() > KEEP) rounds.pollFirst();
                String found = pick(rounds, catchSound);
                if (found != null) learned = found;
                Set<String> every = pickAll(rounds, catchSound);
                if (!every.isEmpty()) all = every;
            }
        }
    }

    static final int KEEP = 8, NEED = 3;
    static final Learner CATCH = new Learner(2500L, 1500L, true, "minecraft:success_sound");
    static final Learner BITE = new Learner(600L, 600L, false, null);

    private static long fishingUntil = 0L, biteAt = 0L;
    /// True while FOE+ plays its own sound (not one to listen to).
    public static boolean own = false;

    /// The sound FOE+ takes for the server's catch sound, or null.
    public static String learned() {
        return CATCH.learned;
    }

    /// The sound FOE+ takes for the server's bite sound, or null (not heard enough bites yet).
    public static Set<String> learnedBite() {
        return BITE.all;
    }

    /// Package-private for tests: the sound among these rounds' sounds that plays in at least 3 of them and 3 in 4 of
    /// them, the most often; null when none does yet.
    static String pick(java.util.Collection<Set<String>> rounds, boolean catchSound) {
        if (rounds.size() < NEED) return null;
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
        if (best == null || bestN < NEED || bestN * 4 < rounds.size() * 3) return null;
        return best;
    }

    /// Package-private for tests: every sound that passes {@link #pick}'s test.
    static Set<String> pickAll(java.util.Collection<Set<String>> rounds, boolean catchSound) {
        Set<String> out = new HashSet<>();
        if (rounds.size() < NEED) return out;
        Map<String, Integer> count = new HashMap<>();
        for (Set<String> r : rounds) for (String id : r) if (!excluded(id, catchSound)) count.merge(id, 1, Integer::sum);
        for (var e : count.entrySet()) if (e.getValue() >= NEED && e.getValue() * 4 >= rounds.size() * 3) out.add(e.getKey());
        return out;
    }

    static String pick(java.util.Collection<Set<String>> rounds) {
        return pick(rounds, true);
    }

    static boolean excluded(String id, boolean catchSound) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
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
        long now = System.currentTimeMillis();
        heard.addLast(new Heard(id, now, sound));
        while (!heard.isEmpty() && now - heard.peekFirst().at() > 6000L) heard.pollFirst();
        // the server's bite sound, just after a BITE!
        if (BITE.all.contains(id) && now - biteAt <= BITE.after && replacingBite()) return true;
        if (CATCH.learned == null || !CATCH.learned.equals(id) || !fishing(now)) return false;
        NotifySounds.Setting s = NotifySounds.get(ID);
        if (s.on && (s.sound == null || s.sound.isBlank())) return false;   // the server's own sound
        if (s.on) {
            own = true;
            try { NotifySounds.play(s, false); } finally { own = false; }
        }
        return true;
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
        long now = System.currentTimeMillis();
        biteAt = now;
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
    }
}
