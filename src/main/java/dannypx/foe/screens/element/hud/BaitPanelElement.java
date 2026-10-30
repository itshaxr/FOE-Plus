package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.InventoryHandler;
import dannypx.foe.item.FishingRodTagObject;
import dannypx.foe.item.TagObject;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/// The bait or lure on your rod and how many are left, in FOE's panel style.
/// Ported from FOECollab's BaitHud (AGPL-3.0); replaces the bait slot of FOE-R's old hotbar.
public class BaitPanelElement extends PanelElement {
    public BaitPanelElement() {
        this(false);
    }

    public BaitPanelElement(boolean isCopy) {
        super(Configs.hudConfig.baitPanelXPosition.get() / 100f,
                Configs.hudConfig.baitPanelYPosition.get() / 100f,
                Configs.hudConfig.baitPanelAlignment.get(),
                Component.literal("Bait Panel"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showBaitPanel.get(); }
    @Override protected float configX() { return Configs.hudConfig.baitPanelXPosition.get() / 100f; }
    @Override protected float configY() { return Configs.hudConfig.baitPanelYPosition.get() / 100f; }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.baitPanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.baitPanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.baitPanelOpacity.get(); }

    @Override
    protected Component title() {
        return Component.literal("ʙᴀɪᴛ").withStyle(ChatFormatting.BOLD);
    }

    private static TagObject activeBait() {
        FishingRodTagObject rod = InventoryHandler.instance().getCurrentFishingRod();
        if (rod == null || rod.getDisableBait() || rod.getActiveBait().isEmpty()) return null;
        return rod.getActiveBait().getFirst();
    }

    @Override
    protected List<PanelIcon> icons() {
        TagObject bait = activeBait();
        return bait == null ? List.of() : List.of(new PanelIcon(bait.getItemStack(), 0xFFFFFFFF));
    }

    @Override
    protected List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        TagObject bait = activeBait();
        if (bait == null) return lines;
        lines.add(Component.empty()
                .append(Component.literal(bait.getItemStack().getHoverName().getString()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(bait.getCount())).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("x").withStyle(ChatFormatting.GRAY)));
        return lines;
    }

    @Override
    protected List<Component> previewLines() {
        return List.of(Component.literal("Bait: 0x").withStyle(ChatFormatting.WHITE));
    }
}
