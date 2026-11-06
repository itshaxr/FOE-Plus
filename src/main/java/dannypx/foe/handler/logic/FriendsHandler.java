package dannypx.foe.handler.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.helper.FoeChat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Your friends list (FOE had one): players you add with /foe friend add, shown on the Timers &
 * Crew panel next to your crew, with a chat line when they come online or go offline. Saved in
 * config/fishonmcextras/friends.json as uuid → name.
 */
public class FriendsHandler {
    private static FriendsHandler INSTANCE = new FriendsHandler();

    public static FriendsHandler instance() {
        if (INSTANCE == null) INSTANCE = new FriendsHandler();
        return INSTANCE;
    }

    private LinkedHashMap<UUID, String> friends;
    /// Leaves wait a second: switching servers on the proxy is a leave and a join.
    private final Map<UUID, Long> pendingLeaves = new HashMap<>();
    private static final long LEAVE_DELAY_MS = 1_500L;

    java.util.function.Supplier<Path> fileLocation =
            () -> FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("friends.json");

    private LinkedHashMap<UUID, String> friends() {
        if (friends == null) {
            friends = new LinkedHashMap<>();
            try {
                Path file = fileLocation.get();
                if (Files.exists(file)) {
                    Map<String, String> read = new Gson().fromJson(Files.readString(file), new TypeToken<LinkedHashMap<String, String>>() {}.getType());
                    if (read != null) read.forEach((k, v) -> {
                        try {
                            friends.put(UUID.fromString(k), v);
                        } catch (IllegalArgumentException ignored) {
                        }
                    });
                }
            } catch (Exception | LinkageError e) {
                LoggerHandler.error(e instanceof Exception ex ? ex : new RuntimeException(e));
            }
        }
        return friends;
    }

    private void save() {
        try {
            Path file = fileLocation.get();
            Files.createDirectories(file.getParent());
            Map<String, String> out = new LinkedHashMap<>();
            friends().forEach((k, v) -> out.put(k.toString(), v));
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(out));
        } catch (Exception | LinkageError e) {
            LoggerHandler.error(e instanceof Exception ex ? ex : new RuntimeException(e));
        }
    }

    public boolean isFriend(UUID uuid) {
        return uuid != null && friends().containsKey(uuid);
    }

    /// Friends in the order you added them: uuid → name.
    public Map<UUID, String> all() {
        return Collections.unmodifiableMap(friends());
    }

    /// Adds an online player by name. Returns their exact name, or null when nobody online has it.
    public String add(String name) {
        PlayerInfo info = findOnline(name);
        if (info == null) return null;
        friends().put(info.getProfile().id(), info.getProfile().name());
        save();
        return info.getProfile().name();
    }

    /// Removes by name (online or not). Returns the removed name, or null.
    public String remove(String name) {
        for (Iterator<Map.Entry<UUID, String>> it = friends().entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, String> e = it.next();
            if (e.getValue().equalsIgnoreCase(name)) {
                it.remove();
                save();
                return e.getValue();
            }
        }
        return null;
    }

    public static boolean isOnline(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() != null && mc.getConnection().getPlayerInfo(uuid) != null;
    }

    private static PlayerInfo findOnline(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return null;
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info.getProfile().name().equalsIgnoreCase(name)) return info;
        }
        return null;
    }

    public void onPlayerJoin(UUID uuid) {
        if (!isFriend(uuid) || !ConnectionHandler.instance().isOnServer() || !LoadingHandler.instance().isLoadingDone()) return;
        if (pendingLeaves.remove(uuid) != null) return; // a server switch, not a real leave
        // Keep the name up to date (name changes).
        PlayerInfo info = Minecraft.getInstance().getConnection() == null ? null : Minecraft.getInstance().getConnection().getPlayerInfo(uuid);
        if (info != null && !info.getProfile().name().equals(friends().get(uuid))) {
            friends().put(uuid, info.getProfile().name());
            save();
        }
        if (Configs.hudConfig.friendStatusChat.get()) {
            FoeChat.send(FoeChat.line("FRIEND", FoeChat.AQUA).append(FoeChat.white(friends().get(uuid))).append(FoeChat.text(" is online")));
        }
    }

    public void onPlayerLeave(UUID uuid) {
        if (!isFriend(uuid) || !LoadingHandler.instance().isLoadingDone()) return;
        pendingLeaves.put(uuid, System.currentTimeMillis());
    }

    public void tick() {
        if (pendingLeaves.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Long>> it = pendingLeaves.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            if (now - e.getValue() < LEAVE_DELAY_MS) continue;
            it.remove();
            if (isOnline(e.getKey())) continue;
            if (Configs.hudConfig.friendStatusChat.get()) {
                FoeChat.send(FoeChat.line("FRIEND", FoeChat.AQUA).append(FoeChat.white(friends().get(e.getKey()))).append(FoeChat.text(" went offline")));
            }
        }
    }
}
