package dannypx.foe.screens;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.logic.CodeExecuterHandler;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.handler.store.CustomButtonDataHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.screens.widget.SmallButtonBuilder;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.screens.interfaces.ScreenConstants;
import dannypx.foe.screens.widget.ButtonListWidget;
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

public class CustomButtonMakerScreen extends Screen implements ScreenConstants {
    //region Fields
    private final Screen parentScreen;
    private final String screenId;

    private ButtonListWidget buttonList;
    private Map<String, ButtonListWidget.ButtonEntry> buttonEntryMap = new HashMap<>();
    private String selectedButtonId;
    private CustomButtonDataHandler.CustomButton selectedButton;

    private Component header;
    private final int widgetHeight = 20;

    private EditBox nameEditBox;
    private Checkbox showButtonCheckBox;
    private final int sideWidth = 100;
    private EditBox descriptionEditBox;
    private EditBox actionEditBox;
    private EditBox iconEditBox;
    //endregion

    //region Methods
    public CustomButtonMakerScreen(Screen parent, String screenId) {
        super(Component.literal("Custom Button Maker Screen"));
        this.parentScreen = parent;
        this.screenId = screenId;
    }

    @Override
    protected void init() {
        super.init();
        CustomButtonDataHandler.instance().init(this.screenId);
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
        if(descriptionEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Can be empty").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        }

        if(actionEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Must start with \"/\"").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        }

        if(iconEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Must be a single character or is an item").withStyle(ChatFormatting.GRAY),
                    Component.literal("using one of the following formats: ").withStyle(ChatFormatting.GRAY),
                    Component.literal("\"minecraft:<id>\"").withStyle(ChatFormatting.GOLD),
                    Component.literal("\"minecraft:<id>[<componentData>]\"").withStyle(ChatFormatting.GOLD)

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
                Component.literal("Description"),
                (BUTTON_WIDTH + PADDING * 2) + PADDING,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 2,
                CommonColors.WHITE,
                true
        );

        guiGraphics.drawString(font,
                Component.literal("Command"),
                (BUTTON_WIDTH + PADDING * 2) + PADDING,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 3,
                CommonColors.WHITE,
                true
        );

        guiGraphics.drawString(font,
                Component.literal("Icon"),
                (BUTTON_WIDTH + PADDING * 2) + PADDING,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 4,
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
        widgets.add(getShowButtonCheckBox());
        widgets.add(getDescriptionEditBox());
        widgets.add(getActionEditBox());
        widgets.add(getIconEditBox());

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
            if(selectedButtonId != null) {
                nameEditBox.setHint(Component.literal(s));
            }
        });

