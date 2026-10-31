package dannypx.foe.screens;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.helper.KeyBindHelper;
import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The FOE+ menu (opened with O): the FOE+ logo and a welcome line on top, then two tidy columns
 * of equal buttons, each with an icon. General on the left (settings, HUDs, tools), Stats on
 * the right (imports, fish timer, inspect), and "More (FOE-R)" / "Done" along the bottom.
 */
public class FoeMainScreen extends Screen {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "textures/gui/sprites/title/icon.png");
    // Where "FOE+" sits inside the 500x500 logo image.
    private static final int LOGO_U = 30, LOGO_V = 184, LOGO_W = 440, LOGO_H = 132, LOGO_TEX = 500;

    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW = 24;
    private static final int COLUMN_GAP = 10;
    private static final int ROWS = 5;

    private final Screen parent;
    private final List<IconButton> icons = new ArrayList<>();
    /// Room the icon takes on the left of a button (3 px margin, 16 px icon, 5 px gap).
    private static final int ICON_SPACE = 24;
    private int logoX, logoY, logoW, logoH;
    private int welcomeY, headerY;
    private int leftX, rightX;

    private record IconButton(Button button, ItemStack icon) {}

    /// A button whose label is centred in the space right of its icon, so the two never touch.
    private static final class LabelledButton extends Button {
        LabelledButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, supplier -> supplier.get());
        }

        @Override
        protected void renderContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
            this.renderDefaultSprite(guiGraphics);
            var font = net.minecraft.client.Minecraft.getInstance().font;
            int left = getX() + ICON_SPACE;
            int right = getX() + getWidth() - 4;
            int color = this.active ? 0xFFFFFFFF : 0xFFA0A0A0;
            int textX = left + Math.max(0, (right - left - font.width(getMessage())) / 2);
            guiGraphics.drawString(font, getMessage(), textX, getY() + (getHeight() - font.lineHeight) / 2 + 1, color, true);
        }
    }

    public FoeMainScreen(Screen parent) {
        super(Component.literal("FOE+"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        icons.clear();

        // Vertical layout: logo, welcome line, column headers, 5 rows, gap, bottom row.
        int fixed = 12 /*welcome*/ + 8 + 12 /*headers*/ + ROWS * ROW + 8 + BUTTON_HEIGHT;
        logoH = Math.max(24, Math.min(60, height - fixed - 24));
        logoW = logoH * LOGO_W / LOGO_H;
        int total = logoH + 6 + fixed;
        int top = Math.max(6, (height - total) / 2);

        logoX = (width - logoW) / 2;
        logoY = top;
        welcomeY = logoY + logoH + 6;
        headerY = welcomeY + 12 + 8;
        int rowsY = headerY + 12;

        int columnsWidth = BUTTON_WIDTH * 2 + COLUMN_GAP;
        leftX = (width - columnsWidth) / 2;
        rightX = leftX + BUTTON_WIDTH + COLUMN_GAP;

        // General
        add(leftX, rowsY, "Settings", Items.COMMAND_BLOCK, "All FOE+ settings",
                b -> ConfigApiJava.INSTANCE.openScreen(FishOnMCExtras.MOD_ID));
        add(leftX, rowsY + ROW, "Move HUD Elements", Items.STRUCTURE_VOID, "Move, anchor and resize your HUD panels",
                b -> this.minecraft.setScreen(new MoveElementScreen(this)));
        add(leftX, rowsY + ROW * 2, "Custom HUDs", Items.OAK_SIGN, "Make your own HUD text, or turn the built-in ones on",
                b -> this.minecraft.setScreen(new CustomHudMakerScreen(this)));
        add(leftX, rowsY + ROW * 3, "Auto Tip", Items.GOLD_NUGGET, "Tip reaction winners and chummers automatically (M)",
                b -> this.minecraft.setScreen(new AutotipScreen(this)));
        add(leftX, rowsY + ROW * 4, "Pet Prices", Items.EMERALD, "Pet price table from the price sheet (U)",
                b -> this.minecraft.setScreen(new PetPriceScreen(this)));

        // Stats
        add(rightX, rowsY, "Import Dry Streaks", Items.CLOCK, "Take your dry streaks from your newest FOE or FOE-R install. Nothing else changes.",
                b -> dannypx.foe.handler.io.DryStreakImporter.importNow());
        add(rightX, rowsY + ROW, "Import FOE Stats", Items.BOOK, "Dry streaks, fish time and fish/hour from a previous FOE install",
                b -> dannypx.foe.handler.io.FoeStatsImporter.importNow(false));
        add(rightX, rowsY + ROW * 2, "Import FOE-R Stats", Items.WRITABLE_BOOK, "Use another FOE-R install's stats if it has more fish (yours are backed up first)",
                b -> dannypx.foe.handler.io.FoerStatsImporter.importNow());
        add(rightX, rowsY + ROW * 3, "Reset Fish Timer", Items.COMPASS, "Start fish time and fish/hour from zero",
                b -> dannypx.foe.handler.logic.FishSessionHandler.instance().reset());
        Button inspect = add(rightX, rowsY + ROW * 4, "", Items.SPYGLASS,
                "Always On: pet %, fish details and armor rolls show without holding the inspect key",
                b -> {
                    dannypx.foe.handler.logic.KeyBindHandler.toggleInspectAlwaysOn();
                    b.setMessage(inspectLabel());
                });
        inspect.setMessage(inspectLabel());

        // Bottom row
        int bottomY = rowsY + ROWS * ROW + 8;
        add(leftX, bottomY, "More (FOE-R)", Items.CHEST, "FOE-R's full menu: triggers, timers, notifications, snippets",
                b -> this.minecraft.setScreen(new MainScreen(this)));
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(rightX, bottomY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private Button add(int x, int y, String label, net.minecraft.world.item.Item icon, String tooltip, Button.OnPress onPress) {
        Button button = new LabelledButton(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.literal(label), onPress);
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        this.addRenderableWidget(button);
        icons.add(new IconButton(button, new ItemStack(icon)));
        return button;
    }

    private static Component inspectLabel() {
        boolean on = Configs.keyBindConfig.inspectAlwaysOn.get();
        String key = KeyBindHelper.getKeyString(Configs.keyBindConfig.inspectKeybind);
        return Component.literal("Inspect: ").append(on
                ? Component.literal("Always On").withStyle(ChatFormatting.GREEN)
                : Component.literal("Hold " + capitalize(key.replaceFirst("(?i)^(left|right) ", ""))).withStyle(ChatFormatting.GRAY));
    }

    private static String capitalize(String text) {
        StringBuilder out = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);

        // Logo
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, logoX, logoY, LOGO_U, LOGO_V, logoW, logoH, LOGO_W, LOGO_H, LOGO_TEX, LOGO_TEX);

        // Welcome, <player name>
        String name = this.minecraft.player != null ? this.minecraft.player.getName().getString() : this.minecraft.getUser().getName();
        Component welcome = Component.literal("Welcome, ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(name).withStyle(ChatFormatting.WHITE));
        guiGraphics.drawCenteredString(font, welcome, width / 2, welcomeY, 0xFFFFFFFF);

        // Column headers with a thin rule under each
        drawHeader(guiGraphics, "ɢᴇɴᴇʀᴀʟ", leftX);
        drawHeader(guiGraphics, "ѕᴛᴀᴛѕ", rightX);

        // Icons inside the buttons
        for (IconButton icon : icons) {
            Button b = icon.button();
            guiGraphics.renderItem(icon.icon(), b.getX() + 3, b.getY() + (b.getHeight() - 16) / 2);
        }

        // Version, bottom right
        Component version = Component.literal("FOE+ " + FishOnMCExtras.VERSION).withStyle(ChatFormatting.DARK_GRAY);
        guiGraphics.drawString(font, version, width - font.width(version) - 4, height - font.lineHeight - 3, 0xFFFFFFFF, false);
    }

    private void drawHeader(GuiGraphics guiGraphics, String text, int x) {
        Component header = Component.literal(text).withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD);
        guiGraphics.drawCenteredString(font, header, x + BUTTON_WIDTH / 2, headerY, 0xFFFFFFFF);
        guiGraphics.fill(x, headerY + font.lineHeight + 1, x + BUTTON_WIDTH, headerY + font.lineHeight + 2, 0x60FFFFFF);
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
