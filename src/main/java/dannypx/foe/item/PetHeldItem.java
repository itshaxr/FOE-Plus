package dannypx.foe.item;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

/**
 * The item a pet is holding. The server stores it in the pet's {@code item} list as a saved
 * ItemStack ({@code {id, count, components}}); this decodes it the way FOECollab's
 * PetEquipHandler does (AGPL-3.0). Decoding is cached per pet stack: the equipped pet is
 * replaced whenever its item changes, so a new held item is picked up straight away.
 */
public final class PetHeldItem {
    private PetHeldItem() {}

    private static PetTagObject cachedPet = null;
    private static ItemStack cachedItem = ItemStack.EMPTY;

    /// The held item, or {@link ItemStack#EMPTY} when the pet holds nothing.
    public static ItemStack of(PetTagObject pet) {
        if (pet == null) return ItemStack.EMPTY;
        if (pet != cachedPet) {
            cachedItem = decode(pet);
            cachedPet = pet;
        }
        return cachedItem;
    }

    /// Uncached: for callers that keep their own cache (the slot marker colour).
    public static ItemStack decodeUncached(PetTagObject pet) {
        return pet == null ? ItemStack.EMPTY : decode(pet);
    }

    /// The colour of the held item's name (the server colours it by rarity: a Fabled Amulet is
    /// red), or -1 when there's no item or its name has no colour.
    public static int nameColor(ItemStack held) {
        if (held == null || held.isEmpty()) return -1;
        return held.getHoverName().visit((style, text) -> {
            if (text.isBlank() || style.getColor() == null) return java.util.Optional.<Integer>empty();
            int rgb = style.getColor().getValue() & 0xFFFFFF;
            return rgb == 0xFFFFFF ? java.util.Optional.<Integer>empty() : java.util.Optional.of(rgb);
        }, net.minecraft.network.chat.Style.EMPTY).orElse(-1);
    }

    private static ItemStack decode(PetTagObject pet) {
        ListTag items = pet.getList(PetTagObject.ITEM);
        if (items.isEmpty()) return ItemStack.EMPTY;
        CompoundTag itemTag = items.getCompound(0).orElse(null);
        if (itemTag == null || itemTag.isEmpty()) return ItemStack.EMPTY;
        Minecraft mc = Minecraft.getInstance();
        try {
            // Registry-aware ops so components that point at registries decode too.
            var ops = mc.level != null
                    ? RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess())
                    : NbtOps.INSTANCE;
            return ItemStack.CODEC.parse((com.mojang.serialization.DynamicOps<Tag>) ops, itemTag).result().orElse(ItemStack.EMPTY);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }
}
