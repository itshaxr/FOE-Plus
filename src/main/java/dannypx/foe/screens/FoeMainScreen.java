package dannypx.foe.screens;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.fetch.TabOverlayHandler;
import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * FOE's main menu (opened with O): "Welcome to FishOnMC-Extras" on the left, and buttons for the
 * config, the HUD editor and custom HUDs on the right. Ported from FOECollab's MainScreen (AGPL-3.0).
 * "More (FOE-R)" opens FOE-R's full menu for its triggers, timers and other creators.
 */
public class FoeMainScreen extends Screen {
    private static final int BUTTON_WIDTH = 130;
    private static final int BUTTON_HEIGHT = 24;

    private final Screen parent;

    public FoeMainScreen(Screen parent) {
        super(Component.literal("FoE Main Screen"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 + 4;

        addIconButton("FoE Config", x, height / 2 - BUTTON_HEIGHT - 8,
                b -> ConfigApiJava.INSTANCE.openScreen(FishOnMCExtras.MOD_ID), "Open the mod settings");
        addIconButton("Move HUD Elements", x, height / 2,
                b -> this.minecraft.setScreen(new MoveElementScreen(this)), "Move, anchor and resize your HUDs");
        addIconButton("Custom HUDs", x, height / 2 + BUTTON_HEIGHT + 8,
                b -> this.minecraft.setScreen(new CustomHudMakerScreen(this)), "Create your own HUD text");
        addIconButton("Auto Tip", x, height / 2 + (BUTTON_HEIGHT + 8) * 2,
                b -> this.minecraft.setScreen(new AutotipScreen(this)), "Tip reaction winners and chummers automatically");
        addIconButton("Pet Prices", x, height / 2 + (BUTTON_HEIGHT + 8) * 3,
                b -> this.minecraft.setScreen(new PetPriceScreen(this)), "Pet price table from the price sheet (U)");
        // Inspect: off (hold the key) or always on
        this.addRenderableWidget(Button.builder(inspectLabel(), b -> {
                    dannypx.foe.handler.logic.KeyBindHandler.toggleInspectAlwaysOn();
                    b.setMessage(inspectLabel());
                })
                .bounds(width / 2 - 4 - 130, height / 2 - 50, 130, 20)
                .tooltip(Tooltip.create(Component.literal("Always On: pet %, fish details and armor rolls show without holding the inspect key. Off: hold the key to inspect.")))
                .build());
        // Stats tools
        this.addRenderableWidget(Button.builder(Component.literal("Import FOE stats"), b -> dannypx.foe.handler.io.FoeStatsImporter.importNow(false))
                .bounds(width / 2 - 4 - 130, height / 2 + 26, 130, 20)
                .tooltip(Tooltip.create(Component.literal("Copy dry streaks, fish time and fish/hour from a previous FOE install")))
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Import FOE-R stats"), b -> dannypx.foe.handler.io.FoerStatsImporter.importNow())
                .bounds(width / 2 - 4 - 130, height / 2 + 48, 130, 20)
                .tooltip(Tooltip.create(Component.literal("Use the stats of another FOE-R install (another profile or .minecraft) if it has more fish. Your current file is backed up first.")))
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Import dry streaks"), b -> dannypx.foe.handler.io.DryStreakImporter.importNow())
                .bounds(width / 2 - 4 - 130, height / 2 + 70, 130, 20)
                .tooltip(Tooltip.create(Component.literal("Take your dry streaks from your newest FOE or FOE-R install (this or another Minecraft profile). Nothing else changes.")))
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Reset fish timer"), b -> dannypx.foe.handler.logic.FishSessionHandler.instance().reset())
                .bounds(width / 2 - 4 - 130, height / 2 + 92, 130, 20)
                .tooltip(Tooltip.create(Component.literal("Start fish time and fish/hour from zero")))
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("More (FOE-R)"), b -> this.minecraft.setScreen(new MainScreen(this)))
                .bounds(x, height / 2 + (BUTTON_HEIGHT + 8) * 4, BUTTON_WIDTH, 20)
                .tooltip(Tooltip.create(Component.literal("FOE-R's full menu: triggers, timers, notifications, snippets")))
                .build());
    }

    private static Component inspectLabel() {
        boolean on = dannypx.foe.config.Configs.keyBindConfig.inspectAlwaysOn.get();
        return Component.literal("Inspect: ").append(on
                ? Component.literal("Always On").withStyle(net.minecraft.ChatFormatting.GREEN)
                : Component.literal("Hold " + dannypx.foe.helper.KeyBindHelper.getKeyString(dannypx.foe.config.Configs.keyBindConfig.inspectKeybind)).withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    private void addIconButton(String label, int x, int y, Button.OnPress onPress, String tooltip) {
        // Leading spaces leave room for the item icon drawn in render().
        this.addRenderableWidget(Button.builder(Component.literal("     " + label), onPress)
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal(tooltip)))
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);

        int centerX = width / 2;
        int centerY = height / 2;
        int lineHeight = font.lineHeight + 1;
        int startY = centerY - (4 * lineHeight) / 2;

        guiGraphics.vLine(centerX, centerY - 36, centerY + 36, 0xFFFFFFFF);

        Component welcome = Component.literal("Welcome to FishOnMC-Extras");
        Component player = TabOverlayHandler.instance().getPlayerName();
        Component version = Component.literal(FishOnMCExtras.VERSION);
        guiGraphics.drawString(font, welcome, centerX - 4 - font.width(welcome), startY, 0xFFFFFFFF, true);
        guiGraphics.drawString(font, player, centerX - 4 - font.width(player), startY + lineHeight, 0xFFFFFFFF, true);
        guiGraphics.drawString(font, version, centerX - 4 - font.width(version), startY + lineHeight * 3, 0xFFAAAAAA, true);

        int x = centerX + 4 + 6;
        guiGraphics.renderItem(new ItemStack(Items.COMMAND_BLOCK), x, height / 2 - BUTTON_HEIGHT - 8 + 4);
        guiGraphics.renderItem(new ItemStack(Items.STRUCTURE_VOID), x, height / 2 + 4);
        guiGraphics.renderItem(new ItemStack(Items.OAK_SIGN), x, height / 2 + BUTTON_HEIGHT + 8 + 4);
        guiGraphics.renderItem(new ItemStack(Items.GOLD_NUGGET), x, height / 2 + (BUTTON_HEIGHT + 8) * 2 + 4);
        guiGraphics.renderItem(new ItemStack(Items.EMERALD), x, height / 2 + (BUTTON_HEIGHT + 8) * 3 + 4);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
