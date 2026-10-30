package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.InventoryHandler;
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

    @Override
    protected ItemStack icon() {
        return hasPet() ? InventoryHandler.instance().getCurrentPet().getItemStack() : null;
    }

    @Override
    protected int iconBorderColor() {
        return 0xFF4C88F1;
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
                .append(Component.literal(String.valueOf(Math.round(current))).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("/").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(max ? "MAX" : String.valueOf(Math.round(needed))).withStyle(ChatFormatting.BLUE))
                .append(Component.literal(") ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.format("%.1f%%", percent)).withStyle(progressColor(percent))));

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

    @Override
    protected List<Component> previewLines() {
        return List.of(Component.literal("No pet equipped").withStyle(ChatFormatting.RED));
    }
}
