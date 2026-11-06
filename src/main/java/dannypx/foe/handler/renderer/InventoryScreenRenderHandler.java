package dannypx.foe.handler.renderer;

import dannypx.foe.handler.ScreenHandler;
import dannypx.foe.handler.logic.LoadingHandler;
import dannypx.foe.handler.store.ConstantDataHandler;
import dannypx.foe.handler.store.CustomButtonDataHandler;
import dannypx.foe.handler.store.StatsDataHandler;
import dannypx.foe.helper.GuiGraphicsHelper;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.screens.PlayerHatEditorScreen;
import dannypx.foe.screens.RodEditorScreen;
import dannypx.foe.screens.widget.SmallButtonBuilder;
import dannypx.foe.type.StringStyle;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.config.Configs;
import dannypx.foe.screens.CustomButtonMakerScreen;
import dannypx.foe.screens.MainScreen;
import dannypx.foe.screens.element.*;
import dannypx.foe.screens.interfaces.ScreenConstants;
import dannypx.foe.screens.widget.StatListWidget;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.CommonColors;
import java.util.*;

public class InventoryScreenRenderHandler extends ScreenHandler {
    private static InventoryScreenRenderHandler INSTANCE = new InventoryScreenRenderHandler();

    public static InventoryScreenRenderHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new InventoryScreenRenderHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private final Font font = minecraft.font;

    List<Pair<String, Element>> elements = new ArrayList<>();
    List<AbstractWidget> widgets = new ArrayList<>();

    AbstractWidget buttonMenuToggle;
    AbstractWidget addFriendButton;
    AbstractWidget buttonMenuEditor;
    List<AbstractWidget> buttons = new ArrayList<>();
    Pair<String, Element> buttonBox;
    final int buttonsPerRow = 8;
    int buttonBoxRows = 0;
    int buttonSize = 18;
    int buttonSizeAndPadding = 20;

    private StatListWidget statList;
    private final int STAT_WIDTH = 160;

    private final int INVENTORY_TRANSLATION = 94;
    private final int INVENTORY_TOP = 83;
    private final int INVENTORY_HEIGHT = 166;
    //endregion

    //region Methods
    public void init(Screen screen) {
        buttonBoxRows = (CustomButtonDataHandler.instance().getCustomButtonData().buttonList.getOrDefault(CustomButtonDataHandler.CustomButtonDataModel.INVENTORY_SCREEN, Pair.of(new ArrayList<>(), false)).value1().size() + buttonsPerRow - 1) / buttonsPerRow;

        this.initWidgets(screen);
        this.initElements();
    }

    public void render(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
        if(LoadingHandler.instance().isLoadingDone()
                && Configs.mainConfig.enableMod.get()
        ) {
            this.renderButtonHelp(guiGraphics, true, false);

            elements.forEach(element -> element.value2().render(guiGraphics));
            if(this.statList != null
                    && Configs.inventoryScreenConfig.showFishingStatsList.get()
            ) {
                this.statList.render(guiGraphics, mouseX, mouseY, tickDelta);
                this.renderStatBoxHeaderString(guiGraphics);
            }
            this.renderButtonBoxString(guiGraphics);
            if(Configs.inventoryScreenConfig.showMoonTimer.get()) this.renderMoonTimer(guiGraphics);
        }
    }

    /// The Timers & Crew box left of the inventory (moon timer from FOECollab's MoonTimerWidget).
    private void renderMoonTimer(GuiGraphics guiGraphics) {
        int right = minecraft.getWindow().getGuiScaledWidth() / 2 - 88 - 4;
        int top = minecraft.getWindow().getGuiScaledHeight() / 2 - INVENTORY_TOP;
        int maxBottom = minecraft.getWindow().getGuiScaledHeight() / 2 + INVENTORY_TOP + 2;
        TimersCrewBox.render(guiGraphics, font, right, top, maxBottom, addFriendButton);
    }

