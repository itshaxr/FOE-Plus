package dannypx.foe.screens;

import dannypx.foe.handler.store.CustomHudDataHandler;
import dannypx.foe.handler.store.CustomHudIconDataHandler;
import dannypx.foe.type.Alignment;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.config.Configs;
import dannypx.foe.screens.element.Element;
import dannypx.foe.screens.element.hud.*;
import dannypx.foe.screens.widget.MovableBoxWidget;
import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedChoice;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedFloat;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FOE's HUD editor ("Move HUD Elements"), rebuilt on FOE-R's drag boxes.
 * Shows FOE's HUDs (contest, quests, daily quests, all-time stats, pet, bait, catch popup), FOE-R's
 * notifications and your custom HUDs. Drag to move, left-click to switch the anchor corner,
 * scroll over a box to resize it, middle-click for its settings. The top bar is fixed: its strip
 * is shown at the top and scrolling over it resizes the bar. Layout ported from FOECollab's
 * MoveHudScreen (AGPL-3.0).
 */
public class MoveElementScreen extends DefaultModScreen {
    private final List<Pair<String, Element>> customHudElements = new ArrayList<>();
    /// Scale setting behind each drag box, for scroll-to-resize.
    private final Map<MovableBoxWidget, ValidatedFloat> scales = new HashMap<>();
    /// Done before a box is sized with the scroll wheel (the FOE-R small bars: fixed where they are first).
    private final Map<MovableBoxWidget, Runnable> beforeResize = new HashMap<>();
    /// The HUD each drag box is (the ones that can be combined).
    private final Map<MovableBoxWidget, String> hudIds = new HashMap<>();

    /// The HUD box under the mouse, other than `dragged`, or null (any HUD, so a drop on one that can't take it can
    /// say why).
    private MovableBoxWidget boxUnderMouse(MovableBoxWidget dragged) {
        var window = minecraft.getWindow();
        double mx = minecraft.mouseHandler.getScaledXPos(window), my = minecraft.mouseHandler.getScaledYPos(window);
        for (var w : hudIds.keySet()) {
            if (w == dragged) continue;
            if (mx >= w.getX() && mx < w.getX() + w.getWidth() && my >= w.getY() && my < w.getY() + w.getHeight()) return w;
        }
        // the mouse slipped off: the box the dragged one's middle is over
        if (dragged != null) {
            double cx = dragged.getX() + dragged.getWidth() / 2.0, cy = dragged.getY() + dragged.getHeight() / 2.0;
            for (var w : hudIds.keySet()) {
                if (w == dragged) continue;
                if (cx >= w.getX() && cx < w.getX() + w.getWidth() && cy >= w.getY() && cy < w.getY() + w.getHeight()) return w;
            }
        }
        return null;
    }

    /// What a Shift-drop of `dragged` onto `target` would do: {host, member} to combine, or null with `why` set.
    private String[] combineFor(String dragged, String target, String[] why) {
        if (!dannypx.foe.screens.element.hud.HudCombine.combinable(dragged)) {
            why[0] = dannypx.foe.screens.element.hud.HudCombine.name(dragged) + " can't go inside another HUD";
            return null;
        }
        if (!dannypx.foe.screens.element.hud.HudCombine.combinable(target)) {
            why[0] = "HUDs can't go inside " + dannypx.foe.screens.element.hud.HudCombine.name(target);
            return null;
        }
        // dropped onto a HUD: it goes inside that one; when it already holds HUDs (the Timers), the one dropped on
        // goes inside it instead
        if (dannypx.foe.screens.element.hud.HudCombine.canCombine(target, dragged)) return new String[]{target, dragged};
        if (dannypx.foe.screens.element.hud.HudCombine.canCombine(dragged, target)) return new String[]{dragged, target};
        why[0] = "Both of these hold other HUDs";
        return null;
    }

