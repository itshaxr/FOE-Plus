package dannypx.foe.screens;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.handler.store.CustomNotificationDataHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.screens.widget.SmallButtonBuilder;
import dannypx.foe.type.tuple.Triplet;
import dannypx.foe.screens.interfaces.ScreenConstants;
import dannypx.foe.screens.widget.ButtonListWidget;
import dannypx.foe.screens.widget.EditCustomNotificationWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.regex.Pattern;

public class CustomNotificationMakerScreen extends Screen implements ScreenConstants {
    //region Fields
    private final Screen parentScreen;

    private ButtonListWidget buttonList;
    private EditCustomNotificationWidget editCustomNotificationWidget;
    private Map<String, ButtonListWidget.ButtonEntry> buttonEntryMap = new HashMap<>();
    private String selectedNotificationId;
    //endregion

    //region Methods
    public CustomNotificationMakerScreen(Screen parent) {
        super(Component.literal("Custom Notification Maker Screen"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.renderWidgets();
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);
        this.buttonList.render(guiGraphics, mouseX, mouseY, delta);
        this.editCustomNotificationWidget.render(guiGraphics, mouseX, mouseY, delta);
    }

    private void renderWidgets() {
        List<AbstractWidget> widgets = new ArrayList<>();

        widgets.add(this.saveBackButton());
        widgets.add(this.saveButton());
        widgets.add(this.backButton());
        widgets.add(this.addLine());

        widgets.add(getEditNotificationWidget());
        widgets.add(getButtonList());

        widgets.add(getNewNotificationElementButton());
        widgets.add(getDeleteNotificationElementButton());
        widgets.add(getImportButton());
        widgets.add(getExportButton());

        widgets.add(this.wikiButton());

        widgets.forEach(this::addRenderableWidget);
    }

    private AbstractWidget getEditNotificationWidget() {
        editCustomNotificationWidget = new EditCustomNotificationWidget(
                (BUTTON_WIDTH + PADDING * 2),
                0,
                width - (BUTTON_WIDTH + PADDING * 2),
                height - (BUTTON_HEIGHT + PADDING_HALF) - 3,
                Component.literal("No Notification Selected")
        );

        return editCustomNotificationWidget;
    }

    private AbstractWidget getNewNotificationElementButton() {
        return new SmallButtonBuilder(PADDING_HALF, this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2 - PADDING, BUTTON_HEIGHT,
                button -> {
                    String id = "Custom Notification #" + UUID.randomUUID();

                    CustomNotificationDataHandler.instance().createNewCustomNotification(id);

                    ButtonListWidget.ButtonEntry buttonEntry = createNotificationEntry(id);

                    buttonList.addEntry(buttonEntry);
                    buttonEntryMap.put(id, buttonEntry);
                })
                .icon("New Notif.")
                .color(0xFF00CA3A)
                .build();
    }

    private AbstractWidget getDeleteNotificationElementButton() {
        return new SmallButtonBuilder(PADDING + (BUTTON_WIDTH / 2 - PADDING_HALF), this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2 - PADDING_HALF, BUTTON_HEIGHT,
                button -> {
                    if(editCustomNotificationWidget.hasSelectedOption) {
                        CustomNotificationDataHandler.instance().deleteCustomNotification(selectedNotificationId);
                        editCustomNotificationWidget.reset();
                        ButtonListWidget.ButtonEntry entry = buttonEntryMap.get(selectedNotificationId);

                        buttonList.removeEntry(entry);
                        buttonEntryMap.remove(selectedNotificationId);

                        selectedNotificationId = null;
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
                        Triplet<String, CustomNotificationDataHandler.CustomNotification, Integer> data = gson.fromJson(json, TypeToken.getParameterized(Triplet.class, String.class, CustomNotificationDataHandler.CustomNotification.class, Integer.class).getType());

                        if(data.value3() > FishOnMCExtras.NOTIFICATION_VERSION) {
                            SystemToast.add(this.minecraft.getToastManager(),
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("FOE+"),
                                    Component.literal("Could not Import. Imported Notification is made on a newer version"));

                            return;
                        }

                        if(CustomNotificationDataHandler.instance().getCustomNotificationData().notificationList.containsKey(data.value1())) {
                            data = Triplet.of(data.value1() + " (Duplicate)", data.value2(), data.value3());
                        }

                        String id = data.value1();

                        CustomNotificationDataHandler.instance().createNewCustomNotification(id, data.value2());

                        ButtonListWidget.ButtonEntry buttonEntry = createNotificationEntry(id);

                        buttonList.addEntry(buttonEntry);
                        buttonEntryMap.put(id, buttonEntry);

                        SystemToast.add(this.minecraft.getToastManager(),
                                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                Component.literal("FOE+"),
                                Component.literal("Imported Notification"));

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
                    if(editCustomNotificationWidget.hasSelectedOption) {
                        try {
                            Triplet<String, CustomNotificationDataHandler.CustomNotification, Integer> dataNotification = Triplet.of(
                                    editCustomNotificationWidget.currentSelectedNotification,
                                    CustomNotificationDataHandler.instance().getCustomNotificationData().notificationList.get(editCustomNotificationWidget.currentSelectedNotification),
                                    FishOnMCExtras.NOTIFICATION_VERSION
                            );
                            String rawData = Base64.getEncoder().encodeToString(
                                    TextHelper.compress(new GsonBuilder().create().toJson(dataNotification))
                            );

                            String dataToCopy = "**Custom Notification: **" + selectedNotificationId + "\n" +
                                    "```\n" +
                                    rawData + "\n" +
                                    "```\n" +
                                    "-# Using Notification version: " + "`v" + FishOnMCExtras.NOTIFICATION_VERSION + "`";

                            this.minecraft.keyboardHandler.setClipboard(dataToCopy);

                            SystemToast.add(this.minecraft.getToastManager(),
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("FOE+"),
                                    Component.literal("Exported Notification on your clipboard"));
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
                "Custom Notifications"
        );

        CustomNotificationDataHandler.instance().getCustomNotificationData().notificationList.forEach((id, ignored) -> {
            ButtonListWidget.ButtonEntry buttonEntry = createNotificationEntry(id);

            buttonList.addEntry(buttonEntry);
            buttonEntryMap.put(id, buttonEntry);
        });

        return buttonList;
    }

    private ButtonListWidget.ButtonEntry createNotificationEntry(String id) {
        return new ButtonListWidget.ButtonEntry(
                new SmallButtonBuilder(
                        button -> {
                            selectedNotificationId = id;
                            editCustomNotificationWidget.selectNotification(
                                    id,
                                    CustomNotificationDataHandler.instance().getCustomNotificationData().notificationList.get(id));
                        })
                        .icon(id)
                        .style(SmallButtonBuilder.Style.NO_SMALL_CAPS)
                        .build()
        );
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
        if(editCustomNotificationWidget.hasSelectedOption) {
            if(editCustomNotificationWidget.newName.isBlank()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Notification name is empty"));

                return false;
            }

            if(
                    !Objects.equals(editCustomNotificationWidget.currentSelectedNotification, editCustomNotificationWidget.newName)
                            && CustomNotificationDataHandler.instance().getCustomNotificationData().notificationList.containsKey(editCustomNotificationWidget.newName)
            ) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Notification name already exist"));

                return false;
            }


            Pattern iconPattern = Pattern.compile("^(?:([a-z0-9_]+:[a-z0-9_]+)(?:\\[(.*)\\])?)?$");
            if(!iconPattern.matcher(editCustomNotificationWidget.icon).matches()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Icon is wrong format"));

                return false;
            }

            CustomNotificationDataHandler.instance().updateNotification(
                    editCustomNotificationWidget.currentSelectedNotification,
                    editCustomNotificationWidget.newName,
                    editCustomNotificationWidget.icon,
                    editCustomNotificationWidget.getEntries()
                            .stream()
                            .map(lineEntry -> lineEntry.lineString)
                            .toList()
            );

            ButtonListWidget.ButtonEntry entry = buttonEntryMap.remove(selectedNotificationId);
            int index = buttonList.entryAt(entry);
            buttonList.removeEntry(entry);

            selectedNotificationId = editCustomNotificationWidget.newName;

            ButtonListWidget.ButtonEntry buttonEntry = createNotificationEntry(selectedNotificationId);
            buttonEntryMap.put(selectedNotificationId, buttonEntry);
            buttonList.addEntryAtPos(buttonEntry, index);
            buttonList.setSelected(buttonEntry);

            editCustomNotificationWidget.selectNotification(selectedNotificationId, CustomNotificationDataHandler.instance().getCustomNotificationData().notificationList.get(selectedNotificationId));

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
                                Component.literal("Notification saved"),
                                Component.literal(selectedNotificationId));
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

    private AbstractWidget addLine() {
        return new SmallButtonBuilder(PADDING_HALF + (BUTTON_WIDTH + PADDING * 2), height - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 4, BUTTON_HEIGHT,
                button -> {
                    if(editCustomNotificationWidget.hasSelectedOption) {
                        editCustomNotificationWidget.addNewEntry();
                    }
                })
                .icon("Add Line")
                .color(0xFF00CA3A)
                .tooltip(Tooltip.create(Component.literal("Add line to the bottom")))
                .build();
    }

    private AbstractWidget wikiButton() {
        return new SmallButtonBuilder(PADDING_HALF + (BUTTON_WIDTH + PADDING * 2) + PADDING_HALF + BUTTON_WIDTH / 4, height - PADDING_HALF - BUTTON_HEIGHT,
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

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if(keyEvent.hasControlDown() && keyEvent.key() == GLFW.GLFW_KEY_S) {
            if(this.save()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("Notification saved"),
                        Component.literal(selectedNotificationId));
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
