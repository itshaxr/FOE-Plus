package dannypx.foe.screens;

import dannypx.foe.handler.store.AmuletStars;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/// The equipped-pet star's colour for each amulet, picked on the colour wheel (Default: the amulet's own colour).
public class AmuletStarsScreen extends Screen {
    private static final int W = 300, ROW = 22;
    private final Screen parent;
    private int scroll = 0, left, top, rowsFit;
    private List<String> amulets = List.of();

    public AmuletStarsScreen(Screen parent) {
        super(Component.literal("Amulet Stars"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        amulets = AmuletStars.all();
        left = width / 2 - W / 2;
        top = Math.max(8, height / 2 - 130);
        int bottom = Math.min(height - 8, height / 2 + 130);
        rowsFit = Math.max(1, (bottom - top - 64) / ROW);
        scroll = Math.max(0, Math.min(scroll, amulets.size() - rowsFit));
        int y = top + 36;
        for (int i = scroll; i < Math.min(amulets.size(), scroll + rowsFit); i++) {
            String name = amulets.get(i);
            String hex = AmuletStars.colour(name);
            int c = dannypx.foe.screens.element.hud.GameXpBar.parse(hex);
            Component label = c < 0 ? Component.literal("Default").withStyle(ChatFormatting.GRAY)
                    : Component.literal("★ " + hex.toUpperCase(java.util.Locale.ROOT)).withColor(c);
            addRenderableWidget(Button.builder(label, b -> minecraft.setScreen(new ColorPickerScreen(this, name + " star", hex,
                            AmuletStars.defaultColour(name),
                            v -> AmuletStars.setColour(name, v), AmuletStars::save)))
                    .bounds(left + W - 12 - 110 - 22, y, 110, 18).build());
            addRenderableWidget(Button.builder(Component.literal("×"), b -> { AmuletStars.setColour(name, ""); rebuildWidgets(); })
                    .bounds(left + W - 12 - 20, y, 20, 18).build());
            y += ROW;
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(left + W - 12 - 60, bottom - 24, 60, 18).build());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int next = Math.max(0, Math.min(scroll - (int) Math.signum(v), amulets.size() - rowsFit));
        if (next != scroll) { scroll = next; rebuildWidgets(); }
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float d) {
        super.renderBackground(g, mx, my, d);
        int bottom = Math.min(height - 8, height / 2 + 130);
        g.fill(left, top, left + W, bottom, 0xC0101418);
        g.renderOutline(left, top, W, bottom - top, 0xFF5A6770);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        super.render(g, mx, my, d);
        g.drawCenteredString(font, Component.literal("AMULET STARS").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), width / 2, top + 8, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.literal("The star on a pet holding each amulet").withStyle(ChatFormatting.GRAY), width / 2, top + 20, 0xFFFFFFFF);
        int y = top + 36;
        for (int i = scroll; i < Math.min(amulets.size(), scroll + rowsFit); i++) {
            // its picture (once seen in your inventory or a menu), then its name
            net.minecraft.world.item.ItemStack pic = AmuletStars.picture(amulets.get(i));
            if (!pic.isEmpty()) g.renderItem(pic, left + 10, y + 1);
            else g.renderOutline(left + 11, y + 2, 14, 14, 0x40FFFFFF);
            g.drawString(font, font.plainSubstrByWidth(amulets.get(i), W - 12 - 110 - 22 - 20 - 18), left + 30, y + 5, 0xFFFFFFFF, true);
            y += ROW;
        }
        if (amulets.size() > rowsFit) g.drawString(font, Component.literal("Scroll for more").withStyle(ChatFormatting.DARK_GRAY), left + 12, Math.min(height - 8, height / 2 + 130) - 19, 0xFFFFFFFF, false);
    }

    @Override
    public void onClose() {
        AmuletStars.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
