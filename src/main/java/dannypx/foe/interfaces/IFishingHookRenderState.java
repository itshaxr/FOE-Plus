package dannypx.foe.interfaces;

import net.minecraft.world.item.ItemStack;

public interface IFishingHookRenderState {
    ItemStack foer$getBaitStack();
    boolean foer$isDisabledBait();

    void foer$setBaitStack(ItemStack stack);
    void foer$setDisabledBait(boolean disabledBait);

    /// True when this is your own bobber.
    boolean foe$isOwn();
    void foe$setOwn(boolean own);
}
