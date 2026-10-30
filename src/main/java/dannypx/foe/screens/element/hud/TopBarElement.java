package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.BossEventHandler;
import dannypx.foe.handler.fetch.ScoreboardHandler;
import dannypx.foe.handler.fetch.TabOverlayHandler;
import dannypx.foe.handler.logic.LoadingHandler;
import dannypx.foe.screens.element.Element;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * FOE's always-on top bar: name, level and level progress on the left; location, catch range,
 * climate, weather and time in the middle; balance, credits and catches on the right.
 * Ported from FOECollab's BarHud / BarHudHandler (AGPL-3.0); the data comes from FOE-R's
 * scoreboard, tab and boss bar readers.
 */
public class TopBarElement extends Element {
    private static final int PADDING = 8;

    /// Climate per location (from FOECollab's LocationInfo), drawn as a two-colour gradient.
    private static final Map<String, Climate> CLIMATES = Map.ofEntries(
            Map.entry("cypresslake", Climate.SUBTROPICAL),
            Map.entry("spawn", Climate.SUBTROPICAL),
            Map.entry("kenairiver", Climate.SUBARCTIC),
            Map.entry("lakebiwa", Climate.SUBTROPICAL),
            Map.entry("murrayriver", Climate.SEMI_ARID),
            Map.entry("everglades", Climate.SAVANNA),
            Map.entry("keywest", Climate.SAVANNA),
            Map.entry("toledobend", Climate.SUBTROPICAL),
            Map.entry("greatlakes", Climate.CONTINENTAL),
            Map.entry("danuberiver", Climate.CONTINENTAL),
            Map.entry("oilrig", Climate.SAVANNA),
            Map.entry("amazonriver", Climate.RAINFOREST),
            Map.entry("mediterraneansea", Climate.MEDITERRANEAN),
            Map.entry("capecod", Climate.OCEANIC),
            Map.entry("hawaii", Climate.SAVANNA),
            Map.entry("cairns", Climate.MONSOON),
            Map.entry("lofotenislands", Climate.SUBARCTIC),
            Map.entry("nileriver", Climate.ARID));

    public TopBarElement() {
        super(0, 0, 0, 0, Alignment.TOP_LEFT, Component.literal("Top Bar"), false);
    }

    /// Height of the bar in GUI pixels, or 0 when it isn't shown. Top-anchored panels move below it.
    public static int barHeight() {
        if (!Configs.hudConfig.showTopBar.get() || !LoadingHandler.instance().isLoadingDone()) return 0;
        Font font = Minecraft.getInstance().font;
        if (font == null) return 0;
        return Math.round((PADDING * 2 + font.lineHeight) * Configs.hudConfig.topBarScale.get());
    }

