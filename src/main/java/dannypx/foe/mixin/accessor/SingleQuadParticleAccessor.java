package dannypx.foe.mixin.accessor;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/// Lets FOE+ give a particle another picture (the chummer circle's sparkles, recoloured).
@Mixin(SingleQuadParticle.class)
public interface SingleQuadParticleAccessor {
    @Invoker("setSprite")
    void foeplus$setSprite(TextureAtlasSprite sprite);
}
