package dannypx.foe.handler.io;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dannypx.foe.handler.store.StatsDataHandler;
import dannypx.foe.item.FishTagObject;
import dannypx.foe.item.TagObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Imports stats from a previous FishOnMC-Extras-Collab (FOE) install: dry streaks, the fish timer
 * (fish time and fish/hour), quests completed and pets caught. FOE saves them in
 * {@code config/foe/stats/<uuid>.json} in the same game folder. Runs once automatically; can be
 * run again from the O menu.
 */
public final class FoeStatsImporter {
    private FoeStatsImporter() {}

    public static Path statsFile(UUID uuid) {
        return FabricLoader.getInstance().getConfigDir().resolve("foe").resolve("stats").resolve(uuid + ".json");
    }

    private static long readyAt = 0L;
    private static boolean checked = false;

    /// Called every tick once the mod has loaded; tries the automatic import once, 5 seconds in
    /// (so your FOE-R stats have finished loading first).
    public static void tick() {
        if (checked) return;
        // The FOE-R import (another FOE-R install) goes first; FOE's streaks then apply on top.
        if (!FoerStatsImporter.isDone()) return;
        long now = System.currentTimeMillis();
        if (readyAt == 0L) readyAt = now + 5000L;
        if (now < readyAt) return;
        checked = true;
        autoImport();
    }

    /// Imports once per player, the first time they join with FOE's stats file present.
    public static void autoImport() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || StatsDataHandler.instance().getStatsData().importedFoeStats) return;
        if (Files.exists(statsFile(mc.player.getUUID()))) importNow(true);
    }

    /// Returns true when something was imported. Messages the player either way unless {@code quiet}.
    public static boolean importNow(boolean quiet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Path file = statsFile(mc.player.getUUID());
        if (!Files.exists(file)) {
            if (!quiet) message(Component.literal("No FOE stats found for you in config/foe/stats.").withStyle(ChatFormatting.RED));
            return false;
        }

        try {
            JsonObject foe = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            StatsDataHandler stats = StatsDataHandler.instance();
            var data = stats.getStatsData();

            int streaks = 0;
            streaks += importStreaks(foe, "rarityDryStreak", TagObject.RARITY);
            streaks += importStreaks(foe, "variantDryStreak", FishTagObject.VARIANT);
            streaks += importStreaks(foe, "fishSizeDryStreak", FishTagObject.FISH_SIZE);

            long activeTime = getLong(foe, "activeTime");
            int timedFish = (int) getLong(foe, "timerFishCaughtCount");
            // Never replace a longer fish timer with an older, shorter one.
            if (activeTime > data.fishTimeMs) {
                data.fishTimeMs = activeTime;
                data.fishTimeFish = timedFish;
            }

            data.questsCompleted = Math.max(data.questsCompleted, (int) getLong(foe, "allQuestsCompleted"));
            data.petTotal = Math.max(data.petTotal, (int) getLong(foe, "allPetCaughtCount"));
            data.importedFoeStats = true;
            stats.markChanged();

            message(Component.literal("Imported FOE stats: ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(streaks + " dry streaks, fish time " + formatTime(data.fishTimeMs)
                            + ", " + data.questsCompleted + " quests.").withStyle(ChatFormatting.GRAY)));
            return true;
        } catch (Exception e) {
            if (!quiet) message(Component.literal("Couldn't read the FOE stats file.").withStyle(ChatFormatting.RED));
            return false;
        }
    }

    private static int importStreaks(JsonObject foe, String key, String category) {
        if (!foe.has(key) || !foe.get(key).isJsonObject()) return 0;
        int count = 0;
        for (Map.Entry<String, JsonElement> entry : foe.getAsJsonObject(key).entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) continue;
            String field = entry.getKey().toLowerCase(java.util.Locale.ROOT);
            if (field.equals("mythic")) field = "mythical";
            StatsDataHandler.instance().setDryStreak(category, field, entry.getValue().getAsInt());
            count++;
        }
        return count;
    }

    private static long getLong(JsonObject json, String key) {
        try {
            return json.has(key) ? json.get(key).getAsLong() : 0L;
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    private static String formatTime(long ms) {
        long s = ms / 1000L;
        return String.format("%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    private static void message(Component text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[FOE+] ").withStyle(ChatFormatting.AQUA).append(text), false);
        }
    }
}
