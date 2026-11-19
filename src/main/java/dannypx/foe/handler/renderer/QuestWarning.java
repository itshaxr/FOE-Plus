package dannypx.foe.handler.renderer;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.DailyQuestHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// A big bold red warning at the top middle of the screen for 5 seconds when the last mission left in a
/// section is one away from done (so the section's daily quest is about to complete): "You are about to
/// complete daily quest" for the missions that count anywhere, and "You are about to complete Everglades
/// daily quest" for a place's own missions. It warns once, when that happens (by a catch, or by the
/// section's other missions finishing), not again at every join while it sits there.
public final class QuestWarning {
    private QuestWarning() {}

    static final long SHOW_MS = 5_000L;

    private static Component text = null;
    private static long shownAt = 0L;

    /// Whether each mission was, when last looked at, the last one left in its section and one away
    /// ("Everglades Missions|Red Drum|4" → false).
    private static final Map<String, Boolean> wasAboutToFinish = new HashMap<>();

    /// Called every tick: warns for a mission that has just got to one away.
    public static void tick() {
        try {
            check(DailyQuestHandler.instance().quests, System.currentTimeMillis());
        } catch (RuntimeException e) {
            // the quests changed under us: next tick
        }
    }

    /// Package-private for tests.
    static void check(List<DailyQuestHandler.Quest> quests, long now) {
        List<DailyQuestHandler.Quest> all = List.copyOf(quests);
        for (DailyQuestHandler.Quest q : all) {
            if (q.needed <= 0) continue;
            String key = q.section + "|" + q.goal + "|" + q.needed;
            // the list only holds unfinished missions: the last one in its section, one away
            long left = all.stream().filter(o -> java.util.Objects.equals(o.section, q.section)).count();
            boolean aboutToFinish = left == 1 && q.progress == q.needed - 1;
            Boolean before = wasAboutToFinish.put(key, aboutToFinish);
            // only when it gets there: not the first time FOE+ sees it like that
            if (before == null || before || !aboutToFinish) continue;
            boolean anywhere = isAnywhere(q.section);
            if (anywhere ? !Configs.hudConfig.questWarningAnywhere.get() : !Configs.hudConfig.questWarningLocation.get()) continue;
            show(message(q.section), now);
            dannypx.foe.handler.store.NotifySounds.play("daily_quest");
        }
    }

    /// The missions that count anywhere ("Base Missions", shown as "Anywhere"). Package-private for tests.
    static boolean isAnywhere(String section) {
        String name = placeOf(section);
        return name.isEmpty() || name.equalsIgnoreCase("base") || name.equalsIgnoreCase("anywhere");
    }

    /// "Everglades Missions" → "Everglades". Package-private for tests.
    static String placeOf(String section) {
        return section == null ? "" : section.trim().replaceFirst("(?i)\\s*missions?\\s*$", "").trim();
    }

    /// Package-private for tests.
    static Component message(String section) {
        String text = isAnywhere(section) ? "You are about to complete daily quest"
                : "You are about to complete " + placeOf(section) + " daily quest";
        return Component.literal(text).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
    }

    static void show(Component message, long now) {
        text = message;
        shownAt = now;
    }

    /// Drawn with the HUD: twice the text size, centred near the top, fading out over its last half second.
    public static void render(GuiGraphics g) {
        if (text == null) return;
        long age = System.currentTimeMillis() - shownAt;
        if (age < 0 || age > SHOW_MS) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int alpha = age > SHOW_MS - 500 ? (int) (255 * (SHOW_MS - age) / 500) : 255;
        if (alpha < 8) return;
        float scale = 2f;
        int w = font.width(text);
        float x = g.guiWidth() / 2f - w * scale / 2f;
        float y = Math.max(28, g.guiHeight() / 7f);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.drawString(font, text, 0, 0, (alpha << 24) | 0xFFFFFF, true);
        g.pose().popMatrix();
    }
}
