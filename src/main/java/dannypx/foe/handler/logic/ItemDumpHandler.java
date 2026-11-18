package dannypx.foe.handler.logic;

import dannypx.foe.item.TagObject;
import dannypx.foe.item.ValidateItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * /foe item dump: the next items you hover are written to a file, exactly as the server sent them
 * (every tooltip line with each of its parts, colours and fonts, and the item's data), next to what
 * FOE+ read from them (rarity, rating, quality, variant, size). When the server changes how items
 * look, one of these files shows what moved, so FOE+ can be fixed to match.
 *
 * <p>Each different item hovered while it's on goes into the same file (up to {@link #MAX} of
 * them), so a pet, a fish, a bait and a piece of armour can be done in one go.
 */
public final class ItemDumpHandler {
    private static final ItemDumpHandler INSTANCE = new ItemDumpHandler();
    public static ItemDumpHandler instance() { return INSTANCE; }

    static final int MAX = 8;
    private boolean armed = false;
    private int count = 0, fileNo = 0;
    private StringBuilder out;
    private final java.util.Set<String> seen = new java.util.HashSet<>();

    public synchronized void arm() {
        armed = true;
        count = 0;
        fileNo++;
        seen.clear();
        out = new StringBuilder("FOE+ item dump (").append(dannypx.foe.FishOnMCExtras.MOD_ID).append(")\n");
    }

    public synchronized void stop() {
        if (!armed) return;
        armed = false;
        write(true);
    }

    /// An item's tooltip is being made (hovered).
    public synchronized void onTooltip(ItemStack stack) {
        if (!armed || stack == null || stack.isEmpty()) return;
        // only what you hover (the game also makes tooltips in the background, for searching)
        if (!net.minecraft.client.Minecraft.getInstance().isSameThread()) return;
        String key = stack.getHoverName().getString() + "|" + stack.getItem();
        if (!seen.add(key)) return;
        count++;
        out.append("\n==================== ITEM ").append(count).append(" ====================\n");
        out.append("item: ").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())).append(" x").append(stack.getCount()).append('\n');
        out.append("name: ").append(MinigameDumpHandler.describe(stack.getHoverName())).append('\n');
        var lore = stack.get(DataComponents.LORE);
        List<Component> lines = lore == null ? List.of() : lore.lines();
        out.append("lore: ").append(lines.size()).append(" lines\n");
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            out.append(String.format("LINE %2d: %s\n", i, line.getString()));
            List<Component> parts = line.getSiblings();
            for (int j = 0; j < parts.size(); j++)
                out.append(String.format("   part %d: %s\n", j, MinigameDumpHandler.describe(parts.get(j))));
            if (parts.isEmpty()) out.append("   (one part) ").append(MinigameDumpHandler.describe(line)).append('\n');
        }
        var data = stack.get(DataComponents.CUSTOM_DATA);
        out.append("data: ").append(data == null ? "none" : data.copyTag().toString()).append('\n');
        // what FOE+ makes of it
        try {
            var valid = ValidateItem.isServerItem(stack, true);
            out.append("FOE+ sees a server item: ").append(valid.value1()).append('\n');
            if (valid.value1()) {
                TagObject t = valid.value2();
                out.append("  rarity: '").append(t.getRarityComponent().getString()).append("'\n");
                var pet = ValidateItem.isPet(t);
                if (pet.value1()) out.append("  pet rating: '").append(pet.value2().getRatingComponent().getString()).append("'\n");
                var armor = ValidateItem.isArmor(t);
                if (armor.value1()) out.append("  armour quality: '").append(armor.value2().getQualityComponent().getString()).append("'\n");
                if (t instanceof dannypx.foe.item.FishTagObject fish)
                    out.append("  fish variant: '").append(fish.getVariantComponent().getString()).append("'  size: '").append(fish.getFishSizeComponent().getString()).append("'\n");
                out.append("  frame border: '").append(t.getBorderComponent().getString()).append("'\n");
            }
        } catch (Throwable e) {
            out.append("  FOE+ failed reading it: ").append(e).append('\n');
        }
        write(false);
        if (count >= MAX) { armed = false; write(true); }
    }

    private void write(boolean done) {
        Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve(dannypx.foe.FishOnMCExtras.MOD_ID).resolve("item_dump_" + fileNo + ".txt");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, out.toString());
            String msg = done ? "Done: " + count + " item(s) saved to config/fishonmcextras/item_dump_" + fileNo + ".txt. Send that file."
                    : "Item " + count + " saved (" + file.getFileName() + "). Hover the next one, or /foe item dump stop.";
            dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.line("ITEMS", dannypx.foe.helper.FoeChat.GREEN)
                    .append(dannypx.foe.helper.FoeChat.text(msg)));
        } catch (Exception e) {
            LoggerHandler.error(e.toString());
        }
    }
}
