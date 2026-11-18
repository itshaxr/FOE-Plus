package dannypx.foe.screens.element.hud;

import dannypx.foe.handler.logic.CommunityEventHandler;
import dannypx.foe.handler.logic.CommunityRateHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Community Catches, at the bottom of the Timers panel (under a line):
 * <pre>
 * 11.19K / 100.00K Catches          11.2%
 * ████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░
 * Per hour +7.19K           Goal in 12h 21m
 * You helped 352
 * </pre>
 * Counts as the boss bar does (every catch), works out the pace over the last hour and starts over when the goal
 * is reached.
 */
final class CommunityCatches {
    static final String BAR = " ".repeat(40);
    private float target = 0f, shown = 0f;
    private long lastNs = 0L;

    /// The lines now, or none before the goal has been read.
    List<Component> lines() {
        CommunityRateHandler rate = CommunityRateHandler.instance();
        long cur = rate.current(), goal = rate.goal();
        if (cur < 0 || goal <= 0) return List.of();
        long now = System.currentTimeMillis();
        CommunityEventHandler e = CommunityEventHandler.instance();
        return communityLines(cur, goal, rate.perHour(now), rate.msToGoal(now), e.hasData() ? e.contribution() : -1);
    }

    List<Component> communityLines(long cur, long goal, double perHour, long msToGoal, long mine) {
        List<Component> lines = new ArrayList<>();
        float pct = Math.min(1f, cur / (float) goal);
        target = pct;
        lines.add(PanelElement.join(Component.literal(num(cur)).withStyle(ChatFormatting.GREEN),
                Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY),
                Component.literal(num(goal)).withStyle(ChatFormatting.WHITE),
                Component.literal(" Catches").withStyle(ChatFormatting.GRAY), PanelElement.TAB,
                Component.literal(String.format(Locale.ROOT, "%.1f%%", pct * 100f)).withStyle(ChatFormatting.AQUA)));
        // the game's look by default: a struck-through line, filled in teal (see GameXpBar)
        lines.add(GameXpBar.gameStyle() ? GameXpBar.bar(pct, 40) : Component.literal(BAR));
        lines.add(PanelElement.join(Component.literal("Per hour ").withStyle(ChatFormatting.GRAY),
                perHour < 0 ? Component.literal("counting…").withStyle(ChatFormatting.DARK_GRAY)
                        : Component.literal("+" + num(Math.round(perHour))).withStyle(ChatFormatting.GREEN),
                PanelElement.TAB,
                Component.literal("Goal in ").withStyle(ChatFormatting.GRAY),
                msToGoal < 0 ? Component.literal("—").withStyle(ChatFormatting.DARK_GRAY)
                        : Component.literal(CommunityRateHandler.duration(msToGoal)).withStyle(ChatFormatting.YELLOW)));
        if (mine >= 0) lines.add(PanelElement.join(Component.literal("You helped ").withStyle(ChatFormatting.GRAY),
                Component.literal(num(mine)).withStyle(ChatFormatting.GOLD)));
        return lines;
    }

    /// 11190 → "11.19K", 100000 → "100.00K", 352 → "352".
    static String num(long n) {
        if (n < 1_000) return String.valueOf(n);
        if (n < 1_000_000) return String.format(Locale.ROOT, "%.2fK", n / 1_000.0);
        return String.format(Locale.ROOT, "%.2fM", n / 1_000_000.0);
    }

    /// Draws the bar on its line (called by the panel just before each line).
    void beforeLine(GuiGraphics g, Font font, Component line, int x, int y) {
        if (GameXpBar.gameStyle() || !line.getString().equals(BAR)) return;
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        shown = ease(shown, target, dt);
        // still, no shine running along it: the community count creeps up all the time, a moving bar distracts
        PetPanelElement.drawBar(g, x, y + 3, font.width(line), shown, 0xFF7CF07C, 0xFF2EA043, false, 3);
    }

    /// Package-private for tests: the bar's fill easing gently to the count (about two seconds to settle), back to
    /// empty only when the goal was reached and a new one began (the count dropped by half or more), never for a
    /// reading that dips a little.
    static float ease(float shown, float target, float dt) {
        if (target < shown - 0.5f) return 0f;
        float next = shown + (target - shown) * (1f - (float) Math.exp(-dt * 2f));
        return Math.abs(target - next) < 0.0005f ? target : next;
    }

    List<Component> previewLines() {
        return communityLines(11_190, 100_000, 7_190, 12 * 3_600_000L + 21 * 60_000L, 352);
    }
}
