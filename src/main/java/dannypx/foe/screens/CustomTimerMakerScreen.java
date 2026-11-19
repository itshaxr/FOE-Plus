package dannypx.foe.screens;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.logic.CodeExecuterHandler;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.handler.logic.TimerHandler;
import dannypx.foe.handler.store.CustomTimerDataHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.screens.interfaces.ScreenConstants;
import dannypx.foe.screens.widget.ButtonListWidget;
import dannypx.foe.screens.widget.SmallButtonBuilder;
import dannypx.foe.type.tuple.Quartet;
import dannypx.foe.type.tuple.Triplet;
import dannypx.foe.type.type_adapter.PatternAdapter;
import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

public class CustomTimerMakerScreen extends Screen implements ScreenConstants {
    //region Fields
    private final Screen parentScreen;

    private ButtonListWidget buttonList;
    private Map<String, ButtonListWidget.ButtonEntry> buttonEntryMap = new HashMap<>();
    private String selectedTimerId;
    private CustomTimerDataHandler.CustomTimer selectedTimer;


    private Component header;
    private final int widgetHeight = 20;

    private EditBox nameEditBox;
    private Checkbox useTimerCheckBox;
    private Checkbox isPeriodCheckBox;

    private final int sideWidth = 100;
    private EditBox timerEditBox;
    private EditBox offTimerEditBox;
    private EditBox offsetEditBox;
    private EditBox notificationToTriggerEditBox;
    private EditBox notificationToTriggerEndEditBox;
    private EditBox chatNotificationToTriggerEditBox;
    private EditBox chatNotificationToTriggerEndEditBox;
    private EditBox trackerToTriggerEditBox;
    private EditBox trackerToTriggerEndEditBox;
    private EditBox cleanUpChatTriggersEditBox;
    //endregion

    //region Methods
    public CustomTimerMakerScreen(Screen parent) {
        super(Component.literal("Custom Timer Maker Screen"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.renderWidgets();
        this.resetFields();
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.renderBox(guiGraphics, mouseX, mouseY, delta);

        super.render(guiGraphics, mouseX, mouseY, delta);

        this.renderComponent(guiGraphics, mouseX, mouseY, delta);
        this.renderTooltip(guiGraphics, mouseX, mouseY, delta);
        this.buttonList.render(guiGraphics, mouseX, mouseY, delta);
    }

    private void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        if(isPeriodCheckBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("'period' mode is for timers that require a period of ON and OFF time").withStyle(ChatFormatting.GRAY),
                    Component.literal("After x seconds of ON time, the timer will be on OFF mode for x seconds, and then back to ON again").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        }

        if(timerEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Time in seconds").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("When not in 'period' mode").withStyle(ChatFormatting.GRAY),
                    Component.literal("Timer for every x seconds").withStyle(ChatFormatting.DARK_GRAY),
                    Component.empty(),
                    Component.literal("When in 'period' mode").withStyle(ChatFormatting.GRAY),
                    Component.literal("Time period in seconds whenever it is 'ON'").withStyle(ChatFormatting.DARK_GRAY)
            ), mouseX, mouseY);
        }

