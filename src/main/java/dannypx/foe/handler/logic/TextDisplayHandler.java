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
    private static final String ICON_CHAR = "\uDB80\uDC6A";

    private final Map<UUID, String> USERS = new ConcurrentHashMap<>();
    /// Players using FOE+ (this merged build); they get a + after their name.
    private final Map<UUID, String> FOE_PLUS_USERS = new ConcurrentHashMap<>();
    private volatile Pattern foePlusPattern = buildPattern(Set.of());
    private final Map<Display.TextDisplay, Entry> CACHE = new WeakHashMap<>();
    private volatile Pattern pattern = build();

    public record Entry(Component source, int lineWidth, Component prefixed, Display.TextDisplay.CachedInfo info) {}

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
        if (changed) this.rebuild();
    }

    public void addFoePlusUser(UUID uuid, String name) {
        if (name.equals(this.FOE_PLUS_USERS.put(uuid, name))) return;
        this.rebuild();
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
        Minecraft.getInstance().execute(this::clearCache);
    }

    private void clearCache() {
        this.CACHE.clear();
    }

    public void clear() {
        USERS.clear();
        FOE_PLUS_USERS.clear();
        rebuild();
    }

    public void onLeave() {
        this.clear();
    }

    public Component apply(Component original) {
        String s = original.getString();
        if (!s.contains(MARKER)) return original;
        if (!this.matches(s)) return original;
        Component named = dannypx.foe.config.Configs.rendererConfig.showFoePlusMarker.get()
                ? dannypx.foe.helper.NameTagMarker.insertAfterName(original, this.foePlusPattern) : original;
        return Component.empty().append(ICON_CHAR).append(" ").append(named);
    }

    public Entry get(Display.TextDisplay display, Component source, int lineWidth, BiFunction<Component, Integer, Display.TextDisplay.CachedInfo> splitter) {
        Entry e = this.CACHE.get(display);
        if (e != null && e.source() == source && e.lineWidth() == lineWidth) {
            return e;
        }

        Component prefixed = this.apply(source);
        Display.TextDisplay.CachedInfo info =
                prefixed == source ? null : splitter.apply(prefixed, lineWidth);

        e = new Entry(source, lineWidth, prefixed, info);
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
        names.add(Minecraft.getInstance().getUser().getName());
        names.addAll(others);

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
