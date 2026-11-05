package dannypx.foe.handler.io;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.Handler;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChangelogFetcherHandler extends Handler {
    private static ChangelogFetcherHandler INSTANCE = new ChangelogFetcherHandler();

    public static ChangelogFetcherHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new ChangelogFetcherHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private boolean needsUpdate = false;

    public boolean isNeedsUpdate() {
        return needsUpdate;
    }

    public void setNeedsUpdate(boolean needsUpdate) {
        this.needsUpdate = needsUpdate;
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private volatile List<ChangelogEntry> entries;
    private volatile String error;
    private volatile boolean fetching = false;

    public interface Listener {
        void onChangelogUpdated();
    }

    public record ChangelogEntry(String versionNumber, String name, String datePublished, String changelog) { }

    public void addListener(Listener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        this.listeners.remove(listener);
    }

    public List<ChangelogEntry> getEntries() {
        return this.entries;
    }

    public String getError() {
        return this.error;
    }

    public boolean isFetching() {
        return this.fetching;
    }

    public boolean hasData() {
        return this.entries != null || this.error != null;
    }
    //endregion

    //region Methods
    public boolean loadFromCache(String json) {
        if (json == null || json.isBlank()) return false;

        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String cachedVersion = getAsStringOrNull(root, "modVersion");
            if (!FishOnMCExtras.VERSION_PARSED.get().equals(cachedVersion)) return false;

            List<ChangelogEntry> parsed = new ArrayList<>();
            JsonArray array = root.getAsJsonArray("entries");
            if (array != null) {
                for (JsonElement element : array) {
                    JsonObject obj = element.getAsJsonObject();
                    parsed.add(new ChangelogEntry(
                            getAsStringOrNull(obj, "versionNumber"),
                            getAsStringOrNull(obj, "name"),
                            getAsStringOrNull(obj, "datePublished"),
                            getAsStringOrNull(obj, "changelog")
                    ));
                }
            }

            this.entries = parsed;
            this.error = null;
            this.notifyListeners();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String toCacheJson() {
        if (this.entries == null) return null;

        JsonObject root = new JsonObject();
        root.addProperty("modVersion", FishOnMCExtras.VERSION_PARSED.get());

        JsonArray array = new JsonArray();
        for (ChangelogEntry entry : this.entries) {
            JsonObject obj = new JsonObject();
            obj.addProperty("versionNumber", entry.versionNumber());
            obj.addProperty("name", entry.name());
            obj.addProperty("datePublished", entry.datePublished());
            obj.addProperty("changelog", entry.changelog());
            array.add(obj);
        }
        root.add("entries", array);

        return root.toString();
    }

    public void fetch(boolean forceRefresh) {
        if (this.fetching) return;
        if (!forceRefresh && this.hasData()) return;

        if (!forceRefresh && this.loadFromCache(DataFileHandler.instance().getChangelogFile())) {
            LoggerHandler._debug("Load changelog from cache");
            return;
        }

        // FOE+'s changelog ships inside the mod (assets/fishonmcextras/changelog.json), so it
        // always matches the version you have. It used to be FOE-R's, downloaded from Modrinth.
        this.fetching = true;
        this.error = null;
        try (java.io.InputStream in = ChangelogFetcherHandler.class.getResourceAsStream(CHANGELOG_RESOURCE)) {
            if (in == null) {
                this.finishWithError("Changelog missing from the mod");
                return;
            }
            String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            this.finishWithSuccess(parseBundled(json));
        } catch (Exception e) {
            this.finishWithError("Couldn't read the changelog: " + e.getMessage());
        }
    }

    private static final String CHANGELOG_RESOURCE = "/assets/" + FishOnMCExtras.MOD_ID + "/changelog.json";

    /// {"entries": [{versionNumber, name, datePublished, changelog}, ...]}, newest first.
    static List<ChangelogEntry> parseBundled(String json) {
        List<ChangelogEntry> result = new ArrayList<>();
        JsonArray array = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("entries");
        if (array == null) return result;
        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            String changelog = getAsStringOrNull(obj, "changelog");
            if (changelog == null || changelog.isBlank()) continue;
            String versionNumber = getAsStringOrNull(obj, "versionNumber");
            String name = getAsStringOrNull(obj, "name");
            String date = getAsStringOrNull(obj, "datePublished");
            result.add(new ChangelogEntry(versionNumber != null ? versionNumber : "unknown", name != null ? name : "",
                    date != null ? date : "", changelog));
        }
        return result;
    }

    private void finishWithSuccess(List<ChangelogEntry> parsed) {
        Minecraft.getInstance().execute(() -> {
            this.entries = parsed;
            this.error = null;
            this.fetching = false;

            String json = toCacheJson();
            if (json != null) {
                DataFileHandler.instance().saveChangelogToFile(json);
                this.needsUpdate = true;
            }

            this.notifyListeners();
        });
    }

    private void finishWithError(String message) {
        Minecraft.getInstance().execute(() -> {
            this.error = message;
            this.fetching = false;
            LoggerHandler.error(message);
            this.notifyListeners();
        });
    }

    private void notifyListeners() {
        for (Listener listener : this.listeners) {
            listener.onChangelogUpdated();
        }
    }

    private static String getAsStringOrNull(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el == null || el.isJsonNull()) ? null : el.getAsString();
    }
    //endregion

    //region Dev

    /// Field, Pair<Value, Tooltip>
    @Override
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
                "key", Pair.of(Component.literal("value"), Component.empty())
        );
    }
    //endregion
}
