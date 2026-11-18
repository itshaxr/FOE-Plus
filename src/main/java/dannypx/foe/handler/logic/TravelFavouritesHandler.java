package dannypx.foe.handler.logic;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dannypx.foe.FishOnMCExtras;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/// Your favourite teleport spot in each place (one each), for the travel map's star: kept on your computer.
public final class TravelFavouritesHandler {
    private static TravelFavouritesHandler INSTANCE;

    public static TravelFavouritesHandler instance() {
        if (INSTANCE == null) INSTANCE = new TravelFavouritesHandler();
        return INSTANCE;
    }

    /// Where they are saved (swappable in tests).
    java.util.function.Supplier<Path> fileLocation =
            () -> FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("travel_favourites.json");

    private Map<String, String> favourites;

    /// Package-private for tests: a spot's name as compared (no colours, no case, single spaces).
    public static String clean(String name) {
        if (name == null) return "";
        String s = net.minecraft.ChatFormatting.stripFormatting(name);
        return s == null ? "" : s.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, String> all() {
        if (favourites == null) {
            favourites = new LinkedHashMap<>();
            try {
                Path f = fileLocation.get();
                if (Files.exists(f)) {
                    Map<String, String> read = new Gson().fromJson(Files.readString(f), new TypeToken<Map<String, String>>() {}.getType());
                    if (read != null) favourites.putAll(read);
                }
            } catch (Exception e) {
                LoggerHandler.error(e);
            }
        }
        return favourites;
    }

    /// The favourite spot's name in this place (as it was shown), or null.
    public String get(String place) {
        return all().get(place);
    }

    public boolean isFavourite(String place, String spotName) {
        String f = get(place);
        return f != null && !clean(f).isEmpty() && clean(f).equals(clean(spotName));
    }

    /// Makes this spot the place's favourite, or (when it already is) clears it. Returns whether it is now.
    public boolean toggle(String place, String spotName) {
        boolean now;
        if (isFavourite(place, spotName)) {
            all().remove(place);
            now = false;
        } else {
            all().put(place, net.minecraft.ChatFormatting.stripFormatting(spotName).trim());
            now = true;
        }
        save();
        return now;
    }

    private void save() {
        try {
            Path f = fileLocation.get();
            Files.createDirectories(f.getParent());
            Files.writeString(f, new Gson().toJson(all()));
        } catch (Exception e) {
            LoggerHandler.error(e);
        }
    }
}
