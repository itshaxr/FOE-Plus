package dannypx.foe.screens.element.hud;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.fetch.TabOverlayHandler;
import dannypx.foe.handler.logic.InventoryHandler;
import dannypx.foe.handler.logic.LoadingHandler;
import dannypx.foe.helper.GuiGraphicsHelper;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.item.PetTagObject;
import dannypx.foe.config.Configs;
import dannypx.foe.screens.element.Element;
import dannypx.foe.type.StringStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class PetElement extends Element {
    //region Fields
    private static final int TEXTURE_WIDTH = 160;
    private static final int TEXTURE_HEIGHT = 37;

    //endregion

    public PetElement() {
        super(TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                Configs.hudConfig.petElementXPosition.get() / 100f,
                Configs.hudConfig.petElementYPosition.get() / 100f,
                Configs.hudConfig.petElementAlignment.get(),
                Configs.hudConfig.petElementGroup.translation("Pet Element"),
                false);
    }

    public PetElement(boolean isCopy) {
        super(TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                Configs.hudConfig.petElementXPosition.get() / 100f,
                Configs.hudConfig.petElementYPosition.get() / 100f,
                Configs.hudConfig.petElementAlignment.get(),
                Configs.hudConfig.petElementGroup.translation("Pet Element"),
                isCopy);
    }

    //region Methods
    static final int BAR_W = 40, HEIGHT = 30;

    @Override
    public void render(GuiGraphics guiGraphics) {
        var cfg = Configs.hudConfig;
        if (!FoeRBoxes.shows(cfg.showPetElement.get(), isCopy)) return;
        float scale = FoeRBoxes.scale(cfg.petElementScale.get());
        int[] screen = FoeRBoxes.screen(scale);
        if (!isCopy) {
            xPos = cfg.petElementXPosition.get() / 100f;
            yPos = cfg.petElementYPosition.get() / 100f;
            alignment = cfg.petElementAlignment.get();
        }
        boolean right = alignment == dannypx.foe.type.Alignment.TOP_RIGHT;
        int x = right ? screen[0] - Math.round(screen[0] * xPos) : Math.round(screen[0] * xPos);
        int y = Math.round(screen[1] * yPos);

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(scale, scale);
        int w = draw(guiGraphics, Minecraft.getInstance().font, x, y, right);
        guiGraphics.pose().popMatrix();
        this.width = Math.round(w * scale);
        this.height = Math.round(HEIGHT * scale);
    }

    /// Draws the box at `x` (its left edge, or its right edge when `right`), `y`; returns how wide it came out.
    private int draw(GuiGraphics g, Font font, int x, int y, boolean right) {
        int railX = right ? x - FoeRBoxes.BOX : x + FoeRBoxes.BOX;
        if (!InventoryHandler.instance().hasPet()) {
            Component none = Component.literal("No pet equipped").withStyle(ChatFormatting.GRAY);
            int rail = FoeRBoxes.railFor(font.width(none));
            FoeRBoxes.box(g, x, y, right, 0x555555);
            FoeRBoxes.rail(g, railX, y + 9, rail, FoeRBoxes.RAIL_H, right, 0x555555);
            FoeRBoxes.text(g, font, none, railX, y + 11, right);
            return FoeRBoxes.BOX + rail;
        }
        PetTagObject pet = InventoryHandler.instance().getCurrentPet();
        int ring = PetPanelElement.frameColor(pet) & 0xFFFFFF;

        Component head = TextHelper.concat(pet.getRarityComponent(), Component.literal(" "), pet.getName());
        Component level = TextHelper.concat(Component.literal("ʟᴠʟ ").withStyle(ChatFormatting.GRAY),
                Component.literal(String.valueOf(pet.getLevel())).withStyle(ChatFormatting.GREEN));
        boolean max = pet.getLevel() >= 100;
        float progress = max ? 1f : Math.max(0f, Math.min(1f, pet.getProgress()));
        // ♣ / ⚓ when its rolls here are maxed, then its rating's % in the rating's colour, the brackets grey
        net.minecraft.network.chat.MutableComponent after = Component.empty();
        Component maxed = PetPanelElement.maxIcons(pet);
        if (maxed != null) after.append(maxed).append(Component.literal(" "));
        int ratingColor = -1;
        try {
            ratingColor = PetPanelElement.ratingColor(pet.getRatingComponent());
        } catch (RuntimeException ignored) {
            // no rating to read
        }
        after.append(Component.literal("(").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format(java.util.Locale.ROOT, "%.0f%%", pet.getTotalPercent() * 100f))
                        .withColor(ratingColor < 0 ? 0xFFFFFF : ratingColor))
                .append(Component.literal(")").withStyle(ChatFormatting.GRAY));

        int levelW = font.width(level) + 4 + BAR_W + 4 + font.width(after);
        int rail1 = FoeRBoxes.railFor(font.width(head)), rail2 = FoeRBoxes.railFor(levelW);

        FoeRBoxes.box(g, x, y, right, ring);
        g.pose().pushMatrix();
        g.pose().translate(FoeRBoxes.inside(x, right), y + 3);
        g.pose().scale(1.5f, 1.5f);
        g.renderItem(pet.getItemStack(), 0, 0);
        g.pose().popMatrix();
        FoeRBoxes.rail(g, railX, y + 2, rail1, FoeRBoxes.RAIL_H, right, ring);
        FoeRBoxes.rail(g, railX, y + 16, rail2, FoeRBoxes.RAIL_H, right, ring);

        FoeRBoxes.text(g, font, head, railX, y + 4, right);
        int lx = right ? railX - FoeRBoxes.TEXT_IN - levelW : railX + FoeRBoxes.TEXT_IN;
        PanelElement.drawText(g, font, level.getVisualOrderText(), lx, y + 18);
        int bx = lx + font.width(level) + 4;
        FoeRBoxes.levelBar(g, bx, y + 21, BAR_W, progress, 0xFF7CF07C, 0xFF2EA043);
        PanelElement.drawText(g, font, after.getVisualOrderText(), bx + BAR_W + 4, y + 18);
        return FoeRBoxes.BOX + Math.max(rail1, rail2);
    }
    //endregion
}
