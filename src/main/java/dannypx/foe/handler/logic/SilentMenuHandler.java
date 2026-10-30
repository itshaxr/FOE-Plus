package dannypx.foe.handler.logic;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Reads the server's /menu without showing it: sends /menu, stops the menu from opening, reads
 * its items as they arrive (your profile for Luck & Scale, Daily Missions for the daily panel)
 * and closes it again. Only runs while you have no screen open, so nothing you're doing is
 * interrupted. Requests made close together are merged into one read.
 */
public class SilentMenuHandler {
    private static SilentMenuHandler INSTANCE = new SilentMenuHandler();

    /// Never ask more often than this.
    private static final long MIN_GAP_MS = 5_000L;
    /// How long to wait for the server to answer /menu.
    private static final long ANSWER_MS = 4_000L;
    /// A request that couldn't run (you were in a menu the whole time) is dropped after this.
    private static final long GIVE_UP_MS = 60_000L;

    private long dueAt = 0L;
    private long lastRequest = 0L;
    /// While > now, the next menu the server opens is our silent /menu.
    private long expectMenuUntil = 0L;
    /// Container id of the silent menu being read, or -1.
    private int containerId = -1;
    private long openedAt = 0L;

    public static SilentMenuHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new SilentMenuHandler();
        }
        return INSTANCE;
    }

    /// Asks for a read {@code delayMs} from now (or sooner, if one is already due earlier).
    public void request(long delayMs) {
        long at = System.currentTimeMillis() + Math.max(0L, delayMs);
        if (dueAt == 0L || at < dueAt) dueAt = at;
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.player == null || mc.getConnection() == null) {
            dueAt = 0L;
            containerId = -1;
            return;
        }
        // The server never sent the contents: close the menu anyway.
        if (containerId != -1 && now - openedAt > ANSWER_MS) close(mc);

        if (dueAt == 0L || now < dueAt) return;
        if (now - dueAt > GIVE_UP_MS) {
            dueAt = 0L;
            return;
        }
        if (mc.screen != null || containerId != -1 || now <= expectMenuUntil || now - lastRequest < MIN_GAP_MS) return;
        dueAt = 0L;
        lastRequest = now;
        expectMenuUntil = now + ANSWER_MS;
        mc.getConnection().sendCommand("menu");
    }

    /// From the packet listener: true when this menu is our silent /menu and must not open.
    public boolean onOpenScreen(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (System.currentTimeMillis() > expectMenuUntil || mc.screen != null) return false;
        expectMenuUntil = 0L;
        containerId = id;
        openedAt = System.currentTimeMillis();
        return true;
    }

    /// From the packet listener: the silent menu's items arrived; hand them out and close it.
    public void onContainerContent(int id, List<ItemStack> items) {
        if (containerId == -1 || id != containerId) return;
        // The packet lists the menu's own slots first, then your inventory (36 slots).
        List<ItemStack> menuItems = items.size() > 36 ? items.subList(0, items.size() - 36) : items;
        ProfileStatsHandler.instance().readMenu(menuItems);
        DailyQuestHandler.instance().readMenu(menuItems);
        close(Minecraft.getInstance());
    }

    private void close(Minecraft mc) {
        if (containerId == -1) return;
        if (mc.getConnection() != null) mc.getConnection().send(new ServerboundContainerClosePacket(containerId));
        containerId = -1;
    }
}
