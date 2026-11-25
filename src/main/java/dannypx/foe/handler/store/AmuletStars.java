package dannypx.foe.handler.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dannypx.foe.FishOnMCExtras;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Your colour for the equipped-pet star of each amulet (Bigfoot Amulet, Cryptid Amulet...): picked with the colour
 * wheel in O → Customize → Amulet Stars. Every amulet FOE+ has seen on a pet is listed there, with the ones it knows.
 */
public final class AmuletStars {
    private AmuletStars() {}

    private static final class Saved {
        Map<String, String> colours = new TreeMap<>();
        Map<String, String> seen = new TreeMap<>();
        /// What each amulet looks like (the item, without its name and lines), as seen in your inventory or a menu.
        Map<String, com.google.gson.JsonElement> pictures = new TreeMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Saved saved;
    /// Goes up with each change (the item markers' cache of star colours starts again).
    public static volatile int version = 0;

    /// Amulets FOE+ knows of (the crafting menu's pet items), listed even before one is seen.
    static final List<String> KNOWN = List.of("Wade's Amulet", "Midas Amulet", "Onyx Amulet", "Ivory Amulet", "Fabled Amulet",
            "Bigfoot's Necklace", "Bigfoot's Amulet", "Moon Amulet", "Cryptid Amulet", "Titan's Graceful Necklace", "Prospecting Amulet");

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("amulet_stars.json");
    }

