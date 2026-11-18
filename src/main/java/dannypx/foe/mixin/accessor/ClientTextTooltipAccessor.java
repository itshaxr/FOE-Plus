package dannypx.foe.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// A tooltip line as it's drawn, for the skin-coloured pet frames.
@Mixin(net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip.class)
public interface ClientTextTooltipAccessor {
    @Accessor("text")
    net.minecraft.util.FormattedCharSequence foe$text();
}
