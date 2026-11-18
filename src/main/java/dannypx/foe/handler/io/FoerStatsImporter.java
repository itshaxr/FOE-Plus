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
                    // nothing found on its own: let you point at it (a launcher FOE+ doesn't know)
                    StatsDataHandler.StatsDataModel here = current;
                    if (here.fishTotal > 0)
                        message(Component.literal("No other FOE-R install found. Stats in this game folder (" + here.fishTotal
                                + " fish) are already the ones FOE-R kept here. Pick another install's folder to import from it.").withStyle(ChatFormatting.YELLOW));
                    else
                        message(Component.literal("No other FOE-R install found on its own. Pick the game folder of the install that has your stats.").withStyle(ChatFormatting.YELLOW));
                    chooseFolder(uuid);
                } else if (best.get().fishTotal() <= current.fishTotal) {
                    message(Component.literal("Stats here are already the newest (" + current.fishTotal + " vs " + best.get().fishTotal()
                            + " fish). Nothing changed. Shift-click the button to pick another install's folder.").withStyle(ChatFormatting.YELLOW));
                } else {
                    apply(best.get(), true);
                }
            });
        }, "FOE-R stats import");
        thread.setDaemon(true);
        thread.start();
    }

    /// Opens your computer's folder window: pick the FOE-R install's game folder (or any folder
    /// above it, or its stats.json) and its stats come in, the current ones backed up first.
    public static void chooseFolder(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        Thread t = new Thread(() -> {
            String picked;
            try {
                picked = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_selectFolderDialog(
                        "Pick the game folder of the FOE-R install with your stats", System.getProperty("user.home"));
            } catch (Throwable e) {
                picked = null;
            }
            if (picked == null || picked.isBlank()) return;
            Optional<Candidate> found = inPicked(Path.of(picked), uuid);
            String where = picked;
            mc.execute(() -> {
                if (found.isEmpty()) {
                    message(Component.literal("No FOE-R stats for you in " + where + " (looked for config/fishonmcextras/data/<your id>/stats.json).").withStyle(ChatFormatting.RED));
                    return;
                }
                Path own = FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("data").resolve(uuid.toString()).resolve("stats.json");
                try {
                    if (Files.exists(own) && Files.isSameFile(found.get().file(), own)) {
                        message(Component.literal("That's this install's own stats file: they're already in use.").withStyle(ChatFormatting.YELLOW));
                        return;
                    }
                } catch (IOException ignored) {
                    // own file not there yet
                }
                apply(found.get(), true);
            });
        }, "FOE-R stats folder");
        t.setDaemon(true);
        t.start();
    }

    /// Your stats file in a folder you picked: the folder itself, its .minecraft/minecraft folder,
    /// or anywhere up to a few folders below it (a launcher's instances folder). Package-private for tests.
    static Optional<Candidate> inPicked(Path picked, UUID uuid) {
        if (Files.isRegularFile(picked)) return readCandidate(picked);
        List<Candidate> out = new ArrayList<>();
        searchFor(picked, uuid, 6, new int[1], p -> readCandidate(p).ifPresent(out::add));
        return best(out).or(() -> out.stream().findFirst());
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
        Set<Path> seen = new java.util.HashSet<>();
        String home = System.getProperty("user.home"), appData = System.getenv("APPDATA"), local = System.getenv("LOCALAPPDATA");
        Set<Path> roots = candidateGameDirs(gameDir, home, appData);
        for (Path root : roots) {
            addFile(out, seen, root.resolve(String.format(STATS_PATH, uuid)).normalize(), own);
        }
        // Then a careful search through every launcher's folders (any profile or instance name,
        // a custom Prism instance folder, flatpak installs...), in case one is laid out differently.
        int[] visited = {0};
        for (Path base : launcherBases(home, appData, local, gameDir)) {
            int[] here = {0}; // each launcher gets its own budget
            searchFor(base, uuid, 6, here, p -> addFile(out, seen, p, own));
            visited[0] += here[0];
        }
        LoggerHandler.info("FOE-R stats import: checked " + roots.size() + " game folders and " + visited[0]
                + " launcher folders, found " + out.size() + (out.isEmpty() ? "" : ": " + out.stream().map(c -> c.file() + " (" + c.fishTotal() + " fish)").toList()));
        return out;
    }

    private static void addFile(List<Candidate> out, Set<Path> seen, Path file, Path own) {
        file = file.toAbsolutePath().normalize();
        if (file.equals(own) || !seen.add(file) || !Files.isRegularFile(file)) return;
        try {
            if (Files.exists(own) && Files.isSameFile(file, own)) return;
        } catch (IOException ignored) {
            // own file may not exist yet
        }
        readCandidate(file).ifPresent(out::add);
    }

    /// Folders not worth going into while looking for a game folder.
    private static final Set<String> SKIP = Set.of("mods", "resourcepacks", "saves", "shaderpacks", "screenshots", "logs",
            "crash-reports", "libraries", "assets", "meta", "cache", "caches", "natives", "jre", "java", "runtime", "runtimes",
            "node_modules", ".git", "downloads", "icons", "skins", "backups", "texturepacks", "schematics", "journeymap", "xaero");

    /// Walks `base` (up to `depth` folders down) for config/fishonmcextras/data/<uuid>/stats.json.
    static void searchFor(Path base, UUID uuid, int depth, int[] visited, java.util.function.Consumer<Path> found) {
        if (base == null || !Files.isDirectory(base)) return;
        Path target = Path.of(String.format(STATS_PATH, uuid));
        try {
            Files.walkFileTree(base, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), depth, new java.nio.file.SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                    if (++visited[0] > 5_000) return java.nio.file.FileVisitResult.TERMINATE;
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    if (!dir.equals(base) && SKIP.contains(name)) return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                    // a game folder: check it directly instead of walking its config
                    if (Files.isDirectory(dir.resolve("config"))) {
                        Path f = dir.resolve(target);
                        if (Files.isRegularFile(f)) found.accept(f);
                        if (!dir.equals(base)) return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException e) {
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | SecurityException ignored) {
            // unreadable: skipped
        }
    }

    /// Where the launchers keep their profiles and instances, on Windows, macOS and Linux:
    /// Modrinth, CurseForge, Prism, MultiMC, ATLauncher, GDLauncher, the official launcher's
    /// .minecraft, TLauncher; plus a Prism/MultiMC custom instance folder from its settings.
    /// Package-private for tests.
    static Set<Path> launcherBases(String home, String appData, String localAppData, Path gameDir) {
        Set<Path> out = new LinkedHashSet<>();
        List<Path> prismLike = new ArrayList<>();
        if (appData != null && !appData.isBlank()) {
            Path a = Path.of(appData);
            for (String n : new String[]{"ModrinthApp", "com.modrinth.theseus", "PrismLauncher", "PolyMC", "MultiMC", "ATLauncher",
                    "gdlauncher_next", "gdlauncher_carbon", ".minecraft", ".tlauncher", "SKlauncher"}) out.add(a.resolve(n));
            prismLike.add(a.resolve("PrismLauncher"));
            prismLike.add(a.resolve("PolyMC"));
        }
        if (localAppData != null && !localAppData.isBlank()) {
            Path l = Path.of(localAppData);
            out.add(l.resolve("Programs").resolve("PrismLauncher"));
            prismLike.add(l.resolve("Programs").resolve("PrismLauncher"));
            // the Microsoft Store launcher's .minecraft
            out.add(l.resolve("Packages").resolve("Microsoft.4297127D64EC6_8wekyb3d8bbwe").resolve("LocalCache").resolve("Roaming"));
        }
        if (home != null && !home.isBlank()) {
            Path h = Path.of(home);
            out.add(h.resolve("curseforge").resolve("minecraft").resolve("Instances"));
            out.add(h.resolve("Documents").resolve("Curse").resolve("Minecraft").resolve("Instances"));
            out.add(h.resolve("Documents").resolve("CurseForge").resolve("Minecraft").resolve("Instances"));
            out.add(h.resolve(".minecraft"));
            out.add(h.resolve("ATLauncher"));
            out.add(h.resolve("MultiMC"));
            Path mac = h.resolve("Library").resolve("Application Support");
            for (String n : new String[]{"minecraft", "ModrinthApp", "com.modrinth.theseus", "PrismLauncher", "PolyMC", "ATLauncher", "gdlauncher_next", "gdlauncher_carbon"})
                out.add(mac.resolve(n));
            prismLike.add(mac.resolve("PrismLauncher"));
            Path share = h.resolve(".local").resolve("share");
            for (String n : new String[]{"ModrinthApp", "com.modrinth.theseus", "PrismLauncher", "PolyMC", "multimc", "ATLauncher", "gdlauncher_next", "gdlauncher_carbon"})
                out.add(share.resolve(n));
            prismLike.add(share.resolve("PrismLauncher"));
            Path flatpak = h.resolve(".var").resolve("app");
            out.add(flatpak.resolve("org.prismlauncher.PrismLauncher").resolve("data").resolve("PrismLauncher"));
            out.add(flatpak.resolve("com.modrinth.ModrinthApp").resolve("data").resolve("ModrinthApp"));
            out.add(flatpak.resolve("com.atlauncher.ATLauncher").resolve("data"));
            prismLike.add(flatpak.resolve("org.prismlauncher.PrismLauncher").resolve("data").resolve("PrismLauncher"));
        }
        // Prism / MultiMC can keep instances anywhere: their settings say where
        for (Path root : prismLike) {
            for (String cfg : new String[]{"prismlauncher.cfg", "polymc.cfg", "multimc.cfg"}) {
                Path dir = instanceDirFrom(root, root.resolve(cfg));
                if (dir != null) out.add(dir);
            }
        }
        // the launcher this game was started from, whichever it is: two folders up from here
        if (gameDir != null) {
            Path up = gameDir.getParent();
            if (up != null && isGameFolderName(gameDir)) up = up.getParent();
            if (up != null) out.add(up);
        }
        return out;
    }

    /// The InstanceDir= line of a Prism/MultiMC settings file (relative to the launcher's folder), or null.
    static Path instanceDirFrom(Path root, Path cfg) {
        try {
            if (!Files.isRegularFile(cfg)) return null;
            for (String line : Files.readAllLines(cfg)) {
                if (!line.startsWith("InstanceDir=")) continue;
                String v = line.substring("InstanceDir=".length()).trim();
                if (v.isEmpty()) return null;
                Path p = Path.of(v);
                return p.isAbsolute() ? p : root.resolve(p);
            }
        } catch (Exception ignored) {
            // unreadable settings
        }
        return null;
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
            launcherRoots.add(a.resolve("ATLauncher").resolve("instances"));
            launcherRoots.add(a.resolve("gdlauncher_next").resolve("instances"));
            launcherRoots.add(a.resolve("gdlauncher_carbon").resolve("data").resolve("instances"));
            launcherRoots.add(a.resolve(".minecraft").resolve("versions")); // TLauncher keeps a folder per version
            launcherRoots.add(a.resolve("MultiMC").resolve("instances"));
        }
        if (home != null && !home.isBlank()) {
            Path h = Path.of(home);
            roots.add(h.resolve(".minecraft"));
            roots.add(h.resolve("Library").resolve("Application Support").resolve("minecraft"));
            launcherRoots.add(h.resolve("curseforge").resolve("minecraft").resolve("Instances"));
            launcherRoots.add(h.resolve("Documents").resolve("Curse").resolve("Minecraft").resolve("Instances"));
            launcherRoots.add(h.resolve("Documents").resolve("CurseForge").resolve("Minecraft").resolve("Instances"));
            launcherRoots.add(h.resolve("ATLauncher").resolve("instances"));
            launcherRoots.add(h.resolve("MultiMC").resolve("instances"));
            launcherRoots.add(h.resolve(".lunarclient").resolve("profiles"));
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
