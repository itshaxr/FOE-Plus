package dannypx.foe.handler.store;

import dannypx.foe.FishOnMCExtras;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Your own sounds: .ogg files you put in config/fishonmcextras/sounds show in the Sounds menu (under "Your sound
 * files", each with a clean name made from its file name) and play like any other sound. Nothing is downloaded or
 * shipped with FOE+: they're your files, on your computer.
 */
public final class FileSounds {
    private FileSounds() {}

    /// A sound file: its id in the Sounds menu ("foeplus:file/mario_coin"), its clean name, the file.
    public record Entry(String id, String name, Path file) {}

    public static final String PREFIX = "foeplus:file/";

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("sounds");
    }

    private static Map<String, Entry> cache = new LinkedHashMap<>();
    private static long readAt = 0L;

    /// The sound files in the folder (read again at most every 2 seconds), by their clean names.
    public static synchronized List<Entry> all() {
        long now = System.currentTimeMillis();
        if (now - readAt > 2_000L) {
            readAt = now;
            Map<String, Entry> found = new LinkedHashMap<>();
            try {
                Files.createDirectories(folder());
                try (var files = Files.list(folder())) {
                    files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg") && Files.isRegularFile(p))
                            .sorted((a, b) -> cleanName(a.getFileName().toString()).compareToIgnoreCase(cleanName(b.getFileName().toString())))
                            .forEach(p -> {
                                String file = p.getFileName().toString();
                                String key = idOf(file);
                                if (!found.containsKey(key)) found.put(key, new Entry(key, cleanName(file), p));
                            });
                }
            } catch (Exception e) {
                dannypx.foe.handler.logic.LoggerHandler.error(e);
            }
            cache = found;
        }
        return new ArrayList<>(cache.values());
    }

    /// The sound file with this id, or null.
    public static Entry byId(String id) {
        if (id == null || !id.startsWith(PREFIX)) return null;
        for (Entry e : all()) if (e.id().equals(id)) return e;
        return null;
    }

    /// Package-private for tests: a file's id ("Mario Coin.ogg" → "foeplus:file/mario_coin").
    static String idOf(String file) {
        String stem = file.replaceAll("(?i)\\.ogg$", "");
        String key = stem.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return PREFIX + (key.isEmpty() ? "sound" : key);
    }

    /// Names for well-known sound files, nicer than the file name says.
    private static final Map<String, String> KNOWN = Map.ofEntries(
            Map.entry("aahyeahyea", "Aah Yeah Yeah"), Map.entry("acexclaim", "Animal Crossing Exclaim"),
            Map.entry("crabrave", "Crab Rave"), Map.entry("enemy encounter undertale", "Undertale Encounter"),
            Map.entry("gaster vanish", "Gaster Vanish"), Map.entry("golden fish sound", "Golden Fish"),
            Map.entry("kirby extra life", "Kirby Extra Life"), Map.entry("kirbydeath", "Kirby Death"),
            Map.entry("kirbyssusleeplouder", "Kirby Sleep"), Map.entry("letsgogambling", "Let's Go Gambling"),
            Map.entry("level start", "Level Start"), Map.entry("mario1up", "Mario 1-Up"), Map.entry("mariocoin", "Mario Coin"),
            Map.entry("mariogoombajump", "Mario Goomba Stomp"), Map.entry("mariojump", "Mario Jump"),
            Map.entry("mariopause", "Mario Pause"), Map.entry("mariopowerup", "Mario Power-Up"),
            Map.entry("new night squid sound", "Night Squid"), Map.entry("pikminthrow", "Pikmin Throw"),
            Map.entry("sklevelstart", "Shovel Knight Level Start"), Map.entry("skvictory", "Shovel Knight Victory"),
            Map.entry("spider man 2099 miguel ohara sound effect", "Spider-Man 2099"), Map.entry("sqek", "Squeak"),
            Map.entry("undertale damage taken", "Undertale Damage"), Map.entry("undertale sfx rare to find", "Undertale Rare Find"),
            Map.entry("undertale sound effect you win", "Undertale You Win"), Map.entry("undertalesave", "Undertale Save"),
            Map.entry("undertalevictory", "Undertale Victory"), Map.entry("victoryff", "Final Fantasy Victory"),
            Map.entry("vulkandies", "Vulkan Dies"), Map.entry("yoshimount", "Yoshi Mount"), Map.entry("monee", "Money"),
            Map.entry("out", "Mystery Sound"));

    /// Package-private for tests: a clean name from a file name ("kirby_extra_life.ogg" → "Kirby Extra Life",
    /// "undertale-damage-taken_0iH1Nxl.ogg" → "Undertale Damage", "MarioCoin.ogg" → "Mario Coin").
    static String cleanName(String file) {
        String stem = file.replaceAll("(?i)\\.ogg$", "");
        // what download sites add: "(1)", "(mp3cut.net)", a random code after the last _ ("_0iH1Nxl")
        stem = stem.replaceAll("\\s*\\([^)]*\\)", "").trim();
        java.util.regex.Matcher code = java.util.regex.Pattern.compile("_([A-Za-z0-9]{5,10})$").matcher(stem);
        if (code.find()) {
            String c = code.group(1);
            boolean mixed = c.matches(".*\\d.*") && c.matches(".*[A-Za-z].*") || !c.equals(c.toLowerCase(Locale.ROOT)) && !c.equals(c.toUpperCase(Locale.ROOT));
            if (mixed) stem = stem.substring(0, code.start());
        }
        String plain = stem.toLowerCase(Locale.ROOT).replaceAll("[_\\-]+", " ").replaceAll("\\s+", " ").trim();
        String known = KNOWN.get(plain);
        if (known != null) return known;
        String spaced = stem.replaceAll("[_\\-]+", " ")
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("([A-Za-z])(\\d)", "$1 $2")
                .replaceAll("\\s+", " ").trim();
        if (spaced.isEmpty()) return "Sound";
        StringBuilder b = new StringBuilder();
        for (String w : spaced.split(" ")) {
            if (w.isEmpty()) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.length() > 1 && w.equals(w.toUpperCase(Locale.ROOT)) && w.length() > 3
                    ? w.substring(1).toLowerCase(Locale.ROOT) : w.substring(1));
        }
        return b.toString();
    }

    /// A sound file played once, at no place (like the menu's sounds), streamed from the file.
    public static SoundInstance instance(Entry entry, SoundSource source, float volume, float pitch) {
        return new Instance(entry, source, volume, pitch);
    }

    private static final class Instance extends AbstractSoundInstance {
        private final Entry entry;

        Instance(Entry entry, SoundSource source, float volume, float pitch) {
            super(Identifier.parse(entry.id()), source, SoundInstance.createUnseededRandom());
            this.entry = entry;
            this.volume = volume;
            this.pitch = pitch;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            // not a sound the game knows: one made here, streamed (so its audio comes from getAudioStream)
            this.sound = new Sound(Identifier.parse(entry.id()), ConstantFloat.of(1f), ConstantFloat.of(1f), 1,
                    Sound.Type.FILE, true, false, 16);
            WeighedSoundEvents events = new WeighedSoundEvents(Identifier.parse(entry.id()), null);
            events.addSound(this.sound);
            return events;
        }

        @Override
        public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, Identifier id, boolean repeatInstantly) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    InputStream in = new BufferedInputStream(Files.newInputStream(entry.file()));
                    return (AudioStream) new net.minecraft.client.sounds.JOrbisAudioStream(in);
                } catch (Exception e) {
                    throw new java.util.concurrent.CompletionException(e);
                }
            }, net.minecraft.util.Util.backgroundExecutor());
        }
    }
}
