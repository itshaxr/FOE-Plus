package dannypx.foe.screens;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.TextDisplayHandler;
import dannypx.foe.helper.NameTagMarker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pick the colour of the + after your name: a grid of colour swatches. The developer also gets
 * Rainbow and beta testers Green; those options only exist for their UUIDs.
 */
public class PlusColorScreen extends Screen {
    private static final int COLUMNS = 4;
    private static final int BUTTON_W = 108;
    private static final int BUTTON_H = 20;
    private static final int GAP = 4;
    private static final int SWATCH = 10;

    private final Screen parent;
    private final List<Option> options = new ArrayList<>();
    private int gridTop;

    /// One choice: its colour value (index, or SPECIAL), name, and the button showing it.
    private record Option(int value, String name, Button button) {}

    public PlusColorScreen(Screen parent) {
        super(Component.literal("+ Colour"));
        this.parent = parent;
    }

    private UUID me() {
        return this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
    }

    private boolean isDev() {
        return TextDisplayHandler.DEV_UUID.equals(me());
    }

    private boolean isBeta() {
        UUID me = me();
        return me != null && TextDisplayHandler.BETA_TESTERS.contains(me);
    }

    /// The option that's selected now (nothing picked yet = your special + if you have one, else gold).
    private int selected() {
        int value = Configs.rendererConfig.foePlusColor.get();
        if (NameTagMarker.isListColor(value)) return value;
        return isDev() || isBeta() ? NameTagMarker.SPECIAL : 0;
    }

    @Override
    protected void init() {
        options.clear();
        boolean special = isDev() || isBeta();
        int count = NameTagMarker.PLUS_COLORS.size() + (special ? 1 : 0);
        int rows = (count + COLUMNS - 1) / COLUMNS;
        int gridW = COLUMNS * BUTTON_W + (COLUMNS - 1) * GAP;
        int gridH = rows * BUTTON_H + (rows - 1) * GAP;
        int left = (width - gridW) / 2;
        gridTop = Math.max(40, (height - gridH) / 2 - 10);

        int i = 0;
        if (special) {
            addOption(i++, left, NameTagMarker.SPECIAL, isDev() ? "Rainbow" : "Green");
        }
        for (int c = 0; c < NameTagMarker.PLUS_COLORS.size(); c++) {
            addOption(i++, left, c, NameTagMarker.PLUS_COLORS.get(c).name());
        }

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(width / 2 - 50, gridTop + gridH + 16, 100, BUTTON_H).build());
    }

    private void addOption(int i, int left, int value, String name) {
        int x = left + (i % COLUMNS) * (BUTTON_W + GAP);
        int y = gridTop + (i / COLUMNS) * (BUTTON_H + GAP);
        Button button = Button.builder(Component.literal(name), b -> pick(value))
                .bounds(x, y, BUTTON_W, BUTTON_H).build();
        this.addRenderableWidget(button);
        options.add(new Option(value, name, button));
    }

    private void pick(int value) {
        Configs.rendererConfig.foePlusColor.accept(value);
        Configs.rendererConfig.save();
        TextDisplayHandler.instance().onOwnColorChanged();
        dannypx.foe.nearby_dispatcher.types.ModUserType.announceColor();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);
        guiGraphics.drawCenteredString(font, Component.literal("+ ᴄᴏʟᴏᴜʀ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                width / 2, gridTop - 30, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(font, Component.literal("The + after your name, for everyone with FOE+").withStyle(ChatFormatting.GRAY),
                width / 2, gridTop - 18, 0xFFFFFFFF);

        long now = System.currentTimeMillis();
        int current = selected();
        for (Option option : options) {
            Button b = option.button();
            // Colour swatch on the left of each button (rainbow cycles; Green for beta testers).
            int sx = b.getX() + 5, sy = b.getY() + (b.getHeight() - SWATCH) / 2;
            int rgb;
            if (option.value() == NameTagMarker.SPECIAL) {
                rgb = isDev() ? net.minecraft.util.Mth.hsvToRgb((now % 3000L) / 3000f, 0.85f, 1f) : 0x55FF55;
            } else {
                rgb = NameTagMarker.plusColor(option.value()).rgb();
            }
            guiGraphics.fill(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, 0xFF000000);
            guiGraphics.fill(sx, sy, sx + SWATCH, sy + SWATCH, 0xFF000000 | rgb);
            if (option.value() == current) {
                guiGraphics.renderOutline(b.getX() - 1, b.getY() - 1, b.getWidth() + 2, b.getHeight() + 2, 0xFFFFFFFF);
                guiGraphics.drawString(font, Component.literal("✔").withStyle(ChatFormatting.GREEN),
                        b.getX() + b.getWidth() - 12, b.getY() + (b.getHeight() - font.lineHeight) / 2 + 1, 0xFFFFFFFF, true);
            }
        }

        // Preview: your name with the + as others will see it.
        String name = this.minecraft.player != null ? this.minecraft.player.getName().getString() : this.minecraft.getUser().getName();
        Component sign;
        if (current == NameTagMarker.SPECIAL) sign = isDev() ? NameTagMarker.devMarker(now) : NameTagMarker.BETA_MARKER;
        else sign = NameTagMarker.colorMarker(current);
        Component preview = Component.literal(name).withStyle(ChatFormatting.WHITE).append(sign);
        int py = options.isEmpty() ? gridTop : options.getLast().button().getY() + BUTTON_H + 4;
        guiGraphics.drawCenteredString(font, Component.literal("Preview: ").withStyle(ChatFormatting.DARK_GRAY).append(preview), width / 2, py, 0xFFFFFFFF);
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
