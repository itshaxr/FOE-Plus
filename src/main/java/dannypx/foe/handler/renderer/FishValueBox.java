package dannypx.foe.handler.renderer;

import dannypx.foe.config.Configs;
import dannypx.foe.item.TagObject;
import dannypx.foe.item.ValidateItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Beside a chest (not the Personal Vault): what the fish in it, and in your inventory, are worth (the value the
 * server puts on each fish), in a small box under the Pet Sorter, styled like it. Only shows when there are fish.
 */
public final class FishValueBox {
    private FishValueBox() {}

    /// {value of the fish, how many} in the container's own slots and in yours.
    public record Totals(double chest, int chestCount, double mine, int mineCount) {}

    static Totals count(AbstractContainerScreen<?> screen) {
        double chest = 0, mine = 0;
        int cn = 0, mn = 0;
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !ValidateItem.isFish(stack).value1()) continue;
            TagObject fish = ValidateItem.isFish(stack).value2();
            // the fish's own value (getMoney is an auction listing's price: always 0 here)
            double v = fish.getSellValue() * Math.max(1, stack.getCount());
            boolean own = slot.container instanceof Inventory;
            if (own && slot.getContainerSlot() >= 36) continue;
            if (own) { mine += v; mn += stack.getCount(); } else { chest += v; cn += stack.getCount(); }
        }
        return new Totals(chest, cn, mine, mn);
    }

    /// "$1.23M", "$210K", "$950".
    static String money(double v) {
        if (v >= 1_000_000_000) return String.format(Locale.ROOT, "$%.2fB", v / 1e9);
        if (v >= 1_000_000) return String.format(Locale.ROOT, "$%.2fM", v / 1e6);
        if (v >= 10_000) return String.format(Locale.ROOT, "$%.1fK", v / 1e3);
        return String.format(Locale.ROOT, "$%,.0f", v);
    }

    /// Whether the box shows on this menu (there are fish in it or in your inventory).
    public static boolean shown(AbstractContainerScreen<?> screen) {
        if (!Configs.inventoryScreenConfig.showFishValue.get()) return false;
        Totals t = count(screen);
        return t.chestCount() + t.mineCount() > 0;
    }

    /// Draws the box for a container `rows` rows tall, under the Pet Sorter on the right. `what`: "Chest" / "Vault".
    public static void render(GuiGraphics g, AbstractContainerScreen<?> screen, int rows, String what) {
        if (!Configs.inventoryScreenConfig.showFishValue.get()) return;
        Totals t = count(screen);
        if (t.chestCount() + t.mineCount() == 0) return;
        Minecraft mc = Minecraft.getInstance();
        int right = mc.getWindow().getGuiScaledWidth() / 2 + 88;
        int top = mc.getWindow().getGuiScaledHeight() / 2 - (114 + rows * 18) / 2 + 18 + 5 * 22 + 2 + 6;
        draw(g, right + 4, top, t, what);
    }

    /// The box itself, its top left at (x, top).
    /// The box's height (two rows: the menu and your inventory).
    public static final int HEIGHT = ValueBox.height(2);

    public static void draw(GuiGraphics g, int x, int top, Totals t, String what) {
        List<ValueBox.Row> rows = List.of(
                new ValueBox.Row(0x55C8FF, Component.literal(what).withStyle(ChatFormatting.WHITE),
                        Component.literal(t.chestCount() + " fish").withColor(0x8A93A0), Component.literal(money(t.chest())).withStyle(ChatFormatting.GOLD)),
                new ValueBox.Row(0xB0B8C4, Component.literal("Inventory").withStyle(ChatFormatting.WHITE),
                        Component.literal(t.mineCount() + " fish").withColor(0x8A93A0), Component.literal(money(t.mine())).withStyle(ChatFormatting.GOLD)));
        ValueBox.draw(g, x, top, Component.literal("Fish Value"), rows,
                Component.literal(money(t.chest() + t.mine())).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
    }
}
