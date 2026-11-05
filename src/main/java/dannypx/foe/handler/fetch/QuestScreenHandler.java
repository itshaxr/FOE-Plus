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

        ScreenEvents.afterTick(screen).register(screen1 -> {
            this.tick(screen);
        });
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
            List<Component> lines = stack.get(DataComponents.LORE).lines();
            if(lines.size() > 6) {
                String goal = lines.get(3).getSiblings().get(3).getString().toLowerCase(Locale.US).trim();
                int max = Integer.parseInt(lines.get(6).getSiblings().get(5).getString());
                int current = Integer.parseInt(lines.get(6).getSiblings().get(3).getString());

                String location = lines.get(4).getString();

                if(location.contains(BossEventHandler.instance().getLocation().getString().trim())) {
                    return new QuestDataHandler.Quest(goal, max, current);
                }
            }
        }
        return null;
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