    private void renderButtonBoxString(GuiGraphics guiGraphics) {
        if(CustomButtonDataHandler.instance().getCustomButtonData().buttonList.getOrDefault(CustomButtonDataHandler.CustomButtonDataModel.INVENTORY_SCREEN, Pair.of(new ArrayList<>(), false)).value2()
                && buttonBoxRows == 0
        ) {
            guiGraphics.drawCenteredString(font, Component.literal("You have no custom buttons").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY),
                    minecraft.getWindow().getGuiScaledWidth() / 2,
                    minecraft.getWindow().getGuiScaledHeight() / 2 + INVENTORY_HEIGHT / 2 + 8 - font.lineHeight / 2,
                    CommonColors.WHITE);
        }
    }

    private void renderStatBoxHeaderString(GuiGraphics guiGraphics) {
        Component headerText = Component.literal("Fishing Statistics").withStyle(ChatFormatting.BOLD);
        int headerWidth = font.width(
                Component.literal(TextHelper.smallCaps(headerText.getString())).setStyle(headerText.getStyle())
        );

        GuiGraphicsHelper.drawString(guiGraphics, font, headerText,
                minecraft.getWindow().getGuiScaledWidth() / 2 + INVENTORY_TRANSLATION
                        + (STAT_WIDTH - ((STAT_WIDTH / 4) / 3)) / 2 - headerWidth / 2,
                minecraft.getWindow().getGuiScaledHeight() / 2
                        - INVENTORY_TOP - 10 + 4 - font.lineHeight / 2,
                StringStyle.SHADOW, StringStyle.MIDDLE, StringStyle.HAS_CUSTOM_FONT, StringStyle.SMALL_CAPS
        );
    }

    private void initWidgets(Screen screen) {
        if(LoadingHandler.instance().isLoadingDone()
                && Configs.mainConfig.enableMod.get()
        ) {
            widgets.clear();
            buttons.clear();

            if(Configs.inventoryScreenConfig.showFishingStatsList.get()) widgets.add(getStatList());

            // The + next to "Online" in the Timers & Crew box: starts /foe friend add in chat.
            addFriendButton = new SmallButtonBuilder(0, 0, 10, 10,
                    button -> minecraft.setScreen(new net.minecraft.client.gui.screens.ChatScreen("/foe friend add ", false)))
                    .icon("+")
                    .tooltip(Tooltip.create(Component.literal("Add a friend (they must be online)")))
                    .build();
            addFriendButton.visible = false;
            if(Configs.inventoryScreenConfig.showMoonTimer.get()) widgets.add(addFriendButton);

            widgets.add(new SmallButtonBuilder(
                    minecraft.getWindow().getGuiScaledWidth() / 2 + 66,
                    minecraft.getWindow().getGuiScaledHeight() / 2 - 23,
                    14, 14,
                    button -> minecraft.setScreen(new MainScreen(minecraft.screen)))
                    .icon("F")
                    .tooltip(Tooltip.create(Component.literal("Open FOER Menu")))
                    .build());

            widgets.add(new SmallButtonBuilder(
                    minecraft.getWindow().getGuiScaledWidth() / 2 - 88,
                    minecraft.getWindow().getGuiScaledHeight() / 2 - 74,
                    7, 13,
                    button -> minecraft.setScreen(new PlayerHatEditorScreen(minecraft.screen)))
                    .icon("⏴")
                    .tooltip(Tooltip.create(Component.literal("Edit Hat Position")))
                    .build());

            widgets.add(new SmallButtonBuilder(
                    minecraft.getWindow().getGuiScaledWidth() / 2 - 88,
                    minecraft.getWindow().getGuiScaledHeight() / 2 + 61,
                    7, 13,
                    button -> minecraft.setScreen(new RodEditorScreen(minecraft.screen)))
                    .icon("⏴")
                    .tooltip(Tooltip.create(Component.literal("Edit Rod Position")))
                    .build());

            for (int i = 0; i < CustomButtonDataHandler.instance().getCustomButtonData().buttonList.getOrDefault(CustomButtonDataHandler.CustomButtonDataModel.INVENTORY_SCREEN, Pair.of(new ArrayList<>(), false)).value1().size(); i++) {
                CustomButtonDataHandler.CustomButton button = CustomButtonDataHandler.instance().getCustomButtonData().buttonList.getOrDefault(CustomButtonDataHandler.CustomButtonDataModel.INVENTORY_SCREEN, Pair.of(new ArrayList<>(), false)).value1().get(i);
                if(button.showButton) {
                    int row = i / buttonsPerRow;
                    int column = i % buttonsPerRow;

                    MutableComponent buttonDrawerTooltip = TextHelper.parseLegacyWithStyle(button.name.replace("&", "§")).value1();

                    if(!button.description.isBlank()) {
                        buttonDrawerTooltip.append(Component.literal("\n\n")).append(TextHelper.parseLegacyWithStyle(button.description.replace("&", "§")).value1());
                    }

                    buttons.add(new SmallButtonBuilder(
                            minecraft.getWindow().getGuiScaledWidth() / 2 - 84 + 5 + (column * 20) + 1,
                            minecraft.getWindow().getGuiScaledHeight() / 2 + INVENTORY_HEIGHT / 2 + (row * 20) + 1,
                            buttonSize, buttonSize,
                            (buttonWidget) -> {
                                if(minecraft.player != null) {
                                    minecraft.player.connection.sendCommand(button.action.substring(1));
                                }
                            })
                            .icon(button.icon)
                            .tooltip( Tooltip.create(buttonDrawerTooltip))
                            .build());
                }
            }


            buttonMenuEditor = getButtonMenuEditorButton(screen);

            if(CustomButtonDataHandler.instance().getCustomButtonData().buttonList.getOrDefault(CustomButtonDataHandler.CustomButtonDataModel.INVENTORY_SCREEN, Pair.of(new ArrayList<>(), false)).value2()) {
                buttonMenuToggle = getCloseButtonMenuButton(screen);
                widgets.add(buttonMenuEditor);
                widgets.addAll(buttons);
            } else {
                buttonMenuToggle = getOpenButtonMenuButton(screen);
            }
            widgets.add(buttonMenuToggle);

            widgets.forEach(Screens.getButtons(screen)::add);
        }
    }

    public AbstractWidget getOpenButtonMenuButton(Screen screen) {
        return new SmallButtonBuilder(
                minecraft.getWindow().getGuiScaledWidth() / 2 - 20,
                minecraft.getWindow().getGuiScaledHeight() / 2 + INVENTORY_HEIGHT / 2 - 3,
                40, 12,
                (button) -> {
                    Screens.getButtons(screen).remove(buttonMenuToggle);
                    buttonMenuToggle = getCloseButtonMenuButton(screen);
                    Screens.getButtons(screen).add(buttonMenuToggle);

                    Screens.getButtons(screen).add(buttonMenuEditor);

                    elements.add(buttonBox);
                    Screens.getButtons(screen).addAll(buttons);

                    CustomButtonDataHandler.instance().updateButton(CustomButtonDataHandler.CustomButtonDataModel.INVENTORY_SCREEN, true);
                })
                .icon("⏷")
                .tooltip(Tooltip.create(Component.literal("Open Button Menu")))
                .build();
    }

    public AbstractWidget getCloseButtonMenuButton(Screen screen) {
        return new SmallButtonBuilder(
                minecraft.getWindow().getGuiScaledWidth() / 2 - 20,
                buttonBoxRows != 0 ? minecraft.getWindow().getGuiScaledHeight() / 2 + INVENTORY_HEIGHT / 2 + (buttonBoxRows * 20) + 1
                        : minecraft.getWindow().getGuiScaledHeight() / 2 + INVENTORY_HEIGHT / 2 + 17,
                40, 12,
                (button) -> {
                    Screens.getButtons(screen).remove(buttonMenuToggle);
                    buttonMenuToggle = getOpenButtonMenuButton(screen);
                    Screens.getButtons(screen).add(buttonMenuToggle);

                    Screens.getButtons(screen).remove(buttonMenuEditor);

                    elements.remove(buttonBox);
                    Screens.getButtons(screen).removeAll(buttons);

                    CustomButtonDataHandler.instance().updateButton(CustomButtonDataHandler.CustomButtonDataModel.INVENTORY_SCREEN, false);
                })
                .icon("⏶")
                .tooltip(Tooltip.create(Component.literal("Close Button Menu")))
                .build();
    }

    public AbstractWidget getButtonMenuEditorButton(Screen screen) {
        return new SmallButtonBuilder(
                minecraft.getWindow().getGuiScaledWidth() / 2 - 20 - 12 - PADDING_QUART,
                buttonBoxRows != 0 ? minecraft.getWindow().getGuiScaledHeight() / 2 + INVENTORY_HEIGHT / 2 + (buttonBoxRows * 20) + 1
                        : minecraft.getWindow().getGuiScaledHeight() / 2 + INVENTORY_HEIGHT / 2 + 17,
                12, 12,
                button -> minecraft.setScreen(new CustomButtonMakerScreen(minecraft.screen, CustomButtonDataHandler.CustomButtonDataModel.INVENTORY_SCREEN)))
                .icon("B")
                .tooltip(Tooltip.create(Component.literal("Edit Inventory Buttons")))
                .build();
    }

    public boolean onMouseScrolled(Screen screen, double mouseX, double mouseY, double horizontalAmount, double verticalAmount, boolean consumed) {
        if(Configs.inventoryScreenConfig.showMoonTimer.get()) TimersCrewBox.mouseScrolled(mouseX, mouseY, verticalAmount);
        if(this.statList != null
                && Configs.inventoryScreenConfig.showFishingStatsList.get()
                && this.statList.isMouseOver(mouseX, mouseY)
        ) {
            this.statList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        return false;
    }

    private AbstractWidget getStatList() {
        statList = new StatListWidget(
                minecraft,
                STAT_WIDTH,
                INVENTORY_HEIGHT - 16 * 2,
                minecraft.getWindow().getGuiScaledWidth() / 2 + INVENTORY_TRANSLATION + ScreenConstants.PADDING_HALF,
                minecraft.getWindow().getGuiScaledHeight() / 2 - INVENTORY_TOP + 16 * 1,
                16
        );

        // Fish Data
        statList.addEntry(new StatListWidget.StatEntry(Component.literal("-- Fishes --").withStyle(style -> style.withBold(true)), true));
        statList.addEntry(new StatListWidget.StatEntry(Component.literal(""), Component.literal("Caught").withStyle(ChatFormatting.GRAY), Component.empty(), Component.literal("Dryst.").withStyle(ChatFormatting.GRAY), List.of(), false));
        statList.addEntry(new StatListWidget.StatEntry(
                Component.literal(""),
                Component.literal("Total"),
                TextHelper.literal(StatsDataHandler.instance().getStatsData().fishTotal),
                Component.literal(""),
                List.of(),
                false));
        statList.addEntry(new StatListWidget.StatEntry(Component.literal(""), true));
        StatsDataHandler.instance().getStatsData().fishData.forEach((category, fieldStats) -> {
            fieldStats.forEach((field, stat) -> {
                if(Objects.equals(field, "normal")) return;
                statList.addEntry(new StatListWidget.StatEntry(Component.literal(category),
                        ConstantDataHandler.instance().getConstantData().fishData.getOrDefault(category, new HashMap<>()).getOrDefault(field, Component.literal(field)),
                        TextHelper.literal(stat.amount()),
                        TextHelper.literal(StatsDataHandler.instance().getStatsData().fishTotal - stat.caughtOn()),
                        List.of(),
                        false
                ));
            });
            statList.addEntry(new StatListWidget.StatEntry(Component.literal(""), true));
        });

        statList.addEntry(new StatListWidget.StatEntry(Component.literal(""), true));
        statList.addEntry(new StatListWidget.StatEntry(Component.literal("-- Pets --").withStyle(style -> style.withBold(true)), true));
        statList.addEntry(new StatListWidget.StatEntry(Component.literal(""), Component.literal("Caught").withStyle(ChatFormatting.GRAY), Component.empty(), Component.literal("Dryst.").withStyle(ChatFormatting.GRAY), List.of(), false));
        statList.addEntry(new StatListWidget.StatEntry(
                Component.literal(""),
                Component.literal("Total"),
                TextHelper.literal(StatsDataHandler.instance().getStatsData().petTotal),
                Component.literal(""),
                List.of(),
                false));
        statList.addEntry(new StatListWidget.StatEntry(Component.literal(""), true));
        StatsDataHandler.instance().getStatsData().petData.forEach((category, fieldStats) -> {
            fieldStats.forEach((field, stat) -> statList.addEntry(new StatListWidget.StatEntry(Component.literal(category),
                    ConstantDataHandler.instance().getConstantData().petData.getOrDefault(category, new HashMap<>()).getOrDefault(field, Component.literal(field)),
                    TextHelper.literal(stat.amount()),
                    TextHelper.literal(StatsDataHandler.instance().getStatsData().fishTotal - stat.caughtOn()),
                    List.of(),
                    false
            )));
            statList.addEntry(new StatListWidget.StatEntry(Component.literal("").withStyle(ChatFormatting.GRAY), true));
        });

        statList.addEntry(new StatListWidget.StatEntry(Component.literal("").withStyle(ChatFormatting.GRAY), true));
        statList.addEntry(new StatListWidget.StatEntry(Component.literal("-- Other Items --").withStyle(style -> style.withBold(true)), true));
        statList.addEntry(new StatListWidget.StatEntry(Component.literal(""), Component.literal("Caught").withStyle(ChatFormatting.GRAY), Component.empty(), Component.literal("Dryst.").withStyle(ChatFormatting.GRAY), List.of(), false));
        StatsDataHandler.instance().getStatsData().itemData.forEach((category, stat) -> statList.addEntry(new StatListWidget.StatEntry(Component.literal(category),
                Component.literal(category),
                TextHelper.literal(stat.amount()),
                TextHelper.literal(StatsDataHandler.instance().getStatsData().fishTotal - stat.caughtOn()),
                ConstantDataHandler.instance().getConstantData().itemData.getOrDefault(category, List.of()),
                false
        )));

        statList.setScrollAmount(16);
        return statList;
    }

    private void initElements() {
        elements.clear();

        if(this.statList != null
                && Configs.inventoryScreenConfig.showFishingStatsList.get()
        ) {
            elements.add(Pair.of("header_box", new BoxElement(minecraft.getWindow().getGuiScaledWidth() / 2 + INVENTORY_TRANSLATION + (STAT_WIDTH - ((STAT_WIDTH / 4) / 3)) / 2 - 65,
                    minecraft.getWindow().getGuiScaledHeight() / 2 - INVENTORY_TOP - 20 + 4,
                    130, 20, true, false)));

            elements.add(Pair.of("stat_box", new BoxElement(minecraft.getWindow().getGuiScaledWidth() / 2 + INVENTORY_TRANSLATION,
                    minecraft.getWindow().getGuiScaledHeight() / 2 - INVENTORY_TOP,
                    STAT_WIDTH - ((STAT_WIDTH / 4) / 3), INVENTORY_HEIGHT, false, false)));
        }

        buttonBox = Pair.of("button_box", new BoxElement(minecraft.getWindow().getGuiScaledWidth() / 2 - 84,
                minecraft.getWindow().getGuiScaledHeight() / 2 + INVENTORY_HEIGHT / 2 - 5,
                0,
                170, Math.max(11 + buttonBoxRows * buttonSizeAndPadding, 11 + 16), false, false, false, true, true, true));
        if(CustomButtonDataHandler.instance().getCustomButtonData().buttonList.getOrDefault(CustomButtonDataHandler.CustomButtonDataModel.INVENTORY_SCREEN, Pair.of(new ArrayList<>(), false)).value2()) {
            elements.add(buttonBox);
        }
    }
    //endregion

    //region Dev
    /// Field, Pair<Value, Tooltip>
    @Override
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(

        );
    }
    //endregion
}
