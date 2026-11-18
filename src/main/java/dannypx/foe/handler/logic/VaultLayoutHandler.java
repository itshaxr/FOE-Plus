package dannypx.foe.handler.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dannypx.foe.config.Configs;
import dannypx.foe.helper.SortPlanner;
import dannypx.foe.type.baitsorter.BaitSortOrder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Your own Personal Vault layout: arrange a page the way you like, press Save Layout, and from
 * then on the bait sorter (B) puts every item on that page back where you saved it, until you
 * sort with one of the sort buttons (or pick another order in the settings).
 *
 * <p>Saved per page ("Personal Vault #2"), as which item sits in which slot: a bait or lure by its
 * name (and a lure's colour and size), a pet or rod part by its id, anything else by its name.
 * Items that weren't on the page when you saved fill the slots that are left.
 */
public final class VaultLayoutHandler {
    private VaultLayoutHandler() {}

    static Supplier<Path> FILE = () -> net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve(dannypx.foe.FishOnMCExtras.MOD_ID).resolve("vault_layouts.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /// What's saved: each page's slots (slot number → item key), and the order B used before
    /// you saved (it goes back to that when you sort with a pet or armor button).
    static final class Saved {
        Map<String, TreeMap<Integer, String>> pages = new LinkedHashMap<>();
        String orderBefore;
    }

    private static Saved saved;

    //region Item keys
    /// Package-private for tests: what identifies an item for the layout.
    static String key(CompoundTag tag, String itemId, String hoverName) {
        if (tag != null && !tag.isEmpty()) {
            String type = tag.getStringOr("type", "").toLowerCase(Locale.ROOT);
            if (type.equals(SortPlanner.BAIT) || type.equals(SortPlanner.LURE)) {
                return type + "|" + tag.getStringOr("name", "") + "|" + tag.getStringOr("color", "") + "|" + tag.getStringOr("size", "");
            }
            int[] id = tag.getIntArray("id").orElse(null);
            if (id != null && id.length > 0) return "id|" + Arrays.toString(id);
            String uuid = tag.getStringOr("uuid", "");
            if (!uuid.isEmpty()) return "uuid|" + uuid;
            int[] rodId = tag.getIntArray("uuid").orElse(null);
            if (rodId != null && rodId.length > 0) return "id|" + Arrays.toString(rodId);
            String name = tag.getStringOr("name", "");
            if (!name.isEmpty()) return type + "|" + name;
        }
        return itemId + "|" + (hoverName == null ? "" : hoverName.replaceAll("§.", "").trim());
    }

    private static String key(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return key(data == null ? null : data.copyTag(),
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.getHoverName().getString());
    }
    //endregion

    //region The open vault page
    /// The open vault page's title ("Personal Vault #2"), or null when no vault is open.
    static String page() {
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)
                || !(screen.getMenu() instanceof ChestMenu)) return null;
        String title = screen.getTitle().getString().replaceAll("[\\uE000-\\uF8FF]", "").trim();
        return title.startsWith(dannypx.foe.handler.fetch.GenericContainerScreenHandler.PERSONAL_VAULT_SCREEN_CONTAINER) ? title : null;
    }

    /// The page's item slots and what's in each (null: empty).
    private static Map<Integer, ItemStack> pageSlots() {
        Map<Integer, ItemStack> out = new TreeMap<>();
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)
                || !(screen.getMenu() instanceof ChestMenu chest)) return out;
        int itemSlots = chest.getRowCount() * 9 - 9; // the bottom row holds the page arrows
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof Inventory || slot.index >= itemSlots) continue;
            out.put(slot.index, slot.getItem().isEmpty() ? null : slot.getItem());
        }
        return out;
    }
    //endregion

    //region Saving and B
    /// Saves the open page as it is now, and makes B keep it. Shift: forget this page's layout.
    public static void saveOrForget(boolean forget) {
        Minecraft mc = Minecraft.getInstance();
        String page = page();
        if (page == null) return;
        load();
        if (forget) {
            saved.pages.remove(page);
            save();
            message(mc, "Saved layout for " + page + " forgotten.", ChatFormatting.YELLOW);
            return;
        }
        TreeMap<Integer, String> slots = new TreeMap<>();
        pageSlots().forEach((slot, stack) -> { if (stack != null) slots.put(slot, key(stack)); });
        saved.pages.put(page, slots);
        BaitSortOrder now = Configs.keyBindConfig.baitSortOrder.get();
        if (now != BaitSortOrder.SAVED_LAYOUT) saved.orderBefore = now.name();
        save();
        if (now != BaitSortOrder.SAVED_LAYOUT) {
            Configs.keyBindConfig.baitSortOrder.accept(BaitSortOrder.SAVED_LAYOUT);
            Configs.keyBindConfig.save();
        }
        message(mc, "Layout saved for " + page + ": B will keep it (" + slots.size() + " items) until you sort another way.", ChatFormatting.GREEN);
    }

    /// Whether the open page has a saved layout.
    public static boolean hasLayout() {
        String page = page();
        if (page == null) return false;
        load();
        return saved.pages.containsKey(page);
    }

    /// Back to the order B used before Save Layout (a pet or armor sort button was used).
    public static void leaveSavedOrder() {
        if (Configs.keyBindConfig.baitSortOrder.get() != BaitSortOrder.SAVED_LAYOUT) return;
        load();
        BaitSortOrder back = BaitSortOrder.COMMON_TO_LEGENDARY;
        try { if (saved.orderBefore != null) back = BaitSortOrder.valueOf(saved.orderBefore); } catch (IllegalArgumentException ignored) {}
        if (back == BaitSortOrder.SAVED_LAYOUT) back = BaitSortOrder.COMMON_TO_LEGENDARY;
        Configs.keyBindConfig.baitSortOrder.accept(back);
        Configs.keyBindConfig.save();
    }

    /// B on a vault page with a saved layout: put every item back in its saved slot, planned when it runs.
    public static Supplier<List<List<SortPlanner.Click>>> planner() {
        return () -> {
            String page = page();
            if (page == null) return List.of();
            load();
            TreeMap<Integer, String> layout = saved.pages.get(page);
            if (layout == null) return List.of();
            Map<Integer, String> now = new LinkedHashMap<>();
            List<Integer> slots = new ArrayList<>();
            pageSlots().forEach((slot, stack) -> {
                slots.add(slot);
                if (stack != null) now.put(slot, key(stack));
            });
            return plan(layout, now, slots);
        };
    }

    /// Package-private for tests: the moves that put the items in {@code now} (slot → key) where
    /// {@code layout} has them. Each saved slot takes an item with its key (the one already there
    /// first); items with no saved slot (or more of a key than were saved) stay put when they can,
    /// else fill the slots that are left.
    static List<List<SortPlanner.Click>> plan(Map<Integer, String> layout, Map<Integer, String> now, List<Integer> slots) {
        // One object per item, compared by identity.
        Map<Integer, Object> state = new HashMap<>();
        Map<Object, String> keyOf = new java.util.IdentityHashMap<>();
        for (var en : now.entrySet()) {
            Object item = new Object();
            state.put(en.getKey(), item);
            keyOf.put(item, en.getValue());
        }
        java.util.Set<Object> placed = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<Object> desired = new ArrayList<>();
        List<Integer> targets = new ArrayList<>();
        // already in place first, so they never move
        for (var en : layout.entrySet()) {
            Object there = state.get(en.getKey());
            if (there != null && en.getValue().equals(keyOf.get(there)) && slots.contains(en.getKey())) placed.add(there);
        }
        for (var en : layout.entrySet()) {
            if (!slots.contains(en.getKey())) continue;
            Object there = state.get(en.getKey());
            Object pick = there != null && placed.contains(there) && en.getValue().equals(keyOf.get(there)) ? there : null;
            if (pick == null) {
                for (int s : slots) {
                    Object o = state.get(s);
                    if (o != null && !placed.contains(o) && en.getValue().equals(keyOf.get(o))) { pick = o; break; }
                }
                if (pick == null) continue; // that item isn't on the page now
                placed.add(pick);
            }
            desired.add(pick);
            targets.add(en.getKey());
        }
        return SortPlanner.arrange(state, desired, targets);
    }
    //endregion

    //region File
    private static void load() {
        if (saved != null) return;
        saved = new Saved();
        try {
            Path f = FILE.get();
            if (f == null || !Files.exists(f)) return;
            Saved s = GSON.fromJson(Files.readString(f), Saved.class);
            if (s != null && s.pages != null) saved = s;
        } catch (Throwable ignored) {
            // nothing saved yet (or a broken file): starts empty
        }
    }

    private static void save() {
        try {
            Path f = FILE.get();
            if (f == null) return;
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(saved));
        } catch (Throwable ignored) {
            // kept for this session
        }
    }

    /// For tests.
    static void reset() { saved = new Saved(); }

    private static void message(Minecraft mc, String text, ChatFormatting color) {
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(text).withStyle(color), true);
    }
    //endregion
}
