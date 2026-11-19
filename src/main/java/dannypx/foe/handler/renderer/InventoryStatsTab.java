package dannypx.foe.handler.renderer;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.store.ConstantDataHandler;
import dannypx.foe.handler.store.StatsDataHandler;
import dannypx.foe.item.FishTagObject;
import dannypx.foe.item.TagObject;
import dannypx.foe.screens.element.hud.FoeRBoxes;
import dannypx.foe.handler.store.StatsDataHandler.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The inventory's Stats tab: an S on the inventory's right edge. Click it and your fishing stats open beside the
 * inventory, and stay open (every time you open it) until you click S again: fish, pets and other drops, how many
 * you've caught and the dry streak (fish since the last one), rarities and variants with the server's badges. It
 * scrolls with the mouse wheel and keeps everything inside its box. Its look follows the HUD Style: FOE+'s panel, or
 * the FOE-R style's dark brown frame. (FOE-R's Fishing Statistics list, brought back behind a tab.)
 */
public final class InventoryStatsTab {
    private InventoryStatsTab() {}

    static final int TAB_W = 14, TAB_H = 18, PANEL_W = 150, ROW_H = 10, CAUGHT_W = 46, DRY_W = 40;
    private static int scroll = 0;

    /// One line: a heading (FISH), or a name with its count and dry streak.
    record Row(Component label, String caught, String dry, boolean heading) {}

    static boolean open() {
        return Configs.inventoryScreenConfig.showFishingStatsList.get();
    }

    //region What's in it
    private static final String[] RARITIES = {"common", "rare", "epic", "legendary", "mythical"};
    private static final String[] VARIANTS = {"albino", "melanistic", "trophy", "fabled"};

    static List<Row> rows() {
        List<Row> out = new ArrayList<>();
        StatsDataHandler stats = StatsDataHandler.instance();
        var data = stats.getStatsData();
        var constants = ConstantDataHandler.instance().getConstantData();
        int fish = data.fishTotal;

        out.add(new Row(Component.literal("ꜰɪꜱʜ"), "", "", true));
        out.add(new Row(Component.literal("Total"), num(fish), "", false));
        Map<String, Stat<Integer, Integer>> rarity = data.fishData.getOrDefault(TagObject.RARITY, Map.of());
        for (String r : RARITIES) add(out, badge(constants.fishData, TagObject.RARITY, r), rarity.get(r), fish);
        Map<String, Stat<Integer, Integer>> variant = data.fishData.getOrDefault(FishTagObject.VARIANT, Map.of());
        for (String v : VARIANTS) add(out, badge(constants.fishData, FishTagObject.VARIANT, v), variant.get(v), fish);
        Map<String, Stat<Integer, Integer>> size = data.fishData.getOrDefault(FishTagObject.FISH_SIZE, Map.of());
        add(out, badge(constants.fishData, FishTagObject.FISH_SIZE, "gigantic"), size.get("gigantic"), fish);

        out.add(new Row(Component.literal("ᴘᴇᴛꜱ"), "", "", true));
        out.add(new Row(Component.literal("Total"), num(data.petTotal), num(stats.petDryStreak()), false));
        Map<String, Stat<Integer, Integer>> pets = data.petData.getOrDefault(TagObject.RARITY, Map.of());
        // the same rarity badges as the fish
        for (String r : RARITIES) add(out, badge(constants.fishData, TagObject.RARITY, r), pets.get(r), fish);

        out.add(new Row(Component.literal("ᴏᴛʜᴇʀ"), "", "", true));
        out.add(new Row(Component.literal("Shards"), num(stats.shardTotal()), num(stats.shardDryStreak()), false));
        for (String key : dannypx.foe.handler.logic.RareCatchHandler.EVENT_PULLS) {
            int streak = stats.rareCatchStreak(key);
            out.add(new Row(Component.literal(pretty(key)), num(stats.rareCatchAmount(key)), streak < 0 ? "-" : num(streak), false));
        }
        return out;
    }

    private static void add(List<Row> out, Component label, Stat<Integer, Integer> stat, int fishTotal) {
        int amount = stat == null || stat.amount() == null ? 0 : stat.amount();
        String dry = stat == null || stat.caughtOn() == null ? num(fishTotal) : num(Math.max(0, fishTotal - stat.caughtOn()));
        out.add(new Row(label, num(amount), dry, false));
    }

