package dannypx.foe.screens;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.handler.store.CustomHudIconDataHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.placeholder.editbox.PlaceholderEditBox;
import dannypx.foe.screens.interfaces.ScreenConstants;
import dannypx.foe.screens.widget.ButtonListWidget;
import dannypx.foe.screens.widget.SmallButtonBuilder;
import dannypx.foe.type.tuple.Triplet;
import net.minecraft.ChatFormatting;
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

public class CustomHudIconMakerScreen extends Screen implements ScreenConstants {
    //region Fields
    private final Screen parentScreen;

    private ButtonListWidget buttonList;
    private Map<String, ButtonListWidget.ButtonEntry> buttonEntryMap = new HashMap<>();
    private String selectedHudIconId;
    private CustomHudIconDataHandler.CustomHudIcon selectedHudIcon;

    private Component header;
    private final int widgetHeight = 20;

    private EditBox idEditBox;
    private EditBox scaleEditBox;
    private Checkbox showBackgroundCheckBox;
    private Checkbox showBarsCheckBox;
    private Checkbox useTrackerNameCheckBox;
    private Checkbox showElementCheckBox;

    private final int sideWidth = 100;

    private PlaceholderEditBox iconEditBox;
    //endregion

    //region Methods
    public CustomHudIconMakerScreen(Screen parent) {
        super(Component.literal("Custom HUD Maker Screen"));
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
        if(iconEditBox.isMouseOver(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Must be of type").withStyle(ChatFormatting.GRAY),
                    Component.literal("- Number, slot index of your inventory").withStyle(ChatFormatting.GRAY),
                    Component.literal("- String, of format \"minecraft:<id>[<componentData>]\"").withStyle(ChatFormatting.GRAY),
                    Component.literal("- Placeholder, results must be a number or string").withStyle(ChatFormatting.GRAY),
                    Component.literal("- Tracker name, if tracker is of ITEMSTACK").withStyle(ChatFormatting.GRAY)
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
                Component.literal("Scale"),
                this.minecraft.getWindow().getGuiScaledWidth() - PADDING - sideWidth - 40 - PADDING_HALF - minecraft.font.width("Scale") - PADDING_HALF,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING),
                CommonColors.WHITE,
                true
        );

        guiGraphics.drawString(font,
                Component.literal("Icon"),
                (BUTTON_WIDTH + PADDING * 2) + PADDING,
                PADDING + widgetHeight / 2 - font.lineHeight / 2 + (widgetHeight + PADDING) * 3,
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

        widgets.add(getNewHudIconElementButton());
        widgets.add(getDeleteHudIconElementButton());
        widgets.add(getImportButton());
        widgets.add(getExportButton());

        widgets.add(getIdEditBox());
        widgets.add(getScaleEditBox());
        widgets.add(getShowElementCheckBox());
        widgets.add(getShowBackgroundCheckBox());
        widgets.add(getShowBarsCheckBox());
        widgets.add(getIconEditBox());
        widgets.add(getUseTrackerNameCheckBox());

        widgets.add(this.wikiButton());

        widgets.forEach(this::addRenderableWidget);
    }

    private AbstractWidget getIdEditBox() {
        idEditBox = new EditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING,
                PADDING + widgetHeight + PADDING,
                this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2 - (sideWidth + PADDING) - 40 - PADDING_QUART - minecraft.font.width("Scale") - PADDING_HALF,
                widgetHeight,
                Component.empty()
        );
        idEditBox.setMaxLength(Integer.MAX_VALUE);

        idEditBox.setResponder(s -> {
            if(selectedHudIconId != null) {
                idEditBox.setHint(Component.literal(s));
            }
        });

        return idEditBox;
    }

    private AbstractWidget getScaleEditBox() {
        scaleEditBox = new EditBox(
                font,
                this.minecraft.getWindow().getGuiScaledWidth() - PADDING - sideWidth - 40 - PADDING_HALF,
                PADDING + widgetHeight + PADDING,
                40,
                widgetHeight,
                Component.empty()
        );
        scaleEditBox.setMaxLength(5);

        scaleEditBox.setResponder(s -> {
            if(selectedHudIconId != null) {
                scaleEditBox.setHint(Component.literal(s));
            }
        });

        return scaleEditBox;
    }

