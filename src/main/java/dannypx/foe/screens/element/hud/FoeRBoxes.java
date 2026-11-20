package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The profile, pet and location boxes FOE+ shows with the FOE-R HUD Style (in place of the top bar): FOE-R's idea (a
 * box with dark rails running off it) in FOE+'s own dress. Each box has a thin coloured ring (gold for you, the
 * pet's rating colour, the weather's colour), its rails fade out and start with a nib in the ring's colour, and the
 * level bars are FOE+'s sliding ones. Anchored on the right, everything is mirrored: the box on the right, the rails
 * running off to the left, the text lined up against them.
 */
public final class FoeRBoxes {
    private FoeRBoxes() {}

    /// The box's size (square), and the rails' height.
    static final int BOX = 30, RAIL_H = 12;
    /// The text's distance from the box's side.
    static final int TEXT_IN = 6;
    /// A rail runs this far past its text before it has faded out, and never shorter than MIN_RAIL.
    static final int RAIL_TAIL = 30, MIN_RAIL = 90;

    /// The HUD Style is FOE-R: the boxes show (each as switched on), and the top bar doesn't.
    public static boolean on() {
        return Configs.hudConfig.hudStyle.get() == dannypx.foe.type.HudStyle.FOE_R;
    }

    /// Whether a box shows: the style is FOE-R, it's switched on, and FOE+ is ready (always in the editor's preview).
    static boolean shows(boolean switchedOn, boolean isCopy) {
        if (!on() || !switchedOn) return false;
        if (isCopy) return true;
        return dannypx.foe.handler.logic.LoadingHandler.instance().isLoadingDone()
                && dannypx.foe.handler.fetch.TabOverlayHandler.instance().isInInstance();
    }

    /// FOE-R's dark glass.
    static final int GLASS = 0x101418, GLASS_ALPHA = 0xD0;
    /// The glass (ARGB) of the box being drawn, and the colour its bars fade from: set by each box from its settings.
    private static int glass = GLASS_ALPHA << 24 | GLASS, railRgb = 0;

    /// The colours for the box drawn next, from its settings ("#RRGGBB" or "" for FOE-R's own; opacity in %).
    static void skin(String boxColor, int opacityPercent) {
        int c = GameXpBar.parse(boxColor);
        int a = Math.round(Math.max(0, Math.min(100, opacityPercent)) * 255 / 100f);
        glass = a << 24 | (c < 0 ? GLASS : c);
        railRgb = c < 0 ? 0 : c;
    }

    /// Back to FOE-R's own colours (after a box is drawn).
    static void unskin() {
        glass = GLASS_ALPHA << 24 | GLASS;
        railRgb = 0;
    }

    /// The border colour picked in a box's settings, or `own` (RGB).
    static int ring(String borderColor, int own) {
        int c = GameXpBar.parse(borderColor);
        return c < 0 ? own : c;
    }

    /// The box at `x` (its left edge; its right edge when `right`), `y`: a dark edge, the coloured ring, dark glass.
    static void box(GuiGraphics g, int x, int y, boolean right, int ring) {
        box(g, x, y, right, ring, BOX);
    }

    /// The same, `w` wide (the location's box is as wide as its weather needs).
    static void box(GuiGraphics g, int x, int y, boolean right, int ring, int w) {
        int left = right ? x - w : x;
        g.fill(left, y, left + w, y + BOX, 0xFF0B0B0B);
        g.fill(left + 1, y + 1, left + w - 1, y + BOX - 1, 0xFF000000 | ring);
        g.fill(left + 2, y + 2, left + w - 2, y + BOX - 2, glass);
        g.fill(left + 2, y + 2, left + w - 2, y + 3, 0x30FFFFFF);
    }

    /// FOE+'s own pictures for the weather on the server's bar (12 by 12, side by side): sun, moon, rain, storm,
    /// Blooming Oasis, Gold Rush, Fabled.
    static final net.minecraft.resources.Identifier WEATHER_ICONS = net.minecraft.resources.Identifier.fromNamespaceAndPath(
            dannypx.foe.FishOnMCExtras.MOD_ID, "textures/gui/weather_icons.png");
    static final String WEATHER_SYMBOLS = "\u2600\u25CB\u2602\u26A1\u2663\u26A0\u2B50";
    static final int WEATHER_ICON = 12;

    /// Which picture a weather symbol has (-1: none, it's drawn as the server's symbol). Package-private for tests.
    static int weatherIcon(int codePoint) {
        return WEATHER_SYMBOLS.indexOf(codePoint);
    }

    /// The weather's symbols, without the spaces between them.
    static int[] weatherSymbols(String weather) {
        return weather.codePoints().filter(cp -> !Character.isWhitespace(cp) && cp != 0xFE0F).toArray();
    }

    /// How wide the weather's pictures (and any symbol without one) come out.
    static int weatherWidth(Font font, String weather) {
        int w = 0;
        for (int cp : weatherSymbols(weather)) {
            if (w > 0) w += 1;
            w += weatherIcon(cp) >= 0 ? WEATHER_ICON : font.width(new String(Character.toChars(cp)));
        }
        return w;
    }

