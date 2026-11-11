package dannypx.foe.handler.logic;

import dannypx.foe.handler.fetch.GenericContainerScreenHandler;
import dannypx.foe.handler.fetch.QuestScreenHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a server menu without showing it: sends the command, stops that menu from opening, reads
 * its items as they arrive and closes it again. /menu gives your profile (Luck & Scale) and the
 * Daily Missions; /quests gives the location quests for the Quests panel. Only runs while you
 * have no screen open, so nothing you're doing is interrupted. Requests made close together are
 * merged into one read.
 */
public class SilentMenuHandler {
    private static SilentMenuHandler INSTANCE = new SilentMenuHandler();

    /// What can be read: the command and the title of the menu it opens.
    public enum Target {
        MENU("menu", DailyQuestHandler.MAIN_MENU_SCREEN_CONTAINER),
        QUESTS("quests", GenericContainerScreenHandler.QUEST_SCREEN_CONTAINER);

        final String command;
        final String title;

        Target(String command, String title) {
            this.command = command;
            this.title = title;
        }
    }

    /// Ordinary reads (e.g. after catches) never come more often than this per menu. Every read
    /// opens a menu on the server for a moment, and while it is open the server ignores clicks
    /// and casting, so reads are kept rare.
    private static final long MIN_GAP_MS = 60_000L;
    /// Ordinary reads also wait this long after your last catch or cast (you're fishing).
    private static final long QUIET_AFTER_FISHING_MS = 20_000L;
    /// A location change is read right away (priority): it skips the two waits above, and only
    /// waits up to this long for a moment when your bobber isn't out.
    private static final long PRIORITY_WAIT_FOR_BOBBER_MS = 15_000L;
    private long lastFishingSeen = 0L;
    /// How long to wait for the server to answer.
    private static final long ANSWER_MS = 4_000L;
    /// A request that couldn't run (you were busy the whole time) waits up to this long.
    private static final long GIVE_UP_MS = 10 * 60_000L;

    private final Map<Target, Long> dueAt = new EnumMap<>(Target.class);
    private final Map<Target, Boolean> priority = new EnumMap<>(Target.class);
    /// How long a priority read may wait for your bobber to come in (shortest asked for wins).
    private final Map<Target, Long> bobberWait = new EnumMap<>(Target.class);
    private final Map<Target, Long> lastRead = new EnumMap<>(Target.class);
    /// The read in progress (sent, waiting for its menu), or null.
    private Target current = null;
    /// While > now, the next menu the server opens with current's title is our silent read.
    private long expectMenuUntil = 0L;
    /// Container id of the silent menu being read, or -1.
    private int containerId = -1;
    private long openedAt = 0L;

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
    /// You sent a command while we were waiting for our menu: the next menu may be yours.
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

