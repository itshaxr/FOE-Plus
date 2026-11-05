package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto-tipping, ported from FishOnAutotip by Kimberlyow (GPL-3.0).
 * Watches chat for reaction winners and chummer placements and runs /pay with the amount you set.
 * <ul>
 *   <li>/pay has a 3 second cooldown on FishOnMC, so tips that arrive together are queued and
 *       sent 3 seconds apart instead of being dropped.</li>
 *   <li>Never tips yourself, players on your ignore list, or past your session spending limit.</li>
 *   <li>FishOnMC's minimum /pay is $100.</li>
 * </ul>
 */
public class AutotipHandler {
    private static AutotipHandler INSTANCE = new AutotipHandler();

    // [A-Za-z0-9_]{2,16}: Minecraft usernames (legacy 2-letter names included).
    private static final Pattern REACTION = Pattern.compile("REACTIONS » ([A-Za-z0-9_]{2,16}) has typed");
    private static final Pattern CHUMMER = Pattern.compile("CHUMMER » ([A-Za-z0-9_]{2,16}) activated a (.) Chummer");
    private static final long PAY_COOLDOWN_MS = 3000L;
    private static final int MAX_QUEUE = 5;

    public record Tip(String player, int amount, String reason, long time) {}

    private final ArrayDeque<Tip> queue = new ArrayDeque<>();
    private final List<Tip> log = new ArrayList<>();
    private long lastPayTime = 0L;
    private long sessionTotal = 0L;

    public static AutotipHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new AutotipHandler();
        }
        return INSTANCE;
    }

    public long getSessionTotal() {
        return sessionTotal;
    }

    /// Most recent tips first.
    public List<Tip> getLog() {
        List<Tip> copy = new ArrayList<>(log);
        Collections.reverse(copy);
        return copy;
    }

    public void onReceiveMessage(Component message) {
        if (!Configs.autotipConfig.enabled.get()) return;
        String text = message.getString();
        if (!text.contains("»")) return;

        var config = Configs.autotipConfig;
        if (text.startsWith("REACTIONS »")) {
            Matcher m = REACTION.matcher(text);
            if (m.find()) consider(m.group(1), config.tipReactions, config.reactionAmount, "reaction");
        } else if (text.startsWith("CHUMMER »")) {
            Matcher m = CHUMMER.matcher(text);
            if (!m.find()) return;
            switch (m.group(2)) {
                case "" -> consider(m.group(1), config.tipCommonChummer, config.commonChummerAmount, "common chummer");
                case "" -> consider(m.group(1), config.tipRareChummer, config.rareChummerAmount, "rare chummer");
                case "" -> consider(m.group(1), config.tipEpicChummer, config.epicChummerAmount, "epic chummer");
                case "" -> consider(m.group(1), config.tipLegendaryChummer, config.legendaryChummerAmount, "legendary chummer");
                case "" -> consider(m.group(1), config.tipMythicalChummer, config.mythicalChummerAmount, "mythical chummer");
                default -> { }
            }
        }
    }

    private void consider(String player, ValidatedBoolean enabled, ValidatedInt amount, String reason) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled.get() || amount.get() < 100 || mc.player == null) return;
        if (mc.player.getName().getString().equalsIgnoreCase(player)) return;
        if (isIgnored(player)) return;
        if (queue.size() >= MAX_QUEUE) return;
        queue.add(new Tip(player, amount.get(), reason, System.currentTimeMillis()));
    }

    private static boolean isIgnored(String player) {
        String list = Configs.autotipConfig.ignoredPlayers.get();
        if (list.isBlank()) return false;
        return Arrays.stream(list.split("[,\\s]+"))
                .anyMatch(name -> name.equalsIgnoreCase(player));
    }

    /// Sends queued tips, one per /pay cooldown.
    public void tick() {
        if (queue.isEmpty()) return;
        if (!Configs.autotipConfig.enabled.get()) {
            queue.clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPayTime < PAY_COOLDOWN_MS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            queue.clear();
            return;
        }

        Tip tip = queue.poll();
        int limit = Configs.autotipConfig.sessionLimit.get();
        if (limit > 0 && sessionTotal + tip.amount() > limit) {
            if (Configs.autotipConfig.announceTips.get()) {
                dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.line("AUTO TIP", dannypx.foe.helper.FoeChat.GOLD)
                        .append(dannypx.foe.helper.FoeChat.text("Session limit reached"))
                        .append(dannypx.foe.helper.FoeChat.sep())
                        .append(dannypx.foe.helper.FoeChat.text("skipped "))
                        .append(dannypx.foe.helper.FoeChat.white(tip.player())));
            }
            return;
        }

        lastPayTime = now;
        sessionTotal += tip.amount();
        log.add(tip);
        if (log.size() > 20) log.removeFirst();
        mc.getConnection().sendCommand("pay " + tip.player() + " " + tip.amount());

        if (Configs.autotipConfig.announceTips.get()) {
            // [FOE+] AUTO TIP Tipped Steve $500 · reaction
            dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.line("AUTO TIP", dannypx.foe.helper.FoeChat.GOLD)
                    .append(dannypx.foe.helper.FoeChat.text("Tipped "))
                    .append(dannypx.foe.helper.FoeChat.white(tip.player()))
                    .append(Component.literal(" $" + String.format(Locale.ROOT, "%,d", (long) tip.amount())).withStyle(ChatFormatting.GREEN))
                    .append(dannypx.foe.helper.FoeChat.sep())
                    .append(reasonComponent(tip.reason())));
        }
    }

    /// "common chummer" -> <common badge> chummer (the server's rarity badge, as in chat and on
    /// items); anything else (e.g. "reaction") as plain text.
    private static final java.util.Map<String, String> RARITY_BADGES = java.util.Map.of(
            "common", "\uF033", "rare", "\uF034", "epic", "\uF035", "legendary", "\uF036", "mythical", "\uF037");

    public static Component reasonComponent(String reason) {
        String lower = reason.toLowerCase(Locale.ROOT);
        if (lower.endsWith(" chummer")) {
            String badge = RARITY_BADGES.get(lower.substring(0, lower.length() - " chummer".length()).trim());
            if (badge != null) {
                return Component.empty()
                        .append(Component.literal(badge).withStyle(ChatFormatting.WHITE))
                        .append(dannypx.foe.helper.FoeChat.text(" chummer"));
            }
        }
        return dannypx.foe.helper.FoeChat.text(lower);
    }
}
