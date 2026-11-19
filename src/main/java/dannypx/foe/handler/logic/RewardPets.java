package dannypx.foe.handler.logic;

import dannypx.foe.item.PetTagObject;
import net.minecraft.world.item.ItemStack;

/**
 * Pets from contests and events, found by the check after a catch: told once each, never counted in your stats.
 * (A pet turning up in your inventory any other way, such as one you unequip or move, is never told.)
 */
final class RewardPets {
    private RewardPets() {}

    private static ItemStack last = ItemStack.EMPTY;
    private static long lastAt = 0L;

    static void notify(ItemStack stack, PetTagObject pet, PetSourceHandler.Source source) {
        if (source == PetSourceHandler.Source.FISHED || source == PetSourceHandler.Source.UNKNOWN) return;
        long now = System.currentTimeMillis();
        if (now - lastAt < 15_000L && ItemStack.isSameItemSameComponents(last, stack)) return;
        last = stack.copy();
        lastAt = now;
        NotifierHandler.instance().notifyRewardPet(pet, source);
    }
}
