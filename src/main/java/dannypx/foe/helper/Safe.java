package dannypx.foe.helper;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A safety net around FOE+'s own work: if the server changes how something looks (a tooltip line
 * moved, a scoreboard line with fewer parts) and FOE+'s reading of it fails, that one feature is
 * skipped instead of crashing the game. Each failure is written to the log once per place, so it
 * can still be found and fixed.
 */
public final class Safe {
    private Safe() {}

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    /// Runs `work`; a failure in it is logged (once per `where`) and skipped.
    public static void run(String where, Runnable work) {
        try {
            work.run();
        } catch (VirtualMachineError e) {
            throw e; // out of memory and the like aren't ours to hide
        } catch (Throwable t) {
            report(where, t);
        }
    }

    /// `work`'s answer, or `fallback` if it fails (logged once per `where`).
    public static <T> T get(String where, Supplier<T> work, T fallback) {
        try {
            T v = work.get();
            return v == null ? fallback : v;
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable t) {
            report(where, t);
            return fallback;
        }
    }

    private static java.lang.reflect.Field stackDepth;
    static {
        try {
            stackDepth = org.joml.Matrix3x2fStack.class.getDeclaredField("curr");
            stackDepth.setAccessible(true);
        } catch (Throwable t) {
            stackDepth = null;
        }
    }

    public static int depth(org.joml.Matrix3x2fStack stack) {
        try {
            return stackDepth == null ? -1 : stackDepth.getInt(stack);
        } catch (Throwable t) {
            return -1;
        }
    }

    /// Drawing that can't crash the game: on a failure it's skipped, and anything it had moved
    /// (pushed) on the drawing stack is put back, so what's drawn after it isn't shifted.
    public static void draw(String where, net.minecraft.client.gui.GuiGraphics g, Runnable work) {
        int before = depth(g.pose());
        try {
            work.run();
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable t) {
            report(where, t);
            if (before >= 0) {
                int guard = 64;
                while (depth(g.pose()) > before && guard-- > 0) g.pose().popMatrix();
            }
        }
    }

    /// After a failure caught by the caller: logged once, and the drawing stack put back to `before`.
    public static void recover(String where, Throwable t, net.minecraft.client.gui.GuiGraphics g, int before) {
        if (t instanceof VirtualMachineError e) throw e;
        report(where, t);
        if (before >= 0) {
            int guard = 64;
            while (depth(g.pose()) > before && guard-- > 0) g.pose().popMatrix();
        }
    }

    public static void report(String where, Throwable t) {
        if (!REPORTED.add(where + ":" + t.getClass().getName())) return;
        try {
            dannypx.foe.FishOnMCExtras.LOGGER.error("[FOE+] {} failed and was skipped (the server may have changed)", where, t);
        } catch (Throwable ignored) {
            System.err.println("[FOE+] " + where + " failed and was skipped: " + t);
            t.printStackTrace();
        }
    }
}
