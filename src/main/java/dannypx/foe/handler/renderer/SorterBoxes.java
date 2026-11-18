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
 * The Bait Sorter and Armor Sorter (left) and Pet Sorter (right) boxes beside a container: the Personal Vault and
 * chests. Shift-click any button for the reverse order.
 */
public final class SorterBoxes {
    private SorterBoxes() {}

    private static final int SORT_WIDTH = 76;      // "Armor Sorter" fits its box
    private static final int BOX_WIDTH = SORT_WIDTH + 8;
    private static final int BOX_HEIGHT = 18 + 3 * 24 + 2;
    private static final int STEP = 22;

    private static int boxHeight(int buttons) { return 18 + buttons * STEP + 2; }

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

        boolean vault = what.equals("page");
        int baitBoxHeight = vault ? 18 + 4 * 24 + 2 : BOX_HEIGHT;
        widgets.add(new TitledBoxWidget(baitBoxX, top, BOX_WIDTH, baitBoxHeight, Component.literal("Bait Sorter")));
        widgets.add(bait("⇅ Amount", VaultSortHandler.Mode.AMOUNT,
                "Sort " + these + " baits by amount, most first.\nShift-click: fewest first.\nPets and other items stay put.\nThe bait sorter (B) keeps this order.", baitBoxX + 4, top + 18));
        widgets.add(bait("⇅ Rarity", VaultSortHandler.Mode.RARITY,
                "Sort " + these + " baits by rarity, rarest first.\nShift-click: most common first.\nPets and other items stay put.\nThe bait sorter (B) keeps this order.", baitBoxX + 4, top + 42));
        widgets.add(bait("⇅ Name", VaultSortHandler.Mode.NAME,
                "Sort " + these + " baits by name, grouped by kind\n(every Crankbait together), A to Z.\nShift-click: Z to A.\nPets and other items stay put.\nThe bait sorter (B) keeps this order.", baitBoxX + 4, top + 66));

        if (vault) {
            widgets.add(Button.builder(Component.literal("Save Layout"), b -> dannypx.foe.handler.logic.VaultLayoutHandler.saveOrForget(Minecraft.getInstance().hasShiftDown()))
                    .bounds(baitBoxX + 4, top + 90, SORT_WIDTH, 20)
                    .tooltip(Tooltip.create(Component.literal("Save this page as it is now: from then on\nthe bait sorter (B) puts every item back in\nthe slot you saved it in, until you use\none of the sort buttons.\nShift-click: forget this page's layout."))).build());
        }

        // Under the Bait Sorter: the Armor Sorter.
        int armorTop = top + baitBoxHeight + 4;
        widgets.add(new TitledBoxWidget(baitBoxX, armorTop, BOX_WIDTH, boxHeight(4), Component.literal("Armor Sorter")));
        widgets.add(armor("⇅ Percent", VaultSortHandler.Mode.PERCENT,
                "Sort " + these + " armor by quality %, highest first.\nShift-click: lowest first.\nEverything else stays put.", baitBoxX + 4, armorTop + 18));
        widgets.add(armor("⇅ Rarity", VaultSortHandler.Mode.RARITY,
                "Sort " + these + " armor by rarity, rarest first\n(same rarity: highest quality first).\nShift-click: most common first.\nEverything else stays put.", baitBoxX + 4, armorTop + 18 + STEP));
        widgets.add(armor("⇅ Climate", VaultSortHandler.Mode.CLIMATE,
                "Sort " + these + " armor by climate, A to Z\n(same climate: highest quality first).\nShift-click: Z to A.\nEverything else stays put.", baitBoxX + 4, armorTop + 18 + STEP * 2));
        widgets.add(armor("⇅ Rolls", VaultSortHandler.Mode.ROLLS,
                "Sort " + these + " armor by its rolled bonuses,\nadded up (+8% and +9.5% count 17.5),\nbest first.\nShift-click: lowest first.\nEverything else stays put.", baitBoxX + 4, armorTop + 18 + STEP * 3));

        widgets.add(new TitledBoxWidget(petBoxX, top, BOX_WIDTH, boxHeight(5), Component.literal("Pet Sorter")));
        widgets.add(pet("⇅ Percent", VaultSortHandler.Mode.PERCENT,
                "Sort " + these + " pets by percent, highest first.\nShift-click: lowest first.\nBaits and other items stay put.", petBoxX + 4, top + 18));
        widgets.add(pet("⇅ Luck", VaultSortHandler.Mode.LUCK,
                "Sort " + these + " pets by luck (climate and\nlocation together), highest first.\nShift-click: lowest first.\nBaits and other items stay put.", petBoxX + 4, top + 18 + STEP));
        widgets.add(pet("⇅ Scale", VaultSortHandler.Mode.SCALE,
                "Sort " + these + " pets by scale (climate and\nlocation together), highest first.\nShift-click: lowest first.\nBaits and other items stay put.", petBoxX + 4, top + 18 + STEP * 2));
        widgets.add(pet("⇅ Rarity", VaultSortHandler.Mode.RARITY,
                "Sort " + these + " pets by rarity, rarest first\n(same rarity: highest percent first).\nShift-click: most common first.\nBaits and other items stay put.", petBoxX + 4, top + 18 + STEP * 3));
        widgets.add(pet("⇅ Name", VaultSortHandler.Mode.NAME,
                "Sort " + these + " pets by name, A to Z.\nShift-click: Z to A.\nBaits and other items stay put.", petBoxX + 4, top + 18 + STEP * 4));
    }

    private static Button armor(String label, VaultSortHandler.Mode mode, String tooltip, int x, int y) {
        return Button.builder(Component.literal(label), b -> VaultSortHandler.sortArmor(mode, Minecraft.getInstance().hasShiftDown()))
                .bounds(x, y, SORT_WIDTH, 20).tooltip(Tooltip.create(Component.literal(tooltip))).build();
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
