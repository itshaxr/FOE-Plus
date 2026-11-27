package dannypx.foe.mixin.inject;

import dannypx.foe.handler.fetch.ChummerHandler;
import dannypx.foe.mixin.accessor.SingleQuadParticleAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The circle a chummer draws on the ground (the server's green sparkles) in its rarity's colour: Common white, Rare
 * blue, Epic light green, Legendary gold, Mythical red (Settings → Chummer Panel → "Chummer circle in its rarity's
 * colour"). Each sparkle near a chummer gets FOE+'s white sparkle (the same shape), tinted.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Unique
    private static final Identifier FOEPLUS_GLINT = Identifier.fromNamespaceAndPath("foeplus", "chum_glint");

    @Inject(method = "createParticle", at = @At("RETURN"))
    private void foeplus$chummerCircle(ParticleOptions options, double x, double y, double z, double xd, double yd, double zd,
                                       CallbackInfoReturnable<Particle> cir) {
        try {
            if (options == null || options.getType() != ParticleTypes.HAPPY_VILLAGER) return;
            if (!(cir.getReturnValue() instanceof SingleQuadParticle particle)) return;
            if (!dannypx.foe.config.Configs.hudConfig.chummerCircleRarityColour.get()) return;
            int rgb = ChummerHandler.instance().circleColourAt(x, y, z);
            if (rgb < 0) return;
            var sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES).getSprite(FOEPLUS_GLINT);
            if (sprite == null) return;
            ((SingleQuadParticleAccessor) particle).foeplus$setSprite(sprite);
            particle.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
        } catch (Throwable ignored) {
            // the server's sparkle, as it was
        }
    }
}
