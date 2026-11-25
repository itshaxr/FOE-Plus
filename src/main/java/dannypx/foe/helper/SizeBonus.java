package dannypx.foe.helper;

import dannypx.foe.handler.fetch.BossEventHandler;
import dannypx.foe.item.FishingRodTagObject;
import dannypx.foe.item.TagObject;
import dannypx.foe.item.ValidateItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Your size bonus right now: the size bonuses on your gear (the lure or bait on your rod, the rod, your armour),
 * added together as the game does ("any size bonuses from lures, armor rolls, or location rolls are added together",
 * wiki). A bonus under "While fishing Nile River" (or "Freshwater", or a climate) counts only there.
 */
public final class SizeBonus {
    private SizeBonus() {}

    /// "Sizes +1%", "Size: +2.5%", "+3% Size", "+3% Sizes".
    static final Pattern AFTER = Pattern.compile("(?i)\\bsizes?\\b\\s*:?\\s*([+-]\\s*\\d+(?:\\.\\d+)?)\\s*%");
    static final Pattern BEFORE = Pattern.compile("(?i)([+-]\\s*\\d+(?:\\.\\d+)?)\\s*%\\s*sizes?\\b");
    static final Pattern WHILE = Pattern.compile("(?i)^\\s*while fishing(?: in| at)?\\s+(.+?)\\s*:?\\s*$");

    /// The size bonus in percent from these lore lines, at a place described by `here` (its name, climate and water,
    /// in lower case). Package-private for tests.
    static float fromLore(List<String> lines, List<String> here) {
        float total = 0f;
        String when = null;
        for (String raw : lines) {
            String line = raw == null ? "" : raw.replaceAll("[\\uE000-\\uF8FF]", "").trim();
            if (line.isEmpty()) { when = null; continue; }
            Matcher w = WHILE.matcher(line);
            if (w.find()) { when = w.group(1).toLowerCase(Locale.ROOT); continue; }
            Matcher m = AFTER.matcher(line);
            if (!m.find()) {
                m = BEFORE.matcher(line);
                if (!m.find()) continue;
            }
            if (when != null && !applies(when, here)) continue;
            try {
                total += Float.parseFloat(m.group(1).replace(" ", ""));
            } catch (NumberFormatException ignored) {
                // not a number after all
            }
        }
        return total;
    }

    private static boolean applies(String when, List<String> here) {
        for (String h : here) if (!h.isEmpty() && (when.contains(h) || h.contains(when))) return true;
        return false;
    }

    /// Your size bonus now (in percent), from the gear you have on.
    public static float current() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0f;
        List<String> here = new ArrayList<>();
        FoeLocations.Location place = FoeLocations.fromName(BossEventHandler.instance().getLocation().getString());
        if (place != null) {
            here.add(FoeLocations.displayName(place).toLowerCase(Locale.ROOT));
            here.add(place.climate.name.toLowerCase(Locale.ROOT));
            for (RodWater.Water water : RodWater.at(place)) here.add(water.label.toLowerCase(Locale.ROOT));
        }
        List<ItemStack> gear = new ArrayList<>();
        ItemStack rod = mc.player.getMainHandItem();
        gear.add(rod);
        try {
            var validated = ValidateItem.isFishingRod(rod);
            if (validated.value1() && validated.value2() != null) {
                FishingRodTagObject r = validated.value2();
                for (TagObject bait : r.getActiveBait()) gear.add(bait.getItemStack());
            }
        } catch (RuntimeException ignored) {
            // not a rod FOE+ knows
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            gear.add(mc.player.getItemBySlot(slot));
        }
        float total = 0f;
        for (ItemStack stack : gear) {
            if (stack == null || stack.isEmpty()) continue;
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null) continue;
            List<String> lines = new ArrayList<>();
            for (Component c : lore.lines()) lines.add(c.getString());
            total += fromLore(lines, here);
        }
        return total;
    }
}
