package dannypx.foe.handler.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.item.ValidateItem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Each fish's name colour (its rarity colour: Dolly Varden white, Burbot blue...), learned from
 * what the server shows: the Fish Compendium's entries while it's open, and fish in your
 * inventory. Saved in config/fishonmcextras/fish_colors.json. The Mini Event panel uses it to
 * colour fish missions like the compendium does.
 */
public final class FishColorHandler {
    private static FishColorHandler INSTANCE;

    public static FishColorHandler instance() {
        if (INSTANCE == null) INSTANCE = new FishColorHandler();
        return INSTANCE;
    }

    private static final java.util.function.Supplier<Path> FILE = () ->
            FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("fish_colors.json");
    private static final int SCAN_EVERY_TICKS = 20;
    private static final long SAVE_DELAY_MS = 5_000L;

    private final Map<String, Integer> colors = new HashMap<>();
    private boolean loaded = false;
    private boolean dirty = false;
    private long dirtySince = 0L;
    private int ticks = 0;

    private FishColorHandler() {}

    /// The fish's name colour, or -1 when FOE+ hasn't seen that fish yet.
    public int colorOf(String fishName) {
        load();
        if (fishName == null) return -1;
        Integer c = colors.get(key(fishName));
        return c == null ? -1 : c;
    }

    /// Package-private for tests.
    void learn(String fishName, int rgb) {
        if (fishName == null || fishName.isBlank() || rgb < 0) return;
        String k = key(fishName);
        Integer old = colors.put(k, rgb);
        if (old == null || old != rgb) {
            if (!dirty) dirtySince = System.currentTimeMillis();
            dirty = true;
        }
    }

    /// Every second: learn from the open compendium (or any menu showing fish) and your inventory.
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        load();
        if (++ticks >= SCAN_EVERY_TICKS) {
            ticks = 0;
            if (mc.screen instanceof AbstractContainerScreen<?> screen) {
                for (Slot slot : screen.getMenu().slots) learnFrom(slot.getItem());
            } else {
                for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) learnFrom(mc.player.getInventory().getItem(i));
            }
        }
        if (dirty && System.currentTimeMillis() - dirtySince >= SAVE_DELAY_MS) save();
    }

    private void learnFrom(ItemStack stack) {
        if (stack.isEmpty()) return;
        boolean fish = isCompendiumEntry(stack);
        if (!fish) {
            try {
                fish = ValidateItem.isFish(stack).value1();
            } catch (RuntimeException e) {
                fish = false;
            }
        }
        if (!fish) return;
        String name = ChatFormatting.stripFormatting(stack.getHoverName().getString());
        if (name == null) return;
        learn(name.trim(), nameColor(stack.getHoverName()));
    }

    /// A Fish Compendium entry: its lore has the fish's details ("FISH GROUP: TROUTS").
    private static boolean isCompendiumEntry(ItemStack stack) {
        var lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;
        for (Component line : lore.lines()) {
            String t = line.getString().toUpperCase(Locale.ROOT);
            if (t.contains("FISH GROUP") || t.contains("IUCN STATUS")) return true;
        }
        return false;
    }

    /// The colour of the first coloured, non-blank part of a name; -1 when it has none.
    static int nameColor(Component name) {
        return name.visit((style, text) -> text.isBlank() || style.getColor() == null
                        ? Optional.<Integer>empty()
                        : Optional.of(style.getColor().getValue() & 0xFFFFFF),
                Style.EMPTY).orElse(-1);
    }

    /// The line with every part that's a known fish's name in that fish's colour (the rest as is).
    public MutableComponent recolor(Component line) {
        load();
        MutableComponent out = Component.empty();
        line.visit((style, text) -> {
            int c = colors.isEmpty() ? -1 : colorFor(text);
            out.append(Component.literal(text).setStyle(c >= 0 ? style.withColor(c) : style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private int colorFor(String text) {
        String plain = ChatFormatting.stripFormatting(text);
        if (plain == null || plain.isBlank()) return -1;
        Integer c = colors.get(key(plain));
        if (c == null) c = colors.get(key(plain.replaceFirst("^[^\\p{L}]+", "")));
        return c == null ? -1 : c;
    }

    private static String key(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        try {
            Path file = FILE.get();
            if (Files.exists(file)) {
                Map<String, Integer> read = new Gson().fromJson(Files.readString(file), new TypeToken<Map<String, Integer>>() {}.getType());
                if (read != null) colors.putAll(read);
            }
        } catch (Exception e) {
            // start empty
        }
    }

    private void save() {
        dirty = false;
        try {
            Path file = FILE.get();
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(new java.util.TreeMap<>(colors)));
        } catch (Exception e) {
            // try again next change
        }
    }

    /// Tests: no file.
    static FishColorHandler forTests() {
        FishColorHandler h = new FishColorHandler();
        h.loaded = true;
        return h;
    }
}
