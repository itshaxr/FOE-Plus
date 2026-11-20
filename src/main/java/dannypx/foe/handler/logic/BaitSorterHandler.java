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
 * Bait sorter by landofif (dofi), Minecraft: merryfish. Written for FOECollab
 * (io.github.foecollab.handler.BaitSorterHandler, Kimberlyow/FOECollab pull requests #10 and #11, AGPL-3.0),
 * ported here and rewritten for Mojang mappings and Fzzy Config.
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

        boolean savedLayout = order == BaitSortOrder.SAVED_LAYOUT;

        // Inventory baits first, so there is one bait per name to carry into the chest.
        stages.add(() -> SortPlanner.plan(scan(true), true, false, order));
        if (container) {
            // Twins that are only in the chest: one of each comes into the inventory first, so
            // the cross merge below can combine them all.
            stages.add(() -> SortPlanner.planPullTwins(scan(false), scan(true), emptyInventorySlots()));
            // Merge inventory baits with all their stashed twins and shift them back in.
            stages.add(() -> SortPlanner.planCrossMerge(scan(false), scan(true)));
        }
        // With a saved vault layout the inventory is only merged, not rearranged.
        stages.add(() -> SortPlanner.plan(scan(true), true, !savedLayout, order));
        if (vault && savedLayout) {
            // Every item back where you saved it (nothing moves on a page with no saved layout).
            stages.add(VaultLayoutHandler.planner());
        } else if (vault) {
            // The whole vault page, exactly as its Amount / Rarity buttons sort it, so B keeps the
            // order you chose instead of re-sorting the baits by rarity among your other items.
            stages.add(VaultSortHandler.planner(order));
        } else if (container && !savedLayout) {
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

    /** Empty slots in the main inventory and hotbar (0-35). */
    private static int emptyInventorySlots() {
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)) return 0;
        int n = 0;
        for (Slot slot : screen.getMenu().slots)
            if (slot.container instanceof Inventory && slot.getContainerSlot() < 36 && slot.getItem().isEmpty()) n++;
        return n;
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
            int amount = tag.getIntOr(dannypx.foe.item.TagObject.COUNTER, stack.getCount());
            String name = tag.getStringOr("name", "");
            if (name.isEmpty()) name = SortPlanner.plainColor(stack.getHoverName().getString());
            java.util.List<String> lore = new ArrayList<>();
            var loreData = stack.get(DataComponents.LORE);
            if (loreData != null) for (var line : loreData.lines()) lore.add(line.getString());
            found.add(new SortPlanner.Entry(slot.index,
                    tag.getStringOr("type", ""),
                    name,
                    tag.getStringOr("rarity", ""),
                    tag.getStringOr("color", ""),
                    tag.getStringOr("size", ""),
                    amount,
                    SortPlanner.look(lore, amount),
                    SortPlanner.LURE.equals(tag.getStringOr("type", "")) ? sameData(tag) : ""));
        }
        logLureMisses(found);
        return found;
    }

    /// What makes a lure the lure it is, from its own data: name, rarity, colour, size, intricacy and water, each as
    /// read (codes and small capitals made plain). Not how many uses it has left, nor its rolls. Two lures alike in
    /// these are the same lure; if the server ever won't merge two, the sorter puts the picked-up one back.
    static String sameData(CompoundTag tag) {
        StringBuilder b = new StringBuilder();
        for (String k : SortPlanner.LURE_FIELDS)
            b.append(k).append('=').append(SortPlanner.plainValue(tag.getStringOr(k, ""))).append('|');
        return b.toString();
    }

    /// Lures that share a name but won't merge, in the log with what tells them apart (their colour, size and
    /// tooltip as the server wrote them), so a lure that never combines can be looked into. Once per set of lures.
    private static final java.util.Set<String> LOGGED = new java.util.HashSet<>();

    private static void logLureMisses(List<SortPlanner.Entry> found) {
        try {
            java.util.Map<String, List<SortPlanner.Entry>> byName = new java.util.LinkedHashMap<>();
            for (SortPlanner.Entry e : found)
                if (SortPlanner.LURE.equals(e.type())) byName.computeIfAbsent(SortPlanner.plainColor(e.name()), k -> new ArrayList<>()).add(e);
            for (var g : byName.entrySet()) {
                java.util.Set<String> keys = new java.util.HashSet<>();
                for (SortPlanner.Entry e : g.getValue()) keys.add(String.valueOf(e.mergeKey()));
                if (g.getValue().size() < 2 || keys.size() < 2) continue;
                StringBuilder b = new StringBuilder("[FOE+ bait sorter] lures named \"" + g.getKey() + "\" won't merge:");
                for (SortPlanner.Entry e : g.getValue())
                    b.append("\n  slot ").append(e.slot()).append(" name=").append(e.name()).append(" color=").append(e.color())
                            .append(" size=").append(e.size()).append(" rarity=").append(e.rarity()).append(" key=").append(e.mergeKey())
                            .append(" look=").append(e.look().replace("\n", " / ")).append(" data=").append(e.data());
                if (LOGGED.add(b.toString()) && LOGGED.size() < 200) LoggerHandler.info(b.toString());
            }
        } catch (RuntimeException ignored) {
            // only a note in the log
        }
    }

    /// The slot the last group picked up from (-1: none), to put back a lure the server wouldn't merge.
    private int lastPick = -1;
    /// Ticks until the check after the last click that nothing is left on the cursor (0: none due).
    private int finalCheck = 0;

    /// Puts down whatever is on the cursor: where the last group picked it up if that's empty, else in an empty slot
    /// of yours (a merge the server didn't take leaves the picked-up lure there).
    private void putBack(Minecraft mc, AbstractContainerMenu menu) {
        if (mc.player == null || mc.gameMode == null || menu.getCarried().isEmpty()) return;
        int to = -1;
        if (lastPick >= 0 && lastPick < menu.slots.size() && menu.slots.get(lastPick).getItem().isEmpty()) to = lastPick;
        if (to < 0) for (Slot slot : menu.slots)
            if (slot.container instanceof Inventory && slot.getContainerSlot() < 36 && slot.getItem().isEmpty()) { to = slot.index; break; }
        if (to < 0) return;
        mc.gameMode.handleInventoryMouseClick(menu.containerId, to, 0, ClickType.PICKUP, mc.player);
    }

    private void process(Minecraft mc) {
        if (finalCheck > 0 && --finalCheck == 0 && mc.screen instanceof AbstractContainerScreen<?> open
                && open.getMenu().containerId == containerId) {
            putBack(mc, open.getMenu());
        }
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
        // a merge the server didn't take (two lures it doesn't count as one) leaves the picked-up one on the cursor:
        // put it back where it came from (that slot is empty now) before going on
        putBack(mc, screen.getMenu());
        credit += Configs.keyBindConfig.baitSortClickSpeed.get() / 2.0;
        while (credit >= 1 && busy()) {
            if (queue.isEmpty()) {
                queue.addAll(stages.poll().get());
                if (queue.isEmpty()) {
                    continue;
                }
            }
            List<SortPlanner.Click> group = queue.poll();
            lastPick = group.isEmpty() || group.get(0).shift() ? -1 : group.get(0).slot();
            for (SortPlanner.Click click : group) {
                mc.gameMode.handleInventoryMouseClick(containerId, click.slot(), 0,
                        click.shift() ? ClickType.QUICK_MOVE : ClickType.PICKUP, mc.player);
            }
            credit--;
            if (queue.isEmpty() && stages.isEmpty()) {
                // the last one: nothing may stay on the cursor (checked once the server has answered)
                finalCheck = 3;
            }
            if (queue.isEmpty()) {
                cooldown = TICKS_BETWEEN_STAGES;
                credit = 0;
                return;
            }
        }
    }
}
