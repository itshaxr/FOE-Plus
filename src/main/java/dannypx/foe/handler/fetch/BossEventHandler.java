package dannypx.foe.handler.fetch;

import com.google.gson.*;
import dannypx.foe.handler.Handler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.mixin.accessor.BossHealthOverlayAccessor;
import java.util.*;

import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

public class BossEventHandler extends Handler {
    private static BossEventHandler INSTANCE = new BossEventHandler();

    public static BossEventHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new BossEventHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private MutableComponent location = Component.empty();
    private MutableComponent weather = Component.empty();
    private MutableComponent time = Component.empty();
    private MutableComponent temperature = Component.empty();
    private MutableComponent subLocation = Component.empty();
    private String prevSubLocationBar = null;
    /// The server drops or rebuilds the spot's boss bar for a moment now and then: the spot is
    /// only cleared once its bar has been gone this long, so it doesn't blink.
    private static final long SUB_LOCATION_GRACE_MS = 3_000L;
    private long subLocationSeenAt = 0L;
    private MutableComponent communityGoalCurrent = Component.empty();
    private MutableComponent communityGoalMax = Component.empty();
    private String prevBossEvent = "";

    public MutableComponent getLocation() {
        return location;
    }

    public MutableComponent getWeather() {
        return weather;
    }

    public MutableComponent getTime() {
        return time;
    }

    public MutableComponent getTemperature() {
        return temperature;
    }

    public MutableComponent getSubLocation() {
        return subLocation;
    }

    public MutableComponent getCommunityGoalCurrent() {
        return communityGoalCurrent;
    }

    public MutableComponent getCommunityGoalMax() {
        return communityGoalMax;
    }
    //endregion

    //region Methods
    public void tick() {
        this.fetchFromBossBar();
    }

