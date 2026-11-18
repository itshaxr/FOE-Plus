package dannypx.foe.mixin.accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// Where a menu sits on screen, for boxes drawn beside it (the bait value).
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenPosAccessor {
    @Accessor("leftPos")
    int foe$leftPos();

    @Accessor("topPos")
    int foe$topPos();

    @Accessor("imageWidth")
    int foe$imageWidth();
}