        return nameEditBox;
    }

    private AbstractWidget getShowButtonCheckBox() {
        showButtonCheckBox = Checkbox.builder(
                        Component.literal("Show Button"),
                        font
                )
                .pos(this.minecraft.getWindow().getGuiScaledWidth() - PADDING - sideWidth
                        , PADDING + widgetHeight + PADDING)
                .selected(true)
                .onValueChange((checkbox, checked) -> {})
                .build();
        return showButtonCheckBox;
    }

    private AbstractWidget getDescriptionEditBox() {
        descriptionEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + (widgetHeight + PADDING) * 2,
                this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2 - sideWidth,
                widgetHeight,
                Component.empty()
        );
        descriptionEditBox.setMaxLength(Integer.MAX_VALUE);

        descriptionEditBox.setResponder(s -> {
            if(selectedButtonId != null) {
                descriptionEditBox.setHint(Component.literal(s));
            }
        });

        return descriptionEditBox;
    }

    private AbstractWidget getActionEditBox() {
        actionEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + (widgetHeight + PADDING) * 3,
                this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2 - sideWidth,
                widgetHeight,
                Component.empty()
        );
        actionEditBox.setMaxLength(Integer.MAX_VALUE);

        actionEditBox.setResponder(s -> {
            if(selectedButtonId != null) {
                actionEditBox.setHint(Component.literal(s));
            }
        });

        return actionEditBox;
    }

    private AbstractWidget getIconEditBox() {
        iconEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + (widgetHeight + PADDING) * 4,
                this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2 - sideWidth,
                widgetHeight,
                Component.empty()
        );
        iconEditBox.setMaxLength(Integer.MAX_VALUE);

        iconEditBox.setResponder(s -> {
            if(selectedButtonId != null) {
                iconEditBox.setHint(Component.literal(s));
            }
        });

        return iconEditBox;
    }

    private AbstractWidget getNewButtonElementButton() {
        return new SmallButtonBuilder(
                PADDING_HALF, this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2 - PADDING, BUTTON_HEIGHT,
                button -> {
                    String id = "Custom Button #" + UUID.randomUUID();

                    CustomButtonDataHandler.instance().createNewButton(screenId, id);

                    ButtonListWidget.ButtonEntry buttonEntry = createButtonEntry(id);

                    buttonList.addEntry(buttonEntry);
                    buttonEntryMap.put(id, buttonEntry);
                })
                .icon("Create Button")
                .color(0xFF00CA3A)
                .build();
    }

    private AbstractWidget getDeleteButtonElementButton() {
        return new SmallButtonBuilder(
                PADDING + (BUTTON_WIDTH / 2 - PADDING_HALF), this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2 - PADDING_HALF, BUTTON_HEIGHT,
                button -> {
                    if(selectedButtonId != null) {
                        CustomButtonDataHandler.instance().deleteButton(screenId, selectedButtonId);

                        ButtonListWidget.ButtonEntry entry = buttonEntryMap.get(selectedButtonId);

                        buttonList.removeEntry(entry);
                        buttonEntryMap.remove(selectedButtonId);

                        selectedButtonId = null;
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

                        Gson gson = new GsonBuilder().create();
                        Pair<CustomButtonDataHandler.CustomButton, Integer> data = gson.fromJson(json, TypeToken.getParameterized(Pair.class, CustomButtonDataHandler.CustomButton.class, Integer.class).getType());

                        if(data.value2() > FishOnMCExtras.BUTTON_VERSION) {
                            SystemToast.add(this.minecraft.getToastManager(),
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("FOE+"),
                                    Component.literal("Could not Import. Imported Button is made on a newer version"));

                            return;
                        }

                        if(CustomButtonDataHandler.instance().getCustomButtonData().buttonList.getOrDefault(screenId, Pair.of(new ArrayList<>(), false)).value1().stream().anyMatch(b -> Objects.equals(b.name, data.value1().name))) {
                            data.value1().name = data.value1().name + " (Duplicate)";
                        }

                        String id = data.value1().name;

                        CustomButtonDataHandler.instance().createNewButton(screenId, data.value1());

                        ButtonListWidget.ButtonEntry buttonEntry = createButtonEntry(id);

                        buttonList.addEntry(buttonEntry);
                        buttonEntryMap.put(id, buttonEntry);

                        SystemToast.add(this.minecraft.getToastManager(),
                                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                Component.literal("FOE+"),
                                Component.literal("Imported Button"));
                    } catch (Exception e) {
                        LoggerHandler.error(e);

                        SystemToast.add(this.minecraft.getToastManager(),
                                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                Component.literal("FOE+"),
                                Component.literal("Could not Import. Data invalid"));
                    }
                })
                .icon("Import")
                .tooltip(Tooltip.create(Component.literal("Imports from the code on your clipboard")))
                .color(0xFF1E90FF)
                .build();
    }

    private AbstractWidget getExportButton() {
        return new SmallButtonBuilder(
                PADDING + (BUTTON_WIDTH / 2 - PADDING_HALF), this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT * 2 - PADDING_HALF,
                BUTTON_WIDTH / 2 - PADDING_HALF, BUTTON_HEIGHT,
                button -> {
                    if(selectedButtonId != null) {
                        try {
                            Pair<CustomButtonDataHandler.CustomButton, Integer> dataButton = Pair.of(
                                    selectedButton,
                                    FishOnMCExtras.BUTTON_VERSION
                            );

                            String rawData = Base64.getEncoder().encodeToString(
                                    TextHelper.compress(new GsonBuilder().create().toJson(dataButton))
                            );

                            String dataToCopy = "**Custom Button: **" + selectedButtonId + "\n" +
                                    "```\n" +
                                    rawData + "\n" +
                                    "```\n" +
                                    "-# Using Button version: " + "`v" + FishOnMCExtras.BUTTON_VERSION + "`";

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
                "Custom Buttons"
        );

        CustomButtonDataHandler.instance().getCustomButtonData().buttonList.getOrDefault(screenId, Pair.of(new ArrayList<>(), false)).value1().forEach((button) -> {
            ButtonListWidget.ButtonEntry buttonEntry = createButtonEntry(button.name);

            buttonList.addEntry(buttonEntry);
            buttonEntryMap.put(button.name, buttonEntry);
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
        if(selectedButtonId != null) {
            if(nameEditBox.getValue().isBlank()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Button name is empty"));

                return false;
            }

            if(CustomButtonDataHandler.instance().getCustomButtonData().buttonList.getOrDefault(screenId, Pair.of(new ArrayList<>(), false)).value1().stream().anyMatch(b -> Objects.equals(b.name, nameEditBox.getValue()))
                    && !Objects.equals(selectedButton.name, nameEditBox.getValue())
            ) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Button name already exist"));

                return false;
            }

            if(!actionEditBox.getValue().startsWith("/")) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Command must start with /"));

                return false;
            }

            Pattern iconPattern = Pattern.compile("^(?:([a-z_]+:[a-z_]+)(?:\\[(.*)\\])?|(.))$");
            if(!iconPattern.matcher(iconEditBox.getValue()).matches()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Icon is not right format"));

                return false;
            }

            CustomButtonDataHandler.CustomButton button = CustomButtonDataHandler.instance().updateButton(screenId, selectedButton,
                    nameEditBox.getValue(),
                    descriptionEditBox.getValue(),
                    actionEditBox.getValue(),
                    iconEditBox.getValue(),
                    showButtonCheckBox.selected());

            ButtonListWidget.ButtonEntry entry = buttonEntryMap.remove(selectedButtonId);
            int index = buttonList.entryAt(entry);
            buttonList.removeEntry(entry);

            selectedButton = button;
            selectedButtonId = nameEditBox.getValue();
            this.header = Component.literal(selectedButtonId);

            ButtonListWidget.ButtonEntry buttonEntry = createButtonEntry(selectedButtonId);
            buttonEntryMap.put(selectedButtonId, buttonEntry);
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
                                Component.literal("Button saved"),
                                Component.literal(selectedButtonId));
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

    private ButtonListWidget.ButtonEntry createButtonEntry(String id) {
        return new ButtonListWidget.ButtonEntry(
                new SmallButtonBuilder(
                        button -> {
                            selectedButton = CustomButtonDataHandler.instance().getCustomButtonData().buttonList.getOrDefault(screenId, Pair.of(new ArrayList<>(), false)).value1().stream().filter(buttonObject -> Objects.equals(buttonObject.name, id)).findAny().orElse(null);

                            if(selectedButton != null) {
                                selectedButtonId = id;
                                this.setFields();
                            }
                        })
                        .icon(Component.literal(TextHelper.parseLegacyWithStyle(id.replace("&", "§")).value1().getString()))
                        .width(BUTTON_WIDTH / 4 * 3)
                        .style(SmallButtonBuilder.Style.NO_SMALL_CAPS)
                        .build(),
                new SmallButtonBuilder(
                        button -> CodeExecuterHandler.runLater(1, () -> {
                            String newId = "Custom Hud #" + UUID.randomUUID();

                            int pos = buttonList.children().indexOf(buttonEntryMap.get(id));

                            CustomButtonDataHandler.instance().createNewButton(screenId, newId, pos);

                            ButtonListWidget.ButtonEntry buttonEntry = createButtonEntry(newId);

                            buttonList.addEntryAtPos(buttonEntry, pos);
                            buttonEntryMap.put(newId, buttonEntry);
                        }))
                        .icon("Add")
                        .width(25)
                        .tooltip(Tooltip.create(Component.literal("Add new button")))
                        .color(0xFF00CA3A)
                        .build(),
                new SmallButtonBuilder(
                        button -> CodeExecuterHandler.runLater(1, () -> {
                            int pos = buttonList.children().indexOf(buttonEntryMap.get(id));

                            if(pos > 0) {
                                CustomButtonDataHandler.instance().swapUp(screenId, pos);

                                buttonList.swapUp(pos);
                            }
                        }))
                        .icon("⏶")
                        .size(25, 8)
                        .tooltip(Tooltip.create(Component.literal("Move button up")))
                        .color(0xFF1E90FF)
                        .build(),
                new SmallButtonBuilder(
                        button -> CodeExecuterHandler.runLater(1, () -> {
                            int pos = buttonList.children().indexOf(buttonEntryMap.get(id));

                            if(pos < buttonList.children().size() - 1) {
                                CustomButtonDataHandler.instance().swapDown(screenId, pos);

                                buttonList.swapDown(pos);
                            }
                        }))
                        .icon("⏷")
                        .size(25, 8)
                        .tooltip(Tooltip.create(Component.literal("Move button Down")))
                        .color(0xFF1E90FF)
                        .build()
        );
    }

    private void setFields() {
        this.header = TextHelper.parseLegacyWithStyle(selectedButtonId.replace("&", "§")).value1();
        nameEditBox.setValue(selectedButtonId);
        nameEditBox.setHint(Component.literal(selectedButtonId));

        if(selectedButton != null) {
            if(selectedButton.showButton != showButtonCheckBox.selected()) {
                showButtonCheckBox.onPress(null);
            }

            descriptionEditBox.setValue(selectedButton.description);
            descriptionEditBox.setHint(Component.literal(selectedButton.description));

            actionEditBox.setValue(selectedButton.action);
            actionEditBox.setHint(Component.literal(selectedButton.action));

            iconEditBox.setValue(selectedButton.icon);
            iconEditBox.setHint(Component.literal(selectedButton.icon));
        }
    }

    private void resetFields() {
        this.header = Component.literal("No Button Selected");

        nameEditBox.setValue("");
        nameEditBox.setHint(Component.literal(""));

        if(showButtonCheckBox.selected()) {
            showButtonCheckBox.onPress(null);
        }

        descriptionEditBox.setValue("");
        descriptionEditBox.setHint(Component.literal(""));

        actionEditBox.setValue("");
        actionEditBox.setHint(Component.literal(""));

        iconEditBox.setValue("");
        iconEditBox.setHint(Component.literal(""));

        selectedButton = null;
        selectedButtonId = null;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if(keyEvent.hasControlDown() && keyEvent.key() == GLFW.GLFW_KEY_S) {
            if(this.save()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("Button saved"),
                        Component.literal(selectedButtonId));
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
