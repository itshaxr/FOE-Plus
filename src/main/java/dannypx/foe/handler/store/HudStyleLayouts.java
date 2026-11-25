package dannypx.foe.handler.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.type.Alignment;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedChoice;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where your HUDs are, kept for each HUD Style: switching from FOE+ to FOE-R keeps a copy of where every HUD was
 * (its corner and place), and switching back puts them all back there; the same the other way. The first time you
 * switch to a style there's no copy for it yet: FOE-R then moves the panels in the top corners down below its boxes.
 */
public final class HudStyleLayouts {
    private HudStyleLayouts() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static dannypx.foe.type.HudStyle last = null;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("hud_layouts.json");
    }

    /// Package-private for tests: whether a HUD setting is part of where a HUD is (its corner and place).
    static boolean isPlace(String field) {
        return field.endsWith("XPosition") || field.endsWith("YPosition") || field.endsWith("Alignment");
    }

    /// Every tick: notices the HUD Style changing (in the O menu, the settings, anywhere) and swaps the layouts.
    public static void tick() {
        dannypx.foe.type.HudStyle now = Configs.hudConfig.hudStyle.get();
        if (last == null) { last = now; return; }
        if (now == last) return;
        dannypx.foe.type.HudStyle from = last;
        last = now;
        try {
            JsonObject all = read();
            all.add(from.name(), snapshot());
            JsonObject saved = all.has(now.name()) && all.get(now.name()).isJsonObject() ? all.getAsJsonObject(now.name()) : null;
            if (saved != null) restore(saved);
            else if (now == dannypx.foe.type.HudStyle.FOE_R) dannypx.foe.screens.element.hud.FoeRBoxes.makeRoom();
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(all));
            Configs.hudConfig.save();
        } catch (Exception e) {
            LoggerHandler.error(e);
        }
    }

    private static JsonObject read() {
        try {
            if (Files.exists(file())) {
                JsonObject o = GSON.fromJson(Files.readString(file()), JsonObject.class);
                if (o != null) return o;
            }
        } catch (Exception e) {
            LoggerHandler.error(e);
        }
        return new JsonObject();
    }

    /// Where every HUD is now.
    static JsonObject snapshot() throws IllegalAccessException {
        JsonObject out = new JsonObject();
        for (Field f : Configs.hudConfig.getClass().getFields()) {
            if (!isPlace(f.getName())) continue;
            Object v = f.get(Configs.hudConfig);
            if (v instanceof ValidatedInt i) out.add(f.getName(), new JsonPrimitive(i.get()));
            else if (v instanceof ValidatedChoice<?> c && c.get() instanceof Alignment a) out.add(f.getName(), new JsonPrimitive(a.name()));
        }
        return out;
    }

    /// Puts every HUD back where a copy has it (a setting the copy doesn't have stays as it is).
    @SuppressWarnings("unchecked")
    static void restore(JsonObject saved) throws IllegalAccessException {
        for (Field f : Configs.hudConfig.getClass().getFields()) {
            if (!isPlace(f.getName()) || !saved.has(f.getName())) continue;
            Object v = f.get(Configs.hudConfig);
            try {
                if (v instanceof ValidatedInt i) i.accept(saved.get(f.getName()).getAsInt());
                else if (v instanceof ValidatedChoice<?> c && c.get() instanceof Alignment) {
                    Alignment a = Alignment.valueOf(saved.get(f.getName()).getAsString());
                    ((ValidatedChoice<Alignment>) c).accept(a);
                }
            } catch (RuntimeException e) {
                // a value this version can't take: that HUD stays where it is
            }
        }
    }
}
