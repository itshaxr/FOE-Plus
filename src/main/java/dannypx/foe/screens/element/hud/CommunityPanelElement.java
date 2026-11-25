package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/// The community goal (see {@link CommunityCatches}): its own HUD, inside the Timers to start with.
public class CommunityPanelElement extends PanelElement {
    public CommunityPanelElement() {
        this(false);
    }

    public CommunityPanelElement(boolean isCopy) {
        super(dannypx.foe.helper.HudFine.get(Configs.hudConfig.communityPanelXPosition),
                dannypx.foe.helper.HudFine.get(Configs.hudConfig.communityPanelYPosition),
                Configs.hudConfig.communityPanelAlignment.get(),
                Component.literal("Community Catches"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showCommunityPanel.get(); }
    @Override protected float configX() { return dannypx.foe.helper.HudFine.get(Configs.hudConfig.communityPanelXPosition); }
    @Override protected float configY() { return dannypx.foe.helper.HudFine.get(Configs.hudConfig.communityPanelYPosition); }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.communityPanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.communityPanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.communityPanelOpacity.get(); }

    @Override
    protected boolean alignTextToAnchor() {
        return false;
    }

    @Override
    protected Component title() {
        return Component.literal("ᴄᴏᴍᴍᴜɴɪᴛʏ ᴄᴀᴛᴄʜᴇѕ").withStyle(ChatFormatting.BOLD);
    }

    /// Made when first needed: the panel's constructor already asks for its lines.
    private CommunityCatches community;

    private CommunityCatches community() {
        if (community == null) community = new CommunityCatches();
        return community;
    }

    @Override
    protected List<Component> lines() {
        return community().lines();
    }

    @Override
    protected List<Component> previewLines() {
        return community().previewLines();
    }

    @Override
    protected void beforeLine(GuiGraphics g, Font font, Component line, int x, int y) {
        community().beforeLine(g, font, line, x, y, lineRightEdge);
    }
}
