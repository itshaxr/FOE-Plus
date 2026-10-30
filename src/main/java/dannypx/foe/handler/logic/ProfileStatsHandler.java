package dannypx.foe.handler.logic;

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
 * open; the Luck & Scale panel shows the last values it saw and how old they are.
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
        if (mc.player == null || !(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        UUID me = mc.player.getUUID();

        List<String> best = null;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.getItem() != Items.PLAYER_HEAD) continue;
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null || lore.lines().isEmpty()) continue;
            List<String> lines = lore.lines().stream().map(Component::getString).toList();
            if (lines.stream().noneMatch(l -> hasWord(l, "luck") || hasWord(l, "scale"))) continue;
            // Your own head first; another head (e.g. someone else's profile) only as a fallback
            // when the menu has no head with your UUID.
            if (isOwnHead(stack, me, mc.player.getGameProfile().name())) {
                best = lines;
                break;
            }
            if (best == null && screen.getTitle().getString().equals(DailyQuestHandler.MAIN_MENU_SCREEN_CONTAINER)) best = lines;
        }
        if (best == null) return;

        String newLuck = null;
        String newScale = null;
        for (String line : best) {
            if (newLuck == null) newLuck = valueFor(line, "luck");
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
