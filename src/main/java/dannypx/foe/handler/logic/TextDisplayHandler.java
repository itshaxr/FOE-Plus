package dannypx.foe.handler.logic;

import dannypx.foe.handler.Handler;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Display;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TextDisplayHandler extends Handler {
    private static TextDisplayHandler INSTANCE = new TextDisplayHandler();

    public static TextDisplayHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new TextDisplayHandler();
        }
        return INSTANCE;
    }

    //region Fields
    public static final String MARKER = "\uF064";
    /// FOE-R's logo (in the default font), shown before FOE-R users' names.
    private static final String ICON_CHAR = "\uDB80\uDC6A";
    /// FOE+'s logo (in this mod's own font, so it can't clash with the server's glyphs), shown
    /// before FOE+ users' names instead.
    private static final Component FOE_PLUS_ICON = Component.literal("\uE000").withStyle(style -> style
            .withFont(new net.minecraft.network.chat.FontDescription.Resource(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(dannypx.foe.FishOnMCExtras.MOD_ID, "foe_plus_icon"))));

    private final Map<UUID, String> USERS = new ConcurrentHashMap<>();
    /// Players using FOE+ (this merged build); they get a + after their name.
    private final Map<UUID, String> FOE_PLUS_USERS = new ConcurrentHashMap<>();
    private volatile Pattern foePlusPattern = buildPattern(Set.of());
    private final Map<Display.TextDisplay, Entry> CACHE = new WeakHashMap<>();
    private volatile Pattern pattern = build();

    public record Entry(Component source, int lineWidth, Component prefixed, Display.TextDisplay.CachedInfo info,
                        boolean animated, long builtAt) {}

    /// The FOE+ developer (@phckd): their name tag gets a rainbow + instead of the gold one.
    public static final UUID DEV_UUID = UUID.fromString("2ec73f99-8e46-49ca-993a-618df38d6171");
    /// How often the developer's rainbow + changes colour.
    private static final long DEV_MARKER_FRAME_MS = 50L;
    private String lastSpecialNames = null;

    /// FOE+ beta testers: their name tag gets a green + instead of the gold one.
    public static final java.util.List<UUID> BETA_TESTERS = java.util.List.of(
            UUID.fromString("1444eb9a-fc24-47ac-8962-63160df55dcd"));

    /// A player's current name, from the server's player list (or yours, if it's you).
    private static String nameOf(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && uuid.equals(mc.player.getUUID())) return mc.player.getGameProfile().name();
        if (mc.getConnection() == null) return null;
        var info = mc.getConnection().getPlayerInfo(uuid);
        return info == null ? null : info.getProfile().name();
    }

    private static String ownName() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? null : mc.getUser().getName();
    }

    private static String devName() {
        return nameOf(DEV_UUID);
    }

    /// Names of the beta testers who are online.
    private static java.util.List<String> betaNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (UUID uuid : BETA_TESTERS) {
            String name = nameOf(uuid);
            if (name != null && !name.isEmpty()) names.add(name);
        }
        return names;
    }

    //endregion

    //region Methods
    @Override
    public void init() {
        this.clear();
    }

    public void addModUser(UUID uuid, String name) {
        this.USERS.put(uuid, name);
        this.rebuild();
    }

    public void removeModUser(UUID uuid) {
        boolean changed = this.USERS.remove(uuid) != null;
        changed |= this.FOE_PLUS_USERS.remove(uuid) != null;
        this.FOE_PLUS_COLORS.remove(uuid);
        if (changed) this.rebuild();
    }

    public void addFoePlusUser(UUID uuid, String name) {
        if (name.equals(this.FOE_PLUS_USERS.put(uuid, name))) return;
        this.rebuild();
    }

    /// /foe users: who this session has seen running FOE-R or FOE+ (for checking name tag logos).
    public void printDetectedUsers() {
        java.util.List<String> foer = new java.util.ArrayList<>();
        for (Map.Entry<UUID, String> e : this.USERS.entrySet()) {
            if (!this.FOE_PLUS_USERS.containsKey(e.getKey())) foer.add(e.getValue());
        }
        java.util.List<String> plus = new java.util.ArrayList<>(this.FOE_PLUS_USERS.values());
        java.util.Collections.sort(foer);
        java.util.Collections.sort(plus);
        dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.line("USERS", dannypx.foe.helper.FoeChat.AQUA)
                .append(dannypx.foe.helper.FoeChat.text("FOE-R (" + foer.size() + "): "))
                .append(dannypx.foe.helper.FoeChat.white(foer.isEmpty() ? "none seen yet" : String.join(", ", foer))));
        dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.line("USERS", dannypx.foe.helper.FoeChat.AQUA)
                .append(dannypx.foe.helper.FoeChat.text("FOE+ (" + plus.size() + "): "))
                .append(dannypx.foe.helper.FoeChat.white(plus.isEmpty() ? "none seen yet" : String.join(", ", plus))));
    }

    /// The + colour other FOE+ users picked (index into NameTagMarker.PLUS_COLORS).
    private final Map<UUID, Integer> FOE_PLUS_COLORS = new ConcurrentHashMap<>();

    public void setFoePlusColor(UUID uuid, int index) {
        Integer old = this.FOE_PLUS_COLORS.put(uuid, index);
        if (old == null || old != index) this.rebuild();
    }

    /// You picked a new colour for your own +: build the tags again.
    public void onOwnColorChanged() {
        this.clearCache();
    }

    private int colorOf(String name, String ownName, int ownColor) {
        if (ownName != null && ownName.equalsIgnoreCase(name)) return ownColor;
        for (Map.Entry<UUID, String> e : this.FOE_PLUS_USERS.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) return this.FOE_PLUS_COLORS.getOrDefault(e.getKey(), 0);
        }
        return 0;
    }

    public boolean isFoePlusUser(UUID uuid) {
        return this.FOE_PLUS_USERS.containsKey(uuid);
    }

    public boolean contains(UUID uuid) {
        return this.USERS.containsKey(uuid);
    }

    public boolean matches(String text) {
        return this.pattern.matcher(text).find();
    }

    private void rebuild() {
        this.pattern = build();
        this.foePlusPattern = buildPattern(new LinkedHashSet<>(this.FOE_PLUS_USERS.values()));
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.execute(this::clearCache);
        else this.clearCache();
    }

    private void clearCache() {
        this.CACHE.clear();
    }

    public void clear() {
        USERS.clear();
        FOE_PLUS_USERS.clear();
        FOE_PLUS_COLORS.clear();
        rebuild();
    }

    public void onLeave() {
        this.clear();
    }

    public Component apply(Component original) {
        return applyFor(original, devName(), betaNames(), System.currentTimeMillis(),
                dannypx.foe.config.Configs.rendererConfig.showFoePlusMarker.get(), ownName(),
                dannypx.foe.config.Configs.rendererConfig.foePlusColor.get());
    }

    /// Package-private for tests: the developer's and beta testers' names if they're online.
    Component applyFor(Component original, String devName, java.util.List<String> betaNames, long nowMs, boolean markers,
                       String ownName, int ownColor) {
        String s = original.getString();
        if (!s.contains(MARKER)) return original;
        boolean dev = isDevTag(s, devName);
        String beta = dev ? null : betaNames.stream().filter(n -> isDevTag(s, n)).findFirst().orElse(null);
        if (!dev && beta == null && !this.matches(s)) return original;
        Component named;
        if (!markers) named = original;
        else if (dev) named = dannypx.foe.helper.NameTagMarker.insertDevMarker(original, devName, nowMs);
        else if (beta != null) named = dannypx.foe.helper.NameTagMarker.insertAfter(original, beta, dannypx.foe.helper.NameTagMarker.BETA_MARKER);
        else {
            // Everyone else: the + in the colour they picked (gold until they pick one).
            java.util.regex.Matcher m = this.foePlusPattern.matcher(s);
            named = m.find()
                    ? dannypx.foe.helper.NameTagMarker.insertAfter(original, m.group(),
                            dannypx.foe.helper.NameTagMarker.colorMarker(colorOf(m.group(), ownName, ownColor)))
                    : original;
        }
        // FOE+ users (you, the developer, beta testers, anyone seen running FOE+) get the FOE+
        // logo; FOE-R users keep FOE-R's.
        boolean foePlus = dev || beta != null || this.foePlusPattern.matcher(s).find();
        Component icon = foePlus ? FOE_PLUS_ICON : Component.literal(ICON_CHAR);
        return Component.empty().append(icon).append(" ").append(named);
    }

    private static boolean isDevTag(String text, String devName) {
        if (devName == null || devName.isEmpty()) return false;
        return Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(devName) + "(?![A-Za-z0-9_])").matcher(text).find();
    }

    public Entry get(Display.TextDisplay display, Component source, int lineWidth, BiFunction<Component, Integer, Display.TextDisplay.CachedInfo> splitter) {
        // The developer or a beta tester joining or leaving changes their tag: build the tags again.
        String devName = devName();
        java.util.List<String> betaNames = betaNames();
        String special = devName + "|" + betaNames;
        if (!special.equals(lastSpecialNames)) {
            lastSpecialNames = special;
            this.CACHE.clear();
        }
        Entry e = this.CACHE.get(display);
        long now = System.currentTimeMillis();
        if (e != null && e.source() == source && e.lineWidth() == lineWidth
                && (!e.animated() || now - e.builtAt() < DEV_MARKER_FRAME_MS)) {
            return e;
        }

        boolean markers = dannypx.foe.config.Configs.rendererConfig.showFoePlusMarker.get();
        Component prefixed = this.applyFor(source, devName, betaNames, now, markers, ownName(),
                dannypx.foe.config.Configs.rendererConfig.foePlusColor.get());
        Display.TextDisplay.CachedInfo info =
                prefixed == source ? null : splitter.apply(prefixed, lineWidth);
        // Only the developer's tag animates (and is rebuilt a few times a second); every other
        // tag is built once and kept.
        boolean animated = prefixed != source && isDevTag(source.getString(), devName) && markers;

        e = new Entry(source, lineWidth, prefixed, info, animated, now);
        this.CACHE.put(display, e);
        return e;
    }

    private Pattern build() {
        Set<String> names = new LinkedHashSet<>(this.USERS.values());
        return buildPattern(names);
    }

    /// Whole-name pattern for these players, always including you (you use this mod too).
    private static Pattern buildPattern(Set<String> others) {
        Set<String> names = new LinkedHashSet<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) names.add(mc.getUser().getName());
        names.addAll(others);
        names.removeIf(n -> n == null || n.isBlank());
        // No names: match nothing. An empty alternation would match every name tag.
        if (names.isEmpty()) return Pattern.compile("(?!)");

        String alternation = names.stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));

        return Pattern.compile("(?<![A-Za-z0-9_])(?:" + alternation + ")(?![A-Za-z0-9_])");
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
