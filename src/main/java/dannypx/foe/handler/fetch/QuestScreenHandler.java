package dannypx.foe.handler.fetch;

import dannypx.foe.handler.Handler;
import dannypx.foe.handler.logic.CodeExecuterHandler;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.handler.logic.NotifierHandler;
import dannypx.foe.handler.store.QuestDataHandler;
import dannypx.foe.type.tuple.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class QuestScreenHandler extends Handler {
    private static QuestScreenHandler INSTANCE = new QuestScreenHandler();

    public static QuestScreenHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new QuestScreenHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private boolean isScanned = false;
    //endregion

    //region Methods
    public void init(ContainerScreen screen) {
        isScanned = false;

        ScreenEvents.afterTick(screen).register(dannypx.foe.helper.SafeScreen.tick("QuestScreenHandler:43", screen1 -> {
            this.tick(screen);
        }));
    }

    public void tick(ContainerScreen screen) {
        if(!isScanned
                && screen.getMenu().slots.get(12).getItem().is(ItemTags.SHULKER_BOXES)
        ) {
            isScanned = true;
            this.checkQuests(screen.getMenu());
        }
    }

    public void checkQuests(ChestMenu chestMenu) {
        List<QuestDataHandler.Quest> questList = new ArrayList<>();

        CodeExecuterHandler.runLater(1, () -> {
            chestMenu.slots.forEach(slot -> {
                if (minecraft.player != null
                        && slot.container != minecraft.player.getInventory()
                        && slot.getItem().is(ItemTags.SHULKER_BOXES)
                        && slot.getItem().getItem() != Items.WHITE_SHULKER_BOX
                        && slot.getItem().getHoverName().getString().startsWith("Fishing Quest")
                ) {
                    QuestDataHandler.Quest quest = this.extractQuestData(slot.getItem());

                    if(quest != null) {
                        questList.add(quest);
                    }
                }
            });

            QuestDataHandler.instance().setQuest(questList);
        });
    }

    /// Items of a /quests menu read in the background (SilentMenuHandler): the quests for where
    /// you are now. Kept only when some were found, so a read while the location is still
    /// changing can't wipe the panel.
    public void readItems(List<ItemStack> items) {
        List<QuestDataHandler.Quest> questList = new ArrayList<>();
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) continue;
            if (!stack.is(ItemTags.SHULKER_BOXES) || stack.getItem() == Items.WHITE_SHULKER_BOX) continue;
            if (!stack.getHoverName().getString().startsWith("Fishing Quest")) continue;
            try {
                QuestDataHandler.Quest quest = this.extractQuestData(stack);
                if (quest != null) questList.add(quest);
            } catch (RuntimeException e) {
                // A quest item laid out differently: skip it.
            }
        }
        if (!questList.isEmpty()) QuestDataHandler.instance().setQuest(questList);
    }

    private QuestDataHandler.Quest extractQuestData(ItemStack stack) {
        if(stack.get(DataComponents.LORE) != null) {
            return parseQuest(stack.get(DataComponents.LORE).lines(), BossEventHandler.instance().getLocation().getString().trim());
        }
        return null;
    }

    static final java.util.regex.Pattern PROGRESS = java.util.regex.Pattern.compile("\\((\\d+)/(\\d+)\\)");
    static final java.util.regex.Pattern CATCH = java.util.regex.Pattern.compile("Catch (\\d+)x (.+)");

    /// A quest's goal and progress from its tooltip, if it's for `here`. Read by what the lines say
    /// ("Catch 26x Epic fish", "at Cypress Lake", "ɪɴ ᴘʀᴏɢʀᴇss (24/26)"), not by how they're split up,
    /// which the server changes. Package-private for tests.
    static QuestDataHandler.Quest parseQuest(List<Component> lines, String here) {
        int catchLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            String t = textOf(lines.get(i));
            if (t.startsWith("Catch ")) { catchLine = i; break; }
        }
        if (catchLine < 0) return null;
        // the goal: the coloured part after "26x " ("Epic" of "Epic fish", "Largemouth Bass")
        String goal = null;
        List<dannypx.foe.item.LoreReader.Run> runs = dannypx.foe.item.LoreReader.content(lines.get(catchLine));
        for (int i = 0; i + 1 < runs.size(); i++) {
            if (runs.get(i).text().trim().matches("\\d+x")) { goal = runs.get(i + 1).text(); break; }
        }
        if (goal == null) {
            java.util.regex.Matcher m = CATCH.matcher(textOf(lines.get(catchLine)));
            if (!m.find()) return null;
            goal = m.group(2).replaceAll("(?i) fish$", "");
        }
        goal = goal.toLowerCase(Locale.US).trim();
        // where: the line after it ("at Cypress Lake")
        String where = catchLine + 1 < lines.size() ? textOf(lines.get(catchLine + 1)) : "";
        if (here != null && !here.isEmpty() && !where.contains(here)) return null;
        for (int i = catchLine + 1; i < lines.size(); i++) {
            java.util.regex.Matcher m = PROGRESS.matcher(textOf(lines.get(i)));
            if (m.find()) {
                try {
                    return new QuestDataHandler.Quest(goal, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /// A line's text without the tooltip frame in front.
    static String textOf(Component line) {
        String t = line.getString();
        return t.substring(dannypx.foe.item.LoreReader.frameLength(t)).trim();
    }

    public void checkForCompletedQuests() {
        List<QuestDataHandler.Quest> quests = QuestDataHandler.instance().getQuestData().questList.getOrDefault(BossEventHandler.instance().getLocation().getString(), new ArrayList<>());

        quests.forEach(quest -> {
            if(quest.isDone()) {
                NotifierHandler.instance().notifyQuest(quest);
            }
        });
    }

    //region Dev
    /// Field, Pair<Value, Tooltip>
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
        );
    }
    //endregion
}
