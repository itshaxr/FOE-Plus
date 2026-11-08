package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.MiniEventHandler;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The mini event from /minievent: when the daily missions reroll, then each mission with its
 * progress, in the server's colours. Hidden when no mini event is on.
 */
public class MiniEventPanelElement extends PanelElement {
    public MiniEventPanelElement() {
        this(false);
    }

    public MiniEventPanelElement(boolean isCopy) {
        super(Configs.hudConfig.miniEventPanelXPosition.get() / 100f,
                Configs.hudConfig.miniEventPanelYPosition.get() / 100f,
                Configs.hudConfig.miniEventPanelAlignment.get(),
                Component.literal("Mini Event"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showMiniEventPanel.get(); }
    @Override protected float configX() { return Configs.hudConfig.miniEventPanelXPosition.get() / 100f; }
    @Override protected float configY() { return Configs.hudConfig.miniEventPanelYPosition.get() / 100f; }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.miniEventPanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.miniEventPanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.miniEventPanelOpacity.get(); }

    @Override
    protected boolean alignTextToAnchor() {
        return false;
    }

    @Override
    protected Component title() {
        return Component.literal("ᴍɪɴɪ ᴇᴠᴇɴᴛ").withStyle(ChatFormatting.BOLD);
    }

    /// "Rerolls 23h 55m 2s" (when the daily missions reroll)
    static Component reroll(long msLeft) {
        return join(Component.literal("Rerolls ").withStyle(ChatFormatting.GRAY),
                Component.literal(MiniEventHandler.dhms(msLeft)).withStyle(ChatFormatting.WHITE));
    }

    /// "— Kenai River GIGANTIC  (0/2) One Timer", with a ✔ once it's done.
    static Component mission(MiniEventHandler.Mission m) {
        String progress = m.progress().getString();
        boolean serverTicked = progress.contains("✓") || progress.contains("✔");
        return join(badged(m.name()), Component.literal("  "), m.progress(),
                m.complete() && !serverTicked ? Component.literal(" ✔").withStyle(ChatFormatting.GREEN) : Component.empty());
    }

    /// The server writes GIGANTIC as plain red text: shown as the GIGANTIC badge instead (the one
    /// on the All-Time Stats panel).
    static Component badged(Component name) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        name.visit((style, text) -> {
            String plain = ChatFormatting.stripFormatting(text);
            String word = plain == null ? "" : plain.trim();
            if (word.equalsIgnoreCase("gigantic") || word.equals("ɢɪɢᴀɴᴛɪᴄ")) {
                int lead = text.indexOf(text.trim());
                if (lead > 0) out.append(Component.literal(text.substring(0, lead)).setStyle(style));
                out.append(StatsPanelElement.GIGANTIC_BADGE);
                String tail = text.substring(lead + text.trim().length());
                if (!tail.isEmpty()) out.append(Component.literal(tail).setStyle(style));
            } else {
                // Badge glyphs (ALBINO, MELANISTIC, TROPHY...) carry their own colours: drawn
                // white so the line's colour doesn't tint them; the words keep the line's colour.
                StringBuilder run = new StringBuilder();
                boolean glyphRun = false;
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    boolean glyph = c >= '\uE000' && c <= '\uF8FF';
                    if (glyph != glyphRun && run.length() > 0) {
                        out.append(Component.literal(run.toString()).setStyle(glyphRun ? style.withColor(ChatFormatting.WHITE) : style));
                        run.setLength(0);
                    }
                    glyphRun = glyph;
                    run.append(c);
                }
                if (run.length() > 0) out.append(Component.literal(run.toString()).setStyle(glyphRun ? style.withColor(ChatFormatting.WHITE) : style));
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }

    @Override
    protected List<Component> lines() {
        MiniEventHandler event = MiniEventHandler.instance();
        List<Component> lines = new ArrayList<>();
        if (!event.active()) return lines;
        if (Configs.hudConfig.miniEventOnlyAtLocation.get()) {
            dannypx.foe.helper.FoeLocations.Location at = event.location();
            if (at != null && dannypx.foe.handler.logic.PetLocationHandler.currentLocation() != at) return lines;
        }
        // Just the daily reroll timer above the missions (the event's end time is left out).
        if (event.rerollInMs() > 0) lines.add(reroll(event.rerollInMs()));
        // Finished missions show their ✔ for a few seconds, then leave until the daily reroll
        // (repeatable ones stay).
        for (MiniEventHandler.Mission m : event.missions()) {
            if (event.shouldShow(m)) lines.add(mission(m));
        }
        // Never an empty (hidden) panel while the event is on.
        if (lines.isEmpty()) lines.add(Component.literal("All missions done ✔").withStyle(ChatFormatting.GREEN));
        return lines;
    }

    @Override
    protected List<Component> previewLines() {
        List<Component> lines = new ArrayList<>();
        lines.add(reroll(23 * 3_600_000L + 55 * 60_000L + 2_000L));
        lines.add(mission(new MiniEventHandler.Mission(
                Component.literal("— ").withStyle(ChatFormatting.RED).append(Component.literal("Kenai River").withStyle(ChatFormatting.GREEN)),
                Component.literal("(0/2) One Timer").withStyle(ChatFormatting.GRAY), 0, 2)));
        lines.add(mission(new MiniEventHandler.Mission(
                Component.literal("— ").withStyle(ChatFormatting.GOLD).append(Component.literal("Sockeye Salmon").withStyle(ChatFormatting.GREEN)),
                Component.literal("(3/10) Daily").withStyle(ChatFormatting.GRAY), 3, 10)));
        return lines;
    }
}
