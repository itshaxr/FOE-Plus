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
        super(dannypx.foe.helper.HudFine.get(Configs.hudConfig.baitPanelXPosition),
                dannypx.foe.helper.HudFine.get(Configs.hudConfig.baitPanelYPosition),
                Configs.hudConfig.baitPanelAlignment.get(),
                Component.literal("Bait Panel"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showBaitPanel.get(); }
    @Override protected float configX() { return dannypx.foe.helper.HudFine.get(Configs.hudConfig.baitPanelXPosition); }
    @Override protected float configY() { return dannypx.foe.helper.HudFine.get(Configs.hudConfig.baitPanelYPosition); }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.baitPanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.baitPanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.baitPanelOpacity.get(); }

    /// The FOE-R look: no words, just the bait in a slot like the bottom bar's, how many are left under it.
    static final int SLOT_W = 28, SLOT_H = 30;

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g) {
        if (!foeR()) {
            super.render(g);
            return;
        }
        if (!enabled()) return;
        // inside another HUD that's showing it: it's there, not here too
        if (!showAlone && HudCombine.shownInHost(lookId())) {
            this.width = 0;
            this.height = 0;
            return;
        }
        if (!isCopy) {
            xPos = configX();
            yPos = configY();
            alignment = configAlignment();
        }
        ItemStack stack;
        long count;
        TagObject bait = activeBait();
        if (bait != null) {
            stack = bait.getItemStack();
            count = bait.getCount();
        } else if (isCopy) {
            stack = new ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS);
            count = 14_000;
        } else {
            this.width = 0;
            this.height = 0;
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        float scale = dannypx.foe.utils.HudScale.crisp(scale());
        this.width = Math.round(SLOT_W * scale);
        this.height = Math.round(SLOT_H * scale);
        int sw = (int) (mc.getWindow().getGuiScaledWidth() / scale), sh = (int) (mc.getWindow().getGuiScaledHeight() / scale);
        boolean right = alignment == Alignment.TOP_RIGHT || alignment == Alignment.BOTTOM_RIGHT || alignment == Alignment.RIGHT;
        boolean bottom = alignment == Alignment.BOTTOM_LEFT || alignment == Alignment.BOTTOM_RIGHT || alignment == Alignment.BOTTOM;
        boolean centered = alignment == Alignment.TOP || alignment == Alignment.BOTTOM || alignment == Alignment.CENTER;
        int ax = right ? sw - Math.round(sw * xPos) : Math.round(sw * xPos);
        int ay = bottom ? sh - Math.round(sh * yPos) : Math.round(sh * yPos);
        int x = centered ? ax - SLOT_W / 2 : right ? ax - SLOT_W : ax;
        int y = bottom ? ay - SLOT_H : ay;
        x = Math.max(0, Math.min(x, sw - SLOT_W));
        y = Math.max(0, Math.min(y, sh - SLOT_H));
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        FoeRHotbar.frame(g, x, y, SLOT_W, SLOT_H);
        g.renderItem(stack, x + (SLOT_W - 16) / 2, y + 3);
        String n = shortCount(count);
        var font = mc.font;
        g.drawString(font, n, x + (SLOT_W - font.width(n) + 1) / 2, y + SLOT_H - 11, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    /// 950 → "950", 1,500 → "1.5K", 14,000 → "14K", 2,300,000 → "2.3M". Package-private for tests.
    static String shortCount(long n) {
        if (n < 1000) return String.valueOf(n);
        double v = n >= 1_000_000 ? n / 1_000_000.0 : n / 1000.0;
        String unit = n >= 1_000_000 ? "M" : "K";
        String s = v >= 100 ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v).replaceAll("\\.0$", "");
        return s + unit;
    }

    @Override
    protected Component title() {
        return Component.literal("ʙᴀɪᴛ").withStyle(ChatFormatting.BOLD);
    }

    /// The tacklebox is locked (the server keeps this bait on the rod).
    private static boolean locked() {
        FishingRodTagObject rod = InventoryHandler.instance().getCurrentFishingRod();
        return rod != null && rod.getDisableBait() && dannypx.foe.config.Configs.hudConfig.showBaitLock.get();
    }

    private static TagObject activeBait() {
        FishingRodTagObject rod = InventoryHandler.instance().getCurrentFishingRod();
        // (disableBait is the tacklebox lock: the bait is still on the rod and still used, so it still shows)
        if (rod == null || rod.getActiveBait().isEmpty()) return null;
        return rod.getActiveBait().getFirst();
    }

    @Override
    protected List<PanelIcon> icons() {
        TagObject bait = activeBait();
        // locked: a red frame and a red padlock in its corner, so it can't be missed
        return bait == null ? List.of() : List.of(locked() ? new PanelIcon(bait.getItemStack(), 0xFFFF3B3B, true)
                : new PanelIcon(bait.getItemStack(), 0xFFFFFFFF));
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
                .append(Component.literal("x").withStyle(ChatFormatting.GRAY))
                .append(locked() ? Component.literal(" ʟᴏᴄᴋᴇᴅ").withColor(0xFF3B3B).withStyle(ChatFormatting.BOLD) : Component.empty()));
        return lines;
    }

    @Override
    protected List<Component> previewLines() {
        return List.of(Component.literal("Bait: 0x").withStyle(ChatFormatting.WHITE));
    }
}