    private AbstractWidget getUseTrackerNameCheckBox() {
        useTrackerNameCheckBox = Checkbox.builder(
                        Component.literal("Is Tracker"),
                        font
                )
                .pos(
                        this.minecraft.getWindow().getGuiScaledWidth() - PADDING - sideWidth,
                        PADDING + (widgetHeight + PADDING) * 3
                )
                .selected(true)
                .onValueChange((checkbox, checked) -> {})
                .build();
        return useTrackerNameCheckBox;
    }

    private AbstractWidget getShowElementCheckBox() {
        showElementCheckBox = Checkbox.builder(
                        Component.literal("Show Element"),
                        font
                )
                .pos(
                        this.minecraft.getWindow().getGuiScaledWidth() - PADDING - sideWidth,
                        PADDING + widgetHeight + PADDING
                )
                .selected(true)
                .onValueChange((checkbox, checked) -> {})
                .build();
        return showElementCheckBox;
    }

    private AbstractWidget getShowBackgroundCheckBox() {
        showBackgroundCheckBox = Checkbox.builder(
                        Component.literal("Show Background"),
                        font
                )
                .pos(
                        (BUTTON_WIDTH + PADDING * 2) + PADDING,
                        PADDING + (widgetHeight + PADDING) * 2
                )
                .selected(true)
                .onValueChange((checkbox, checked) -> {})
                .build();
        return showBackgroundCheckBox;
    }

    private AbstractWidget getShowBarsCheckBox() {
        showBarsCheckBox = Checkbox.builder(
                        Component.literal("Show Bars"),
                        font
                )
                .pos(
                        (BUTTON_WIDTH + PADDING * 2) + PADDING + PADDING + 16 + PADDING_HALF + minecraft.font.width("Show Background"),
                        PADDING + (widgetHeight + PADDING) * 2
                )
                .selected(true)
                .onValueChange((checkbox, checked) -> {})
                .build();
        return showBarsCheckBox;
    }

    private AbstractWidget getIconEditBox() {
        iconEditBox = new PlaceholderEditBox(
                font,
                (BUTTON_WIDTH + PADDING * 2) + PADDING + sideWidth,
                PADDING + (widgetHeight + PADDING) * 3,
                this.minecraft.getWindow().getGuiScaledWidth() - (BUTTON_WIDTH + PADDING * 2) - PADDING * 2  - sideWidth - sideWidth - PADDING_HALF,
                widgetHeight,
                Component.empty()
        );
        iconEditBox.setMaxLength(Integer.MAX_VALUE);

        iconEditBox.setResponder(s -> {
            if(selectedHudIconId != null) {
                iconEditBox.setHint(Component.literal(s));
            }
        });

        return iconEditBox;
    }

    private AbstractWidget getNewHudIconElementButton() {
        return new SmallButtonBuilder(PADDING_HALF, this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2 - PADDING, BUTTON_HEIGHT,
                button -> {
                    String id = "Custom Hud Icon #" + UUID.randomUUID();

                    CustomHudIconDataHandler.instance().createNewCustomHudIcon(id);

                    ButtonListWidget.ButtonEntry buttonEntry = createHudIconEntry(id);

                    buttonList.addEntry(buttonEntry);
                    buttonEntryMap.put(id, buttonEntry);
                })
                .icon("New HUD Icon")
                .color(0xFF00CA3A)
                .build();
    }

