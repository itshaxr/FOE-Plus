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
        super(dannypx.foe.helper.HudFine.get(Configs.hudConfig.gearWarningXPosition),
                dannypx.foe.helper.HudFine.get(Configs.hudConfig.gearWarningYPosition),
                Configs.hudConfig.gearWarningAlignment.get(),
                Component.literal("Gear Warning"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showGearWarning.get() || (Configs.hudConfig.showInventoryWarning.get() && !Configs.hudConfig.inventoryWarningBig.get() && !Configs.hudConfig.showInventoryWarningPanel.get()); }
    @Override protected float configX() { return dannypx.foe.helper.HudFine.get(Configs.hudConfig.gearWarningXPosition); }
    @Override protected float configY() { return dannypx.foe.helper.HudFine.get(Configs.hudConfig.gearWarningYPosition); }
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
        // Hidden with the dismiss key (default -) until you press it again.
        if (!isCopy && dannypx.foe.handler.logic.WarningDismissHandler.instance().isDismissed()) return lines;
        gearLines(mc, lines);
        // last: how to hide it
        if (!lines.isEmpty() && Configs.hudConfig.gearWarningDismissHint.get()) {
            lines.add(Component.literal("Press \"" + dannypx.foe.handler.logic.WarningDismissHandler.key() + "\" to dismiss")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        return lines;
    }

    private void gearLines(Minecraft mc, List<Component> lines) {

        // Inventory almost full (FOE's FullInventoryHandler warning)
        dannypx.foe.handler.logic.FullInventoryHandler inventory = dannypx.foe.handler.logic.FullInventoryHandler.instance();
        // (with the big warning on, it's at the top of the screen instead)
        // (in its own box instead, when that's on)
        if (inventory.isAlmostFull() && !Configs.hudConfig.inventoryWarningBig.get() && !Configs.hudConfig.showInventoryWarningPanel.get()) {
            lines.add(Component.empty()
                    .append(Component.literal("Inventory almost full. Slots left: ").withStyle(ChatFormatting.RED))
                    .append(Component.literal(String.valueOf(inventory.getSlotsLeft())).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(".").withStyle(ChatFormatting.RED)));
        }

        if (!Configs.hudConfig.showGearWarning.get() || !TabOverlayHandler.instance().isInInstance()) return;

        FoeLocations.Location location = FoeLocations.fromName(BossEventHandler.instance().getLocation().getString());
        if (location == null) return; // hub, crew island, or unknown
        // the place's name in its own colour (as /tt shows it)
        Component locationName = FoeLocations.styledName(FoeLocations.displayName(location));
        // Cypress Lake's spawn area: no warning until you get to the water (like FOE).
        if (!isCopy && dannypx.foe.handler.logic.PetLocationHandler.inCypressHub()) return;

        // Armor: shown for a few seconds when it starts being wrong (you arrive, or put a piece on), then hidden
        List<Component> armorLines = new ArrayList<>();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            Pair<Boolean, ArmorTagObject> armor = ValidateItem.isArmor(stack);
            if (!armor.value1()) continue;
            FoeLocations.Climate climate = FoeLocations.climateFromId(armor.value2().getString("name"));
            if (climate != null && climate != location.climate) {
                armorLines.add(Component.empty()
                        .append(Component.literal("You have equipped a ").withStyle(ChatFormatting.RED))
                        .append(stack.getHoverName())
                        .append(Component.literal(" in a ").withStyle(ChatFormatting.RED))
                        .append(location.climate.tag())
                        .append(Component.literal(" location.").withStyle(ChatFormatting.RED)));
            }
        }

        if (isCopy || armorShown(armorSignature(armorLines, location), System.currentTimeMillis())) lines.addAll(armorLines);

        // Rod: every part made for the water here (always shown while it's wrong) (salt at a freshwater place, or fresh at a saltwater one).
        if (Configs.hudConfig.showWrongRodWarning.get()) lines.addAll(wrongRodLines(mc, location, locationName));

        // Pet: must be this exact location's pet, not just one from the same climate (not while this location's
        // cryptid is out: you'd wear the cryptid's gear then).
        if (PetLocationHandler.isWrongPet(location) && (isCopy || !cryptidHere(location))) {
            PetTagObject pet = InventoryHandler.instance().getCurrentPet();
            lines.add(Component.empty()
                    .append(Component.literal("Your ").withStyle(ChatFormatting.RED))
                    .append(pet.getName())
                    .append(Component.literal(" is not the ").withStyle(ChatFormatting.RED))
                    .append(locationName)
                    .append(Component.literal(" pet.").withStyle(ChatFormatting.RED)));
            lines.add(PetLocationHandler.suggestion(location));
        }
    }

    /// How long a wrong-armour warning stays up.
    static final long ARMOR_MS = 3_000L;
    private static String armorSeen = "";
    private static long armorSince = 0L;

    private static String armorSignature(List<Component> armorLines, FoeLocations.Location location) {
        StringBuilder sb = new StringBuilder(location.name());
        for (Component c : armorLines) sb.append('|').append(c.getString());
        return armorLines.isEmpty() ? "" : sb.toString();
    }

    /// Package-private for tests: whether a wrong-armour warning (this signature: the place and the pieces) is
    /// still up: for ARMOR_MS after it first appears, again whenever it changes.
    static boolean armorShown(String signature, long now) {
        if (signature.isEmpty()) { armorSeen = ""; return false; }
        if (!signature.equals(armorSeen)) { armorSeen = signature; armorSince = now; }
        return now - armorSince < ARMOR_MS;
    }

    /// This location's cryptid is out now.
    private static boolean cryptidHere(FoeLocations.Location location) {
        dannypx.foe.handler.logic.AlertHandler alerts = dannypx.foe.handler.logic.AlertHandler.instance();
        for (dannypx.foe.handler.logic.AlertHandler.Active a : alerts.active()) {
            if (a.alert() != dannypx.foe.handler.logic.AlertHandler.Alert.FABLED && alerts.locationOf(a.alert()) == location) return true;
        }
        return false;
    }

    /// "Your Marlin Reel is for Saltwater, but Kenai River is Freshwater." for each part of the rod in your hand
    /// (or, when you're holding something else, the first rod on your hotbar) that's for the other water.
    static List<Component> wrongRodLines(Minecraft mc, FoeLocations.Location location, Component locationName) {
        List<Component> out = new ArrayList<>();
        ItemStack rod = mc.player.getMainHandItem();
        if (!ValidateItem.isFishingRod(rod).value1()) {
            rod = ItemStack.EMPTY;
            for (int i = 0; i < 9; i++) {
                ItemStack s = mc.player.getInventory().getItem(i);
                if (ValidateItem.isFishingRod(s).value1()) { rod = s; break; }
            }
        }
        if (rod.isEmpty()) return out;
        net.minecraft.nbt.CompoundTag rodTag = dannypx.foe.helper.ItemStackHelper.getTag(rod);
        if (rodTag == null) return out;
        java.util.Set<dannypx.foe.helper.RodWater.Water> here = dannypx.foe.helper.RodWater.at(location);
        // the rod itself, when it's marked for a water
        dannypx.foe.helper.RodWater.Water rodWater = dannypx.foe.helper.RodWater.of(rodTag.getStringOr("water", ""));
        if (rodWater != null && !here.contains(rodWater)) {
            dannypx.foe.helper.RodWater.Water place = here.iterator().next();
            out.add(Component.empty().append(Component.literal("Your ").withStyle(ChatFormatting.RED)).append(rod.getHoverName())
                    .append(Component.literal(" is for ").withStyle(ChatFormatting.RED)).append(Component.literal(rodWater.label).withColor(rodWater.color))
                    .append(Component.literal(", but ").withStyle(ChatFormatting.RED)).append(locationName)
                    .append(Component.literal(" is ").withStyle(ChatFormatting.RED)).append(Component.literal(place.label).withColor(place.color))
                    .append(Component.literal(".").withStyle(ChatFormatting.RED)));
        }
        String[][] kinds = {{"line", "Line"}, {"pole", "Pole"}, {"reel", "Reel"}, {"activeBait", "Bait"}};
        for (String[] kind : kinds) {
            net.minecraft.nbt.ListTag list = rodTag.getListOrEmpty(kind[0]);
            for (int i = 0; i < list.size(); i++) {
                net.minecraft.nbt.CompoundTag part = list.getCompoundOrEmpty(i);
                dannypx.foe.helper.RodWater.Water w = dannypx.foe.helper.RodWater.of(waterOf(part));
                if (w == null || here.contains(w)) continue;
                dannypx.foe.helper.RodWater.Water place = here.iterator().next();
                out.add(Component.empty()
                        .append(Component.literal("Your ").withStyle(ChatFormatting.RED))
                        .append(partName(mc, part, kind[1]))
                        .append(Component.literal(" is for ").withStyle(ChatFormatting.RED))
                        .append(Component.literal(w.label).withColor(w.color))
                        .append(Component.literal(", but ").withStyle(ChatFormatting.RED))
                        .append(locationName)
                        .append(Component.literal(" is ").withStyle(ChatFormatting.RED))
                        .append(Component.literal(place.label).withColor(place.color))
                        .append(Component.literal(".").withStyle(ChatFormatting.RED)));
            }
        }
        return out;
    }

    /// Package-private for tests: a stored rod part's "water" (its item data as the rod keeps it: {id, count,
    /// components: {"minecraft:custom_data": {water: "saltwater", ...}}}), read straight from the data so a part
    /// whose item can't be rebuilt here still counts.
    static String waterOf(net.minecraft.nbt.CompoundTag part) {
        net.minecraft.nbt.CompoundTag data = part.getCompoundOrEmpty("components").getCompoundOrEmpty("minecraft:custom_data");
        String w = data.getStringOr("water", "");
        return w.isEmpty() ? part.getStringOr("water", "") : w;
    }

    /// A stored part's name ("Marlin Reel"), rebuilt with the game's registries; "Reel" when it can't be.
    private static Component partName(Minecraft mc, net.minecraft.nbt.CompoundTag part, String fallback) {
        try {
            var ops = mc.level != null ? net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, mc.level.registryAccess())
                    : net.minecraft.nbt.NbtOps.INSTANCE;
            ItemStack s = ItemStack.CODEC.parse((com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag>) ops, part).result().orElse(ItemStack.EMPTY);
            if (!s.isEmpty()) return s.getHoverName();
        } catch (RuntimeException ignored) {
        }
        return Component.literal(fallback).withStyle(ChatFormatting.GOLD);
    }

    @Override
    protected List<Component> previewLines() {
        return List.of(Component.literal("Your Eagle Pet is not the Danube River pet.").withStyle(ChatFormatting.RED),
                Component.literal("Use a ").withStyle(ChatFormatting.RED).append(Component.literal("Wolf Pet").withStyle(ChatFormatting.GOLD)));
    }
}
