package dannypx.foe.screens;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.handler.store.CustomEventTriggerDataHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.screens.interfaces.ScreenConstants;
import dannypx.foe.screens.widget.ButtonListWidget;
import dannypx.foe.screens.widget.SmallButtonBuilder;
import dannypx.foe.type.event.EventTrigger;
import dannypx.foe.type.tuple.Triplet;
import dannypx.foe.type.type_adapter.PatternAdapter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.regex.Pattern;

public class CustomEventTriggerMakerScreen extends Screen implements ScreenConstants {
    //region Fields
    private final Screen parentScreen;

    private ButtonListWidget buttonList;
    private Map<String, ButtonListWidget.ButtonEntry> buttonEntryMap = new HashMap<>();
    private String selectedEventTriggerId;
    private CustomEventTriggerDataHandler.CustomEventTrigger selectedEventTrigger;


    private Component header;
    private final int widgetHeight = 20;

    private EditBox nameEditBox;
    private Checkbox useEventTriggerCheckBox;

    private final int sideWidth = 100;
    private EditBox eventEditBox;
    private EditBox notificationToTriggerEditBox;
    private EditBox chatNotificationToTriggerEditBox;
    private EditBox trackerToTriggerEditBox;
    //endregion

    //region Methods
    public CustomEventTriggerMakerScreen(Screen parent) {
        super(Component.literal("Custom Event Trigger Maker Screen"));
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
        if(eventEditBox.isMouseOver(mouseX, mouseY)) {
            List<Component> suggestions = new ArrayList<>(List.of(
                    Component.literal("Event Types").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("Supported Types").withStyle(ChatFormatting.GRAY)
            ));

            for (EventTrigger value : EventTrigger.values()) {
                suggestions.add(Component.literal("- " + value.name()).withStyle(ChatFormatting.YELLOW));
            }

            guiGraphics.setComponentTooltipForNextFrame(font, suggestions, mouseX, mouseY);
        }

        if(notificationToTriggerEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Optional").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    Component.empty(),
                    Component.literal("Notification Name").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("Split multiple notifications with a comma").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        }

        if(chatNotificationToTriggerEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Optional").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    Component.empty(),
                    Component.literal("Chat Notification Name").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("Split multiple chat notifications with a comma").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        }

        if(trackerToTriggerEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Optional").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    Component.empty(),
                    Component.literal("Tracker and Action Name split using a dot").withStyle(ChatFormatting.GRAY),
                    Component.literal("e.g. \"tracker.action\"").withStyle(ChatFormatting.GRAY),
                    Component.empty(),
                    Component.literal("Split multiple trackers with a comma").withStyle(ChatFormatting.GRAY)
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
                Component.literal("Event Type"),
                (BUTTON_WIDTH + PADDING * 2) + PADDING,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 2,
                CommonColors.WHITE,
                true
        );

        guiGraphics.drawString(font,
                Component.literal("Trigger Notif."),
                (BUTTON_WIDTH + PADDING * 2) + PADDING,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 3,
                CommonColors.WHITE,
                true
        );

        guiGraphics.drawString(font,
                Component.literal("Trigger Chat Notif."),
                (BUTTON_WIDTH + PADDING * 2) + PADDING,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 4,
                CommonColors.WHITE,
                true
        );

        guiGraphics.drawString(font,
                Component.literal("Trigger Tracker"),
                (BUTTON_WIDTH + PADDING * 2) + PADDING,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 5,
                CommonColors.WHITE,
                true
        );
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
        widgets.add(getUseEventTriggerCheckBox());
        widgets.add(getEventEditBox());
        widgets.add(getNotificationToTriggerEditBox());
        widgets.add(getChatNotificationToTriggerEditBox());
        widgets.add(getTrackerToTriggerEditBox());

        widgets.forEach(this::addRenderableWidget);
    }

    private AbstractWidget getNameEditBox() {
        nameEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + widgetHeight + PADDING,
                this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2 - (sideWidth + PADDING) - sideWidth,
                widgetHeight,
                Component.empty()
        );
        nameEditBox.setMaxLength(Integer.MAX_VALUE);

        nameEditBox.setResponder(s -> {
            if(selectedEventTriggerId != null) {
                nameEditBox.setHint(Component.literal(s));
            }
        });

