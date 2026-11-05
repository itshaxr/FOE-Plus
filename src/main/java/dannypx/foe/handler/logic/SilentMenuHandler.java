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

    /// Never ask more often than this. Every read opens a menu on the server for a moment, and
    /// while it is open the server ignores clicks and casting, so reads are kept rare.
    private static final long MIN_GAP_MS = 60_000L;
    /// Nor within this long of your last catch or cast (you're fishing: don't get in the way).
    private static final long QUIET_AFTER_FISHING_MS = 20_000L;
    private long lastFishingSeen = 0L;
    /// How long to wait for the server to answer /menu.
    private static final long ANSWER_MS = 4_000L;
    /// A request that couldn't run (you were busy the whole time) waits up to this long.
    private static final long GIVE_UP_MS = 10 * 60_000L;

    private long dueAt = 0L;
    private long lastRequest = 0L;
    /// While > now, the next menu the server opens is our silent /menu.
    private long expectMenuUntil = 0L;
    /// Container id of the silent menu being read, or -1.
    private int containerId = -1;
    private long openedAt = 0L;

    /// Your own commands/chat: a background /menu is never sent right after you did something, so
    /// it can't collide with a menu you just asked for (like /s).
    /// The server makes you wait 5 s between some commands ("Wait 5 seconds."), so background
    /// commands keep at least this far from your own commands and from each other.
    private static final long QUIET_AFTER_ACTIVITY_MS = 6_000L;
    private static final long BACKGROUND_COMMAND_GAP_MS = 6_000L;
    private long lastBackgroundCommandAt = 0L;
    private String lastBackgroundCommand = "";
    /// …nor right after you closed a screen (or the chat box).
    private static final long QUIET_AFTER_SCREEN_MS = 1_500L;
    private long lastUserActivity = 0L;
    private long lastScreenSeen = 0L;
    private boolean sendingOwn = false;
    /// You sent a command while we were waiting for our /menu: the next menu may be yours.
    private boolean userCommandDuringWait = false;

    /// Called for every command or chat message sent; ours are ignored.
    public void onMessageSent() {
        if (sendingOwn) return;
        lastUserActivity = System.currentTimeMillis();
        if (lastUserActivity <= expectMenuUntil) userCommandDuringWait = true;
    }

    public static SilentMenuHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new SilentMenuHandler();
        }
        return INSTANCE;
    }

    /// True when a background command (a silent /menu, the contest refresh) may be sent now: no
    /// screen of any kind is open (inventory, chest, Auction House, chat...), you closed the last
    /// one a moment ago at least, you haven't just typed a command, and no silent /menu is in
    /// progress. Anything that can't run now waits and tries again.
    public boolean isQuietForBackgroundCommand() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.player == null || mc.getConnection() == null || mc.screen != null) return false;
        if (now - lastScreenSeen < QUIET_AFTER_SCREEN_MS || now - lastUserActivity < QUIET_AFTER_ACTIVITY_MS) return false;
        if (now - lastBackgroundCommandAt < BACKGROUND_COMMAND_GAP_MS) return false;
        return containerId == -1 && now > expectMenuUntil;
    }

    /// Called just before a background command is sent.
    public void noteBackgroundCommand(String command) {
        lastBackgroundCommandAt = System.currentTimeMillis();
        lastBackgroundCommand = command;
    }

    private static final java.util.regex.Pattern COOLDOWN = java.util.regex.Pattern.compile("^\\s*Wait (\\d+) seconds?\\.?\\s*$");

    /// The server's "Wait 5 seconds." answer to one of our background commands: hidden from chat,
    /// and the command is tried again once the wait is over. Your own commands' answers show.
    public boolean hideCooldownReply(String text) {
        if (System.currentTimeMillis() - lastBackgroundCommandAt > 3_000L) return false;
        if (lastUserActivity >= lastBackgroundCommandAt) return false; // the reply may be to your own command
        java.util.regex.Matcher m = COOLDOWN.matcher(net.minecraft.ChatFormatting.stripFormatting(text) == null ? "" : net.minecraft.ChatFormatting.stripFormatting(text));
        if (!m.matches()) return false;
        long retryMs = Long.parseLong(m.group(1)) * 1000L + 1_500L;
        if (lastBackgroundCommand.equals("menu")) {
            expectMenuUntil = 0L;
            request(retryMs);
        } else if (lastBackgroundCommand.equals("contest")) {
            ContestTrackerHandler.instance().retryRefreshIn(retryMs);
        }
        lastBackgroundCommandAt = 0L;
        return true;
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
        if (mc.screen != null) lastScreenSeen = now;
        if (mc.player.fishing != null) lastFishingSeen = now;

        if (dueAt == 0L || now < dueAt) return;
        if (now - dueAt > GIVE_UP_MS) {
            dueAt = 0L;
            return;
        }
        if (mc.screen != null || containerId != -1 || now <= expectMenuUntil || now - lastRequest < MIN_GAP_MS) return;
        if (now - lastUserActivity < QUIET_AFTER_ACTIVITY_MS || now - lastScreenSeen < QUIET_AFTER_SCREEN_MS) return;
        if (now - lastBackgroundCommandAt < BACKGROUND_COMMAND_GAP_MS) return;
        if (now - lastFishingSeen < QUIET_AFTER_FISHING_MS) return;
        dueAt = 0L;
        lastRequest = now;
        expectMenuUntil = now + ANSWER_MS;
        userCommandDuringWait = false;
        sendingOwn = true;
        noteBackgroundCommand("menu");
        try {
            mc.getConnection().sendCommand("menu");
        } finally {
            sendingOwn = false;
        }
    }

    /// From the packet listener: true when this menu is our silent /menu and must not open.
    /// Only a menu titled exactly like the main menu is ever held back. Any other menu (the
    /// Auction House from an NPC, /s, a crate) always opens: hiding it left the server thinking
    /// it was open, so clicks, casting and NPCs stopped working until another menu was opened.
    public boolean onOpenScreen(int id, String title) {
        Minecraft mc = Minecraft.getInstance();
        if (System.currentTimeMillis() > expectMenuUntil || mc.screen != null) return false;
        if (!DailyQuestHandler.MAIN_MENU_SCREEN_CONTAINER.equals(title)) return false;
        if (userCommandDuringWait) return false; // you asked for /menu yourself: show it
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
