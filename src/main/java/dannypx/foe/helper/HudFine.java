package dannypx.foe.helper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Where a HUD is, to the pixel. The settings keep a HUD's place in whole percent of the screen (so a HUD could never sit
 * exactly in the middle, it was always a little to one side); a HUD dragged in Move HUD Elements now keeps its exact
 * place here as well, and that's the one used for as long as the setting still says the same whole percent (move it
 * with the setting's slider and the slider wins).
 */
public final class HudFine {
    private HudFine() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Float> saved;
    private static final Map<ValidatedInt, String> names = new IdentityHashMap<>();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("hud_exact_places.json");
    }

    private static Map<String, Float> saved() {
        if (saved != null) return saved;
        saved = new TreeMap<>();
        try {
            if (Files.exists(file())) {
                Map<String, Float> m = GSON.fromJson(Files.readString(file()), new TypeToken<TreeMap<String, Float>>() {}.getType());
                if (m != null) saved.putAll(m);
            }
        } catch (Exception e) {
            dannypx.foe.handler.logic.LoggerHandler.error(e);
        }
        return saved;
    }

    /// The setting's name ("hud.questPanelXPosition"), found once.
    private static String nameOf(ValidatedInt setting) {
        String n = names.get(setting);
        if (n != null) return n;
        Object[][] configs = {{"hud", Configs.hudConfig}, {"debug", Configs.debugConfig}, {"main", Configs.mainConfig},
                {"renderer", Configs.rendererConfig}, {"inventory", Configs.inventoryScreenConfig}};
        for (Object[] c : configs) {
            if (c[1] == null) continue;
            for (Field f : c[1].getClass().getFields()) {
                try {
                    if (f.get(c[1]) == setting) {
                        n = c[0] + "." + f.getName();
                        names.put(setting, n);
                        return n;
                    }
                } catch (IllegalAccessException ignored) {}
            }
        }
        names.put(setting, "");
        return "";
    }

    /// A HUD's place (0-1 of the screen): the exact one while the setting still says its whole percent, else the setting's.
    public static float get(ValidatedInt setting) {
        int whole = setting.get();
        String n = nameOf(setting);
        if (!n.isEmpty()) {
            Float exact = saved().get(n);
            if (exact != null && Math.round(exact * 100f) == whole) return exact;
        }
        return whole / 100f;
    }

    /// Puts a HUD at `fraction` (0-1 of the screen): the setting gets its whole percent, and the exact place is kept.
    public static void set(ValidatedInt setting, float fraction) {
        float f = Math.max(0f, Math.min(1f, fraction));
        setting.accept(Math.round(f * 100f));
        String n = nameOf(setting);
        if (n.isEmpty()) return;
        saved().put(n, f);
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(saved()));
        } catch (Exception e) {
            dannypx.foe.handler.logic.LoggerHandler.error(e);
        }
    }
}
