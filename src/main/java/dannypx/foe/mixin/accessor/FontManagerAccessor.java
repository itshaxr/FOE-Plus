package dannypx.foe.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.client.gui.font.FontManager.class)
public interface FontManagerAccessor {
    @Accessor("fontSets")
    java.util.Map<net.minecraft.resources.Identifier, net.minecraft.client.gui.font.FontSet> foe$fontSets();
}
