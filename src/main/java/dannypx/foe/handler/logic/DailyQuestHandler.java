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

import dannypx.foe.helper.FoeLocations;
import dannypx.foe.item.FishTagObject;
import dannypx.foe.item.TagObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
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
            requestRefresh();
        } else if (plain.startsWith("DAILY MISSIONS")) {
            if (plain.startsWith("DAILY MISSIONS » Contest Participation")) updateQuest("Contest Participation");
            requestRefresh();
        }
    }

    /// Items in a menu arrive a few ticks after it opens, so scan every tick while it's open.
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        this.tickAutoRefresh(mc);
        if (!menuOpen || !(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (!(slot.container instanceof Inventory)) items.add(slot.getItem());
        }
        this.readMenu(items);
    }

    /// Reads the Daily Missions item among a menu's items (the open main menu, or a /menu read
    /// in the background by {@link SilentMenuHandler}). The server's numbers replace ours.
    public void readMenu(List<ItemStack> items) {
        for (ItemStack stack : items) {
            if (stack == null || stack.getItem() != Items.END_CRYSTAL || !"Daily Missions".equals(stack.getHoverName().getString())) continue;
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null) continue;
            quests.clear();
            quests.addAll(parseQuests(lore.lines().stream().map(Component::getString).toList()));
            initialized = true;
            return;
        }
    }

    //region Background refresh
    /// After a catch the server needs a moment to count it.
    private static final long AFTER_CATCH_DELAY_MS = 1_500L;
    private boolean requestedOnJoin = false;

    private void tickAutoRefresh(Minecraft mc) {
        if (mc.player == null) {
            requestedOnJoin = false;
            return;
        }
        // Load the panel on joining, without having to open /menu first.
        if (!requestedOnJoin && autoRefreshOn() && dannypx.foe.handler.fetch.TabOverlayHandler.instance().isInInstance()) {
            requestedOnJoin = true;
            SilentMenuHandler.instance().request(5_000L);
        }
    }

    private static boolean autoRefreshOn() {
        return dannypx.foe.config.Configs.hudConfig.showDailyQuestPanel.get()
                && dannypx.foe.config.Configs.hudConfig.dailyQuestAutoRefresh.get();
    }

    private static void requestRefresh() {
        if (autoRefreshOn()) SilentMenuHandler.instance().request(AFTER_CATCH_DELAY_MS);
    }
    //endregion

    //region Per-catch progress
    /// What a catch was, for matching it against the mission goals.
    public record Catch(String fishName, String group, String rarity, String variant, String size, FoeLocations.Location here) {}

    private static final Map<String, String> RARITY_GLYPHS = Map.of(
            "common", "\uF033", "rare", "\uF034", "epic", "\uF035", "legendary", "\uF036", "mythical", "\uF037");
    private static final Map<String, String> VARIANT_GLYPHS = Map.of(
            "albino", "\uF041", "melanistic", "\uF042", "trophy", "\uF043", "fabled", "\uF044");
    private static final List<String> SIZES = List.of("baby", "juvenile", "adult", "large", "gigantic");
    /// Goals with these words are not "catch a fish" goals (they have their own triggers or none).
    private static final Pattern NOT_A_CATCH_GOAL = Pattern.compile(
            "\\b(quests?|contests?|shards?|sell|sold|earn|spend|pets?|baits?|lures?|crates?|levels?|money|coins?)\\b|\\$");
    private static final Pattern GENERIC_CATCH = Pattern.compile("\\b(anywhere|any|fish|fishes)\\b");

    /// Called for every fish you catch: every unfinished mission it counts for goes up by one,
    /// straight away, so the panel never waits for the main menu to be reopened.
    public void onFishCaught(FishTagObject fish) {
        // Exact numbers from the server shortly after; the guess below shows up instantly.
        requestRefresh();
        if (quests.isEmpty()) return;
        String rarity = fish.getRarity().toLowerCase(Locale.ROOT);
        if (rarity.equals("mythic")) rarity = "mythical";
        Catch caught = new Catch(fish.getName().getString(), fish.getString("group"), rarity,
                fish.getVariant().toLowerCase(Locale.ROOT), fish.getFishSize().toLowerCase(Locale.ROOT),
                PetLocationHandler.currentLocation());
        applyCatch(quests, caught);
    }

    /// Called for every other item you catch; shards count for the shard missions.
    public void onItemCaught(TagObject item, int count) {
        if (quests.isEmpty() || !item.getType().toLowerCase(Locale.ROOT).contains("shard")) return;
        requestRefresh();
        FoeLocations.Location here = PetLocationHandler.currentLocation();
        quests.removeIf(quest -> {
            String goal = quest.goal.toLowerCase(Locale.ROOT);
            if (!goal.contains("shard") || !sectionAllows(quest.section, here)) return false;
            quest.progress += Math.max(1, count);
            return quest.isDone();
        });
    }

    /// Adds the catch to every quest it counts for and drops the ones it finishes. Package-private for tests.
    static void applyCatch(List<Quest> quests, Catch caught) {
        quests.removeIf(quest -> {
            if (!countsCatch(quest.goal, quest.section, caught)) return false;
            quest.progress++;
            return quest.isDone();
        });
    }

    /// Whether a mission goal (under its section header) counts this catch. Package-private for tests.
    static boolean countsCatch(String goal, String section, Catch c) {
        if (goal == null || goal.isBlank()) return false;
        String g = goal.toLowerCase(Locale.ROOT);
        if (NOT_A_CATCH_GOAL.matcher(g).find()) return false;

        // Location: a location section ("Danube River Missions") or a location named in the goal
        // only counts there.
        if (!sectionAllows(section, c.here())) return false;
        FoeLocations.Location goalLocation = locationNamedIn(g);
        if (goalLocation != null && goalLocation != c.here()) return false;

        // Rarity / variant / size named in the goal (as a server glyph or a word) must match.
        boolean constrained = goalLocation != null;
        for (Map.Entry<String, String> rarity : RARITY_GLYPHS.entrySet()) {
            boolean named = goal.contains(rarity.getValue()) || hasWord(g, rarity.getKey())
                    || (rarity.getKey().equals("mythical") && hasWord(g, "mythic"));
            if (named) {
                if (!rarity.getKey().equals(c.rarity())) return false;
                constrained = true;
            }
        }
        for (Map.Entry<String, String> variant : VARIANT_GLYPHS.entrySet()) {
            if (goal.contains(variant.getValue()) || hasWord(g, variant.getKey())) {
                if (!variant.getKey().equals(c.variant())) return false;
                constrained = true;
            }
        }
        for (String size : SIZES) {
            if (hasWord(g, size)) {
                if (!size.equals(c.size())) return false;
                constrained = true;
            }
        }

        // A specific fish or fish family ("Rainbow Trout", "Perches").
        String name = c.fishName() == null ? "" : c.fishName().toLowerCase(Locale.ROOT).trim();
        if (!name.isEmpty() && g.contains(name)) return true;
        String group = c.group() == null ? "" : c.group().toLowerCase(Locale.ROOT).trim();
        if (!group.isEmpty() && (hasWord(g, group) || hasWord(g, plural(group)))) return true;

        // Otherwise only generic goals ("Catch 50 Fish", "Anywhere") or ones that only set
        // conditions we checked above ("Catch 3 <mythical glyph>").
        return GENERIC_CATCH.matcher(g).find() || (constrained && !mentionsOtherWords(g));
    }

    private static boolean sectionAllows(String section, FoeLocations.Location here) {
        if (section == null || section.isBlank()) return true;
        FoeLocations.Location sectionLocation = locationNamedIn(section.toLowerCase(Locale.ROOT));
        return sectionLocation == null || sectionLocation == here;
    }

    /// Location names as players write them, matched as whole words.
    private static final Map<FoeLocations.Location, List<String>> LOCATION_WORDS = Map.ofEntries(
            Map.entry(FoeLocations.Location.CYPRESS_LAKE, List.of("cypress lake", "cypress")),
            Map.entry(FoeLocations.Location.KENAI_RIVER, List.of("kenai")),
            Map.entry(FoeLocations.Location.LAKE_BIWA, List.of("biwa")),
            Map.entry(FoeLocations.Location.MURRAY_RIVER, List.of("murray")),
            Map.entry(FoeLocations.Location.EVERGLADES, List.of("everglades")),
            Map.entry(FoeLocations.Location.KEY_WEST, List.of("key west")),
            Map.entry(FoeLocations.Location.TOLEDO_BEND, List.of("toledo bend", "toledo")),
            Map.entry(FoeLocations.Location.GREAT_LAKES, List.of("great lakes")),
            Map.entry(FoeLocations.Location.DANUBE_RIVER, List.of("danube")),
            Map.entry(FoeLocations.Location.OIL_RIG, List.of("oil rig")),
            Map.entry(FoeLocations.Location.AMAZON_RIVER, List.of("amazon")),
            Map.entry(FoeLocations.Location.MEDITERRANEAN_SEA, List.of("mediterranean sea")),
            Map.entry(FoeLocations.Location.CAPE_COD, List.of("cape cod")),
            Map.entry(FoeLocations.Location.HAWAII, List.of("hawaii")),
            Map.entry(FoeLocations.Location.CAIRNS, List.of("cairns")),
            Map.entry(FoeLocations.Location.LOFOTEN_ISLANDS, List.of("lofoten")),
            Map.entry(FoeLocations.Location.NILE_RIVER, List.of("nile")));

    private static FoeLocations.Location locationNamedIn(String lower) {
        for (Map.Entry<FoeLocations.Location, List<String>> entry : LOCATION_WORDS.entrySet()) {
            for (String word : entry.getValue()) {
                if (hasWord(lower, word)) return entry.getKey();
            }
        }
        return null;
    }

    /// After removing numbers, glyphs and filler words, is anything left (like a fish name we
    /// didn't match)? Then the goal is about something else.
    private static boolean mentionsOtherWords(String lower) {
        String rest = lower.replaceAll("[\\uE000-\\uF8FF]", " ")
                .replaceAll("\\b(catch|caught|reel|in|a|an|the|of|at|on|x|common|rare|epic|legendary|mythical|mythic|albino|melanistic|trophy|fabled|baby|juvenile|adult|large|gigantic)\\b", " ")
                .replaceAll("[^a-z]", " ").trim();
        for (List<String> words : LOCATION_WORDS.values()) {
            for (String word : words) rest = rest.replace(word, " ");
        }
        return !rest.isBlank();
    }

    private static boolean hasWord(String text, String word) {
        return Pattern.compile("(?<![a-z])" + Pattern.quote(word) + "(?![a-z])").matcher(text).find();
    }

    private static String plural(String word) {
        if (word.endsWith("s") || word.endsWith("x") || word.endsWith("ch") || word.endsWith("sh")) return word + "es";
        if (word.endsWith("y") && word.length() > 2 && "aeiou".indexOf(word.charAt(word.length() - 2)) < 0) {
            return word.substring(0, word.length() - 1) + "ies";
        }
        return word + "s";
    }
    //endregion

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
