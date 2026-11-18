package dannypx.foe.handler.store;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The server's bait pictures, found in its resource pack. A bait is a vanilla item with a number (Nightcrawlers is a
 * Cooked Cod with custom model data 98), and the pack's item definition (items/cooked_cod.json) says which model each
 * number shows. FOE+ reads those definitions once, notes every numbered model by its name and its texture's name
 * ("bait_hooknightcrawlers", as the wiki calls the picture), and so can show any bait by the wiki's picture name.
 */
public final class BaitModels {
    private BaitModels() {}

    /// A numbered model: the item, which number of its custom model data, and the number.
    record Entry(String item, int index, float threshold, String model) {}

    private static Map<String, Entry> byName = null;
    private static Map<String, Entry> byTexture = null;
    private static Object builtFor = null;

    /// Package-private for tests: the numbered models in one item definition, by their model's last name.
    static void index(String itemId, JsonElement json, Map<String, Entry> out) {
        if (json == null) return;
        if (json.isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray()) index(itemId, e, out);
            return;
        }
        if (!json.isJsonObject()) return;
        JsonObject o = json.getAsJsonObject();
        String type = o.has("type") ? o.get("type").getAsString().replace("minecraft:", "") : "";
        String property = o.has("property") ? o.get("property").getAsString().replace("minecraft:", "") : "";
        if (type.equals("range_dispatch") && property.equals("custom_model_data") && o.has("entries") && o.get("entries").isJsonArray()) {
            int idx = o.has("index") ? o.get("index").getAsInt() : 0;
            for (JsonElement e : o.getAsJsonArray("entries")) {
                if (!e.isJsonObject() || !e.getAsJsonObject().has("threshold")) continue;
                float t = e.getAsJsonObject().get("threshold").getAsFloat();
                for (String model : models(e.getAsJsonObject().get("model"))) {
                    String last = model.substring(model.lastIndexOf('/') + 1).toLowerCase(java.util.Locale.ROOT);
                    if (last.contains(":")) last = last.substring(last.indexOf(':') + 1);
                    out.putIfAbsent(last, new Entry(itemId, idx, t, model));
                }
            }
        }
        for (Map.Entry<String, JsonElement> child : o.entrySet()) {
            if (child.getKey().equals("entries") && type.equals("range_dispatch")) continue;
            if (child.getValue().isJsonObject() || child.getValue().isJsonArray()) index(itemId, child.getValue(), out);
        }
    }

    /// The model names a model description shows ({"type": "model", "model": "fishonmc:item/bait/x"}, inside any
    /// conditions or selects).
    static List<String> models(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e == null) return out;
        if (e.isJsonArray()) { for (JsonElement x : e.getAsJsonArray()) out.addAll(models(x)); return out; }
        if (!e.isJsonObject()) return out;
        JsonObject o = e.getAsJsonObject();
        if (o.has("model") && o.get("model").isJsonPrimitive()) out.add(o.get("model").getAsString());
        for (Map.Entry<String, JsonElement> c : o.entrySet())
            if (!c.getKey().equals("model") || !c.getValue().isJsonPrimitive()) out.addAll(models(c.getValue()));
        return out;
    }

    private static void build() {
        Minecraft mc = Minecraft.getInstance();
        Object now = mc.getResourceManager();
        if (byName != null && builtFor == now) return;
        builtFor = now;
        byName = new HashMap<>();
        byTexture = null;
        try {
            var files = mc.getResourceManager().listResources("items", id -> id.getPath().endsWith(".json"));
            for (var f : files.entrySet()) {
                Identifier file = f.getKey();
                String path = file.getPath().substring("items/".length(), file.getPath().length() - ".json".length());
                String itemId = file.getNamespace() + ":" + path;
                try (Reader r = f.getValue().openAsReader()) {
                    index(itemId, JsonParser.parseReader(r), byName);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            dannypx.foe.handler.logic.LoggerHandler.error(e);
        }
    }

    /// The same numbered models by their texture's last name (read from each model, the first time it's needed).
    private static Map<String, Entry> byTexture() {
        if (byTexture != null) return byTexture;
        byTexture = new HashMap<>();
        var rm = Minecraft.getInstance().getResourceManager();
        for (Entry e : byName.values()) {
            Identifier model = Identifier.tryParse(e.model());
            if (model == null) continue;
            Identifier file = Identifier.fromNamespaceAndPath(model.getNamespace(), "models/" + model.getPath() + ".json");
            try (Reader r = rm.openAsReader(file)) {
                JsonElement json = JsonParser.parseReader(r);
                if (!json.isJsonObject() || !json.getAsJsonObject().has("textures")) continue;
                for (var t : json.getAsJsonObject().getAsJsonObject("textures").entrySet()) {
                    if (!t.getValue().isJsonPrimitive()) continue;
                    String tex = t.getValue().getAsString();
                    byTexture.putIfAbsent(tex.substring(tex.lastIndexOf('/') + 1).replaceFirst("^.*:", "").toLowerCase(java.util.Locale.ROOT), e);
                }
            } catch (Exception ignored) {
            }
        }
        return byTexture;
    }

    /// The item showing the server's picture of this name ("bait_hooknightcrawlers"), or an empty stack.
    public static ItemStack picture(List<String> names) {
        try {
            build();
            Entry found = null;
            for (String n : names) if ((found = byName.get(n)) != null) break;
            if (found == null) for (String n : names) if ((found = byTexture().get(n)) != null) break;
            return found == null ? ItemStack.EMPTY : stackFor(found);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    static ItemStack stackFor(Entry e) {
        Identifier id = Identifier.tryParse(e.item());
        if (id == null) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.getOptional(id);
        ItemStack s;
        if (item.isPresent()) {
            s = new ItemStack(item.get());
        } else {
            // a definition of the pack's own: shown through its item model
            s = new ItemStack(net.minecraft.world.item.Items.PAPER);
            s.set(DataComponents.ITEM_MODEL, id);
        }
        List<Float> floats = new ArrayList<>();
        for (int i = 0; i < e.index(); i++) floats.add(0f);
        floats.add(e.threshold());
        s.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(floats, List.of(), List.of(), List.of()));
        return s;
    }
}
