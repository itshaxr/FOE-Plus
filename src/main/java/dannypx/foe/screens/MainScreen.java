package dannypx.foe.screens;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.helper.GuiGraphicsHelper;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.screens.element.BoxElement;
import dannypx.foe.screens.element.Element;
import dannypx.foe.screens.widget.SmallButtonBuilder;
import dannypx.foe.type.StringStyle;
import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonColors;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MainScreen extends DefaultModScreen {
    //region Fields
    private static final Identifier ICON_TEXTURE = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "elements/icon");
    private static final int iconSize = 150;

    private static final int BUTTON_WIDTH = 64;
    private static final int BUTTON_HEIGHT = 16;

    List<Element> elements = new ArrayList<>();
    //endregion

    //region Methods
    public MainScreen(Screen parent) {
        super(parent, Component.literal("Main Screen"));
    }

    @Override
    protected void init() {
        super.init();
        this.initElements();
        this.initWidgets();
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);

        GuiGraphicsHelper.drawString(
                guiGraphics,
                font,
                Component.literal("HUD Elements"),
                width / 2 - BUTTON_WIDTH - PADDING_QUART - BUTTON_WIDTH,
                height / 2 - BUTTON_HEIGHT / 2 - font.lineHeight - PADDING - BUTTON_HEIGHT - font.lineHeight,
                StringStyle.SMALL_CAPS, StringStyle.MIDDLE, StringStyle.SHADOW
        );

        GuiGraphicsHelper.drawString(
                guiGraphics,
                font,
                Component.literal("Triggers"),
                width / 2 - BUTTON_WIDTH - PADDING_QUART - BUTTON_WIDTH,
                height / 2 - BUTTON_HEIGHT / 2 - font.lineHeight,
                StringStyle.SMALL_CAPS, StringStyle.MIDDLE, StringStyle.SHADOW
        );

        GuiGraphicsHelper.drawString(
                guiGraphics,
                font,
                Component.literal("Observers"),
                width / 2 - BUTTON_WIDTH - PADDING_QUART - BUTTON_WIDTH,
                height / 2 - BUTTON_HEIGHT / 2 + BUTTON_HEIGHT + PADDING,
                StringStyle.SMALL_CAPS, StringStyle.MIDDLE, StringStyle.SHADOW
        );

        GuiGraphicsHelper.drawString(
                guiGraphics,
                font,
                Component.literal("Creators"),
                width / 2 - BUTTON_WIDTH - font.width(TextHelper.smallCaps("Creators")) / 2 + BUTTON_WIDTH / 2,
                height / 2 - BUTTON_HEIGHT / 2 - font.lineHeight - PADDING - BUTTON_HEIGHT - font.lineHeight - PADDING - font.lineHeight + 1,
                StringStyle.SMALL_CAPS, StringStyle.MIDDLE, StringStyle.SHADOW
        );

        guiGraphics.drawString(font, Component.literal("HUD Version: v" + FishOnMCExtras.HUD_VERSION).withStyle(ChatFormatting.DARK_GRAY), PADDING_QUART, height - (font.lineHeight + PADDING_QUART) * 2, CommonColors.WHITE, true);
        guiGraphics.drawString(font, Component.literal("Chat Trigger Version: v" + FishOnMCExtras.CHAT_TRIGGER_VERSION).withStyle(ChatFormatting.DARK_GRAY), PADDING_QUART, height - (font.lineHeight + PADDING_QUART) * 3, CommonColors.WHITE, true);
        guiGraphics.drawString(font, Component.literal("Timer Version: v" + FishOnMCExtras.TIMER_VERSION).withStyle(ChatFormatting.DARK_GRAY), PADDING_QUART, height - (font.lineHeight + PADDING_QUART) * 4, CommonColors.WHITE, true);
        guiGraphics.drawString(font, Component.literal("Notification Version: v" + FishOnMCExtras.NOTIFICATION_VERSION).withStyle(ChatFormatting.DARK_GRAY), PADDING_QUART, height - (font.lineHeight + PADDING_QUART) * 5, CommonColors.WHITE, true);
        guiGraphics.drawString(font, Component.literal("Button Version: v" + FishOnMCExtras.BUTTON_VERSION).withStyle(ChatFormatting.DARK_GRAY), PADDING_QUART, height - (font.lineHeight + PADDING_QUART) * 6, CommonColors.WHITE, true);
        guiGraphics.drawString(font, Component.literal("Chat Notification Version: v" + FishOnMCExtras.CHAT_NOTIFICATION_VERSION).withStyle(ChatFormatting.DARK_GRAY), PADDING_QUART, height - (font.lineHeight + PADDING_QUART) * 7, CommonColors.WHITE, true);
        guiGraphics.drawString(font, Component.literal("Event Trigger Version: v" + FishOnMCExtras.EVENT_TRIGGER_VERSION).withStyle(ChatFormatting.DARK_GRAY), PADDING_QUART, height - (font.lineHeight + PADDING_QUART) * 8, CommonColors.WHITE, true);
        guiGraphics.drawString(font, Component.literal("Tracker Version: v" + FishOnMCExtras.TRACKER_VERSION).withStyle(ChatFormatting.DARK_GRAY), PADDING_QUART, height - (font.lineHeight + PADDING_QUART) * 9, CommonColors.WHITE, true);
        guiGraphics.drawString(font, Component.literal("HUD Icon Version: v" + FishOnMCExtras.HUD_VERSION).withStyle(ChatFormatting.DARK_GRAY), PADDING_QUART, height - (font.lineHeight + PADDING_QUART) * 10, CommonColors.WHITE, true);
        guiGraphics.drawString(font, Component.literal("Snippet Version: v" + FishOnMCExtras.SNIPPET_VERSION).withStyle(ChatFormatting.DARK_GRAY), PADDING_QUART, height - (font.lineHeight + PADDING_QUART) * 11, CommonColors.WHITE, true);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
        super.renderBackground(guiGraphics, i, j, f);

        elements.forEach(element -> {
            element.render(guiGraphics);
        });
    }

    private void initWidgets() {
        List<AbstractWidget> widgets = new ArrayList<>();

        widgets.add(new SmallButtonBuilder(
                width / 2 - BUTTON_WIDTH - PADDING_QUART - BUTTON_WIDTH,
                height / 2 - BUTTON_HEIGHT / 2 - font.lineHeight - PADDING - BUTTON_HEIGHT,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                button -> this.minecraft.setScreen(new CustomHudMakerScreen(this.minecraft.screen)))
                .icon("HUD Text")
                .color(0xFF1E90FF)
                .tooltip(Tooltip.create(Component.literal("Open Custom HUD Text Creator screen")))
                .build());

        widgets.add(new SmallButtonBuilder(
                width / 2 - BUTTON_WIDTH,
                height / 2 - BUTTON_HEIGHT / 2 - font.lineHeight - PADDING - BUTTON_HEIGHT,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                button -> this.minecraft.setScreen(new CustomHudIconMakerScreen(this.minecraft.screen)))
                .icon("HUD Icon")
                .color(0xFF1E90FF)
                .tooltip(Tooltip.create(Component.literal("Open Custom HUD Icon Creator screen")))
                .build());

        widgets.add(new SmallButtonBuilder(
                width / 2 + PADDING_QUART,
                height / 2 - BUTTON_HEIGHT / 2 - font.lineHeight - PADDING - BUTTON_HEIGHT,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                button -> this.minecraft.setScreen(new MoveElementScreen(this.minecraft.screen)))
                .icon("Move")
                .color(0xFF00CA3A)
                .tooltip(Tooltip.create(Component.literal("Move HUD elements")))
                .build());

        widgets.add(new SmallButtonBuilder(
                width / 2 - BUTTON_WIDTH - PADDING_QUART - BUTTON_WIDTH,
                height / 2 - BUTTON_HEIGHT / 2,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                button -> this.minecraft.setScreen(new CustomChatTriggerMakerScreen(this.minecraft.screen)))
                .icon("Chat")
                .color(0xFF1E90FF)
                .tooltip(Tooltip.create(Component.literal("Open Custom Chat Trigger Creator screen")))
                .build());

        widgets.add(new SmallButtonBuilder(
                width / 2 - BUTTON_WIDTH,
                height / 2 - BUTTON_HEIGHT / 2,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                button -> this.minecraft.setScreen(new CustomEventTriggerMakerScreen(this.minecraft.screen)))
                .icon("Event")
                .color(0xFF1E90FF)
                .tooltip(Tooltip.create(Component.literal("Open Custom Event Trigger Creator screen")))
                .build());

        widgets.add(new SmallButtonBuilder(
                width / 2 + PADDING_QUART,
                height / 2 - BUTTON_HEIGHT / 2,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                button -> this.minecraft.setScreen(new CustomTimerMakerScreen(this.minecraft.screen)))
                .icon("Timer")
                .color(0xFF1E90FF)
                .tooltip(Tooltip.create(Component.literal("Open Custom Timer Creator screen")))
                .build());

        widgets.add(new SmallButtonBuilder(
                width / 2 - BUTTON_WIDTH - PADDING_QUART - BUTTON_WIDTH,
                height / 2 - BUTTON_HEIGHT / 2 + BUTTON_HEIGHT + PADDING + font.lineHeight,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                button -> this.minecraft.setScreen(new CustomNotificationMakerScreen(this.minecraft.screen)))
                .icon("Notif.")
                .color(0xFF1E90FF)
                .tooltip(Tooltip.create(Component.literal("Open Custom Notification Creator screen")))
                .build());

        widgets.add(new SmallButtonBuilder(
                width / 2 - BUTTON_WIDTH,
                height / 2 - BUTTON_HEIGHT / 2 + BUTTON_HEIGHT + PADDING + font.lineHeight,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                button -> this.minecraft.setScreen(new CustomChatNotificationMakerScreen(this.minecraft.screen)))
                .icon("Chat Notif.")
                .color(0xFF1E90FF)
                .tooltip(Tooltip.create(Component.literal("Open Custom Chat Notification Creator screen")))
                .build());

        widgets.add(new SmallButtonBuilder(
                width / 2 + PADDING_QUART,
                height / 2 - BUTTON_HEIGHT / 2 + BUTTON_HEIGHT + PADDING + font.lineHeight,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                button -> this.minecraft.setScreen(new CustomTrackerMakerScreen(this.minecraft.screen)))
                .icon("Tracker")
                .color(0xFF1E90FF)
                .tooltip(Tooltip.create(Component.literal("Open Custom Tracker Creator screen")))
                .build());

        widgets.add(new SmallButtonBuilder(
                width / 2 + PADDING_QUART + BUTTON_WIDTH + PADDING * 2 + PADDING_QUART,
                height / 2 - BUTTON_HEIGHT / 2 - font.lineHeight - PADDING - BUTTON_HEIGHT - font.lineHeight,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                button -> this.minecraft.setScreen(new CustomSnippetMakerScreen(this.minecraft.screen)))
                .icon("Snippet")
                .color(0xFF00CA3A)
                .tooltip(Tooltip.create(Component.literal("Open Placeholder Snippet editor")))
                .build());

        widgets.add(new SmallButtonBuilder(
                width / 2 + PADDING_QUART + BUTTON_WIDTH + PADDING * 2 + PADDING_QUART,
                height / 2 - BUTTON_HEIGHT / 2 + BUTTON_HEIGHT + PADDING + font.lineHeight - BUTTON_HEIGHT - PADDING_QUART,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                button -> FoeSettingsScreen.open(Configs.keyBindConfig))
                .icon("Keybinds")
                .color(0xFFCC0000)
                .tooltip(Tooltip.create(Component.literal("Open Mod keybinds")))
                .build());

        widgets.add(new SmallButtonBuilder(
                width / 2 + PADDING_QUART + BUTTON_WIDTH + PADDING * 2 + PADDING_QUART,
                height / 2 - BUTTON_HEIGHT / 2 + BUTTON_HEIGHT + PADDING + font.lineHeight,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                button -> FoeSettingsScreen.open(null))
                .icon("Config")
                .color(0xFFCC0000)
                .tooltip(Tooltip.create(Component.literal("Open Configuration screen")))
                .build());

        Component modVersion = Component.literal("Mod Version: v" + FishOnMCExtras.VERSION + " (Click for Changelog)").withStyle(ChatFormatting.YELLOW);
        widgets.add(
                new PlainTextButton(
                        PADDING_QUART,
                        height - font.lineHeight - PADDING_QUART,
                        font.width(modVersion),
                        font.lineHeight,
                        modVersion,
                        button -> minecraft.setScreen(new ChangelogScreen(this)),
                        font
                )
        );

        widgets.forEach(this::addRenderableWidget);
    }

    private void initElements() {
        elements.clear();
        elements.add(
                new BoxElement(
                        width / 2 - BUTTON_WIDTH - PADDING_HALF,
                        height / 2 - BUTTON_HEIGHT / 2 - font.lineHeight - PADDING - BUTTON_HEIGHT - font.lineHeight - PADDING - font.lineHeight - PADDING_HALF,
                        PADDING_HALF * 2 + BUTTON_WIDTH,
                        PADDING_HALF + font.lineHeight + 4,
                        false,
                        false
                )
        );

        elements.add(
                new BoxElement(
                        width / 2 - BUTTON_WIDTH - PADDING_QUART - BUTTON_WIDTH - PADDING,
                        height / 2 - BUTTON_HEIGHT / 2 - font.lineHeight - PADDING - BUTTON_HEIGHT - font.lineHeight - PADDING,
                        PADDING * 2 + BUTTON_WIDTH * 3 + PADDING_QUART * 2,
                        PADDING * 2 + BUTTON_HEIGHT * 3 + font.lineHeight * 3 + PADDING * 2,
                        false,
                        false
                )
        );

        elements.add(
                new BoxElement(
                        width / 2 + PADDING_QUART + BUTTON_WIDTH + PADDING + PADDING_QUART,
                        height / 2 - BUTTON_HEIGHT / 2 - font.lineHeight - PADDING - BUTTON_HEIGHT - font.lineHeight - PADDING,
                        PADDING * 2 + BUTTON_WIDTH,
                        PADDING * 2 + BUTTON_HEIGHT * 3 + font.lineHeight * 3 + PADDING * 2,
                        false,
                        false
                )
        );
    }
    //endregion
}
