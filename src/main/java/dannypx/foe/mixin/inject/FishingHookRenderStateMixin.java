package dannypx.foe.mixin.inject;

import dannypx.foe.interfaces.IFishingHookRenderState;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(FishingHookRenderState.class)
public abstract class FishingHookRenderStateMixin implements IFishingHookRenderState {

    @Unique
    private ItemStack baitStack = ItemStack.EMPTY;

    @Unique
    private boolean disabledBait = false;

    @Unique
    private boolean own = false;

    @Unique
    private java.util.UUID owner = null;

    @Override
    public java.util.UUID foe$getOwner() {
        return owner;
    }

    @Override
    public void foe$setOwner(java.util.UUID owner) {
        this.owner = owner;
    }

    @Override
    public boolean foe$isOwn() {
        return own;
    }

    @Override
    public void foe$setOwn(boolean own) {
        this.own = own;
    }

    @Override
    public ItemStack foer$getBaitStack() {
        return baitStack;
    }

    @Override
    public void foer$setBaitStack(ItemStack stack) {
        this.baitStack = stack;
    }

    @Override
    public boolean foer$isDisabledBait() {
        return disabledBait;
    }

    @Override
    public void foer$setDisabledBait(boolean disabledBait) {
        this.disabledBait = disabledBait;
    }
}
