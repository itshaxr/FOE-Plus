package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.helper.KeyBindHelper;
import dannypx.foe.helper.SortPlanner;
import dannypx.foe.type.baitsorter.BaitSortOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Bait sorter, ported from FOECollab (io.github.foecollab.handler.BaitSorterHandler, AGPL-3.0)
 * and rewritten for Mojang mappings and Fzzy Config.
 * <p>
 * Press the sort key (default B) while an inventory, chest or vault is open: identical baits and
 * lures (same name, and for lures also same color and size) are merged, then sorted by rarity or
 * amount (the order picked in the settings or last used on the vault's sort buttons).
 * Clicks are queued and sent a few per tick. Only player inventory slots 0-35 are touched.
 */
public class BaitSorterHandler {
    private static BaitSorterHandler INSTANCE = new BaitSorterHandler();

    /** Ticks to wait after a stage so the server's answers have arrived before the next scan. */
    private static final int TICKS_BETWEEN_STAGES = 6;

    private final ArrayDeque<List<SortPlanner.Click>> queue = new ArrayDeque<>();
    /** Stages run in order; each scans the screen fresh because the server decides where shift clicked bait lands. */
    private final ArrayDeque<Supplier<List<List<SortPlanner.Click>>>> stages = new ArrayDeque<>();
    private int containerId = -1;
    private int cooldown = 0;
    /** Click groups we may still send; grows by clickSpeed / 2 every tick. */
    private double credit = 0;
    private boolean wasKeyDown = false;

    public static BaitSorterHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new BaitSorterHandler();
        }
        return INSTANCE;
    }

    private boolean busy() {
        return !queue.isEmpty() || !stages.isEmpty();
    }

    private static boolean isSortableScreen(Minecraft mc) {
        return mc.screen instanceof AbstractContainerScreen<?>
                && !(mc.screen instanceof CreativeModeInventoryScreen);
    }

    /** Called every client tick. Keybinds don't fire while a screen is open, so the key is polled here. */
    public void tick(Minecraft mc) {
        boolean keyDown = isSortableScreen(mc)
                // Typing in a search box must not start a sort.
                && !(mc.screen.getFocused() instanceof EditBox)
                && KeyBindHelper.isPressed(Configs.keyBindConfig.sortBaitsKeybind);
        if (keyDown && !wasKeyDown) {
            start(mc);
        }
        wasKeyDown = keyDown;

        process(mc);
    }

    public void start(Minecraft mc) {
        if (busy() || !isSortableScreen(mc)) {
            return;
        }
        AbstractContainerMenu menu = ((AbstractContainerScreen<?>) mc.screen).getMenu();
        containerId = menu.containerId;
        boolean container = menu instanceof ChestMenu;
        boolean vault = mc.screen.getTitle().getString()
                .startsWith(dannypx.foe.handler.fetch.GenericContainerScreenHandler.PERSONAL_VAULT_SCREEN_CONTAINER);
        // The order you last picked here or with the vault's Amount / Rarity buttons.
        BaitSortOrder order = Configs.keyBindConfig.baitSortOrder.get();

        // Inventory baits first, so there is one bait per name to carry into the chest.
        stages.add(() -> SortPlanner.plan(scan(true), true, false, order));
        if (container) {
            // Merge inventory baits into their stashed twins and shift them back in.
            stages.add(() -> SortPlanner.planCrossMerge(scan(false), scan(true)));
        }
        stages.add(() -> SortPlanner.plan(scan(true), true, true, order));
        if (vault) {
            // The whole vault page, exactly as its Amount / Rarity buttons sort it, so B keeps the
            // order you chose instead of re-sorting the baits by rarity among your other items.
            stages.add(VaultSortHandler.planner(order));
        } else if (container) {
            // Baits cannot stack in a chest, so only rearrange there.
            stages.add(() -> SortPlanner.plan(scan(false), false, true, order));
        }
        cooldown = 0;
        credit = 0;
    }

    /**
     * Runs another sort through the same paced click queue (used by the vault's sort buttons).
     * The plan is made when it runs, from the screen as it is then. Ignored while a sort is running.
     */
    public boolean queueSort(Minecraft mc, Supplier<List<List<SortPlanner.Click>>> plan) {
        if (busy() || !isSortableScreen(mc)) return false;
        containerId = ((AbstractContainerScreen<?>) mc.screen).getMenu().containerId;
        stages.add(plan);
        cooldown = 0;
        credit = 0;
        return true;
    }

    public boolean isSorting() {
        return busy();
    }

    /** Baits and lures in the open screen: player inventory + hotbar when {@code playerSide}, otherwise the chest/vault slots. */
    private List<SortPlanner.Entry> scan(boolean playerSide) {
        List<SortPlanner.Entry> found = new ArrayList<>();
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)) {
            return found;
        }
        for (Slot slot : screen.getMenu().slots) {
            boolean mine = slot.container instanceof Inventory;
            // Player side is container index 0-35 only: never armor or offhand. Chest side is everything else.
            if (mine != playerSide || (mine && slot.getContainerSlot() >= 36)) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) {
                continue;
            }
            CompoundTag tag = data.copyTag();
            found.add(new SortPlanner.Entry(slot.index,
                    tag.getStringOr("type", ""),
                    tag.getStringOr("name", ""),
                    tag.getStringOr("rarity", ""),
                    tag.getStringOr("color", ""),
                    tag.getStringOr("size", ""),
                    tag.getIntOr(dannypx.foe.item.TagObject.COUNTER, stack.getCount())));
        }
        return found;
    }

    private void process(Minecraft mc) {
        if (!busy()) {
            return;
        }
        // Stop between groups (never inside one) if the screen went away.
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)
                || screen.getMenu().containerId != containerId
                || mc.player == null || mc.gameMode == null) {
            queue.clear();
            stages.clear();
            return;
        }
        if (cooldown-- > 0) {
            return;
        }
        credit += Configs.keyBindConfig.baitSortClickSpeed.get() / 2.0;
        while (credit >= 1 && busy()) {
            if (queue.isEmpty()) {
                queue.addAll(stages.poll().get());
                if (queue.isEmpty()) {
                    continue;
                }
            }
            for (SortPlanner.Click click : queue.poll()) {
                mc.gameMode.handleInventoryMouseClick(containerId, click.slot(), 0,
                        click.shift() ? ClickType.QUICK_MOVE : ClickType.PICKUP, mc.player);
            }
            credit--;
            if (queue.isEmpty()) {
                cooldown = TICKS_BETWEEN_STAGES;
                credit = 0;
                return;
            }
        }
    }
}
