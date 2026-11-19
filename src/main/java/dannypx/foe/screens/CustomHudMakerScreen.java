package dannypx.foe.screens;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.handler.store.CustomHudDataHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.screens.widget.SmallButtonBuilder;
import dannypx.foe.type.tuple.Triplet;
import dannypx.foe.screens.interfaces.ScreenConstants;
import dannypx.foe.screens.widget.ButtonListWidget;
import dannypx.foe.screens.widget.EditCustomHUDWidget;
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

public class CustomHudMakerScreen extends Screen implements ScreenConstants {
    //region Fields
    private final Screen parentScreen;

    private ButtonListWidget buttonList;
    private EditCustomHUDWidget editCustomHUDWidget;
    private Map<String, ButtonListWidget.ButtonEntry> buttonEntryMap = new HashMap<>();
    private String selectedHudId;
    //endregion

    //region Methods
    public CustomHudMakerScreen(Screen parent) {
        super(Component.literal("Custom HUD Maker Screen"));
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
        this.editCustomHUDWidget.render(guiGraphics, mouseX, mouseY, delta);
    }

    private void renderWidgets() {
        List<AbstractWidget> widgets = new ArrayList<>();

        widgets.add(this.saveBackButton());
        widgets.add(this.saveButton());
        widgets.add(this.backButton());
        widgets.add(this.addLine());

        widgets.add(getEditHudWidget());
        widgets.add(getButtonList());

        widgets.add(getNewHudElementButton());
        widgets.add(getDeleteHudElementButton());
        widgets.add(getImportButton());
        widgets.add(getExportButton());


        widgets.add(this.wikiButton());

        widgets.forEach(this::addRenderableWidget);
    }

    private AbstractWidget getEditHudWidget() {
        editCustomHUDWidget = new EditCustomHUDWidget(
                (BUTTON_WIDTH + PADDING * 2),
                0,
                width - (BUTTON_WIDTH + PADDING * 2),
                height - (BUTTON_HEIGHT + PADDING_HALF) - 3,
                Component.literal("No Hud Selected")
        );

        return editCustomHUDWidget;
    }

    private AbstractWidget getNewHudElementButton() {
        return new SmallButtonBuilder(
                PADDING_HALF, this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2 - PADDING, BUTTON_HEIGHT,
                button -> {
                    String id = "Custom Hud #" + UUID.randomUUID();

                    CustomHudDataHandler.instance().createNewCustomHud(id);

                    ButtonListWidget.ButtonEntry buttonEntry = createHudEntry(id);

                    buttonList.addEntry(buttonEntry);
                    buttonEntryMap.put(id, buttonEntry);
                })
                .icon("New HUD")
                .color(0xFF00CA3A)
                .build();
    }