    /// True when a background command (a silent read, the contest refresh) may be sent now: no
    /// screen of any kind is open (inventory, chest, Auction House, chat...), you closed the last
    /// one a moment ago at least, you haven't just typed a command, and no silent read is in
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
        String plain = net.minecraft.ChatFormatting.stripFormatting(text);
        java.util.regex.Matcher m = COOLDOWN.matcher(plain == null ? "" : plain);
        if (!m.matches()) return false;
        long retryMs = Long.parseLong(m.group(1)) * 1000L + 1_500L;
        Target retry = null;
        for (Target t : Target.values()) if (t.command.equals(lastBackgroundCommand)) retry = t;
        if (retry != null) {
            expectMenuUntil = 0L;
            current = null;
            schedule(retry, retryMs, true);
        } else if (lastBackgroundCommand.equals("contest")) {
            ContestTrackerHandler.instance().retryRefreshIn(retryMs);
        } else if (lastBackgroundCommand.equals("communityevent")) {
            CommunityEventHandler.instance().retryIn(retryMs);
        } else if (lastBackgroundCommand.equals("events")) {
            EventTimesHandler.instance().retryIn(retryMs);
        } else if (lastBackgroundCommand.equals("minievent")) {
            MiniEventHandler.instance().retryIn(retryMs);
        } else if (lastBackgroundCommand.equals("active")) {
            ActiveBoostersHandler.instance().retryIn(retryMs);
        }
        lastBackgroundCommandAt = 0L;
        return true;
    }

    /// An ordinary /menu read {@code delayMs} from now (after catches, on joining).
    public void request(long delayMs) {
        schedule(Target.MENU, delayMs, false);
    }

    /// A /menu read that can't wait for you to stop fishing (a booster started or ended, so Luck
    /// and Scale just changed): it waits at most a few seconds for a moment between casts.
    public void requestSoon(long delayMs) {
        schedule(Target.MENU, delayMs, true);
    }

    /// A /menu read for a change you just made yourself (you equipped or unequipped a pet), so
    /// Luck & Scale catch up within a few seconds: it waits only a moment for your bobber.
    public static final long PET_CHANGE_DELAY_MS = 1_000L;
    public static final long PET_CHANGE_BOBBER_WAIT_MS = 2_500L;

    /// What you're wearing (item and name of each armor piece), to notice a change; null = not seen yet.
    private String lastArmor = null;

    private static String armorKey(net.minecraft.world.entity.player.Player player) {
        StringBuilder key = new StringBuilder();
        for (net.minecraft.world.entity.EquipmentSlot slot : ARMOR_SLOTS) {
            net.minecraft.world.item.ItemStack stack = player.getItemBySlot(slot);
            key.append(stack.isEmpty() ? "-" : stack.getItem() + "/" + stack.getHoverName().getString()).append('|');
        }
        return key.toString();
    }

    private static final net.minecraft.world.entity.EquipmentSlot[] ARMOR_SLOTS = {
            net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
            net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};

    public void requestForPetChange() {
        schedule(Target.MENU, PET_CHANGE_DELAY_MS, true);
        Long wait = bobberWait.get(Target.MENU);
        if (wait == null || PET_CHANGE_BOBBER_WAIT_MS < wait) bobberWait.put(Target.MENU, PET_CHANGE_BOBBER_WAIT_MS);
    }

    /// Package-private for tests: true while a priority read due {@code sinceDueMs} ago should keep
    /// waiting for a moment between casts.
    static boolean waitForBobber(boolean bobberOut, long sinceDueMs, long maxWaitMs) {
        return bobberOut && sinceDueMs < maxWaitMs;
    }

    /// You arrived somewhere new: read /menu (Luck & Scale, Daily Quests) and, if wanted,
    /// /quests (Quests panel) right away, without the usual waits.
    public void requestLocationRefresh(long delayMs, boolean menu, boolean quests) {
        if (menu) schedule(Target.MENU, delayMs, true);
        if (quests) schedule(Target.QUESTS, delayMs, true);
    }

    private void schedule(Target target, long delayMs, boolean isPriority) {
        long at = System.currentTimeMillis() + Math.max(0L, delayMs);
        Long due = dueAt.get(target);
        if (due == null || at < due) dueAt.put(target, at);
        if (isPriority) priority.put(target, true);
        else priority.putIfAbsent(target, false);
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.player == null || mc.getConnection() == null) {
            dueAt.clear();
            priority.clear();
            bobberWait.clear();
            containerId = -1;
            current = null;
            lastArmor = null;
            return;
        }
        // Armor put on or taken off changes Luck & Scale: read /menu again in a few seconds.
        String armor = armorKey(mc.player);
        if (lastArmor != null && !armor.equals(lastArmor)) requestForPetChange();
        lastArmor = armor;
        // The server never sent the contents: close the menu anyway.
        if (containerId != -1 && now - openedAt > ANSWER_MS) close(mc);
        // Asked, but the menu never came (e.g. the command doesn't exist here): stop waiting.
        if (current != null && containerId == -1 && now > expectMenuUntil) current = null;
        if (mc.screen != null) lastScreenSeen = now;
        boolean bobberOut = mc.player.fishing != null;
        if (bobberOut) lastFishingSeen = now;

        if (dueAt.isEmpty() || !isQuietForBackgroundCommand()) return;

        for (Target target : Target.values()) {
            Long due = dueAt.get(target);
            if (due == null || now < due) continue;
            if (now - due > GIVE_UP_MS) {
                dueAt.remove(target);
                priority.remove(target);
                bobberWait.remove(target);
                continue;
            }
            boolean urgent = Boolean.TRUE.equals(priority.get(target));
            if (urgent) {
                // Prefer a moment between casts, but don't wait for ever.
                if (waitForBobber(bobberOut, now - due, bobberWait.getOrDefault(target, PRIORITY_WAIT_FOR_BOBBER_MS))) continue;
            } else {
                if (now - lastRead.getOrDefault(target, 0L) < MIN_GAP_MS) continue;
                if (now - lastFishingSeen < QUIET_AFTER_FISHING_MS) continue;
            }
            send(mc, target, now);
            return; // one at a time; the next one waits for the command gap
        }
    }

    private void send(Minecraft mc, Target target, long now) {
        dueAt.remove(target);
        priority.remove(target);
        bobberWait.remove(target);
        lastRead.put(target, now);
        current = target;
        expectMenuUntil = now + ANSWER_MS;
        userCommandDuringWait = false;
        sendingOwn = true;
        noteBackgroundCommand(target.command);
        try {
            mc.getConnection().sendCommand(target.command);
        } finally {
            sendingOwn = false;
        }
    }

    /// From the packet listener: true when this menu is our silent read and must not open.
    /// Only a menu titled exactly like the one we asked for is ever held back. Any other menu
    /// (the Auction House from an NPC, /s, a crate) always opens: hiding it left the server
    /// thinking it was open, so clicks, casting and NPCs stopped working until another opened.
    public boolean onOpenScreen(int id, String title) {
        Minecraft mc = Minecraft.getInstance();
        if (current == null || System.currentTimeMillis() > expectMenuUntil || mc.screen != null) return false;
        if (!current.title.equals(title)) return false;
        if (userCommandDuringWait) return false; // you asked for it yourself: show it
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
        if (current == Target.QUESTS) {
            QuestScreenHandler.instance().readItems(menuItems);
        } else {
            ProfileStatsHandler.instance().readMenu(menuItems);
            DailyQuestHandler.instance().readMenu(menuItems);
        }
        close(Minecraft.getInstance());
    }

    private void close(Minecraft mc) {
        if (containerId == -1) return;
        if (mc.getConnection() != null) mc.getConnection().send(new ServerboundContainerClosePacket(containerId));
        containerId = -1;
        current = null;
    }
}
