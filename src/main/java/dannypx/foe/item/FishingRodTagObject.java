package dannypx.foe.item;

import dannypx.foe.helper.ItemStackHelper;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class FishingRodTagObject extends TagObject {
    private static final String LINE = "line";
    private List<TagObject> lineItem = List.of();
    private static final String POLE = "pole";
    private List<TagObject> poleItem = List.of();
    private static final String REEL = "reel";
    private List<TagObject> reelItem = List.of();
    private static final String TACKLEBOX = "tacklebox";
    private List<TagObject> tackleBox = List.of();
    public static final String ACTIVE_BAIT = "activeBait";
    private List<TagObject> activeBait = List.of();
    public static final String DISABLE_BAIT = "disableBait";

    public FishingRodTagObject(CompoundTag compoundTag, ItemStack itemStack) {
        super(compoundTag, itemStack);
        if(itemStack != ItemStack.EMPTY) {
            this.init();
        }
    }

    private void init() {
        this.lineItem = this.getItemStackList(LINE);
        this.poleItem = this.getItemStackList(POLE);
        this.reelItem = this.getItemStackList(REEL);
        this.tackleBox = this.getItemStackList(TACKLEBOX);
        this.activeBait = this.getItemStackList(ACTIVE_BAIT);
        // in case the server renames it: any other list on the rod named for its bait
        if (this.activeBait.isEmpty()) {
            for (String key : this.compoundTag.keySet()) {
                String k = key.toLowerCase(java.util.Locale.ROOT);
                if (key.equals(ACTIVE_BAIT) || !k.contains("bait") || k.contains("disable")) continue;
                List<TagObject> found = this.getItemStackList(key);
                if (!found.isEmpty()) { this.activeBait = found; break; }
            }
        }
        if (this.activeBait.isEmpty() && this.contains(ACTIVE_BAIT) && !loggedNoBait) {
            loggedNoBait = true;
            dannypx.foe.handler.logic.LoggerHandler.info("Rod has no bait FOE+ can read; its data has: " + this.compoundTag.keySet()
                    + (this.contains(ACTIVE_BAIT) ? " | activeBait=" + String.valueOf(this.get(ACTIVE_BAIT)).substring(0, Math.min(600, String.valueOf(this.get(ACTIVE_BAIT)).length())) : ""));
        }
    }

    private static boolean loggedNoBait = false;

    public List<TagObject> getLineItem() {
        return this.lineItem;
    }

    public List<TagObject> getPoleItem() {
        return this.poleItem;
    }

    public List<TagObject> getReelItem() {
        return this.reelItem;
    }

    public List<TagObject> getTackleBox() {
        return this.tackleBox;
    }

    public List<TagObject> getActiveBait() {
        return this.activeBait;
    }

    public boolean getDisableBait() {
        if (this.contains(DISABLE_BAIT)) {
            return this.getBoolean(DISABLE_BAIT);
        }
        return false;
    }

    public static FishingRodTagObject of(@NotNull CompoundTag compoundTag, @NotNull ItemStack itemStack) {
        return new FishingRodTagObject(compoundTag, itemStack);
    }

    public static FishingRodTagObject empty() {
        return new FishingRodTagObject(ItemStackHelper.getTag(ItemStack.EMPTY), ItemStack.EMPTY);
    }
}
