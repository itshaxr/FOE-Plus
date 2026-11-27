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
    public static final String PROSPECTING_AMULET = "prospecting_amulet";
    /// Any shard: counted when it lands in your inventory (as an item), so its line only reads the streak.
    public static final String SHARD = "shard";

    /// Tracked pulls: display name (as in chat, any case) → stats key.
    public static final Map<String, String> TRACKED = Map.of(
            "infusion capsule", INFUSION_CAPSULE,
            "lightning in a bottle", LIGHTNING_BOTTLE,
            "prospecting amulet", PROSPECTING_AMULET);

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
            if (LIGHTNING_BOTTLE.equals(key)) return isThunderstorm(dannypx.foe.handler.fetch.BossEventHandler.instance().weatherIcons());
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
        return false;
    }

    /// The boss bar's weather shows ⚡ in a thunderstorm (also at night, next to the moon's ○).
    /// Rain (☂) doesn't count.
    /// Package-private for tests.
    public static boolean isThunderstorm(String weather) {
        return weather != null && weather.contains("\u26A1");
    }

    /// What a chat line is, if it's a tracked rare pull. Package-private for tests.
    record Pull(String name, String key, int count, Component styled) {
        Pull(String name, String key, int count) { this(name, key, count, null); }
    }

    static Pull parse(String text) {
        if (text == null) return null;
        String plain = ChatFormatting.stripFormatting(text);
        if (plain == null || plain.contains("»")) return null;
        Matcher m = RARE_CATCH.matcher(plain);
        if (!m.find()) return null;
        String name = m.group(2).trim();
        String lower = name.toLowerCase(Locale.ROOT);
        String key = TRACKED.get(lower);
        if (key == null && lower.endsWith(" shard")) key = SHARD;
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

    /// A game message: counts a rare pull and sends FOE+'s line for it. True when the server's own line can be
    /// hidden (FOE+'s line for it is on, and so is hiding the server's).
    public static boolean onGameMessage(Component message) {
        Pull plain = parse(message.getString());
        if (plain == null) return false;
        Pull pull = new Pull(plain.name(), plain.key(), plain.count(), styledName(message, plain.name()));
        var c = Configs.hudConfig;
        if (SHARD.equals(pull.key())) {
            // the shard itself is counted when it reaches your inventory (before or after this line)
            dannypx.foe.helper.Safe.run("shard sound", CatchSound::onShard);
            int streak = StatsDataHandler.instance().onShardLine();
            boolean sent = c.showShardChat.get() && streak >= c.rarePullChatMinStreak.get();
            if (sent) FoeChat.send(line(pull, streak));
            // the server's line goes only when FOE+'s takes its place (under the smallest dry streak shown, FOE+
            // sends none, and hiding the server's left just the "+ 3x Oceanic Shard" pick-up line)
            return sent && c.hideServerPullLines.get();
        }
        int streak = StatsDataHandler.instance().onRareCatch(pull.key(), pull.count());
        dannypx.foe.handler.store.NotifySounds.play(switch (pull.key()) {
            case LIGHTNING_BOTTLE -> "lightning_bottle";
            case PROSPECTING_AMULET -> "prospecting_amulet";
            default -> "infusion_capsule";
        });
        boolean chat = switch (pull.key()) {
            case LIGHTNING_BOTTLE -> c.showLightningBottleChat.get();
            case PROSPECTING_AMULET -> c.showProspectingAmuletChat.get();
            default -> c.showInfusionCapsuleChat.get();
        };
        boolean sent = chat && (streak < 0 || streak >= c.rarePullChatMinStreak.get());
        if (sent) FoeChat.send(line(pull, streak));
        return sent && c.hideServerPullLines.get();
    }

    /// The item's name as the server coloured it in its line (a shard's gradient), or null when it can't be found.
    static Component styledName(Component message, String name) {
        if (message == null || name == null || name.isEmpty()) return null;
        java.util.List<String> parts = new java.util.ArrayList<>();
        java.util.List<net.minecraft.network.chat.Style> styles = new java.util.ArrayList<>();
        message.visit((style, text) -> {
            parts.add(text);
            styles.add(style);
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        String all = String.join("", parts);
        int from = all.lastIndexOf(name);
        if (from < 0) return null;
        int to = from + name.length(), at = 0;
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            String p = parts.get(i);
            int a = Math.max(from, at), b = Math.min(to, at + p.length());
            if (a < b) out.append(Component.literal(p.substring(a - at, b - at)).setStyle(styles.get(i)
                    .withClickEvent(null).withHoverEvent(null).withInsertion(null)));
            at += p.length();
        }
        return out;
    }

    /// The line with your own text, if you wrote one: & colour codes, %item% %count% %streak%.
    static Component own(Pull pull, int streak) {
        String t = Configs.hudConfig.rarePullChatText.get();
        if (t == null || t.isBlank()) return null;
        String filled = t.replace("%item%", pull.name()).replace("%count%", Integer.toString(pull.count()))
                .replace("%streak%", streak >= 0 ? String.format(Locale.ROOT, "%,d", streak) : "?");
        return Component.literal(filled.replaceAll("&([0-9a-fk-orA-FK-OR])", "\u00A7$1"));
    }

    /// [FOE+] RARE CATCH! Infusion Capsule · Dry streak 1,234  (or "2x ..."; "first one tracked")
    static Component line(Pull pull, int streak) {
        Component own = own(pull, streak);
        if (own != null) return own;
        return foeLine(pull, streak);
    }

    static final int SHARD_GREEN = 0x55FF55, AMULET_GOLD = 0xFFAA00;

    static MutableComponent foeLine(Pull pull, int streak) {
        // shards in green, Prospecting Amulets in gold, capsules and bottles in FOE+'s purple
        int colour = switch (pull.key()) {
            case SHARD -> SHARD_GREEN;
            case PROSPECTING_AMULET -> AMULET_GOLD;
            default -> FoeChat.PURPLE;
        };
        MutableComponent line = FoeChat.line("RARE CATCH!", colour);
        if (pull.count() > 1) line.append(FoeChat.white(pull.count() + "x "));
        // a shard in its own colours, as the server gives them; the others in FOE+'s aqua
        if (SHARD.equals(pull.key()) && pull.styled() != null) line.append(pull.styled());
        else line.append(Component.literal(pull.name()).withColor(FoeChat.AQUA));
        if (streak >= 0) line.append(FoeChat.dryStreak(streak));
        else line.append(FoeChat.sep()).append(FoeChat.text("First one tracked"));
        return line;
    }
}
