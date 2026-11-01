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
 * Tracked ones are counted with a dry streak (fish since the last one) and get a FOE+ line:
 * [FOE+] RARE CATCH! Infusion Capsule · Dry streak 1,234
 */
public final class RareCatchHandler {
    private RareCatchHandler() {}

    /// Server line (not player chat, which has "»" before the text).
    static final Pattern RARE_CATCH = Pattern.compile("RARE CATCH!\\s*You pulled (\\d+)\\s*x\\s+(.+?)\\s*[.!]?\\s*$");

    /// Tracked pulls: display name (as in chat, any case) → stats key.
    public static final Map<String, String> TRACKED = Map.of("infusion capsule", "infusion_capsule");

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
        int count;
        try {
            count = Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            count = 1;
        }
        return new Pull(name, key, Math.max(1, count));
    }

    public static void onGameMessage(String text) {
        Pull pull = parse(text);
        if (pull == null) return;
        int streak = StatsDataHandler.instance().onRareCatch(pull.key(), pull.count());
        if (Configs.hudConfig.showInfusionCapsuleChat.get()) FoeChat.send(line(pull, streak));
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
