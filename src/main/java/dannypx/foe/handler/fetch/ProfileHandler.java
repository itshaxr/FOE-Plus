package dannypx.foe.handler.fetch;

import com.mojang.authlib.GameProfile;
import dannypx.foe.handler.Handler;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.mixin.accessor.MinecraftAccessor;
import dannypx.foe.type.tuple.Pair;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.players.ProfileResolver;

public class ProfileHandler extends Handler {
    private static ProfileHandler INSTANCE = new ProfileHandler();

    public static ProfileHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new ProfileHandler();
        }
        return INSTANCE;
    }

    public ProfileHandler() {
        // Mojang's lookup itself; the server's player list is checked first, on the game thread.
        profileResolver = ((MinecraftAccessor) minecraft).getServices().profileResolver();
    }

    //region Fields
    private final Map<UUID, String> names = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> failedAt = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<UUID> inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final ProfileResolver profileResolver;
    /// A name that couldn't be found is asked for again after this.
    private static final long RETRY_FAILED_MS = 10L * 60L * 1000L;
    /// Looking a name up asks Mojang's servers and waits for the answer, so it runs here, one at a
    /// time, never on the game thread (that froze the game while hovering armor tooltips).
    private static final java.util.concurrent.ExecutorService LOOKUPS = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "FOE+ name lookup");
        thread.setDaemon(true);
        return thread;
    });
    //endregion

    //region Methods
    /// The player's name, or null while it's being looked up (the tooltip shows "Loading" and
    /// picks the name up a moment later). Never blocks.
    public String getUsernameFromId(UUID uuid) {
        if (uuid == null) return null;
        String known = names.get(uuid);
        if (known != null) return known;

        // Online right now: the server's player list has the name already.
        var connection = minecraft.getConnection();
        if (connection != null) {
            var info = connection.getPlayerInfo(uuid);
            if (info != null && info.getProfile() != null && info.getProfile().name() != null) {
                names.put(uuid, info.getProfile().name());
                return info.getProfile().name();
            }
        }

        Long failed = failedAt.get(uuid);
        if (failed != null && System.currentTimeMillis() - failed < RETRY_FAILED_MS) return null;
        if (!inFlight.add(uuid)) return null;

        LoggerHandler._debug("Fetching: " + uuid);
        LOOKUPS.execute(() -> {
            try {
                java.util.Optional<GameProfile> profile = profileResolver.fetchById(uuid);
                if (profile.isPresent() && profile.get().name() != null) {
                    names.put(uuid, profile.get().name());
                    failedAt.remove(uuid);
                } else {
                    failedAt.put(uuid, System.currentTimeMillis());
                }
            } catch (Exception e) {
                failedAt.put(uuid, System.currentTimeMillis());
            } finally {
                inFlight.remove(uuid);
            }
        });
        return null;
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
