package dannypx.foe.screens;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.fetch.ChatHandler;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.handler.store.CustomChatTriggerDataHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.screens.interfaces.ScreenConstants;
import dannypx.foe.screens.widget.ButtonListWidget;
import dannypx.foe.screens.widget.SmallButtonBuilder;
import dannypx.foe.type.tuple.Triplet;
import dannypx.foe.type.type_adapter.PatternAdapter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
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

public class CustomChatTriggerMakerScreen extends Screen implements ScreenConstants {
    //region Fields
    private final Screen parentScreen;

    private ButtonListWidget buttonList;
    private Map<String, ButtonListWidget.ButtonEntry> buttonEntryMap = new HashMap<>();
    private String selectedChatTriggerId;
    private CustomChatTriggerDataHandler.CustomChatTrigger selectedChatTrigger;

    private Component header;
    private final int widgetHeight = 20;

    private EditBox nameEditBox;
    private Checkbox useChatTriggerCheckBox;

    private final int sideWidth = 100;
    private EditBox regexEditBox;
    private EditBox notificationToTriggerEditBox;
    private EditBox chatNotificationToTriggerEditBox;
    private EditBox trackerToTriggerEditBox;
    //endregion

    //region Methods
    public CustomChatTriggerMakerScreen(Screen parent) {
        super(Component.literal("Custom Chat Trigger Maker Screen"));
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
        if(regexEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Regex").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
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
                Component.literal("Regex Filter"),
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
        widgets.add(getUseChatTriggerCheckBox());
        widgets.add(getRegexEditBox());
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
            if(selectedChatTriggerId != null) {
                nameEditBox.setHint(Component.literal(s));
            }
        });

