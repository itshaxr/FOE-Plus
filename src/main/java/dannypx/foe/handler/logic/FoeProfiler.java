package dannypx.foe.handler.logic;

import dannypx.foe.FishOnMCExtras;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Measures what each part of FOE+ costs on the game's own thread, for a short while: how much memory it makes
 * (what the garbage collector then has to clear: when that's a lot, the game stutters) and how long it takes. Runs
 * from /foe profile, and by itself when memory runs high; writes a report to config/fishonmcextras/memory.
 * <p>
 * Each measured part is wrapped as {@code long p = FoeProfiler.mark(); ... FoeProfiler.end("name", p);}: while
 * nothing is being measured that's one check, nothing else.
 */
public final class FoeProfiler {
    private FoeProfiler() {}

    private static volatile boolean on = false;
    private static long until = 0L, startedAt = 0L, startAlloc = 0L, startGcCount = 0L, startGcMs = 0L;
    private static int frames = 0;
    private static Thread thread = null;
    private static String reason = "";
    private static final Map<String, long[]> parts = new HashMap<>();   // {calls, bytes, nanos}
    private static com.sun.management.ThreadMXBean beans = null;

    public static boolean running() {
        return on;
    }

    private static long allocated() {
        try {
            if (beans == null) beans = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            return beans.getCurrentThreadAllocatedBytes();
        } catch (Throwable t) {
            return 0L;
        }
    }

    /// Starts measuring for `seconds` (nothing when it already is).
    public static synchronized void begin(int seconds, String why) {
        if (on) return;
        parts.clear();
        frames = 0;
        reason = why;
        thread = Thread.currentThread();
        startedAt = System.currentTimeMillis();
        until = startedAt + seconds * 1000L;
        startAlloc = allocated();
        long[] gc = gc();
        startGcCount = gc[0];
        startGcMs = gc[1];
        on = true;
    }

    private static final ThreadLocal<long[]> T0 = ThreadLocal.withInitial(() -> new long[64]);
    private static int depth = 0;

    /// Begins a part: the bytes made so far on this thread, and the time (kept on a small stack).
    public static long mark() {
        if (!on || Thread.currentThread() != thread) return -1L;
        long[] t = T0.get();
        if (depth < t.length) t[depth] = System.nanoTime();
        depth++;
        return allocated();
    }

    /// Ends a part begun with {@link #mark}.
    public static void end(String name, long mark) {
        if (mark < 0 || !on || Thread.currentThread() != thread) return;
        long bytes = allocated() - mark;
        depth = Math.max(0, depth - 1);
        long t0 = depth < T0.get().length ? T0.get()[depth] : System.nanoTime();
        long[] p = parts.computeIfAbsent(name, k -> new long[3]);
        p[0]++;
        p[1] += Math.max(0, bytes);
        p[2] += System.nanoTime() - t0;
    }

    /// Once a frame (the HUD's drawing).
    public static void frame() {
        if (on) frames++;
    }

    /// Once a tick: when the time's up, the report.
    public static void tick() {
        if (!on || System.currentTimeMillis() < until) return;
        on = false;
        Path report = write();
        if (report != null && net.minecraft.client.Minecraft.getInstance().player != null) {
            dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.line("PROFILE", dannypx.foe.helper.FoeChat.GOLD)
                    .append(dannypx.foe.helper.FoeChat.text("Saved config/fishonmcextras/memory/" + report.getFileName()
                            + ". Please send it to the FOE+ team.")));
        }
    }

    private static long[] gc() {
        long n = 0, ms = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            n += Math.max(0, b.getCollectionCount());
            ms += Math.max(0, b.getCollectionTime());
        }
        return new long[]{n, ms};
    }

    /// Package-private for tests: the report's table, biggest makers of memory first.
    static String table(Map<String, long[]> parts, int frames, double seconds) {
        List<Map.Entry<String, long[]>> rows = new ArrayList<>(parts.entrySet());
        rows.sort((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]));
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-44s %10s %12s %12s %10s%n", "part", "calls", "MB in all", "KB a frame", "ms a frame"));
        int f = Math.max(1, frames);
        for (var r : rows) {
            long[] v = r.getValue();
            sb.append(String.format(java.util.Locale.ROOT, "%-44s %10d %12.1f %12.1f %10.3f%n", r.getKey(), v[0], v[1] / 1048576.0, v[1] / 1024.0 / f, v[2] / 1e6 / f));
        }
        return sb.toString();
    }

    private static Path write() {
        try {
            double secs = Math.max(0.001, (System.currentTimeMillis() - startedAt) / 1000.0);
            long total = allocated() - startAlloc;
            long[] gc = gc();
            long ours = 0;
            for (long[] v : parts.values()) ours += v[1];
            Path dir = MemoryGuard.folder();
            Files.createDirectories(dir);
            Path f = dir.resolve("profile_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".txt");
            StringBuilder sb = new StringBuilder();
            sb.append("FOE+ ").append(FishOnMCExtras.VERSION).append(" profile (").append(reason).append(")\n");
            sb.append(MemoryGuard.summaryLine()).append("\n\n");
            sb.append(String.format(java.util.Locale.ROOT, "%.0f seconds, %d frames (%.0f fps)%n", secs, frames, frames / secs));
            sb.append(String.format(java.util.Locale.ROOT, "Memory made on the game's thread: %.0f MB a second (%.0f KB a frame)%n",
                    total / 1048576.0 / secs, total / 1024.0 / Math.max(1, frames)));
            sb.append(String.format(java.util.Locale.ROOT, "  of which FOE+ (the parts below): %.0f MB a second (%.0f%%)%n",
                    ours / 1048576.0 / secs, total > 0 ? ours * 100.0 / total : 0));
            sb.append(String.format(java.util.Locale.ROOT, "Clean-ups: %d, %d ms in all%n%n", gc[0] - startGcCount, gc[1] - startGcMs));
            sb.append(table(parts, frames, secs));
            Files.writeString(f, sb.toString());
            return f;
        } catch (Exception e) {
            LoggerHandler.error(e);
            return null;
        }
    }
}
