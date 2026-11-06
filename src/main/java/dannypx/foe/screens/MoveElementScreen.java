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

    public MoveElementScreen(Screen parent) {
        super(parent, Component.literal("Move HUD Elements"), true);
    }

    @Override
    protected void init() {
        super.init();
        scales.clear();
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
        MovableBoxWidget widget = new MovableBoxWidget(this.minecraft, element, anchors, new MovableBoxWidget.Callback() {
            @Override
            public void onRelease(int xPercent, int yPercent, Alignment newAlignment) {
                x.accept(xPercent);
                y.accept(yPercent);
                if (alignment != null) alignment.accept(newAlignment);
                Configs.hudConfig.save();
            }

            @Override
            public void onConfig() {
                ConfigApiJava.INSTANCE.openScreen(show != null ? show.translationKey() : x.translationKey());
            }
        });
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
        addPanel(new dannypx.foe.screens.element.hud.EventsPanelElement(true), hud.showEventsPanel, hud.eventsPanelXPosition, hud.eventsPanelYPosition,
                hud.eventsPanelAlignment, hud.eventsPanelScale, Alignment.getCornersAndCenters());
        addPanel(new dannypx.foe.screens.element.hud.MiniEventPanelElement(true), hud.showMiniEventPanel, hud.miniEventPanelXPosition, hud.miniEventPanelYPosition,
                hud.miniEventPanelAlignment, hud.miniEventPanelScale, Alignment.getCornersAndCenters());
        addPanel(new GearWarningPanelElement(true), hud.showGearWarning, hud.gearWarningXPosition, hud.gearWarningYPosition,
                hud.gearWarningAlignment, hud.gearWarningScale, Alignment.getCornersAndCenters());
        addPanel(new CatchPopupElement(true), null, hud.catchPopupXPosition, hud.catchPopupYPosition,
                null, hud.catchPopupScale, List.of(Alignment.TOP));
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
                    ConfigApiJava.INSTANCE.openScreen(Configs.debugConfig.debugFieldElement.translationKey());
                }
            }));
        }
    }

    private int barStripHeight() {
        return Configs.hudConfig.showTopBar.get()
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
        guiGraphics.drawString(this.font, help, this.width / 2 - this.font.width(help) / 2, this.height - this.font.lineHeight - 4, 0xFFFFFFFF, true);
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
                    resize(entry.getValue(), step);
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void resize(ValidatedFloat scale, float step) {
        float next = Math.round((scale.get() + step) * 100f) / 100f;
        scale.accept(next);
        Configs.hudConfig.save();
        this.rebuildWidgets();
    }
}
