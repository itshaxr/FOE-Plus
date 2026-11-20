package dannypx.foe.helper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * One look for every chat line FOE+ sends:
 * <pre>[FOE+] TAG  subject · detail · Dry streak 1,234</pre>
 * The prefix is aqua, the tag bold in the event's colour, details gray, numbers yellow, and
 * parts are split by a dark gray "·". Build a line with {@link #line(String, int)} and the
 * helpers, then {@link #send(Component)} it.
 */
public final class FoeChat {
    private FoeChat() {}

    public static final int GREEN = 0x55FF55;
    public static final int RED = 0xFF5555;
    public static final int YELLOW = 0xFFFF55;
    public static final int GOLD = 0xFFAA00;
    public static final int AQUA = 0x55FFFF;
    public static final int PURPLE = 0xFF55FF;

    /// "[FOE+] " in dark gray brackets.
    public static MutableComponent prefix() {
        return Component.empty()
                .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("FOE+").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
    }

    /// Starts a line: prefix plus a bold tag ("PET PULLED!", "WRONG PET"); no tag when null.
    public static MutableComponent line(String tag, int color) {
        MutableComponent out = prefix();
        if (tag != null && !tag.isEmpty()) out.append(Component.literal(tag + " ").withColor(color).withStyle(ChatFormatting.BOLD));
        return out;
    }

    /// A line whose tag sits beside a badge ("EVENT PET!" before the EPIC box): the tag in small caps, so it is
    /// no taller than the badge.
    public static MutableComponent badgeLine(String tag, int color) {
        MutableComponent out = prefix();
        if (tag != null && !tag.isEmpty())
            out.append(smallCapsWord(tag.toUpperCase(java.util.Locale.ROOT) + " ").withColor(color).withStyle(ChatFormatting.BOLD));
        return out;
    }

    /// FOE+'s small capitals font: plain capitals (A-Z) drawn as the game's small capitals, everything else as normal.
    public static final net.minecraft.network.chat.FontDescription SMALL_CAPS = new net.minecraft.network.chat.FontDescription.Resource(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(dannypx.foe.FishOnMCExtras.MOD_ID, "small_caps"));

    /// Text in small capitals that copies as plain letters ("QUEST PET!"): the server's chat drops the small
    /// capital characters ("ǫᴜᴇѕᴛ"), so a copied line pasted into chat would lose them.
    public static MutableComponent smallCapsWord(String text) {
        return Component.literal(text).withStyle(st -> st.withFont(SMALL_CAPS));
    }

    /// A pet's rating letter ("ʙ" from "ʙᴀᴅ", in its colour) as a plain capital in the small capitals font, so it
    /// looks the same and copies as "B".
    public static Component ratingLetter(Component rating) {
        String text = rating.getString();
        if (text.isEmpty()) return Component.empty();
        char c = Character.toUpperCase(TextHelper.normalLetter(text.charAt(0)));
        Component first = TextHelper.substring(rating, 0, 1);
        if (c < 'A' || c > 'Z') return first;
        net.minecraft.network.chat.Style style = first.getStyle();
        Component[] styled = {null};
        first.visit((st, t) -> { if (!t.isEmpty() && styled[0] == null) styled[0] = Component.literal(String.valueOf(c)).setStyle(st.withFont(SMALL_CAPS)); return java.util.Optional.empty(); }, style);
        return styled[0] != null ? styled[0] : Component.literal(String.valueOf(c)).setStyle(style.withFont(SMALL_CAPS));
    }

    public static Component sep() {
        return Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY);
    }

    public static Component text(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    public static Component white(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    public static Component number(long value) {
        return Component.literal(String.format(java.util.Locale.ROOT, "%,d", value)).withStyle(ChatFormatting.YELLOW);
    }

    public static Component highlight(String text) {
        return Component.literal(text).withStyle(ChatFormatting.YELLOW);
    }

    /// A location name in the server's colours for it (as in /tt).
    public static Component place(String text) {
        return FoeLocations.styledName(text);
    }

    /// " · Dry streak 1,234"
    public static Component dryStreak(long value) {
        return Component.empty().append(sep()).append(text("Dry streak ")).append(number(Math.max(0, value)));
    }

    /// A small picture of the item, drawn inside the chat line (from the item's own texture, so the
    /// server's resource pack fish show as they do in your inventory). Empty when there's no
    /// picture to show, e.g. before the item models are loaded.
    /// The GIGANTIC badge for chat lines, written as the word "GIGANTIC" in FOE+'s own font: it
    /// draws as the badge (the T is the badge, the C is a small gap after it, the other letters
    /// take no space), but a copied line reads "GIGANTIC".
    public static final Component GIGANTIC_WORD = Component.literal("GIGANTIC").withStyle(style -> style
            .withColor(ChatFormatting.WHITE)
            .withFont(new net.minecraft.network.chat.FontDescription.Resource(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(dannypx.foe.FishOnMCExtras.MOD_ID, "gigantic_word"))));

    public static Component itemIcon(net.minecraft.world.item.ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || stack == null || stack.isEmpty()) return Component.empty();
        try {
            var state = new net.minecraft.client.renderer.item.ItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(state, stack, net.minecraft.world.item.ItemDisplayContext.GUI, mc.level, mc.player, 0);
            if (state.isEmpty()) return Component.empty();
            var sprite = state.pickParticleIcon(net.minecraft.util.RandomSource.create(0L));
            if (sprite == null) return Component.empty();
            net.minecraft.resources.Identifier name = sprite.contents().name();
            if (name.equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation())) return Component.empty();
            // Chat names atlases by id ("minecraft:items"); the sprite knows its texture location.
            net.minecraft.resources.Identifier[] atlasId = {null};
            mc.getAtlasManager().forEach((id, atlas) -> {
                if (atlasId[0] == null && atlas.location().equals(sprite.atlasLocation())) atlasId[0] = id;
            });
            if (atlasId[0] == null) return Component.empty();
            return Component.empty()
                    .append(Component.object(new net.minecraft.network.chat.contents.objects.AtlasSprite(atlasId[0], name)))
                    .append(Component.literal(" "));
        } catch (RuntimeException e) {
            return Component.empty();
        }
    }

    public static void send(Component line) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) mc.player.displayClientMessage(line, false);
    }

    /// A plain status message: [FOE+] text (green for done, red for a problem, gray otherwise).
    public static void info(String text, int color) {
        send(prefix().append(Component.literal(text).withColor(color)));
    }
}
