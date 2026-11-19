package dannypx.foe.handler.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dannypx.foe.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/// Every sound FOE+ plays, each with its own on/off, sound (any game or resource-pack sound), volume, pitch and
/// which of Minecraft's volume sliders it plays under; and your own sound triggers (a sound when a chat line
/// has some text). Set in O → Customize → Sounds. Saved in config/fishonmcextras/sounds.json.
///
/// The sound picker's lists and the idea of a sound per notification come from ChatNotify by TerminalMC
/// (https://github.com/TerminalMC/ChatNotify, Apache-2.0).
public final class NotifySounds {
    private NotifySounds() {}

    public enum Group {
        EVENTS("Event alerts"), SERVER("Server events"), CATCHES("Catches"), SOCIAL("Social & chat"), WARNINGS("Warnings"), CUSTOM("Your own triggers");
        public final String label;
        Group(String label) { this.label = label; }
    }

    /// One of FOE+'s own sounds. {@code onByDefault}: whether it plays before you change anything.
    public record Kind(String id, String label, Group group, int color, boolean onByDefault) {}

    public static final List<Kind> KINDS = List.of(
            new Kind("witching_hour", "Witching Hour", Group.EVENTS, 0xE09A5B, true),
            new Kind("titanoboa", "Titanoboa", Group.EVENTS, 0x7CD67C, true),
            new Kind("bessie", "Bessie", Group.EVENTS, 0x7FC4F0, true),
            new Kind("rougarou", "Rougarou", Group.EVENTS, 0xB7A2EC, true),
            new Kind("garaboncias", "Garaboncias", Group.EVENTS, 0xD6A2EC, true),
            new Kind("fabled_event", "Fabled Fish event", Group.EVENTS, 0xFC3E35, true),
            new Kind("fabled_caught", "Fabled Fish caught", Group.EVENTS, 0xFC3E35, false),

            new Kind("moon_rise", "A moon rises", Group.SERVER, 0x60C2E9, false),
            new Kind("blooming_oasis", "Blooming Oasis", Group.SERVER, 0xE87BD9, false),
            new Kind("gold_rush", "Gold Rush", Group.SERVER, 0xFFC93C, false),
            new Kind("fish_frenzy", "Fish Frenzy", Group.SERVER, 0x55FFFF, false),
            new Kind("thunderstorm", "Thunderstorm", Group.SERVER, 0xC8C8FF, false),
            new Kind("booster", "Global booster", Group.SERVER, 0x55FF55, false),

            new Kind("albino", "Albino", Group.CATCHES, 0xF2F2F2, false),
            new Kind("melanistic", "Melanistic", Group.CATCHES, 0x8A8AA0, false),
            new Kind("trophy", "Trophy", Group.CATCHES, 0xFFC93C, false),
            new Kind("fabled", "Fabled catch", Group.CATCHES, 0xFC3E35, false),
            new Kind("gigantic", "Gigantic", Group.CATCHES, 0xFF5555, false),
            new Kind("rare_catch", "Capsule / Lightning Bottle", Group.CATCHES, 0xB04CFF, false),
            new Kind("pet_pulled", "Pet pulled", Group.CATCHES, 0xB04CFF, false),
            new Kind("reward_pet", "Contest / event / quest pet", Group.CATCHES, 0xFFAA00, false),

            new Kind("friend_online", "Friend online", Group.SOCIAL, 0x55FFFF, false),
            new Kind("friend_offline", "Friend offline", Group.SOCIAL, 0x55FFFF, false),
            new Kind("crew_online", "Crew member on/offline", Group.SOCIAL, 0xFFAA00, false),
            new Kind("contest_best", "Contest personal best", Group.SOCIAL, 0xFFAA00, false),
            new Kind("auto_tip", "Auto Tip", Group.SOCIAL, 0xFFAA00, false),
            new Kind("update", "FOE+ update", Group.SOCIAL, 0x55FF55, false),

            new Kind("inventory_full", "Inventory full", Group.WARNINGS, 0xFF5555, true),
            new Kind("daily_quest", "Daily quest", Group.WARNINGS, 0xFF5555, false),
            new Kind("wrong_pet", "Wrong pet", Group.WARNINGS, 0xFF5555, false));