    /// The box being dragged with Shift held, or null.
    private MovableBoxWidget shiftDragging() {
        if (!minecraft.hasShiftDown()) return null;
        return dragging != null && hudIds.containsKey(dragging) ? dragging : null;
    }

    /// The box being dragged with the left button, or null.
    private MovableBoxWidget dragging;

    /// A small button on a HUD in the mover (move a part up or down, two columns, split it off): where, what it shows,
    /// what it says when pointed at, and what it does.
    private record PartButton(int x, int y, String glyph, int colour, boolean on, String tip, Runnable action) {
        static final int SIZE = 11;

        boolean over(double mx, double my) {
            return mx >= x && mx < x + SIZE && my >= y && my < y + SIZE;
        }
    }

    private final List<PartButton> partButtons = new ArrayList<>();
    /// The box the buttons are on (kept while the mouse is on it or its buttons).
    private MovableBoxWidget buttonsOn;

    /// The buttons for the HUD box under the mouse: on each part's line (its own title for the top one) ▲ ▼ to change
    /// the order, ⫼ for two columns and ✕ to split it off.
    private void makePartButtons(int mouseX, int mouseY) {
        partButtons.clear();
        MovableBoxWidget box = null;
        if (dragging == null) {
            if (buttonsOn != null && hudIds.containsKey(buttonsOn) && (buttonsOn.isMouseOver(mouseX, mouseY) || nearButtons(buttonsOn, mouseX, mouseY)))
                box = buttonsOn;
            else for (var w : hudIds.keySet()) if (w.isMouseOver(mouseX, mouseY)) { box = w; break; }
        }
        buttonsOn = box;
        if (box == null || !(box.element() instanceof PanelElement p)) return;
        String hostId = hudIds.get(box);
        if (!dannypx.foe.screens.element.hud.HudCombine.combinable(hostId)) return;
        List<String> members = dannypx.foe.screens.element.hud.HudCombine.members(hostId);
        for (PanelElement.Section s : List.copyOf(p.sections)) {
            String id = s.id();
            boolean top = id.equals(hostId);
            int idx = members.indexOf(id);
            if (!top && idx < 0) continue;
            String name = dannypx.foe.screens.element.hud.HudCombine.name(id);
            List<PartButton> row = new ArrayList<>();
            if (!top) row.add(new PartButton(0, 0, "▲", 0xFFFFFFFF, false,
                    idx == 0 ? "Move " + name + " to the top" : "Move " + name + " up",
                    () -> dannypx.foe.screens.element.hud.HudCombine.up(id)));
            if (!members.isEmpty() && (top || idx < members.size() - 1)) row.add(new PartButton(0, 0, "▼", 0xFFFFFFFF, false,
                    "Move " + name + " down", () -> dannypx.foe.screens.element.hud.HudCombine.down(id)));
            boolean two = dannypx.foe.handler.store.HudLooks.get(id).twoColumns;
            row.add(new PartButton(0, 0, "cols", two ? 0xFF6BE36B : 0xFFFFFFFF, two,
                    two ? name + ": back to one column" : name + ": two columns (for lots of short lines)",
                    () -> {
                        var look = dannypx.foe.handler.store.HudLooks.edit(id);
                        look.twoColumns = !look.twoColumns;
                        dannypx.foe.handler.store.HudLooks.save();
                    }));
            if (!top) row.add(new PartButton(0, 0, "split", 0xFFFF6B6B, false, "Split " + name + " off (back to its own place)",
                    () -> dannypx.foe.screens.element.hud.HudCombine.split(id)));
            // right-aligned on the part's line, inside the box
            int x = s.x() + s.w() - row.size() * (PartButton.SIZE + 1) + 1;
            int y = s.y() + (s.h() - PartButton.SIZE) / 2;
            if (top) y = Math.max(s.y(), box.getY() + 2);
            for (PartButton b : row) {
                partButtons.add(new PartButton(x, y, b.glyph(), b.colour(), b.on(), b.tip(), b.action()));
                x += PartButton.SIZE + 1;
            }
        }
    }

