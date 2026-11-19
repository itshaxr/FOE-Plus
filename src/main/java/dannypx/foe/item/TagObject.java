package dannypx.foe.item;

import com.mojang.serialization.DataResult;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.helper.ItemStackHelper;
import dannypx.foe.helper.UUIDHelper;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class TagObject {

    protected final Minecraft minecraft = Minecraft.getInstance();

    public static final String ID = "id";
    public static final String CATCHER = "catcher";
    public static final String UUID_KEY = "uuid";
    public static final String COUNTER = "counter";
    public static final String TYPE = "type";
    public static final String RARITY = "rarity";
    public static final String RENDER_INFO = "renderInfo";
    public static final String MONEY = "money";

    public static final int RARITY_LINE = 1;
    public static final int RARITY_SIBLING = 1;

    //From the bottom
    public static final int SHOP_PRICE_LINE = 5;

    public static final int BORDER_LINE = 2;

    protected final CompoundTag compoundTag;
    protected final ItemStack itemStack;

    protected TagObject() {
        this.compoundTag = new CompoundTag();
        this.itemStack = ItemStack.EMPTY;
    }

    protected TagObject(@NotNull CompoundTag compoundTag, ItemStack itemStack) {
        this.compoundTag = compoundTag;
        // the stack itself, not a copy: items are checked for every slot drawn, every frame, and copying each one made
        // a great deal of garbage (nothing here changes a stack)
        this.itemStack = itemStack;
    }

    protected TagObject(@NotNull CompoundTag compoundTag) {
        this.compoundTag = compoundTag;
        this.itemStack = ItemStack.EMPTY;
    }

    //region Generics
    public boolean contains(String key) {
        return this.compoundTag.contains(key);
    }

    public int getInt(String key) {
        return this.compoundTag.getInt(key).orElse(0);
    }

    public short getShort(String key) {
        return this.compoundTag.getShort(key).orElse((short) 0);
    }

    public long getLong(String key) {
        return this.compoundTag.getLong(key).orElse(0L);
    }

    public double getDouble(String key) {
        return this.compoundTag.getDouble(key).orElse(0d);
    }

    public float getFloat(String key) {
        return this.compoundTag.getFloat(key).orElse(0.0f);
    }

    public String getString(String key) {
        return this.compoundTag.getString(key).orElse("");
    }

    public boolean getBoolean(String key) {
        return this.compoundTag.getBoolean(key).orElse(false);
    }

    public UUID getUuid(String key) {
        return UUIDHelper.getUUID(this.compoundTag.getIntArray(key).orElse(new int[]{0}));
    }

    public ListTag getList(String key) {
        return this.compoundTag.getList(key).orElse(new ListTag());
    }

    public int getIntFromArray(String key, int index) {
        int[] array = this.compoundTag.getIntArray(key).orElse(new int[]{});
        if(array.length > 0) {
            return array[index];
        }
        return 0;
    }

    public long getLongFromArray(String key, int index) {
        long[] array = this.compoundTag.getLongArray(key).orElse(new long[]{});
        if(array.length > 0) {
            return array[index];
        }
        return 0;
    }

    public byte getByteFromArray(String key, int index) {
        byte[] array = this.compoundTag.getByteArray(key).orElse(new byte[]{});
        if(array.length > 0) {
            return array[index];
        }
        return 0;
    }

    public CompoundTag getTag(String key) {
        return this.compoundTag.getCompound(key).orElse(null);
    }

    public Tag get(String key) {
        return this.compoundTag.get(key);
    }

    public byte getType(String key) {
        return this.compoundTag.get(key).getId();
    }
    //endregion

    public UUID getID() {
        return this.getUuid(ID);
    }

    public UUID getPlayerUUID() {
        if(this.contains(CATCHER)) {
            return this.getUuid(CATCHER);
        } else if (this.contains(UUID_KEY)) {
            return this.getUuid(UUID_KEY);
        }
        return null;
    }

    //region Generic
    public Component getName() {
        Component name = this.itemStack.getCustomName();
        return name != null ? name : this.itemStack.getHoverName();
    }

    public int getCount() {
        if(this.contains(COUNTER)) {
            return this.getInt(COUNTER);
        }
        return this.itemStack.getCount();
    }

    public boolean isOwn() {
        if(minecraft.player != null && getPlayerUUID() != null) {
            return minecraft.player.getUUID().equals(getPlayerUUID());
        }
        return false;
    }

    public @NotNull String getType() {
        if(this.contains(TYPE)) {
            return this.getString(TYPE);
        } else if (this.contains(FishTagObject.FISH)) {
            return "fish";
        }
        return "";
    }

    public @NotNull String getRarity() {
        if(this.contains(RARITY)) {
            return this.getString(RARITY);
        }
        return "";
    }


    public Component getRarityComponent() {
        if(this.itemStack.get(DataComponents.LORE) != null
                && !this.getLore().isEmpty()
        ) {
            try {
                return rarityIn(this.getLore());
            } catch (IndexOutOfBoundsException e) {
                return Component.empty();
            }
        }
        return Component.empty();
    }

    /// The rarity badge (its glyph, in its style) on the line under the name.
    public static Component rarityIn(List<Component> lore) {
        if (lore.size() <= RARITY_LINE) return Component.empty();
        return LoreReader.glyphs(lore.get(RARITY_LINE));
    }

    public Component getBorderComponent() {
        if(this.itemStack.get(DataComponents.LORE) != null
                && !this.getLore().isEmpty()
        ) {
            List<Component> componentList = this.getLore();
            // the frame at the start of a middle line (" 퀑    "), for lines FOE+ adds to the tooltip
            if (componentList.size() > BORDER_LINE) return LoreReader.frame(componentList.get(BORDER_LINE));
        }
        return Component.empty();
    }

    public static Component getBorderComponent(ItemStack itemStack) {
        if(itemStack.get(DataComponents.LORE) != null) {
            List<Component> componentList = itemStack.get(DataComponents.LORE).lines();
            // the frame at the start of a middle line (" 퀑    "), for lines FOE+ adds to the tooltip
            if (componentList.size() > BORDER_LINE) return LoreReader.frame(componentList.get(BORDER_LINE));
        }
        return Component.empty();
    }

    public List<Component> getLore() {
        if(this.itemStack.get(DataComponents.LORE) != null) {
            return Objects.requireNonNull(this.itemStack.get(DataComponents.LORE)).lines();
        }
        return List.of();
    }

    public ItemStack getItemStack() {
        return this.itemStack;
    }

    protected List<TagObject> getItemStackList(String key) {
        if(this.contains(key)) {
            List<ItemStack> itemStackList = parseItems(this.get(key));

            return itemStackList.stream().map(item -> {
                Pair<Boolean, TagObject> validatedItem = ValidateItem.isType(item);
                return validatedItem.value2();
            }).toList();
        }
        return List.of();
    }

    private static boolean warned = false;

    /// The items stored under a key (the rod's parts, its bait). Read with the game's registries (the server's items
    /// can carry parts that need them, like a glint); one that still can't be read whole is rebuilt from its id, count
    /// and FishOnMC data, so the bait on your rod is never lost. Package-private for tests.
    static List<ItemStack> parseItems(Tag tag) {
        if (tag == null) return List.of();
        com.mojang.serialization.DynamicOps<Tag> ops = NbtOps.INSTANCE;
        try {
            var level = Minecraft.getInstance().level;
            if (level != null) ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        } catch (Throwable ignored) {
            // plain NBT then
        }
        DataResult<List<ItemStack>> all = ItemStack.CODEC.listOf().parse(ops, tag);
        if (all.result().isPresent()) return all.result().get();
        if (!(tag instanceof ListTag list)) return List.of();
        List<ItemStack> out = new java.util.ArrayList<>();
        for (Tag t : list) {
            DataResult<ItemStack> one = ItemStack.CODEC.parse(ops, t);
            ItemStack stack = one.result().orElse(null);
            if (stack == null) stack = rebuild(t);
            if (stack != null && !stack.isEmpty()) out.add(stack);
            else if (!warned) {
                warned = true;
                dannypx.foe.handler.logic.LoggerHandler.error("Couldn't read an item on your rod: " + one.error().map(Object::toString).orElse("?") + " | " + t);
            }
        }
        return out;
    }

    /// An item from its id, count and FishOnMC data alone (the rest of it left out), or null.
    private static ItemStack rebuild(Tag t) {
        if (!(t instanceof CompoundTag c)) return null;
        String id = c.getStringOr("id", "");
        if (id.isEmpty()) return null;
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.tryParse(id)).orElse(null);
        if (item == null) return null;
        ItemStack stack = new ItemStack(item, Math.max(1, c.getIntOr("count", 1)));
        CompoundTag components = c.getCompoundOrEmpty("components");
        CompoundTag data = components.getCompoundOrEmpty("minecraft:custom_data");
        if (!data.isEmpty()) stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(data));
        float model = components.getCompoundOrEmpty("minecraft:custom_model_data").getListOrEmpty("floats").getFloatOr(0, 0f);
        if (model > 0f) stack.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(List.of(model), List.of(), List.of(), List.of()));
        return stack;
    }

    public ListTag getRenderInfo() {
        if(this.contains(RENDER_INFO)) {
            return (ListTag) this.get(RENDER_INFO);
        }
        return new ListTag();
    }

    /// What a fish sells for: its "value" (88.57), or the "ᴠᴀʟᴜᴇ: $88.57" in its tooltip; 0 if neither.
    public double getSellValue() {
        if (this.contains("value")) return this.getFloat("value");
        java.util.List<Component> lore = this.getLore();
        Component line = LoreReader.findLine(lore, "value:");
        if (line == null) return 0;
        String t = line.getString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\$([0-9][0-9.,]*)([KMBT]?)").matcher(t);
        if (!m.find()) return 0;
        try {
            double v = Double.parseDouble(m.group(1).replace(",", ""));
            return switch (m.group(2)) { case "K" -> v * 1e3; case "M" -> v * 1e6; case "B" -> v * 1e9; case "T" -> v * 1e12; default -> v; };
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public float getMoney() {
        ListTag renderInfo = this.getRenderInfo();
        if(!renderInfo.isEmpty()) {
            if(((CompoundTag) renderInfo.getFirst()).contains(MONEY)) {
                return ((CompoundTag) renderInfo.getFirst()).getFloat(MONEY).orElse(0.0f);
            }
        }
        return 0f;
    }

    protected boolean isAuctionItem() {
        ListTag listTag = this.getRenderInfo();

        if(!listTag.isEmpty()) {
            return ((CompoundTag) listTag.getFirst()).contains(MONEY);
        }
        return false;
    }
    //endregion

    public static TagObject of(@NotNull CompoundTag compoundTag, @NotNull ItemStack itemStack) {
        return new TagObject(compoundTag, itemStack);
    }

    public static TagObject of(@NotNull CompoundTag compoundTag) {
        return new TagObject(compoundTag);
    }

    public static TagObject empty() {
        return new TagObject(ItemStackHelper.getTag(ItemStack.EMPTY), ItemStack.EMPTY);
    }
}