    /// The server's badge for it (from what FOE+ has seen), or its name.
    private static Component badge(Map<String, Map<String, Component>> data, String category, String field) {
        Component c = data == null ? null : data.getOrDefault(category, Map.of()).get(field);
        return c != null ? c : Component.literal(pretty(field));
    }

    private static String pretty(String key) {
        String s = key.replace('_', ' ');
        StringBuilder b = new StringBuilder();
        for (String w : s.split(" ")) if (!w.isEmpty()) b.append(b.length() > 0 ? " " : "").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
        return b.toString();
    }

    private static String num(int v) {
        return String.format(Locale.ROOT, "%,d", v);
    }
    //endregion

    //region Where it goes
    /// The tab's box, on the inventory's right edge.
    static int[] tab(int invRight, int invTop) {
        return new int[]{invRight, invTop + 8, TAB_W, TAB_H};
    }

    /// The panel's box: right of the tab, as tall as the inventory, narrower if the screen is (null: no room).
    static int[] panel(int invRight, int invTop, int invHeight, int screenW) {
        int x = invRight + TAB_W + 4;
        int w = Math.min(PANEL_W, screenW - 4 - x);
        if (w < 110) return null;
        return new int[]{x, invTop, w, invHeight};
    }
    //endregion

    public static void render(GuiGraphics g, Font font, int invRight, int invTop, int invHeight, int screenW, int mouseX, int mouseY) {
        boolean open = open();
        boolean foeR = FoeRBoxes.on();
        int[] t = tab(invRight, invTop);
        // the tab
        g.fill(t[0] - 1, t[1], t[0] + t[2] + 1, t[1] + t[3], 0xFF000000);
        int fill = foeR ? (open ? 0xFF8A6440 : 0xFF6B4A2B) : (open ? 0xFF2A4E6E : 0xFF1B3450);
        g.fill(t[0] - 1, t[1] + 1, t[0] + t[2], t[1] + t[3] - 1, fill);
        g.drawString(font, Component.literal("S").withStyle(open ? ChatFormatting.YELLOW : ChatFormatting.WHITE), t[0] + 4, t[1] + 5, 0xFFFFFFFF, true);
        if (in(mouseX, mouseY, t)) g.setTooltipForNextFrame(font, Component.literal(open ? "Hide your fishing stats" : "Show your fishing stats"), mouseX, mouseY);
        if (!open) return;

        int[] p = panel(invRight, invTop, invHeight, screenW);
        if (p == null) return;
        int x = p[0], y = p[1], w = p[2], h = p[3];
        Component title = Component.literal("ꜰɪꜱʜɪɴɢ ꜱᴛᴀᴛꜱ").withStyle(ChatFormatting.BOLD);
        int innerTop;
        if (foeR) {
            // the FOE-R style's dark brown rounded frame, dark glass, the title on a rail with a tan nib
            g.fill(x + 2, y, x + w - 2, y + h, 0xFF0B0B0B);
            g.fill(x, y + 2, x + w, y + h - 2, 0xFF0B0B0B);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0B0B0B);
            g.fill(x + 2, y + 1, x + w - 2, y + h - 1, 0xFF6B4A2B);
            g.fill(x + 1, y + 2, x + w - 1, y + h - 2, 0xFF6B4A2B);
            g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xD0101418);
            for (int i = 0; i < w - 8; i++) {
                float k = 1f - (float) i / (w - 8);
                g.fill(x + 4 + i, y + 4, x + 5 + i, y + 15, Math.round(0xB0 * k * k) << 24);
            }
            g.fill(x + 4, y + 5, x + 6, y + 14, 0xFFC9A26B);
            g.drawString(font, title, x + 10, y + 6, 0xFFFFFFFF, true);
            innerTop = y + 19;
        } else {
            // FOE+'s panel: a light edge, see-through black, the title on a black plate in the top edge
            g.fill(x, y, x + w, y + h, 0xFFE6E6E6);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xC0000000);
            int tw = font.width(title) + 10;
            g.fill(x + w / 2 - tw / 2, y - 5, x + w / 2 + tw / 2, y + 5, 0xFF000000);
            g.drawCenteredString(font, title, x + w / 2, y - 4, 0xFFFFFFFF);
            innerTop = y + 8;
        }
        int labelColor = foeR ? 0xFFD9C8B0 : 0xFFC9D7E6, headColor = foeR ? 0xFFC9A26B : 0xFFFFAA00, dim = 0xFF8A9AAA;
        int left = x + 6, right = x + w - 8;
        int dryRight = right, caughtRight = right - DRY_W;
        // the column names
        g.drawString(font, "ᴄᴀᴜɢʜᴛ", caughtRight - font.width("ᴄᴀᴜɢʜᴛ"), innerTop, dim, false);
        g.drawString(font, "ᴅʀʏ", dryRight - font.width("ᴅʀʏ"), innerTop, dim, false);
        int listTop = innerTop + 11, listBottom = y + h - 4;

        List<Row> rows = rows();
        int total = 0;
        for (Row r : rows) total += r.heading() ? ROW_H + 4 : ROW_H;
        int max = Math.max(0, total - (listBottom - listTop));
        scroll = Math.max(0, Math.min(max, scroll));
        int labelMax = caughtRight - CAUGHT_W - left - 2;

        g.enableScissor(x + 2, listTop, x + w - 2, listBottom);
        int yy = listTop - scroll;
        for (Row r : rows) {
            if (r.heading()) {
                yy += 3;
                g.drawString(font, r.label().copy().withStyle(ChatFormatting.BOLD).withColor(headColor & 0xFFFFFF), left, yy, -1, true);
                int lx = left + font.width(r.label().copy().withStyle(ChatFormatting.BOLD)) + 4;
                g.fill(lx, yy + 4, right, yy + 5, 0x40FFFFFF);
                yy += ROW_H + 1;
                continue;
            }
            if (yy + ROW_H > listTop - ROW_H && yy < listBottom) {
                Component label = fit(font, r.label(), labelMax, labelColor);
                dannypx.foe.screens.element.hud.PanelElement.drawLine(g, font, label, left + 1, yy);
                g.drawString(font, r.caught(), caughtRight - font.width(r.caught()), yy, 0xFFFFFFFF, true);
                if (!r.dry().isEmpty()) g.drawString(font, r.dry(), dryRight - font.width(r.dry()), yy, 0xFFFF5555, true);
            }
            yy += ROW_H;
        }
        g.disableScissor();
        // the scrollbar, when it doesn't all fit
        if (max > 0) {
            int trackH = listBottom - listTop;
            int thumbH = Math.max(12, trackH * trackH / total);
            int thumbY = listTop + (trackH - thumbH) * scroll / max;
            g.fill(x + w - 4, listTop, x + w - 3, listBottom, 0x30FFFFFF);
            g.fill(x + w - 4, thumbY, x + w - 3, thumbY + thumbH, 0xA0FFFFFF);
        }
    }

    /// The name, in its colour, cut with "…" to fit before the numbers (badges are kept whole).
    private static Component fit(Font font, Component label, int max, int color) {
        boolean badge = label.getString().codePoints().anyMatch(cp -> cp >= 0xE000 && cp <= 0xF8FF);
        Component styled = badge || label.getStyle().getColor() != null ? label : label.copy().withColor(color & 0xFFFFFF);
        if (font.width(styled) <= max || badge) return styled;
        String s = label.getString();
        while (!s.isEmpty() && font.width(s + "…") > max) s = s.substring(0, s.length() - 1);
        return Component.literal(s + "…").withColor(color & 0xFFFFFF);
    }

    private static boolean in(double mx, double my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    /// A click on the tab opens or closes it (and stays that way). True when it was the tab.
    public static boolean click(double mx, double my, int invRight, int invTop) {
        if (!in(mx, my, tab(invRight, invTop))) return false;
        Configs.inventoryScreenConfig.showFishingStatsList.accept(!open());
        Configs.inventoryScreenConfig.save();
        scroll = 0;
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
        return true;
    }

    /// The mouse wheel over the open panel scrolls it.
    public static boolean scroll(double mx, double my, double amount, int invRight, int invTop, int invHeight, int screenW) {
        if (!open()) return false;
        int[] p = panel(invRight, invTop, invHeight, screenW);
        if (p == null || !in(mx, my, p)) return false;
        scroll += amount > 0 ? -ROW_H * 2 : ROW_H * 2;
        return true;
    }
}