    private boolean nearButtons(MovableBoxWidget box, double mx, double my) {
        for (PartButton b : partButtons) if (b.over(mx, my)) return true;
        return false;
    }

    private void renderPartButtons(GuiGraphics g, int mouseX, int mouseY) {
        makePartButtons(mouseX, mouseY);
        if (partButtons.isEmpty()) return;
        g.nextStratum();
        for (PartButton b : partButtons) {
            boolean hot = b.over(mouseX, mouseY);
            int s = PartButton.SIZE;
            g.fill(b.x(), b.y(), b.x() + s, b.y() + s, hot ? 0xF0505A60 : 0xE0202428);
            g.renderOutline(b.x(), b.y(), s, s, hot ? 0xFFFFFFFF : b.on() ? 0xFF6BE36B : 0xFF7A8590);
            // the game's font has no ⫼ or ✕: those two are drawn
            switch (b.glyph()) {
                case "cols" -> {
                    g.fill(b.x() + 3, b.y() + 3, b.x() + 5, b.y() + s - 3, b.colour());
                    g.fill(b.x() + s - 5, b.y() + 3, b.x() + s - 3, b.y() + s - 3, b.colour());
                }
                case "split" -> {
                    for (int i = 0; i < 5; i++) {
                        g.fill(b.x() + 3 + i, b.y() + 3 + i, b.x() + 4 + i, b.y() + 4 + i, b.colour());
                        g.fill(b.x() + s - 4 - i, b.y() + 3 + i, b.x() + s - 3 - i, b.y() + 4 + i, b.colour());
                    }
                }
                default -> g.drawString(this.font, b.glyph(), b.x() + (s - this.font.width(b.glyph())) / 2 + 1, b.y() + 2, b.colour(), false);
            }
            if (hot) g.setTooltipForNextFrame(this.font, Component.literal(b.tip()), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            for (PartButton b : partButtons) {
                if (b.over(event.x(), event.y())) {
                    b.action().run();
                    minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                    partButtons.clear();
                    rebuildWidgets();
                    return true;
                }
            }
        }
        boolean r = super.mouseClicked(event, doubled);
        // the box that took the click (the one on top where they overlap)
        dragging = r && event.button() == 0 && getFocused() instanceof MovableBoxWidget w ? w : null;
        return r;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        boolean r = super.mouseReleased(event);
        dragging = null;
        return r;
    }

    /// A message shown for a moment after a Shift-drop.
    private Component dropNote;
    private long dropNoteAt;

    public MoveElementScreen(Screen parent) {
        super(parent, Component.literal("Move HUD Elements"), true);
    }

    @Override
    protected void init() {
        super.init();
        scales.clear();
        beforeResize.clear();
        hudIds.clear();
        this.assembleCustomHudElements();
        this.addWidgets();
    }

    private void assembleCustomHudElements() {
        customHudElements.clear();
        CustomHudDataHandler.instance().getCustomHudData().customHudRawDataList
                .forEach((key, hud) -> customHudElements.add(Pair.of(key, new CustomHudElement(hud, Component.literal(key)))));
        CustomHudIconDataHandler.instance().getCustomHudIconData().customHudIconDataList
                .forEach((key, icon) -> customHudElements.add(Pair.of(key, new CustomHudIconElement(icon, Component.literal(key)))));
    }

    /// One FOE panel: only added while it is switched on, like FOE's editor.
    private void addPanel(Element element, ValidatedBoolean show, ValidatedInt x, ValidatedInt y,
                          ValidatedChoice<Alignment> alignment, ValidatedFloat scale, List<Alignment> anchors) {
        if (show != null && !show.get()) return;
        // a HUD inside another (that's showing) moves with that one
        String id = element instanceof dannypx.foe.screens.element.hud.PanelElement p ? p.lookId() : null;
        if (id != null && dannypx.foe.screens.element.hud.HudCombine.movesWithHost(id)) return;
        MovableBoxWidget[] self = new MovableBoxWidget[1];
        MovableBoxWidget widget = new MovableBoxWidget(this.minecraft, element, anchors, new MovableBoxWidget.Callback() {
            @Override
            public void onRelease(int xPercent, int yPercent, Alignment newAlignment) {
                // held Shift and dropped onto another HUD: put it inside that one (it keeps its own place for later)
                MovableBoxWidget onto = id != null && minecraft.hasShiftDown() ? boxUnderMouse(self[0]) : null;
                if (onto != null) {
                    String[] why = new String[1];
                    String[] pair = combineFor(id, hudIds.get(onto), why);
                    if (pair != null) {
                        dannypx.foe.screens.element.hud.HudCombine.combine(pair[0], pair[1]);
                        dropNote = Component.literal(dannypx.foe.screens.element.hud.HudCombine.name(pair[1]) + " is now inside "
                                + dannypx.foe.screens.element.hud.HudCombine.name(pair[0])).withStyle(ChatFormatting.GREEN);
                        dropNoteAt = net.minecraft.util.Util.getMillis();
                        // the host keeps its place; the one put inside it keeps its own for when it's split off
                        if (pair[0].equals(id)) {
                            x.accept(xPercent);
                            y.accept(yPercent);
                            if (alignment != null) alignment.accept(newAlignment);
                            Configs.hudConfig.save();
                        }
                        minecraft.execute(() -> rebuildWidgets());
                        return;
                    }
                    dropNote = Component.literal(why[0]).withStyle(ChatFormatting.RED);
                    dropNoteAt = net.minecraft.util.Util.getMillis();
                }
                x.accept(xPercent);
                y.accept(yPercent);
                if (alignment != null) alignment.accept(newAlignment);
                Configs.hudConfig.save();
            }

            @Override
            public void onConfig() {
                FoeSettingsScreen.open(show != null ? show : x);
            }
        });
        self[0] = widget;
        if (id != null) hudIds.put(widget, id);
        if (scale != null) scales.put(widget, scale);
        this.addRenderableWidget(widget);
    }

    private void addWidgets() {
        var hud = Configs.hudConfig;

        addPanel(new ContestPanelElement(true), hud.showContestPanel, hud.contestPanelXPosition, hud.contestPanelYPosition,
                hud.contestPanelAlignment, hud.contestPanelScale, Alignment.getCorners());
        addPanel(new DailyQuestPanelElement(true), hud.showDailyQuestPanel, hud.dailyQuestPanelXPosition, hud.dailyQuestPanelYPosition,
                hud.dailyQuestPanelAlignment, hud.dailyQuestPanelScale, Alignment.getCorners());
        addPanel(new StatsPanelElement(true), hud.showStatsPanel, hud.statsPanelXPosition, hud.statsPanelYPosition,
                hud.statsPanelAlignment, hud.statsPanelScale, Alignment.getCorners());
        addPanel(new QuestPanelElement(true), hud.showQuestPanel, hud.questPanelXPosition, hud.questPanelYPosition,
                hud.questPanelAlignment, hud.questPanelScale, Alignment.getCorners());
        addPanel(new PetPanelElement(true), hud.showPetPanel, hud.petPanelXPosition, hud.petPanelYPosition,
                hud.petPanelAlignment, hud.petPanelScale, Alignment.getCorners());
        addPanel(new BaitPanelElement(true), hud.showBaitPanel, hud.baitPanelXPosition, hud.baitPanelYPosition,
                hud.baitPanelAlignment, hud.baitPanelScale, Alignment.getCorners());
        addPanel(new AlertPanelElement(true), hud.showAlertPanel, hud.alertPanelXPosition, hud.alertPanelYPosition,
                hud.alertPanelAlignment, hud.alertPanelScale, Alignment.getCornersAndCenters());
        addPanel(new LuckScalePanelElement(true), hud.showLuckScalePanel, hud.luckScalePanelXPosition, hud.luckScalePanelYPosition,
                hud.luckScalePanelAlignment, hud.luckScalePanelScale, Alignment.getCorners());
        addPanel(new dannypx.foe.screens.element.hud.TimersPanelElement(true), hud.showTimersPanel, hud.timersPanelXPosition, hud.timersPanelYPosition,
                hud.timersPanelAlignment, hud.timersPanelScale, Alignment.getCorners());
        addPanel(new dannypx.foe.screens.element.hud.CommunityPanelElement(true), hud.showCommunityPanel, hud.communityPanelXPosition, hud.communityPanelYPosition,
                hud.communityPanelAlignment, hud.communityPanelScale, Alignment.getCorners());
        addPanel(new dannypx.foe.screens.element.hud.CrewPanelElement(true), hud.showCrewPanel, hud.crewPanelXPosition, hud.crewPanelYPosition,
                hud.crewPanelAlignment, hud.crewPanelScale, Alignment.getCorners());
        addPanel(new dannypx.foe.screens.element.hud.EventsPanelElement(true), hud.showEventsPanel, hud.eventsPanelXPosition, hud.eventsPanelYPosition,
                hud.eventsPanelAlignment, hud.eventsPanelScale, Alignment.getCornersAndCenters());
        addPanel(new dannypx.foe.screens.element.hud.MiniEventPanelElement(true), hud.showMiniEventPanel, hud.miniEventPanelXPosition, hud.miniEventPanelYPosition,
                hud.miniEventPanelAlignment, hud.miniEventPanelScale, Alignment.getCornersAndCenters());
        addPanel(new dannypx.foe.screens.element.hud.SessionStatsPanelElement(true), hud.showSessionStatsPanel, hud.sessionStatsPanelXPosition, hud.sessionStatsPanelYPosition,
                hud.sessionStatsPanelAlignment, hud.sessionStatsPanelScale, Alignment.getCorners());
        addPanel(new GearWarningPanelElement(true), hud.showGearWarning, hud.gearWarningXPosition, hud.gearWarningYPosition,
                hud.gearWarningAlignment, hud.gearWarningScale, Alignment.getCornersAndCenters());
        addPanel(new dannypx.foe.screens.element.hud.ChummerPanelElement(true), hud.showChummerPanel, hud.chummerPanelXPosition, hud.chummerPanelYPosition,
                hud.chummerPanelAlignment, hud.chummerPanelScale, Alignment.getCornersAndCenters());
        // the FOE-R HUD Style's boxes: only there (and so only here) with that style
        if (dannypx.foe.screens.element.hud.FoeRBoxes.on()) {
            addPanel(new dannypx.foe.screens.element.hud.ProfileElement(true), hud.showProfileElement, hud.profileElementXPosition, hud.profileElementYPosition,
                    hud.profileElementAlignment, hud.profileElementScale, Alignment.getTopCorners());
            addPanel(new dannypx.foe.screens.element.hud.PetElement(true), hud.showPetElement, hud.petElementXPosition, hud.petElementYPosition,
                    hud.petElementAlignment, hud.petElementScale, Alignment.getTopCorners());
            addPanel(new dannypx.foe.screens.element.hud.LocationElement(true), hud.showLocationElement, hud.locationElementXPosition, hud.locationElementYPosition,
                    hud.locationElementAlignment, hud.locationElementScale, Alignment.getTopCorners());
            // the bottom bar's rod parts and armour bars: anywhere, any size
            if (hud.showFoeRHotbar.get()) {
                if (hud.foeRHotbarParts.get()) addGroup(false, hud.foeRHotbarPartsScale);
                if (hud.foeRHotbarArmor.get()) addGroup(true, hud.foeRHotbarArmorScale);
            }
        }
        addPanel(new CatchPopupElement(true), hud.showCatchPopup, hud.catchPopupXPosition, hud.catchPopupYPosition,
                null, hud.catchPopupScale, List.of(Alignment.TOP));
        if (hud.catchRingOn.get()) {
            dannypx.foe.screens.element.hud.CatchRingElement ring = new dannypx.foe.screens.element.hud.CatchRingElement();
            MovableBoxWidget ringWidget = new MovableBoxWidget(this.minecraft, ring, List.of(Alignment.TOP_LEFT), new MovableBoxWidget.Callback() {
                @Override
                public void onRelease(int xPercent, int yPercent, Alignment newAlignment) {
                    float[] c = ring.centrePercent();
                    hud.catchRingX.accept(c[0]);
                    hud.catchRingY.accept(c[1]);
                    Configs.hudConfig.save();
                }

                @Override
                public void onConfig() {
                    FoeSettingsScreen.open(hud.catchRingOn);
                }
            });
            scales.put(ringWidget, hud.catchRingScale);
            this.addRenderableWidget(ringWidget);
        }
        addPanel(new NotifierElement(true), hud.showNotifierElement, hud.notifierElementXPosition, hud.notifierElementYPosition,
                hud.notifierElementAlignment, hud.notifierElementScale, Alignment.getCorners());

        for (Pair<String, Element> element : customHudElements) {
            if (element.value2() instanceof CustomHudElement) {
                this.addRenderableWidget(new MovableBoxWidget(this.minecraft, element.value2(), Alignment.getAll(), new MovableBoxWidget.Callback() {
                    @Override
                    public void onRelease(int xPercent, int yPercent, Alignment alignment) {
                        CustomHudDataHandler.instance().updateHud(element.value1(), xPercent, yPercent, alignment);
                    }

                    @Override
                    public void onConfig() {
                        Minecraft.getInstance().setScreen(new CustomHudMakerScreen(Minecraft.getInstance().screen));
                    }
                }));
            } else if (element.value2() instanceof CustomHudIconElement) {
                this.addRenderableWidget(new MovableBoxWidget(this.minecraft, element.value2(), Alignment.getAll(), new MovableBoxWidget.Callback() {
                    @Override
                    public void onRelease(int xPercent, int yPercent, Alignment alignment) {
                        CustomHudIconDataHandler.instance().updateHudIcon(element.value1(), xPercent, yPercent, alignment);
                    }

                    @Override
                    public void onConfig() {
                        Minecraft.getInstance().setScreen(new CustomHudIconMakerScreen(Minecraft.getInstance().screen));
                    }
                }));
            }
        }

        if (Configs.debugConfig.debugMode.get()) {
            this.addRenderableWidget(new MovableBoxWidget(this.minecraft, new _DebugField(true), Alignment.getCorners(), new MovableBoxWidget.Callback() {
                @Override
                public void onRelease(int xPercent, int yPercent, Alignment alignment) {
                    Configs.debugConfig.debugFieldXPosition.accept(xPercent);
                    Configs.debugConfig.debugFieldYPosition.accept(yPercent);
                    Configs.debugConfig.debugFieldAlignment.accept(alignment);
                    Configs.debugConfig.save();
                }

                @Override
                public void onConfig() {
                    FoeSettingsScreen.open(Configs.debugConfig.debugFieldElement);
                }
            }));
        }
    }

    private int barStripHeight() {
        return dannypx.foe.screens.element.hud.TopBarElement.shown()
                ? Math.round((16 + this.font.lineHeight) * Configs.hudConfig.topBarScale.get())
                : 0;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);

        // The top bar isn't movable; show its strip so you know the space is taken.
        int strip = barStripHeight();
        if (strip > 0) {
            guiGraphics.fill(0, 0, this.width, strip, 0x66000000);
            Component label = Component.literal("Top Bar ")
                    .append(Component.literal("Not movable").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))
                    .append(Component.literal("   •   ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("Scroll to resize").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            guiGraphics.drawString(this.font, label, this.width / 2 - this.font.width(label) / 2, strip / 2 - this.font.lineHeight / 2, 0xFFFFFFFF, true);
        }

        Component help = Component.literal("Drag to move • Click to change anchor • Scroll to resize • Middle-click for settings")
                .withStyle(ChatFormatting.GRAY);
        Component combine = Component.literal("Shift + drop a HUD onto another to put it inside • Point at a HUD for its order, columns and split buttons")
                .withStyle(ChatFormatting.DARK_GRAY);
        guiGraphics.drawString(this.font, help, this.width / 2 - this.font.width(help) / 2, this.height - this.font.lineHeight - 4, 0xFFFFFFFF, true);
        guiGraphics.drawString(this.font, combine, this.width / 2 - this.font.width(combine) / 2, this.height - this.font.lineHeight * 2 - 6, 0xFFFFFFFF, true);
        renderSize(guiGraphics);
        renderPartButtons(guiGraphics, mouseX, mouseY);
        renderCombine(guiGraphics);
    }

    /// While Shift-dragging over another HUD: that one outlined green (and what will happen) or red (and why not).
    /// After a drop, what happened.
    private void renderCombine(GuiGraphics g) {
        Component text = null;
        int tx = this.width / 2, ty = this.height / 2;
        MovableBoxWidget dragged = shiftDragging();
        if (dragged != null) {
            MovableBoxWidget target = boxUnderMouse(dragged);
            if (target != null) {
                String[] why = new String[1];
                String[] pair = combineFor(hudIds.get(dragged), hudIds.get(target), why);
                int colour = pair != null ? 0xFF55FF55 : 0xFFFF5555;
                g.renderOutline(target.getX() - 3, target.getY() - 3, target.getWidth() + 6, target.getHeight() + 6, colour);
                g.renderOutline(target.getX() - 2, target.getY() - 2, target.getWidth() + 4, target.getHeight() + 4, colour);
                text = pair != null
                        ? Component.literal("Let go to show " + dannypx.foe.screens.element.hud.HudCombine.name(pair[1]) + " inside "
                        + dannypx.foe.screens.element.hud.HudCombine.name(pair[0])).withStyle(ChatFormatting.GREEN)
                        : Component.literal(why[0]).withStyle(ChatFormatting.RED);
                tx = target.getX() + target.getWidth() / 2;
                ty = target.getY() - 16 >= 2 ? target.getY() - 16 : target.getY() + target.getHeight() + 5;
            } else {
                text = Component.literal("Drop onto another HUD to put it inside").withStyle(ChatFormatting.YELLOW);
                tx = dragged.getX() + dragged.getWidth() / 2;
                ty = dragged.getY() - 16 >= 2 ? dragged.getY() - 16 : dragged.getY() + dragged.getHeight() + 5;
            }
        } else if (dropNote != null && net.minecraft.util.Util.getMillis() - dropNoteAt < 2500) {
            text = dropNote;
            ty = this.height - this.font.lineHeight * 3 - 14;
        }
        if (text == null) return;
        int w = this.font.width(text) + 8;
        int x = Math.max(2, Math.min(this.width - w - 2, tx - w / 2));
        g.fill(x, ty, x + w, ty + 12, 0xDD000000);
        g.renderOutline(x, ty, w, 12, 0xFF555555);
        g.drawString(this.font, text, x + 4, ty + 2, 0xFFFFFFFF, true);
    }

    /// The FOE-R bottom bar's rod parts (or armour) bar, to drag and size.
    private void addGroup(boolean armor, ValidatedFloat scale) {
        var el = new dannypx.foe.screens.element.hud.FoeRGroupElement(armor);
        MovableBoxWidget w = new MovableBoxWidget(this.minecraft, el, List.of(Alignment.TOP_LEFT), new MovableBoxWidget.Callback() {
            @Override
            public void onRelease(int xPercent, int yPercent, Alignment newAlignment) {
                el.save();
            }

            @Override
            public void onConfig() {
                FoeSettingsScreen.open(Configs.hudConfig.foeRHotbarArmor);
            }
        });
        scales.put(w, scale);
        beforeResize.put(w, el::save);
        this.addRenderableWidget(w);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            float step = verticalAmount > 0 ? 0.05f : -0.05f;

            // Over the top bar strip: resize the bar.
            if (mouseY < barStripHeight()) {
                resize(Configs.hudConfig.topBarScale, step);
                return true;
            }

            for (Map.Entry<MovableBoxWidget, ValidatedFloat> entry : scales.entrySet()) {
                if (entry.getKey().isMouseOver(mouseX, mouseY)) {
                    Runnable first = beforeResize.get(entry.getKey());
                    if (first != null) first.run();
                    resize(entry.getValue(), step);
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void resize(ValidatedFloat scale, float step) {
        float next = Math.round((scale.get() + step) * 100f) / 100f;
        // never past the smallest it's drawn at, so scrolling back up makes it bigger at once
        if (step < 0) next = Math.max(next, Math.min(scale.get(), dannypx.foe.utils.HudScale.smallest(0.1f)));
        scale.accept(next);
        Configs.hudConfig.save();
        this.rebuildWidgets();
        sized = scale;
        sizedAt = net.minecraft.util.Util.getMillis();
    }

    /// The box last resized and when: its size shows as a percentage for a moment after each scroll.
    private ValidatedFloat sized;
    private long sizedAt;
    private static final long SIZE_SHOWN_MS = 1500;

    /// How big a box is, from 0% (the smallest it can be) to 100% (the biggest).
    static int percent(float value, float min, float max) {
        if (max <= min) return 100;
        return Math.max(0, Math.min(100, Math.round((value - min) / (max - min) * 100f)));
    }

    private static int percent(ValidatedFloat scale) {
        try {
            java.lang.reflect.Method lo = me.fzzyhmstrs.fzzy_config.validation.number.ValidatedNumber.class.getDeclaredMethod("getMinValue");
            java.lang.reflect.Method hi = me.fzzyhmstrs.fzzy_config.validation.number.ValidatedNumber.class.getDeclaredMethod("getMaxValue");
            lo.setAccessible(true); hi.setAccessible(true);
            return percent(scale.get(), ((Number) lo.invoke(scale)).floatValue(), ((Number) hi.invoke(scale)).floatValue());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return percent(scale.get(), 0.5f, 2.0f);
        }
    }

    private void renderSize(GuiGraphics g) {
        if (sized == null || net.minecraft.util.Util.getMillis() - sizedAt > SIZE_SHOWN_MS) return;
        int cx, y;
        if (sized == Configs.hudConfig.topBarScale) {
            cx = this.width / 2;
            y = barStripHeight() + 4;
        } else {
            MovableBoxWidget box = null;
            for (Map.Entry<MovableBoxWidget, ValidatedFloat> entry : scales.entrySet())
                if (entry.getValue() == sized) box = entry.getKey();
            if (box == null) return;
            cx = box.getX() + box.getWidth() / 2;
            // above the box, or below it when there's no room above
            y = box.getY() - 14 >= 2 ? box.getY() - 14 : box.getY() + box.getHeight() + 3;
        }
        Component text = Component.literal("Size ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(percent(sized) + "%").withStyle(ChatFormatting.WHITE));
        int w = this.font.width(text) + 8;
        int x = Math.max(2, Math.min(this.width - w - 2, cx - w / 2));
        g.fill(x, y, x + w, y + 12, 0xCC000000);
        g.renderOutline(x, y, w, 12, 0xFF555555);
        g.drawString(this.font, text, x + 4, y + 2, 0xFFFFFFFF, true);
    }
}
