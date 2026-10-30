package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.InventoryHandler;
import dannypx.foe.item.PetHeldItem;
import dannypx.foe.item.PetTagObject;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/// Equipped pet in FOE's panel style: pet icon, name and level, XP progress and rating.
/// Ported from FOECollab's PetEquipHud / PetEquipHudHandler (AGPL-3.0); the pet data comes
/// from FOE-R's inventory tracking.
public class PetPanelElement extends PanelElement {
    public PetPanelElement() {
        this(false);
    }

    public PetPanelElement(boolean isCopy) {
        super(Configs.hudConfig.petPanelXPosition.get() / 100f,
                Configs.hudConfig.petPanelYPosition.get() / 100f,
                Configs.hudConfig.petPanelAlignment.get(),
                Component.literal("Pet Panel"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showPetPanel.get(); }
    @Override protected float configX() { return Configs.hudConfig.petPanelXPosition.get() / 100f; }
    @Override protected float configY() { return Configs.hudConfig.petPanelYPosition.get() / 100f; }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.petPanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.petPanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.petPanelOpacity.get(); }

    @Override
    protected boolean alignTextToAnchor() {
        return false;
    }

    @Override
    protected Component title() {
        return Component.literal("ᴘᴇᴛ").withStyle(ChatFormatting.BOLD);
    }

    private static boolean hasPet() {
        return InventoryHandler.instance().hasPet();
    }

    private static final int PET_BORDER = 0xFF4C88F1;
    private static final int HELD_ITEM_BORDER = 0xFFD9A441;

    /// The pet, then the item it holds (an empty box when it holds nothing).
    @Override
    protected List<PanelIcon> icons() {
        if (!hasPet()) return List.of();
        PetTagObject pet = InventoryHandler.instance().getCurrentPet();
        return List.of(new PanelIcon(pet.getItemStack(), PET_BORDER),
                new PanelIcon(PetHeldItem.of(pet), HELD_ITEM_BORDER));
    }

    private static ChatFormatting progressColor(double value) {
        return value <= 25 ? ChatFormatting.RED
                : value <= 50 ? ChatFormatting.GOLD
                : value <= 75 ? ChatFormatting.YELLOW
                : ChatFormatting.GREEN;
    }

    @Override
    protected List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        if (!hasPet()) {
            lines.add(Component.literal("No pet equipped").withStyle(ChatFormatting.RED));
            return lines;
        }

        PetTagObject pet = InventoryHandler.instance().getCurrentPet();
        int level = pet.getLevel();
        lines.add(Component.empty()
                .append(pet.getRarityComponent())
                .append(Component.literal(" "))
                .append(pet.getName())
                .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("ʟᴠʟ ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(level)).withStyle(progressColor(level)))
                .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY)));

        float current = pet.contains(PetTagObject.XP_CURRENT) ? pet.getFloat(PetTagObject.XP_CURRENT) : 0f;
        float needed = pet.contains(PetTagObject.XP_NEED) ? pet.getFloat(PetTagObject.XP_NEED) : 0f;
        boolean max = level >= 100 || needed <= 0;
        float percent = max ? 100f : Math.min(100f, current / needed * 100f);
        lines.add(Component.empty()
                .append(Component.literal("xᴘ ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("(").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(compact(current)).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("/").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(max ? "MAX" : compact(needed)).withStyle(ChatFormatting.BLUE))
                .append(Component.literal(") ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.format("%.1f%%", percent)).withStyle(progressColor(percent))));

        ItemStack held = PetHeldItem.of(pet);
        lines.add(Component.empty()
                .append(Component.literal("ʜᴇʟᴅ ɪᴛᴇᴍ ").withStyle(ChatFormatting.GRAY))
                .append(held.isEmpty()
                        ? Component.literal("ɴᴏɴᴇ").withStyle(ChatFormatting.DARK_GRAY)
                        : held.getHoverName().copy()));

        Component rating = pet.getRatingComponent();
        if (!rating.getString().isBlank()) {
            lines.add(Component.empty()
                    .append(Component.literal("ʀᴀᴛɪɴɢ ").withStyle(ChatFormatting.GRAY))
                    .append(rating)
                    .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(String.format("%.1f%%", pet.getTotalPercent() * 100f)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY)));
        }
        return lines;
    }

    /// 950 → "950", 23500 → "23.5k", 201000 → "201k", 1500000 → "1.5m".
    static String compact(float value) {
        double v = Math.round(value);
        String suffix = "";
        if (Math.abs(v) >= 1_000_000_000) { v /= 1_000_000_000; suffix = "b"; }
        else if (Math.abs(v) >= 1_000_000) { v /= 1_000_000; suffix = "m"; }
        else if (Math.abs(v) >= 1_000) { v /= 1_000; suffix = "k"; }
        if (suffix.isEmpty()) return String.valueOf((long) v);
        String text = v >= 100 ? String.valueOf((long) Math.floor(v)) : String.format(java.util.Locale.ROOT, "%.1f", Math.floor(v * 10) / 10);
        if (text.endsWith(".0")) text = text.substring(0, text.length() - 2);
        return text + suffix;
    }

    @Override
    protected List<Component> previewLines() {
        return List.of(Component.literal("No pet equipped").withStyle(ChatFormatting.RED));
    }
}
