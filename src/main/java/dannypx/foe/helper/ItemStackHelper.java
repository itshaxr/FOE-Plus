package dannypx.foe.helper;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.brigadier.StringReader;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.function.UnaryOperator;

import dannypx.foe.handler.logic.LoggerHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class ItemStackHelper {
    private static final GsonBuilder gson = new GsonBuilder();

    /// The item's custom data. Every item on screen is checked several times a frame (rarity
    /// marker, stack count, pet markers...), and copying its data each time made menus full of
    /// items very slow, so one copy is kept per data object (they never change; a changed item
    /// gets a new one). The returned tag is shared: read it, never modify it.
    public static CompoundTag getTag(ItemStack stack) {
        CustomData component = stack.get(DataComponents.CUSTOM_DATA);
        if (component == null) return new CompoundTag();
        synchronized (TAG_CACHE) {
            CompoundTag cached = TAG_CACHE.get(component);
            if (cached != null) return cached;
            if (TAG_CACHE.size() >= TAG_CACHE_MAX) TAG_CACHE.clear();
            CompoundTag copy = component.copyTag();
            TAG_CACHE.put(component, copy);
            return copy;
        }
    }

    private static final int TAG_CACHE_MAX = 1024;
    /// Keyed by identity: comparing custom data by value would walk the whole tag every lookup.
    private static final java.util.Map<CustomData, CompoundTag> TAG_CACHE = new java.util.IdentityHashMap<>();

    public static ItemStack jsonToItemStack(String json) {
        return ItemStack.CODEC
                .decode(RegistryOps.create(JsonOps.INSTANCE, Minecraft.getInstance().player.registryAccess()), gson.create().fromJson(json, JsonElement.class))
                .mapOrElse((Pair::getFirst), (pairError -> ItemStack.EMPTY));
    }

    public static String itemStackToJson(ItemStack itemStack) {
        if(!itemStack.isEmpty()) {
            return gson.setPrettyPrinting().create().toJson(ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, Minecraft.getInstance().player.registryAccess()), itemStack).getOrThrow());
        }
        return "{}";
    }

    public static String itemStackListToJson(List<ItemStack> itemStacks) {
        List<ItemStack> stacksToSerialize = itemStacks.stream().filter(stack -> !stack.isEmpty()).toList();
        if (stacksToSerialize.isEmpty()) return "[]";
        return gson.setPrettyPrinting().create().toJson(ItemStack.CODEC.listOf().encodeStart(RegistryOps.create(JsonOps.INSTANCE, Minecraft.getInstance().player.registryAccess()), stacksToSerialize).getOrThrow());
    }

    public static <T> NonNullList<T> deepCopy(
            NonNullList<T> original,
            T defaultValue,
            UnaryOperator<T> copier
    ) {
        NonNullList<T> copy =
                NonNullList.withSize(original.size(), defaultValue);

        for (int i = 0; i < original.size(); i++) {
            copy.set(i, copier.apply(original.get(i)));
        }
        return copy;
    }

    public static ItemStack valueOf(String value) {
        if(Minecraft.getInstance().player != null) {
            HolderLookup.Provider lookup = Minecraft.getInstance().player.registryAccess();
            try {
                ItemParser itemParser = new ItemParser(lookup);
                StringReader stringReader = new StringReader(value);
                ItemParser.ItemResult result = itemParser.parse(stringReader);

                ItemStack itemStack = new ItemStack(result.item(), 1);
                itemStack.applyComponents(result.components());

                return itemStack;
            } catch (Exception e) {
                LoggerHandler._debug(e.getMessage());
            }
        }
        return ItemStack.EMPTY;
    }
}
