package dannypx.foe.handler.logic;

import dannypx.foe.handler.Handler;
import dannypx.foe.handler.fetch.ScoreboardHandler;
import dannypx.foe.handler.store.CrewDataHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.type.placeholder.PlaceholderValue;
import dannypx.foe.type.placeholder.StringValue;
import dannypx.foe.type.placeholder.ComponentValue;
import dannypx.foe.type.tuple.Pair;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public class CrewHandler extends Handler {
    private static CrewHandler INSTANCE = new CrewHandler();

    public static CrewHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new CrewHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private Map<UUID, Long> pendingLeavesList = new HashMap<>();

    List<Pair<UUID, String>> crewListOrdered = new ArrayList<>();
    List<Pair<UUID, String>> onlineMembers = new ArrayList<>();
    List<Pair<UUID, String>> offlineMembers = new ArrayList<>();

    boolean isCrewNearby = false;

    int leaveDelay = 20;

    public List<Pair<UUID, String>> getCrewListOrdered() {
        return crewListOrdered;
    }

    public List<Pair<UUID, String>> getOnlineMembers() {
        return onlineMembers;
    }

    public List<Pair<UUID, String>> getOfflineMembers() {
        return offlineMembers;
    }

    public boolean isCrewNearby() {
        return isCrewNearby;
    }
    //endregion

    //region Method
    @Override
    public void init() {
        onlineMembers.clear();
        offlineMembers.clear();
        pendingLeavesList.clear();
        crewListOrdered.clear();
    }

    public void tick() {
        if(!ScoreboardHandler.instance().getCrew().getString().isBlank()) {
            if(crewListOrdered.isEmpty()) this.updateCrewOrderedList(CrewDataHandler.instance().getCrewData().crewList);
        }

        if(minecraft.player != null
                && minecraft.level != null
        ) {
            this.checkCrewNearby();
        }

        pendingLeavesList.forEach(((uuid, time) -> {
            if(System.currentTimeMillis() > time + (leaveDelay * 50L) + 1000L) CodeExecuterHandler.runLater(1, () -> pendingLeavesList.remove(uuid));
        }));
    }

    private void checkCrewNearby() {
        AABB searchBox = minecraft.player.getBoundingBox().inflate(10d);
        ClientLevel world = minecraft.level;

        List<Player> playerEntities = world.getEntitiesOfClass(
                Player.class,
                searchBox,
                playerEntity -> {
                    if(playerEntity.getUUID().equals(minecraft.player.getUUID())) return false;

                    return CrewDataHandler.instance().getCrewData().crewList.containsKey(playerEntity.getUUID())
                            && playerEntity.position().distanceTo(minecraft.player.position()) < 10d;
                }
        );

        this.isCrewNearby = !playerEntities.isEmpty();
    }

    public void updateCrewOrderedList(Map<UUID, Pair<String, ItemStack>> crewMap) {
        this.crewListOrdered.clear();
        crewMap.forEach(((uuid, s) -> this.crewListOrdered.add(Pair.of(uuid, s.value1()))));


        this.fetchCrewMemberStatus();
    }

    private void fetchCrewMemberStatus() {
        onlineMembers.clear();
        offlineMembers.clear();
        crewListOrdered.forEach(crew -> {
            PlayerInfo playerInfo = minecraft.getConnection().getPlayerInfo(crew.value1());
            if(playerInfo != null) {
                onlineMembers.add(crew);
            } else {
                offlineMembers.add(crew);
            }
        });
    }

    public void updatePlayerToOffline(UUID uuid) {
        AtomicReference<Pair<UUID, String>> updatedMember = new AtomicReference<>();

        onlineMembers.removeIf(crew -> {
            if(crew.value1().equals(uuid)) {
                updatedMember.set(crew);
                return true;
            }
            return false;
        });

        if(updatedMember.get() != null) {
            Pair<String, ItemStack> crewMember = CrewDataHandler.instance().getCrewData().crewList.get(updatedMember.get().value1());

            LoggerHandler._debug("player " + crewMember.value1() + " left");

            offlineMembers.add(updatedMember.get());
            NotifierHandler.instance().notifyPlayerStatus(false, crewMember);
            crewChat(crewMember.value1(), false);
            EventHandler.instance().onCrewLeave();
        }
    }

    public void updatePlayerToOnline(UUID uuid) {
        AtomicReference<Pair<UUID, String>> updatedMember = new AtomicReference<>();

        offlineMembers.removeIf(crew -> {
            if(crew.value1().equals(uuid)) {
                updatedMember.set(crew);
                return true;
            }
            return false;
        });

        if(updatedMember.get() != null) {
            Pair<String, ItemStack> crewMember = CrewDataHandler.instance().getCrewData().crewList.get(updatedMember.get().value1());

            LoggerHandler._debug("player " + crewMember.value1() + " joined");

            onlineMembers.add(updatedMember.get());
            NotifierHandler.instance().notifyPlayerStatus(true, crewMember);
            crewChat(crewMember.value1(), true);
            EventHandler.instance().onCrewJoin();
        }
    }

    /// [FOE+] CREW Ewiksen is online / went offline (FOE-R's notification box is hidden in FOE+).
    private static void crewChat(String name, boolean online) {
        if (!dannypx.foe.config.Configs.hudConfig.crewStatusChat.get()) return;
        dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.line("CREW", dannypx.foe.helper.FoeChat.GOLD)
                .append(dannypx.foe.helper.FoeChat.white(name))
                .append(dannypx.foe.helper.FoeChat.text(online ? " is online" : " went offline")));
    }

    public void onPlayerJoin(UUID uuid) {
        if(LoadingHandler.instance().isLoadingDone()
                && !ScoreboardHandler.instance().getCrew().getString().isBlank()
                && CrewDataHandler.instance().getCrewData().crewList.containsKey(uuid)
        ) {
            pendingLeavesList.remove(uuid);

            if(onlineMembers.stream().noneMatch(m -> m.value1().equals(uuid))) {
                updatePlayerToOnline(uuid);
            }
        }
    }

    public void onPlayerLeave(UUID uuid) {
        if(LoadingHandler.instance().isLoadingDone()
                && !ScoreboardHandler.instance().getCrew().getString().isBlank()
                && CrewDataHandler.instance().getCrewData().crewList.containsKey(uuid)
        ) {
            this.pendingLeavesList.put(uuid, System.currentTimeMillis());

            // Delay leaves in case of proxy change
            CodeExecuterHandler.runLater(leaveDelay, () -> {
                if(this.pendingLeavesList.containsKey(uuid)) {
                    this.pendingLeavesList.remove(uuid);
                    updatePlayerToOffline(uuid);
                }
            });
        }
    }
    //endregion

    //region Dev
    /// Field, Pair<Value, Tooltip>
    @Override
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
                "key", Pair.of(Component.literal("value"), Component.empty())
        );
    }
    //endregion
}