        return nameEditBox;
    }

    private AbstractWidget getUseEventTriggerCheckBox() {
        useEventTriggerCheckBox = Checkbox.builder(
                        Component.literal("Use Trigger"),
                        font
                )
                .pos(this.minecraft.getWindow().getGuiScaledWidth() - PADDING - sideWidth
                        , PADDING + widgetHeight + PADDING)
                .selected(true)
                .onValueChange((checkbox, checked) -> {})
                .build();
        return useEventTriggerCheckBox;
    }

    private AbstractWidget getEventEditBox() {
        eventEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + (widgetHeight + PADDING) * 2,
                this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2 - sideWidth,
                widgetHeight,
                Component.empty()
        );
        eventEditBox.setMaxLength(Integer.MAX_VALUE);

        eventEditBox.setResponder(s -> {
            if(selectedEventTriggerId != null) {
                eventEditBox.setHint(Component.literal(s));

                if (s.isEmpty()) {
                    eventEditBox.setSuggestion(null);
                    return;
                }

                for (String event : Arrays.stream(EventTrigger.values()).map(Enum::name).toList()) {
                    if (event.toLowerCase().startsWith(s.toLowerCase()) &&
                            !event.equalsIgnoreCase(s)) {

                        eventEditBox.setSuggestion(
                                event.substring(s.length())
                        );
                        return;
                    }
                }

                eventEditBox.setSuggestion(null);
            }
        });

        return eventEditBox;
    }

    private AbstractWidget getNotificationToTriggerEditBox() {
        notificationToTriggerEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + (widgetHeight + PADDING) * 3,
                this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2 - sideWidth,
                widgetHeight,
                Component.empty()
        );
        notificationToTriggerEditBox.setMaxLength(Integer.MAX_VALUE);

        notificationToTriggerEditBox.setResponder(s -> {
            if(selectedEventTriggerId != null) {
                notificationToTriggerEditBox.setHint(Component.literal(s));
            }
        });

        return notificationToTriggerEditBox;
    }

    private AbstractWidget getChatNotificationToTriggerEditBox() {
        chatNotificationToTriggerEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + (widgetHeight + PADDING) * 4,
                this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2 - sideWidth,
                widgetHeight,
                Component.empty()
        );
        chatNotificationToTriggerEditBox.setMaxLength(Integer.MAX_VALUE);

        chatNotificationToTriggerEditBox.setResponder(s -> {
            if(selectedEventTriggerId != null) {
                chatNotificationToTriggerEditBox.setHint(Component.literal(s));
            }
        });

        return chatNotificationToTriggerEditBox;
    }

    private AbstractWidget getTrackerToTriggerEditBox() {
        trackerToTriggerEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + (widgetHeight + PADDING) * 5,
                this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2 - sideWidth,
                widgetHeight,
                Component.empty()
        );
        trackerToTriggerEditBox.setMaxLength(Integer.MAX_VALUE);

        trackerToTriggerEditBox.setResponder(s -> {
            if(selectedEventTriggerId != null) {
                trackerToTriggerEditBox.setHint(Component.literal(s));
            }
        });

        return trackerToTriggerEditBox;
    }

    private AbstractWidget getNewButtonElementButton() {
        return new SmallButtonBuilder(PADDING_HALF, this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2 - PADDING, BUTTON_HEIGHT,
                button -> {
                    String id = "Custom Event Trigger #" + UUID.randomUUID();

                    CustomEventTriggerDataHandler.instance().createNewCustomEventTrigger(id);

                    ButtonListWidget.ButtonEntry buttonEntry = createEventTriggerEntry(id);

                    buttonList.addEntry(buttonEntry);
                    buttonEntryMap.put(id, buttonEntry);
                })
                .icon("New Event Trig.")
                .color(0xFF00CA3A)
                .build();
    }

    private AbstractWidget getDeleteButtonElementButton() {
        return new SmallButtonBuilder(PADDING + (BUTTON_WIDTH / 2 - PADDING_HALF), this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2 - PADDING_HALF, BUTTON_HEIGHT,
                button -> {
                    if(selectedEventTriggerId != null) {
                        CustomEventTriggerDataHandler.instance().deleteCustomEventTrigger(selectedEventTriggerId);

                        ButtonListWidget.ButtonEntry entry = buttonEntryMap.get(selectedEventTriggerId);

                        buttonList.removeEntry(entry);
                        buttonEntryMap.remove(selectedEventTriggerId);

                        selectedEventTriggerId = null;
                        resetFields();
                    }
                })
                .icon("Delete Selected")
                .color(0xFFCC0000)
                .build();
    }

    private AbstractWidget getImportButton() {
        return new SmallButtonBuilder(PADDING_HALF, this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT * 2 - PADDING_HALF,
                BUTTON_WIDTH / 2 - PADDING, BUTTON_HEIGHT,
                button -> {
                    String rawData = this.minecraft.keyboardHandler.getClipboard().trim();
                    try {
                        String json = TextHelper.decompress(Base64.getDecoder().decode(rawData));

                        Gson gson = new GsonBuilder().registerTypeAdapter(Pattern.class, new PatternAdapter()).create();
                        Triplet<String, CustomEventTriggerDataHandler.CustomEventTrigger, Integer> data = gson.fromJson(json, TypeToken.getParameterized(Triplet.class, String.class, CustomEventTriggerDataHandler.CustomEventTrigger.class, Integer.class).getType());

                        if(data.value3() > FishOnMCExtras.EVENT_TRIGGER_VERSION) {
                            SystemToast.add(this.minecraft.getToastManager(),
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("FOE+"),
                                    Component.literal("Could not Import. Imported Event Trigger is made on a newer version"));
                            return;
                        }

                        if(CustomEventTriggerDataHandler.instance().getCustomEventTriggerData().eventTriggerList.containsKey(data.value1())) {
                            CustomEventTriggerDataHandler.CustomEventTrigger trigger = data.value2();
                            trigger.setName(data.value1() + " (Duplicate)");

                            data = Triplet.of(data.value1() + " (Duplicate)", trigger, data.value3());
                        }

                        String id = data.value1();

                        CustomEventTriggerDataHandler.instance().createNewCustomEventTrigger(data.value1(), data.value2());

                        ButtonListWidget.ButtonEntry buttonEntry = createEventTriggerEntry(id);

                        buttonList.addEntry(buttonEntry);
                        buttonEntryMap.put(id, buttonEntry);

                        SystemToast.add(this.minecraft.getToastManager(),
                                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                Component.literal("FOE+"),
                                Component.literal("Imported Event Trigger"));
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
        return new SmallButtonBuilder(PADDING + (BUTTON_WIDTH / 2 - PADDING_HALF), this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT * 2 - PADDING_HALF,
                BUTTON_WIDTH / 2 - PADDING_HALF, BUTTON_HEIGHT,
                button -> {
                    if(selectedEventTriggerId != null) {
                        try {
                            Triplet<String, CustomEventTriggerDataHandler.CustomEventTrigger, Integer> dataButton = Triplet.of(
                                    selectedEventTriggerId,
                                    selectedEventTrigger,
                                    FishOnMCExtras.EVENT_TRIGGER_VERSION
                            );

                            String rawData = Base64.getEncoder().encodeToString(
                                    TextHelper.compress(new GsonBuilder().registerTypeAdapter(Pattern.class, new PatternAdapter()).create().toJson(dataButton))
                            );

                            String dataToCopy = "**Custom Event Trigger: **" + selectedEventTriggerId + "\n" +
                                    "```\n" +
                                    rawData + "\n" +
                                    "```\n" +
                                    "-# Using Event Trigger version: " + "`v" + FishOnMCExtras.EVENT_TRIGGER_VERSION + "`";

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
                this.minecraft,
                (BUTTON_WIDTH + PADDING * 2),
                height - ScreenConstants.BUTTON_HEIGHT * 3 - PADDING * 2,
                0,
                BUTTON_HEIGHT + PADDING_HALF,
                BUTTON_HEIGHT,
                "Custom Event Triggers"
        );

        CustomEventTriggerDataHandler.instance().getCustomEventTriggerData().eventTriggerList.forEach((name, eventTrigger) -> {
            ButtonListWidget.ButtonEntry buttonEntry = createEventTriggerEntry(eventTrigger.getName());

            buttonList.addEntry(buttonEntry);
            buttonEntryMap.put(eventTrigger.getName(), buttonEntry);
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
        if(selectedEventTriggerId != null) {
            if(nameEditBox.getValue().isBlank()) {
                SystemToast.add(minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Event Trigger name is empty"));

                return false;
            }

            if(!Objects.equals(selectedEventTriggerId, nameEditBox.getValue())
                    && CustomEventTriggerDataHandler.instance().getCustomEventTriggerData().eventTriggerList.containsKey(nameEditBox.getValue())
            ) {
                SystemToast.add(minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Event Trigger name already exist"));

                return false;
            }

            if(Arrays.stream(EventTrigger.values()).noneMatch(eventTrigger ->
                    eventTrigger.name().equals(eventEditBox.getValue())
            )) {
                SystemToast.add(minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Event does not exist"));

                return false;
            }

            CustomEventTriggerDataHandler.CustomEventTrigger eventTrigger = CustomEventTriggerDataHandler.instance().updateEventTrigger(selectedEventTriggerId,
                    nameEditBox.getValue(),
                    EventTrigger.valueOf(eventEditBox.getValue()),
                    notificationToTriggerEditBox.getValue(),
                    chatNotificationToTriggerEditBox.getValue(),
                    trackerToTriggerEditBox.getValue(),
                    useEventTriggerCheckBox.selected());

            ButtonListWidget.ButtonEntry entry = buttonEntryMap.remove(selectedEventTriggerId);
            int index = buttonList.entryAt(entry);
            buttonList.removeEntry(entry);

            selectedEventTrigger = eventTrigger;
            selectedEventTriggerId = nameEditBox.getValue();
            this.header = Component.literal(selectedEventTriggerId);

            ButtonListWidget.ButtonEntry buttonEntry = createEventTriggerEntry(selectedEventTriggerId);
            buttonEntryMap.put(selectedEventTriggerId, buttonEntry);
            buttonList.addEntryAtPos(buttonEntry, index);
            buttonList.setSelected(buttonEntry);

            return true;
        }
        return false;
    }

    private AbstractWidget saveButton() {
        return new SmallButtonBuilder(width - PADDING_HALF - BUTTON_WIDTH / 2 - (PADDING_HALF + BUTTON_WIDTH / 4), height - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 4, BUTTON_HEIGHT,
                button -> {
                    if(this.save()) {
                        SystemToast.add(this.minecraft.getToastManager(),
                                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                Component.literal("Event trigger saved"),
                                Component.literal(selectedEventTriggerId));
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

    private ButtonListWidget.ButtonEntry createEventTriggerEntry(String id) {
        return new ButtonListWidget.ButtonEntry(
                new SmallButtonBuilder(button -> {
                    selectedEventTrigger = CustomEventTriggerDataHandler.instance().getCustomEventTriggerData().eventTriggerList.get(id);

                    if(selectedEventTrigger != null) {
                        selectedEventTriggerId = id;
                        this.setFields();
                    }})
                        .icon(id)
                        .style(SmallButtonBuilder.Style.NO_SMALL_CAPS)
                        .build()
        );
    }

    private void setFields() {
        this.header = Component.literal(selectedEventTriggerId);
        nameEditBox.setValue(selectedEventTriggerId);
        nameEditBox.setHint(Component.literal(selectedEventTriggerId));

        if(selectedEventTrigger != null) {
            if(selectedEventTrigger.isUseEventTrigger() != useEventTriggerCheckBox.selected()) {
                useEventTriggerCheckBox.onPress(null);
            }

            eventEditBox.setValue(selectedEventTrigger.getEvent().name());
            eventEditBox.setHint(Component.literal(selectedEventTrigger.getEvent().name()));

            notificationToTriggerEditBox.setValue(selectedEventTrigger.getNotificationToTrigger());
            notificationToTriggerEditBox.setHint(Component.literal(selectedEventTrigger.getNotificationToTrigger()));

            chatNotificationToTriggerEditBox.setValue(selectedEventTrigger.getChatNotificationToTrigger());
            chatNotificationToTriggerEditBox.setHint(Component.literal(selectedEventTrigger.getChatNotificationToTrigger()));

            trackerToTriggerEditBox.setValue(selectedEventTrigger.getTrackerToTrigger());
            trackerToTriggerEditBox.setHint(Component.literal(selectedEventTrigger.getTrackerToTrigger()));
        }
    }

    private void resetFields() {
        this.header = Component.literal("No Chat Trigger Selected");

        nameEditBox.setValue("");
        nameEditBox.setHint(Component.literal(""));

        if(useEventTriggerCheckBox.selected()) {
            useEventTriggerCheckBox.onPress(null);
        }

        eventEditBox.setValue("");
        eventEditBox.setHint(Component.literal(""));

        notificationToTriggerEditBox.setValue("");
        notificationToTriggerEditBox.setHint(Component.literal(""));

        chatNotificationToTriggerEditBox.setValue("");
        chatNotificationToTriggerEditBox.setHint(Component.literal(""));

        trackerToTriggerEditBox.setValue("");
        trackerToTriggerEditBox.setHint(Component.literal(""));

        selectedEventTrigger = null;
        selectedEventTriggerId = null;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parentScreen);
    }


    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == GLFW.GLFW_KEY_TAB) {
            String current = eventEditBox.getValue();

            for (String event : Arrays.stream(EventTrigger.values()).map(Enum::name).toList()) {
                if (event.toLowerCase().startsWith(current.toLowerCase())) {
                    eventEditBox.setValue(event);
                    eventEditBox.setSuggestion(null);
                    return true;
                }
            }
        }

        if(keyEvent.hasControlDown() && keyEvent.key() == GLFW.GLFW_KEY_S) {
            if(this.save()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("Event trigger saved"),
                        Component.literal(selectedEventTriggerId));
            }
            return true;
        }

        return super.keyPressed(keyEvent);
    }
    //endregion
}