    private AbstractWidget getDeleteHudElementButton() {
        return new SmallButtonBuilder(
                PADDING + (BUTTON_WIDTH / 2 - PADDING_HALF), this.minecraft.getWindow().getGuiScaledHeight() - PADDING_HALF - BUTTON_HEIGHT,
                BUTTON_WIDTH / 2 - PADDING_HALF, BUTTON_HEIGHT,
                button -> {
                    if(editCustomHUDWidget.hasSelectedOption) {
                        CustomHudDataHandler.instance().deleteCustomHud(selectedHudId);
                        editCustomHUDWidget.reset();
                        ButtonListWidget.ButtonEntry entry = buttonEntryMap.get(selectedHudId);

                        buttonList.removeEntry(entry);
                        buttonEntryMap.remove(selectedHudId);

                        selectedHudId = null;
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
                        Triplet<String, CustomHudDataHandler.CustomHud, Integer> data = gson.fromJson(json, TypeToken.getParameterized(Triplet.class, String.class, CustomHudDataHandler.CustomHud.class, Integer.class).getType());

                        if(data.value3() > FishOnMCExtras.HUD_VERSION) {
                            SystemToast.add(this.minecraft.getToastManager(),
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("FOE+"),
                                    Component.literal("Could not Import. Imported HUD is made on a newer version"));

                            return;
                        }

                        if(CustomHudDataHandler.instance().getCustomHudData().customHudRawDataList.containsKey(data.value1())) {
                            data = Triplet.of(data.value1() + " (Duplicate)", data.value2(), data.value3());
                        }

                        String id = data.value1();

                        CustomHudDataHandler.instance().createNewCustomHud(id, data.value2());

                        ButtonListWidget.ButtonEntry buttonEntry = createHudEntry(id);

                        buttonList.addEntry(buttonEntry);
                        buttonEntryMap.put(id, buttonEntry);

                        SystemToast.add(this.minecraft.getToastManager(),
                                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                Component.literal("FOE+"),
                                Component.literal("Imported HUD"));

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
                    if(editCustomHUDWidget.hasSelectedOption) {
                        try {
                            Triplet<String, CustomHudDataHandler.CustomHud, Integer> dataHud = Triplet.of(
                                    editCustomHUDWidget.currentSelectedHud,
                                    CustomHudDataHandler.instance().getCustomHudData().customHudRawDataList.get(editCustomHUDWidget.currentSelectedHud),
                                    FishOnMCExtras.HUD_VERSION
                            );

                            String rawData = Base64.getEncoder().encodeToString(
                                    TextHelper.compress(new GsonBuilder().create().toJson(dataHud))
                            );

                            String dataToCopy = "**Custom HUD: **" + selectedHudId + "\n" +
                                    "```\n" +
                                    rawData + "\n" +
                                    "```\n" +
                                    "-# Using HUD version: " + "`v" + FishOnMCExtras.HUD_VERSION + "`";

                            this.minecraft.keyboardHandler.setClipboard(dataToCopy);

                            SystemToast.add(this.minecraft.getToastManager(),
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("FOE+"),
                                    Component.literal("Exported HUD on your clipboard"));
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
                "Custom HUDs"
        );

        CustomHudDataHandler.instance().getCustomHudData().customHudRawDataList.forEach((id, ignored) -> {
            ButtonListWidget.ButtonEntry buttonEntry = createHudEntry(id);

            buttonList.addEntry(buttonEntry);
            buttonEntryMap.put(id, buttonEntry);
        });

        return buttonList;
    }

    private ButtonListWidget.ButtonEntry createHudEntry(String id) {
        return new ButtonListWidget.ButtonEntry(
                new SmallButtonBuilder(
                        button -> {
                            selectedHudId = id;
                            editCustomHUDWidget.selectHud(
                                    id,
                                    CustomHudDataHandler.instance().getCustomHudData().customHudRawDataList.get(id));
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
        if(editCustomHUDWidget.hasSelectedOption) {
            if(editCustomHUDWidget.newName.isBlank()) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("HUD name is empty"));

                return false;
            }

            if(
                    !Objects.equals(editCustomHUDWidget.currentSelectedHud, editCustomHUDWidget.newName)
                            && CustomHudDataHandler.instance().getCustomHudData().customHudRawDataList.containsKey(editCustomHUDWidget.newName)
            ) {
                SystemToast.add(this.minecraft.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("FOE+"),
                        Component.literal("HUD name already exist"));

                return false;
            }

            CustomHudDataHandler.instance().updateHud(
                    editCustomHUDWidget.currentSelectedHud,
                    editCustomHUDWidget.newName,
                    editCustomHUDWidget.scale,
                    editCustomHUDWidget.showBackground,
                    editCustomHUDWidget.showBars,
                    editCustomHUDWidget.showElement,
                    editCustomHUDWidget.getEntries()
                            .stream()
                            .map(lineEntry -> Triplet.of(lineEntry.lineString, lineEntry.isCentre, lineEntry.isSmall))
                            .toList());

            ButtonListWidget.ButtonEntry entry = buttonEntryMap.remove(selectedHudId);
            int index = buttonList.entryAt(entry);
            buttonList.removeEntry(entry);

            selectedHudId = editCustomHUDWidget.newName;

            ButtonListWidget.ButtonEntry buttonEntry = createHudEntry(selectedHudId);
            buttonEntryMap.put(selectedHudId, buttonEntry);
            buttonList.addEntryAtPos(buttonEntry, index);
            buttonList.setSelected(buttonEntry);

            editCustomHUDWidget.selectHud(selectedHudId, CustomHudDataHandler.instance().getCustomHudData().customHudRawDataList.get(selectedHudId));

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
                                Component.literal("HUD saved"),
                                Component.literal(selectedHudId));
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
                    if(editCustomHUDWidget.hasSelectedOption) {
                        editCustomHUDWidget.addNewEntry();
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
                        Component.literal("HUD saved"),
                        Component.literal(selectedHudId));
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
