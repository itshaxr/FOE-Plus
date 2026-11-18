package dannypx.foe.handler.logic;

import dannypx.foe.handler.Handler;
import dannypx.foe.type.tuple.Pair;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.MutableComponent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CodeExecuterHandler extends Handler {
    private static CodeExecuterHandler INSTANCE = new CodeExecuterHandler();

    public static CodeExecuterHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new CodeExecuterHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private static final List<ScheduledTask> TASKS = new ArrayList<>();
    private static final List<ScheduledTask> PENDING = new ArrayList<>();
    static { MemoryGuard.register("scheduled", () -> TASKS.size() + PENDING.size(), () -> {}); }

    public void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            synchronized (PENDING) {
                TASKS.addAll(PENDING);
                PENDING.clear();
            }

            Iterator<ScheduledTask> iterator = TASKS.iterator();
            while (iterator.hasNext()) {
                ScheduledTask task = iterator.next();
                task.ticks--;
                if (task.ticks <= 0) {
                    task.runnable.run();
                    iterator.remove();
                }
            }
        });
    }

    public static void runLater(int ticks, Runnable runnable) {
        synchronized (PENDING) { PENDING.add(new ScheduledTask(ticks, runnable)); }
    }

    private static class ScheduledTask {
        int ticks;
        Runnable runnable;

        ScheduledTask(int ticks, Runnable runnable) {
            this.ticks = ticks;
            this.runnable = runnable;
        }
    }
    //endregion

    //region Methods
    //endregion

    //region Dev
    /// Field, Pair<Value, Tooltip>
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
        );
    }
    //endregion
}
