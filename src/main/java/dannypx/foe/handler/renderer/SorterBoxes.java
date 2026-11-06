package dannypx.foe.handler.renderer;

import dannypx.foe.handler.logic.VaultSortHandler;
import dannypx.foe.screens.widget.TitledBoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The Bait Sorter (left) and Pet Sorter (right) boxes beside a container: the Personal Vault and
 * chests. Shift-click any button for the reverse order.
 */
public final class SorterBoxes {
    private SorterBoxes() {}

    private static final int SORT_WIDTH = 64;
    private static final int BOX_WIDTH = SORT_WIDTH + 8;
    private static final int BOX_HEIGHT = 18 + 3 * 24 + 2;

    /// Adds both boxes for a container {@code rows} rows tall (centred on screen, like vanilla);
    /// {@code what} is how the tooltips call it ("page" for the vault, "chest").
    public static void add(List<AbstractWidget> widgets, int rows, String what) {
        Minecraft mc = Minecraft.getInstance();
        int left = mc.getWindow().getGuiScaledWidth() / 2 - 88;
        int right = mc.getWindow().getGuiScaledWidth() / 2 + 88;
        int top = mc.getWindow().getGuiScaledHeight() / 2 - (114 + rows * 18) / 2;
        int baitBoxX = left - 4 - BOX_WIDTH;
        int petBoxX = right + 4;
        String these = "this " + what + "'s";

        widgets.add(new TitledBoxWidget(baitBoxX, top, BOX_WIDTH, BOX_HEIGHT, Component.literal("Bait Sorter")));
        widgets.add(bait("⇅ Amount", VaultSortHandler.Mode.AMOUNT,
                "Sort " + these + " baits by amount, most first.\nShift-click: fewest first.\nPets and other items stay put.\nThe bait sorter (B) keeps this order.", baitBoxX + 4, top + 18));
        widgets.add(bait("⇅ Rarity", VaultSortHandler.Mode.RARITY,
                "Sort " + these + " baits by rarity, rarest first.\nShift-click: most common first.\nPets and other items stay put.\nThe bait sorter (B) keeps this order.", baitBoxX + 4, top + 42));
        widgets.add(bait("⇅ Name", VaultSortHandler.Mode.NAME,
                "Sort " + these + " baits by name, grouped by kind\n(every Crankbait together), A to Z.\nShift-click: Z to A.\nPets and other items stay put.\nThe bait sorter (B) keeps this order.", baitBoxX + 4, top + 66));

        widgets.add(new TitledBoxWidget(petBoxX, top, BOX_WIDTH, BOX_HEIGHT, Component.literal("Pet Sorter")));
        widgets.add(pet("⇅ Percent", VaultSortHandler.Mode.PERCENT,
                "Sort " + these + " pets by percent, highest first.\nShift-click: lowest first.\nBaits and other items stay put.", petBoxX + 4, top + 18));
        widgets.add(pet("⇅ Rarity", VaultSortHandler.Mode.RARITY,
                "Sort " + these + " pets by rarity, rarest first\n(same rarity: highest percent first).\nShift-click: most common first.\nBaits and other items stay put.", petBoxX + 4, top + 42));
        widgets.add(pet("⇅ Name", VaultSortHandler.Mode.NAME,
                "Sort " + these + " pets by name, A to Z.\nShift-click: Z to A.\nBaits and other items stay put.", petBoxX + 4, top + 66));
    }

    private static Button bait(String label, VaultSortHandler.Mode mode, String tooltip, int x, int y) {
        return Button.builder(Component.literal(label), b -> VaultSortHandler.sort(mode, Minecraft.getInstance().hasShiftDown()))
                .bounds(x, y, SORT_WIDTH, 20).tooltip(Tooltip.create(Component.literal(tooltip))).build();
    }

    private static Button pet(String label, VaultSortHandler.Mode mode, String tooltip, int x, int y) {
        return Button.builder(Component.literal(label), b -> VaultSortHandler.sortPets(mode, Minecraft.getInstance().hasShiftDown()))
                .bounds(x, y, SORT_WIDTH, 20).tooltip(Tooltip.create(Component.literal(tooltip))).build();
    }
}
