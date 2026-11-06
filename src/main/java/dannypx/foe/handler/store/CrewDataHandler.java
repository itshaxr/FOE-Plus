package dannypx.foe.handler.store;

import dannypx.foe.handler.Handler;
import dannypx.foe.handler.io.DataFileHandler;
import dannypx.foe.handler.io.DataModels;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.type.tuple.Pair;
import java.util.*;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

public class CrewDataHandler extends Handler {
    private static CrewDataHandler INSTANCE = new CrewDataHandler();

    public static CrewDataHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new CrewDataHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private CrewDataModel crewData = new CrewDataModel();
    private boolean needsUpdate = false;

    public CrewDataModel getCrewData() {
        return crewData;
    }
    //endregion

    //region Methods
    public void tick() {
        if(crewData.uuid == null && minecraft.player != null) {
            crewData.uuid = minecraft.player.getUUID();
        } else if(crewData.uuid != null && this.needsUpdate) {
            this.updateCrewData();
        } else if(!CrewDataModel.CREW_DATA_MODEL_VERSION.equals(crewData.version)) {
            crewData.version = CrewDataModel.CREW_DATA_MODEL_VERSION;
            needsUpdate = true;
        }
    }

    public void init() {
        if(minecraft.player != null) this.setUUID(minecraft.player.getUUID());
    }

    private void setUUID(UUID uuid) {
        this.crewData.uuid = uuid;
    }

    /// Members saved under a made-up id (their /crew head had no id) get their real one once
    /// they're in the tab list. True when something changed.
    public boolean swapInRealIds(net.minecraft.client.multiplayer.ClientPacketListener connection) {
        if (connection == null) return false;
        Map<UUID, Pair<String, ItemStack>> fixed = null;
        for (var e : crewData.crewList.entrySet()) {
            String name = e.getValue().value1();
            if (name == null || name.isBlank() || connection.getPlayerInfo(e.getKey()) != null) continue;
            var info = connection.getPlayerInfo(name);
            if (info == null || info.getProfile().id().equals(e.getKey())) continue;
            if (fixed == null) fixed = new java.util.LinkedHashMap<>(crewData.crewList);
            fixed.remove(e.getKey());
            fixed.put(info.getProfile().id(), e.getValue());
        }
        if (fixed == null) return false;
        updateCrewList(fixed);
        return true;
    }

    public void updateCrewList(Map<UUID, Pair<String, ItemStack>> crewList) {
        this.crewData.crewList = crewList;
        this.needsUpdate = true;
    }

    public void setCrewData(CrewDataModel crewData) {
        this.crewData = crewData;
        this.updateCrewData();
    }

    private void updateCrewData() {
        if(needsUpdate) {
            DataFileHandler.instance().saveToFile(DataModels.DataModelType.CREW_DATA);
        }
        this.needsUpdate = false;
    }
    //endregion

    //region Model
    public static class CrewDataModel extends DataModels.DataModel {
        private static final String CREW_DATA_MODEL_VERSION = "0.2";

        public Map<UUID, Pair<String, ItemStack>> crewList = new HashMap<>();

        public CrewDataModel() {
            super(CREW_DATA_MODEL_VERSION, null);
        }
    }
    //endregion

    //region Dev
    /// Field, Pair<Value, Tooltip>
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
                "crewData", Pair.of(Component.literal("[crewData]"), TextHelper.literal(getCrewData()))
        );
    }
    //endregion
}
