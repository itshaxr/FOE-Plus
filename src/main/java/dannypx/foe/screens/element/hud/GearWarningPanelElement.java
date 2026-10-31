package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.BossEventHandler;
import dannypx.foe.handler.fetch.TabOverlayHandler;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * FOE's wrong-gear warning: stays on screen while your equipped pet or armor doesn't fit where
 * you are fishing, and disappears once you swap to the right gear.
 * <ul>
 *   <li>Armor is wrong when its climate isn't the climate of the current location.</li>
 *   <li>A pet is wrong when it is not the current location's own pet (same climate is not enough).</li>
 * </ul>
 * Ported from FOECollab's ArmorHandler / PetEquipHandler / NotificationHudHandler (AGPL-3.0).
 */
public class GearWarningPanelElement extends PanelElement {
    public GearWarningPanelElement() {
        this(false);
    }

    public GearWarningPanelElement(boolean isCopy) {
        super(Configs.hudConfig.gearWarningXPosition.get() / 100f,
                Configs.hudConfig.gearWarningYPosition.get() / 100f,
                Configs.hudConfig.gearWarningAlignment.get(),
                Component.literal("Gear Warning"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showGearWarning.get() || Configs.hudConfig.showInventoryWarning.get(); }
    @Override protected float configX() { return Configs.hudConfig.gearWarningXPosition.get() / 100f; }
    @Override protected float configY() { return Configs.hudConfig.gearWarningYPosition.get() / 100f; }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.gearWarningAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.gearWarningScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.gearWarningOpacity.get(); }

    @Override
    protected boolean alignTextToAnchor() {
        return false;
    }

    @Override
    protected boolean centerText() {
        return true;
    }

    @Override
    protected Component title() {
        return Component.literal("⚠ ᴡᴀʀɴɪɴɢ").withStyle(ChatFormatting.BOLD, ChatFormatting.RED);
    }

    @Override
    protected List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return lines;
        // Hidden with the dismiss key (default -) until you change location.
        if (!isCopy && dannypx.foe.handler.logic.WarningDismissHandler.instance().isDismissed()) return lines;

        // Inventory almost full (FOE's FullInventoryHandler warning)
        dannypx.foe.handler.logic.FullInventoryHandler inventory = dannypx.foe.handler.logic.FullInventoryHandler.instance();
        if (inventory.isAlmostFull()) {
            lines.add(Component.empty()
                    .append(Component.literal("Inventory almost full. Slots left: ").withStyle(ChatFormatting.RED))
                    .append(Component.literal(String.valueOf(inventory.getSlotsLeft())).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(".").withStyle(ChatFormatting.RED)));
        }

        if (!Configs.hudConfig.showGearWarning.get() || !TabOverlayHandler.instance().isInInstance()) return lines;

        Component locationName = BossEventHandler.instance().getLocation();
        FoeLocations.Location location = FoeLocations.fromName(locationName.getString());
        if (location == null) return lines; // hub, crew island, or unknown

        // Armor
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            Pair<Boolean, ArmorTagObject> armor = ValidateItem.isArmor(stack);
            if (!armor.value1()) continue;
            FoeLocations.Climate climate = FoeLocations.climateFromId(armor.value2().getString("name"));
            if (climate != null && climate != location.climate) {
                lines.add(Component.empty()
                        .append(Component.literal("You have equipped a ").withStyle(ChatFormatting.RED))
                        .append(stack.getHoverName())
                        .append(Component.literal(" in a ").withStyle(ChatFormatting.RED))
                        .append(location.climate.tag())
                        .append(Component.literal(" location.").withStyle(ChatFormatting.RED)));
            }
        }

        // Pet: must be this exact location's pet, not just one from the same climate.
        if (PetLocationHandler.isWrongPet(location)) {
            PetTagObject pet = InventoryHandler.instance().getCurrentPet();
            lines.add(Component.empty()
                    .append(Component.literal("Your ").withStyle(ChatFormatting.RED))
                    .append(pet.getName())
                    .append(Component.literal(" is not the ").withStyle(ChatFormatting.RED))
                    .append(locationName)
                    .append(Component.literal(" pet.").withStyle(ChatFormatting.RED)));
            lines.add(PetLocationHandler.suggestion(location));
        }
        return lines;
    }

    @Override
    protected List<Component> previewLines() {
        return List.of(Component.literal("Your Eagle Pet is not the Danube River pet.").withStyle(ChatFormatting.RED),
                Component.literal("Use a ").withStyle(ChatFormatting.RED).append(Component.literal("Wolf Pet").withStyle(ChatFormatting.GOLD)));
    }
}
