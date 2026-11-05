package dannypx.foe.helper;

import java.lang.reflect.Method;

/// Whether an Iris shader pack is on, through Iris's public API (looked up by name, so FOE+ works
/// the same without Iris installed). Checked at most every half second.
public final class ShaderHelper {
    private ShaderHelper() {}

    private static boolean looked = false;
    private static Object api = null;
    private static Method inUse = null;
    private static boolean lastValue = false;
    private static long lastCheck = 0L;

    public static boolean isShaderPackInUse() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 500L) return lastValue;
        lastCheck = now;
        if (!looked) {
            looked = true;
            try {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = type.getMethod("getInstance").invoke(null);
                inUse = type.getMethod("isShaderPackInUse");
            } catch (Throwable ignored) {
                api = null; // no Iris
            }
        }
        if (api == null) return lastValue = false;
        try {
            lastValue = (Boolean) inUse.invoke(api);
        } catch (Throwable e) {
            lastValue = false;
        }
        return lastValue;
    }
}
