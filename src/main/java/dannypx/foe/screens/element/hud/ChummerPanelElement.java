package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.ChummerHandler;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The chummer you're standing in the range of, in one small line like the bait panel: its picture, its rarity (in
 * the rarity's colour, the same as its badge) and the time it has left. Shown only while you're in its range. The idea is FOE's chummer HUD (FOECollab, AGPL-3.0); the time comes from the chummer's own sign
 * (read by FOE-R's ChummerHandler), so it's exact.
 */
public class ChummerPanelElement extends PanelElement {
    @Override public boolean usesBadges() { return true; }

    public ChummerPanelElement() {
        this(false);
    }

    public ChummerPanelElement(boolean isCopy) {
        super(dannypx.foe.helper.HudFine.get(Configs.hudConfig.chummerPanelXPosition),
                dannypx.foe.helper.HudFine.get(Configs.hudConfig.chummerPanelYPosition),
                Configs.hudConfig.chummerPanelAlignment.get(),
                Component.literal("Chummer Panel"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showChummerPanel.get(); }
    @Override protected float configX() { return dannypx.foe.helper.HudFine.get(Configs.hudConfig.chummerPanelXPosition); }
    @Override protected float configY() { return dannypx.foe.helper.HudFine.get(Configs.hudConfig.chummerPanelYPosition); }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.chummerPanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.chummerPanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.chummerPanelOpacity.get(); }
    @Override protected boolean alignTextToAnchor() { return false; }

    @Override
    protected Component title() {
        return Component.literal("ᴄʜᴜᴍᴍᴇʀ").withStyle(ChatFormatting.BOLD);
    }

    @Override
    protected List<Component> lines() {
        ChummerHandler handler = ChummerHandler.instance();
        ChummerHandler.ChummerInfo c = handler.inRange();
        if (c == null) return List.of();
        // with the top bar on, it's in the bar (between your name and the location) instead, when it fits there
        if (TopBarElement.shown() && TopBarElement.chummerInBar) return List.of();
        return List.of(line(c.getRarity(), c.remainingMs()));
    }

    @Override
    protected List<Component> previewLines() {
        return List.of(line("epic", 4 * 60_000L + 32_000L));
    }

    /// FOE+'s chummer bucket (tools/chummer_icon.py: the server's grey bucket with red chum, at twice its detail).
    public static final net.minecraft.resources.Identifier BUCKET = net.minecraft.resources.Identifier.fromNamespaceAndPath("foeplus", "textures/gui/chummer_bucket.png");

    /// "[bucket] [EPIC] 4:32": small, like the bait panel. The chummer bucket, its rarity's badge and the time left
    /// (green, then yellow under two minutes, red under one).
    static Component line(String rarityText, long msLeft) {
        return join(inlinePicture(BUCKET), Component.literal(" "), text(rarityText, msLeft));
    }

    /// The server's rarity badges, as on its items.
    static final java.util.Map<String, String> BADGES = java.util.Map.of("common", "\uF033", "rare", "\uF034",
            "epic", "\uF035", "legendary", "\uF036", "mythical", "\uF037");

    /// The server's badge character for a rarity ("Epic" → its EPIC box), or null.
    public static String badgeGlyph(String rarityText) {
        return BADGES.get(PetPanelElement.rarityFromText(rarityText));
    }

    /// The rarity's badge (the same box the server puts on its items), or null for a rarity it has none for.
    static Component badge(String rarityText) {
        String glyph = BADGES.get(PetPanelElement.rarityFromText(rarityText));
        return glyph == null ? null : Component.literal(glyph).withStyle(ChatFormatting.WHITE);
    }

    /// The words of {@link #line}, without the picture. Package-private for tests.
    static Component text(String rarityText, long msLeft) {
        Component badge = badge(rarityText);
        Component name = badge != null ? badge : Component.literal("ᴄʜᴜᴍᴍᴇʀ").withStyle(ChatFormatting.GRAY);
        long s = (msLeft + 999) / 1000;
        ChatFormatting timeColor = s > 120 ? ChatFormatting.GREEN : s > 60 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        return join(name, Component.literal(" "),
                Component.literal(String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60)).withStyle(timeColor));
    }
}
