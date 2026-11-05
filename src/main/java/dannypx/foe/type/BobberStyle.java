package dannypx.foe.type;

import dannypx.foe.FishOnMCExtras;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/// The 3D bobber's colour presets, picked with the Bobber button in the O menu. Each one is a
/// texture for the same model; Classic is FOE-R's original white and red.
public enum BobberStyle {
    CLASSIC("Classic", "fishing_hook"),
    OCEAN("Ocean", "fishing_hook/ocean"),
    LIME("Lime", "fishing_hook/lime"),
    GOLD("Gold", "fishing_hook/gold"),
    PUMPKIN("Pumpkin", "fishing_hook/pumpkin"),
    CANDY("Candy", "fishing_hook/candy"),
    ROYAL("Royal", "fishing_hook/royal"),
    MIDNIGHT("Midnight", "fishing_hook/midnight"),
    GLASS("Glass", "fishing_hook/glass");

    private final String displayName;
    private final RenderType renderType;

    BobberStyle(String displayName, String texture) {
        this.displayName = displayName;
        this.renderType = RenderTypes.entityTranslucent(
                Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "textures/entity/" + texture + ".png"));
    }

    public RenderType renderType() {
        return renderType;
    }

    public String displayName() {
        return displayName;
    }

    /// The next preset (or the previous one when {@code back}), wrapping round.
    public BobberStyle cycle(boolean back) {
        BobberStyle[] all = values();
        return all[Math.floorMod(ordinal() + (back ? -1 : 1), all.length)];
    }

    @Override
    public String toString() {
        return displayName;
    }
}
