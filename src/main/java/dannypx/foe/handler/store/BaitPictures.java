package dannypx.foe.handler.store;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.logic.LoggerHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What each bait looks like, for the Contest panel's best-bait list. The server's baits are drawn with its own
 * models, so FOE+ remembers each one it sees (in your inventory, a chest, the shop...) by its name: the item and
 * the parts that make up its picture, and the bait's rarity (for its name's colour), nothing else. Kept in config/fishonmcextras/bait_pictures.json, so a bait
 * you've seen once keeps its picture.
 */
public final class BaitPictures {
    private BaitPictures() {}

    /// Learned baits by name key (see {@link #key}): the saved form ({"item": the picture, "rarity": "rare"}), and the
    /// stacks made from it.
    private static final Map<String, JsonElement> saved = new HashMap<>();
    private static final Map<String, ItemStack> stacks = new HashMap<>();
    private static boolean loaded = false, dirty = false;
    private static int ticks = 0;
    private static long savedAt = 0L;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("bait_pictures.json");
    }

    /// Package-private for tests: a bait's name as it's matched: lower case, letters and numbers only, single
    /// spaces ("§6Small Cut Squid " → "small cut squid"; the server's icons dropped).
    static String key(String name) {
        if (name == null) return "";
        String t = ChatFormatting.stripFormatting(name);
        if (t == null) return "";
        t = t.replaceAll("[\\uE000-\\uF8FF]", " ").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        return t;
    }

    /// Package-private for tests: which learned name a bait sheet name means: the same name, else the learned
    /// name that contains it or that it contains (the longest such), else null.
    static String match(String wanted, java.util.Collection<String> known) {
        String w = key(wanted);
        if (w.isEmpty()) return null;
        if (known.contains(w)) return w;
        String best = null;
        for (String k : known) {
            if (k.isEmpty()) continue;
            boolean fits = (" " + k + " ").contains(" " + w + " ") || (" " + w + " ").contains(" " + k + " ");
            if (fits && (best == null || k.length() > best.length())) best = k;
        }
        return best;
    }

    /// Pictures found by name among the loaded item models (null: looked, none).
    private static final Map<String, ItemStack> byModel = new HashMap<>();
    static { dannypx.foe.handler.logic.MemoryGuard.register("baitPictures", () -> stacks.size() + byModel.size(), () -> { stacks.clear(); byModel.clear(); }); }
    private static Object lastModels = null;

    /// Package-private for tests: the model whose name is the bait's ("Bonito Live Bait" → ".../bonito_live_bait"),
    /// among these, or null.
    static net.minecraft.resources.Identifier modelNamed(String baitName, java.util.Collection<net.minecraft.resources.Identifier> models) {
        // the names its model may have: the wiki's picture name ("bait_bonito_livebait"), that without its
        // "bait_" / "lure_", and the bait's own name ("bonito_live_bait")
        java.util.List<String> names = new java.util.ArrayList<>();
        dannypx.foe.helper.WikiBaits.Bait wiki = dannypx.foe.helper.WikiBaits.find(baitName);
        if (wiki != null && !wiki.texture().isEmpty()) {
            names.add(wiki.texture());
            names.add(wiki.texture().replaceFirst("^(bait|lure)_", ""));
        }
        String own = key(baitName).replace(' ', '_');
        if (!own.isEmpty()) names.add(own);
        for (String n : names) {
            net.minecraft.resources.Identifier id = modelLastNamed(n, models, n.startsWith("bait_") || n.startsWith("lure_"));
            if (id != null) return id;
        }
        return null;
    }

    /// `anyPack`: also in the game's own "minecraft" namespace (where a server's pack may put its models; fine for
    /// names no vanilla item has, like "bait_bread").
    private static net.minecraft.resources.Identifier modelLastNamed(String slug, java.util.Collection<net.minecraft.resources.Identifier> models, boolean anyPack) {
        net.minecraft.resources.Identifier best = null;
        for (net.minecraft.resources.Identifier id : models) {
            if (!anyPack && id.getNamespace().equals("minecraft")) continue;
            String path = id.getPath();
            String last = path.substring(path.lastIndexOf('/') + 1);
            if (!last.equals(slug)) continue;
            // a bait folder wins over any other model of the same name
            String folder = path.substring(0, Math.max(0, path.lastIndexOf('/')));
            if (best == null || folder.contains("bait")) best = id;
        }
        return best;
    }

    /// Package-private for tests: the names the server's picture of a bait may have: the wiki's picture name, that
    /// without its "bait_" / "lure_", and the bait's own name.
    static java.util.List<String> pictureNames(String baitName) {
        java.util.List<String> names = new java.util.ArrayList<>();
        dannypx.foe.helper.WikiBaits.Bait wiki = dannypx.foe.helper.WikiBaits.find(baitName);
        if (wiki != null && !wiki.texture().isEmpty()) {
            names.add(wiki.texture());
            names.add(wiki.texture().replaceFirst("^(bait|lure)_", ""));
        }
        String own = key(baitName).replace(' ', '_');
        if (!own.isEmpty()) names.add(own);
        return names;
    }

    private static ItemStack fromModel(String baitName) {
        String k = key(baitName);
        Object now = null;
        try { now = ((dannypx.foe.mixin.accessor.ModelManagerAccessor) Minecraft.getInstance().getModelManager()).foe$itemModels(); } catch (RuntimeException ignored) {}
        if (now != lastModels) { lastModels = now; byModel.clear(); }
        if (byModel.containsKey(k)) {
            ItemStack s = byModel.get(k);
            return s == null ? ItemStack.EMPTY : s;
        }
        ItemStack found = null;
        try {
            var models = ((dannypx.foe.mixin.accessor.ModelManagerAccessor) Minecraft.getInstance().getModelManager()).foe$itemModels();
            if (models != lastModels) {             // resource packs (re)loaded: look again
                lastModels = models;
                byModel.clear();
            }
            if (models != null && !models.isEmpty()) {
                // the pack's numbered model of the wiki's picture name (a Cooked Cod numbered 98 for Nightcrawlers)
                ItemStack numbered = BaitModels.picture(pictureNames(baitName));
                net.minecraft.resources.Identifier id = numbered.isEmpty() ? modelNamed(baitName, models.keySet()) : null;
                if (!numbered.isEmpty()) found = numbered;
                if (id != null) {
                    found = new ItemStack(net.minecraft.world.item.Items.PAPER);
                    found.set(DataComponents.ITEM_MODEL, id);
                }
                byModel.put(k, found);
            }
        } catch (RuntimeException e) {
            LoggerHandler.error(e);
        }
        return found == null ? ItemStack.EMPTY : found;
    }

    /// The picture for a bait the sheet names: as FOE+ saw it, else the server's model of that name, else an empty
    /// stack.
    public static ItemStack pictureFor(String baitName) {
        load();
        String k = match(baitName, saved.keySet());
        // A seen bait whose name is only part of this one ("roostertail" for "Roostertail (Fire Tiger)": a lure seen
        // before its colour was kept) is another colour of it: the server's own picture of this one comes first.
        if (k != null && !specific(baitName, k)) {
            ItemStack model = fromModel(baitName);
            if (!model.isEmpty()) return model;
        }
        if (k == null) {
            ItemStack model = fromModel(baitName);
            if (!model.isEmpty()) return model;
            // a bait drawn with the same picture as one you've seen (the server draws Small, Medium and Large Cut
            // Squid alike): that one's picture
            String twin = sameLookAs(baitName, saved.keySet());
            if (twin == null) return ItemStack.EMPTY;
            k = twin;
        }
        ItemStack s = stacks.get(k);
        if (s != null) return s;
        HolderLookup.Provider regs = registries();
        if (regs == null) return ItemStack.EMPTY;
        try {
            JsonElement e = saved.get(k);
            JsonElement item = e.isJsonObject() && e.getAsJsonObject().has("item") ? e.getAsJsonObject().get("item") : e;
            s = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, regs), item).result().orElse(ItemStack.EMPTY);
        } catch (RuntimeException e) {
            s = ItemStack.EMPTY;
        }
        stacks.put(k, s);
        return s;
    }

    /// Package-private for tests: whether a seen name is this bait itself (or names more than it does), not just part
    /// of its name.
    static boolean specific(String wanted, String seenKey) {
        String w = key(wanted);
        return seenKey.equals(w) || (" " + seenKey + " ").contains(" " + w + " ");
    }

    /// Package-private for tests: a bait's name with its lure colour ("Roostertail" and "Fire Tiger" → "Roostertail
    /// (Fire Tiger)"), unless the name has it already.
    static String withColor(String name, String color) {
        String c = color == null ? "" : color.replaceAll("<#[0-9A-Fa-f]{6}>|</?[a-zA-Z_:#0-9]+>", "")
                .replaceAll("§x(§[0-9A-Fa-f]){6}", "").replaceAll("§.", "").trim();
        if (c.isEmpty() || (" " + key(name) + " ").contains(" " + key(c) + " ")) return name;
        return name + " (" + colourName(c) + ")";
    }

    /// A lure colour as the wiki names it: the server calls the Smokin' Shad crankbait's colour "Sexy Shad".
    public static String colourName(String colour) {
        return colour != null && colour.trim().equalsIgnoreCase("sexy shad") ? "Smokin' Shad" : colour;
    }

    /// Package-private for tests: a seen bait whose wiki picture is the same as this bait's, or null.
    static String sameLookAs(String baitName, java.util.Collection<String> seen) {
        dannypx.foe.helper.WikiBaits.Bait wiki = dannypx.foe.helper.WikiBaits.find(baitName);
        if (wiki == null || wiki.texture().isEmpty()) return null;
        for (String k : seen) {
            dannypx.foe.helper.WikiBaits.Bait other = dannypx.foe.helper.WikiBaits.find(k);
            if (other != null && wiki.texture().equals(other.texture())) return k;
        }
        return null;
    }

    /// The baits and lures you've had: {name, rarity, "lure" or "bait"} each (for the Bait Prices list,
    /// which also lists ones the wiki doesn't, like mythical baits).
    public static List<String[]> seen() {
        load();
        List<String[]> out = new java.util.ArrayList<>();
        for (var e : saved.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject o = e.getValue().getAsJsonObject();
            String name = o.has("name") ? o.get("name").getAsString() : titled(e.getKey());
            String rarity = o.has("rarity") ? o.get("rarity").getAsString() : "";
            boolean lure = o.has("lure") && o.get("lure").getAsBoolean();
            out.add(new String[]{name, rarity, lure ? "lure" : "bait"});
        }
        return out;
    }

    private static String titled(String key) {
        StringBuilder b = new StringBuilder();
        for (String w : key.split(" ")) if (!w.isEmpty()) b.append(b.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return b.toString();
    }

    /// The rarity of a bait the sheet names ("rare"), or "" when FOE+ hasn't seen that bait yet.
    public static String rarityFor(String baitName) {
        load();
        String k = match(baitName, saved.keySet());
        if (k == null) {
            // not seen yet: the wiki's rarity for it
            dannypx.foe.helper.WikiBaits.Bait wiki = dannypx.foe.helper.WikiBaits.find(baitName);
            return wiki == null ? "" : wiki.rarity();
        }
        JsonElement e = saved.get(k);
        return e.isJsonObject() && e.getAsJsonObject().has("rarity") ? e.getAsJsonObject().get("rarity").getAsString() : "";
    }

    private static HolderLookup.Provider registries() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) return mc.level.registryAccess();
        return mc.getConnection() != null ? mc.getConnection().registryAccess() : null;
    }

    /// Looks over your inventory and any open menu now and then for baits it hasn't seen.
    public static void tick(Minecraft mc) {
        if (mc.player == null || ++ticks % 20 != 0) return;
        load();
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) learn(mc.player.getInventory().getItem(i));
        if (mc.player.containerMenu != null && mc.player.containerMenu != mc.player.inventoryMenu) {
            for (Slot slot : mc.player.containerMenu.slots) learn(slot.getItem());
        }
        if (dirty && System.currentTimeMillis() - savedAt > 10_000L) save();
    }

    /// A bait's name as Bait Prices lists it: a lure with its colour ("Saltwater Spoon (Silver)").
    public static String listedName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        String shown = ChatFormatting.stripFormatting(stack.getHoverName().getString());
        if (shown == null) shown = "";
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.copyTag().getStringOr("type", "").equals("lure")) {
            shown = withColor(shown, data.copyTag().getStringOr("color", ""));
        }
        return shown;
    }

    /// Remembers what a bait looks like, if it is one and it's new.
    static void learn(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        String type = data == null ? "" : data.copyTag().getStringOr("type", "");
        if (!type.equals("lure") && !dannypx.foe.handler.logic.BaitboxHandler.isBait(stack)) return;
        String rarity = dannypx.foe.handler.logic.BaitboxHandler.rarityName(dannypx.foe.handler.logic.BaitboxHandler.rarityOf(stack));
        // a lure's colour is its own picture: kept under "Roostertail (Fire Tiger)", not just "Roostertail"
        String shown = ChatFormatting.stripFormatting(stack.getHoverName().getString());
        if (shown == null) shown = "";
        if (type.equals("lure")) shown = withColor(shown, data.copyTag().getStringOr("color", ""));
        String k = key(shown);
        if (k.isEmpty()) return;
        if (saved.containsKey(k)) {
            // learned before without its rarity (or its name / lure mark): fill those in now
            JsonElement old = saved.get(k);
            if (old.isJsonObject()) {
                JsonObject o = old.getAsJsonObject();
                boolean changed = false;
                if (!rarity.isEmpty() && (!o.has("rarity") || o.get("rarity").getAsString().isEmpty())) { o.addProperty("rarity", rarity); changed = true; }
                if (!o.has("name")) { o.addProperty("name", shown); changed = true; }
                if (!o.has("lure")) { o.addProperty("lure", type.equals("lure")); changed = true; }
                if (changed) dirty = true;
            }
            return;
        }
        HolderLookup.Provider regs = registries();
        if (regs == null) return;
        // Only what makes the picture: no name, lore or bait data.
        ItemStack look = stack.copyWithCount(1);
        look.remove(DataComponents.CUSTOM_DATA);
        look.remove(DataComponents.LORE);
        look.remove(DataComponents.CUSTOM_NAME);
        try {
            JsonElement json = ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, regs), look).result().orElse(null);
            if (json == null) return;
            JsonObject entry = new JsonObject();
            entry.add("item", json);
            entry.addProperty("rarity", rarity);
            entry.addProperty("name", shown);
            entry.addProperty("lure", type.equals("lure"));
            saved.put(k, entry);
            stacks.put(k, look);
            dirty = true;
        } catch (RuntimeException e) {
            LoggerHandler.error(e);
        }
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonElement root = JsonParser.parseString(Files.readString(f));
            if (root.isJsonObject()) for (var e : root.getAsJsonObject().entrySet()) saved.put(e.getKey(), e.getValue());
        } catch (Exception e) {
            LoggerHandler.error(e);
        }
    }

    private static void save() {
        dirty = false;
        savedAt = System.currentTimeMillis();
        try {
            JsonObject root = new JsonObject();
            saved.forEach(root::add);
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception e) {
            LoggerHandler.error(e);
        }
    }
}
