package dannypx.foe.handler.fetch;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers what the last few chest menus held (each page counts as its own), so /foe menu dump
 * can copy them to the clipboard. Read only: it never clicks anything. Used to learn a server
 * menu's layout before FOE+ builds a screen for it.
 */
public final class MenuDumpHandler {
    private static final MenuDumpHandler INSTANCE = new MenuDumpHandler();
    public static MenuDumpHandler instance() { return INSTANCE; }

    private static final int KEEP = 8;
    /// The last KEEP different menu states, oldest first.
    private final LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
    private long lastLook = 0L;

    private MenuDumpHandler() {}

    /// Called while a chest menu is on screen: notes what it holds, twice a second at most.
    public void look(Screen screen) {
        if (!(screen instanceof ContainerScreen chest)) return;
        long now = System.currentTimeMillis();
        if (now - lastLook < 500L) return;
        lastLook = now;
        remember(dump(chest.getTitle().getString(), chest.getMenu()));
    }

    /// Package-private for tests: keeps the newest KEEP different states, a repeat moving to the end.
    void remember(String dump) {
        seen.remove(dump);
        seen.put(dump, Boolean.TRUE);
        while (seen.size() > KEEP) seen.remove(seen.keySet().iterator().next());
    }

    /// Everything remembered, oldest first ("" when no menu has been opened).
    public String all() {
        StringBuilder out = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, Boolean> e : seen.entrySet()) {
            out.append("===== FOE+ menu dump ").append(++n).append(" of ").append(seen.size()).append(" =====\n").append(e.getKey()).append('\n');
        }
        return out.toString();
    }

    public int count() { return seen.size(); }

    private static String dump(String title, ChestMenu menu) {
        StringBuilder out = new StringBuilder("Title: ").append(title).append("\nTitle codes:");
        title.chars().forEach(c -> out.append(' ').append(Integer.toHexString(c)));
        int rows = menu.getRowCount();
        out.append("\nRows: ").append(rows).append('\n');
        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.isEmpty()) continue;
            out.append(i < rows * 9 ? "\n[menu slot " : "\n[inventory slot ").append(i).append("] ")
                    .append(BuiltInRegistries.ITEM.getKey(stack.getItem())).append(" x").append(stack.getCount())
                    .append("\nName: ").append(stack.getHoverName().getString());
            var lore = stack.get(DataComponents.LORE);
            if (lore != null) lore.lines().forEach(l -> out.append("\nLore: ").append(l.getString()));
            var model = stack.get(DataComponents.ITEM_MODEL);
            if (model != null) out.append("\nModel: ").append(model);
            var cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
            if (cmd != null) out.append("\nModelData: ").append(cmd);
            var data = stack.get(DataComponents.CUSTOM_DATA);
            if (data != null) out.append("\nData: ").append(data.copyTag());
            out.append('\n');
        }
        return out.toString();
    }
}