    /// "Bigfoot's Amulet" and "Bigfoot Amulet" are the same one.
    static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("['’]s\\b", "").replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static Saved saved() {
        if (saved != null) return saved;
        saved = new Saved();
        try {
            if (Files.exists(file())) {
                Saved s = GSON.fromJson(Files.readString(file()), Saved.class);
                if (s != null) {
                    // kept under today's keys (an older one kept "Bigfoot's Amulet" apart from "Bigfoot Amulet")
                    if (s.colours != null) s.colours.forEach((k, v) -> { if (!key(k).isEmpty()) saved.colours.putIfAbsent(key(k), v); });
                    if (s.seen != null) s.seen.forEach((k, v) -> { if (!key(k).isEmpty()) saved.seen.putIfAbsent(key(k), v); });
                    if (s.pictures != null) saved.pictures.putAll(s.pictures);
                }
            }
        } catch (Exception e) {
            dannypx.foe.handler.logic.LoggerHandler.error(e);
        }
        return saved;
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(saved()));
        } catch (Exception e) {
            dannypx.foe.handler.logic.LoggerHandler.error(e);
        }
    }

    /// An amulet seen on a pet (by its plain name): remembered for the list.
    public static void seen(String name) {
        String k = key(name);
        if (k.isEmpty() || saved().seen.containsKey(k)) return;
        saved().seen.put(k, name.trim());
        save();
    }

    /// Your colour for this amulet's star ("#RRGGBB"), or "" for its own.
    public static String colour(String name) {
        String c = saved().colours.get(key(name));
        return c == null ? "" : c;
    }

    public static void setColour(String name, String hex) {
        String k = key(name);
        if (k.isEmpty()) return;
        if (hex == null || hex.isBlank()) saved().colours.remove(k);
        else saved().colours.put(k, hex.trim());
        version++;
        save();
    }

    /// Every amulet for the list: the known ones (in the crafting menu's order), then any other seen, by name.
    public static List<String> all() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String n : KNOWN) out.put(key(n), n);
        List<String> others = new ArrayList<>();
        for (var e : saved().seen.entrySet()) if (!out.containsKey(e.getKey())) others.add(e.getValue());
        others.sort(String.CASE_INSENSITIVE_ORDER);
        List<String> list = new ArrayList<>(out.values());
        list.addAll(others);
        return list;
    }

    /// The star's own colour for an amulet when you've picked none: a Bigfoot's Amulet's pink (its Pet Luck), else gold.
    public static int defaultColour(String name) {
        String k = key(name);
        return k.contains("bigfoot") && k.contains("amulet") ? 0xFF55FF : 0xF2B33D;
    }

    private static final Map<String, net.minecraft.world.item.ItemStack> stacks = new java.util.HashMap<>();

    /// How the server's pack draws each amulet: a slime ball with this model number (its items/slime_ball.json).
    static final Map<String, Integer> MODELS = Map.ofEntries(
            Map.entry(key("Wade's Amulet"), 1), Map.entry(key("Prospecting Amulet"), 2), Map.entry(key("Midas Amulet"), 3),
            Map.entry(key("Fabled Amulet"), 4), Map.entry(key("Ivory Amulet"), 5), Map.entry(key("Onyx Amulet"), 6),
            Map.entry(key("Bigfoot's Necklace"), 7), Map.entry(key("Bigfoot's Amulet"), 8), Map.entry(key("Titan's Graceful Necklace"), 9),
            Map.entry(key("Moon Amulet"), 10), Map.entry(key("Cryptid Amulet"), 11));

    /// The pack's picture of a known amulet (empty for one it doesn't know).
    static net.minecraft.world.item.ItemStack fromPack(String k) {
        Integer n = MODELS.get(k);
        if (n == null) return net.minecraft.world.item.ItemStack.EMPTY;
        net.minecraft.world.item.ItemStack s = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SLIME_BALL);
        s.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(List.of((float) n), List.of(), List.of(), List.of()));
        return s;
    }
    private static int ticks = 0;

    /// What an amulet looks like: as seen in your inventory or a menu, else the server pack's picture of it.
    public static net.minecraft.world.item.ItemStack picture(String name) {
        String k = key(name);
        net.minecraft.world.item.ItemStack s = stacks.get(k);
        if (s != null) return s;
        com.google.gson.JsonElement json = saved().pictures.get(k);
        var regs = registries();
        if (json == null || regs == null) {
            // not seen yet: the pack's own picture of it
            s = fromPack(k);
            if (!s.isEmpty()) stacks.put(k, s);
            return s;
        }
        try {
            s = net.minecraft.world.item.ItemStack.CODEC.parse(net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, regs), json)
                    .result().orElse(net.minecraft.world.item.ItemStack.EMPTY);
        } catch (RuntimeException e) {
            s = net.minecraft.world.item.ItemStack.EMPTY;
        }
        stacks.put(k, s);
        return s;
    }

    private static net.minecraft.core.HolderLookup.Provider registries() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level != null) return mc.level.registryAccess();
        return mc.getConnection() != null ? mc.getConnection().registryAccess() : null;
    }

    /// Looks over your inventory and any open menu now and then for amulets (to show their pictures in Amulet Stars).
    public static void tick(net.minecraft.client.Minecraft mc) {
        if (mc.player == null || ++ticks % 40 != 0) return;
        boolean changed = false;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) changed |= learn(mc.player.getInventory().getItem(i));
        if (mc.player.containerMenu != null && mc.player.containerMenu != mc.player.inventoryMenu) {
            for (net.minecraft.world.inventory.Slot slot : mc.player.containerMenu.slots) changed |= learn(slot.getItem());
        }
        if (changed) save();
    }

    /// Remembers what this amulet looks like, if it's one and it's new.
    static boolean learn(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String name = net.minecraft.ChatFormatting.stripFormatting(stack.getHoverName().getString());
        if (name == null) return false;
        name = name.replaceAll("[\\uE000-\\uF8FF]", "").trim();
        String k = key(name);
        if (k.isEmpty() || saved().pictures.containsKey(k) || !isAmulet(stack, k)) return false;
        var regs = registries();
        if (regs == null) return false;
        net.minecraft.world.item.ItemStack look = stack.copyWithCount(1);
        look.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        look.remove(net.minecraft.core.component.DataComponents.LORE);
        look.remove(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        try {
            com.google.gson.JsonElement json = net.minecraft.world.item.ItemStack.CODEC
                    .encodeStart(net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, regs), look).result().orElse(null);
            if (json == null) return false;
            saved().pictures.put(k, json);
            stacks.put(k, look);
            if (!saved().seen.containsKey(k) && KNOWN.stream().noneMatch(n -> key(n).equals(k))) saved().seen.put(k, name);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /// One of the known amulets, or a pet item called an amulet or necklace (a new one).
    private static boolean isAmulet(net.minecraft.world.item.ItemStack stack, String k) {
        for (String n : KNOWN) if (key(n).equals(k)) return true;
        if (!k.endsWith("amulet") && !k.endsWith("necklace")) return false;
        var lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore == null) return false;
        for (var line : lore.lines()) {
            String l = line.getString().toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", "");
            if (l.contains("pet item")) return true;
        }
        return false;
    }
}
