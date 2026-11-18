package dannypx.foe.handler.logic;

import dannypx.foe.mixin.accessor.GuiAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import com.mojang.serialization.JsonOps;

import java.util.ArrayList;
import java.util.List;

/**
 * /foe minigame dump: records what the reel-in minigame shows (the action bar, the title and subtitle, and the boss bars)
 * through your next catches, as text and as the game's JSON with every glyph and colour, and copies it to
 * your clipboard. For building the catch ring from the minigame's own progress.
 */
public final class MinigameDumpHandler {
    private static final MinigameDumpHandler INSTANCE = new MinigameDumpHandler();

    public static MinigameDumpHandler instance() {
        return INSTANCE;
    }

    private boolean recording = false;
    private final List<String> lines = new ArrayList<>();
    private String last = "";
    private long startedAt = 0L;

    public boolean toggle() {
        recording = !recording;
        if (recording) { lines.clear(); last = ""; startedAt = System.currentTimeMillis(); GlyphDump.reset(); }
        return recording;
    }

    public boolean recording() {
        return recording;
    }

    public int count() {
        return lines.size();
    }

    public String all() {
        return String.join("\n", lines);
    }

    public void tick(Minecraft mc) {
        if (!recording || mc.gui == null) return;
        if (System.currentTimeMillis() - startedAt > 5 * 60_000L) { recording = false; return; }   // never left on
        GuiAccessor gui = (GuiAccessor) mc.gui;
        StringBuilder now = new StringBuilder();
        if (gui.getOverlayMessageTime() > 0) GlyphDump.save(gui.getOverlayMessageString());
        if (gui.foe$getTitle() != null) GlyphDump.save(gui.foe$getTitle());
        if (gui.getOverlayMessageTime() > 0) now.append("ACTIONBAR ").append(describe(gui.getOverlayMessageString())).append('\n');
        if (gui.foe$getTitle() != null) now.append("TITLE ").append(describe(gui.foe$getTitle())).append('\n');
        if (gui.foe$getSubtitle() != null) now.append("SUBTITLE ").append(describe(gui.foe$getSubtitle())).append('\n');
        // the boss bars too, in case the minigame is drawn there
        for (var ev : ((dannypx.foe.mixin.accessor.BossHealthOverlayAccessor) mc.gui.getBossOverlay()).getEvents().values()) {
            now.append("BOSSBAR ").append(String.format(java.util.Locale.ROOT, "%.3f ", ev.getProgress())).append(describe(ev.getName())).append('\n');
        }
        String s = now.toString();
        if (s.isEmpty() || s.equals(last)) return;
        last = s;
        if (lines.size() > 4000) return;
        lines.add("@" + (System.currentTimeMillis() - startedAt) + "ms rod=" + (mc.player != null && mc.player.fishing != null) + "\n" + s);
    }

    public static String describe(Component c) {
        if (c == null) return "null";
        StringBuilder codes = new StringBuilder();
        c.getString().chars().forEach(ch -> codes.append(' ').append(Integer.toHexString(ch)));
        String json;
        try {
            json = ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, c).result().map(Object::toString).orElse("?");
        } catch (RuntimeException e) {
            json = "?";
        }
        // each part: its font, colour and codes ("fishing_2 #477BC3: e998 e2fb")
        StringBuilder parts = new StringBuilder();
        c.visit((style, text) -> {
            if (text.isEmpty()) return java.util.Optional.empty();
            parts.append("\n  part ").append(style.getFont() instanceof net.minecraft.network.chat.FontDescription.Resource r ? r.id().getPath() : "default")
                    .append(' ').append(style.getColor() == null ? "-" : style.getColor().serialize()).append(':');
            text.chars().forEach(ch -> parts.append(' ').append(Integer.toHexString(ch)));
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return c.getString() + "\n  codes:" + codes + parts + "\n  json: " + json;
    }
}