    /// Draws the weather's pictures side by side from `x`, their top at `y` (a symbol without a picture as itself, in
    /// `color`).
    static void drawWeather(GuiGraphics g, Font font, String weather, int x, int y, int color) {
        for (int cp : weatherSymbols(weather)) {
            int i = weatherIcon(cp);
            if (i >= 0) {
                g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, WEATHER_ICONS, x, y, i * WEATHER_ICON, 0,
                        WEATHER_ICON, WEATHER_ICON, WEATHER_ICON, WEATHER_ICON, WEATHER_ICON * WEATHER_SYMBOLS.length(), WEATHER_ICON);
                x += WEATHER_ICON + 1;
            } else {
                String s = new String(Character.toChars(cp));
                g.drawString(font, s, x, y + 2, 0xFF000000 | color, true);
                x += font.width(s) + 1;
            }
        }
    }

    /// Where the box's inside starts (for the head, the pet, the weather).
    static int inside(int x, boolean right) {
        return right ? x - BOX + 3 : x + 3;
    }

    /// A rail `len` long leaving the box at `x` (running right, or left when `right`): dark, fading to nothing, with
    /// a nib in the ring's colour where it starts.
    static void rail(GuiGraphics g, int x, int y, int len, int h, boolean right, int ring) {
        for (int i = 0; i < len; i++) {
            float k = 1f - (float) i / len;
            int a = Math.round(0xB0 * k * k);
            int px = right ? x - 1 - i : x + i;
            g.fill(px, y, px + 1, y + h, a << 24 | railRgb);
        }
        if (right) g.fill(x - 2, y + 1, x, y + h - 1, 0xFF000000 | ring);
        else g.fill(x, y + 1, x + 2, y + h - 1, 0xFF000000 | ring);
    }

    static int railFor(int textWidth) {
        return Math.max(MIN_RAIL, textWidth + TEXT_IN + RAIL_TAIL);
    }

    /// Text starting `TEXT_IN` from `from` (the rail's start), or ending there when `right`.
    static void text(GuiGraphics g, Font font, Component text, int from, int y, boolean right) {
        int x = right ? from - TEXT_IN - font.width(text) : from + TEXT_IN;
        PanelElement.drawText(g, font, text.getVisualOrderText(), x, y);
    }

    /// A level bar `w` wide, its top at `y`: the game's own look or FOE+'s (as picked for every XP bar).
    static void levelBar(GuiGraphics g, int x, int y, int w, float fraction, int light, int dark, int c) {
        if (GameXpBar.gameStyle()) PetPanelElement.drawGameBar(g, x, y - 3, w, fraction, c);
        else PetPanelElement.drawBar(g, x, y, w, fraction, GameXpBar.foeLight(light, c), GameXpBar.foeDark(dark, c), true, 2);
    }

    /// The panels that can sit in a top corner, by their settings' names.
    static final String[] CORNER_PANELS = {"contestPanel", "questPanel", "dailyQuestPanel", "statsPanel", "sessionStatsPanel",
            "timersPanel", "crewPanel", "eventsPanel", "miniEventPanel", "petPanel", "baitPanel", "luckScalePanel", "alertPanel",
            "chummerPanel"};

    /// Just after switching to the FOE-R style: the panels in the top left and top right corners move down (each corner
    /// together, keeping their spacing) so they start below the boxes in that corner instead of under them. Panels
    /// already lower than the boxes stay where they are.
    public static void makeRoom() {
        try {
            var cfg = Configs.hudConfig;
            int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            if (screenH <= 0) return;
            for (dannypx.foe.type.Alignment corner : new dannypx.foe.type.Alignment[]{dannypx.foe.type.Alignment.TOP_LEFT, dannypx.foe.type.Alignment.TOP_RIGHT}) {
                int boxesBottom = 0;
                if (cfg.showProfileElement.get() && cfg.profileElementAlignment.get() == corner)
                    boxesBottom = Math.max(boxesBottom, bottom(cfg.profileElementYPosition.get(), ProfileElement.HEIGHT, cfg.profileElementScale.get(), screenH));
                if (cfg.showPetElement.get() && cfg.petElementAlignment.get() == corner)
                    boxesBottom = Math.max(boxesBottom, bottom(cfg.petElementYPosition.get(), PetElement.HEIGHT, cfg.petElementScale.get(), screenH));
                if (cfg.showLocationElement.get() && cfg.locationElementAlignment.get() == corner)
                    boxesBottom = Math.max(boxesBottom, bottom(cfg.locationElementYPosition.get(), LocationElement.HEIGHT, cfg.locationElementScale.get(), screenH));
                if (boxesBottom <= 0) continue;
                int wanted = boxesBottom + 4;
                java.util.List<me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt> ys = new java.util.ArrayList<>();
                int highest = Integer.MAX_VALUE;
                for (String p : CORNER_PANELS) {
                    Object show = field("show" + Character.toUpperCase(p.charAt(0)) + p.substring(1));
                    if (show instanceof me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean b && !b.get()) continue;
                    if (!(field(p + "Alignment") instanceof me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedChoice<?> a) || a.get() != corner) continue;
                    if (!(field(p + "YPosition") instanceof me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt y)) continue;
                    ys.add(y);
                    highest = Math.min(highest, Math.round(y.get() / 100f * screenH));
                }
                if (ys.isEmpty() || highest >= wanted) continue;
                // in whole percent, rounded up so they clear the boxes
                int shift = (int) Math.ceil((wanted - highest) * 100f / screenH);
                for (var y : ys) y.accept(Math.min(100, y.get() + shift));
            }
            cfg.save();
        } catch (RuntimeException e) {
            dannypx.foe.handler.logic.LoggerHandler.error(e);
        }
    }

    private static int bottom(int yPercent, int height, float scale, int screenH) {
        return Math.round(yPercent / 100f * screenH + height * scale(scale));
    }

    private static Object field(String name) {
        try {
            return Configs.hudConfig.getClass().getField(name).get(Configs.hudConfig);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /// The scale for an element (snapped to whole pixels) and the screen's size at it.
    static float scale(float configScale) {
        return dannypx.foe.utils.HudScale.crisp(configScale);
    }

    static int[] screen(float scale) {
        var w = Minecraft.getInstance().getWindow();
        return new int[]{(int) (w.getGuiScaledWidth() / scale), (int) (w.getGuiScaledHeight() / scale)};
    }
}
