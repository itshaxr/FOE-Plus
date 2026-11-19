package dannypx.foe.screens;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.handler.store.CustomChatNotificationDataHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.placeholder.editbox.PlaceholderEditBox;
import dannypx.foe.screens.interfaces.ScreenConstants;
import dannypx.foe.screens.widget.ButtonListWidget;
import dannypx.foe.screens.widget.SmallButtonBuilder;
import dannypx.foe.type.tuple.Triplet;
import dannypx.foe.type.type_adapter.PatternAdapter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.regex.Pattern;

public class CustomChatNotificationMakerScreen extends Screen implements ScreenConstants {
    //region Fields
    private final Screen parentScreen;

    private ButtonListWidget buttonList;
    private Map<String, ButtonListWidget.ButtonEntry> buttonEntryMap = new HashMap<>();
    private String selectedChatNotificationId;

    private Component header;
    private final int widgetHeight = 20;

    private EditBox nameEditBox;

    private final int sideWidth = 100;
    private PlaceholderEditBox stringEditBox;
    private String stringField;
    //endregion

    //region Methods
    public CustomChatNotificationMakerScreen(Screen parent) {
        super(Component.literal("Custom Chat Notification Maker Screen"));
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
        if(stringEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("You can also use placeholders. See wiki")
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
                Component.literal("Notif. Text"),
                (BUTTON_WIDTH + PADDING * 2) + PADDING,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 2,
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
        widgets.add(getStringEditBox());

        widgets.add(this.wikiButton());

        widgets.forEach(this::addRenderableWidget);
    }

    private AbstractWidget getNameEditBox() {
        nameEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + widgetHeight + PADDING,
                this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2  - sideWidth,
                widgetHeight,
                Component.empty()
        );
        nameEditBox.setMaxLength(Integer.MAX_VALUE);

        nameEditBox.setResponder(s -> {
            if(selectedChatNotificationId != null) {
                nameEditBox.setHint(Component.literal(s));
            }
        });

        return nameEditBox;
    }

    private AbstractWidget getStringEditBox() {
        stringEditBox = new PlaceholderEditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + (widgetHeight + PADDING) * 2,
                this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2 - sideWidth,
                widgetHeight,
                Component.empty()
        );
        stringEditBox.setMaxLength(Integer.MAX_VALUE);

        stringEditBox.setResponder(s -> {
            if(selectedChatNotificationId != null) {
                stringField = s;
                stringEditBox.setHint(Component.literal(s));
            }
        });

