package dannypx.foe.screens.element.hud;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.handler.renderer.TimersCrewBox;
import dannypx.foe.type.Alignment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Your crew and friends: whether a crew member is in range, then who's on (with the instance they're on), then who
 * isn't. Split out of the Timers panel, where it used to sit underneath.
 */
public class CrewPanelElement extends PanelElement {
    public CrewPanelElement() {
        this(false);
    }

    public CrewPanelElement(boolean isCopy) {
        super(dannypx.foe.helper.HudFine.get(Configs.hudConfig.crewPanelXPosition),
                dannypx.foe.helper.HudFine.get(Configs.hudConfig.crewPanelYPosition),
                Configs.hudConfig.crewPanelAlignment.get(),
                Component.literal("Social"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showCrewPanel.get(); }
    @Override protected float configX() { return dannypx.foe.helper.HudFine.get(Configs.hudConfig.crewPanelXPosition); }
    @Override protected float configY() { return dannypx.foe.helper.HudFine.get(Configs.hudConfig.crewPanelYPosition); }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.crewPanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.crewPanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.crewPanelOpacity.get(); }

    @Override
    protected boolean alignTextToAnchor() {
        return false;
    }

    @Override
    protected Component title() {
        return title(0, 0);
    }

    /// Package-private for tests: just "ѕᴏᴄɪᴀʟ" (who's on is in the lines below it).
    static Component title(int online, int total) {
        return Component.literal("ѕᴏᴄɪᴀʟ").withStyle(ChatFormatting.BOLD);
    }

    @Override
    protected List<Component> lines() {
        splitOnce();
        if (!Configs.inventoryScreenConfig.timersCrew.get()) return List.of();
        // offline names follow the inventory box's "Show offline names" switch, so both always agree
        TimersCrewBox.Crew crew = TimersCrewBox.crew();
        List<Component> all = lines(crew.range(), crew.total(), TimersCrewBox.legend(), crew.onlineNames(), crew.offlineNames());
        return fitBelow(all, roomForLines());
    }

    /// How many lines fit between where the panel starts and the screen's bottom, for a panel anchored at the top
    /// (it grows down: kept on screen, a longer list used to push the whole panel up instead); no limit otherwise.
    private int roomForLines() {
        Alignment a = configAlignment();
        if (a != Alignment.TOP_LEFT && a != Alignment.TOP_RIGHT) return Integer.MAX_VALUE;
        Minecraft mc = Minecraft.getInstance();
        float s = Math.max(0.1f, scale());
        float screenH = mc.getWindow().getGuiScaledHeight() / s;
        float room = screenH - screenH * configY() - 24;      // its border, title and padding
        return Math.max(3, (int) (room / (mc.font.lineHeight + 2)));
    }

    /// Package-private for tests: the first lines that fit in `max`, the last one then saying how many more there
    /// are ("+4 more").
    static List<Component> fitBelow(List<Component> lines, int max) {
        if (lines.size() <= max) return lines;
        List<Component> out = new ArrayList<>(lines.subList(0, max - 1));
        out.add(Component.literal("+" + (lines.size() - (max - 1)) + " more").withStyle(ChatFormatting.DARK_GRAY));
        return out;
    }

    /// Package-private for tests: the panel's lines: the range line, then (when there's anyone) the key and the
    /// names, online first.
    static List<Component> lines(Component range, int total, Component legend, List<Component> on, List<Component> off) {
        List<Component> lines = new ArrayList<>();
        if (range != null) lines.add(range);
        if (total > 0) {
            lines.add(legend);
            lines.addAll(on);
            lines.addAll(off);
        }
        return lines;
    }

    @Override
    protected List<Component> previewLines() {
        return List.of(
                Component.literal("✔ Member in Range").withStyle(ChatFormatting.GREEN),
                TimersCrewBox.legend(),
                Component.literal("■ ").withColor(0xFFAA00).append(Component.literal("Crewmate").withStyle(ChatFormatting.WHITE)).append(Component.literal(" (Instance 2)").withStyle(ChatFormatting.GRAY)),
                Component.literal("■ ").withColor(0x55AAFF).append(Component.literal("Friend").withStyle(ChatFormatting.WHITE)),
                Component.literal("■ Offline").withStyle(ChatFormatting.DARK_GRAY));
    }

    //region The split from the Timers panel, once

    private static boolean checked = false;

    private static Path marker() {
        return FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("crew_panel_split.txt");
    }

    /// The first time this version runs: whoever had their crew under the Timers panel gets this panel on, right
    /// under it (or, for a panel anchored at the bottom, right below it with the timers moved up), so nothing goes
    /// missing. Everyone else gets it off.
    static void splitOnce() {
        if (checked) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        checked = true;
        try {
            if (Files.exists(marker())) return;
            var hud = Configs.hudConfig;
            boolean had = hud.showTimersPanel.get() && Configs.inventoryScreenConfig.timersCrew.get();
            hud.showCrewPanel.accept(had);
            if (had) {
                int screenH = Math.max(1, mc.getWindow().getGuiScaledHeight());
                int timersH = new TimersPanelElement(true).measuredHeight();
                int crewH = new CrewPanelElement(false).measuredHeight();
                int[] at = placeUnder(hud.timersPanelAlignment.get(), hud.timersPanelYPosition.get(), timersH, crewH, screenH);
                hud.crewPanelAlignment.accept(hud.timersPanelAlignment.get());
                hud.crewPanelXPosition.accept(hud.timersPanelXPosition.get());
                hud.crewPanelScale.accept(hud.timersPanelScale.get());
                hud.crewPanelOpacity.accept(hud.timersPanelOpacity.get());
                hud.timersPanelYPosition.accept(at[0]);
                hud.crewPanelYPosition.accept(at[1]);
            }
            hud.save();
            Files.createDirectories(marker().getParent());
            Files.writeString(marker(), "FOE+ split your crew and friends out of the Timers panel into their own panel.\n");
        } catch (Exception e) {
            LoggerHandler.error(e);
        }
    }

    /// Package-private for tests: {the timers' y %, the crew panel's y %} so the crew panel sits right under the
    /// timers. Anchored at the top: the timers stay and the crew goes below. At the bottom (y counts up from the
    /// bottom): the crew takes the timers' place and the timers move up above it.
    static int[] placeUnder(Alignment anchor, int timersY, int timersH, int crewH, int screenH) {
        boolean bottom = anchor == Alignment.BOTTOM_LEFT || anchor == Alignment.BOTTOM_RIGHT || anchor == Alignment.BOTTOM;
        if (!bottom) return new int[]{timersY, Math.min(100, timersY + pct(timersH + 3, screenH))};
        return new int[]{Math.min(100, timersY + pct(crewH + 3, screenH)), timersY};
    }

    private static int pct(int px, int screen) {
        return (int) Math.ceil(px * 100.0 / screen);
    }
    //endregion
}
