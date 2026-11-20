package dannypx.foe.screens.element.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * HUDs shown inside other HUDs: a host (Timers) shows its own lines and then each HUD put in it, in order, each under
 * a line with its title (as the Community Catches always sat under the timers). A HUD put inside another leaves its
 * own place on screen; split, it goes back there. Only the text HUDs can be combined (not the pet, bait, luck and
 * scale or chummer ones, whose pictures and bars wouldn't carry over). Everyone starts with the Community Catches in
 * the Timers. Saved in config/fishonmcextras/hud_combine.json.
 */
public final class HudCombine {
    private HudCombine() {}

    /// The HUDs that can be combined, by their look id, each made as a live HUD or a preview copy.
    static final Map<String, Function<Boolean, PanelElement>> PARTS = new LinkedHashMap<>();

    static {
        PARTS.put("TimersPanelElement", TimersPanelElement::new);
        PARTS.put("CommunityPanelElement", CommunityPanelElement::new);
        PARTS.put("EventsPanelElement", EventsPanelElement::new);
        PARTS.put("CrewPanelElement", CrewPanelElement::new);
        PARTS.put("ContestPanelElement", ContestPanelElement::new);
        PARTS.put("QuestPanelElement", QuestPanelElement::new);
        PARTS.put("DailyQuestPanelElement", DailyQuestPanelElement::new);
        PARTS.put("StatsPanelElement", StatsPanelElement::new);
        PARTS.put("SessionStatsPanelElement", SessionStatsPanelElement::new);
        PARTS.put("MiniEventPanelElement", MiniEventPanelElement::new);
        PARTS.put("LuckScalePanelElement", LuckScalePanelElement::new);
        PARTS.put("GearWarningPanelElement", GearWarningPanelElement::new);
        PARTS.put("AlertPanelElement", AlertPanelElement::new);
        PARTS.put("ChummerPanelElement", ChummerPanelElement::new);
        PARTS.put("BaitPanelElement", BaitPanelElement::new);
        PARTS.put("PetPanelElement", PetPanelElement::new);
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static java.util.function.Supplier<Path> FILE = () -> net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve(dannypx.foe.FishOnMCExtras.MOD_ID).resolve("hud_combine.json");

    /// host → the HUDs inside it, in order.
    private static Map<String, List<String>> groups;
    private static final Map<String, PanelElement> LIVE = new HashMap<>(), COPIES = new HashMap<>();

    /// Package-private for tests: everyone's start, the Community Catches inside the Timers.
    static Map<String, List<String>> defaults() {
        Map<String, List<String>> d = new LinkedHashMap<>();
        d.put("TimersPanelElement", new ArrayList<>(List.of("CommunityPanelElement")));
        return d;
    }

    private static Map<String, List<String>> groups() {
        if (groups != null) return groups;
        groups = defaults();
        try {
            Path f = FILE.get();
            if (f != null && Files.exists(f)) {
                Map<String, List<String>> read = GSON.fromJson(Files.readString(f), new TypeToken<LinkedHashMap<String, List<String>>>() {}.getType());
                if (read != null) {
                    groups = new LinkedHashMap<>();
                    read.forEach((host, members) -> {
                        if (!PARTS.containsKey(host) || members == null) return;
                        List<String> ok = new ArrayList<>();
                        for (String m : members) if (PARTS.containsKey(m) && !m.equals(host) && !ok.contains(m)) ok.add(m);
                        if (!ok.isEmpty()) groups.put(host, ok);
                    });
                }
            } else {
                // the old "Show community catches" (in the Timers) switched off: so is the Community Catches HUD
                var cfg = dannypx.foe.config.Configs.hudConfig;
                if (cfg != null && !cfg.timersShowCommunity.get()) {
                    cfg.showCommunityPanel.accept(false);
                    cfg.save();
                }
            }
        } catch (Throwable ignored) {
            // the start then
        }
        return groups;
    }

    private static void save() {
        try {
            Path f = FILE.get();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(groups()));
        } catch (Throwable ignored) {
            // kept for this session
        }
    }

    public static boolean combinable(String id) {
        return PARTS.containsKey(id);
    }

    /// The HUDs inside `host`, in order (none when it holds none).
    public static List<String> members(String host) {
        return List.copyOf(groups().getOrDefault(host, List.of()));
    }

    /// The HUD `member` is inside, or null.
    public static String hostOf(String member) {
        for (var e : groups().entrySet()) if (e.getValue().contains(member)) return e.getKey();
        return null;
    }

    /// Whether `member` can go inside `host`: both text HUDs, the member not holding any itself, the host not inside
    /// another.
    public static boolean canCombine(String host, String member) {
        if (!combinable(host) || !combinable(member) || host.equals(member)) return false;
        if (!members(member).isEmpty()) return false;
        if (hostOf(host) != null) return false;
        return !members(host).contains(member);
    }

    /// Puts `member` inside `host`, at the end (out of any other HUD first).
    public static void combine(String host, String member) {
        if (!canCombine(host, member)) return;
        split(member);
        groups().computeIfAbsent(host, k -> new ArrayList<>()).add(member);
        save();
    }

    /// Puts `member` back in its own place.
    public static void split(String member) {
        for (var it = groups().entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue().remove(member) && e.getValue().isEmpty()) it.remove();
        }
        save();
    }

    /// Moves `member` one up (-1) or down (+1) inside its host.
    public static void move(String member, int by) {
        String host = hostOf(member);
        if (host == null) return;
        List<String> list = groups().get(host);
        int i = list.indexOf(member), j = i + by;
        if (j < 0 || j >= list.size()) return;
        list.remove(i);
        list.add(j, member);
        save();
    }

    /// Moves `member` one up, and the first one up to the top: it then holds the others (the one that held them first
    /// under it) and takes that one's place on screen, so the box stays where it was.
    public static void up(String member) {
        String host = hostOf(member);
        if (host == null) return;
        List<String> list = groups().get(host);
        if (list.indexOf(member) > 0) { move(member, -1); return; }
        List<String> rest = new ArrayList<>(list);
        rest.remove(member);
        rest.addFirst(host);
        groups().remove(host);
        groups().put(member, rest);
        copyPlace(host, member);
        save();
    }

    /// Moves `id` one down; the top one (the HUD holding the others) swaps with the first under it.
    public static void down(String id) {
        if (hostOf(id) != null) { move(id, 1); return; }
        List<String> list = groups().get(id);
        if (list != null && !list.isEmpty()) up(list.getFirst());
    }

    /// Puts HUD `to` where HUD `from` is (its spot, corner and size), when both have one in the settings.
    static void copyPlace(String from, String to) {
        try {
            var cfg = dannypx.foe.config.Configs.hudConfig;
            if (cfg == null) return;
            for (String what : new String[]{"XPosition", "YPosition", "Alignment", "Scale"}) {
                Object a = field(cfg, from, what), b = field(cfg, to, what);
                if (!(a instanceof me.fzzyhmstrs.fzzy_config.validation.ValidatedField<?> va)
                        || !(b instanceof me.fzzyhmstrs.fzzy_config.validation.ValidatedField<?> vb)) continue;
                @SuppressWarnings("unchecked")
                var into = (me.fzzyhmstrs.fzzy_config.validation.ValidatedField<Object>) vb;
                Object v = va.get();
                // a corner the other can't take (a centre one) is left as it was
                try { into.accept(v); } catch (RuntimeException ignored) { }
            }
            cfg.save();
        } catch (Throwable ignored) {
            // it keeps its own place
        }
    }

    /// The setting "eventsPanelXPosition" for "EventsPanelElement" and "XPosition", or null.
    private static Object field(Object cfg, String id, String what) {
        if (!id.endsWith("PanelElement")) return null;
        String base = Character.toLowerCase(id.charAt(0)) + id.substring(1, id.length() - "Element".length());
        try {
            return cfg.getClass().getField(base + what).get(cfg);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /// The HUD with this id: the live one, or a preview copy.
    public static PanelElement part(String id, boolean copy) {
        Map<String, PanelElement> cache = copy ? COPIES : LIVE;
        PanelElement p = cache.get(id);
        if (p != null || !PARTS.containsKey(id)) return p;
        // made outside the map: making a HUD builds its lines, which can ask for the HUDs inside it
        p = PARTS.get(id).apply(copy);
        PanelElement had = cache.putIfAbsent(id, p);
        return had != null ? had : p;
    }

    /// A HUD's name ("Events").
    public static String name(String id) {
        PanelElement p = part(id, true);
        if (p == null) return id;
        String n = p.message == null ? id : p.message.getString().trim();
        return n.endsWith(" Panel") ? n.substring(0, n.length() - " Panel".length()) : n;
    }

    /// Whether the HUD `id` shows anywhere: switched on, and (inside another) that one switched on too.
    public static boolean visible(String id) {
        PanelElement p = part(id, false);
        if (p == null || !p.enabled()) return false;
        String host = hostOf(id);
        return host == null || shownInHost(id);
    }

    /// Whether `id` is inside a host that is showing, so it doesn't show in its own place.
    public static boolean shownInHost(String id) {
        String host = hostOf(id);
        if (host == null) return false;
        PanelElement h = part(host, false);
        return h != null && h.enabled();
    }
}
