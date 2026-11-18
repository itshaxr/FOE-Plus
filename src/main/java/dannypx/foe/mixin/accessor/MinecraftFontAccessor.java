package dannypx.foe.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// The game's fonts, for /foe minigame dump's glyph pictures.
@Mixin(net.minecraft.client.Minecraft.class)
public interface MinecraftFontAccessor {
    @Accessor("fontManager")
    net.minecraft.client.gui.font.FontManager foe$fontManager();
}