        if(offTimerEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Time in seconds").withStyle(ChatFormatting.GRAY),
                    Component.literal("Only for 'period' mode").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("When in 'period' mode").withStyle(ChatFormatting.GRAY),
                    Component.literal("Time period in seconds whenever it is 'OFF'").withStyle(ChatFormatting.DARK_GRAY)
            ), mouseX, mouseY);
        }

        if(offsetEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Time in seconds").withStyle(ChatFormatting.GRAY),
                    Component.literal("Offset of the timer for alignment").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        }

        if(notificationToTriggerEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Optional").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    Component.empty(),
                    Component.literal("When not in 'period' mode").withStyle(ChatFormatting.GRAY),
                    Component.literal("Triggers when timer hits 0").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("When in 'period' mode").withStyle(ChatFormatting.GRAY),
                    Component.literal("Trigger when OFF timer hits 0").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("Notification Name").withStyle(ChatFormatting.YELLOW),
                    Component.empty(),
                    Component.literal("Split multiple notifications with a comma").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        }

        if(notificationToTriggerEndEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Optional").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    Component.literal("Only for 'period' mode").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("When in 'period' mode").withStyle(ChatFormatting.GRAY),
                    Component.literal("Trigger when ON timer hits 0").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("Notification Name").withStyle(ChatFormatting.YELLOW),
                    Component.empty(),
                    Component.literal("Split multiple notifications with a comma").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        }

        if(chatNotificationToTriggerEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Optional").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    Component.empty(),
                    Component.literal("When not in 'period' mode").withStyle(ChatFormatting.GRAY),
                    Component.literal("Triggers when timer hits 0").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("When in 'period' mode").withStyle(ChatFormatting.GRAY),
                    Component.literal("Trigger when OFF timer hits 0").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("Chat Notification Name").withStyle(ChatFormatting.YELLOW),
                    Component.empty(),
                    Component.literal("Split multiple chat notifications with a comma").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        }

        if(chatNotificationToTriggerEndEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Optional").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    Component.literal("Only for 'period' mode").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("When in 'period' mode").withStyle(ChatFormatting.GRAY),
                    Component.literal("Trigger when ON timer hits 0").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("Chat Notification Name").withStyle(ChatFormatting.YELLOW),
                    Component.empty(),
                    Component.literal("Split multiple chat notifications with a comma").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        }

        if(trackerToTriggerEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Optional").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    Component.empty(),
                    Component.literal("When not in 'period' mode").withStyle(ChatFormatting.GRAY),
                    Component.literal("Triggers when timer hits 0").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("When in 'period' mode").withStyle(ChatFormatting.GRAY),
                    Component.literal("Trigger when OFF timer hits 0").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("Tracker and Action Name split using a dot").withStyle(ChatFormatting.YELLOW),
                    Component.literal("e.g. \"tracker.action\"").withStyle(ChatFormatting.YELLOW),
                    Component.empty(),
                    Component.literal("Split multiple trackers with a comma").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        }

        if(trackerToTriggerEndEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Optional").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    Component.literal("Only for 'period' mode").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("When in 'period' mode").withStyle(ChatFormatting.GRAY),
                    Component.literal("Trigger when ON timer hits 0").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("Tracker and Action Name split using a dot").withStyle(ChatFormatting.YELLOW),
                    Component.literal("e.g. \"tracker.action\"").withStyle(ChatFormatting.YELLOW),
                    Component.empty(),
                    Component.literal("Split multiple trackers with a comma").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        }

        if(cleanUpChatTriggersEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Optional").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    Component.empty(),
                    Component.literal("When not in 'period' mode").withStyle(ChatFormatting.GRAY),
                    Component.literal("Clean chat triggers when timer hits 0").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("When in 'period' mode").withStyle(ChatFormatting.GRAY),
                    Component.literal("Clean chat triggers when ON timer hits 0").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("Split multiple chat triggers with a comma").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        }
    }

    private void renderComponent(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        guiGraphics.drawCenteredString(font,
                this.header,
                (BUTTON_WIDTH + PADDING * 2) + (this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2)) / 2,
                PADDING + widgetHeight / 2 - font.lineHeight / 2,
                CommonColors.WHITE
        );

        guiGraphics.drawString(font,
                Component.literal("Name"),
                (BUTTON_WIDTH + PADDING * 2) + PADDING,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING),
                CommonColors.WHITE,
                true
        );

        guiGraphics.drawString(font,
                Component.literal("Timer"),
                (BUTTON_WIDTH + PADDING * 2) + PADDING,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 2,
                CommonColors.WHITE,
                true
        );

        guiGraphics.drawString(font,
                Component.literal("Off Timer"),
                (BUTTON_WIDTH + PADDING * 2) + PADDING,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 3,
                CommonColors.WHITE,
                true
        );

        guiGraphics.drawString(font,
                Component.literal("Offset"),
                (BUTTON_WIDTH + PADDING * 2) + PADDING,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 4,
                CommonColors.WHITE,
                true
        );

        guiGraphics.drawString(font,
                Component.literal("Trigger Notif."),
                (BUTTON_WIDTH + PADDING * 2) + PADDING,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 5,
                CommonColors.WHITE,
                true
        );

        guiGraphics.drawString(font,
                Component.literal("Trigger Notif. End"),
                (BUTTON_WIDTH + PADDING * 2) + PADDING + (this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2) / 2,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 5,
                CommonColors.WHITE,
                true
        );

        guiGraphics.drawString(font,
                Component.literal("Trigger C.Notif."),
                (BUTTON_WIDTH + PADDING * 2) + PADDING,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 6,
                CommonColors.WHITE,
                true
        );

        guiGraphics.drawString(font,
                Component.literal("Trigger C.Notif. End"),
                (BUTTON_WIDTH + PADDING * 2) + PADDING + (this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2) / 2,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 6,
                CommonColors.WHITE,
                true
        );

        guiGraphics.drawString(font,
                Component.literal("Trigger Tracker"),
                (BUTTON_WIDTH + PADDING * 2) + PADDING,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 7,
                CommonColors.WHITE,
                true
        );

        guiGraphics.drawString(font,
                Component.literal("Trigger Track. End"),
                (BUTTON_WIDTH + PADDING * 2) + PADDING + (this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2) / 2,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 7,
                CommonColors.WHITE,
                true
        );

        guiGraphics.drawString(font,
                Component.literal("Clear Triggers"),
                (BUTTON_WIDTH + PADDING * 2) + PADDING,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 8,
                CommonColors.WHITE,
                true
        );

        try {
            if(selectedTimerId != null) {
                long timeSeconds = System.currentTimeMillis() / 1000;
                long adjustedWithOffset = timeSeconds + Integer.parseInt(offsetEditBox.getValue());

                if(isPeriodCheckBox.selected()) {
                    long timer = Integer.parseInt(timerEditBox.getValue());
                    long offTimer = Integer.parseInt(offTimerEditBox.getValue());
                    long cycle = timer + offTimer;
                    long pos = (adjustedWithOffset + offTimer) % cycle;
                    long remainingOn = cycle - pos;
                    long midPos = adjustedWithOffset % cycle;
                    long remainingOff = cycle - midPos;

                    Triplet<Long, Long, Long> remainingTime = getTime(remainingOn);
                    Triplet<Long, Long, Long> remainingTimeMid = getTime(remainingOff);

                    boolean isOnTimer = remainingOn < timer;

                    Component onTimerComponent = TextHelper.concat(
                            Component.literal("Timer till ").withStyle(ChatFormatting.GRAY),
                            Component.literal("ON").withStyle(ChatFormatting.GREEN),
                            Component.literal(" period ends: ").withStyle(ChatFormatting.GRAY),
                            Component.literal(String.valueOf(remainingTime.value3())).withStyle(ChatFormatting.YELLOW),
                            Component.literal(":").withStyle(ChatFormatting.YELLOW),
                            Component.literal(String.format("%02d", remainingTime.value2())).withStyle(ChatFormatting.YELLOW),
                            Component.literal(":").withStyle(ChatFormatting.YELLOW),
                            Component.literal(String.format("%02d", remainingTime.value1())).withStyle(ChatFormatting.YELLOW)
                    );

                    Component offTimerComponent = TextHelper.concat(
                            Component.literal("Timer till ").withStyle(ChatFormatting.GRAY),
                            Component.literal("OFF").withStyle(ChatFormatting.RED),
                            Component.literal(" period ends: ").withStyle(ChatFormatting.GRAY),
                            Component.literal(String.valueOf(remainingTimeMid.value3())).withStyle(ChatFormatting.YELLOW),
                            Component.literal(":").withStyle(ChatFormatting.YELLOW),
                            Component.literal(String.format("%02d", remainingTimeMid.value2())).withStyle(ChatFormatting.YELLOW),
                            Component.literal(":").withStyle(ChatFormatting.YELLOW),
                            Component.literal(String.format("%02d", remainingTimeMid.value1())).withStyle(ChatFormatting.YELLOW)
                    );

                    guiGraphics.drawString(font,
                            onTimerComponent,
                            (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                            PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 9,
                            CommonColors.WHITE,
                            true
                    );

                    guiGraphics.drawString(font,
                            offTimerComponent,
                            (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                            PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 9 + (font.lineHeight + PADDING_QUART) * 1,
                            CommonColors.WHITE,
                            true
                    );

                    Component isOnTimerComponent = TextHelper.concat(
                            Component.literal("Currently in ").withStyle(ChatFormatting.GRAY),
                            isOnTimer ? Component.literal("ON").withStyle(ChatFormatting.GREEN) : Component.literal("OFF").withStyle(ChatFormatting.RED),
                            Component.literal(" period").withStyle(ChatFormatting.GRAY)
                    );

                    guiGraphics.drawString(font,
                            isOnTimerComponent,
                            (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                            PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 9 + (font.lineHeight + PADDING_QUART) * 2,
                            CommonColors.WHITE,
                            true
                    );
                } else {
                    long interval = Integer.parseInt(timerEditBox.getValue());
                    long pos = adjustedWithOffset % interval;
                    long remainingOn = interval - pos;

                    Triplet<Long, Long, Long> remainingTime = getTime(remainingOn);

                    Component onTimerComponent = TextHelper.concat(
                            Component.literal("Timer: ").withStyle(ChatFormatting.GRAY),
                            Component.literal(String.valueOf(remainingTime.value3())).withStyle(ChatFormatting.YELLOW),
                            Component.literal(":").withStyle(ChatFormatting.YELLOW),
                            Component.literal(String.format("%02d", remainingTime.value2())).withStyle(ChatFormatting.YELLOW),
                            Component.literal(":").withStyle(ChatFormatting.YELLOW),
                            Component.literal(String.format("%02d", remainingTime.value1())).withStyle(ChatFormatting.YELLOW)
                    );

                    guiGraphics.drawString(font,
                            onTimerComponent,
                            (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                            PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 9,
                            CommonColors.WHITE,
                            true
                    );

                }
            }
        } catch (Exception ignored) {

        }
    }

    private Triplet<Long, Long, Long> getTime(long seconds) {
        long hour = seconds / 3600;
        long minute = (seconds % 3600) / 60;
        long second = seconds % 60;

        return Triplet.of(second, minute, hour);
    }

    private void renderBox(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta)
    {
        guiGraphics.fill(
                (BUTTON_WIDTH + PADDING * 2), 0,
                this.minecraft.getWindow().getGuiScaledWidth(),
                this.minecraft.getWindow().getGuiScaledHeight() - (BUTTON_HEIGHT + PADDING_HALF) - 3,
                0x99000000);
        guiGraphics.hLine((BUTTON_WIDTH + PADDING * 2), this.minecraft.getWindow().getGuiScaledWidth(), this.minecraft.getWindow().getGuiScaledHeight() - (BUTTON_HEIGHT + PADDING_HALF) - 3, CommonColors.DARK_GRAY);
        guiGraphics.vLine((BUTTON_WIDTH + PADDING * 2), 0, this.minecraft.getWindow().getGuiScaledHeight() - (BUTTON_HEIGHT + PADDING_HALF) - 3, CommonColors.DARK_GRAY);
    }

    private void renderWidgets() {
        List<AbstractWidget> widgets = new ArrayList<>();

        widgets.add(this.saveBackButton());
        widgets.add(this.saveButton());
        widgets.add(this.backButton());

        widgets.add(getButtonList());

        widgets.add(getNewButtonElementButton());
        widgets.add(getDeleteButtonElementButton());
        widgets.add(getImportButton());
        widgets.add(getExportButton());

        widgets.add(getNameEditBox());
        widgets.add(getUseTimerCheckBox());
        widgets.add(getIsPeriodCheckBox());
        widgets.add(getTimerEditBox());
        widgets.add(getOffTimerEditBox());
        widgets.add(getOffsetEditBox());
        widgets.add(getNotificationToTriggerEditBox());
        widgets.add(getNotificationToTriggerEndEditBox());
        widgets.add(getChatNotificationToTriggerEditBox());
        widgets.add(getChatNotificationToTriggerEndEditBox());
        widgets.add(getTrackerToTriggerEditBox());
        widgets.add(getTrackerToTriggerEndEditBox());
        widgets.add(getCleanUpChatTriggersEditBox());

        widgets.forEach(this::addRenderableWidget);
    }

    private AbstractWidget getNameEditBox() {
        nameEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + widgetHeight + PADDING,
                this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2 - (sideWidth + PADDING) * 2 - sideWidth,
                widgetHeight,
                Component.empty()
        );
        nameEditBox.setMaxLength(Integer.MAX_VALUE);

        nameEditBox.setResponder(s -> {
            if(selectedTimerId != null) {
                nameEditBox.setHint(Component.literal(s));
            }
        });

        return nameEditBox;
    }

    private AbstractWidget getUseTimerCheckBox() {
        useTimerCheckBox = Checkbox.builder(
                        Component.literal("Use Timer"),
                        font
                )
                .pos(this.minecraft.getWindow().getGuiScaledWidth() - (PADDING + sideWidth) * 2
                        , PADDING + widgetHeight + PADDING)
                .selected(true)
                .onValueChange((checkbox, checked) -> {})
                .build();
        return useTimerCheckBox;
    }

    private AbstractWidget getIsPeriodCheckBox() {
        isPeriodCheckBox = Checkbox.builder(
                        Component.literal("Is Period"),
                        font
                )
                .pos(this.minecraft.getWindow().getGuiScaledWidth() - (PADDING + sideWidth)
                        , PADDING + widgetHeight + PADDING)
                .selected(false)
                .onValueChange((checkbox, checked) -> {
                    if(checkbox.selected()) {
                        offTimerEditBox.setMaxLength(Integer.MAX_VALUE);
                        offTimerEditBox.setValue(String.valueOf(60));
                        offTimerEditBox.setHint(Component.literal(String.valueOf(60)));
                        notificationToTriggerEndEditBox.setMaxLength(Integer.MAX_VALUE);
                        chatNotificationToTriggerEndEditBox.setMaxLength(Integer.MAX_VALUE);
                        trackerToTriggerEndEditBox.setMaxLength(Integer.MAX_VALUE);
                    } else {
                        offTimerEditBox.setMaxLength(0);
                        offTimerEditBox.setValue("");
                        offTimerEditBox.setHint(Component.literal(""));

                        notificationToTriggerEndEditBox.setMaxLength(0);
                        notificationToTriggerEndEditBox.setValue("");
                        notificationToTriggerEndEditBox.setHint(Component.literal(""));

                        chatNotificationToTriggerEndEditBox.setMaxLength(0);
                        chatNotificationToTriggerEndEditBox.setValue("");
                        chatNotificationToTriggerEndEditBox.setHint(Component.literal(""));

                        trackerToTriggerEndEditBox.setMaxLength(0);
                        trackerToTriggerEndEditBox.setValue("");
                        trackerToTriggerEndEditBox.setHint(Component.literal(""));
                    }
                })
                .build();
        return isPeriodCheckBox;
    }

    private AbstractWidget getTimerEditBox() {
        timerEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + (widgetHeight + PADDING) * 2,
                this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2 - sideWidth,
                widgetHeight,
                Component.empty()
        );
        timerEditBox.setMaxLength(Integer.MAX_VALUE);

        timerEditBox.setResponder(s -> {
            if(selectedTimerId != null) {
                timerEditBox.setHint(Component.literal(s));
            }
        });

        return timerEditBox;
    }

    private AbstractWidget getOffTimerEditBox() {
        offTimerEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + (widgetHeight + PADDING) * 3,
                this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2 - sideWidth,
                widgetHeight,
                Component.empty()
        );
        offTimerEditBox.setMaxLength(0);

        offTimerEditBox.setResponder(s -> {
            if(selectedTimerId != null) {
                offTimerEditBox.setHint(Component.literal(s));
            }
        });

        return offTimerEditBox;
    }

    private AbstractWidget getOffsetEditBox() {
        offsetEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + (widgetHeight + PADDING) * 4,
                this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2 - sideWidth,
                widgetHeight,
                Component.empty()
        );
        offsetEditBox.setMaxLength(Integer.MAX_VALUE);

        offsetEditBox.setResponder(s -> {
            if(selectedTimerId != null) {
                offsetEditBox.setHint(Component.literal(s));
            }
        });

        return offsetEditBox;
    }

    private AbstractWidget getNotificationToTriggerEditBox() {
        notificationToTriggerEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + (widgetHeight + PADDING) * 5,
                (this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2) / 2 - sideWidth - PADDING,
                widgetHeight,
                Component.empty()
        );
        notificationToTriggerEditBox.setMaxLength(Integer.MAX_VALUE);

        notificationToTriggerEditBox.setResponder(s -> {
            if(selectedTimerId != null) {
                notificationToTriggerEditBox.setHint(Component.literal(s));
            }
        });

        return notificationToTriggerEditBox;
    }

    private AbstractWidget getNotificationToTriggerEndEditBox() {
        notificationToTriggerEndEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth + (this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2) / 2,
                PADDING + (widgetHeight + PADDING) * 5,
                (this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2) / 2 - sideWidth,
                widgetHeight,
                Component.empty()
        );
        notificationToTriggerEndEditBox.setMaxLength(0);

        notificationToTriggerEndEditBox.setResponder(s -> {
            if(selectedTimerId != null) {
                notificationToTriggerEndEditBox.setHint(Component.literal(s));
            }
        });

        return notificationToTriggerEndEditBox;
    }

    private AbstractWidget getChatNotificationToTriggerEditBox() {
        chatNotificationToTriggerEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + (widgetHeight + PADDING) * 6,
                (this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2) / 2 - sideWidth - PADDING,
                widgetHeight,
                Component.empty()
        );
        chatNotificationToTriggerEditBox.setMaxLength(Integer.MAX_VALUE);

        chatNotificationToTriggerEditBox.setResponder(s -> {
            if(selectedTimerId != null) {
                chatNotificationToTriggerEditBox.setHint(Component.literal(s));
            }
        });

        return chatNotificationToTriggerEditBox;
    }

    private AbstractWidget getChatNotificationToTriggerEndEditBox() {
        chatNotificationToTriggerEndEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth + (this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2) / 2,
                PADDING + (widgetHeight + PADDING) * 6,
                (this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2) / 2 - sideWidth,
                widgetHeight,
                Component.empty()
        );
        chatNotificationToTriggerEndEditBox.setMaxLength(0);

        chatNotificationToTriggerEndEditBox.setResponder(s -> {
            if(selectedTimerId != null) {
                chatNotificationToTriggerEndEditBox.setHint(Component.literal(s));
            }
        });

        return chatNotificationToTriggerEndEditBox;
    }

    private AbstractWidget getTrackerToTriggerEditBox() {
        trackerToTriggerEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + (widgetHeight + PADDING) * 7,
                (this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2) / 2 - sideWidth - PADDING,
                widgetHeight,
                Component.empty()
        );
        trackerToTriggerEditBox.setMaxLength(Integer.MAX_VALUE);

        trackerToTriggerEditBox.setResponder(s -> {
            if(selectedTimerId != null) {
                trackerToTriggerEditBox.setHint(Component.literal(s));
            }
        });

        return trackerToTriggerEditBox;
    }

    private AbstractWidget getTrackerToTriggerEndEditBox() {
        trackerToTriggerEndEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth + (this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2) / 2,
                PADDING + (widgetHeight + PADDING) * 7,
                (this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2) / 2 - sideWidth,
                widgetHeight,
                Component.empty()
        );
        trackerToTriggerEndEditBox.setMaxLength(0);

        trackerToTriggerEndEditBox.setResponder(s -> {
            if(selectedTimerId != null) {
                trackerToTriggerEndEditBox.setHint(Component.literal(s));
            }
        });

        return trackerToTriggerEndEditBox;
    }

    private AbstractWidget getCleanUpChatTriggersEditBox() {
        cleanUpChatTriggersEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + (widgetHeight + PADDING) * 8,
                this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2 - sideWidth,
                widgetHeight,
                Component.empty()
        );
        cleanUpChatTriggersEditBox.setMaxLength(Integer.MAX_VALUE);

        cleanUpChatTriggersEditBox.setResponder(s -> {
            if(selectedTimerId != null) {
                cleanUpChatTriggersEditBox.setHint(Component.literal(s));
            }
        });

        return cleanUpChatTriggersEditBox;
    }

    private AbstractWidget getNewButtonElementButton() {
        return new SmallButtonBuilder(
                PADDING_HALF, this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2 - PADDING, BUTTON_HEIGHT,
                button -> {
                    String id = "Custom Timer " + UUID.randomUUID();

                    CustomTimerDataHandler.instance().createNewCustomTimer(id);

                    ButtonListWidget.ButtonEntry buttonEntry = createTimerEntry(id);

                    buttonList.addEntry(buttonEntry);
                    buttonEntryMap.put(id, buttonEntry);
                })
                .icon("New Timer")
                .color(0xFF00CA3A)
                .build();
    }

    private AbstractWidget getDeleteButtonElementButton() {
        return new SmallButtonBuilder(
                PADDING + (BUTTON_WIDTH / 2 - PADDING_HALF), this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2 - PADDING_HALF, BUTTON_HEIGHT,
                button -> {
                    if(selectedTimerId != null) {
                        CustomTimerDataHandler.instance().deleteCustomTimer(selectedTimerId);
                        TimerHandler.instance().initTimers();

                        ButtonListWidget.ButtonEntry entry = buttonEntryMap.get(selectedTimerId);

                        buttonList.removeEntry(entry);
                        buttonEntryMap.remove(selectedTimerId);

                        selectedTimerId = null;
                        resetFields();
                    }
                })
                .icon("Delete Selected")
                .color(0xFFCC0000)
                .build();
    }

    private AbstractWidget getImportButton() {
        return new SmallButtonBuilder(
                PADDING_HALF, this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT * 2 - PADDING_HALF,
                BUTTON_WIDTH / 2 - PADDING, BUTTON_HEIGHT,
                button -> {
                    String rawData = this.minecraft.keyboardHandler.getClipboard().trim();
                    try {
                        String json = TextHelper.decompress(Base64.getDecoder().decode(rawData));

                        Gson gson = new GsonBuilder().registerTypeAdapter(Pattern.class, new PatternAdapter()).create();
                        Quartet<String, CustomTimerDataHandler.CustomTimer, Boolean, Integer> data = gson.fromJson(json, TypeToken.getParameterized(
                                Quartet.class,
                                String.class,
                                CustomTimerDataHandler.CustomTimer.class,
                                Boolean.class,
                                Integer.class).getType()
                        );

                        if(data.value3()) {
                            data = gson.fromJson(json, TypeToken.getParameterized(
                                    Quartet.class,
                                    String.class,
                                    CustomTimerDataHandler.CustomTimer.class,
                                    Boolean.class,
                                    Integer.class).getType()
                            );
                        }

                        if(data.value4() > FishOnMCExtras.TIMER_VERSION) {
                            SystemToast.add(this.minecraft.getToastManager(),
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("FOE+"),
                                    Component.literal("Could not Import. Imported Timer is made on a newer version"));
                            return;
                        }

                        if(CustomTimerDataHandler.instance().getCustomTimerData().timerList.containsKey(data.value1())) {
                            CustomTimerDataHandler.CustomTimer trigger = data.value2();
                            trigger.setName(data.value1() + " (Duplicate)");

                            data = Quartet.of(data.value1() + " (Duplicate)", trigger, data.value3(), data.value4());
                        }

                        String id = data.value1();

                        CustomTimerDataHandler.instance().createNewCustomTimer(data.value1(), data.value2());
                        TimerHandler.instance().initTimers();

                        ButtonListWidget.ButtonEntry buttonEntry = createTimerEntry(id);

                        buttonList.addEntry(buttonEntry);
                        buttonEntryMap.put(id, buttonEntry);

                        SystemToast.add(this.minecraft.getToastManager(),
                                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                Component.literal("FOE+"),
                                Component.literal("Imported Timer"));
                    } catch (Exception e) {
                        LoggerHandler.error(e);

                        SystemToast.add(this.minecraft.getToastManager(),
                                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                Component.literal("FOE+"),
                                Component.literal("Could not Import. Data invalid"));
                    }
                })
                .tooltip(Tooltip.create(Component.literal("Imports from the code on your clipboard")))
                .icon("Import")
                .color(0xFF1E90FF)
                .build();
    }

    private AbstractWidget getExportButton() {
        return new SmallButtonBuilder(
                PADDING + (BUTTON_WIDTH / 2 - PADDING_HALF), this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT * 2 - PADDING_HALF,
                BUTTON_WIDTH / 2 - PADDING_HALF, BUTTON_HEIGHT,
                button -> {
                    if(selectedTimerId != null) {
                        try {
                            Quartet<String, CustomTimerDataHandler.CustomTimer, Boolean, Integer> dataButton = Quartet.of(
                                    selectedTimerId,
                                    selectedTimer,
                                    selectedTimer instanceof CustomTimerDataHandler.CustomTimerPeriod,
                                    FishOnMCExtras.TIMER_VERSION
                            );

                            String rawData = Base64.getEncoder().encodeToString(
                                    TextHelper.compress(new GsonBuilder().registerTypeAdapter(Pattern.class, new PatternAdapter()).create().toJson(dataButton))
                            );

                            String dataToCopy = "**Custom Timer: **" + selectedTimerId + "\n" +
                                    "```\n" +
                                    rawData + "\n" +
                                    "```\n" +
                                    "-# Using Timer version: " + "`v" + FishOnMCExtras.TIMER_VERSION + "`";

                            this.minecraft.keyboardHandler.setClipboard(dataToCopy);

                            SystemToast.add(this.minecraft.getToastManager(),
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("FOE+"),
                                    Component.literal("Exported Button on your clipboard"));
                        } catch (Exception e) {
                            LoggerHandler.error(e);

                            SystemToast.add(this.minecraft.getToastManager(),
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("FOE+"),
                                    Component.literal("An error has occurred"));
                        }
                    }
                })
                .icon("Export Selected")
                .tooltip(Tooltip.create(Component.literal("Save first before exporting")))
                .color(0xFF1E90FF)
                .build();
    }

    private AbstractWidget getButtonList() {
        buttonList = new ButtonListWidget(
                minecraft,
                (BUTTON_WIDTH + PADDING * 2),
                height - ScreenConstants.BUTTON_HEIGHT * 3 - PADDING * 2,
                0,
                BUTTON_HEIGHT + PADDING_HALF,
                BUTTON_HEIGHT,
                "Custom Timers"
        );

        CustomTimerDataHandler.instance().getCustomTimerData().timerList.forEach((name, timer) -> {
            ButtonListWidget.ButtonEntry buttonEntry = createTimerEntry(timer.getName());

            buttonList.addEntry(buttonEntry);
            buttonEntryMap.put(timer.getName(), buttonEntry);
        });

        return buttonList;
    }

    private AbstractWidget saveBackButton() {
        return new SmallButtonBuilder(
                width - PADDING_HALF - BUTTON_WIDTH / 2, height - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2, BUTTON_HEIGHT,
                button -> {
                    if(this.save()) {
                        this.onClose();
                    }
                })
                .icon("Save and Return")
                .color(0xFF00CA3A)
                .build();
    }

    private boolean save() {
        if(selectedTimerId != null) {
            if(nameEditBox.getValue().isBlank()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Timer name is empty"));

                return false;
            }

            if(!Objects.equals(selectedTimerId, nameEditBox.getValue())
                    && CustomTimerDataHandler.instance().getCustomTimerData().timerList.containsKey(nameEditBox.getValue())
            ) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Timer name already exist"));

                return false;
            }

            try {
                Integer.parseInt(timerEditBox.getValue());
            } catch (NumberFormatException e) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Timer is not correct format"));

                LoggerHandler.error(e);

                return false;
            }

            try {
                if(isPeriodCheckBox.selected()) {
                    Integer.parseInt(offTimerEditBox.getValue());
                }
            } catch (NumberFormatException e) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Off Timer is not correct format"));

                LoggerHandler.error(e);

                return false;
            }

            try {
                Integer.parseInt(offsetEditBox.getValue());
            } catch (NumberFormatException e) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Offset is not correct format"));

                LoggerHandler.error(e);

                return false;
            }


            CustomTimerDataHandler.CustomTimer timer;
            if(isPeriodCheckBox.selected()) {
                timer = CustomTimerDataHandler.instance().updateTimer(selectedTimerId,
                        nameEditBox.getValue(),
                        Integer.parseInt(timerEditBox.getValue()),
                        Integer.parseInt(offTimerEditBox.getValue()),
                        Integer.parseInt(offsetEditBox.getValue()),
                        notificationToTriggerEditBox.getValue(),
                        notificationToTriggerEndEditBox.getValue(),
                        chatNotificationToTriggerEditBox.getValue(),
                        chatNotificationToTriggerEndEditBox.getValue(),
                        trackerToTriggerEditBox.getValue(),
                        trackerToTriggerEndEditBox.getValue(),
                        cleanUpChatTriggersEditBox.getValue(),
                        useTimerCheckBox.selected(),
                        isPeriodCheckBox.selected());
            } else {
                timer = CustomTimerDataHandler.instance().updateTimer(selectedTimerId,
                        nameEditBox.getValue(),
                        Integer.parseInt(timerEditBox.getValue()),
                        Integer.parseInt(offsetEditBox.getValue()),
                        notificationToTriggerEditBox.getValue(),
                        chatNotificationToTriggerEditBox.getValue(),
                        trackerToTriggerEditBox.getValue(),
                        cleanUpChatTriggersEditBox.getValue(),
                        useTimerCheckBox.selected(),
                        isPeriodCheckBox.selected());
            }

            ButtonListWidget.ButtonEntry entry = buttonEntryMap.remove(selectedTimerId);
            int index = buttonList.entryAt(entry);
            buttonList.removeEntry(entry);

            selectedTimer = timer;
            selectedTimerId = nameEditBox.getValue();
            this.header = Component.literal(selectedTimerId);

            ButtonListWidget.ButtonEntry buttonEntry = createTimerEntry(selectedTimerId);
            buttonEntryMap.put(selectedTimerId, buttonEntry);
            buttonList.addEntryAtPos(buttonEntry, index);
            buttonList.setSelected(buttonEntry);

            TimerHandler.instance().initTimers();

            return true;
        }
        return false;
    }

    private AbstractWidget saveButton() {
        return new SmallButtonBuilder(
                width - PADDING_HALF - BUTTON_WIDTH / 2 - (PADDING_HALF + BUTTON_WIDTH / 4), height - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 4, BUTTON_HEIGHT,
                button -> {
                    if(this.save()) {
                        SystemToast.add(this.minecraft.getToastManager(),
                                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                Component.literal("Timer saved"),
                                Component.literal(selectedTimerId));
                    }
                })
                .icon("Save")
                .color(0xFF00CA3A)
                .tooltip(Tooltip.create(Component.literal("Can also use Ctrl+S")))
                .build();
    }

    private AbstractWidget backButton() {
        return new SmallButtonBuilder(
                width - PADDING_HALF - BUTTON_WIDTH / 2 - (PADDING_HALF + BUTTON_WIDTH / 4) * 2, height - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 4, BUTTON_HEIGHT,
                button -> this.onClose())
                .icon("Return")
                .color(0xFFCC0000)
                .build();
    }

    private ButtonListWidget.ButtonEntry createTimerEntry(String id) {
        return new ButtonListWidget.ButtonEntry(
                new SmallButtonBuilder(
                        button -> {
                            selectedTimer = CustomTimerDataHandler.instance().getCustomTimerData().timerList.get(id);

                            if(selectedTimer != null) {
                                selectedTimerId = id;
                                this.setFields();
                            }
                        })
                        .icon(id)
                        .style(SmallButtonBuilder.Style.NO_SMALL_CAPS)
                        .build()
        );
    }

    private void setFields() {
        this.header = Component.literal(selectedTimerId);
        nameEditBox.setValue(selectedTimerId);
        nameEditBox.setHint(Component.literal(selectedTimerId));

        if(selectedTimer != null) {
            if(selectedTimer.isUseTimer() != useTimerCheckBox.selected()) {
                useTimerCheckBox.onPress(null);
            }

            if(selectedTimer.isPeriod() != isPeriodCheckBox.selected()) {
                isPeriodCheckBox.onPress(null);
            }

            timerEditBox.setValue(String.valueOf(selectedTimer.getTimer()));
            timerEditBox.setHint(Component.literal(String.valueOf(selectedTimer.getTimer())));

            offsetEditBox.setValue(String.valueOf(selectedTimer.getOffset()));
            offsetEditBox.setHint(Component.literal(String.valueOf(selectedTimer.getOffset())));

            notificationToTriggerEditBox.setValue(selectedTimer.getNotificationToTrigger());
            notificationToTriggerEditBox.setHint(Component.literal(selectedTimer.getNotificationToTrigger()));

            chatNotificationToTriggerEditBox.setValue(selectedTimer.getChatNotificationToTrigger());
            chatNotificationToTriggerEditBox.setHint(Component.literal(selectedTimer.getChatNotificationToTrigger()));

            trackerToTriggerEditBox.setValue(selectedTimer.getTrackerToTrigger());
            trackerToTriggerEditBox.setHint(Component.literal(selectedTimer.getTrackerToTrigger()));

            cleanUpChatTriggersEditBox.setValue(selectedTimer.getCleanUpChatTrigger());
            cleanUpChatTriggersEditBox.setHint(Component.literal(selectedTimer.getCleanUpChatTrigger()));

            CodeExecuterHandler.runLater(1, () -> {
                if(selectedTimer.isPeriod() && selectedTimer instanceof CustomTimerDataHandler.CustomTimerPeriod selectedTimerPeriod) {
                    offTimerEditBox.setMaxLength(Integer.MAX_VALUE);
                    offTimerEditBox.setValue(String.valueOf(selectedTimerPeriod.getOffTimer()));
                    offTimerEditBox.setHint(Component.literal(String.valueOf(selectedTimerPeriod.getOffTimer())));

                    notificationToTriggerEndEditBox.setMaxLength(Integer.MAX_VALUE);
                    notificationToTriggerEndEditBox.setValue(selectedTimerPeriod.getNotificationToTriggerEnd());
                    notificationToTriggerEndEditBox.setHint(Component.literal(selectedTimerPeriod.getNotificationToTriggerEnd()));

                    chatNotificationToTriggerEndEditBox.setMaxLength(Integer.MAX_VALUE);
                    chatNotificationToTriggerEndEditBox.setValue(selectedTimerPeriod.getChatNotificationToTriggerEnd());
                    chatNotificationToTriggerEndEditBox.setHint(Component.literal(selectedTimerPeriod.getChatNotificationToTriggerEnd()));

                    trackerToTriggerEndEditBox.setMaxLength(Integer.MAX_VALUE);
                    trackerToTriggerEndEditBox.setValue(selectedTimerPeriod.getTrackerToTriggerEnd());
                    trackerToTriggerEndEditBox.setHint(Component.literal(selectedTimerPeriod.getTrackerToTriggerEnd()));
                } else {
                    offTimerEditBox.setMaxLength(0);
                    offTimerEditBox.setValue("");
                    offTimerEditBox.setHint(Component.literal(""));

                    notificationToTriggerEndEditBox.setMaxLength(0);
                    notificationToTriggerEndEditBox.setValue("");
                    notificationToTriggerEndEditBox.setHint(Component.literal(""));

                    chatNotificationToTriggerEndEditBox.setMaxLength(0);
                    chatNotificationToTriggerEndEditBox.setValue("");
                    chatNotificationToTriggerEndEditBox.setHint(Component.literal(""));

                    trackerToTriggerEndEditBox.setMaxLength(0);
                    trackerToTriggerEndEditBox.setValue("");
                    trackerToTriggerEndEditBox.setHint(Component.literal(""));
                }
            });
        }
    }

    private void resetFields() {
        this.header = Component.literal("No Timer Selected");

        nameEditBox.setValue("");
        nameEditBox.setHint(Component.literal(""));

        if(useTimerCheckBox.selected()) {
            useTimerCheckBox.onPress(null);
        }

        if(isPeriodCheckBox.selected()) {
            isPeriodCheckBox.onPress(null);
        }

        timerEditBox.setValue("");
        timerEditBox.setHint(Component.literal(""));

        offTimerEditBox.setValue("");
        offTimerEditBox.setHint(Component.literal(""));

        offsetEditBox.setValue("");
        offsetEditBox.setHint(Component.literal(""));

        notificationToTriggerEditBox.setValue("");
        notificationToTriggerEditBox.setHint(Component.literal(""));

        notificationToTriggerEndEditBox.setValue("");
        notificationToTriggerEndEditBox.setHint(Component.literal(""));

        chatNotificationToTriggerEditBox.setValue("");
        chatNotificationToTriggerEditBox.setHint(Component.literal(""));

        chatNotificationToTriggerEndEditBox.setValue("");
        chatNotificationToTriggerEndEditBox.setHint(Component.literal(""));

        trackerToTriggerEditBox.setValue("");
        trackerToTriggerEditBox.setHint(Component.literal(""));

        trackerToTriggerEndEditBox.setValue("");
        trackerToTriggerEndEditBox.setHint(Component.literal(""));

        cleanUpChatTriggersEditBox.setValue("");
        cleanUpChatTriggersEditBox.setHint(Component.literal(""));

        selectedTimer = null;
        selectedTimerId = null;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if(keyEvent.hasControlDown() && keyEvent.key() == GLFW.GLFW_KEY_S) {
            if(this.save()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("Timer saved"),
                        Component.literal(selectedTimerId));
            }
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parentScreen);
    }
    //endregion
}
