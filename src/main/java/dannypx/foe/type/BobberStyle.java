package dannypx.foe.type;

import dannypx.foe.FishOnMCExtras;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/// The 3D bobber's colour presets, picked in O → Bobber. Each one is a texture for the same
/// model (textures/entity/fishing_hook/<name>.png); Classic is FOE-R's original white and red.
/// {@code outer} is the top and bottom of the bobber, {@code band} the stripe round its middle and
/// {@code dot} the spot on its top and bottom faces. The textures are made from these colours.
public enum BobberStyle {
    CLASSIC("Classic", 0, 0xFFFFFF, 0xFF0000, 0x2C2D35),
    CHERRY("Cherry", 1, 0xE02020, 0xFFFFFF, 0x2C2D35),
    OCEAN("Ocean", 2, 0xFFFFFF, 0x2F6FE0, 0x2C2D35),
    ICE("Ice", 3, 0x9FD8FF, 0xFFFFFF, 0x2C2D35),
    LIME("Lime", 4, 0xFFFFFF, 0x3CC43C, 0x2C2D35),
    EMERALD("Emerald", 5, 0x1F8F4E, 0xF2C230, 0x2C2D35),
    MINT("Mint", 6, 0x8FF0C8, 0xFFFFFF, 0x2C2D35),
    GOLD("Gold", 7, 0xF2C230, 0xFFFFFF, 0x2C2D35),
    BEE("Bee", 8, 0xFFD21F, 0x2C2D35, 0x2C2D35),
    PUMPKIN("Pumpkin", 9, 0xFF8A1F, 0x2C2D35, 0x2C2D35),
    LAVA("Lava", 10, 0x8A1414, 0xFF8A1F, 0xFF8A1F),
    CANDY("Candy", 11, 0xFF7AC8, 0xFFFFFF, 0x2C2D35),
    ROYAL("Royal", 12, 0x8A3CE0, 0xF2C230, 0xF2C230),
    GALAXY("Galaxy", 13, 0x1B1F4D, 0xA45CFF, 0xA45CFF),
    MIDNIGHT("Midnight", 14, 0x2C2D35, 0x36D6E0, 0x36D6E0),
    SNOW("Snow", 15, 0xFFFFFF, 0xC8C8C8, 0x2C2D35);

    private final String displayName;
    /// Sent to other players. Never change or reuse one; give a new preset the next number.
    private final int id;
    private final int outer, band, dot;
    private RenderType renderType;

    BobberStyle(String displayName, int id, int outer, int band, int dot) {
        this.displayName = displayName;
        this.id = id;
        this.outer = outer;
        this.band = band;
        this.dot = dot;
    }

    /// Classic keeps FOE-R's own texture file.
    public String texturePath() {
        return this == CLASSIC ? "textures/entity/fishing_hook.png"
                : "textures/entity/fishing_hook/" + name().toLowerCase(java.util.Locale.ROOT) + ".png";
    }

    public RenderType renderType() {
        if (renderType == null) {
            renderType = RenderTypes.entityTranslucent(Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, texturePath()));
        }
        return renderType;
    }

    public String displayName() {
        return displayName;
    }

    public int outer() {
        return outer;
    }

    public int band() {
        return band;
    }

    public int dot() {
        return dot;
    }

    public int id() {
        return id;
    }

    /// The preset with this id (as sent between players), or null if it's one this version
    /// doesn't have.
    public static BobberStyle byId(int id) {
        for (BobberStyle s : values()) if (s.id == id) return s;
        return null;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
