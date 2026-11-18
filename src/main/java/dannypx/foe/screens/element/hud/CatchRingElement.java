package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.renderer.CatchRing;
import dannypx.foe.screens.element.Element;
import dannypx.foe.type.Alignment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The catch ring's box in Move HUD Elements: drag it anywhere, scroll over it to size it. It shows the ring filling
 * round. (In game the ring is drawn by {@link CatchRing} while you reel, at the place and size set here.)
 */
public class CatchRingElement extends Element {
    public CatchRingElement() {
        super(20, 20, 0f, 0f, Alignment.TOP_LEFT, Component.literal("Catch Ring"), true);
        fit();
    }

    /// The box round the ring at its size, its top-left as a fraction of the screen, from the ring's centre.
    private void fit() {
        Minecraft mc = Minecraft.getInstance();
        int side = 2 * CatchRing.reach(Configs.hudConfig.catchRingScale.get());
        width = side;
        height = side;
        float w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        xPos = Math.max(0f, CatchRing.centreX() - side / 2f / w);
        yPos = Math.max(0f, CatchRing.centreY() - side / 2f / h);
    }

    /// Where the ring's centre is for this box's top-left (fractions of the screen), as percents for the settings.
    public float[] centrePercent() {
        Minecraft mc = Minecraft.getInstance();
        float w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        return new float[]{Math.min(100f, (xPos + width / 2f / w) * 100f), Math.min(100f, (yPos + height / 2f / h) * 100f)};
    }

    @Override
    public void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        float w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int side = 2 * CatchRing.reach(Configs.hudConfig.catchRingScale.get());
        width = side;
        height = side;
        CatchRing.preview(g, Math.round(xPos * w) + side / 2.0, Math.round(yPos * h) + side / 2.0, Configs.hudConfig.catchRingScale.get());
    }
}