    /// What's saved for each sound.
    public static class Setting {
        public boolean on;
        /// Empty: no sound (what every sound FOE+ never had starts as, until you pick one).
        public String sound = "";
        public float volume = 1f;
        public float pitch = 1f;
        public String source = "MASTER";
        /// A second play straight after (pitch × echoPitch, volume × echoVolume; 0: none). FOE+'s own alert
        /// and inventory sounds always played twice; a sound you pick plays once.
        public float echoPitch = 0f, echoVolume = 0f;

        Setting copy() {
            Setting s = new Setting();
            s.on = on; s.sound = sound; s.volume = volume; s.pitch = pitch; s.source = source; s.echoPitch = echoPitch; s.echoVolume = echoVolume;
            return s;
        }
    }

    /// Your own sound trigger: a sound when a chat line has {@code match} in it (any case), or matches it as a
    /// pattern when {@code regex}.
    public static class Custom extends Setting {
        public String name = "My trigger";
        public String match = "";
        public boolean regex = false;
    }

    static final class Saved {
        int version = 1;
        Map<String, Setting> sounds = new LinkedHashMap<>();
        List<Custom> custom = new ArrayList<>();
    }

    static Supplier<Path> FILE = () -> net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve(dannypx.foe.FishOnMCExtras.MOD_ID).resolve("sounds.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Saved saved;

    //region Settings
    /// A sound's settings (made from FOE+'s defaults the first time).
    public static Setting get(String id) {
        load();
        Setting s = saved.sounds.get(id);
        if (s == null) {
            s = defaults(id);
            saved.sounds.put(id, s);
        }
        return s;
    }

    public static List<Custom> custom() {
        load();
        return saved.custom;
    }

    public static Custom addCustom() {
        Custom c = new Custom();
        c.on = true;
        custom().add(c);
        save();
        return c;
    }

    public static void removeCustom(Custom c) {
        custom().remove(c);
        save();
    }

    /// FOE+'s sound before you change it: the alerts and the inventory warning as their old settings had them
    /// (note block, played twice); everything else had no sound, so it has none until you pick one.
    static Setting defaults(String id) {
        Setting s = new Setting();
        Kind kind = KINDS.stream().filter(k -> k.id().equals(id)).findFirst().orElse(null);
        s.on = kind != null && kind.onByDefault();
        try {
            switch (kind == null ? Group.CUSTOM : kind.group()) {
                case EVENTS -> {
                    if (!id.equals("fabled_caught")) {
                        s.on = Configs.hudConfig.alertSound.get();
                        s.sound = soundId(Configs.hudConfig.alertSoundType.get());
                        s.echoPitch = 1.2f;
                        s.echoVolume = 1f;
                    }
                }
                case WARNINGS -> {
                    if (id.equals("inventory_full")) {
                        s.on = Configs.hudConfig.inventoryWarningSound.get();
                        s.sound = soundId(Configs.hudConfig.inventoryWarningSoundType.get());
                        s.echoPitch = 1f;
                        s.echoVolume = 0.5f;
                    }
                }
                default -> { }
            }
        } catch (Throwable ignored) {
            // config not loaded (tests): the plain defaults
        }
        return s;
    }

    private static String soundId(dannypx.foe.type.WarningSound sound) {
        return sound.sound.value().location().getPath();
    }

    public static void save() {
        load();
        try {
            Path f = FILE.get();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(saved));
        } catch (Throwable ignored) {
            // kept for this session
        }
    }

    private static void load() {
        if (saved != null) return;
        saved = new Saved();
        try {
            Path f = FILE.get();
            if (f != null && Files.exists(f)) {
                Saved s = GSON.fromJson(Files.readString(f), Saved.class);
                if (s != null) {
                    if (s.sounds == null) s.sounds = new LinkedHashMap<>();
                    if (s.custom == null) s.custom = new ArrayList<>();
                    saved = s;
                }
            }
        } catch (Throwable ignored) {
            // a broken file: FOE+'s defaults
        }
    }

    /// For tests.
    static void reset() { saved = new Saved(); }
    //endregion

