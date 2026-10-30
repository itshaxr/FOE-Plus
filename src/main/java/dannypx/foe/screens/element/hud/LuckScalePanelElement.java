package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.InventoryHandler;
import dannypx.foe.handler.logic.PetLocationHandler;
import dannypx.foe.helper.FoeLocations;
import dannypx.foe.item.ArmorTagObject;
import dannypx.foe.item.PetTagObject;
import dannypx.foe.item.ValidateItem;
import dannypx.foe.type.Alignment;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Your current Luck and Scale, in FOE's panel style. Recomputed every frame from what you have
 * equipped and where you are, so it changes the moment you swap pet or armor or move:
 * <ul>
 *   <li>pet: its location bonus ({@code lbase}) at its own location, its climate bonus
 *       ({@code cbase}) in its climate;</li>
 *   <li>armor: each piece's Luck/Scale ({@code base} list, like FOECollab's Armor type) in its climate.</li>
 * </ul>
 * Outside a fishing location it shows what your gear can give at most.
 */
public class LuckScalePanelElement extends PanelElement {
    private static final int LUCK_COLOR = 0x80DAC3;
    private static final int SCALE_COLOR = 0x4C88F1;

    public LuckScalePanelElement() {
        this(false);
    }

    public LuckScalePanelElement(boolean isCopy) {
        super(Configs.hudConfig.luckScalePanelXPosition.get() / 100f,
                Configs.hudConfig.luckScalePanelYPosition.get() / 100f,
                Configs.hudConfig.luckScalePanelAlignment.get(),
                Component.literal("Luck & Scale Panel"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showLuckScalePanel.get(); }
    @Override protected float configX() { return Configs.hudConfig.luckScalePanelXPosition.get() / 100f; }
    @Override protected float configY() { return Configs.hudConfig.luckScalePanelYPosition.get() / 100f; }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.luckScalePanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.luckScalePanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.luckScalePanelOpacity.get(); }

    @Override
    protected boolean alignTextToAnchor() {
        return false;
    }

    @Override
    protected Component title() {
        return Component.literal("ʟᴜᴄᴋ & ѕᴄᴀʟᴇ").withStyle(ChatFormatting.BOLD);
    }

    /// Luck and scale from one source.
    public record Stats(int luck, int scale) {
        Stats plus(Stats o) {
            return new Stats(luck + o.luck, scale + o.scale);
        }
    }

    /// Pet bonus that applies at {@code here} (everything when {@code here} is null).
    public static Stats petStats(FoeLocations.Location here) {
        if (!InventoryHandler.instance().hasPet()) return new Stats(0, 0);
        PetTagObject pet = InventoryHandler.instance().getCurrentPet();
        Stats total = new Stats(0, 0);
        FoeLocations.Location petLocation = PetLocationHandler.locationOf(pet);
        if (here == null || petLocation == here) total = total.plus(read(pet.getLocationBase()));
        FoeLocations.Climate petClimate = FoeLocations.climateFromId(pet.getString("climate"));
        if (here == null || petClimate == here.climate) total = total.plus(read(pet.getClimateBase()));
        return total;
    }

    /// Armor bonus that applies at {@code here} (everything when {@code here} is null).
    public static Stats armorStats(FoeLocations.Location here) {
        Minecraft mc = Minecraft.getInstance();
        Stats total = new Stats(0, 0);
        if (mc.player == null) return total;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            Pair<Boolean, ArmorTagObject> armor = ValidateItem.isArmor(stack);
            if (!armor.value1()) continue;
            FoeLocations.Climate climate = FoeLocations.climateFromId(armor.value2().getString("name"));
            if (here != null && climate != null && climate != here.climate) continue;
            total = total.plus(read(armor.value2().getList("base")));
        }
        return total;
    }

    /// {@code [ {cur: luck}, {cur: scale}, ... ]}, the layout pets and armor share.
    private static Stats read(ListTag base) {
        if (base == null || base.isEmpty()) return new Stats(0, 0);
        CompoundTag luck = base.getCompound(0).orElse(new CompoundTag());
        CompoundTag scale = base.size() > 1 ? base.getCompound(1).orElse(new CompoundTag()) : new CompoundTag();
        return new Stats(luck.getIntOr("cur", 0), scale.getIntOr("cur", 0));
    }

    @Override
    protected List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        if (Minecraft.getInstance().player == null) return lines;
        FoeLocations.Location here = PetLocationHandler.currentLocation();
        Stats pet = petStats(here);
        Stats armor = armorStats(here);
        Stats total = pet.plus(armor);

        lines.add(statLine("♣ ʟᴜᴄᴋ ", total.luck(), LUCK_COLOR, pet.luck(), armor.luck()));
        lines.add(statLine("⚓ ѕᴄᴀʟᴇ ", total.scale(), SCALE_COLOR, pet.scale(), armor.scale()));
        if (here == null) lines.add(label("ɴᴏᴛ ᴀᴛ ᴀ ꜰɪѕʜɪɴɢ ѕᴘᴏᴛ: ɢᴇᴀʀ ᴍᴀxɪᴍᴜᴍ"));
        return lines;
    }

    private static Component statLine(String name, int value, int color, int fromPet, int fromArmor) {
        List<Component> parts = new ArrayList<>();
        parts.add(Component.literal(name).withColor(color));
        parts.add(Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE));
        if (Configs.hudConfig.luckScalePanelBreakdown.get()) {
            parts.add(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY));
            parts.add(label("ᴘᴇᴛ " + fromPet));
            parts.add(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            parts.add(label("ᴀʀᴍᴏʀ " + fromArmor));
            parts.add(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
        }
        return join(parts.toArray(new Component[0]));
    }

    @Override
    protected List<Component> previewLines() {
        return List.of(statLine("♣ ʟᴜᴄᴋ ", 245, LUCK_COLOR, 120, 125), statLine("⚓ ѕᴄᴀʟᴇ ", 130, SCALE_COLOR, 60, 70));
    }
}
