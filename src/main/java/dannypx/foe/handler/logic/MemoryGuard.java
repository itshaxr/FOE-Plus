package dannypx.foe.handler.logic;

import dannypx.foe.FishOnMCExtras;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * Keeps an eye on the game's memory over long sessions.
 * <ul>
 *   <li>Every 5 minutes it notes how much memory is still in use after the game's last clean-up (what's really kept,
 *   not garbage waiting to be cleared) in config/fishonmcextras/memory/memory_log.txt, with the size of each of
 *   FOE+'s own stores, so a slow climb can be seen and traced.</li>
 *   <li>If that passes 80% of what the game may use, FOE+ empties its own caches (they fill again as needed), writes a
 *   full report once (memory_report_*.txt: which kinds of things take the memory, by count and size) and says so
 *   in chat, so the report can be sent in.</li>
 *   <li>/foe memory shows the numbers now and writes a report.</li>
 * </ul>
 */
public final class MemoryGuard {
    private MemoryGuard() {}

    /// One of FOE+'s stores: its name, how many things it holds, and how to empty it.
    private record Store(String name, IntSupplier size, Runnable clear) {}

    private static final Map<String, Store> STORES = new LinkedHashMap<>();

    /// For FOE+'s caches: name, size, and how to empty it (it must fill again by itself as needed).
    public static void register(String name, IntSupplier size, Runnable clear) {
        STORES.put(name, new Store(name, size, clear));
    }

    static final long LOG_EVERY_MS = 5 * 60_000L, CHECK_EVERY_MS = 30_000L;
    static final double HIGH = 0.80;
    private static long nextLog = 0L, nextCheck = 0L, startedAt = System.currentTimeMillis();
    private static boolean reported = false;
    private static long lastClearAt = 0L;

    public static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now < nextCheck) return;
        nextCheck = now + CHECK_EVERY_MS;
        double used = keptFraction();
        if (now >= nextLog) {
            nextLog = now + LOG_EVERY_MS;
            log(summaryLine());
        }
        if (used >= HIGH && now - lastClearAt > 10 * 60_000L) {
            lastClearAt = now;
            int freed = clearAll();
            log("HIGH memory (" + pct(used) + " kept after clean-up): FOE+ emptied its caches (" + freed + " things)");
            // and what FOE+ costs right now, measured for half a minute
            FoeProfiler.begin(30, "high memory, " + pct(used));
            if (!reported) {
                reported = true;
                Path report = writeReport("high memory");
                if (mc.player != null && report != null) {
                    dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.line("MEMORY", 0xFFAA00)
                            .append(dannypx.foe.helper.FoeChat.text("Your game's memory is running high (" + pct(used)
                                    + "). FOE+ freed what it could and saved a report: config/fishonmcextras/memory/"
                                    + report.getFileName() + ". Please send it to the FOE+ team.")));
                }
            }
        }
    }

    /// What's still in use after the last clean-up, of the most the game may use (0-1); the live figure when the
    /// game hasn't said.
    public static double keptFraction() {
        long used = 0, max = 0;
        boolean any = false;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != MemoryType.HEAP) continue;
            MemoryUsage after = pool.getCollectionUsage();
            if (after == null) continue;
            any = true;
            used += after.getUsed();
        }
        max = Runtime.getRuntime().maxMemory();
        if (!any || used == 0) used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return max <= 0 ? 0 : used / (double) max;
    }

    static String pct(double f) {
        return Math.round(f * 100) + "%";
    }

    private static long mb(long bytes) {
        return bytes / (1024 * 1024);
    }

    /// "used 1834 MB now, 1210 MB kept after clean-up, of 4096 MB · up 3h 12m · FOE+ stores: ..."
    public static String summaryLine() {
        Runtime rt = Runtime.getRuntime();
        long now = rt.totalMemory() - rt.freeMemory();
        long upMin = (System.currentTimeMillis() - startedAt) / 60_000L;
        return "used " + mb(now) + " MB now, " + pct(keptFraction()) + " kept after clean-up, of " + mb(rt.maxMemory()) + " MB"
                + " · up " + (upMin / 60) + "h " + (upMin % 60) + "m · FOE+ stores: " + storeSizes();
    }

    static String storeSizes() {
        List<String> parts = new ArrayList<>();
        for (Store s : STORES.values()) {
            try {
                parts.add(s.name() + "=" + s.size().getAsInt());
            } catch (RuntimeException e) {
                parts.add(s.name() + "=?");
            }
        }
        return String.join(", ", parts);
    }

    /// Empties every FOE+ cache; how many things they held.
    public static int clearAll() {
        int n = 0;
        for (Store s : STORES.values()) {
            try {
                n += s.size().getAsInt();
                s.clear().run();
            } catch (RuntimeException e) {
                LoggerHandler.error(e);
            }
        }
        return n;
    }

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("memory");
    }

    private static void log(String line) {
        try {
            Files.createDirectories(folder());
            Path f = folder().resolve("memory_log.txt");
            if (Files.exists(f) && Files.size(f) > 512 * 1024) Files.move(f, folder().resolve("memory_log.old.txt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(f, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "  " + line + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            LoggerHandler.error(e);
        }
    }

    /// A report: the numbers, FOE+'s stores and the game's biggest kinds of objects (the JVM's class histogram,
    /// which cleans up first, so it can pause the game a second). Null when it couldn't be written.
    public static Path writeReport(String why) {
        try {
            Files.createDirectories(folder());
            Path f = folder().resolve("memory_report_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".txt");
            StringBuilder sb = new StringBuilder();
            sb.append("FOE+ ").append(FishOnMCExtras.VERSION).append(" memory report (").append(why).append(")\n");
            sb.append(summaryLine()).append("\n\n");
            sb.append("Biggest kinds of objects in memory:\n");
            sb.append(histogram(80));
            Files.writeString(f, sb.toString());
            return f;
        } catch (Exception e) {
            LoggerHandler.error(e);
            return null;
        }
    }

    /// The top `lines` of the JVM's class histogram (instances, bytes, class), or why it isn't there.
    static String histogram(int lines) {
        try {
            Object out = ManagementFactory.getPlatformMBeanServer().invoke(new ObjectName("com.sun.management:type=DiagnosticCommand"),
                    "gcClassHistogram", new Object[]{new String[0]}, new String[]{String[].class.getName()});
            String[] all = String.valueOf(out).split("\n");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(all.length, lines + 3); i++) sb.append(all[i]).append('\n');
            if (all.length > 0) sb.append(all[all.length - 1]).append('\n');   // the total
            // FOE+'s own objects, wherever they rank
            sb.append("\nFOE+'s own objects:\n");
            int n = 0;
            for (String l : all) if (l.contains("dannypx") && n++ < 40) sb.append(l).append('\n');
            if (n == 0) sb.append("(none listed)\n");
            return sb.toString();
        } catch (Exception e) {
            return "(not available on this Java: " + e + ")\n";
        }
    }
}
