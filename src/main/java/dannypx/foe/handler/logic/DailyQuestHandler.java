package dannypx.foe.handler.logic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads your daily missions from the "Daily Missions" item in the server's main menu and keeps
 * them up to date from chat. Ported from FOECollab's DailyQuestHandler (AGPL-3.0).
 * Open the main menu once per session so the panel has something to show.
 */
public class DailyQuestHandler {
    private static DailyQuestHandler INSTANCE = new DailyQuestHandler();

    /** Title of the server's main menu, which holds the Daily Missions item. */
    public static final String MAIN_MENU_SCREEN_CONTAINER = "할";

    private static final Pattern DAILY_QUEST_PATTERN = Pattern.compile("\\|\\s*(?<goal>.+?)\\s*\\((?<progress>\\d+)\\s*/\\s*(?<needed>\\d+)\\)");
    private static final Pattern MISSION_NAME_PATTERN = Pattern.compile("^\\s*-\\s+\\x{1F3A3}\\s+(?<goal>.*?)\\s*(?<done>\\u2714)?\\s*$");
    private static final Pattern MISSION_PROGRESS_PATTERN = Pattern.compile("^\\s*\\u2517\\s*\\((?<progress>[\\d,]+)\\s*/\\s*(?<needed>[\\d,]+)\\)\\s*$");
    private static final Pattern MISSION_SECTION_PATTERN = Pattern.compile("^\\s*(?<name>\\S.*?) Missions\\s*$");
    /** Last character of the tooltip frame glyphs that start every lore line. */
    private static final char LORE_FRAME_END = '';

    public final List<Quest> quests = new ArrayList<>();
    private boolean menuOpen = false;
    private boolean initialized = false;

    public static DailyQuestHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new DailyQuestHandler();
        }
        return INSTANCE;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setMenuOpen(boolean open) {
        this.menuOpen = open;
    }

    public void onReceiveMessage(Component message) {
        String plain = message.getString();
        if (plain.startsWith("QUEST Complete [")) {
            updateQuest("Quests Completed");
        } else if (plain.startsWith("DAILY MISSIONS » Contest Participation")) {
            updateQuest("Contest Participation");
        }
    }

    /// Items in a menu arrive a few ticks after it opens, so scan every tick while it's open.
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!menuOpen || !(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.getItem() == Items.END_CRYSTAL && "Daily Missions".equals(stack.getHoverName().getString())) {
                ItemLore lore = stack.get(DataComponents.LORE);
                if (lore == null) continue;
                quests.clear();
                quests.addAll(parseQuests(lore.lines().stream().map(Component::getString).toList()));
                initialized = true;
            }
        }
    }

    private void updateQuest(String goal) {
        Quest match = null;
        for (Quest quest : quests) {
            if (quest.goal.equals(goal)) { match = quest; break; }
            if (match == null && quest.goal.contains(goal)) match = quest;
        }
        if (match == null) return;
        match.progress++;
        if (match.isDone()) quests.remove(match);
    }

    /// Unfinished missions in the Daily Missions tooltip, in order. Reads the two-line layout and
    /// falls back to the old one-line layout ({@code | Goal (progress/needed)}).
    static List<Quest> parseQuests(List<String> lore) {
        List<Quest> out = new ArrayList<>();
        String section = "";
        String goal = null;
        boolean goalDone = false;

        for (String raw : lore) {
            String line = raw.substring(raw.lastIndexOf(LORE_FRAME_END) + 1);

            Matcher name = MISSION_NAME_PATTERN.matcher(line);
            if (name.matches()) {
                goal = name.group("goal");
                goalDone = name.group("done") != null;
                continue;
            }

            Matcher progress = MISSION_PROGRESS_PATTERN.matcher(line);
            if (goal != null && progress.matches()) {
                Quest quest = new Quest(goal, parseCount(progress.group("progress")),
                        parseCount(progress.group("needed")), out.size() + 1, section);
                if (!goalDone && !quest.isDone() && !goal.isBlank()) out.add(quest);
                goal = null;
                continue;
            }

            Matcher header = MISSION_SECTION_PATTERN.matcher(line);
            if (header.matches() && !"Daily".equals(header.group("name"))) {
                section = line.strip();
                continue;
            }

            Optional<Quest> oldFormat = parseQuestLine(line, out.size() + 1);
            if (oldFormat.isPresent() && !oldFormat.get().isDone()) out.add(oldFormat.get());
        }
        return out;
    }

    private static int parseCount(String count) {
        return Integer.parseInt(count.replace(",", ""));
    }

    private static Optional<Quest> parseQuestLine(String line, int slot) {
        if (!line.contains("|") || !line.contains("(") || !line.contains(")")) return Optional.empty();
        Matcher matcher = DAILY_QUEST_PATTERN.matcher(line);
        if (!matcher.find()) return Optional.empty();
        return Optional.of(new Quest(matcher.group("goal").trim(),
                Integer.parseInt(matcher.group("progress")),
                Integer.parseInt(matcher.group("needed")), slot, ""));
    }

    public static class Quest {
        public final String goal;
        public int progress;
        public final int needed;
        public final int slot;
        /** Header the mission sits under in the tooltip ("Base Missions"), empty when unknown. */
        public final String section;

        public Quest(String goal, int progress, int needed, int slot, String section) {
            this.goal = goal;
            this.progress = progress;
            this.needed = needed;
            this.slot = slot;
            this.section = section;
        }

        public boolean isDone() {
            return needed > 0 && progress >= needed;
        }
    }
}
