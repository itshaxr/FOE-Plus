package dannypx.foe.utils;

import net.minecraft.client.Minecraft;

/// A HUD's size, kept from vanishing: it can shrink to half a screen pixel per text pixel (where the letters start
/// losing pixels, but are still read), and no further; above that, as set. (It used to stop at one screen pixel per
/// text pixel: at GUI scale 2 everything stopped getting smaller at about 20%.)
public final class HudScale {
    private HudScale() {}

    /// The smallest a HUD is drawn: half a screen pixel per text pixel.
    static final double SMALLEST_SCREEN_PIXELS = 0.5;

    /// The smallest scale a HUD can be set to at the current GUI scale (a whole 0.05 step, at least `settingMin`):
    /// scrolling stops there, so scrolling back up makes it bigger straight away.
    public static float smallest(float settingMin) {
        Minecraft mc = Minecraft.getInstance();
        double gui = mc == null || mc.getWindow() == null ? 1.0 : mc.getWindow().getGuiScale();
        float floor = gui <= 0 ? settingMin : (float) (Math.ceil(SMALLEST_SCREEN_PIXELS / gui * 20.0 - 1e-6) / 20.0);
        return Math.max(settingMin, floor);
    }

    public static float crisp(float scale) {
        Minecraft mc = Minecraft.getInstance();
        double gui = mc == null || mc.getWindow() == null ? 1.0 : mc.getWindow().getGuiScale();
        return crisp(scale, gui);
    }

    /// Package-private in spirit, public for tests: the same, for a given GUI scale.
    public static float crisp(float scale, double guiScale) {
        if (guiScale <= 0) return scale;
        float min = (float) (SMALLEST_SCREEN_PIXELS / guiScale);
        return Math.max(scale, min);
    }
}