    @Override
    public void render(GuiGraphics guiGraphics) {
        if (!Configs.hudConfig.showTopBar.get() || !LoadingHandler.instance().isLoadingDone()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float scale = Configs.hudConfig.topBarScale.get();
        int screenWidth = Math.round(mc.getWindow().getGuiScaledWidth() / scale);

        Component left = leftText();
        Component middle = middleText();
        Component right = rightText();

        int alpha = Math.round(Configs.hudConfig.topBarOpacity.get() / 100f * 255f) << 24;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(scale, scale);
        guiGraphics.fill(0, 0, screenWidth, PADDING * 2 + font.lineHeight, alpha);
        guiGraphics.drawString(font, left, PADDING, PADDING, 0xFFFFFFFF, true);
        guiGraphics.drawString(font, middle, screenWidth / 2 - font.width(middle) / 2, PADDING, 0xFFFFFFFF, true);
        guiGraphics.drawString(font, right, screenWidth - font.width(right) - PADDING, PADDING, 0xFFFFFFFF, true);
        guiGraphics.pose().popMatrix();
    }

    private static Component leftText() {
        Minecraft mc = Minecraft.getInstance();
        float progress = mc.player != null ? mc.player.experienceProgress : 0f;
        int filled = Math.round(progress * 20);

        return Component.empty()
                .append(TabOverlayHandler.instance().getPlayerName())
                .append(Component.literal(" [").withStyle(ChatFormatting.DARK_GRAY))
                .append(ScoreboardHandler.instance().getLevel().copy().withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" ".repeat(filled)).withStyle(ChatFormatting.GREEN, ChatFormatting.STRIKETHROUGH))
                .append(Component.literal(" ".repeat(20 - filled)).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH))
                .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(Math.round(progress * 100) + "%").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component middleText() {
        BossEventHandler boss = BossEventHandler.instance();
        Component location = boss.getLocation();
        MutableComponent text = Component.literal(" ").withStyle(ChatFormatting.WHITE).append(location);

        String min = ScoreboardHandler.instance().getLocationMin().getString().trim();
        String max = ScoreboardHandler.instance().getLocationMax().getString().trim();
        if (!min.isEmpty() && !max.isEmpty() && !Objects.equals(min, max)) {
            text.append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(min).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("/").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(max).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
        }

        if (Configs.hudConfig.topBarShowClimate.get()) {
            Climate climate = CLIMATES.get(location.getString().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", ""));
            if (climate != null) {
                text.append(Component.literal(" [").withStyle(ChatFormatting.DARK_GRAY))
                        .append(climate.tag())
                        .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        String time = boss.getTime().getString().trim();
        if (!time.isEmpty()) {
            text.append(Component.literal(" ")).append(boss.getWeather())
                    .append(Component.literal(" ")).append(boss.getTime());
        }
        return text;
    }

    private static Component rightText() {
        ScoreboardHandler scoreboard = ScoreboardHandler.instance();
        return Component.empty()
                .append(Component.literal("ʙᴀʟᴀɴᴄᴇ: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("$").withStyle(ChatFormatting.DARK_GREEN))
                // The scoreboard's wallet already starts with its own "$"; keep only ours.
                .append(Component.literal(scoreboard.getWallet().getString().trim().replaceFirst("^\\$\\s*", "")).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(scoreboard.getCredits().getString().trim()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("    "))
                .append(Component.literal("ᴄᴀᴛᴄʜᴇѕ: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(scoreboard.getCatches().getString().trim()).withStyle(ChatFormatting.RED))
                .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(scoreboard.getCatchRate().getString().trim()).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
    }

    private enum Climate {
        SUBTROPICAL("Subtropical", 0x4FB07A, 0x47F7D6),
        SUBARCTIC("Subarctic", 0x53A1C1, 0x98B7CA),
        SEMI_ARID("Semi-Arid", 0xE6902E, 0xE4B76D),
        SAVANNA("Savanna", 0xBAC153, 0xE2DD7E),
        CONTINENTAL("Continental", 0xA4A9AB, 0xE5EBE8),
        RAINFOREST("Rainforest", 0x569579, 0x2ADD8E),
        MEDITERRANEAN("Mediterranean", 0x80C4EF, 0xB0D8F3),
        OCEANIC("Oceanic", 0x397FAC, 0x319CE3),
        MONSOON("Monsoon", 0x6141DF, 0x7B93E2),
        ARID("Arid", 0xFD3535, 0xC12D2D);

        private final String name;
        private final int from;
        private final int to;

        Climate(String name, int from, int to) {
            this.name = name;
            this.from = from;
            this.to = to;
        }

        Component tag() {
            MutableComponent out = Component.empty();
            int n = name.length();
            for (int i = 0; i < n; i++) {
                float t = n == 1 ? 0 : (float) i / (n - 1);
                out.append(Component.literal(String.valueOf(name.charAt(i))).withColor(lerp(from, to, t)));
            }
            return out;
        }

        private static int lerp(int a, int b, float t) {
            int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
            int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
            int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
            return (r << 16) | (g << 8) | bl;
        }
    }
}
