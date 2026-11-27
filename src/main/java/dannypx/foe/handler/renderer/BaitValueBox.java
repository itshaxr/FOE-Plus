package dannypx.foe.handler.renderer;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.BaitboxHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Beside a baitbox's Package (or any menu holding bait): what the bait in it is worth at your bait prices, by
 * rarity, styled like the Fish Value box. Only shows when the menu itself holds bait.
 */
public final class BaitValueBox {
    private BaitValueBox() {}

    static final String[] NAMES = {"Common", "Rare", "Epic", "Legendary", "Mythical"};
    static final int[] COLORS = {0xFFFFFF, 0x2B85C4, 0x1CD832, 0xD98103, 0xC93832}; // the game's own rarity colours (see FoeLocations.rarityColor)

    /// Package-private for tests: the rows {rarity index} that have bait, and the box's height for them.
    static List<Integer> rows(long[] got) {
        List<Integer> out = new ArrayList<>();
        for (int r = 0; r < Math.min(got.length, NAMES.length); r++) if (got[r] > 0) out.add(r);
        return out;
    }

    static int height(int rows) {
        return ValueBox.height(rows);
    }

    /// `belowSorters`: the sorter boxes are on the right (a chest or the vault): go under them, and under the Fish
    /// Value box when `fishBox`.
    public static void render(GuiGraphics g, net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen, boolean belowSorters, boolean fishBox) {
        if (!Configs.mainConfig.enableMod.get()) return;
        long[] got = BaitboxHandler.baitIn(screen.getMenu());
        List<Integer> rows = rows(got);
        if (rows.isEmpty()) return;
        var pos = (dannypx.foe.mixin.accessor.ContainerScreenPosAccessor) screen;
        int right = pos.foe$leftPos() + pos.foe$imageWidth();
        int menuTop = pos.foe$topPos();
        // On a chest or the vault the sorter boxes (and the fish value) are on the right already: go under them.
        int top = belowSorters ? menuTop + 18 + 5 * 22 + 2 + 6 + (fishBox ? FishValueBox.HEIGHT + 4 : 0) : menuTop;
        draw(g, right + 4, top, got, BaitboxHandler.worthIn(screen.getMenu()), rows, BaitboxHandler.instance().packageOpen(screen) ? "Package Value" : "Bait Value");
    }

    static void draw(GuiGraphics g, int x, int top, long[] got, long[] worth, List<Integer> rows, String title) {
        List<ValueBox.Row> out = new ArrayList<>();
        long total = 0;
        for (int r : rows) {
            total += worth[r];
            out.add(new ValueBox.Row(COLORS[r], Component.literal(NAMES[r]).withColor(COLORS[r]),
                    Component.literal(String.format(java.util.Locale.ROOT, "%,d bait", got[r])).withColor(0x8A93A0),
                    Component.literal(BaitboxHandler.money(worth[r])).withStyle(ChatFormatting.GOLD)));
        }
        ValueBox.draw(g, x, top, Component.literal(title), out,
                Component.literal(BaitboxHandler.money(total)).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
    }
}