    private AbstractWidget getDeleteHudIconElementButton() {
        return new SmallButtonBuilder(PADDING + (BUTTON_WIDTH / 2 - PADDING_HALF), this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2 - PADDING_HALF, BUTTON_HEIGHT,
                button -> {
                    if(selectedHudIconId != null) {
                        CustomHudIconDataHandler.instance().deleteCustomHudIcon(selectedHudIconId);

                        ButtonListWidget.ButtonEntry entry = buttonEntryMap.get(selectedHudIconId);

                        buttonList.removeEntry(entry);
                        buttonEntryMap.remove(selectedHudIconId);

                        selectedHudIconId = null;
                        this.resetFields();
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
                        Triplet<String, CustomHudIconDataHandler.CustomHudIcon, Integer> data = gson.fromJson(json, TypeToken.getParameterized(Triplet.class, String.class, CustomHudIconDataHandler.CustomHudIcon.class, Integer.class).getType());

                        if(data.value3() > FishOnMCExtras.HUD_ICON_VERSION) {
                            SystemToast.add(this.minecraft.getToastManager(),
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("FOE+"),
                                    Component.literal("Could not Import. Imported HUD Icon is made on a newer version"));

                            return;
                        }

                        if(CustomHudIconDataHandler.instance().getCustomHudIconData().customHudIconDataList.containsKey(data.value1())) {
                            data = Triplet.of(data.value1() + " (Duplicate)", data.value2(), data.value3());
                        }

                        String id = data.value1();

                        CustomHudIconDataHandler.instance().createNewCustomHudIcon(id, data.value2());

                        ButtonListWidget.ButtonEntry buttonEntry = createHudIconEntry(id);

                        buttonList.addEntry(buttonEntry);
                        buttonEntryMap.put(id, buttonEntry);

                        SystemToast.add(this.minecraft.getToastManager(),
                                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                Component.literal("FOE+"),
                                Component.literal("Imported HUD Icon"));

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
                    if(selectedHudIconId != null) {
                        try {
                            Triplet<String, CustomHudIconDataHandler.CustomHudIcon, Integer> dataHud = Triplet.of(
                                    selectedHudIconId,
                                    selectedHudIcon,
                                    FishOnMCExtras.HUD_ICON_VERSION
                            );

                            String rawData = Base64.getEncoder().encodeToString(
                                    TextHelper.compress(new GsonBuilder().create().toJson(dataHud))
                            );

                            String dataToCopy = "**Custom HUD Icon: **" + selectedHudIconId + "\n" +
                                    "```\n" +
                                    rawData + "\n" +
                                    "```\n" +
                                    "-# Using HUD Icon version: " + "`v" + FishOnMCExtras.HUD_ICON_VERSION + "`";

                            this.minecraft.keyboardHandler.setClipboard(dataToCopy);

                            SystemToast.add(this.minecraft.getToastManager(),
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("FOE+"),
                                    Component.literal("Exported HUD Icon on your clipboard"));
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
                height - ScreenConstants.BUTTON_HEIGHT * 3 - PADDING * 2
                ,
                0,
                BUTTON_HEIGHT + PADDING_HALF,
                BUTTON_HEIGHT,
                "Custom HUD Icons"
        );

        CustomHudIconDataHandler.instance().getCustomHudIconData().customHudIconDataList.forEach((id, ignored) -> {
            ButtonListWidget.ButtonEntry buttonEntry = createHudIconEntry(id);

            buttonList.addEntry(buttonEntry);
            buttonEntryMap.put(id, buttonEntry);
        });

        return buttonList;
    }

    private ButtonListWidget.ButtonEntry createHudIconEntry(String id) {
        return new ButtonListWidget.ButtonEntry(
                new SmallButtonBuilder(button -> {
                    selectedHudIcon = CustomHudIconDataHandler.instance().getCustomHudIconData().customHudIconDataList.get(id);

                    if(selectedHudIcon != null) {
                        selectedHudIconId = id;
                        this.setFields();
                    }})
                        .icon(id)
                        .style(SmallButtonBuilder.Style.NO_SMALL_CAPS)
                        .build()
        );
    }

    private void setFields() {
        this.header = Component.literal(selectedHudIconId);

        idEditBox.setValue(selectedHudIconId);
        idEditBox.setHint(Component.literal(selectedHudIconId));

        scaleEditBox.setValue(String.format(Locale.US, "%f", selectedHudIcon.getScale()));
        scaleEditBox.setHint(Component.literal(String.format(Locale.US, "%f", selectedHudIcon.getScale())));

        if(selectedHudIcon.isShowBackground() != showBackgroundCheckBox.selected()) {
            showBackgroundCheckBox.onPress(null);
        }

        if(selectedHudIcon.isShowBars() != showBarsCheckBox.selected()) {
            showBarsCheckBox.onPress(null);
        }

        if(selectedHudIcon.isShowElement() != showElementCheckBox.selected()) {
            showElementCheckBox.onPress(null);
        }

        if(selectedHudIcon.isUseTrackerItem() != useTrackerNameCheckBox.selected()) {
            useTrackerNameCheckBox.onPress(null);
        }

        iconEditBox.setValue(selectedHudIcon.getIcon());
        iconEditBox.setHint(Component.literal(selectedHudIcon.getIcon()));
    }

    private void resetFields() {
        this.header = Component.literal("No HUD Icon Selected");

        idEditBox.setValue("");
        idEditBox.setHint(Component.literal(""));

        scaleEditBox.setValue("");
        scaleEditBox.setHint(Component.literal(""));

        if(showBackgroundCheckBox.selected()) {
            showBackgroundCheckBox.onPress(null);
        }

        if(showBarsCheckBox.selected()) {
            showBarsCheckBox.onPress(null);
        }

        if(showElementCheckBox.selected()) {
            showElementCheckBox.onPress(null);
        }

        if(useTrackerNameCheckBox.selected()) {
            useTrackerNameCheckBox.onPress(null);
        }

        iconEditBox.setValue("");
        iconEditBox.setHint(Component.literal(""));

        selectedHudIcon = null;
        selectedHudIconId = null;
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
        if(selectedHudIconId != null) {
            if(idEditBox.getValue().isBlank()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("HUD Icon name is empty"));

                return false;
            }

            float scale;
            try {
                scale = Float.parseFloat(scaleEditBox.getValue());
            } catch (NumberFormatException ignored) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Could not parse scale"));

                return false;
            }

            if(iconEditBox.getValue().isBlank()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Icon is empty"));
            }

            boolean couldParseIcon = false;
            CustomHudIconDataHandler.IconType iconType = null;

            if(useTrackerNameCheckBox.selected()) {
                couldParseIcon = true;
                iconType = CustomHudIconDataHandler.IconType.TRACKER;
            } else {
                try {
                    Integer.parseInt(iconEditBox.getValue());
                    couldParseIcon = true;
                    iconType = CustomHudIconDataHandler.IconType.SLOT;
                } catch (NumberFormatException ignored) {}

                Pattern iconPattern = Pattern.compile("^(?:([a-z_]+:[a-z_]+)(?:\\[(.*)\\])?|(.))$");
                if(iconPattern.matcher(iconEditBox.getValue()).matches() && !couldParseIcon) {
                    couldParseIcon = true;
                    iconType = CustomHudIconDataHandler.IconType.ITEM;
                }

                if(iconEditBox.getValue().startsWith("%") && iconEditBox.getValue().endsWith("%") && !couldParseIcon) {
                    couldParseIcon = true;
                    iconType = CustomHudIconDataHandler.IconType.PLACEHOLDER;
                }
            }

            if(!couldParseIcon) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("Could not parse icon"));
                return false;
            }

            CustomHudIconDataHandler.CustomHudIcon hudIcon = CustomHudIconDataHandler.instance().updateHudIcon(
                    selectedHudIconId,
                    idEditBox.getValue(),
                    scale,
                    showBackgroundCheckBox.selected(),
                    showBarsCheckBox.selected(),
                    showElementCheckBox.selected(),
                    useTrackerNameCheckBox.selected(),
                    iconEditBox.getValue(),
                    iconType
            );

            ButtonListWidget.ButtonEntry entry = buttonEntryMap.remove(selectedHudIconId);
            int index = buttonList.entryAt(entry);
            buttonList.removeEntry(entry);

            selectedHudIcon = hudIcon;
            selectedHudIconId = idEditBox.getValue();
            this.header = Component.literal(selectedHudIconId);

            ButtonListWidget.ButtonEntry buttonEntry = createHudIconEntry(selectedHudIconId);
            buttonEntryMap.put(selectedHudIconId, buttonEntry);
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
                                Component.literal("HUD icon saved"),
                                Component.literal(selectedHudIconId));
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

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if(keyEvent.hasControlDown() && keyEvent.key() == GLFW.GLFW_KEY_S) {
            if(this.save()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("HUD icon saved"),
                        Component.literal(selectedHudIconId));
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
