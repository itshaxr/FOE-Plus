package dannypx.foe.helper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GlyphHelper {
    private static final Map<Long, OptionalInt> CACHE = new ConcurrentHashMap<>();
    static { dannypx.foe.handler.logic.MemoryGuard.register("glyphColours", CACHE::size, CACHE::clear); }

    public static OptionalInt getColor(Identifier font, int codepoint) {
        long key = ((long) font.hashCode() << 32) | (codepoint & 0xFFFFFFFFL);
        return CACHE.computeIfAbsent(key, k ->
                find(Minecraft.getInstance().getResourceManager(), font, codepoint, new HashSet<>()));
    }

    public static OptionalInt getColor(Identifier font, String singleChar) {
        return getColor(font, singleChar.codePointAt(0));
    }

    public static OptionalInt getColor(String singleChar) {
        return getColor(Identifier.parse("minecraft:default"), singleChar);
    }

    private static OptionalInt find(ResourceManager resourceManager, Identifier font, int codepoint, Set<Identifier> visited) {
        if (!visited.add(font)) return OptionalInt.empty();

        Identifier json = Identifier.fromNamespaceAndPath(font.getNamespace(), "font/" + font.getPath() + ".json");
        List<Resource> stack = resourceManager.getResourceStack(json);

        for (int i = stack.size() - 1; i >= 0; i--) {
            try (BufferedReader reader = stack.get(i).openAsReader()) {
                JsonArray providers = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("providers");
                for (JsonElement provider : providers) {
                    JsonObject providerJson = provider.getAsJsonObject();
                    String type = providerJson.get("type").getAsString();

                    OptionalInt result = OptionalInt.empty();
                    if (type.equals("reference")) {
                        result = find(resourceManager, Identifier.parse(providerJson.get("id").getAsString()), codepoint, visited);
                    } else if (type.equals("bitmap")) {
                        result = fromBitmap(resourceManager, providerJson, codepoint);
                    }
                    if (result.isPresent()) return result;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return OptionalInt.empty();
    }

    private static OptionalInt fromBitmap(ResourceManager resourceManager, JsonObject fontJson, int codepoint) throws IOException {
        JsonArray rows = fontJson.getAsJsonArray("chars");
        String needle = Character.toString(codepoint);

        int row = -1, col = -1, columns = 0;
        for (int r = 0; r < rows.size(); r++) {
            String string = rows.get(r).getAsString();
            columns = Math.max(columns, string.codePointCount(0, string.length()));
            int index = row == -1 ? string.indexOf(needle) : -1;
            if (index >= 0) { row = r; col = string.codePointCount(0, index); }
        }
        if (row == -1) return OptionalInt.empty();

        Optional<Resource> resource = resourceManager.getResource(Identifier.parse(fontJson.get("file").getAsString()).withPrefix("textures/"));
        if (resource.isEmpty()) return OptionalInt.empty();

        try (InputStream input = resource.get().open(); NativeImage img = NativeImage.read(input)) {
            int cellWidth = img.getWidth() / columns;
            int cellHeight = img.getHeight() / rows.size();
            int x0 = col * cellWidth;
            int y0 = row * cellHeight;

            for (int i = 0, n = cellWidth * cellHeight; i < n; i++) {
                int argb = img.getPixel(x0 + i % cellWidth, y0 + i / cellWidth);
                if ((argb >>> 24) != 0) return OptionalInt.of(argb);
            }
        }
        return OptionalInt.empty();
    }
}
