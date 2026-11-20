package dannypx.foe.handler.renderer;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.BaitboxHandler;
import dannypx.foe.handler.store.PetPrices;
import dannypx.foe.item.PetTagObject;
import dannypx.foe.item.TagObject;
import dannypx.foe.item.ValidateItem;
import dannypx.foe.screens.element.hud.PetPanelElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * On a trade ("Trade with MarcyPoro"): what each side puts in is worth, in a box beside its half of the menu. Your
 * side (the left four columns) on the left, theirs (the right four) on the right: fish at their sell value, bait and
 * lures at your bait prices, pets at your pet prices (a range), money offered, and how many other things there are;
 * under theirs, what you gain or lose by it. Estimates, from your own prices.
 */
public final class TradeValueBox {
    private TradeValueBox() {}

    static final int W = 140, ROW = 12;

    /// What one side puts in: fish, bait, pets (low and high), money, and how many other things.
    static final class Side {
        double fish;
        int fishCount;
        long bait;
        int baitCount;
        long petLow, petHigh;
        int petCount;
        long money;
        int other;

        long low() {
            return Math.round(fish) + bait + petLow + money;
        }

        long high() {
            return Math.round(fish) + bait + petHigh + money;
        }

        boolean empty() {
            return fishCount + baitCount + petCount + other == 0 && money == 0;
        }
    }

    /// Package-private for tests: the trade's other player ("Trade with MarcyPoro" → "MarcyPoro"), or null when the
    /// menu isn't a trade.
    static String partner(String title) {
        if (title == null) return null;
        String t = title.replaceAll("§.", "").trim();
        if (!t.regionMatches(true, 0, "Trade with ", 0, "Trade with ".length())) return null;
        String name = t.substring("Trade with ".length()).trim();
        return name.isEmpty() ? null : name;
    }

