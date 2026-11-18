package dannypx.foe.handler.logic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.helper.FoeLocations;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The spot you're at within a place (Harbor, Skrei Inlet, Big Bear Dock...), for the top bar. The server no longer
 * says, so FOE+ learns where each spot is: when you teleport to one from the travel map, where you land is that spot.
 * After that the nearest spot you've been to, within a short way, is where you are. Kept in
 * config/fishonmcextras/spots.json.
 */
public final class SpotHandler {
    private static final SpotHandler INSTANCE = new SpotHandler();

    public static SpotHandler instance() {
        return INSTANCE;
    }

    /// Spots by place ("lofoten islands") then name, at {x, z}.
    private final Map<String, Map<String, double[]>> spots = new HashMap<>();
    private boolean loaded = false;
    private String pending = null;
    private long pendingAt = 0L, recordAt = 0L;
    private double lastX = Double.NaN, lastZ = Double.NaN;

    /// How far from a spot you still count as at it (blocks).
    static final double NEAR = 90.0;

    /// A spot was picked on the travel map: where you land next is that spot.
    public void noteSpot(String name) {
        if (name == null) return;
        String n = net.minecraft.ChatFormatting.stripFormatting(name);
        if (n == null) return;
        n = n.replaceAll("[\\uE000-\\uF8FF]", "").trim();
        if (n.isEmpty() || n.equalsIgnoreCase("air")) return;
        pending = n;
        pendingAt = System.currentTimeMillis();
    }

    public void tick(Minecraft mc) {
        if (mc.player == null) { lastX = Double.NaN; return; }
        long now = System.currentTimeMillis();
        double x = mc.player.getX(), z = mc.player.getZ();
        boolean jumped = !Double.isNaN(lastX) && Math.hypot(x - lastX, z - lastZ) > 24.0;
        lastX = x;
        lastZ = z;
        if (jumped && pending != null && now - pendingAt < 20_000L) recordAt = now + 2_500L;   // landed: settle first
        if (recordAt != 0L && now >= recordAt) {
            recordAt = 0L;
            FoeLocations.Location here = PetLocationHandler.currentLocation();
            if (here != null && pending != null) {
                load();
                spots.computeIfAbsent(key(here), k -> new HashMap<>()).put(pending, new double[]{x, z});
                save();
            }
            pending = null;
        }
    }

    /// The spot you're at, or null when you aren't near one FOE+ knows.
    public String current() {
        Minecraft mc = Minecraft.getInstance();
        FoeLocations.Location here = PetLocationHandler.currentLocation();
        if (mc.player == null || here == null) return null;
        load();
        return nearest(spots.get(key(here)), mc.player.getX(), mc.player.getZ());
    }

    /// Package-private for tests: the nearest of these spots within {@link #NEAR}, or null.
    static String nearest(Map<String, double[]> at, double x, double z) {
        if (at == null) return null;
        String best = null;
        double bestD = NEAR;
        for (Map.Entry<String, double[]> e : at.entrySet()) {
            double d = Math.hypot(e.getValue()[0] - x, e.getValue()[1] - z);
            if (d <= bestD) { bestD = d; best = e.getKey(); }
        }
        return best;
    }

    private static String key(FoeLocations.Location l) {
        return l.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("spots.json");
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            for (var place : root.entrySet()) {
                Map<String, double[]> m = new HashMap<>();
                for (var spot : place.getValue().getAsJsonObject().entrySet()) {
                    var a = spot.getValue().getAsJsonArray();
                    m.put(spot.getKey(), new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble()});
                }
                spots.put(place.getKey(), m);
            }
        } catch (Exception e) {
            LoggerHandler.error(e);
        }
    }

    private void save() {
        try {
            JsonObject root = new JsonObject();
            spots.forEach((place, m) -> {
                JsonObject o = new JsonObject();
                m.forEach((name, xz) -> {
                    com.google.gson.JsonArray a = new com.google.gson.JsonArray();
                    a.add(Math.round(xz[0] * 10) / 10.0);
                    a.add(Math.round(xz[1] * 10) / 10.0);
                    o.add(name, a);
                });
                root.add(place, o);
            });
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception e) {
            LoggerHandler.error(e);
        }
    }
}
