package dannypx.foe.item;

import dannypx.foe.helper.TextHelper;
import dannypx.foe.helper.ItemStackHelper;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class PetTagObject extends TagObject {

    public static final String LEVEL = "level";
    public static final String XP_NEED = "xp_need";
    public static final String XP_CURRENT = "xp_cur";
    public static final String RATING = "rating";
    public static final String LOCATION_BASE = "lbase";
    public static final String CLIMATE_BASE = "cbase";
    public static final String PERCENT_MAX_BASE = "percent_max";
    public static final String MAX_BASE = "cur_max";
    public static final String ITEM = "item";
    public static final String SKIN = "skin";
    public static final String TRAIL = "trail";

    public static final int RATING_LINE = 15;
    public static final int RATING_SIBLING = 2;

    public static final int C_BASE_LUCK_LINE = 8;
    public static final int C_BASE_SCALE_LINE = 9;
    public static final int L_BASE_LUCK_LINE = 12;
    public static final int L_BASE_SCALE_LINE = 13;

    public PetTagObject(CompoundTag nbtCompound, ItemStack itemStack) {
        super(nbtCompound, itemStack);
    }

    public int getLevel() {
        if(this.contains(LEVEL)) {
            return this.getInt(LEVEL);
        }
        return 0;
    }

    public float getProgress() {
        if(this.contains(XP_NEED) && this.contains(XP_CURRENT)) {
            float neededXP = this.getFloat(XP_NEED);
            float currentXP = this.getFloat(XP_CURRENT);
            return Math.min(currentXP / neededXP, 1f);
        }
        return 0f;
    }

    public Component getRatingComponent() {
        if(this.itemStack.get(DataComponents.LORE) != null
                && !this.getLore().isEmpty()
        ) {
            try {
                return TextHelper.trim(ratingIn(this.getLore()));
            } catch (IndexOutOfBoundsException e) {
                return Component.empty();
            }
        }
        return Component.empty();
    }

    /// The rating ("ɢʀᴇᴀᴛ", in its colour) from a pet's tooltip, found by its "RATING:" label
    /// wherever the line is. Empty if none.
    public static Component ratingIn(List<Component> lore) {
        return LoreReader.valueAfter(LoreReader.findLine(lore, "rating:"), "rating:");
    }

    public ListTag getLocationBase() {
        if(this.contains(LOCATION_BASE)) {
            return this.getList(LOCATION_BASE);
        }
        return new ListTag();
    }

    public ListTag getClimateBase() {
        if(this.contains(CLIMATE_BASE)) {
            return this.getList(CLIMATE_BASE);
        }
        return new ListTag();
    }

    public float getLocationPercentMaxLuck() {
        ListTag base = this.getLocationBase();
        if(!base.isEmpty()) {
            CompoundTag compound = base.getCompound(0).orElse(new CompoundTag());
            if(compound.contains(PERCENT_MAX_BASE)) {
                return compound.getFloat(PERCENT_MAX_BASE).orElse(0.0f);
            }
        }
        return 0f;
    }

    public float getLocationPercentMaxScale() {
        ListTag base = this.getLocationBase();
        if(!base.isEmpty()) {
            CompoundTag compound = base.getCompound(1).orElse(new CompoundTag());
            if(compound.contains(PERCENT_MAX_BASE)) {
                return compound.getFloat(PERCENT_MAX_BASE).orElse(0.0f);
            }
        }
        return 0f;
    }

    public float getClimatePercentMaxLuck() {
        ListTag base = this.getClimateBase();
        if(!base.isEmpty()) {
            CompoundTag compound = base.getCompound(0).orElse(new CompoundTag());
            if(compound.contains(PERCENT_MAX_BASE)) {
                return compound.getFloat(PERCENT_MAX_BASE).orElse(0.0f);
            }
        }
        return 0f;
    }

    public float getClimatePercentMaxScale() {
        ListTag base = this.getClimateBase();
        if(!base.isEmpty()) {
            CompoundTag compound = base.getCompound(1).orElse(new CompoundTag());
            if(compound.contains(PERCENT_MAX_BASE)) {
                return compound.getFloat(PERCENT_MAX_BASE).orElse(0.0f);
            }
        }
        return 0f;
    }

    public float getLocationMaxLuck() {
        ListTag base = this.getLocationBase();
        if(!base.isEmpty()) {
            CompoundTag compound = base.getCompound(0).orElse(new CompoundTag());
            if(compound.contains(MAX_BASE)) {
                return compound.getInt(MAX_BASE).orElse(0);
            }
        }
        return 0f;
    }

    public float getLocationMaxScale() {
        ListTag base = this.getLocationBase();
        if(!base.isEmpty()) {
            CompoundTag compound = base.getCompound(1).orElse(new CompoundTag());
            if(compound.contains(MAX_BASE)) {
                return compound.getInt(MAX_BASE).orElse(0);
            }
        }
        return 0f;
    }

    public float getClimateMaxLuck() {
        ListTag base = this.getClimateBase();
        if(!base.isEmpty()) {
            CompoundTag compound = base.getCompound(0).orElse(new CompoundTag());
            if(compound.contains(MAX_BASE)) {
                return compound.getInt(MAX_BASE).orElse(0);
            }
        }
        return 0f;
    }

    public float getClimateMaxScale() {
        ListTag base = this.getClimateBase();
        if(!base.isEmpty()) {
            CompoundTag compound = base.getCompound(1).orElse(new CompoundTag());
            if(compound.contains(MAX_BASE)) {
                return compound.getInt(MAX_BASE).orElse(0);
            }
        }
        return 0f;
    }

    public float getTotalPercent() {
        float lBaseLuck = this.getLocationPercentMaxLuck();
        float lBaseScale = this.getLocationPercentMaxScale();
        float cBaseLuck = this.getClimatePercentMaxLuck();
        float cBaseScale = this.getClimatePercentMaxScale();

        if(lBaseLuck != 0f || lBaseScale != 0f || cBaseLuck != 0f || cBaseScale != 0f) {
            return (lBaseLuck + lBaseScale + cBaseLuck + cBaseScale) / 4;
        }
        return 0f;
    }

    public static PetTagObject of(@NotNull CompoundTag compoundTag, @NotNull ItemStack itemStack) {
        return new PetTagObject(compoundTag, itemStack);
    }

    public static PetTagObject empty() {
        return new PetTagObject(ItemStackHelper.getTag(ItemStack.EMPTY), ItemStack.EMPTY);
    }
}
