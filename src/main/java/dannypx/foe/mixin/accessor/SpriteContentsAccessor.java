package dannypx.foe.mixin.accessor;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// A block texture's pixels, for the map scan's colours (test builds).
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
    @Accessor
    NativeImage getOriginalImage();
}
