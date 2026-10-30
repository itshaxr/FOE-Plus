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
    private static final int LUCK_COLOR = 0x80DAC3;
    private static final int SCALE_COLOR = 0x4C88F1;

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
            lines.add(statLine("♣ ʟᴜᴄᴋ ", "?", LUCK_COLOR));
            lines.add(statLine("⚓ ѕᴄᴀʟᴇ ", "?", SCALE_COLOR));
            lines.add(join(label("ᴏᴘᴇɴ "), Component.literal("/menu").withStyle(ChatFormatting.AQUA), label(" ᴛᴏ ʟᴏᴀᴅ")));
            return lines;
        }
        lines.add(statLine("♣ ʟᴜᴄᴋ ", profile.getLuck(), LUCK_COLOR));
        lines.add(statLine("⚓ ѕᴄᴀʟᴇ ", profile.getScale(), SCALE_COLOR));
        if (profile.isFromOtherLocation()) {
            lines.add(join(Component.literal("⚠ ").withStyle(ChatFormatting.YELLOW), label("ᴀʀᴇᴀ ᴄʜᴀɴɢᴇᴅ, ᴏᴘᴇɴ "),
                    Component.literal("/menu").withStyle(ChatFormatting.AQUA)));
        } else if (Configs.hudConfig.luckScalePanelBreakdown.get()) {
            lines.add(join(label("ᴜᴘᴅᴀᴛᴇᴅ "), Component.literal(age(System.currentTimeMillis() - profile.getReadAt())).withStyle(ChatFormatting.GREEN),
                    label(" ᴀɢᴏ ᴠɪᴀ "), Component.literal("/menu").withStyle(ChatFormatting.AQUA)));
        }
        return lines;
    }

    private static String age(long ms) {
        long s = ms / 1000L;
        if (s < 60) return s + "s";
        if (s < 3600) return (s / 60) + "m";
        return (s / 3600) + "h " + (s / 60) % 60 + "m";
    }

    private static Component statLine(String name, String value, int color) {
        return join(Component.literal(name).withColor(color),
                Component.literal(value == null ? "-" : value).withStyle(ChatFormatting.WHITE));
    }

    @Override
    protected List<Component> previewLines() {
        return List.of(statLine("♣ ʟᴜᴄᴋ ", "1,245", LUCK_COLOR), statLine("⚓ ѕᴄᴀʟᴇ ", "530", SCALE_COLOR),
                join(label("ᴜᴘᴅᴀᴛᴇᴅ "), Component.literal("2m").withStyle(ChatFormatting.GREEN), label(" ᴀɢᴏ ᴠɪᴀ "), Component.literal("/menu").withStyle(ChatFormatting.AQUA)));
    }
}