    private void fetchFromBossBar() {
        Map<UUID, LerpingBossEvent> bossEventMap = ((BossHealthOverlayAccessor) (minecraft.gui.getBossOverlay())).getEvents();
        boolean[] sawSubLocation = {false};
        if(!bossEventMap.isEmpty()) {
            bossEventMap.forEach(((uuid, lerpingBossEvent) -> {
                String name = lerpingBossEvent.getName().getString();
                if(name.contains("\uF039")) {
                    if(Objects.equals(prevBossEvent, name)) return;
                    prevBossEvent = lerpingBossEvent.getName().getString();
                    String json = TextHelper.componentToJson(lerpingBossEvent.getName());
                    JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();

                    if(jsonObject.get("extra") != null) {
                        JsonObject locationObject = jsonObject.get("extra").getAsJsonArray().get(0).getAsJsonObject()
                                .get("extra").getAsJsonArray().get(0).getAsJsonObject();
                        String locationString = locationObject.get("text").getAsString().substring(4).trim();
                        if(locationString.contains("(")) {
                            locationString = locationString.substring(0, locationString.indexOf("(") - 1);
                        }

                        location = Component.literal(locationString)
                                .withColor(TextColor.parseColor(locationObject.get("color").getAsString()).getOrThrow().getValue());

                        JsonObject weatherObject = jsonObject.get("extra").getAsJsonArray().get(2).getAsJsonObject()
                                .get("extra").getAsJsonArray().get(0).getAsJsonObject();
                        weather = Component.literal(weatherObject.get("text").getAsString())
                                .withColor(TextColor.parseColor(weatherObject.get("color").getAsString()).getOrThrow().getValue());

                        if(weather.getString().trim().length() != 1) {
                            weather = Component.literal(weatherObject.get("text").getAsString().substring(0, 1))
                                    .withColor(TextColor.parseColor(weatherObject.get("color").getAsString()).getOrThrow().getValue());

                            time = Component.literal(weatherObject.get("text").getAsString().substring(1).trim())
                                    .withColor(TextColor.parseColor(weatherObject.get("color").getAsString()).getOrThrow().getValue());

                            if(weatherObject.get("extra") != null) {
                                JsonObject temperatureObject = weatherObject.get("extra").getAsJsonArray().get(0).getAsJsonObject();
                                temperature = Component.literal(temperatureObject.get("text").getAsString().trim())
                                        .withColor(TextColor.parseColor(temperatureObject.get("color").getAsString()).getOrThrow().getValue());
                            }
                            return;
                        }

                        if(weatherObject.get("extra") != null) {
                            JsonObject timeObject = weatherObject.get("extra").getAsJsonArray().get(0).getAsJsonObject();
                            time = Component.literal(timeObject.get("text").getAsString().trim())
                                    .withColor(TextColor.parseColor(timeObject.get("color").getAsString()).getOrThrow().getValue());

                            if(timeObject.get("extra") != null) {
                                JsonObject temperatureObject = timeObject.get("extra").getAsJsonArray().get(0).getAsJsonObject();
                                temperature = Component.literal(temperatureObject.get("text").getAsString().trim())
                                        .withColor(TextColor.parseColor(temperatureObject.get("color").getAsString()).getOrThrow().getValue());
                            }
                        }

                        if(jsonObject.get("extra").getAsJsonArray().size() > 4 && lerpingBossEvent.getName().getString().contains("Community Goal:")) {
                            JsonObject communityGoalObject = jsonObject.get("extra").getAsJsonArray().get(4).getAsJsonObject()
                                    .get("extra").getAsJsonArray().get(0).getAsJsonObject().get("extra").getAsJsonArray().get(0).getAsJsonObject();

                            communityGoalCurrent = Component.literal(communityGoalObject.get("text").getAsString().trim())
                                    .withColor(TextColor.parseColor(communityGoalObject.get("color").getAsString()).getOrThrow().getValue());

                            JsonObject communityGoalMaxObject = communityGoalObject.get("extra").getAsJsonArray().get(0).getAsJsonObject()
                                    .get("extra").getAsJsonArray().get(0).getAsJsonObject();

                            communityGoalMax = Component.literal(communityGoalMaxObject.get("text").getAsString().trim())
                                    .withColor(TextColor.parseColor(communityGoalMaxObject.get("color").getAsString()).getOrThrow().getValue());
                        }
                    }
                } else if(name.contains("\uA201\uEEE1\uA208")) {
                    // The spot within the location (Big Bear Dock...). Before, every other boss bar
                    // (even the location bar itself) cleared this, so it flickered or never showed.
                    sawSubLocation[0] = true;
                    subLocationSeenAt = System.currentTimeMillis();
                    if(Objects.equals(prevSubLocationBar, name)) return;
                    prevSubLocationBar = name;
                    JsonObject jsonObject = JsonParser.parseString(TextHelper.componentToJson(lerpingBossEvent.getName())).getAsJsonObject();

                    if(jsonObject.get("extra") != null) {
                        JsonObject locationObject = jsonObject.get("extra").getAsJsonArray().get(0).getAsJsonObject();
                        subLocation = Component.literal(locationObject.get("text").getAsString())
                                .withColor(TextColor.parseColor(locationObject.get("color").getAsString()).getOrThrow().getValue());
                    }
                }
            }));
        }
        if(!sawSubLocation[0] && System.currentTimeMillis() - subLocationSeenAt > SUB_LOCATION_GRACE_MS) {
            subLocation = Component.empty();
            prevSubLocationBar = null;
        }
    }
    //endregion

    //region Dev
    /// Field, Pair<Value, Tooltip>
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
                "location", Pair.of(getLocation(), Component.empty()),
                "weather", Pair.of(getWeather(), Component.empty()),
                "time", Pair.of(getTime(), Component.empty()),
                "temperature", Pair.of(getTemperature(), Component.empty()),
                "subLocation", Pair.of(getSubLocation(), Component.empty())
        );
    }
    //endregion
}
