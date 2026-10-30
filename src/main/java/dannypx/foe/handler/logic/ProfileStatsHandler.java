package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.helper.FoeLocations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Your current Luck and Scale as the server shows them on your profile in /menu (your player
 * head), so every booster, event and gear bonus is included. Read every tick while the menu is
 * open, and automatically a few seconds after every location change: the mod quietly runs
 * /menu, reads your head from the menu's contents and closes it again without ever showing it
 * (only when you have no other screen open). The panel shows how old the values are.
 */
public class ProfileStatsHandler {
    private static ProfileStatsHandler INSTANCE = new ProfileStatsHandler();

    /// A number like 1,245 / 12.5 / +300 / 45%.
    private static final Pattern NUMBER = Pattern.compile("[+-]?\\d[\\d,]*(?:\\.\\d+)?%?");

    private String luck = null;
    private String scale = null;
    private long readAt = 0L;
    private FoeLocations.Location readAtLocation = null;
    private boolean loggedMiss = false;

    public static ProfileStatsHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new ProfileStatsHandler();
        }
        return INSTANCE;
    }

    public boolean hasData() {
        return luck != null || scale != null;
    }

    public String getLuck() {
        return luck;
    }

    public String getScale() {
        return scale;
    }

    public long getReadAt() {
        return readAt;
    }

    /// True when you have moved to another location since the profile was read (the server's
    /// numbers depend on where you fish, so they may have changed).
    public boolean isFromOtherLocation() {
        return hasData() && readAtLocation != PetLocationHandler.currentLocation();
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        this.tickAutoRefresh(mc);
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        List<ItemStack> items = new java.util.ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (!(slot.container instanceof Inventory)) items.add(slot.getItem());
        }
        this.read(items, screen.getTitle().getString().equals(DailyQuestHandler.MAIN_MENU_SCREEN_CONTAINER));
    }

    /// Reads Luck/Scale from the profile head among {@code items} (a menu's own slots).
    /// {@code isMainMenu}: another head may be used when none is yours.
    private void read(List<ItemStack> items, boolean isMainMenu) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID me = mc.player.getUUID();

        List<String> best = null;
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) continue;
            // Your profile: "Profile of <name>" (a player head on FishOnMC).
            String name = stack.getHoverName().getString();
            boolean profileItem = name.startsWith("Profile of");
            if (!profileItem && stack.getItem() != Items.PLAYER_HEAD) continue;
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null || lore.lines().isEmpty()) continue;
            List<String> lines = lore.lines().stream().map(Component::getString).toList();
            if (lines.stream().noneMatch(l -> hasWord(l, "luck") || hasWord(l, "scale"))) continue;
            // Your own head first; another head only as a fallback in the main menu.
            String myName = mc.player.getGameProfile().name();
            if (isOwnHead(stack, me, myName) || name.equalsIgnoreCase("Profile of " + myName)) {
                best = lines;
                break;
            }
            if (best == null && (isMainMenu || profileItem)) best = lines;
        }
        if (best == null) return;

        String newLuck = null;
        String newScale = null;
        for (String line : best) {
            // "Pet Luck +82.5%" is a different stat from your Luck.
            if (newLuck == null && !hasWord(line, "pet")) newLuck = valueFor(line, "luck");
            if (newScale == null) newScale = valueFor(line, "scale");
        }
        if (newLuck == null && newScale == null) {
            if (!loggedMiss) {
                loggedMiss = true;
                LoggerHandler.info("Profile head found but no Luck/Scale numbers in its lore: " + best);
            }
            return;
        }
        luck = newLuck;
        scale = newScale;
        readAt = System.currentTimeMillis();
        readAtLocation = PetLocationHandler.currentLocation();
    }

    //region Auto refresh on location change
    /// Wait after arriving, so the server has applied the new location's bonuses.
    private static final long ARRIVAL_DELAY_MS = 3_000L;
    /// Give up waiting for a closed screen after this long (try again next location change).
    private static final long GIVE_UP_MS = 60_000L;
    /// Never ask more often than this.
    private static final long MIN_GAP_MS = 10_000L;
    /// How long to wait for the server to answer /menu.
    private static final long ANSWER_MS = 4_000L;

    private FoeLocations.Location lastLocation = null;
    private boolean locationKnown = false;
    private long refreshDueAt = 0L;
    private long lastRequest = 0L;
    /// While > now, the next menu the server opens is our silent /menu.
    private long expectMenuUntil = 0L;
    /// Container id of the silent menu we are reading, or -1.
    private int silentContainerId = -1;
    private long silentOpenedAt = 0L;

    private void tickAutoRefresh(Minecraft mc) {
        long now = System.currentTimeMillis();
        FoeLocations.Location here = PetLocationHandler.currentLocation();
        if (!locationKnown || here != lastLocation) {
            locationKnown = true;
            lastLocation = here;
            refreshDueAt = here == null ? 0L : now + ARRIVAL_DELAY_MS;
        }

        // The server never sent the contents: close the menu anyway.
        if (silentContainerId != -1 && now - silentOpenedAt > ANSWER_MS) closeSilentMenu(mc);

        if (refreshDueAt == 0L || now < refreshDueAt) return;
        if (now - refreshDueAt > GIVE_UP_MS) {
            refreshDueAt = 0L;
            return;
        }
        if (!Configs.hudConfig.showLuckScalePanel.get() || !Configs.hudConfig.luckScaleAutoRefresh.get()) {
            refreshDueAt = 0L;
            return;
        }
        // Only while you aren't in a menu or typing, so nothing you're doing gets interrupted.
        if (mc.screen != null || mc.getConnection() == null || silentContainerId != -1 || now - lastRequest < MIN_GAP_MS) return;
        refreshDueAt = 0L;
        lastRequest = now;
        expectMenuUntil = now + ANSWER_MS;
        mc.getConnection().sendCommand("menu");
    }

    /// From the packet listener: true when this menu is our silent /menu and must not open.
    public boolean onOpenScreen(int containerId) {
        Minecraft mc = Minecraft.getInstance();
        if (System.currentTimeMillis() > expectMenuUntil || mc.screen != null) return false;
        expectMenuUntil = 0L;
        silentContainerId = containerId;
        silentOpenedAt = System.currentTimeMillis();
        return true;
    }

    /// From the packet listener: the silent menu's items arrived; read them and close it.
    public void onContainerContent(int containerId, List<ItemStack> items) {
        if (containerId != silentContainerId || silentContainerId == -1) return;
        this.read(items, true);
        closeSilentMenu(Minecraft.getInstance());
    }

    private void closeSilentMenu(Minecraft mc) {
        if (silentContainerId == -1) return;
        if (mc.getConnection() != null) {
            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(silentContainerId));
        }
        silentContainerId = -1;
    }
    //endregion

    private static boolean isOwnHead(ItemStack stack, UUID me, String myName) {
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        if (profile == null) return false;
        try {
            if (me.equals(profile.partialProfile().id())) return true;
            return profile.name().map(name -> name.equalsIgnoreCase(myName)).orElse(false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * The number that goes with {@code word} on a lore line: "♣ Luck: 1,245", "Luck 245",
     * "+300 Luck", "Scale » 12.5%". Server tooltip glyphs are ignored. Null when the line has no
     * such word or no number. Package-private for tests.
     */
    static String valueFor(String rawLine, String word) {
        String line = rawLine.replaceAll("[\\uE000-\\uF8FF]", " ");
        String lower = line.toLowerCase(Locale.ROOT);
        Matcher w = Pattern.compile("(?<![a-z])" + word + "(?![a-z])").matcher(lower);
        if (!w.find()) return null;
        // Prefer the number after the word ("Luck: 245"), then the one right before it ("+245 Luck").
        Matcher after = NUMBER.matcher(line);
        if (after.find(w.end())) return clean(after.group());
        String before = line.substring(0, w.start());
        Matcher m = NUMBER.matcher(before);
        String last = null;
        while (m.find()) last = m.group();
        return last == null ? null : clean(last);
    }

    private static String clean(String number) {
        return number.startsWith("+") ? number.substring(1) : number;
    }

    private static boolean hasWord(String line, String word) {
        return Pattern.compile("(?<![a-z])" + word + "(?![a-z])").matcher(line.toLowerCase(Locale.ROOT)).find();
    }
}
