package dannypx.foe.handler.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dannypx.foe.FishOnMCExtras;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Countdowns for the server's shops: the Tackle Shop restocks every 4 hours (6pm EST is one of
 * them; FOE's timer, re-synced every time "TACKLE SHOP » Tackle items restocked!" shows in chat)
 * and the Cosmetic Store resets every day at 8pm New York time.
 */
public class StoreTimerHandler {
    private static StoreTimerHandler INSTANCE = new StoreTimerHandler();

    public static StoreTimerHandler instance() {
        if (INSTANCE == null) INSTANCE = new StoreTimerHandler();
        return INSTANCE;
    }

    static final long TACKLE_PERIOD_MS = 4L * 60 * 60 * 1000;
    /// A known restock: 5 Nov 2026, 6pm EST (23:00 UTC). Every 4 hours from there.
    static final long DEFAULT_TACKLE_ANCHOR = Instant.parse("2026-11-05T23:00:00Z").toEpochMilli();
    static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    static final int COSMETIC_HOUR = 20;

    private static final class Saved {
        long tackleAnchor = DEFAULT_TACKLE_ANCHOR;
    }

    private Saved saved;

    java.util.function.Supplier<Path> fileLocation =
            () -> FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("store_timers.json");

    private Saved saved() {
        if (saved == null) {
            saved = new Saved();
            try {
                Path file = fileLocation.get();
                if (Files.exists(file)) {
                    Saved read = new Gson().fromJson(Files.readString(file), Saved.class);
                    if (read != null && read.tackleAnchor > 0) saved = read;
                }
            } catch (Exception | LinkageError e) {
                LoggerHandler.error(e instanceof Exception ex ? ex : new RuntimeException(e));
            }
        }
        return saved;
    }

    private void save() {
        try {
            Path file = fileLocation.get();
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(saved()));
        } catch (Exception | LinkageError e) {
            LoggerHandler.error(e instanceof Exception ex ? ex : new RuntimeException(e));
        }
    }

    /// From chat: the Tackle Shop just restocked, so the next one is 4 hours from now.
    public void onGameMessage(String text) {
        if (text == null) return;
        String plain = text.replaceAll("§.", "");
        if (plain.contains("TACKLE SHOP") && plain.contains("restocked")) {
            long now = System.currentTimeMillis();
            saved().tackleAnchor = now - now % 1000L;
            save();
        }
    }

    public long tackleShopRemainingMs() {
        return tackleRemaining(System.currentTimeMillis(), saved().tackleAnchor);
    }

    public long cosmeticStoreRemainingMs() {
        return cosmeticRemaining(System.currentTimeMillis());
    }

    /// Until the next 4-hourly restock after {@code anchor} (a known restock). Package-private for tests.
    static long tackleRemaining(long now, long anchor) {
        return TACKLE_PERIOD_MS - Math.floorMod(now - anchor, TACKLE_PERIOD_MS);
    }

    /// Until the next 8pm in New York (EST in winter, EDT in summer). Package-private for tests.
    static long cosmeticRemaining(long now) {
        ZonedDateTime here = Instant.ofEpochMilli(now).atZone(NEW_YORK);
        ZonedDateTime next = here.toLocalDate().atTime(COSMETIC_HOUR, 0).atZone(NEW_YORK);
        if (!next.isAfter(here)) next = here.toLocalDate().plusDays(1).atTime(COSMETIC_HOUR, 0).atZone(NEW_YORK);
        return next.toInstant().toEpochMilli() - now;
    }

    /// 3725000 → "1:02:05", 59000 → "0:59".
    public static String clock(long ms) {
        long s = Math.max(0L, ms) / 1000L;
        long h = s / 3600, m = (s / 60) % 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }
}
