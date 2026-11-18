package dannypx.foe.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.client.gui.font.FontSet.class)
public interface FontSetAccessor {
    @Accessor("activeProviders")
    java.util.List<com.mojang.blaze3d.font.GlyphProvider> foe$activeProviders();
}
