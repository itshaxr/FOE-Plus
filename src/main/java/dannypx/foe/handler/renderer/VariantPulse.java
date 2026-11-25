package dannypx.foe.handler.renderer;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.LoggerHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Variant pulse: when an albino, melanistic, trophy or fabled fish bites your line, the server shows its badge over
 * your bobber for a moment (a text display). From then until the fish is caught or the line comes in, the screen's
 * edges pulse in the variant's colour. A badge counts only when it's at your own bobber, nearer to it than to anyone
 * else's, so a variant on the line next to yours never sets it off. Settings: HUD → Variant Pulse.
 */
public final class VariantPulse {
    private VariantPulse() {}

    public enum Variant {
        ALBINO(0xFFFFFF), MELANISTIC(0x000000), TROPHY(0xFFC93C), FABLED(0xFC3E35);

        public final int rgb;

        Variant(int rgb) { this.rgb = rgb; }
    }

    /// How far (blocks, sideways) a badge may be from your bobber, and how long a pulse is.
    static final double NEAR = 2.5;
    static final long PULSE_MS = 1100, MAX_MS = 90_000, END_FADE_MS = 400;

    private static Variant on;
    private static long since, endedAt;
    private static int hookId = -1;
    private static final Set<Integer> seen = new HashSet<>();

    /// Which variant a badge's text shows, or null: the server's badge glyphs, or the word itself.
    static Variant of(String text) {
        if (text == null) return null;
        if (text.indexOf('') >= 0) return Variant.ALBINO;
        if (text.indexOf('') >= 0) return Variant.MELANISTIC;
        if (text.indexOf('') >= 0) return Variant.TROPHY;
        if (text.indexOf('') >= 0) return Variant.FABLED;
        String plain = ChatFormatting.stripFormatting(text);
        if (plain == null) return null;
        plain = plain.trim().toLowerCase(Locale.ROOT);
        // the badge alone, never a sentence (a name tag or chat-like display that mentions a variant)
        if (plain.length() > 14) return null;
        if (plain.equals("albino") || plain.equals("ᴀʟʙɪɴᴏ")) return Variant.ALBINO;
        if (plain.equals("melanistic") || plain.equals("ᴍᴇʟᴀɴɪѕᴛɪᴄ") || plain.equals("ᴍᴇʟᴀɴɪꜱᴛɪᴄ")) return Variant.MELANISTIC;
        if (plain.equals("trophy") || plain.equals("ᴛʀᴏᴘʜʏ")) return Variant.TROPHY;
        if (plain.equals("fabled") || plain.equals("ꜰᴀʙʟᴇᴅ")) return Variant.FABLED;
        return null;
    }

    static boolean wanted(Variant v) {
        var c = Configs.hudConfig;
        return switch (v) {
            case ALBINO -> c.variantPulseAlbino.get();
            case MELANISTIC -> c.variantPulseMelanistic.get();
            case TROPHY -> c.variantPulseTrophy.get();
            case FABLED -> c.variantPulseFabled.get();
        };
    }

    /// A text display's text changed (the server shows a badge with one).
    public static void onTextUpdate(Display.TextDisplay display, Component text) {
        if (text == null) return;
        Variant v = of(text.getString());
        if (v == null) logUnknown(display, text.getString());
        if (v == null || !seen.add(display.getId())) return;
        Minecraft mc = Minecraft.getInstance();
        FishingHook mine = mc.player == null ? null : mc.player.fishing;
        if (mine == null || mc.level == null) return;
        double mineD = flat(display, mine);
        if (mineD > NEAR * NEAR) return;
        // someone else's bobber nearer to the badge than yours: their fish
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof FishingHook other && other != mine && flat(display, other) < mineD) return;
        }
        String kind = v.name().toLowerCase(Locale.ROOT);
        LoggerHandler.info("Variant pulse: " + kind + " on your line");
        // its sound (Sounds → Catches: Albino, Melanistic, Trophy, Fabled catch), once for this fish
        if (soundedHook != mine.getId()) {
            soundedHook = mine.getId();
            soundedKind = kind.equals("fabled") ? "fabled" : kind;
            soundedAt = System.currentTimeMillis();
            dannypx.foe.helper.Safe.run("variant sound", () -> dannypx.foe.handler.store.NotifySounds.playVariant(soundedKind));
        }
        if (!Configs.hudConfig.variantPulseOn.get() || !wanted(v)) return;
        on = v;
        since = System.currentTimeMillis();
        endedAt = 0;
        hookId = mine.getId();
    }

    private static int soundedHook = -1;
    private static String soundedKind = "";
    private static long soundedAt = 0L;

    /// Whether this variant's sound already played while the fish was on your line (so the catch doesn't play it
    /// again).
    public static boolean soundedOnLine(String variant) {
        return variant != null && variant.equalsIgnoreCase(soundedKind) && System.currentTimeMillis() - soundedAt < 120_000L;
    }

    private static long loggedAt;

    /// A display at your bobber whose text isn't a known badge: written to the log (at most every 2 s), so a badge the
    /// server draws another way can be added.
    private static void logUnknown(Display.TextDisplay display, String text) {
        Minecraft mc = Minecraft.getInstance();
        FishingHook mine = mc.player == null ? null : mc.player.fishing;
        long now = System.currentTimeMillis();
        if (mine == null || text == null || text.isBlank() || flat(display, mine) > NEAR * NEAR || now - loggedAt < 2000) return;
        loggedAt = now;
        StringBuilder cps = new StringBuilder();
        text.codePoints().limit(24).forEach(cp -> cps.append(String.format(Locale.ROOT, "U+%04X ", cp)));
        LoggerHandler.info("Variant pulse: text at your bobber not known: " + cps.toString().trim());
    }

    private static double flat(Entity a, Entity b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /// The pulse while it lasts: ends (fading) once your line comes in or the fish is caught.
    public static void render(GuiGraphics g) {
        if (on == null) return;
        var c = Configs.hudConfig;
        long now = System.currentTimeMillis();
        if (!c.variantPulseOn.get() || now - since > MAX_MS) { stop(); return; }
        Minecraft mc = Minecraft.getInstance();
        FishingHook mine = mc.player == null ? null : mc.player.fishing;
        if (endedAt == 0 && (mine == null || mine.getId() != hookId)) endedAt = now;
        float peak = c.variantPulseOpacity.get() / 100f;
        long t = now - since;
        float a;
        if (!c.variantPulseKeep.get()) {
            // one pulse
            if (t >= PULSE_MS) { stop(); return; }
            float s = (float) Math.sin(Math.PI * t / (double) PULSE_MS);
            a = s * s;
        } else {
            a = wave(t % PULSE_MS);
            if (endedAt != 0) {
                float left = 1 - (now - endedAt) / (float) END_FADE_MS;
                if (left <= 0) { stop(); return; }
                a *= left;
            }
        }
        BigCatchMoment.edgeGlow(g, Math.round(a * peak * 255), on.rgb);
    }

    /// One pulse: up smoothly, then down; never fully gone in between, so it reads as a heartbeat, not a flash.
    static float wave(long t) {
        float x = t / (float) PULSE_MS;
        float s = (float) Math.sin(Math.PI * x);
        return 0.25f + 0.75f * s * s;
    }

    /// The fish was caught.
    public static void onCatch() {
        if (on != null && endedAt == 0) endedAt = System.currentTimeMillis();
    }

    private static void stop() {
        on = null;
        endedAt = 0;
        hookId = -1;
        if (seen.size() > 256) seen.clear();
    }
}
