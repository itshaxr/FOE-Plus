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
    /// "Time Fishing" is taken from your profile once each login (FOE+ counts on from there).
    private boolean fishTimeSynced = false;
    private long fishTimeRequestedAt = 0L;
    private long joinedAt = 0L;
    private static final long FISH_TIME_READ_DELAY_MS = 10_000L;
    /// A background read can be dropped while you keep fishing: ask again after this.
    private static final long FISH_TIME_RETRY_MS = 2L * 60L * 1000L;
    private static final Pattern TIME_FISHING = Pattern.compile("(?i)\\btime\\s+fishing\\b[^0-9]*(.*)$");

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

    /// A new login: read "Time Fishing" from your profile again.
    public void onJoin() {
        fishTimeSynced = false;
        fishTimeRequestedAt = 0L;
        joinedAt = 0L;
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        this.tickAutoRefresh(mc);
        this.tickFishTimeRead();
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
            // Someone else's profile (e.g. viewing another player) is never taken as yours outside
            // your own /menu.
            if (best == null && isMainMenu) best = lines;
        }
        if (best == null) return;

        if (!fishTimeSynced) {
            long fishing = timeFishing(best);
            if (fishing >= 0) {
                fishTimeSynced = true;
                FishSessionHandler.instance().syncServerFishTime(fishing);
            }
        }

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
    private FoeLocations.Location lastLocation = null;
    private boolean locationKnown = false;

    private void tickAutoRefresh(Minecraft mc) {
        FoeLocations.Location here = PetLocationHandler.currentLocation();
        if (locationKnown && here == lastLocation) return;
        locationKnown = true;
        lastLocation = here;
        if (here == null) return;
        // A new location: read /menu (Luck & Scale, Daily Quests) and /quests (Quests panel) now.
        boolean menu = (Configs.hudConfig.showLuckScalePanel.get() && Configs.hudConfig.luckScaleAutoRefresh.get())
                || (Configs.hudConfig.showDailyQuestPanel.get() && Configs.hudConfig.dailyQuestAutoRefresh.get());
        boolean quests = Configs.hudConfig.showQuestPanel.get();
        SilentMenuHandler.instance().requestLocationRefresh(ARRIVAL_DELAY_MS, menu, quests);
    }

    /// Once each login, a quiet /menu read a few seconds in, for "Time Fishing" (skipped if you
    /// opened /menu yourself first, retried until it's been read).
    private void tickFishTimeRead() {
        if (fishTimeSynced) return;
        if (!dannypx.foe.handler.fetch.TabOverlayHandler.instance().isInInstance()) return;
        long now = System.currentTimeMillis();
        if (joinedAt == 0L) joinedAt = now;
        if (now - joinedAt < FISH_TIME_READ_DELAY_MS) return;
        if (fishTimeRequestedAt != 0L && now - fishTimeRequestedAt < FISH_TIME_RETRY_MS) return;
        fishTimeRequestedAt = now;
        SilentMenuHandler.instance().request(0L);
    }

    /// "Time Fishing: 10d 5h 25m 14s" on the profile → ms, or -1 when it isn't there.
    /// Package-private for tests.
    static long timeFishing(List<String> lore) {
        for (String raw : lore) {
            String line = raw.replaceAll("[\\uE000-\\uF8FF]", " ");
            Matcher m = TIME_FISHING.matcher(line);
            if (!m.find()) continue;
            long ms = EventTimesHandler.parseDuration(m.group(1));
            if (ms >= 0) return ms;
        }
        return -1L;
    }

    /// Items of a /menu read in the background by {@link SilentMenuHandler}.
    public void readMenu(List<ItemStack> items) {
        this.read(items, true);
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
