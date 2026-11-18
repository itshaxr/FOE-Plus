package dannypx.foe.utils;

import net.minecraft.client.Minecraft;

/// A HUD's size, made readable: shrunk until one pixel of its text is less than one pixel of the screen, parts of the
/// letters drop out. So it never goes smaller than one screen pixel per text pixel; above that, as set.
public final class HudScale {
    private HudScale() {}

    public static float crisp(float scale) {
        Minecraft mc = Minecraft.getInstance();
        double gui = mc == null || mc.getWindow() == null ? 1.0 : mc.getWindow().getGuiScale();
        return crisp(scale, gui);
    }

    /// Package-private in spirit, public for tests: the same, for a given GUI scale.
    public static float crisp(float scale, double guiScale) {
        if (guiScale <= 0) return scale;
        float min = (float) (1.0 / guiScale);
        return Math.max(scale, min);
    }
}
