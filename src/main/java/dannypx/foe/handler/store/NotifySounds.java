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

    /// The kinds "Every notifier" turns on and off together: all but the server's catch sound (that one isn't FOE+'s).
    public static List<Kind> notifiers() {
        return KINDS.stream().filter(k -> !k.id().equals(dannypx.foe.handler.logic.CatchSound.ID)).toList();
    }

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

            new Kind("fish_bite", "Fish bite (BITE!)", Group.CATCHES, 0x55FFFF, false),
            // the server's own sound for a catch: On plays it (or the sound you pick instead), Off mutes it
            new Kind(dannypx.foe.handler.logic.CatchSound.ID, "Fish caught (server's sound)", Group.CATCHES, 0x55FF55, true),
            new Kind("albino", "Albino", Group.CATCHES, 0xF2F2F2, false),
            new Kind("melanistic", "Melanistic", Group.CATCHES, 0x8A8AA0, false),
            new Kind("trophy", "Trophy", Group.CATCHES, 0xFFC93C, false),
            new Kind("fabled", "Fabled catch", Group.CATCHES, 0xFC3E35, false),
            new Kind("gigantic", "Gigantic", Group.CATCHES, 0xFF5555, false),
            new Kind("rare_catch", "Capsule / Lightning Bottle", Group.CATCHES, 0xB04CFF, false),
            new Kind("pet_pulled", "Pet pulled", Group.CATCHES, 0xB04CFF, false),
            new Kind("perfect_pet", "Perfect pet pulled", Group.CATCHES, 0xFF55FF, true),
            new Kind("reward_pet", "Contest / event / quest pet", Group.CATCHES, 0xFFAA00, false),

            new Kind("friend_online", "Friend online", Group.SOCIAL, 0x55FFFF, false),
            new Kind("friend_offline", "Friend offline", Group.SOCIAL, 0x55FFFF, false),
            new Kind("crew_online", "Crew member on/offline", Group.SOCIAL, 0xFFAA00, false),
            new Kind("contest_best", "Contest personal best", Group.SOCIAL, 0xFFAA00, false),
            new Kind("auto_tip", "Auto Tip", Group.SOCIAL, 0xFFAA00, false),
            new Kind("update", "FOE+ update", Group.SOCIAL, 0x55FF55, false),

            new Kind("inventory_full", "Inventory full", Group.WARNINGS, 0xFF5555, true),
            new Kind("daily_quest", "About to complete daily quest", Group.WARNINGS, 0xFF5555, false),
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
        /// The first versions' one trigger (moved into {@link #triggers} when read).
        public String match = "";
        public boolean regex = false;
        /// Like ChatNotify: it sounds when any trigger matches and no exception does.
        public List<Rule> triggers = new ArrayList<>();
        public List<Rule> exceptions = new ArrayList<>();
        /// Whose lines: ANYONE, PLAYERS (a player's chat) or SERVER (everything else).
        public String from = "ANYONE";
        public boolean caseSensitive = false;
        /// Leave out your own chat lines.
        public boolean skipOwn = true;
        /// Seconds before it can sound again (0: every time).
        public int cooldown = 0;
        /// Also: colour the matched words in chat (bold / underlined as picked), show the line on screen, flash the
        /// game's icon on your taskbar when it isn't the window in front.
        public boolean colorWords = false, bold = false, underline = false, title = false, flash = false;
        public int wordColor = 0xFFD866;
        transient long lastAt = 0L;
    }

    /// One trigger or exception: its text and how it's looked for.
    public static class Rule {
        public String text = "";
        /// CONTAINS, WORD (a whole word), STARTS (the line starts with it), REGEX (a pattern), KEY (the server's
        /// message key, for game messages that have one).
        public String mode = "CONTAINS";

        public Rule() {}

        public Rule(String text, String mode) {
            this.text = text;
            this.mode = mode;
        }
    }

    public static final String[] MODES = {"CONTAINS", "WORD", "STARTS", "REGEX", "KEY"};

    public static String modeName(String mode) {
        return switch (mode == null ? "" : mode) {
            case "WORD" -> "Whole word";
            case "STARTS" -> "Starts with";
            case "REGEX" -> "Pattern";
            case "KEY" -> "Key";
            default -> "Contains";
        };
    }

    public static String nextMode(String mode) {
        for (int i = 0; i < MODES.length; i++) if (MODES[i].equals(mode)) return MODES[(i + 1) % MODES.length];
        return MODES[1];
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
        c.triggers.add(new Rule());
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
                default -> {
                    // a perfect pet: a fanfare from the start (pick another in Sounds)
                    if (id.equals("perfect_pet")) s.sound = "minecraft:ui.toast.challenge_complete";
                }
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
                    for (Custom c : s.custom) upgrade(c);
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
        boolean was = dannypx.foe.handler.logic.CatchSound.own;
        dannypx.foe.handler.logic.CatchSound.own = true;     // FOE+'s own: never taken for the server's catch sound
        try {
            mc.getSoundManager().play(last);
            if (s.echoVolume > 0f && s.echoPitch > 0f) {
                mc.getSoundManager().play(instance(id, source, s.volume * s.echoVolume, s.pitch * s.echoPitch));
            }
        } finally {
            dannypx.foe.handler.logic.CatchSound.own = was;
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
    /// The first versions' one trigger becomes the first of its triggers; missing lists are made.
    public static void upgrade(Custom c) {
        if (c.triggers == null) c.triggers = new ArrayList<>();
        if (c.exceptions == null) c.exceptions = new ArrayList<>();
        if (c.from == null) c.from = "ANYONE";
        if (c.triggers.isEmpty() && c.match != null && !c.match.isBlank()) {
            c.triggers.add(new Rule(c.match, c.regex ? "REGEX" : "CONTAINS"));
            c.match = "";
        }
    }

    public static void onChat(net.minecraft.network.chat.Component line) {
        if (line == null) return;
        String plain = line.getString();
        if (plain.isBlank()) return;
        try {
            if (!Configs.mainConfig.enableMod.get()) return;
            long now = System.currentTimeMillis();
            for (Custom c : List.copyOf(custom())) {
                if (!c.on || !matches(c, line, plain)) continue;
                if (c.cooldown > 0 && now - c.lastAt < c.cooldown * 1000L) continue;
                c.lastAt = now;
                play(c, false);
                if (c.title) dannypx.foe.handler.renderer.QuestWarning.flash(line.copy());
                if (c.flash) flashWindow();
                return; // one sound per line
            }
        } catch (Throwable ignored) {
            // a bad pattern: no sound
        }
    }

    private static void flashWindow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isWindowActive()) return;
        org.lwjgl.glfw.GLFW.glfwRequestWindowAttention(mc.getWindow().handle());
    }

    /// A player's chat line ("! [227] Gorkinzola » awoooo"), not the server's.
    static final Pattern PLAYER_LINE = Pattern.compile("^\\s*(?:\\S\\s+)?\\[\\d+\\]\\s+\\S+.*?»");

    /// Package-private for tests: whether a line sets this trigger off (who it's from, any trigger, no exception).
    static boolean matches(Custom c, net.minecraft.network.chat.Component line, String plain) {
        upgrade(c);
        if (c.triggers.isEmpty()) return false;
        plain = plain.replaceAll("§.", "");
        boolean player = PLAYER_LINE.matcher(plain).find();
        if ("PLAYERS".equals(c.from) && !player) return false;
        if ("SERVER".equals(c.from) && player) return false;
        if (c.skipOwn && player) {
            Minecraft mc = Minecraft.getInstance();
            String me = mc == null || mc.player == null ? null : mc.player.getGameProfile().name();
            if (me != null && Pattern.compile("\\b" + Pattern.quote(me) + "\\s*»").matcher(plain).find()) return false;
        }
        boolean hit = false;
        for (Rule r : c.triggers) if (ruleMatches(r, c.caseSensitive, line, plain)) { hit = true; break; }
        if (!hit) return false;
        for (Rule r : c.exceptions) if (ruleMatches(r, c.caseSensitive, line, plain)) return false;
        return true;
    }

    static boolean ruleMatches(Rule r, boolean caseSensitive, net.minecraft.network.chat.Component line, String plain) {
        if (r == null || r.text == null || r.text.isBlank()) return false;
        if ("KEY".equals(r.mode)) return line != null && hasKey(line, r.text.trim());
        return !spans(r, caseSensitive, plain).isEmpty();
    }

    /// Where a rule matches in the line, as {start, end} pairs (none for a Key rule). Package-private for tests.
    static List<int[]> spans(Rule r, boolean caseSensitive, String plain) {
        List<int[]> out = new ArrayList<>();
        if (r.text == null || r.text.isBlank() || "KEY".equals(r.mode)) return out;
        String regex = switch (r.mode == null ? "" : r.mode) {
            case "REGEX" -> r.text;
            case "WORD" -> "(?<![\\p{L}\\p{N}])" + Pattern.quote(r.text) + "(?![\\p{L}\\p{N}])";
            case "STARTS" -> "^\\s*" + Pattern.quote(r.text);
            default -> Pattern.quote(r.text);
        };
        try {
            var m = Pattern.compile(regex, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(plain);
            while (m.find()) {
                if (m.end() > m.start()) out.add(new int[]{m.start(), m.end()});
                else if (m.end() >= plain.length()) break;
            }
        } catch (RuntimeException ignored) {
            // a bad pattern matches nothing
        }
        return out;
    }

    /// The message (or a part of it) is the game's translated message with this key.
    private static boolean hasKey(net.minecraft.network.chat.Component c, String key) {
        if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
            if (t.getKey().equals(key)) return true;
            for (Object arg : t.getArgs()) if (arg instanceof net.minecraft.network.chat.Component a && hasKey(a, key)) return true;
        }
        for (net.minecraft.network.chat.Component sibling : c.getSiblings()) if (hasKey(sibling, key)) return true;
        return false;
    }

    /// Chat as it's shown: the words your triggers matched in their colour (bold / underlined as picked), for each
    /// trigger with "Colour the words in chat" on that this line sets off.
    public static net.minecraft.network.chat.Component restyle(net.minecraft.network.chat.Component line) {
        try {
            if (line == null || !Configs.mainConfig.enableMod.get()) return line;
            String plain = line.getString();
            net.minecraft.network.chat.Component out = line;
            for (Custom c : List.copyOf(custom())) {
                if (!c.on || !c.colorWords || !matches(c, line, plain)) continue;
                List<int[]> spans = new ArrayList<>();
                for (Rule r : c.triggers) spans.addAll(spans(r, c.caseSensitive, plain));
                if (!spans.isEmpty()) out = paint(out, spans, c);
            }
            return out;
        } catch (Throwable e) {
            return line;
        }
    }

    private static net.minecraft.network.chat.Component paint(net.minecraft.network.chat.Component line, List<int[]> spans, Custom c) {
        net.minecraft.network.chat.MutableComponent out = net.minecraft.network.chat.Component.empty();
        int[] at = {0};
        line.visit((style, text) -> {
            int start = at[0];
            int i = 0;
            while (i < text.length()) {
                boolean in = inSpan(spans, start + i);
                int j = i + 1;
                while (j < text.length() && inSpan(spans, start + j) == in) j++;
                net.minecraft.network.chat.Style st = style;
                if (in) {
                    st = st.withColor(c.wordColor & 0xFFFFFF);
                    if (c.bold) st = st.withBold(true);
                    if (c.underline) st = st.withUnderlined(true);
                }
                out.append(net.minecraft.network.chat.Component.literal(text.substring(i, j)).withStyle(st));
                i = j;
            }
            at[0] = start + text.length();
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }

    private static boolean inSpan(List<int[]> spans, int pos) {
        for (int[] s : spans) if (pos >= s[0] && pos < s[1]) return true;
        return false;
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
