package dannypx.foe.handler.io;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.handler.store.StatsDataHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Brings your stats over from another FOE-R install (another launcher profile/instance, or the
 * default .minecraft folder), so reinstalling or moving to a new profile doesn't lose them.
 * <p>
 * FOE-R keeps stats in {@code config/fishonmcextras/data/<uuid>/stats.json} in each game folder.
 * On the first join this looks for that file in the other game folders it can find and, when
 * this install has no stats of its own yet, copies the one with the most fish. It never
 * overwrites stats this install already has; the O menu button can still swap in a bigger set
 * (after backing the current file up). The source is remembered, so nothing is imported twice.
 * The FOE (FishOnMC-Extras-Collab) import runs after this one and still works as before.
 */
public final class FoerStatsImporter {
    private FoerStatsImporter() {}

    private static final String STATS_PATH = "config/" + FishOnMCExtras.MOD_ID + "/data/%s/stats.json";

    public record Candidate(Path file, int fishTotal, int petTotal, long lastModified) {}

    private static long readyAt = 0L;
    private static volatile boolean done = false;
    private static boolean started = false;

    /// True once the automatic check has finished (the FOE import waits for it).
    public static boolean isDone() {
        return done;
    }

    /// Called every tick after loading; runs the automatic check once, 3 seconds in.
    public static void tick() {
        if (started) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        if (readyAt == 0L) readyAt = now + 3000L;
        if (now < readyAt) return;
        started = true;
        UUID uuid = mc.player.getUUID();
        // Looking through other folders is file IO: keep it off the game thread.
        Thread thread = new Thread(() -> {
            List<Candidate> found = findCandidates(uuid);
            mc.execute(() -> {
                try {
                    autoImport(found);
                } finally {
                    done = true;
                }
            });
        }, "FOE-R stats import");
        thread.setDaemon(true);
        thread.start();
    }

    /// No fish, pets or items yet: a fresh install (FOE's import only touches streaks/timers).
    public static boolean isFresh(StatsDataHandler.StatsDataModel data) {
        return data.fishTotal == 0 && data.petTotal == 0 && (data.itemData == null || data.itemData.isEmpty());
    }

    private static void autoImport(List<Candidate> found) {
        StatsDataHandler.StatsDataModel current = StatsDataHandler.instance().getStatsData();
        if (current.importedFoerFrom != null && !current.importedFoerFrom.isEmpty()) return;
        if (!isFresh(current)) return; // never overwrite stats this install already has
        best(found).ifPresent(candidate -> apply(candidate, false));
    }

