package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.ProfileStatsHandler;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Your current Luck and Scale, in FOE's panel style, exactly as the server shows them on your
 * profile in /menu, so boosters, events and gear are all included. The numbers update every
 * time you open /menu; the panel shows how old they are, and asks you to reopen /menu after you
 * move to another area (the numbers depend on where you fish).
 */
public class LuckScalePanelElement extends PanelElement {
    public static final int LUCK_COLOR = 0x80DAC3;
    public static final int SCALE_COLOR = 0x4C88F1;

    public LuckScalePanelElement() {
        this(false);
    }

    public LuckScalePanelElement(boolean isCopy) {
        super(Configs.hudConfig.luckScalePanelXPosition.get() / 100f,
                Configs.hudConfig.luckScalePanelYPosition.get() / 100f,
                Configs.hudConfig.luckScalePanelAlignment.get(),
                Component.literal("Luck & Scale Panel"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showLuckScalePanel.get(); }
    @Override protected float configX() { return Configs.hudConfig.luckScalePanelXPosition.get() / 100f; }
    @Override protected float configY() { return Configs.hudConfig.luckScalePanelYPosition.get() / 100f; }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.luckScalePanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.luckScalePanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.luckScalePanelOpacity.get(); }

    @Override
    protected boolean alignTextToAnchor() {
        return false;
    }

    @Override
    protected Component title() {
        return Component.literal("ʟᴜᴄᴋ & ѕᴄᴀʟᴇ").withStyle(ChatFormatting.BOLD);
    }

    @Override
    protected List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        if (Minecraft.getInstance().player == null) return lines;
        ProfileStatsHandler profile = ProfileStatsHandler.instance();
        if (!profile.hasData()) {
            lines.add(luckLine("ʟᴜᴄᴋ ", "?"));
            lines.add(scaleLine("ѕᴄᴀʟᴇ ", "?"));
            lines.add(join(label("ᴏᴘᴇɴ "), Component.literal("/menu").withStyle(ChatFormatting.AQUA), label(" ᴛᴏ ʟᴏᴀᴅ")));
            return lines;
        }
        lines.add(withBooster(luckLine("ʟᴜᴄᴋ ", profile.getLuck()), true));
        lines.add(withBooster(scaleLine("ѕᴄᴀʟᴇ ", profile.getScale()), false));
        if (profile.isFromOtherLocation()) {
            lines.add(join(Component.literal("⚠ ").withStyle(ChatFormatting.YELLOW), label("ᴀʀᴇᴀ ᴄʜᴀɴɢᴇᴅ, ᴏᴘᴇɴ "),
                    Component.literal("/menu").withStyle(ChatFormatting.AQUA)));
        } else if (Configs.hudConfig.luckScalePanelBreakdown.get()) {
            lines.add(join(label("ᴜᴘᴅᴀᴛᴇᴅ "), Component.literal(age(System.currentTimeMillis() - profile.getReadAt())).withStyle(ChatFormatting.GREEN),
                    label(" ᴀɢᴏ ᴠɪᴀ "), Component.literal("/menu").withStyle(ChatFormatting.AQUA)));
        }
        return lines;
    }

    /// "♣ Luck 1,245 ⚡ 23:10": time left on a global booster, counting down live.
    private static Component withBooster(Component line, boolean luck) {
        long left = dannypx.foe.handler.logic.StoreTimerHandler.instance().boosterRemainingMs(luck);
        if (left <= 0) return line;
        return join(line, Component.literal(" ⚡ ").withStyle(ChatFormatting.YELLOW),
                Component.literal(dannypx.foe.handler.logic.StoreTimerHandler.clock(left)).withStyle(ChatFormatting.YELLOW));
    }

    private static String age(long ms) {
        long s = ms / 1000L;
        if (s < 60) return s + "s";
        if (s < 3600) return (s / 60) + "m";
        return (s / 3600) + "h " + (s / 60) % 60 + "m";
    }

    /// Green for Luck with the green clover setting on (the game's ♣ in green, as on the pet panel).
    public static final int GREEN_LUCK_COLOR = 0x55FF55;

    static boolean greenClover() {
        try {
            return Configs.hudConfig.luckScaleGreenLuck.get();
        } catch (Throwable e) {
            return false;
        }
    }

    /// "♣ ʟᴜᴄᴋ 729": the game's ♣ in the Luck colour (or green, with the luck in green too, with that setting on).
    private static Component luckLine(String name, String value) {
        boolean green = greenClover();
        return statLine(dannypx.foe.helper.NameTagMarker.club(green ? GREEN_LUCK_COLOR : LUCK_COLOR),
                name, value, green ? GREEN_LUCK_COLOR : LUCK_COLOR);
    }

    private static Component scaleLine(String name, String value) {
        return statLine(dannypx.foe.helper.NameTagMarker.anchor(SCALE_COLOR), name, value, SCALE_COLOR);
    }

    /// The icon in a column as wide as the widest icon, so "ʟᴜᴄᴋ" and "ѕᴄᴀʟᴇ" start at the same
    /// place (the icons aren't the same width).
    private static Component statLine(Component icon, String name, String value, int color) {
        int col = dannypx.foe.helper.NameTagMarker.widest(dannypx.foe.helper.NameTagMarker.club(GREEN_LUCK_COLOR),
                dannypx.foe.helper.NameTagMarker.anchor(SCALE_COLOR));
        return join(dannypx.foe.helper.NameTagMarker.padIcon(icon, col),
                Component.literal(" " + name).withColor(color),
                Component.literal(value == null ? "-" : value).withStyle(ChatFormatting.WHITE));
    }

    @Override
    protected List<Component> previewLines() {
        return List.of(luckLine("ʟᴜᴄᴋ ", "1,245"), scaleLine("ѕᴄᴀʟᴇ ", "530"),
                join(label("ᴜᴘᴅᴀᴛᴇᴅ "), Component.literal("2m").withStyle(ChatFormatting.GREEN), label(" ᴀɢᴏ ᴠɪᴀ "), Component.literal("/menu").withStyle(ChatFormatting.AQUA)));
    }
}