        return nameEditBox;
    }

    private AbstractWidget getUseChatTriggerCheckBox() {
        useChatTriggerCheckBox = Checkbox.builder(
                        Component.literal("Use Trigger"),
                        font
                )
                .pos(this.minecraft.getWindow().getGuiScaledWidth() - PADDING - sideWidth
                        , PADDING + widgetHeight + PADDING)
                .selected(true)
                .onValueChange((checkbox, checked) -> {})
                .build();
        return useChatTriggerCheckBox;
    }

    private AbstractWidget getRegexEditBox() {
        regexEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + (widgetHeight + PADDING) * 2,
                this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2 - sideWidth,
                widgetHeight,
                Component.empty()
        );
        regexEditBox.setMaxLength(Integer.MAX_VALUE);

        regexEditBox.setResponder(s -> {
            if(selectedChatTriggerId != null) {
                regexEditBox.setHint(Component.literal(s));
            }
        });

        return regexEditBox;
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
            if(selectedChatTriggerId != null) {
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
            if(selectedChatTriggerId != null) {
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
            if(selectedChatTriggerId != null) {
                trackerToTriggerEditBox.setHint(Component.literal(s));
            }
        });

        return trackerToTriggerEditBox;
    }

    private AbstractWidget getNewButtonElementButton() {
        return new SmallButtonBuilder(PADDING_HALF, this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2 - PADDING, BUTTON_HEIGHT,
                button -> {
                    String id = "Custom Chat Trigger #" + UUID.randomUUID();

                    CustomChatTriggerDataHandler.instance().createNewCustomChatTrigger(id);

                    ButtonListWidget.ButtonEntry buttonEntry = createChatTriggerEntry(id);

                    buttonList.addEntry(buttonEntry);
                    buttonEntryMap.put(id, buttonEntry);
                })
                .icon("New Chat Trig.")
                .color(0xFF00CA3A)
                .build();
    }

    private AbstractWidget getDeleteButtonElementButton() {
        return new SmallButtonBuilder(PADDING + (BUTTON_WIDTH / 2 - PADDING_HALF), this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2 - PADDING_HALF, BUTTON_HEIGHT,
                button -> {
                    if(selectedChatTriggerId != null) {
                        CustomChatTriggerDataHandler.instance().deleteCustomChatTrigger(selectedChatTriggerId);
                        ChatHandler.instance().initChatTrigger();

                        ButtonListWidget.ButtonEntry entry = buttonEntryMap.get(selectedChatTriggerId);

                        buttonList.removeEntry(entry);
                        buttonEntryMap.remove(selectedChatTriggerId);

                        selectedChatTriggerId = null;
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
                        Triplet<String, CustomChatTriggerDataHandler.CustomChatTrigger, Integer> data = gson.fromJson(json, TypeToken.getParameterized(Triplet.class, String.class, CustomChatTriggerDataHandler.CustomChatTrigger.class, Integer.class).getType());

                        if(data.value3() > FishOnMCExtras.CHAT_TRIGGER_VERSION) {
                            SystemToast.add(this.minecraft.getToastManager(),
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("FOE+"),
                                    Component.literal("Could not Import. Imported Chat Trigger is made on a newer version"));
                            return;
                        }

                        if(CustomChatTriggerDataHandler.instance().getCustomChatTriggerData().chatTriggerList.containsKey(data.value1())) {
                            CustomChatTriggerDataHandler.CustomChatTrigger trigger = data.value2();
                            trigger.setName(data.value1() + " (Duplicate)");

                            data = Triplet.of(data.value1() + " (Duplicate)", trigger, data.value3());
                        }

                        String id = data.value1();

                        CustomChatTriggerDataHandler.instance().createNewCustomChatTrigger(data.value1(), data.value2());
                        ChatHandler.instance().initChatTrigger();

                        ButtonListWidget.ButtonEntry buttonEntry = createChatTriggerEntry(id);

                        buttonList.addEntry(buttonEntry);
                        buttonEntryMap.put(id, buttonEntry);

                        SystemToast.add(this.minecraft.getToastManager(),
                                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                Component.literal("FOE+"),
                                Component.literal("Imported Chat Trigger"));
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
                    if(selectedChatTriggerId != null) {
                        try {
                            Triplet<String, CustomChatTriggerDataHandler.CustomChatTrigger, Integer> dataButton = Triplet.of(
                                    selectedChatTriggerId,
                                    selectedChatTrigger,
                                    FishOnMCExtras.CHAT_TRIGGER_VERSION
                            );

                            String rawData = Base64.getEncoder().encodeToString(
                                    TextHelper.compress(new GsonBuilder().registerTypeAdapter(Pattern.class, new PatternAdapter()).create().toJson(dataButton))
                            );

                            String dataToCopy = "**Custom Chat Trigger: **" + selectedChatTriggerId + "\n" +
                                    "```\n" +
                                    rawData + "\n" +
                                    "```\n" +
                                    "-# Using Chat Trigger version: " + "`v" + FishOnMCExtras.CHAT_TRIGGER_VERSION + "`";

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
                "Custom Chat Triggers"
        );

        CustomChatTriggerDataHandler.instance().getCustomChatTriggerData().chatTriggerList.forEach((name, chatTrigger) -> {
            ButtonListWidget.ButtonEntry buttonEntry = createChatTriggerEntry(chatTrigger.getName());

            buttonList.addEntry(buttonEntry);
            buttonEntryMap.put(chatTrigger.getName(), buttonEntry);
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
        if(selectedChatTriggerId != null) {
            if(nameEditBox.getValue().isBlank()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Chat Trigger name is empty"));

                return false;
            }

            if(!Objects.equals(selectedChatTriggerId, nameEditBox.getValue())
                    && CustomChatTriggerDataHandler.instance().getCustomChatTriggerData().chatTriggerList.containsKey(nameEditBox.getValue())
            ) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Chat Trigger name already exist"));

                return false;
            }

            try {
                Pattern.compile(regexEditBox.getValue());
            } catch (PatternSyntaxException e) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Regex cannot be compiled"));

                LoggerHandler.error(e);

                return false;
            }

            CustomChatTriggerDataHandler.CustomChatTrigger chatTrigger = CustomChatTriggerDataHandler.instance().updateChatTrigger(selectedChatTriggerId,
                    nameEditBox.getValue(),
                    regexEditBox.getValue(),
                    notificationToTriggerEditBox.getValue(),
                    chatNotificationToTriggerEditBox.getValue(),
                    trackerToTriggerEditBox.getValue(),
                    useChatTriggerCheckBox.selected());

            ButtonListWidget.ButtonEntry entry = buttonEntryMap.remove(selectedChatTriggerId);
            int index = buttonList.entryAt(entry);
            buttonList.removeEntry(entry);

            selectedChatTrigger = chatTrigger;
            selectedChatTriggerId = nameEditBox.getValue();
            this.header = Component.literal(selectedChatTriggerId);

            ButtonListWidget.ButtonEntry buttonEntry = createChatTriggerEntry(selectedChatTriggerId);
            buttonEntryMap.put(selectedChatTriggerId, buttonEntry);
            buttonList.addEntryAtPos(buttonEntry, index);
            buttonList.setSelected(buttonEntry);

            ChatHandler.instance().initChatTrigger();

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
                                Component.literal("Chat trigger saved"),
                                Component.literal(selectedChatTriggerId));
                    }
                })
                .icon("Save")
                .color(0xFF00CA3A)
                .tooltip(Tooltip.create(Component.literal("Can also use Ctrl+S")))
                .build();

    }

    private AbstractWidget backButton() {
        return new SmallButtonBuilder(width - PADDING_HALF - BUTTON_WIDTH / 2 - (PADDING_HALF + BUTTON_WIDTH / 4) * 2, height - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 4, BUTTON_HEIGHT,
                buttonm -> this.onClose())
                .icon("Return")
                .color(0xFFCC0000)
                .build();
    }

    private ButtonListWidget.ButtonEntry createChatTriggerEntry(String id) {
        return new ButtonListWidget.ButtonEntry(
                new SmallButtonBuilder(button -> {
                    selectedChatTrigger = CustomChatTriggerDataHandler.instance().getCustomChatTriggerData().chatTriggerList.get(id);

                    if(selectedChatTrigger != null) {
                        selectedChatTriggerId = id;
                        this.setFields();
                    }})
                        .icon(id)
                        .style(SmallButtonBuilder.Style.NO_SMALL_CAPS)
                        .build()
        );
    }

    private void setFields() {
        this.header = Component.literal(selectedChatTriggerId);
        nameEditBox.setValue(selectedChatTriggerId);
        nameEditBox.setHint(Component.literal(selectedChatTriggerId));

        if(selectedChatTrigger != null) {
            if(selectedChatTrigger.isUseChatTrigger() != useChatTriggerCheckBox.selected()) {
                useChatTriggerCheckBox.onPress(null);
            }

            regexEditBox.setValue(selectedChatTrigger.getRegex());
            regexEditBox.setHint(Component.literal(selectedChatTrigger.getRegex()));

            notificationToTriggerEditBox.setValue(selectedChatTrigger.getNotificationToTrigger());
            notificationToTriggerEditBox.setHint(Component.literal(selectedChatTrigger.getNotificationToTrigger()));

            chatNotificationToTriggerEditBox.setValue(selectedChatTrigger.getChatNotificationToTrigger());
            chatNotificationToTriggerEditBox.setHint(Component.literal(selectedChatTrigger.getChatNotificationToTrigger()));

            trackerToTriggerEditBox.setValue(selectedChatTrigger.getTrackerToTrigger());
            trackerToTriggerEditBox.setHint(Component.literal(selectedChatTrigger.getTrackerToTrigger()));
        }
    }

    private void resetFields() {
        this.header = Component.literal("No Chat Trigger Selected");

        nameEditBox.setValue("");
        nameEditBox.setHint(Component.literal(""));

        if(useChatTriggerCheckBox.selected()) {
            useChatTriggerCheckBox.onPress(null);
        }

        regexEditBox.setValue("");
        regexEditBox.setHint(Component.literal(""));

        notificationToTriggerEditBox.setValue("");
        notificationToTriggerEditBox.setHint(Component.literal(""));

        chatNotificationToTriggerEditBox.setValue("");
        chatNotificationToTriggerEditBox.setHint(Component.literal(""));

        trackerToTriggerEditBox.setValue("");
        trackerToTriggerEditBox.setHint(Component.literal(""));

        selectedChatTrigger = null;
        selectedChatTriggerId = null;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if(keyEvent.hasControlDown() && keyEvent.key() == GLFW.GLFW_KEY_S) {
            if(this.save()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("Chat trigger saved"),
                        Component.literal(selectedChatTriggerId));
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
