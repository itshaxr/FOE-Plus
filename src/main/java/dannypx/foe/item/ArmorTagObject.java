package dannypx.foe.item;

import dannypx.foe.helper.ItemStackHelper;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ArmorTagObject extends TagObject {
    public static final String ARMOR_ROLLS = "fish_bonus";
    public static final String ARMOR_ROLLS_UNLOCKED = "unlocked";
    public static final String ARMOR_ROLLS_ROLLED = "rolled";
    public static final String ARMOR_ROLLS_ROLLS = "rolls";
    public static final String IDENTIFIED = "identified";


    public static final int ARMOR_QUALITY_LINE = 2;
    public static final int ARMOR_QUALITY_SIBLING = 3;

    public ArmorTagObject(CompoundTag compoundTag, ItemStack itemStack) {
        super(compoundTag, itemStack);
    }

    public ListTag getArmorRolls() {
        if(this.contains(ARMOR_ROLLS)) {
            return (ListTag)  this.compoundTag.get(ARMOR_ROLLS);
        }
        return new ListTag();
    }

    public boolean isArmorRollUnlocked(int index) {
        if(!getArmorRolls().isEmpty()
                && index < this.getArmorRolls().size()
                && ((CompoundTag) this.getArmorRolls().get(index)).contains(ARMOR_ROLLS_UNLOCKED)
        ) {
            return ((CompoundTag) this.getArmorRolls().get(index)).getBoolean(ARMOR_ROLLS_UNLOCKED).orElse(false);
        }
        return false;
    }

    public boolean isArmorRollRolled(int index) {
        if(!getArmorRolls().isEmpty()
                && index < this.getArmorRolls().size()
                && ((CompoundTag) this.getArmorRolls().get(index)).contains(ARMOR_ROLLS_ROLLED)
        ) {
            return ((CompoundTag) this.getArmorRolls().get(index)).getBoolean(ARMOR_ROLLS_ROLLED).orElse(false);
        }
        return false;
    }

    public boolean isIdentified() {
        if(this.contains(IDENTIFIED)) {
            return this.getBoolean(IDENTIFIED);
        }
        return false;
    }

    public int getArmorRollRolls(int index) {
        if(!getArmorRolls().isEmpty()
                && index < this.getArmorRolls().size()
                && ((CompoundTag) this.getArmorRolls().get(index)).contains(ARMOR_ROLLS_ROLLS)
        ) {
            return ((CompoundTag) this.getArmorRolls().get(index)).getInt(ARMOR_ROLLS_ROLLS).orElse(0);
        }
        return 0;
    }

    public Component getQualityComponent() {
        if(this.itemStack.get(DataComponents.LORE) != null
                && !this.getLore().isEmpty()
        ) {
            List<Component> components = this.getLore();
            Component qualityComponent;
            try {
                // "ꞯᴜᴀʟɪᴛʏ: sᴜᴘᴇʀɪᴏʀ [96%]" → "96%", in its colour
                Component line = LoreReader.findLine(components, "quality:");
                qualityComponent = LoreReader.percent(line != null ? line : components.size() > ARMOR_QUALITY_LINE ? components.get(ARMOR_QUALITY_LINE) : null);
                return qualityComponent;
            } catch (IndexOutOfBoundsException e) {
                return Component.empty();
            }
        }
        return Component.empty();
    }

    public static int calculateMoneyRolls(int rolls, int tier) {
        int adjustedRolls = rolls - 1;

        if (adjustedRolls <= 0) {
            return 0;
        }

        int cap = 15;
        int overflowValue = 25000;

        int amount = calculateSquareSum(Math.min(adjustedRolls, cap));

        if (adjustedRolls > cap) {
            amount += (adjustedRolls - cap) * overflowValue;
        }

        return amount;
    }

    private static int calculateSquareSum(int rolls) {
        int sum = 0;
        for (int i = 1; i <= rolls; i++) {
            sum += i * i * 100;
        }
        return sum;
    }

    public static ArmorTagObject of(@NotNull CompoundTag compoundTag, @NotNull ItemStack itemStack) {
        return new ArmorTagObject(compoundTag, itemStack);
    }

    public static ArmorTagObject empty() {
        return new ArmorTagObject(ItemStackHelper.getTag(ItemStack.EMPTY), ItemStack.EMPTY);
    }
}
