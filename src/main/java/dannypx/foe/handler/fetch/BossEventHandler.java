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
    private String iconsNearTime = "";
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
                    iconsNearTime = iconsBeforeTime(name);
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
                        // The weather icons, then sometimes the time: "⚡ 3:15 PM", or two icons at
                        // once ("○⚡": the moon and a thunderstorm). Every icon is kept, so a storm
                        // (or an oasis, a gold rush) is still seen when the moon is up too.
                        String[] split = splitWeather(weatherObject.get("text").getAsString());
                        int weatherColor = TextColor.parseColor(weatherObject.get("color").getAsString()).getOrThrow().getValue();
                        weather = Component.literal(split[0]).withColor(weatherColor);

                        if(!split[1].isEmpty()) {
                            time = Component.literal(split[1]).withColor(weatherColor);

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
                } else if(name.contains("\uA201\uEEE1\uA208") || looksLikeSpot(name)) {
                    // The spot within the location (Big Bear Dock...). Before, every other boss bar
                    // (even the location bar itself) cleared this, so it flickered or never showed.
                    sawSubLocation[0] = true;
                    subLocationSeenAt = System.currentTimeMillis();
                    if(Objects.equals(prevSubLocationBar, name)) return;
                    prevSubLocationBar = name;
                    // the name, without the server's icons and spacing glyphs, in the colour of its letters
                    String plain = spotText(name);
                    TextColor[] color = {null};
                    lerpingBossEvent.getName().visit((style, part) -> {
                        if (color[0] == null && style.getColor() != null && part.replaceAll("[\\uE000-\\uF8FF\\uA000-\\uA4CF\\s]", "").length() > 0) color[0] = style.getColor();
                        return java.util.Optional.empty();
                    }, net.minecraft.network.chat.Style.EMPTY);
                    subLocation = Component.literal(plain);
                    if (color[0] != null) subLocation.withColor(color[0].getValue());
                }
            }));
        }
        if(!sawSubLocation[0] && System.currentTimeMillis() - subLocationSeenAt > SUB_LOCATION_GRACE_MS) {
            subLocation = Component.empty();
            prevSubLocationBar = null;
        }
    }
    //endregion

    /// Package-private for tests: a boss bar that's only a place's name ("Big Bear Dock"): the spot you're at,
    /// even if the server changes the spacing glyphs around it. Timers, goals and events have numbers or colons.
    static boolean looksLikeSpot(String bar) {
        if (bar == null || bar.contains("\uF039")) return false;
        String plain = spotText(bar);
        if (plain.length() < 3 || plain.length() > 40) return false;
        // timers, goals, money and percentages aren't places
        if (plain.matches(".*[:/%$].*") || plain.matches(".*\\d+\\.?\\d*[KkMm]?\\b.*\\d.*") || Character.isDigit(plain.charAt(0))) return false;
        long letters = plain.chars().filter(Character::isLetter).count();
        if (letters < 3) return false;
        String lower = plain.toLowerCase(java.util.Locale.ROOT);
        for (String word : NOT_SPOTS) if (lower.contains(word)) return false;
        return true;
    }

    /// Package-private for tests: a bar's words without the server's icons and spacing glyphs ("⚓ Big Bear Dock" →
    /// "Big Bear Dock").
    static String spotText(String bar) {
        String t = bar.replaceAll("[\\uE000-\\uF8FF\\uA000-\\uA4CF]", "");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length(); ) {
            int cp = t.codePointAt(i);
            int type = Character.getType(cp);
            boolean keep = Character.isLetterOrDigit(cp) || Character.isSpaceChar(cp) || "'&.-,()#/:%$".indexOf(cp) >= 0;
            if (type == Character.OTHER_SYMBOL || type == Character.SURROGATE || type == Character.PRIVATE_USE) keep = false;
            if (keep) b.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        return b.toString().replaceAll("\\s+", " ").trim();
    }

    /// Every boss bar's text as the server sent it (for /foe bossbar dump).
    public java.util.List<String> dump() {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            var map = ((BossHealthOverlayAccessor) (minecraft.gui.getBossOverlay())).getEvents();
            for (var e : map.values()) {
                String name = e.getName().getString();
                out.add("TEXT: " + name);
                out.add("CODES: " + name.codePoints().mapToObj(c -> c < 0x7F && c >= 0x20 ? String.valueOf((char) c) : String.format("\\u%04X", c))
                        .collect(java.util.stream.Collectors.joining()));
                out.add("JSON: " + TextHelper.componentToJson(e.getName()));
                out.add("spot? " + looksLikeSpot(name) + "  as: " + spotText(name));
                out.add("");
            }
        } catch (RuntimeException ex) {
            out.add("failed: " + ex);
        }
        out.add("location: " + getLocation().getString() + "  spot: " + getSubLocation().getString());
        // the sidebar and the tab list's footer too, in case the spot is shown there now
        try {
            var level = minecraft.level;
            if (level != null) {
                var board = level.getScoreboard();
                var obj = board.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
                if (obj != null) {
                    out.add("");
                    out.add("SIDEBAR: " + obj.getDisplayName().getString());
                    for (var entry : board.listPlayerScores(obj)) {
                        var team = board.getPlayersTeam(entry.owner());
                        Component line = net.minecraft.world.scores.PlayerTeam.formatNameForTeam(team, entry.ownerName());
                        out.add("  " + line.getString());
                    }
                }
            }
            var footer = ((dannypx.foe.mixin.accessor.PlayerTabOverlayAccessor) minecraft.gui.getTabList()).getFooter();
            if (footer != null) out.add("TAB FOOTER: " + footer.getString().replace("\n", " | "));
        } catch (RuntimeException ex) {
            out.add("sidebar failed: " + ex);
        }
        return out;
    }

    private static final String[] NOT_SPOTS = {"contest", "event", "frenzy", "community", "goal", "boost", "oasis", "rush",
            "storm", "moon", "fabled", "cryptid", "crew", "quest", "loading", "teleport"};

    /// The icons just before the time on the location bar ("○⚡" in "... ○⚡ 11:30 PM ..."), read
    /// from the whole bar so it doesn't depend on how the server splits it into parts.
    /// Package-private for tests.
    static String iconsBeforeTime(String bar) {
        if (bar == null) return "";
        java.util.regex.Matcher m = ICONS_BEFORE_TIME.matcher(bar);
        return m.find() ? m.group(1) : "";
    }

    private static final java.util.regex.Pattern ICONS_BEFORE_TIME = java.util.regex.Pattern.compile("([^\\s\\d:]{1,4})\\s*\\d{1,2}:\\d{2}");

    /// Every weather icon showing: the parsed weather plus whatever sits right before the time
    /// (for spotting a thunderstorm, an oasis or a gold rush while the moon is up too).
    public String weatherIcons() {
        return weather.getString() + iconsNearTime;
    }

    /// "⚡ 3:15 PM" → {"⚡", "3:15 PM"}; "○⚡" → {"○⚡", ""}; "☀" → {"☀", ""}: the icons (everything
    /// before the first digit) and the rest. Package-private for tests.
    static String[] splitWeather(String raw) {
        if (raw == null) return new String[]{"", ""};
        int digit = -1;
        for (int i = 0; i < raw.length(); i++) {
            if (Character.isDigit(raw.charAt(i))) { digit = i; break; }
        }
        if (digit < 0) return new String[]{raw.trim(), ""};
        return new String[]{raw.substring(0, digit).trim(), raw.substring(digit).trim()};
    }

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
