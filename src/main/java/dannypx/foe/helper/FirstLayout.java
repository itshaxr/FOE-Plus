package dannypx.foe.helper;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.screens.element.hud.*;
import dannypx.foe.type.Alignment;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The HUD the first time anyone joins with FOE+: a tidy layout instead of every panel wherever its own default
 * puts it. The contest and quests stacked down the left, your pet, timers and events down the right, the
 * community goal at the top in the middle, the gear warning above the hotbar, each at about 70% size (kept sharp), nothing overlapping; the bigger extras (all-time
 * and session stats, mini event, alerts, bait, luck &amp; scale) start hidden, to turn on in HUD Settings.
 * <p>
 * It runs once ever on this install, and only for someone who has never played with FOE+ (a player who has keeps
 * their layout). A file in the config folder remembers it's been done.
 */
public final class FirstLayout {
    private FirstLayout() {}

    /// About 70% size, rounded to whole screen pixels per text pixel so the text stays sharp (2/3 at GUI scale 3,
    /// 3/4 at 4; full size at 2 or less, where there's room).
    static float SCALE = 0.7f;

    static float crispScale(double guiScale) {
        if (guiScale <= 0) return 0.7f;
        long k = Math.max(1, Math.round(0.7 * guiScale));
        if (k / guiScale < 0.6) return 1f;      // GUI scale 2 or less: there's room, full size
        return (float) (k / guiScale);
    }
    static final int GAP = 4;

    private static Path marker() {
        return FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("first_layout_done");
    }

    /// Called once the game knows which FOE+ (if any) this player last used. `newToFoePlus`: never played with it.
    public static void onJoin(boolean newToFoePlus, int screenW, int screenH) {
        try {
            if (Files.exists(marker())) return;
            if (newToFoePlus) apply(screenW, screenH);
            Files.createDirectories(marker().getParent());
            Files.writeString(marker(), "FOE+ set out the HUD neatly on the first join. Delete this file to have it do so again.\n");
        } catch (Exception e) {
            LoggerHandler.error(e);
        }
    }

    record Spot(String prefix, java.util.function.Supplier<PanelElement> make) {}

    static void apply(int screenW, int screenH) throws ReflectiveOperationException {
        var hud = Configs.hudConfig;
        SCALE = crispScale(net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
        for (String off : List.of("statsPanel", "sessionStatsPanel", "miniEventPanel", "alertPanel", "baitPanel", "luckScalePanel")) show(off, false);
        int top = hud.showTopBar.get() ? Math.round(25 * hud.topBarScale.get()) + GAP : GAP;
        column(List.of(new Spot("contestPanel", () -> new ContestPanelElement(true)), new Spot("questPanel", () -> new QuestPanelElement(true)),
                new Spot("dailyQuestPanel", () -> new DailyQuestPanelElement(true))), Alignment.TOP_LEFT, top, screenW, screenH);
        column(List.of(new Spot("petPanel", () -> new PetPanelElement(true)), new Spot("eventsPanel", () -> new EventsPanelElement(true)),
                new Spot("timersPanel", () -> new TimersPanelElement(true)), new Spot("crewPanel", () -> new CrewPanelElement(true))), Alignment.TOP_RIGHT, top, screenW, screenH);
        // The community goal in the middle at the top; the gear warning (only there when something's wrong) above the
        // hotbar, clear of the catch popup in the middle.
        place("communityPanel", Alignment.TOP, 50, pct(top, screenH));
        setFloat("gearWarningScale", SCALE);
        place("gearWarning", Alignment.BOTTOM, 50, pct(62, screenH));
        hud.save();
    }

    /// Panels one under another from `top`, each sized for a fuller panel than its preview (live text runs longer);
    /// one that wouldn't fit any more is hidden.
    static void column(List<Spot> spots, Alignment anchor, int top, int screenW, int screenH) throws ReflectiveOperationException {
        int y = top;
        for (Spot s : spots) {
            int h = measure(s.prefix(), s.make());
            if (s.prefix().toLowerCase().contains("quest")) h = Math.max(h, 64);   // its live list is longer than its sample
            if (y + h > screenH - 8) { show(s.prefix(), false); continue; }
            show(s.prefix(), true);
            place(s.prefix(), anchor, 1, pct(y, screenH));
            y += h + GAP;
        }
    }

    private static int measure(String prefix, java.util.function.Supplier<PanelElement> make) throws ReflectiveOperationException {
        setFloat(prefix + "Scale", SCALE);
        PanelElement p = make.get();
        return Math.max(20, p.measuredHeight());
    }

    static int pct(int px, int screen) {
        return Math.max(0, Math.min(100, (int) Math.ceil(px * 100.0 / screen)));
    }

    private static Field field(String name) throws NoSuchFieldException {
        return Configs.hudConfig.getClass().getField(name);
    }

    private static void show(String prefix, boolean on) throws ReflectiveOperationException {
        String n = prefix.equals("gearWarning") ? "showGearWarning" : "show" + Character.toUpperCase(prefix.charAt(0)) + prefix.substring(1);
        ((me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean) field(n).get(Configs.hudConfig)).accept(on);
    }

    @SuppressWarnings("unchecked")
    private static void place(String prefix, Alignment anchor, int xPct, int yPct) throws ReflectiveOperationException {
        ((me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt) field(prefix + "XPosition").get(Configs.hudConfig)).accept(xPct);
        ((me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt) field(prefix + "YPosition").get(Configs.hudConfig)).accept(yPct);
        Object choice = field(prefix + "Alignment").get(Configs.hudConfig);
        try {
            ((me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedChoice<Alignment>) choice).accept(anchor);
        } catch (RuntimeException ignored) {}
    }

    private static void setFloat(String name, float v) throws ReflectiveOperationException {
        ((me.fzzyhmstrs.fzzy_config.validation.number.ValidatedFloat) field(name).get(Configs.hudConfig)).accept(v);
    }
}