    //region Playing
    private static SoundInstance last;

    /// Plays one of FOE+'s sounds, if it's on.
    public static void play(String id) {
        try {
            if (!Configs.mainConfig.enableMod.get()) return;
            Setting s = get(id);
            if (s.on) play(s, false);
        } catch (Throwable ignored) {
            // never let a sound break what called it
        }
    }

    /// Plays a sound's settings (on or not: the ▶ buttons). {@code test}: stops the last test first.
    public static void play(Setting s, boolean test) {
        Minecraft mc = Minecraft.getInstance();
        if (s.sound == null || s.sound.isBlank()) return;
        Identifier id = Identifier.tryParse(s.sound.trim());
        if (id == null || s.volume <= 0f) return;
        SoundSource source = sourceOf(s.source);
        if (test && last != null) mc.getSoundManager().stop(last);
        last = instance(id, source, s.volume, s.pitch);
        mc.getSoundManager().play(last);
        if (s.echoVolume > 0f && s.echoPitch > 0f) {
            mc.getSoundManager().play(instance(id, source, s.volume * s.echoVolume, s.pitch * s.echoPitch));
        }
    }

    private static SoundInstance instance(Identifier id, SoundSource source, float volume, float pitch) {
        return new SimpleSoundInstance(id, source, Math.max(0f, Math.min(1f, volume)), Math.max(0.5f, Math.min(2f, pitch)),
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
    }

    public static SoundSource sourceOf(String name) {
        try {
            return SoundSource.valueOf(name == null ? "MASTER" : name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SoundSource.MASTER;
        }
    }
    //endregion

    //region Your own triggers
    /// Called for every chat line from the server: plays the sound of each of your triggers it matches.
    public static void onChat(String line) {
        if (line == null || line.isBlank()) return;
        try {
            if (!Configs.mainConfig.enableMod.get()) return;
            for (Custom c : List.copyOf(custom())) {
                if (c.on && matches(c, line)) {
                    play(c, false);
                    return; // one sound per line
                }
            }
        } catch (Throwable ignored) {
            // a bad pattern: no sound
        }
    }

    /// Package-private for tests.
    static boolean matches(Custom c, String line) {
        if (c.match == null || c.match.isBlank()) return false;
        String plain = line.replaceAll("§.", "");
        if (c.regex) {
            try {
                return Pattern.compile(c.match, Pattern.CASE_INSENSITIVE).matcher(plain).find();
            } catch (RuntimeException e) {
                return false;
            }
        }
        return plain.toLowerCase(Locale.ROOT).contains(c.match.toLowerCase(Locale.ROOT));
    }
    //endregion

    //region Server events (sounds when one starts)
    private static final Map<String, Boolean> was = new LinkedHashMap<>();

    /// Called every tick: a sound for each server event that has just started. Nothing on the first look
    /// after joining (an event that was already on when you came in doesn't count as starting).
    public static void tick() {
        try {
            if (!dannypx.foe.handler.logic.ConnectionHandler.instance().isOnServer()) {
                was.clear();
                return;
            }
            var events = dannypx.foe.handler.logic.EventTimesHandler.instance();
            started("moon_rise", dannypx.foe.handler.logic.MoonTimerHandler.instance().getCurrentMoon() != null);
            started("blooming_oasis", events.oasisActive());
            started("gold_rush", events.goldRushActive());
            started("fish_frenzy", events.frenzyActive());
            started("thunderstorm", dannypx.foe.handler.logic.RareCatchHandler.isThunderstorm(
                    dannypx.foe.handler.fetch.BossEventHandler.instance().weatherIcons()));
            var boosters = dannypx.foe.handler.logic.StoreTimerHandler.instance();
            for (String kind : List.of("money", "xp", "luck", "scale")) {
                started("booster:" + kind, boosters.boosterRemainingMs(kind) > 0);
            }
        } catch (Throwable ignored) {
            // not ready yet
        }
    }

    private static void started(String key, boolean now) {
        Boolean before = was.put(key, now);
        if (before != null && !before && now) play(key.startsWith("booster:") ? "booster" : key);
    }
    //endregion
}
