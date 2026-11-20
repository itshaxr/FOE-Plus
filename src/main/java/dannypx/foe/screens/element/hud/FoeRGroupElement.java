package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.screens.element.Element;
import dannypx.foe.type.Alignment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The FOE-R bottom bar's rod parts (or armour) bar in Move HUD Elements: drag it anywhere, scroll over it to size it.
 * Until it's moved it sits above the bar's end, as it starts. (In game {@link FoeRHotbar} draws it there.)
 */
public class FoeRGroupElement extends Element {
    private final boolean armor;

    public FoeRGroupElement(boolean armor) {
        super(FoeRHotbar.GROUP_W, FoeRHotbar.GROUP_H, 0f, 0f, Alignment.TOP_LEFT,
                Component.literal(armor ? "Armour bar" : "Rod parts bar"), true);
        this.armor = armor;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        float[] at = FoeRHotbar.groupPlace(armor, w, h);
        xPos = Math.max(0f, at[0] / w);
        yPos = Math.max(0f, at[1] / h);
    }

    /// Its size now (moved ones have their own; one not moved yet takes the bar's).
    private float size() {
        var cfg = Configs.hudConfig;
        boolean moved = armor ? cfg.foeRHotbarArmorX.get() >= 0 : cfg.foeRHotbarPartsX.get() >= 0;
        if (!moved) return FoeRHotbar.barScale();
        return armor ? cfg.foeRHotbarArmorScale.get() : cfg.foeRHotbarPartsScale.get();
    }

    /// Saves where it was dropped (its top-left, % of the screen), at the size it shows.
    public void save() {
        var cfg = Configs.hudConfig;
        boolean moved = armor ? cfg.foeRHotbarArmorX.get() >= 0 : cfg.foeRHotbarPartsX.get() >= 0;
        if (!moved) (armor ? cfg.foeRHotbarArmorScale : cfg.foeRHotbarPartsScale).accept(FoeRHotbar.barScale());
        (armor ? cfg.foeRHotbarArmorX : cfg.foeRHotbarPartsX).accept(Math.min(100f, xPos * 100f));
        (armor ? cfg.foeRHotbarArmorY : cfg.foeRHotbarPartsY).accept(Math.min(100f, yPos * 100f));
        cfg.save();
    }

    @Override
    public void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        float w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        float s = size();
        width = Math.round(FoeRHotbar.GROUP_W * s);
        height = Math.round(FoeRHotbar.GROUP_H * s);
        g.pose().pushMatrix();
        g.pose().translate(Math.round(xPos * w), Math.round(yPos * h));
        g.pose().scale(s, s);
        FoeRHotbar.drawGroupAt(g, armor);
        g.pose().popMatrix();
    }
}
