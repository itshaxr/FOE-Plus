package dannypx.foe.screens;

import dannypx.foe.config.Configs;
import dannypx.foe.screens.element.hud.CatchPopupElement;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The catch popup's and the pet popup's lines, each turned on or off with a click, with both popups shown as they
 * look now. Opened from the HUD Editor ("Popups"); the same switches are in HUD settings → FOE Catch Popup / FOE Pet
 * Popup.
 */
public class PopupLinesScreen extends Screen {
    private final Screen parent;

    public PopupLinesScreen(Screen parent) {
        super(Component.literal("Popup lines"));
        this.parent = parent;
    }

    private record Toggle(String label, ValidatedBoolean entry) {}

    private static List<Toggle> fish() {
        var c = Configs.hudConfig;
        return List.of(new Toggle("Popup", c.showCatchPopup), new Toggle("Catch icon", c.catchPopupShowIcon),
                new Toggle("Name", c.catchPopupShowName), new Toggle("Size", c.catchPopupShowSize),
                new Toggle("Weight", c.catchPopupShowWeight), new Toggle("Length", c.catchPopupShowLength),
                new Toggle("Size bonus", c.catchPopupShowSizeBonus), new Toggle("Size bonus: contests only", c.catchPopupSizeBonusContestOnly),
                new Toggle("Inventory Full!", c.catchPopupShowInventoryFull), new Toggle("Fade away", c.catchPopupFade));
    }

    private static List<Toggle> pet() {
        var c = Configs.hudConfig;
        return List.of(new Toggle("Popup", c.showPetPopup), new Toggle("Name", c.petPopupName),
                new Toggle("Rarity", c.petPopupRarity), new Toggle("Level", c.petPopupLevel),
                new Toggle("Rating", c.petPopupRating), new Toggle("Stats", c.petPopupStats),
                new Toggle("Stat names", c.petPopupStatNames), new Toggle("Inventory Full!", c.petPopupShowInventoryFull),
                new Toggle("Fade away", c.petPopupFade));
    }

    static final int COL_W = 150, H = 16, GAP = 2;
    private int fishX, petX, top, previewTop;

    @Override
    protected void init() {
        fishX = width / 2 - COL_W - 10;
        petX = width / 2 + 10;
        top = 30;
        int y = column(fishX, fish());
        // the units (weight and length)
        var units = Configs.hudConfig.catchPopupUnits;
        this.addRenderableWidget(Button.builder(Component.literal("Units: ").append(Component.literal(units.get().displayName()).withStyle(ChatFormatting.AQUA)), b -> {
            units.accept(units.get().next());
            Configs.hudConfig.save();
            rebuildWidgets();
        }).bounds(fishX, y, COL_W, H).build());
        y += H + GAP;
        int y2 = column(petX, pet());
        previewTop = Math.max(y, y2) + 10;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 50, height - 26, 100, 20).build());
    }

    private int column(int x, List<Toggle> toggles) {
        int y = top;
        for (Toggle t : toggles) {
            boolean on = t.entry().get();
            this.addRenderableWidget(Button.builder(Component.literal(t.label() + ": ")
                    .append(Component.literal(on ? "On" : "Off").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED)), b -> {
                t.entry().accept(!t.entry().get());
                Configs.hudConfig.save();
                rebuildWidgets();
            }).bounds(x, y, COL_W, H).build());
            y += H + GAP;
        }
        return y;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, Component.literal("Catch popup").withStyle(ChatFormatting.BOLD), fishX + COL_W / 2, top - 12, 0xFFFFD866);
        g.drawCenteredString(font, Component.literal("Pet popup").withStyle(ChatFormatting.BOLD), petX + COL_W / 2, top - 12, 0xFFFFD866);
        // both popups as they look now, small enough to fit under their switches
        float room = height - 34 - previewTop;
        float scale = Math.max(0.35f, Math.min(0.75f, room / 120f));
        CatchPopupElement.drawPreview(g, false, fishX + COL_W / 2f, previewTop, scale);
        CatchPopupElement.drawPreview(g, true, petX + COL_W / 2f, previewTop, scale);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