    /// From the O menu: imports the biggest set found if it has more fish than this install.
    public static void importNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID uuid = mc.player.getUUID();
        Thread thread = new Thread(() -> {
            List<Candidate> found = findCandidates(uuid);
            mc.execute(() -> {
                Optional<Candidate> best = best(found);
                StatsDataHandler.StatsDataModel current = StatsDataHandler.instance().getStatsData();
                if (best.isEmpty()) {
                    message(Component.literal("No other FOE-R install with your stats found.").withStyle(ChatFormatting.RED));
                } else if (best.get().fishTotal() <= current.fishTotal) {
                    message(Component.literal("Stats here are already the newest (" + current.fishTotal + " vs " + best.get().fishTotal()
                            + " fish). Nothing changed.").withStyle(ChatFormatting.YELLOW));
                } else {
                    apply(best.get(), true);
                }
            });
        }, "FOE-R stats import");
        thread.setDaemon(true);
        thread.start();
    }

    /// The candidate with the most fish (then pets, then the newest file). Package-private for tests.
    static Optional<Candidate> best(List<Candidate> candidates) {
        return candidates.stream()
                .filter(c -> c.fishTotal() > 0 || c.petTotal() > 0)
                .max(Comparator.comparingInt(Candidate::fishTotal)
                        .thenComparingInt(Candidate::petTotal)
                        .thenComparingLong(Candidate::lastModified));
    }

    private static void apply(Candidate candidate, boolean backup) {
        try {
            StatsDataHandler stats = StatsDataHandler.instance();
            StatsDataHandler.StatsDataModel current = stats.getStatsData();
            if (backup) backupCurrent(current.uuid);

            StatsDataHandler.StatsDataModel imported = new Gson().fromJson(Files.readString(candidate.file()), StatsDataHandler.StatsDataModel.class);
            if (imported == null) return;
            imported.uuid = current.uuid;
            // Keep whichever fish timer is bigger, and FOE's import status from either side.
            if (current.fishTimeMs > imported.fishTimeMs) {
                imported.fishTimeMs = current.fishTimeMs;
                imported.fishTimeFish = current.fishTimeFish;
            }
            imported.importedFoeStats = imported.importedFoeStats || current.importedFoeStats;
            imported.questsCompleted = Math.max(imported.questsCompleted, current.questsCompleted);
            imported.importedFoerFrom = candidate.file().toString();
            stats.setStatsData(imported);
            stats.markChanged();

            LoggerHandler.info("Imported FOE-R stats from " + candidate.file());
            message(Component.literal("Imported FOE-R stats: ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(imported.fishTotal + " fish, " + imported.petTotal + " pets.").withStyle(ChatFormatting.GRAY)));
        } catch (Exception e) {
            LoggerHandler.error(e);
            message(Component.literal("Couldn't read the FOE-R stats at " + candidate.file()).withStyle(ChatFormatting.RED));
        }
    }

    private static void backupCurrent(UUID uuid) {
        if (uuid == null) return;
        Path own = FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("data").resolve(uuid.toString());
        Path file = own.resolve("stats.json");
        try {
            if (Files.exists(file)) Files.copy(file, own.resolve("stats.backup-" + System.currentTimeMillis() + ".json"));
        } catch (IOException e) {
            LoggerHandler.error(e);
        }
    }

    /// Every other install's stats file for this player.
    static List<Candidate> findCandidates(UUID uuid) {
        Path gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        Path own = gameDir.resolve(String.format(STATS_PATH, uuid)).normalize();
        List<Candidate> out = new ArrayList<>();
        for (Path root : candidateGameDirs(gameDir, System.getProperty("user.home"), System.getenv("APPDATA"))) {
            Path file = root.resolve(String.format(STATS_PATH, uuid)).normalize();
            if (file.equals(own) || !Files.isRegularFile(file)) continue;
            try {
                if (Files.isSameFile(file, own)) continue;
            } catch (IOException ignored) {
                // own file may not exist yet
            }
            readCandidate(file).ifPresent(out::add);
        }
        return out;
    }

    private static Optional<Candidate> readCandidate(Path file) {
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            int fish = json.has("fishTotal") ? json.get("fishTotal").getAsInt() : 0;
            int pets = json.has("petTotal") ? json.get("petTotal").getAsInt() : 0;
            return Optional.of(new Candidate(file, fish, pets, Files.getLastModifiedTime(file).toMillis()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Game folders worth checking: sibling profiles/instances of this one, the default .minecraft
     * folder, and the usual Modrinth / Prism / CurseForge locations. Package-private for tests.
     */
    static Set<Path> candidateGameDirs(Path gameDir, String home, String appData) {
        Set<Path> roots = new LinkedHashSet<>();
        // Siblings: Modrinth/CurseForge put each profile's game folder side by side.
        Path parent = gameDir.getParent();
        if (parent != null) addChildren(roots, parent);
        // Prism/MultiMC: instances/<name>/.minecraft, so look one level up as well.
        if (parent != null && parent.getParent() != null && isGameFolderName(gameDir)) addChildren(roots, parent.getParent());

        List<Path> launcherRoots = new ArrayList<>();
        if (appData != null && !appData.isBlank()) {
            Path a = Path.of(appData);
            roots.add(a.resolve(".minecraft"));
            launcherRoots.add(a.resolve("ModrinthApp").resolve("profiles"));
            launcherRoots.add(a.resolve("com.modrinth.theseus").resolve("profiles"));
            launcherRoots.add(a.resolve("PrismLauncher").resolve("instances"));
        }
        if (home != null && !home.isBlank()) {
            Path h = Path.of(home);
            roots.add(h.resolve(".minecraft"));
            roots.add(h.resolve("Library").resolve("Application Support").resolve("minecraft"));
            launcherRoots.add(h.resolve("curseforge").resolve("minecraft").resolve("Instances"));
            launcherRoots.add(h.resolve("Library").resolve("Application Support").resolve("ModrinthApp").resolve("profiles"));
            launcherRoots.add(h.resolve("Library").resolve("Application Support").resolve("PrismLauncher").resolve("instances"));
            launcherRoots.add(h.resolve(".local").resolve("share").resolve("ModrinthApp").resolve("profiles"));
            launcherRoots.add(h.resolve(".local").resolve("share").resolve("PrismLauncher").resolve("instances"));
        }
        for (Path launcherRoot : launcherRoots) addChildren(roots, launcherRoot);
        roots.remove(gameDir);
        return roots;
    }

    private static boolean isGameFolderName(Path dir) {
        String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
        return name.equals(".minecraft") || name.equals("minecraft");
    }

    /// Each subfolder, plus its .minecraft/minecraft folder (Prism layout).
    private static void addChildren(Set<Path> roots, Path dir) {
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(dir, Files::isDirectory)) {
            int count = 0;
            for (Path child : children) {
                if (++count > 200) break; // don't wander through huge folders
                Path normalized = child.toAbsolutePath().normalize();
                roots.add(normalized);
                roots.add(normalized.resolve(".minecraft"));
                roots.add(normalized.resolve("minecraft"));
            }
        } catch (IOException | SecurityException ignored) {
            // unreadable folder: skip it
        }
    }

    private static void message(Component text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(dannypx.foe.helper.FoeChat.line("STATS", dannypx.foe.helper.FoeChat.AQUA).append(text), false);
        }
    }
}
