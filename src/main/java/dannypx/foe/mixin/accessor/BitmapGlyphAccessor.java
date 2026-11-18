package dannypx.foe.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// A picture glyph from a resource pack font: its sheet and where it sits on it.
@Mixin(targets = "net.minecraft.client.gui.font.providers.BitmapProvider$Glyph")
public interface BitmapGlyphAccessor {
    @Accessor("image") com.mojang.blaze3d.platform.NativeImage foe$image();
    @Accessor("offsetX") int foe$offsetX();
    @Accessor("offsetY") int foe$offsetY();
    @Accessor("width") int foe$width();
    @Accessor("height") int foe$height();
}