        return stringEditBox;
    }

    private AbstractWidget getNewButtonElementButton() {
        return new SmallButtonBuilder(PADDING_HALF, this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2 - PADDING, BUTTON_HEIGHT,
                button -> {
                    String id = "Custom Chat Notification #" + UUID.randomUUID();

                    CustomChatNotificationDataHandler.instance().createNewChatCustomNotification(id);

                    ButtonListWidget.ButtonEntry buttonEntry = createChatNotificationEntry(id);

                    buttonList.addEntry(buttonEntry);
                    buttonEntryMap.put(id, buttonEntry);
                })
                .icon("New Chat Notif.")
                .color(0xFF00CA3A)
                .build();
    }

    private AbstractWidget getDeleteButtonElementButton() {
        return new SmallButtonBuilder(
                PADDING + (BUTTON_WIDTH / 2 - PADDING_HALF), this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2 - PADDING_HALF, BUTTON_HEIGHT,
                button -> {
                    if(selectedChatNotificationId != null) {
                        CustomChatNotificationDataHandler.instance().deleteCustomChatNotification(selectedChatNotificationId);

                        ButtonListWidget.ButtonEntry entry = buttonEntryMap.get(selectedChatNotificationId);

                        buttonList.removeEntry(entry);
                        buttonEntryMap.remove(selectedChatNotificationId);

                        selectedChatNotificationId = null;
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

                        Gson gson = new GsonBuilder().create();
                        Triplet<String, String, Integer> data = gson.fromJson(json, TypeToken.getParameterized(Triplet.class, String.class, String.class, Integer.class).getType());

                        if(data.value3() > FishOnMCExtras.CHAT_NOTIFICATION_VERSION) {
                            SystemToast.add(this.minecraft.getToastManager(),
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("FOE+"),
                                    Component.literal("Could not Import. Imported Chat Notification is made on a newer version"));
                            return;
                        }

                        if(CustomChatNotificationDataHandler.instance().getCustomChatNotificationData().notificationList.containsKey(data.value1())) {
                            String notification = data.value1() + " (Duplicate)";

                            data = Triplet.of(data.value1() + " (Duplicate)", notification, data.value3());
                        }

                        String id = data.value1();

                        CustomChatNotificationDataHandler.instance().createNewChatCustomNotification(data.value1(), data.value2());

                        ButtonListWidget.ButtonEntry buttonEntry = createChatNotificationEntry(id);

                        buttonList.addEntry(buttonEntry);
                        buttonEntryMap.put(id, buttonEntry);

                        SystemToast.add(this.minecraft.getToastManager(),
                                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                Component.literal("FOE+"),
                                Component.literal("Imported Chat Notification"));
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
                    if(selectedChatNotificationId != null) {
                        try {
                            Triplet<String, String, Integer> dataButton = Triplet.of(
                                    selectedChatNotificationId,
                                    stringEditBox.getValue(),
                                    FishOnMCExtras.CHAT_NOTIFICATION_VERSION
                            );

                            String rawData = Base64.getEncoder().encodeToString(
                                    TextHelper.compress(new GsonBuilder().registerTypeAdapter(Pattern.class, new PatternAdapter()).create().toJson(dataButton))
                            );

                            String dataToCopy = "**Custom Chat Notification: **" + selectedChatNotificationId + "\n" +
                                    "```\n" +
                                    rawData + "\n" +
                                    "```\n" +
                                    "-# Using Chat Notification version: " + "`v" + FishOnMCExtras.CHAT_NOTIFICATION_VERSION + "`";

                            this.minecraft.keyboardHandler.setClipboard(dataToCopy);

                            SystemToast.add(this.minecraft.getToastManager(),
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("FOE+"),
                                    Component.literal("Exported Chat Notification on your clipboard"));
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
                "Custom Chat Notification"
        );

        CustomChatNotificationDataHandler.instance().getCustomChatNotificationData().notificationList.forEach((name, text) -> {
            ButtonListWidget.ButtonEntry buttonEntry = createChatNotificationEntry(name);

            buttonList.addEntry(buttonEntry);
            buttonEntryMap.put(name, buttonEntry);
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
        if(selectedChatNotificationId != null) {
            if(nameEditBox.getValue().isBlank()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Chat Notification name is empty"));

                return false;
            }

            if(!Objects.equals(selectedChatNotificationId, nameEditBox.getValue())
                    && CustomChatNotificationDataHandler.instance().getCustomChatNotificationData().notificationList.containsKey(nameEditBox.getValue())
            ) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Chat Notification name already exist"));

                return false;
            }

            CustomChatNotificationDataHandler.instance().updateChatNotification(selectedChatNotificationId, nameEditBox.getValue(), stringEditBox.getValue());

            ButtonListWidget.ButtonEntry entry = buttonEntryMap.remove(selectedChatNotificationId);
            int index = buttonList.entryAt(entry);
            buttonList.removeEntry(entry);

            selectedChatNotificationId = nameEditBox.getValue();
            this.header = Component.literal(selectedChatNotificationId);

            ButtonListWidget.ButtonEntry buttonEntry = createChatNotificationEntry(selectedChatNotificationId);
            buttonEntryMap.put(selectedChatNotificationId, buttonEntry);
            buttonList.addEntryAtPos(buttonEntry, index);
            buttonList.setSelected(buttonEntry);

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
                                Component.literal("Chat notification saved"),
                                Component.literal(selectedChatNotificationId));
                    }
                })
                .icon("Save")
                .color(0xFFCC0000)
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

    private AbstractWidget wikiButton() {
        return new SmallButtonBuilder(PADDING_HALF + (BUTTON_WIDTH + PADDING * 2), height - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 4, BUTTON_HEIGHT,
                button -> {
                    String url = Configs.mainConfig.wikiUrl.get();

                    this.minecraft.setScreen(new ConfirmLinkScreen((confirmed) -> {
                        if (confirmed) {
                            Util.getPlatform().openUri(url);
                        }

                        this.minecraft.setScreen(null);
                    }, url, true));
                })
                .icon("Wiki")
                .tooltip(Tooltip.create(Component.literal("Open Wiki to Placeholders")))
                .build();
    }

    private ButtonListWidget.ButtonEntry createChatNotificationEntry(String id) {
        return new ButtonListWidget.ButtonEntry(
                new SmallButtonBuilder(
                        button -> {
                            selectedChatNotificationId = id;
                            stringField = CustomChatNotificationDataHandler.instance().getCustomChatNotificationData().notificationList.get(id);
                            this.setFields();
                        })
                        .icon(id)
                        .style(SmallButtonBuilder.Style.NO_SMALL_CAPS)
                        .build()
        );
    }

    private void setFields() {
        this.header = Component.literal(selectedChatNotificationId);
        nameEditBox.setValue(selectedChatNotificationId);
        nameEditBox.setHint(Component.literal(selectedChatNotificationId));

        if(selectedChatNotificationId != null) {
            stringEditBox.setValue(stringField);
            stringEditBox.setHint(Component.literal(stringField));
        }
    }

    private void resetFields() {
        this.header = Component.literal("No Chat Notification Selected");

        nameEditBox.setValue("");
        nameEditBox.setHint(Component.literal(""));


        stringEditBox.setValue("");
        stringEditBox.setHint(Component.literal(""));


        stringField = "";
        selectedChatNotificationId = null;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if(keyEvent.hasControlDown() && keyEvent.key() == GLFW.GLFW_KEY_S) {
            if(this.save()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("Chat notification saved"),
                        Component.literal(selectedChatNotificationId));
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