    /// Package-private for tests: "$1,500" in a line → 1500 ("$2.5M" → 2,500,000), or -1.
    static long dollars(String line) {
        if (line == null) return -1;
        var m = java.util.regex.Pattern.compile("\\$\\s?([0-9][0-9,]*(?:\\.[0-9]+)?)\\s?([kKmMbB])?").matcher(line);
        if (!m.find()) return -1;
        double v = Double.parseDouble(m.group(1).replace(",", ""));
        String u = m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.ROOT);
        v *= switch (u) { case "k" -> 1e3; case "m" -> 1e6; case "b" -> 1e9; default -> 1; };
        return Math.round(v);
    }

    static Side[] read(AbstractContainerScreen<?> screen) {
        Side you = new Side(), them = new Side();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            int i = slot.getContainerSlot();
            int col = i % 9;
            if (col == 4) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            add(col < 4 ? you : them, st);
        }
        return new Side[]{you, them};
    }

    private static void add(Side side, ItemStack st) {
        var fish = ValidateItem.isFish(st);
        if (fish.value1()) {
            side.fish += fish.value2().getSellValue() * Math.max(1, st.getCount());
            side.fishCount += st.getCount();
            return;
        }
        long bait = BaitboxHandler.stackValue(st);
        if (bait >= 0) {
            side.bait += bait;
            side.baitCount += BaitboxHandler.countOf(st);
            return;
        }
        var pet = ValidateItem.isPet(st);
        if (pet.value1()) {
            long[] p = petPrice(pet.value2());
            side.petCount++;
            if (p != null) {
                side.petLow += p[0];
                side.petHigh += p[1];
            }
            return;
        }
        // money offered: the trade's own item, its amount in its name or text
        TagObject server = ValidateItem.isType(st).value1() ? ValidateItem.isType(st).value2() : null;
        if (server == null) {
            long d = dollars(st.getHoverName().getString());
            var lore = st.get(DataComponents.LORE);
            if (d < 0 && lore != null) for (Component l : lore.lines()) { d = dollars(l.getString()); if (d >= 0) break; }
            if (d > 0) side.money += d;
            return;      // the menu's own buttons and panes
        }
        side.other += st.getCount();
    }

    /// A pet's price on your pet price sheet: {low, high}, or null when it can't be priced.
    public static long[] petPrice(PetTagObject pet) {
        try {
            int row = PetPrices.rowFor(pet.getTotalPercent() * 100.0);
            int rarity = PetPrices.rarityIndex(PetPanelElement.rarityOf(pet));
            if (row < 0 || rarity < 0) return null;
            boolean[] max = PetPanelElement.maxedLuckScale(pet);
            return PetPrices.price(row, rarity, max[0], max[1]);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /// "$45K", "$1.25M", "$950" (short, so a range fits).
    static String money(long v) {
        return "$" + PetPrices.format(v);
    }

    static String range(long low, long high) {
        return high > low ? money(low) + "–" + PetPrices.format(high) : money(low);
    }

    public static void render(GuiGraphics g, AbstractContainerScreen<?> screen) {
        if (!Configs.inventoryScreenConfig.showTradeValue.get()) return;
        String partner = partner(screen.getTitle().getString());
        if (partner == null) return;
        Side[] s = read(screen);
        var pos = (dannypx.foe.mixin.accessor.ContainerScreenPosAccessor) screen;
        int left = pos.foe$leftPos(), right = left + pos.foe$imageWidth(), top = pos.foe$topPos();
        draw(g, Math.max(2, left - 4 - W), top, "You give", s[0], null);
        draw(g, right + 4, top, partner + " gives", s[1], s[0]);
    }

    private static void draw(GuiGraphics g, int x, int top, String title, Side side, Side yours) {
        Font font = Minecraft.getInstance().font;
        List<Component[]> rows = new ArrayList<>();
        if (side.fishCount > 0) rows.add(row("Fish", money(Math.round(side.fish)), "×" + side.fishCount));
        if (side.baitCount > 0) rows.add(row("Bait", money(side.bait), "×" + PetPrices.format(side.baitCount)));
        if (side.petCount > 0) rows.add(row("Pets", range(side.petLow, side.petHigh), "×" + side.petCount));
        if (side.money > 0) rows.add(row("Money", money(side.money), ""));
        if (side.other > 0) rows.add(row("Other", "—", "×" + side.other));
        boolean compare = yours != null && !(side.empty() && yours.empty());
        // one clean line each, all at full size: what, how many (grey), what it's worth (gold, on the right)
        int h = 18 + Math.max(1, rows.size()) * ROW + 6 + 13 + (compare ? 12 : 0) + 3;
        g.fill(x + 1, top + 1, x + W - 1, top + h - 1, 0xC0101418);
        g.fill(x + 1, top, x + W - 1, top + 1, 0xFF5A6770);
        g.fill(x + 1, top + h - 1, x + W - 1, top + h, 0xFF5A6770);
        g.fill(x, top + 1, x + 1, top + h - 1, 0xFF5A6770);
        g.fill(x + W - 1, top + 1, x + W, top + h - 1, 0xFF5A6770);
        Component head = Component.literal(font.plainSubstrByWidth(title, W - 10)).withStyle(ChatFormatting.BOLD);
        g.drawString(font, head, x + W / 2 - font.width(head) / 2, top + 5, 0xFFFFFFFF, true);
        int y = top + 18;
        if (rows.isEmpty()) {
            g.drawString(font, Component.literal("Nothing yet").withStyle(ChatFormatting.DARK_GRAY), x + 5, y, 0xFFFFFFFF, true);
            y += ROW;
        }
        for (Component[] r : rows) {
            g.drawString(font, r[0], x + 5, y, 0xFFFFFFFF, true);
            if (!r[2].getString().isEmpty()) g.drawString(font, r[2], x + 5 + font.width(r[0]) + 4, y, 0xFFFFFFFF, true);
            g.drawString(font, r[1], x + W - 5 - font.width(r[1]), y, 0xFFFFFFFF, true);
            y += ROW;
        }
        y += 3;
        g.fill(x + 5, y - 2, x + W - 5, y - 1, 0x40FFFFFF);
        Component sum = Component.literal(range(side.low(), side.high()) + (side.other > 0 ? "+" : "")).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        g.drawString(font, Component.literal("Total").withStyle(ChatFormatting.WHITE), x + 5, y + 2, 0xFFFFFFFF, true);
        g.drawString(font, sum, x + W - 5 - font.width(sum), y + 2, 0xFFFFFFFF, true);
        if (compare) {
            // what you come out with: their side less yours (pets at the middle of their range)
            long mid = (side.low() + side.high()) / 2 - (yours.low() + yours.high()) / 2;
            Component diff = mid == 0 ? Component.literal("Even trade").withStyle(ChatFormatting.GRAY)
                    : Component.literal((mid > 0 ? "You gain " : "You lose ") + money(Math.abs(mid))).withStyle(mid > 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
            g.drawString(font, diff, x + (W - font.width(diff)) / 2, y + 14, 0xFFFFFFFF, true);
        }
    }

    private static Component[] row(String name, String value, String count) {
        return new Component[]{Component.literal(name).withStyle(ChatFormatting.GRAY),
                Component.literal(value).withStyle(ChatFormatting.GOLD),
                Component.literal(count).withStyle(ChatFormatting.DARK_GRAY)};
    }
}
