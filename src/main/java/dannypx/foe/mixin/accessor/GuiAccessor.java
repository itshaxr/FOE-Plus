package dannypx.foe.mixin.accessor;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// How long the action bar message has left: the reel-in minigame runs there (bobber timer).
@Mixin(Gui.class)
public interface GuiAccessor {
    @Accessor
    int getOverlayMessageTime();
}
