package dannypx.foe.screens.element.hud;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.fetch.BossEventHandler;
import dannypx.foe.handler.fetch.TabOverlayHandler;
import dannypx.foe.handler.logic.LoadingHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.config.Configs;
import dannypx.foe.helper.GuiGraphicsHelper;
import dannypx.foe.screens.element.Element;
import dannypx.foe.type.StringStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

public class LocationElement extends Element {
    //region Fields
    private static final int TEXTURE_WIDTH = 160;
    private static final int TEXTURE_HEIGHT = 36;

    //endregion

    public LocationElement() {
        super(TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                Configs.hudConfig.locationElementXPosition.get() / 100f,
                Configs.hudConfig.locationElementYPosition.get() / 100f,
                Configs.hudConfig.locationElementAlignment.get(),
                Configs.hudConfig.locationElementGroup.translation("Location Element"),
                false);
    }

    public LocationElement(boolean isCopy) {
        super(TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                Configs.hudConfig.locationElementXPosition.get() / 100f,
                Configs.hudConfig.locationElementYPosition.get() / 100f,
                Configs.hudConfig.locationElementAlignment.get(),
                Configs.hudConfig.locationElementGroup.translation("Location Element"),
                isCopy);
    }

    //region Methods
    static final int HEIGHT = 30, SKY = 0x4FC3F7;

    @Override
    public void render(GuiGraphics guiGraphics) {
        var cfg = Configs.hudConfig;
        if (!FoeRBoxes.shows(cfg.showLocationElement.get(), isCopy)) return;
        float scale = FoeRBoxes.scale(cfg.locationElementScale.get());
        int[] screen = FoeRBoxes.screen(scale);
        if (!isCopy) {
            xPos = cfg.locationElementXPosition.get() / 100f;
            yPos = cfg.locationElementYPosition.get() / 100f;
            alignment = cfg.locationElementAlignment.get();
        }
        boolean right = alignment == dannypx.foe.type.Alignment.TOP_RIGHT;
        int x = right ? screen[0] - Math.round(screen[0] * xPos) : Math.round(screen[0] * xPos);
        int y = Math.round(screen[1] * yPos);

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(scale, scale);
        FoeRBoxes.skin(cfg.locationElementBoxColor.get(), cfg.locationElementOpacity.get());
        int w;
        try {
            w = draw(guiGraphics, Minecraft.getInstance().font, x, y, right);
        } finally {
            FoeRBoxes.unskin();
        }
        guiGraphics.pose().popMatrix();
        this.width = Math.round(w * scale);
        this.height = Math.round(HEIGHT * scale);
    }

    /// Draws the box at `x` (its left edge, or its right edge when `right`), `y`; returns how wide it came out.
    /// The box holds the weather and the temperature; the rails the place (and your spot in it), then the time and
    /// the place's climate.
    private int draw(GuiGraphics g, Font font, int x, int y, boolean right) {
        BossEventHandler boss = BossEventHandler.instance();
        Component weather = boss.getWeather();
        Component temperature = boss.getTemperature();
        int ring = FoeRBoxes.ring(Configs.hudConfig.locationElementBorderColor.get(),
                weather.getStyle().getColor() != null ? weather.getStyle().getColor().getValue() & 0xFFFFFF : SKY);

        String place = boss.getLocation().getString().replaceAll("[\\uE000-\\uF8FF]", "").trim();
        MutableComponent top = Component.empty();
        top.append(place.isEmpty() ? Component.literal(isCopy ? "Location" : "").withStyle(ChatFormatting.GRAY)
                : dannypx.foe.helper.FoeLocations.styledName(place));
        Component spot = TopBarElement.spot();
        if (spot != null) top.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(spot);
        Component topLine = TextHelper.smallCaps(top);

        MutableComponent bottom = Component.empty();
        String time = boss.getTime().getString().trim();
        if (!time.isEmpty()) bottom.append(dannypx.foe.helper.SmoothClock.topBar(boss.getTime()));
        Component climate = place.isEmpty() ? null : TopBarElement.climate(place);
        if (climate != null) {
            if (!bottom.getSiblings().isEmpty()) bottom.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
            bottom.append(TextHelper.smallCaps(climate));
        }

        String symbols = weather.getString();
        int weatherW = FoeRBoxes.weatherWidth(font, symbols);
        int boxW = Math.max(FoeRBoxes.BOX, Math.max(weatherW, font.width(temperature)) + 8);
        int railX = right ? x - boxW : x + boxW;
        int rail1 = FoeRBoxes.railFor(font.width(topLine)), rail2 = FoeRBoxes.railFor(font.width(bottom));

        FoeRBoxes.box(g, x, y, right, ring, boxW);
        int mid = right ? x - boxW / 2 : x + boxW / 2;
        // the weather in FOE+'s own pictures, the temperature under it
        FoeRBoxes.drawWeather(g, font, symbols, mid - weatherW / 2, y + 4, ring);
        PanelElement.drawText(g, font, temperature.getVisualOrderText(), mid - font.width(temperature) / 2 + 1, y + 18);
        FoeRBoxes.rail(g, railX, y + 2, rail1, FoeRBoxes.RAIL_H, right, ring);
        FoeRBoxes.rail(g, railX, y + 16, rail2, FoeRBoxes.RAIL_H, right, ring);
        FoeRBoxes.text(g, font, topLine, railX, y + 4, right);
        FoeRBoxes.text(g, font, bottom, railX, y + 18, right);
        return boxW + Math.max(rail1, rail2);
    }
    //endregion
}
