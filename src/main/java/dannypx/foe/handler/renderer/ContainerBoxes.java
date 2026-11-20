package dannypx.foe.handler.renderer;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.GenericContainerScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;

/**
 * The boxes FOE+ puts beside a menu: what the fish in a chest are worth (chests only, not the Personal Vault) and
 * what the bait in a menu is worth (a baitbox's Package, a chest or the vault; not the auction house or shops). Drawn as part of the menu,
 * before its tooltip, so the tooltip goes over them.
 */
public final class ContainerBoxes {
    private ContainerBoxes() {}

    /// Package-private for tests: which boxes a menu titled `title` gets: {fish value, sorters on the right}.
    static boolean[] which(String title, boolean chestMenu) {
        boolean chest = chestMenu && GenericContainerScreenHandler.STORAGE_SCREEN_CONTAINER.equals(title);
        boolean vault = chestMenu && title.startsWith(GenericContainerScreenHandler.PERSONAL_VAULT_SCREEN_CONTAINER);
        return new boolean[]{chest, chest || vault};
    }

    public static void draw(AbstractContainerScreen<?> screen, GuiGraphics g) {
        if (!Configs.mainConfig.enableMod.get()) return;
        if (screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
                || screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) return;
        TradeValueBox.render(g, screen);
        boolean[] w = which(screen.getTitle().getString(), screen instanceof ContainerScreen);
        boolean fish = w[0] && FishValueBox.shown(screen);
        if (fish) FishValueBox.render(g, screen, ((ContainerScreen) screen).getMenu().getRowCount(), "Chest");
        // bait: on a baitbox's Package, a chest or the vault only (not the auction house, shops or other menus)
        if (w[1] || dannypx.foe.handler.logic.BaitboxHandler.instance().packageOpen(screen)) BaitValueBox.render(g, screen, w[1], fish);
    }
}
