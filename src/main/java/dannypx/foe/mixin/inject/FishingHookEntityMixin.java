package dannypx.foe.mixin.inject;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.item.FishingRodTagObject;
import dannypx.foe.item.ValidateItem;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import dannypx.foe.interfaces.IFishingHookEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHook.class)
public abstract class FishingHookEntityMixin implements IFishingHookEntity {
    @Shadow @Nullable public abstract Player getPlayerOwner();

    @Unique
    private ItemStack baitStack = ItemStack.EMPTY;

    @Unique
    private boolean disabledBait = false;

    @Unique
    private FishingRodTagObject currentFishingRod = FishingRodTagObject.empty();

    @Inject(method = "tick", at = @At("TAIL"))
    private void injectTick(CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && this.getPlayerOwner() != null
                    && currentFishingRod.getItemStack() == ItemStack.EMPTY
            ) {
                Pair<Boolean, @Nullable FishingRodTagObject> validatedFishingRod = ValidateItem.isFishingRod(this.getPlayerOwner().getMainHandItem());

                if(validatedFishingRod.value1()
                        && !ItemStack.isSameItemSameComponents(currentFishingRod.getItemStack(), validatedFishingRod.value2().getItemStack())
                ) {
                    currentFishingRod = validatedFishingRod.value2();
                    if(!currentFishingRod.getActiveBait().isEmpty()) {
                        baitStack = currentFishingRod.getActiveBait().getFirst().getItemStack();
                        disabledBait = currentFishingRod.getDisableBait();
                    }
                }
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("FishingHookEntityMixin.injectTick", foe$t);
        }
    }

    @Override
    public ItemStack foer$getBaitStack() {
        return baitStack;
    }

    @Override
    public boolean foer$isDisabledBait() {
        return disabledBait;
    }
}
