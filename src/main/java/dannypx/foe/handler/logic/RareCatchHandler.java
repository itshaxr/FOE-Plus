package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.store.StatsDataHandler;
import dannypx.foe.helper.FoeChat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rare pulls the server announces in chat: "RARE CATCH! You pulled 1x Infusion Capsule".
 * Tracked ones are counted with a dry streak and get a FOE+ line:
 * [FOE+] RARE CATCH! Infusion Capsule · Dry streak 1,234
 * <p>
 * Infusion Capsules only drop during a Blood Moon and Lightning in a Bottle only in a
 * thunderstorm or supercell, so their dry streaks only count fish caught while that's on.
 */
public final class RareCatchHandler {
    private RareCatchHandler() {}

    /// Server line (not player chat, which has "»" before the text).
    /// "You pulled 1x Infusion Capsule", "You pulled a 2x ...", "You pulled a Lightning in a Bottle".
    static final Pattern RARE_CATCH = Pattern.compile("RARE CATCH!\\s*You pulled (?:an? )?(?:(\\d+)\\s*x\\s+)?(.+?)\\s*[.!]?\\s*$");

    public static final String INFUSION_CAPSULE = "infusion_capsule";
    public static final String LIGHTNING_BOTTLE = "lightning_bottle";

    /// Tracked pulls: display name (as in chat, any case) → stats key.
    public static final Map<String, String> TRACKED = Map.of(
            "infusion capsule", INFUSION_CAPSULE,
            "lightning in a bottle", LIGHTNING_BOTTLE);

    /// Pulls whose dry streak only counts fish caught during their event.
    public static final java.util.List<String> EVENT_PULLS = java.util.List.of(INFUSION_CAPSULE, LIGHTNING_BOTTLE);

    public static boolean isEventPull(String key) {
        return EVENT_PULLS.contains(key);
    }

    /// Whether the event a pull drops in is on right now: a Blood Moon for capsules, a
    /// thunderstorm or supercell (⚡ on the boss bar) for bottles. Rain doesn't count.
    public static boolean eventOn(String key) {
        try {
            if (INFUSION_CAPSULE.equals(key)) return MoonTimerHandler.instance().getCurrentMoon() == MoonTimerHandler.Moon.BLOOD;
            if (LIGHTNING_BOTTLE.equals(key)) return isThunderstorm(dannypx.foe.handler.fetch.BossEventHandler.instance().getWeather().getString());
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
        return false;
    }

    /// The boss bar's weather shows ⚡ in a thunderstorm. Rain (☂) doesn't count.
    /// Package-private for tests.
    static boolean isThunderstorm(String weather) {
        return weather != null && weather.contains("\u26A1");
    }

    /// What a chat line is, if it's a tracked rare pull. Package-private for tests.
    record Pull(String name, String key, int count) {}

    static Pull parse(String text) {
        if (text == null) return null;
        String plain = ChatFormatting.stripFormatting(text);
        if (plain == null || plain.contains("»")) return null;
        Matcher m = RARE_CATCH.matcher(plain);
        if (!m.find()) return null;
        String name = m.group(2).trim();
        String key = TRACKED.get(name.toLowerCase(Locale.ROOT));
        if (key == null) return null;
        int count = 1;
        if (m.group(1) != null) {
            try {
                count = Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                count = 1;
            }
        }
        return new Pull(name, key, Math.max(1, count));
    }

    public static void onGameMessage(String text) {
        Pull pull = parse(text);
        if (pull == null) return;
        int streak = StatsDataHandler.instance().onRareCatch(pull.key(), pull.count());
        boolean chat = LIGHTNING_BOTTLE.equals(pull.key())
                ? Configs.hudConfig.showLightningBottleChat.get() : Configs.hudConfig.showInfusionCapsuleChat.get();
        if (chat) FoeChat.send(line(pull, streak));
    }

    /// [FOE+] RARE CATCH! Infusion Capsule · Dry streak 1,234  (or "2x ..."; "first one tracked")
    static MutableComponent line(Pull pull, int streak) {
        MutableComponent line = FoeChat.line("RARE CATCH!", FoeChat.PURPLE);
        if (pull.count() > 1) line.append(FoeChat.white(pull.count() + "x "));
        line.append(Component.literal(pull.name()).withColor(FoeChat.AQUA));
        if (streak >= 0) line.append(FoeChat.dryStreak(streak));
        else line.append(FoeChat.sep()).append(FoeChat.text("First one tracked"));
        return line;
    }
}
