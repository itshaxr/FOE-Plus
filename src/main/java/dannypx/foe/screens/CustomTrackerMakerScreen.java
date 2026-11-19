package dannypx.foe.screens;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.handler.store.CustomHudDataHandler;
import dannypx.foe.handler.store.CustomTrackerDataHandler;
import dannypx.foe.helper.ItemStackHelper;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.screens.interfaces.ScreenConstants;
import dannypx.foe.screens.widget.ButtonListWidget;
import dannypx.foe.screens.widget.EditCustomTrackerWidget;
import dannypx.foe.screens.widget.SmallButtonBuilder;
import dannypx.foe.type.custom_value.*;
import dannypx.foe.type.tracker.TrackerAction;
import dannypx.foe.type.tracker.TrackerType;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.type.tuple.Triplet;
import dannypx.foe.type.type_adapter.TrackerValueAdapter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class CustomTrackerMakerScreen extends Screen implements ScreenConstants {
    //region Fields
    private final Screen parentScreen;

    private ButtonListWidget buttonList;
    private EditCustomTrackerWidget editCustomTrackerWidget;
    private Map<String, ButtonListWidget.ButtonEntry> buttonEntryMap = new HashMap<>();
    private String selectedTrackerId;
    //endregion

    //region Methods
    public CustomTrackerMakerScreen(Screen parent) {
        super(Component.literal("Custom Tracker Maker Screen"));
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
        this.editCustomTrackerWidget.render(guiGraphics, mouseX, mouseY, delta);
    }

    private void renderWidgets() {
        List<AbstractWidget> widgets = new ArrayList<>();

        widgets.add(this.saveBackButton());
        widgets.add(this.saveButton());
        widgets.add(this.backButton());
        widgets.add(this.addLine());

        widgets.add(getEditTrackerWidget());
        widgets.add(getButtonList());

        widgets.add(getNewTrackerElementButton());
        widgets.add(getDeleteTrackerElementButton());
        widgets.add(getImportButton());
        widgets.add(getExportButton());

        widgets.add(this.wikiButton());

        widgets.forEach(this::addRenderableWidget);
    }

    private AbstractWidget getEditTrackerWidget() {
        editCustomTrackerWidget = new EditCustomTrackerWidget(
                (BUTTON_WIDTH + PADDING * 2),
                0,
                width - (BUTTON_WIDTH + PADDING * 2),
                height - (BUTTON_HEIGHT + PADDING_HALF) - 3,
                Component.literal("No Tracker Selected")
        );

        return editCustomTrackerWidget;
    }

    private AbstractWidget getNewTrackerElementButton() {
        return new SmallButtonBuilder(
                PADDING_HALF, this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2 - PADDING, BUTTON_HEIGHT,
                button -> {
                    String id = "Custom Tracker #" + UUID.randomUUID();

                    CustomTrackerDataHandler.instance().createNewCustomTracker(id);

                    ButtonListWidget.ButtonEntry buttonEntry = createTrackerEntry(id);

                    buttonList.addEntry(buttonEntry);
                    buttonEntryMap.put(id, buttonEntry);
                })
                .icon("New Tracker")
                .color(0xFF00CA3A)
                .build();
    }

    private AbstractWidget getDeleteTrackerElementButton() {
        return new SmallButtonBuilder(
                PADDING + (BUTTON_WIDTH / 2 - PADDING_HALF), this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2 - PADDING_HALF, BUTTON_HEIGHT,
                button -> {
                    if(editCustomTrackerWidget.hasSelectedOption) {
                        CustomTrackerDataHandler.instance().deleteCustomTracker(selectedTrackerId);
                        editCustomTrackerWidget.reset();
                        ButtonListWidget.ButtonEntry entry = buttonEntryMap.get(selectedTrackerId);

                        buttonList.removeEntry(entry);
                        buttonEntryMap.remove(selectedTrackerId);

                        selectedTrackerId = null;
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

                        Gson gson = new GsonBuilder().registerTypeAdapter(TrackerValue.class, new TrackerValueAdapter()).create();
                        Triplet<String, CustomTrackerDataHandler.CustomTracker, Integer> data = gson.fromJson(json, TypeToken.getParameterized(Triplet.class, String.class, CustomTrackerDataHandler.CustomTracker.class, Integer.class).getType());

                        if(data.value3() > FishOnMCExtras.TRACKER_VERSION) {
                            SystemToast.add(this.minecraft.getToastManager(),
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("FOE+"),
                                    Component.literal("Could not Import. Imported Tracker is made on a newer version"));

                            return;
                        }

                        if(CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.containsKey(data.value1())) {
                            data = Triplet.of(data.value1() + " (Duplicate)", data.value2(), data.value3());
                        }

                        String id = data.value1();

                        CustomTrackerDataHandler.instance().createNewCustomTracker(id, data.value2());

                        ButtonListWidget.ButtonEntry buttonEntry = createTrackerEntry(id);

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
        return new SmallButtonBuilder(
                PADDING + (BUTTON_WIDTH / 2 - PADDING_HALF), this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT * 2 - PADDING_HALF,
                BUTTON_WIDTH / 2 - PADDING_HALF, BUTTON_HEIGHT,
                button -> {
                    if(editCustomTrackerWidget.hasSelectedOption) {
                        try {
                            Triplet<String, CustomTrackerDataHandler.CustomTracker, Integer> dataTracker = Triplet.of(
                                    editCustomTrackerWidget.currentSelectedTracker,
                                    CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.get(editCustomTrackerWidget.currentSelectedTracker),
                                    FishOnMCExtras.TRACKER_VERSION
                            );
                            String rawData = Base64.getEncoder().encodeToString(
                                    TextHelper.compress(new GsonBuilder()
                                            .registerTypeAdapter(TrackerValue.class, new TrackerValueAdapter())
                                            .create().toJson(dataTracker)
                                    )
                            );

                            String dataToCopy = "**Custom Tracker: **" + selectedTrackerId + "\n" +
                                    "```\n" +
                                    rawData + "\n" +
                                    "```\n" +
                                    "-# Using Tracker version: " + "`v" + FishOnMCExtras.TRACKER_VERSION + "`";

                            this.minecraft.keyboardHandler.setClipboard(dataToCopy);

                            SystemToast.add(this.minecraft.getToastManager(),
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("FOE+"),
                                    Component.literal("Exported Tracker on your clipboard"));
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
                "Custom Trackers"
        );

        CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.forEach((id, ignored) -> {
            ButtonListWidget.ButtonEntry buttonEntry = createTrackerEntry(id);

            buttonList.addEntry(buttonEntry);
            buttonEntryMap.put(id, buttonEntry);
        });

        return buttonList;
    }

    private ButtonListWidget.ButtonEntry createTrackerEntry(String id) {
        return new ButtonListWidget.ButtonEntry(
                new SmallButtonBuilder(
                        button -> {
                            selectedTrackerId = id;
                            editCustomTrackerWidget.selectTracker(
                                    id,
                                    CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.get(id));
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
        if(editCustomTrackerWidget.hasSelectedOption) {
            if(editCustomTrackerWidget.idName.isBlank()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Tracker name is empty"));

                return false;
            }

            if(!Objects.equals(editCustomTrackerWidget.currentSelectedTracker, editCustomTrackerWidget.idName)
                    && CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.containsKey(editCustomTrackerWidget.idName)
            ) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Tracker name already exist"));

                return false;
            }

            TrackerValue defaultValue = null;
            AtomicBoolean couldParse = new AtomicBoolean(false);

            if("true".equals(editCustomTrackerWidget.defaultValue) || "false".equals(editCustomTrackerWidget.defaultValue)) {
                defaultValue = BooleanValue.of(Boolean.parseBoolean(editCustomTrackerWidget.defaultValue));
                couldParse.set(true);
            }

            try {
                float parsed = Float.parseFloat(editCustomTrackerWidget.defaultValue);
                defaultValue = new NumberValue(parsed);
                couldParse.set(true);
            } catch (Exception ignored) {}

            if(editCustomTrackerWidget.trackerType == TrackerType.ITEMSTACK
                    && editCustomTrackerWidget.defaultValue.isBlank()
            ) {
                defaultValue = EmptyValue.getDefault();
                couldParse.set(true);
            }

            if(!couldParse.get()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Default value is not correct format"));

                return false;
            }

            AtomicBoolean actionIdIsNotEmpty = new AtomicBoolean(true);
            AtomicBoolean trackerActionIsNotEmpty = new AtomicBoolean(true);
            AtomicBoolean couldParseValueToUse = new AtomicBoolean(false);
            AtomicBoolean couldParseTrackerAction = new AtomicBoolean(true);
            editCustomTrackerWidget.getEntries().forEach(lineEntry -> {
                if(lineEntry.actionId.isEmpty()) actionIdIsNotEmpty.set(false);

                if(lineEntry.trackerAction.isEmpty()) trackerActionIsNotEmpty.set(false);

                if("true".equals(lineEntry.valueToUse) || "false".equals(lineEntry.valueToUse)) {
                    couldParseValueToUse.set(true);
                }

                try {
                    Float.parseFloat(lineEntry.valueToUse);
                    couldParseValueToUse.set(true);
                } catch (Exception ignored) {}

                if(lineEntry.valueToUse.startsWith("%") || lineEntry.valueToUse.endsWith("%")) {
                    couldParseValueToUse.set(true);
                }

                if(lineEntry.valueToUse.isEmpty()) {
                    couldParseValueToUse.set(true);
                }

                ItemStack itemStack = ItemStackHelper.valueOf(lineEntry.valueToUse);
                if(!itemStack.isEmpty()) {
                    couldParseValueToUse.set(true);
                }

                try {
                    TrackerAction action = TrackerAction.valueOf(lineEntry.trackerAction);
                    if(!TrackerAction.getActions(editCustomTrackerWidget.trackerType).contains(action)) {
                        couldParseTrackerAction.set(false);
                    }
                } catch (Exception e) {
                    LoggerHandler.error(e);
                    couldParseTrackerAction.set(false);
                }
            });

            if(!couldParseValueToUse.get()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Value to use is not correct format"));

                return false;
            }

            if(!couldParseTrackerAction.get()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Tracker action does not exist"));

                return false;
            }

            if(!actionIdIsNotEmpty.get()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("An Action ID is empty"));

                return false;
            }

            if(!trackerActionIsNotEmpty.get()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("An Tracker Action is empty"));

                return false;
            }

            Set<String> uniqueActionIds = editCustomTrackerWidget.getEntries().stream()
                    .map(EditCustomTrackerWidget.LineEntry::getActionId)
                    .collect(Collectors.toSet());

            if(uniqueActionIds.size() != editCustomTrackerWidget.getEntries().size()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Action IDs are not unique"));

                return false;
            }

            if(defaultValue == null) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Error at Default Value"));

                return false;
            }

            CustomTrackerDataHandler.instance().updateTracker(
                    editCustomTrackerWidget.currentSelectedTracker,
                    editCustomTrackerWidget.idName,
                    editCustomTrackerWidget.trackerType,
                    defaultValue,
                    defaultValue,
                    editCustomTrackerWidget.isPersistent,
                    editCustomTrackerWidget.useTracker,
                    editCustomTrackerWidget.getEntries().stream().map(lineEntry -> Pair.of(lineEntry.actionId, Triplet.of(
                            TrackerAction.valueOf(lineEntry.trackerAction),
                            lineEntry.condition,
                            !lineEntry.valueToUse.isEmpty() ? switch (editCustomTrackerWidget.trackerType) {
                                case BOOLEAN -> (lineEntry.valueToUse.startsWith("%") && lineEntry.valueToUse.endsWith("%"))
                                        ? PlaceholderStringValue.of(lineEntry.valueToUse)
                                        : BooleanValue.of(Boolean.parseBoolean(lineEntry.valueToUse));
                                case INTEGER -> (lineEntry.valueToUse.startsWith("%") && lineEntry.valueToUse.endsWith("%"))
                                        ? PlaceholderStringValue.of(lineEntry.valueToUse)
                                        : NumberValue.of(Float.parseFloat(lineEntry.valueToUse));
                                case ITEMSTACK -> (lineEntry.valueToUse.startsWith("%") && lineEntry.valueToUse.endsWith("%"))
                                        ? PlaceholderStringValue.of(lineEntry.valueToUse)
                                        : ItemStackValue.of(lineEntry.valueToUse);
                            } : EmptyValue.getDefault()
                    ))).collect(Collectors.toMap(Pair::value1, Pair::value2))
            );

            ButtonListWidget.ButtonEntry entry = buttonEntryMap.remove(selectedTrackerId);
            int index = buttonList.entryAt(entry);
            buttonList.removeEntry(entry);

            selectedTrackerId = editCustomTrackerWidget.idName;

            ButtonListWidget.ButtonEntry buttonEntry = createTrackerEntry(selectedTrackerId);
            buttonEntryMap.put(selectedTrackerId, buttonEntry);
            buttonList.addEntryAtPos(buttonEntry, index);
            buttonList.setSelected(buttonEntry);

            editCustomTrackerWidget.selectTracker(selectedTrackerId, CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.get(selectedTrackerId));

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
                                Component.literal("Tracker saved"),
                                Component.literal(selectedTrackerId));
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
        return new SmallButtonBuilder(
                PADDING_HALF + (BUTTON_WIDTH + PADDING * 2), height - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 4, BUTTON_HEIGHT,
                button -> {
                    if(editCustomTrackerWidget.hasSelectedOption) {
                        editCustomTrackerWidget.addNewEntry();
                    }
                })
                .icon("Add Line")
                .color(0xFF00CA3A)
                .tooltip(Tooltip.create(Component.literal("Add line to the bottom")))
                .build();
    }

    private AbstractWidget wikiButton() {
        return new SmallButtonBuilder(
                PADDING_HALF + (BUTTON_WIDTH + PADDING * 2) + PADDING_HALF + BUTTON_WIDTH / 4, height - PADDING_HALF - BUTTON_HEIGHT,
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
                        Component.literal("Tracker saved"),
                        Component.literal(selectedTrackerId));
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
