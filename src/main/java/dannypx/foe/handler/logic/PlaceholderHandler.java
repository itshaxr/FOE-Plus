package dannypx.foe.handler.logic;

import dannypx.foe.handler.Handler;
import dannypx.foe.placeholder.evaluator.PlaceholderEvaluator;
import dannypx.foe.placeholder.evaluator.PlaceholderResult;
import dannypx.foe.placeholder.compiler.PlaceholderCompiler;
import dannypx.foe.placeholder.registry.PlaceholderRegistry;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlaceholderHandler extends Handler {
    private static PlaceholderHandler INSTANCE = new PlaceholderHandler();

    public static PlaceholderHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new PlaceholderHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private final PlaceholderEvaluator evaluator = new PlaceholderEvaluator();
    private final Map<String, PlaceholderCompiler.ThrottledPlaceholder> throttled = new ConcurrentHashMap<>();
    { dannypx.foe.handler.logic.MemoryGuard.register("placeholders", throttled::size, throttled::clear); }
    //endregion

    //region Methods
    @Override
    public void tick() {
        PlaceholderCompiler.tick();
    }

    @Override
    public void init() {
        PlaceholderRegistry.init();
    }

    public PlaceholderResult resolve(String placeholderString) {
        return resolve(placeholderString, true);
    }

    public PlaceholderResult resolve(String placeholderString, boolean isThrottled) {
        if (throttled.size() > 1024) throttled.clear();     // never more than the texts in use
        PlaceholderCompiler.ThrottledPlaceholder t = throttled.computeIfAbsent(placeholderString, PlaceholderCompiler.ThrottledPlaceholder::new);
        return t.get(evaluator, isThrottled);
    }

    public void setUpdateIntervalMillis(int millis) {
        PlaceholderCompiler.setUpdateIntervalMillis(millis);
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
