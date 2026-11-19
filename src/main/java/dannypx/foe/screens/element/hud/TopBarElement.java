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
    /// The least space kept between the middle and either side.
    private static final int SIDE_GAP = 12;
    /// What's shown at each step down: level bar (1/0), right side level (0 all, 1 no TPS/ping,
    /// 2 no labels either), middle level (see [#middleText]).
    private static final int[][] STEPS = {
            {1, 0, 0}, {1, 0, 1}, {1, 0, 2}, {0, 0, 2}, {0, 1, 2}, {0, 2, 2}, {0, 2, 3}};

    /// `text` cut to `width`, ending in "…", keeping its colours.
    static Component cut(Font font, Component text, int width) {
        if (font.width(text) <= width) return text;
        int dots = font.width("…");
        net.minecraft.network.chat.FormattedText kept = font.substrByWidth(text, Math.max(0, width - dots));
        MutableComponent out = Component.empty();
        kept.visit((style, str) -> { out.append(Component.literal(str).withStyle(style)); return java.util.Optional.empty(); },
                net.minecraft.network.chat.Style.EMPTY);
        return out.append(Component.literal("…").withStyle(ChatFormatting.DARK_GRAY));
    }

    /// Climate per location (from FOECollab's LocationInfo), drawn as a two-colour gradient.
    static final Map<String, Climate> CLIMATES = Map.ofEntries(
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

    /// The bar is on: switched on, and the HUD Style isn't FOE-R (whose profile, pet and location boxes take its place).
    public static boolean shown() {
        return Configs.hudConfig.showTopBar.get() && !FoeRBoxes.on();
    }

    /// Height of the bar in GUI pixels, or 0 when it isn't shown. Top-anchored panels move below it.
    public static int barHeight() {
        if (!shown() || !LoadingHandler.instance().isLoadingDone()) return 0;
        Font font = Minecraft.getInstance().font;
        if (font == null) return 0;
        return Math.round((PADDING * 2 + font.lineHeight) * dannypx.foe.utils.HudScale.crisp(Configs.hudConfig.topBarScale.get()));
    }

    @Override
    public void render(GuiGraphics guiGraphics) {
        if (!shown() || !LoadingHandler.instance().isLoadingDone()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float scale = dannypx.foe.utils.HudScale.crisp(Configs.hudConfig.topBarScale.get());
        int screenWidth = Math.round(mc.getWindow().getGuiScaledWidth() / scale);

        int alpha = Math.round(Configs.hudConfig.topBarOpacity.get() / 100f * 255f) << 24;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(scale, scale);
        drawBar(guiGraphics, font, screenWidth, alpha, TopBarElement::leftText, TopBarElement::middleText, TopBarElement::rightText, communityText());
        guiGraphics.pose().popMatrix();
    }

    /// Lays the bar out across `screenWidth` and draws it. The left comes with or without its level
    /// bar, the right in levels 0-2 (see [#rightText]) and the middle in levels 0-3 (see
    /// [#middleText]); the shorter ones are only built when the fuller ones don't fit.
    public static void drawBar(GuiGraphics guiGraphics, Font font, int screenWidth, int alpha,
                               java.util.function.Function<Boolean, Component> lefts,
                               java.util.function.IntFunction<Component> middles,
                               java.util.function.IntFunction<Component> rights, Component community0) {
        // On a narrow screen (a laptop, a big GUI scale) things give way one at a time, the least
        // missed first, until the middle fits between the sides: the catch range, the weather and
        // time, the level bar, the TPS/ping, the "Balance:"/"Catches:" words, then the spot. If even
        // the place alone doesn't fit, it's cut short with "…". Nothing ever draws over anything else.
        Component left = null, right = null, middle = null;
        int leftEnd = 0, rightStart = 0;
        for (int[] step : STEPS) {
            left = lefts.apply(step[0] == 1);
            right = rights.apply(step[1]);
            leftEnd = PADDING + font.width(left);
            rightStart = screenWidth - PADDING - font.width(right);
            Component m = middles.apply(step[2]);
            if (font.width(m) <= rightStart - leftEnd - SIDE_GAP * 2) { middle = m; break; }
        }
        if (middle == null) middle = cut(font, middles.apply(3), Math.max(0, rightStart - leftEnd - SIDE_GAP * 2));
        int middleW = font.width(middle);
        int middleX = screenWidth / 2 - middleW / 2;
        middleX = Math.max(middleX, leftEnd + SIDE_GAP);
        middleX = Math.min(middleX, rightStart - SIDE_GAP - middleW);
        // The chummer you're in the range of goes between your name and the location (when it fits there).
        dannypx.foe.handler.fetch.ChummerHandler.ChummerInfo chummer = Configs.hudConfig.showChummerPanel.get()
                ? dannypx.foe.handler.fetch.ChummerHandler.instance().inRange() : null;
        int chummerX = -1, chummerW = chummer == null ? 0 : chummerWidth(font, chummer);
        if (chummer != null && middleX - leftEnd >= chummerW + PADDING * 2) chummerX = leftEnd + (middleX - leftEnd - chummerW) / 2;
        chummerInBar = chummerX >= 0;
        // The community event goes in the bigger gap: after the level bar or before the balance (only the right one
        // when the chummer's in the left). If neither gap fits it, it's left out, so it never runs into the location
        // in the middle.
        Component community = community0;
        int communityX = -1;
        if (community != null) {
            int gap = PADDING;
            int w = font.width(community);
            int middleLeft = middleX;
            int middleRight = middleX + middleW;
            int leftRoom = chummerInBar ? -1 : middleLeft - leftEnd;
            int rightRoom = rightStart - middleRight;
            if (leftRoom >= rightRoom && leftRoom >= w + gap * 2) communityX = leftEnd + (leftRoom - w) / 2;
            else if (rightRoom >= w + gap * 2) communityX = middleRight + (rightRoom - w) / 2;
        }

        guiGraphics.fill(0, 0, screenWidth, PADDING * 2 + font.lineHeight, alpha);
        guiGraphics.drawString(font, left, PADDING, PADDING, 0xFFFFFFFF, true);
        guiGraphics.drawString(font, middle, middleX, PADDING, 0xFFFFFFFF, true);
        guiGraphics.drawString(font, right, screenWidth - font.width(right) - PADDING, PADDING, 0xFFFFFFFF, true);
        if (communityX >= 0) guiGraphics.drawString(font, community, communityX, PADDING, 0xFFFFFFFF, true);
        if (chummerX >= 0) drawChummer(guiGraphics, font, chummer, chummerX);
    }

    /// The chummer was drawn in the bar this frame (so its own panel stays hidden).
    static boolean chummerInBar = false;

    /// The chummer's picture's size in the bar, and the gap before its time.
    private static final int CHUMMER_ICON = 12, CHUMMER_GAP = 2;

    /// The rarity's badge (the server's own box), or "" without a rarity.
    private static Component chummerRarity(dannypx.foe.handler.fetch.ChummerHandler.ChummerInfo c) {
        Component badge = ChummerPanelElement.badge(c.getRarity());
        return badge == null ? Component.empty() : badge;
    }

    private static Component chummerTime(dannypx.foe.handler.fetch.ChummerHandler.ChummerInfo c) {
        long s = (c.remainingMs() + 999) / 1000;
        ChatFormatting color = s > 120 ? ChatFormatting.GREEN : s > 60 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        return Component.literal(String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)).withStyle(color);
    }

    private static int chummerWidth(Font font, dannypx.foe.handler.fetch.ChummerHandler.ChummerInfo c) {
        return Math.max(CHUMMER_ICON, font.width(chummerRarity(c))) + CHUMMER_GAP + font.width(chummerTime(c));
    }

    /// The chummer at `x`: its rarity's badge above its picture, the time left beside the picture.
    private static void drawChummer(GuiGraphics g, Font font, dannypx.foe.handler.fetch.ChummerHandler.ChummerInfo c, int x) {
        Component rarity = chummerRarity(c);
        int column = Math.max(CHUMMER_ICON, font.width(rarity));
        int iconY = PADDING + 3;
        PanelElement.drawText(g, font, rarity.getVisualOrderText(), x + (column - font.width(rarity)) / 2, 2);
        g.pose().pushMatrix();
        g.pose().translate(x + (column - CHUMMER_ICON) / 2f, iconY);
        g.pose().scale(CHUMMER_ICON / 16f, CHUMMER_ICON / 16f);
        g.renderItem(dannypx.foe.handler.fetch.ChummerHandler.instance().icon(c), 0, 0);
        g.pose().popMatrix();
        g.drawString(font, chummerTime(c), x + column + CHUMMER_GAP, PADDING, 0xFFFFFFFF, true);
    }

    /// Name and level, and the level progress bar when `withBar`; each piece can be turned off.
    private static Component leftText(boolean withBar) {
        var cfg = Configs.hudConfig;
        Minecraft mc = Minecraft.getInstance();
        float progress = mc.player != null ? mc.player.experienceProgress : 0f;
        int filled = Math.round(progress * 20);

        MutableComponent out = Component.empty();
        if (cfg.topBarShowName.get()) out.append(TabOverlayHandler.instance().getPlayerName());
        if (cfg.topBarShowLevel.get()) {
            if (!out.getSiblings().isEmpty()) out.append(Component.literal(" "));
            // in the colours your level has in chat (a gradient at high levels), once one of your
            // messages has been seen; until then the old purple
            String lv = ScoreboardHandler.instance().getLevel().getString().trim();
            Component chatColours = dannypx.foe.handler.logic.LevelStyleHandler.instance().level(lv);
            if (chatColours != null) out.append(chatColours);
            else out.append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                    .append(ScoreboardHandler.instance().getLevel().copy().withStyle(ChatFormatting.LIGHT_PURPLE))
                    .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!withBar || !cfg.topBarShowLevelBar.get()) return out;
        if (!out.getSiblings().isEmpty()) out.append(Component.literal(" "));
        return out
                .append(GameXpBar.gameStyle() ? GameXpBar.bar(progress, 20) : Component.empty()
                        .append(Component.literal(" ".repeat(filled)).withStyle(ChatFormatting.GREEN, ChatFormatting.STRIKETHROUGH))
                        .append(Component.literal(" ".repeat(20 - filled)).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH)))
                .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(Math.round(progress * 100) + "%").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
    }

    /// The middle, from everything (`drop` 0) to the least (`drop` 3): 1 drops the catch range and
    /// climate, 2 also the weather and time, 3 also the spot. Each piece can be turned off too.
    private static Component middleText(int drop) {
        var cfg = Configs.hudConfig;
        BossEventHandler boss = BossEventHandler.instance();
        Component location = boss.getLocation();
        MutableComponent text = Component.empty().withStyle(ChatFormatting.WHITE);
        // in the place's own colour, as the travel map shows it
        if (cfg.topBarShowLocation.get()) {
            String place = location.getString().replaceAll("[\\uE000-\\uF8FF]", "").trim();
            text.append(place.isEmpty() ? location : dannypx.foe.helper.FoeLocations.styledName(place));
        }
        // The spot you're at within the location (Big Bear Dock, Thunderwood Marina...), from the
        // server's second boss bar; "Spawn" in Cypress Lake's hub before the water.
        String spot = boss.getSubLocation().getString().replaceAll("[\\uE000-\\uF8FF]", "").trim();
        // the server no longer says on most places: the spot FOE+ learned you're at (see SpotHandler)
        String learnedSpot = spot.isEmpty() ? dannypx.foe.handler.logic.SpotHandler.instance().current() : null;
        Component spotText = null;
        if (learnedSpot != null) spotText = Component.literal(learnedSpot).withStyle(ChatFormatting.GRAY);
        else if (!spot.isEmpty()) spotText = Component.literal(spot).withStyle(boss.getSubLocation().getStyle().getColor() == null
                ? net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.GRAY) : boss.getSubLocation().getStyle());
        else if (dannypx.foe.handler.logic.PetLocationHandler.inCypressHub()) spotText = Component.literal("Spawn").withStyle(ChatFormatting.GRAY);
        if (drop < 3 && spotText != null && cfg.topBarShowSpot.get()) {
            if (!text.getSiblings().isEmpty()) text.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            text.append(spotText);
        }

        String min = ScoreboardHandler.instance().getLocationMin().getString().trim();
        String max = ScoreboardHandler.instance().getLocationMax().getString().trim();
        if (drop < 1 && cfg.topBarShowRange.get() && !min.isEmpty() && !max.isEmpty() && !Objects.equals(min, max)) {
            if (!text.getSiblings().isEmpty()) text.append(Component.literal(" "));
            text.append(Component.literal("(").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(min).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("/").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(max).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
        }

        // the spot takes the climate's place, keeping the bar short
        boolean atSpot = spotText != null && cfg.topBarShowSpot.get() && !"Spawn".equals(spotText.getString());
        if (drop < 1 && cfg.topBarShowClimate.get() && !atSpot) {
            Climate climate = CLIMATES.get(location.getString().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", ""));
            if (climate != null) {
                if (!text.getSiblings().isEmpty()) text.append(Component.literal(" "));
                text.append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                        .append(climate.tag())
                        .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        String time = boss.getTime().getString().trim();
        if (drop < 2 && !time.isEmpty()) {
            if (cfg.topBarShowWeather.get()) {
                if (!text.getSiblings().isEmpty()) text.append(Component.literal(" "));
                text.append(boss.getWeather());
            }
            if (cfg.topBarShowTime.get()) {
                if (!text.getSiblings().isEmpty()) text.append(Component.literal(" "));
                text.append(dannypx.foe.helper.SmoothClock.topBar(boss.getTime()));
            }
        }
        return text;
    }

    /// The spot you're at within the location (Big Bear Dock...), as the middle shows it, or null: for the FOE-R
    /// style's location box.
    static Component spot() {
        BossEventHandler boss = BossEventHandler.instance();
        String spot = boss.getSubLocation().getString().replaceAll("[\\uE000-\\uF8FF]", "").trim();
        String learnedSpot = spot.isEmpty() ? dannypx.foe.handler.logic.SpotHandler.instance().current() : null;
        if (learnedSpot != null) return Component.literal(learnedSpot).withStyle(ChatFormatting.GRAY);
        if (!spot.isEmpty()) return Component.literal(spot).withStyle(boss.getSubLocation().getStyle().getColor() == null
                ? net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.GRAY) : boss.getSubLocation().getStyle());
        if (dannypx.foe.handler.logic.PetLocationHandler.inCypressHub()) return Component.literal("Spawn").withStyle(ChatFormatting.GRAY);
        return null;
    }

    /// The location's climate in its colours ("Subtropical"), or null.
    static Component climate(String location) {
        Climate climate = CLIMATES.get(location.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", ""));
        return climate == null ? null : climate.tag();
    }

    /// "ᴄᴏᴍ 88.9K/100K (352)": the community total, the goal and (in brackets) your contribution,
    /// or null before it's been read (or when turned off).
    private static Component communityText() {
        if (!Configs.hudConfig.topBarShowCommunity.get() || dannypx.foe.handler.logic.CommunityEventHandler.communityInTimers()) return null;
        dannypx.foe.handler.logic.CommunityEventHandler event = dannypx.foe.handler.logic.CommunityEventHandler.instance();
        if (!event.hasData() || event.total().isEmpty()) return null;
        MutableComponent text = Component.empty()
                .append(Component.literal("ᴄᴏᴍ ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(dannypx.foe.handler.logic.CommunityEventHandler.shortNumber(event.total())).withStyle(ChatFormatting.AQUA));
        if (!event.goal().isEmpty()) {
            text.append(Component.literal("/").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(dannypx.foe.handler.logic.CommunityEventHandler.shortNumber(event.goal())).withStyle(ChatFormatting.WHITE));
        }
        if (event.contribution() >= 0) {
            text.append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(dannypx.foe.handler.logic.CommunityEventHandler.shortNumber(String.valueOf(event.contribution()))).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
        }
        return text;
    }

    /// Balance, credits, catches and TPS/ping, each one can be turned off; `level` 1 leaves off the
    /// TPS/ping, 2 also the labels.
    private static Component rightText(int level) {
        var cfg = Configs.hudConfig;
        ScoreboardHandler scoreboard = ScoreboardHandler.instance();
        boolean labels = level < 2;
        String gap = labels ? "    " : "   ";
        MutableComponent out = Component.empty();
        boolean balance = cfg.topBarShowBalance.get(), credits = cfg.topBarShowCredits.get();
        if (balance || credits) {
            if (labels) out.append(Component.literal("ʙᴀʟᴀɴᴄᴇ: ").withStyle(ChatFormatting.GRAY));
            if (balance) out.append(Component.literal("$").withStyle(ChatFormatting.DARK_GREEN))
                    .append(dannypx.foe.helper.NameTagMarker.GAP_WIDE)   // a two-pixel gap after the "$"
                    // The scoreboard's wallet already starts with its own "$"; keep only ours.
                    .append(Component.literal(scoreboard.getWallet().getString().trim().replaceFirst("^\\$\\s*", "")).withStyle(ChatFormatting.GREEN));
            if (balance && credits) out.append(Component.literal(" "));
            if (credits) {
                // the server's coin icon, in its own font: it's on the credits line but not always
                // in the number's part of it
                // part of it, and in its own font, which plain text would lose
                Component inValue = dannypx.foe.handler.fetch.ScoreboardHandler.glyphs(scoreboard.getCredits());
                out.append(inValue.getString().isEmpty() ? scoreboard.getCreditsIcon() : inValue);
                String value = scoreboard.getCredits().getString().replaceAll("[\\uE000-\\uF8FF]", "").trim();
                out.append(Component.literal(value).withStyle(ChatFormatting.YELLOW));
            }
        }
        if (cfg.topBarShowCatches.get()) {
            if (!out.getSiblings().isEmpty()) out.append(Component.literal(gap));
            if (labels) out.append(Component.literal("ᴄᴀᴛᴄʜᴇѕ: ").withStyle(ChatFormatting.GRAY));
            out.append(Component.literal(scoreboard.getCatches().getString().trim()).withStyle(ChatFormatting.RED))
                    .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(scoreboard.getCatchRate().getString().trim()).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (level == 0 && cfg.topBarShowTpsPing.get() && Minecraft.getInstance().player != null) {
            // the same space as between the others (no divider)
            if (!out.getSiblings().isEmpty()) out.append(Component.literal(gap));
            out.append(dannypx.foe.screens.element.hud.TpsPing.now());
        }
        return out;
    }

    enum Climate {
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
