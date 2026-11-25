package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.FullInventoryHandler;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The inventory almost full warning in a box of its own (HUD settings → Inventory Warning Box), so it stands apart
 * from the wrong gear warnings and can go anywhere. Off by default: the line is in the Wrong Gear box then.
 */
public class InventoryWarningPanelElement extends PanelElement {
    public InventoryWarningPanelElement() {
        this(false);
    }

    public InventoryWarningPanelElement(boolean isCopy) {
        super(dannypx.foe.helper.HudFine.get(Configs.hudConfig.inventoryWarningPanelXPosition),
                dannypx.foe.helper.HudFine.get(Configs.hudConfig.inventoryWarningPanelYPosition),
                Configs.hudConfig.inventoryWarningPanelAlignment.get(),
                Component.literal("Inventory Warning"),
                isCopy);
    }

    @Override protected boolean enabled() {
        var c = Configs.hudConfig;
        return c.showInventoryWarningPanel.get() && c.showInventoryWarning.get() && !c.inventoryWarningBig.get();
    }
    @Override protected float configX() { return dannypx.foe.helper.HudFine.get(Configs.hudConfig.inventoryWarningPanelXPosition); }
    @Override protected float configY() { return dannypx.foe.helper.HudFine.get(Configs.hudConfig.inventoryWarningPanelYPosition); }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.inventoryWarningPanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.inventoryWarningPanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.inventoryWarningPanelOpacity.get(); }

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
        return Component.literal("⚠ ɪɴᴠᴇɴᴛᴏʀʏ").withStyle(ChatFormatting.BOLD, ChatFormatting.RED);
    }

    @Override
    protected List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        FullInventoryHandler inventory = FullInventoryHandler.instance();
        if (inventory.isAlmostFull()) lines.add(line(inventory.getSlotsLeft()));
        return lines;
    }

    @Override
    protected List<Component> previewLines() {
        return List.of(line(2));
    }

    private static Component line(int left) {
        return Component.empty()
                .append(Component.literal("Inventory almost full. Slots left: ").withStyle(ChatFormatting.RED))
                .append(Component.literal(String.valueOf(left)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(".").withStyle(ChatFormatting.RED));
    }
}
