package dannypx.foe.handler.fetch;

import dannypx.foe.handler.Handler;
import dannypx.foe.handler.logic.*;
import dannypx.foe.handler.store.ConstantDataHandler;
import dannypx.foe.handler.store.CustomChatTriggerDataHandler;
import dannypx.foe.handler.store.CustomTrackerDataHandler;
import dannypx.foe.handler.store.ProfileDataHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.type.placeholder.PlaceholderValue;
import dannypx.foe.type.placeholder.ComponentValue;
import dannypx.foe.type.tuple.Pair;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class ChatHandler extends Handler {
    private static ChatHandler INSTANCE = new ChatHandler();

    public static ChatHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new ChatHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private Map<String, Component> storedChatTriggerComponent = new HashMap<>();

    public Map<String, Component> getStoredChatTriggerComponent() {
        return Collections.unmodifiableMap(storedChatTriggerComponent);
    }

    final List<String> blacklistedMessageFilters = List.of(
            "REACTIONS »"
    );
    //endregion

    //region Methods
    public void init() {
        if(storedChatTriggerComponent.isEmpty()) {
            this.initChatTrigger();
        }
    }

    public void initChatTrigger() {
        storedChatTriggerComponent.clear();
        CustomChatTriggerDataHandler.instance().getCustomChatTriggerData().chatTriggerList.forEach((name, trigger) -> {
            storedChatTriggerComponent.put(name, Component.empty());
        });
    }

    public void onReceiveMessage(Component component) {
        // Auto Tip needs REACTIONS lines, which the blacklist below skips.
        dannypx.foe.handler.logic.AutotipHandler.instance().onReceiveMessage(component);
        if(this.inBlackList(component)) return;
        
        this.checkPet(component);
        this.checkQuest(component);
        // FOE-style info panels
        dannypx.foe.handler.logic.ContestTrackerHandler.instance().onReceiveMessage(component);
        dannypx.foe.handler.logic.DailyQuestHandler.instance().onReceiveMessage(component);
        dannypx.foe.handler.logic.MoonTimerHandler.instance().onReceiveMessage(component);
        dannypx.foe.handler.logic.AlertHandler.instance().onReceiveMessage(component);
        if (component.getString().startsWith("QUEST Complete")) {
            dannypx.foe.handler.store.StatsDataHandler.instance().addQuestCompleted();
        }
        this.checkChatTrigger(component);
    }

    private boolean inBlackList(Component component) {
        return blacklistedMessageFilters.stream().anyMatch(filter -> component.getString().startsWith(filter));
    }

    private void checkPet(Component component) {
        if(component.getString().startsWith("PETS » Equipped your")) {
            ProfileDataHandler.instance().updatePet(true);
            EventHandler.instance().onPetEquip();
            // Your pet changes Luck & Scale: read /menu again in a few seconds.
            dannypx.foe.handler.logic.SilentMenuHandler.instance().requestForPetChange();
        } else if (component.getString().startsWith("PETS » Pet unequipped!")) {
            ProfileDataHandler.instance().updatePet(false);
            EventHandler.instance().onPetUnequip();
            dannypx.foe.handler.logic.SilentMenuHandler.instance().requestForPetChange();
        } else if(component.getString().startsWith("CREWS » Crew Chat has been enabled")) {
            ProfileDataHandler.instance().updateCrewChat(true);
        } else if(component.getString().startsWith("CREWS » Crew Chat has been disabled")) {
            ProfileDataHandler.instance().updateCrewChat(false);
        } else if(component.getString().startsWith("TOURNAMENT You have ENABLED tournament contributions")) {
            ProfileDataHandler.instance().updateTournamentContribution(true);
        } else if(component.getString().startsWith("TOURNAMENT You have DISABLED tournament contributions")) {
            ProfileDataHandler.instance().updateTournamentContribution(false);
        }
    }

    private void checkQuest(Component component) {
        if(component.getString().startsWith("QUEST Complete")) {
            QuestHandler.instance().initScan();
        }
    }

    private void checkChatTrigger(Component component) {
        CustomChatTriggerDataHandler.instance().getCustomChatTriggerData().chatTriggerList.forEach((name, trigger) -> {
            if(!trigger.getRegex().isBlank()
                    && trigger.getPattern().matcher(component.getString()).matches()
            ) {
                storedChatTriggerComponent.put(name, component);
                if(trigger.getNotificationToTrigger() != null
                        && !trigger.getNotificationToTrigger().isBlank()
                        && trigger.isUseChatTrigger()
                ) {
                    CodeExecuterHandler.runLater(1, () -> {
                        String[] notificationIds = trigger.getNotificationToTrigger().split(",");
                        NotifierHandler.instance().notifyOnTrigger(notificationIds);
                    });
                }

                if(trigger.getChatNotificationToTrigger() != null
                        && !trigger.getChatNotificationToTrigger().isBlank()
                        && trigger.isUseChatTrigger()
                ) {
                    CodeExecuterHandler.runLater(1, () -> {
                        String [] chatNotificationIds = trigger.getChatNotificationToTrigger().split(",");
                        ChatNotifierHandler.instance().notifyChatOnTrigger(chatNotificationIds);
                    });
                }

                if(trigger.getTrackerToTrigger() != null
                        && !trigger.getTrackerToTrigger().isBlank()
                        && trigger.isUseChatTrigger()
                ) {
                    CodeExecuterHandler.runLater(1, () -> {
                        String[] trackerIds = trigger.getTrackerToTrigger().split(",");
                        CustomTrackerDataHandler.instance().updateTracker(trackerIds);
                    });
                }
            }
        });
    }

    public String onModifyChatMessage(String text) {
        if(ConnectionHandler.instance().isOnServer()) {
            AtomicReference<String> modified = new AtomicReference<>(text);
            ConstantDataHandler.instance().getConstantData().fishData.forEach((category, fieldMap) -> {
                fieldMap.forEach((stringField, textField) -> {
                    if(modified.get().contains(textField.getString().trim())) {
                        modified.set(modified.get().replace(textField.getString().trim(), TextHelper.capitalize(stringField)));
                    }
                });
            });

            modified.set(modified.get().replace("FoER » ", "").replace("[FOE+] ", ""));

            return modified.get();
        }
        return text;
    }

    public void cleanChatTriggerStore(String[] chatTriggers) {
        for (String chatTrigger : chatTriggers) {
            if(storedChatTriggerComponent.containsKey(chatTrigger.trim())) {
                CodeExecuterHandler.runLater(2, () -> {
                    storedChatTriggerComponent.put(chatTrigger.trim(), Component.empty());
                });
            }
        }
    }
    //endregion

    //region Dev
    /// Field, Pair<Value, Tooltip>
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
        );
    }
    //endregion
}
