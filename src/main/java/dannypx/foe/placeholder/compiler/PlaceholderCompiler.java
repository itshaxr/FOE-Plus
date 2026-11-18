package dannypx.foe.placeholder.compiler;

import dannypx.foe.placeholder.evaluator.PlaceholderEvaluator;
import dannypx.foe.placeholder.evaluator.PlaceholderResult;
import dannypx.foe.placeholder.parser.PlaceholderParser;
import dannypx.foe.placeholder.parser.ast.AstError;
import dannypx.foe.placeholder.parser.ast.Group;
import dannypx.foe.placeholder.token.PlaceholderParseException;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlaceholderCompiler {
    private static final Map<String, Group> CACHE = new ConcurrentHashMap<>();

    public static Group compile(String source) {
        // texts that change (typing in an editor, values put into a text) would each stay here for good: kept small
        if (CACHE.size() > 1024) CACHE.clear();
        return CACHE.computeIfAbsent(source, PlaceholderCompiler::compileOrError);
    }

    public static Group compileOrError(String source) {
        try {
            return new PlaceholderParser(source).parse();
        } catch (PlaceholderParseException e) {
            return PlaceholderCompiler.errorGroup(PlaceholderCompiler.buildErrorMessage(source, e));
        } catch (RuntimeException e) {
            return PlaceholderCompiler.errorGroup("Failed to parse placeholder string: " + e);
        }
    }

    public static Group errorGroup(String message) {
        return new Group(List.of(new AstError(message, 0, 0)));
    }

    private static String buildErrorMessage(String source, PlaceholderParseException e) {
        if(e.position < 0) {
            return "Placeholder syntax error: " + e.getMessage();
        }
        return "Placeholder syntax error:" + e.getMessage() + " [" + snippet(source, e.position) + "]";
    }

    private static String snippet(String source, int position) {
        int radius = 12;
        int clampedPos = Math.clamp(source.length(), 0, position);
        int start = Math.max(0, clampedPos - radius);
        int end = Math.min(source.length(), clampedPos + radius);

        String before = source.substring(start, clampedPos);
        String after = source.substring(clampedPos, end);
        String prefix = start > 0 ? "..." : "";
        String suffix = end < source.length() ? "..." : "";

        return prefix + before + "»" + after + suffix;
    }

    public static void clear() {
        CACHE.clear();
    }

    public static int size() {
        return CACHE.size();
    }

    /// Throttler

    private static volatile int updateIntervalMillis = 250;
    private static long lastGlobalUpdateAtMillis = -1L;
    private static long updateGeneration = 0L;

    public static void setUpdateIntervalMillis(int millis) {
        updateIntervalMillis = millis;
    }

    public static int getUpdateIntervalMillis() {
        return updateIntervalMillis;
    }

    public static void tick() {
        long now = System.currentTimeMillis();

        if(updateIntervalMillis <= 0
                || lastGlobalUpdateAtMillis < 0
                || (now - lastGlobalUpdateAtMillis) >= updateIntervalMillis
        ) {
            lastGlobalUpdateAtMillis = now;
            updateGeneration++;
        }
    }

    static long currentGeneration() {
        return updateGeneration;
    }

    public static class ThrottledPlaceholder {
        private final Group ast;
        private PlaceholderResult lastResult = new PlaceholderResult(Component.empty(), new boolean[]{ true, false }, List.of());
        private long lastSeenGeneration = -1;

        public ThrottledPlaceholder(String source) {
            this.ast = PlaceholderCompiler.compile(source);
        }

        public PlaceholderResult get(PlaceholderEvaluator evaluator) {
            return get(evaluator, true);
        }

        public PlaceholderResult get(PlaceholderEvaluator evaluator, boolean isThrottled) {
            if(isThrottled) {
                long gen = PlaceholderCompiler.currentGeneration();
                if(gen != lastSeenGeneration) {
                    lastResult = evaluator.eval(ast);
                    lastSeenGeneration = gen;
                }
                return lastResult;
            } else {
                return evaluator.eval(ast);
            }
        }

        public void forceRefresh() {
            lastSeenGeneration = -1L;
        }
    }
}
