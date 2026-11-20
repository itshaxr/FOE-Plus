package dannypx.foe.screens.element.hud;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.fetch.LocalPlayerHandler;
import dannypx.foe.handler.fetch.ScoreboardHandler;
import dannypx.foe.handler.fetch.TabOverlayHandler;
import dannypx.foe.handler.logic.LoadingHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.helper.GuiGraphicsHelper;
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
import net.minecraft.util.CommonColors;

public class ProfileElement extends Element {
    //region Fields

    private static final int TEXTURE_WIDTH = 160;
    private static final int TEXTURE_HEIGHT = 42;
    //endregion

    public ProfileElement() {
        super(TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                Configs.hudConfig.profileElementXPosition.get() / 100f,
                Configs.hudConfig.profileElementYPosition.get() / 100f,
                Configs.hudConfig.profileElementAlignment.get(),
                Configs.hudConfig.profileElementGroup.translation("Profile Element"),
                false);
    }

    public ProfileElement(boolean isCopy) {
        super(TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                Configs.hudConfig.profileElementXPosition.get() / 100f,
                Configs.hudConfig.profileElementYPosition.get() / 100f,
                Configs.hudConfig.profileElementAlignment.get(),
                Configs.hudConfig.profileElementGroup.translation("Profile Element"),
                isCopy);
    }

    //region Methods
    static final int GOLD = 0xE8B54A, BAR_W = 56;

    @Override
    public void render(GuiGraphics guiGraphics) {
        var cfg = Configs.hudConfig;
        if (!FoeRBoxes.shows(cfg.showProfileElement.get(), isCopy)) return;
        float scale = FoeRBoxes.scale(cfg.profileElementScale.get());
        int[] screen = FoeRBoxes.screen(scale);
        if (!isCopy) {
            xPos = cfg.profileElementXPosition.get() / 100f;
            yPos = cfg.profileElementYPosition.get() / 100f;
            alignment = cfg.profileElementAlignment.get();
        }
        boolean right = alignment == dannypx.foe.type.Alignment.TOP_RIGHT;
        int x = right ? screen[0] - Math.round(screen[0] * xPos) : Math.round(screen[0] * xPos);
        int y = Math.round(screen[1] * yPos);

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(scale, scale);
        FoeRBoxes.skin(cfg.profileElementBoxColor.get(), cfg.profileElementOpacity.get());
        int w;
        try {
            w = draw(guiGraphics, Minecraft.getInstance().font, x, y, right);
        } finally {
            FoeRBoxes.unskin();
        }
        guiGraphics.pose().popMatrix();
        this.width = Math.round(w * scale);
        this.height = Math.round(HEIGHT * scale);
    }

    static final int HEIGHT = 42;

    /// Draws the box at `x` (its left edge, or its right edge when `right`), `y`; returns how wide it came out.
    private int draw(GuiGraphics g, Font font, int x, int y, boolean right) {
        Minecraft mc = Minecraft.getInstance();
        Component name = TabOverlayHandler.instance().getPlayerName();
        if (name.getString().isBlank()) name = Component.literal(mc.player != null ? mc.player.getGameProfile().name() : "Player");
        // your + (in the colour you picked), unless the name already has it
        if (mc.player != null && !name.getString().contains(dannypx.foe.helper.NameTagMarker.PLUS_GLYPH)) {
            Component plus = dannypx.foe.handler.logic.TextDisplayHandler.instance().tabMarker(mc.player.getUUID());
            if (plus != null) name = TextHelper.concat(name, Component.literal(" "), plus);
        }
        String levelText = ScoreboardHandler.instance().getLevel().getString().trim();
        Component level = TextHelper.concat(Component.literal("ʟᴠʟ ").withStyle(ChatFormatting.GRAY),
                Component.literal(levelText.isEmpty() ? "0" : levelText).withColor(0xFFC94D));
        float progress = Math.max(0f, Math.min(1f, LocalPlayerHandler.instance().getExperienceProgress()));
        Component percent = Component.literal(Math.round(progress * 100) + "%").withStyle(ChatFormatting.GRAY);
        Component money = money();

        int levelW = font.width(level) + 4 + BAR_W + 4 + font.width(percent);
        int rail1 = FoeRBoxes.railFor(font.width(name)), rail2 = FoeRBoxes.railFor(levelW);
        int rail3 = FoeRBoxes.railFor(font.width(money));
        int railX = right ? x - FoeRBoxes.BOX : x + FoeRBoxes.BOX;

        int ring = FoeRBoxes.ring(Configs.hudConfig.profileElementBorderColor.get(), GOLD);
        FoeRBoxes.box(g, x, y, right, ring);
        renderHead(g, FoeRBoxes.inside(x, right), y + 3);
        FoeRBoxes.rail(g, railX, y + 2, rail1, FoeRBoxes.RAIL_H, right, ring);
        FoeRBoxes.rail(g, railX, y + 16, rail2, FoeRBoxes.RAIL_H, right, ring);
        // money and credits hang under the box on their own short rail
        int moneyX = right ? x - 6 : x + 6;
        FoeRBoxes.rail(g, moneyX, y + 31, rail3, 11, right, ring);

        FoeRBoxes.text(g, font, name, railX, y + 4, right);
        int lx = right ? railX - FoeRBoxes.TEXT_IN - levelW : railX + FoeRBoxes.TEXT_IN;
        PanelElement.drawText(g, font, level.getVisualOrderText(), lx, y + 18);
        int bx = lx + font.width(level) + 4;
        FoeRBoxes.levelBar(g, bx, y + 21, BAR_W, progress, 0xFFFFD866, 0xFFD9901A,
                GameXpBar.colour(dannypx.foe.config.Configs.hudConfig.profileElementXpBarColor::get));
        g.drawString(font, percent, bx + BAR_W + 4, y + 18, 0xFFFFFFFF, true);
        FoeRBoxes.text(g, font, money, moneyX, y + 33, right);

        return Math.max(FoeRBoxes.BOX + Math.max(rail1, rail2), 6 + rail3);
    }

    /// "$19.69M  ◆70": your balance and credits, as the top bar shows them.
    private static Component money() {
        ScoreboardHandler scoreboard = ScoreboardHandler.instance();
        String wallet = scoreboard.getWallet().getString().trim().replaceFirst("^\\$\\s*", "");
        Component credits = ScoreboardHandler.glyphs(scoreboard.getCredits());
        if (credits.getString().isEmpty()) credits = scoreboard.getCreditsIcon();
        String creditValue = scoreboard.getCredits().getString().replaceAll("[\\uE000-\\uF8FF]", "").trim();
        return TextHelper.concat(
                Component.literal("$").withStyle(ChatFormatting.DARK_GREEN),
                dannypx.foe.helper.NameTagMarker.GAP_WIDE,
                Component.literal(wallet.isEmpty() ? "0" : wallet).withStyle(ChatFormatting.GREEN),
                Component.literal("   "),
                credits,
                Component.literal(creditValue.isEmpty() ? "0" : creditValue).withStyle(ChatFormatting.YELLOW));
    }

    /// Your face (with its hat layer), 24 pixels, its top left at (x, y).
    private void renderHead(GuiGraphics g, int x, int y) {
        if (Minecraft.getInstance().player == null) return;
        Identifier skin = Minecraft.getInstance().player.getSkin().body().texturePath();
        g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8, 8, 24, 24, 8, 8, 64, 64);
        g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40, 8, 24, 24, 8, 8, 64, 64);
    }
    //endregion
}
