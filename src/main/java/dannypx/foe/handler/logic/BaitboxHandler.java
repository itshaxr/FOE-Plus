package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.helper.FoeChat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Locale;

/**
 * What bait is worth at your bait prices (Tools → Bait Prices; 250 / 500 / 1K / 5K a bait to start with).
 * <p>
 * Opening a baitbox opens its Package menu with the bait inside ("Stock: 18x" each): a line in chat says what's in
 * it and what that's worth, once per package:
 * <pre>
 * BAITBOX » 26 Common · 15 Rare · worth $14,000
 * </pre>
 * and the {@link dannypx.foe.handler.renderer.BaitValueBox} beside the menu shows the same. A bait's tooltip shows
 * what its stack is worth.
 */
public final class BaitboxHandler {
    private static final BaitboxHandler INSTANCE = new BaitboxHandler();

    public static BaitboxHandler instance() {
        return INSTANCE;
    }

    static final String[] RARITIES = {"common", "rare", "epic", "legendary", "mythical"};
    /// How many rarities bait comes in (common to mythical).
    public static final int N = 5;
    static final long WINDOW_MS = 4_000L;

    private long[] lastBait = null;
    private int lastBoxes = -1;
    private long[] baseline = null;
    private long until = 0L;
    private int opened = 0;

    /// The Package menu we've already put in chat (one line per opening).
    private Object toldFor = null;
    private long toldAt = 0L;
    /// When a baitbox last left your inventory: a menu of bait opening just after is its package.
    private long boxUsedAt = 0L;

    /// A bait or a lure (both come in stock that's used up, and both are worth their rarity's price): by its data,
    /// or (a Package's display copy) a "BAIT ITEM" / "LURE ITEM" lore line.
    public static boolean isBait(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        CustomData data = s.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            String type = data.copyTag().getStringOr("type", "");
            if (!type.isEmpty()) return isBaitType(type);
        }
        var lore = s.get(DataComponents.LORE);
        if (lore == null) return false;
        for (Component l : lore.lines()) if (isBaitLine(l.getString())) return true;
        return false;
    }

    /// Package-private for tests: an item type that counts as bait ("bait", "lure").
    static boolean isBaitType(String type) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        return t.equals("bait") || t.equals("lure");
    }

    /// Package-private for tests: the "BAIT ITEM" (or "LURE ITEM") line under a bait's name.
    static boolean isBaitLine(String line) {
        String t = net.minecraft.ChatFormatting.stripFormatting(line == null ? "" : line);
        if (t == null) return false;
        t = t.replaceAll("[\\uE000-\\uF8FF]", "").trim().toLowerCase(Locale.ROOT);
        return t.equals("bait item") || t.equals("lure item");
    }

    /// Package-private for tests: the rarity in a lore line: the server's rarity badge (common, rare, epic,
    /// legendary), or the word on its own; -1 when none.
    static int rarityInLine(String line) {
        if (line == null) return -1;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c >= '\uF033' && c <= '\uF037') return c - '\uF033';     // common .. mythical
        }
        String t = net.minecraft.ChatFormatting.stripFormatting(line);
        return t == null ? -1 : rarityIndex(t.replaceAll("[\\uE000-\\uF8FF]", "").trim());
    }

    /// Package-private for tests: the number in a "Stock: 18x" lore line, or -1.
    static long stockIn(String line) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)\\bstock\\s*:\\s*([\\d,]+)\\s*x?").matcher(line == null ? "" : String.valueOf(net.minecraft.ChatFormatting.stripFormatting(line)));
        if (!m.find()) return -1;
        try {
            return Long.parseLong(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /// What one of a bait (or one use of a lure) shown only as text (hovered in chat) is worth: your own price for
    /// it, else its rarity's (from the badge in its first lines, else the wiki); -1 when it can't be told.
    public static long priceOfText(String name, List<String> lines) {
        String colour = null;
        for (String l : lines) {
            String t = net.minecraft.ChatFormatting.stripFormatting(l);
            if (t != null && t.trim().startsWith("Color:")) colour = t.trim().substring("Color:".length()).trim();
        }
        Long own = null;
        if (colour != null && !colour.isEmpty())
            own = dannypx.foe.handler.store.BaitPriceOverrides.get(name + " (" + dannypx.foe.handler.store.BaitPictures.colourName(colour) + ")");
        if (own == null) own = dannypx.foe.handler.store.BaitPriceOverrides.get(name);
        if (own != null) return own;
        int r = -1;
        for (int i = 0; i < Math.min(4, lines.size()) && r < 0; i++) r = rarityInLine(lines.get(i));
        if (r < 0) {
            dannypx.foe.helper.WikiBaits.Bait wiki = dannypx.foe.helper.WikiBaits.find(name);
            if (wiki != null) r = rarityIndex(wiki.rarity());
        }
        return r < 0 ? -1 : prices()[r];
    }

    /// The number in a "Remaining Uses: 25" line (a lure), or -1.
    public static long usesOf(String line) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Remaining Uses:\\s*([\\d,]+)").matcher(line == null ? "" : line);
        if (!m.find()) return -1;
        try {
            return Long.parseLong(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /// The number in a "Stock: 18x" line, or -1.
    public static long stockOf(String line) {
        return stockIn(line);
    }

    /// How much bait a stack is: its counter, else its "Stock: 18x" line, else the stack's count.
    public static long countOf(ItemStack s) {
        CustomData data = s.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            if (tag.contains(dannypx.foe.item.TagObject.COUNTER)) return tag.getIntOr(dannypx.foe.item.TagObject.COUNTER, s.getCount());
        }
        var lore = s.get(DataComponents.LORE);
        if (lore != null) for (Component l : lore.lines()) {
            long n = stockIn(l.getString());
            if (n >= 0) return n;
        }
        return s.getCount();
    }

    /// 0-3 for Common..Legendary, or -1: from its data, else the rarity badge in the first lines of its lore.
    public static int rarityOf(ItemStack s) {
        CustomData data = s.get(DataComponents.CUSTOM_DATA);
        int r = data == null ? -1 : rarityIndex(data.copyTag().getStringOr("rarity", ""));
        if (r >= 0) return r;
        var lore = s.get(DataComponents.LORE);
        if (lore == null) return -1;
        List<Component> lines = lore.lines();
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            r = rarityInLine(lines.get(i).getString());
            if (r >= 0) return r;
        }
        // last: the wiki's rarity for a bait of this name
        dannypx.foe.helper.WikiBaits.Bait wiki = dannypx.foe.helper.WikiBaits.find(s.getHoverName().getString());
        return wiki == null ? -1 : rarityIndex(wiki.rarity());
    }

    /// "common", "rare", "epic", "legendary", or "".
    public static String rarityName(int r) {
        return r >= 0 && r < RARITIES.length ? RARITIES[r] : "";
    }

    /// What a bait stack is worth at your prices, or -1 when it isn't bait or its rarity isn't known.
    public static long stackValue(ItemStack s) {
        if (!isBait(s)) return -1;
        long each = priceOf(s);
        return each < 0 ? -1 : countOf(s) * each;
    }

    /// One bait's price: yours for that bait (Bait Prices), else its rarity's; -1 when neither is known.
    public static long priceOf(ItemStack s) {
        // a lure's price is set per colour ("Saltwater Spoon (Silver)"); its plain name is the fallback
        Long own = dannypx.foe.handler.store.BaitPriceOverrides.get(dannypx.foe.handler.store.BaitPictures.listedName(s));
        if (own == null) own = dannypx.foe.handler.store.BaitPriceOverrides.get(s.getHoverName().getString());
        if (own != null) return own;
        int r = rarityOf(s);
        return r < 0 ? -1 : prices()[r];
    }

    /// What the bait in a menu's own slots is worth, by rarity (each at its own price, when you've set one).
    public static long[] worthIn(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        long[] worth = new long[N];
        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack st = slot.getItem();
            if (!isBait(st)) continue;
            int r = rarityOf(st);
            long each = priceOf(st);
            if (r >= 0 && each >= 0) worth[r] += countOf(st) * each;
        }
        return worth;
    }

    /// The bait in a menu's own slots (not your inventory), by rarity.
    public static long[] baitIn(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        long[] got = new long[N];
        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack st = slot.getItem();
            if (!isBait(st)) continue;
            int r = rarityOf(st);
            if (r >= 0) got[r] += countOf(st);
        }
        return got;
    }

    /// Package-private for tests: the menu is a baitbox's package: its title says so, or it opened just after a
    /// baitbox left your inventory.
    static boolean isPackage(String title, long msSinceBoxUsed) {
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        return t.contains("package") || (msSinceBoxUsed >= 0 && msSinceBoxUsed < 6_000L);
    }

    /// Whether the open menu is a package, for the value box's title.
    public boolean packageOpen(net.minecraft.client.gui.screens.Screen screen) {
        return screen != null && isPackage(screen.getTitle().getString(), boxUsedAt == 0 ? -1 : System.currentTimeMillis() - boxUsedAt);
    }

    public static String money(long v) {
        return String.format(Locale.ROOT, "$%,d", v);
    }

    /// The package being read: its contents, and since when they've stayed the same.
    private net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> pendingFor = null;
    private long[] pendingGot = new long[N], pendingWorth = new long[N];
    private long pendingSince = 0L;
    static final long SETTLE_MS = 400L;

    private void tellPackage(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> menu, long[] got, long[] worth) {
        pendingFor = null;
        if (got == null || java.util.Arrays.stream(got).sum() <= 0) return;
        toldFor = menu;
        toldAt = System.currentTimeMillis();
        baseline = null;     // the package is the news, not the bait moving into your inventory after
        opened = 0;
        if (Configs.mainConfig.baitboxValueChat.get()) {
            Component msg = messageWorth(got, worth, 1);
            if (msg != null) FoeChat.send(msg);
        }
    }

    public void tick(Minecraft mc) {
        if (mc.player == null) { lastBait = null; lastBoxes = -1; baseline = null; return; }
        // A package open: say what's in it, once, when its contents have all arrived (the server fills the menu
        // over a few ticks; telling at the first slot gave a fraction of the box).
        long now0 = System.currentTimeMillis();
        net.minecraft.client.gui.screens.Screen open = mc.screen;
        if (open instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs && cs != toldFor && packageOpen(cs)) {
            long[] got = baitIn(cs.getMenu());
            if (cs != pendingFor || !java.util.Arrays.equals(got, pendingGot)) {
                pendingFor = cs;
                pendingGot = got;
                pendingWorth = worthIn(cs.getMenu());
                pendingSince = now0;
            } else if (now0 - pendingSince >= SETTLE_MS) {
                tellPackage(cs, got, worthIn(cs.getMenu()));
            }
        } else if (pendingFor != null && pendingFor != toldFor && open != pendingFor) {
            // closed before it settled: what it held last
            tellPackage(pendingFor, pendingGot, pendingWorth);
        }
        long[] bait = new long[N];
        int boxes = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            if (isBaitbox(s)) { boxes += s.getCount(); continue; }
            CustomData data = s.get(DataComponents.CUSTOM_DATA);
            if (data == null) continue;
            CompoundTag tag = data.copyTag();
            if (!isBaitType(tag.getStringOr("type", ""))) continue;
            int r = rarityOf(s);
            if (r >= 0) bait[r] += tag.getIntOr(dannypx.foe.item.TagObject.COUNTER, s.getCount());
        }
        long now = System.currentTimeMillis();
        if (lastBoxes >= 0 && boxes < lastBoxes && lastBait != null) {
            boxUsedAt = now;
            if (baseline == null) baseline = lastBait.clone();
            opened += lastBoxes - boxes;
            until = now + WINDOW_MS;
        }
        // bait arriving with no package seen (the box gave it straight to you): say so after the counting
        if (boxUsedAt > 0 && toldAt > 0 && Math.abs(toldAt - boxUsedAt) < 10_000L) baseline = null;
        if (baseline != null && now > until) {
            long[] got = new long[N];
            for (int r = 0; r < N; r++) got[r] = Math.max(0, bait[r] - baseline[r]);
            if (Configs.mainConfig.baitboxValueChat.get()) {
                Component msg = message(got, prices(), opened);
                if (msg != null) FoeChat.send(msg);
            }
            baseline = null;
            opened = 0;
        }
        lastBait = bait;
        lastBoxes = boxes;
    }

    static int rarityIndex(String rarity) {
        String r = (rarity == null ? "" : rarity).trim().toLowerCase(Locale.ROOT);
        if (r.startsWith("legend")) return 3;
        if (r.startsWith("mythic")) return 4;
        for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(r)) return i;
        return -1;
    }

    /// A baitbox, by its name ("Common Baitbox", "Bait Box", "Bait Crate").
    static boolean isBaitbox(ItemStack s) {
        String n = s.getHoverName().getString().toLowerCase(Locale.ROOT);
        return n.contains("baitbox") || n.contains("bait box") || n.contains("bait crate") || n.contains("bait bag");
    }

    static long[] prices() {
        var c = Configs.mainConfig;
        return new long[]{c.baitPriceCommon.get(), c.baitPriceRare.get(), c.baitPriceEpic.get(), c.baitPriceLegendary.get(), c.baitPriceMythical.get()};
    }

    /// Package-private for tests: the chat line for what came out of `opened` boxes, or null when nothing did: the
    /// box's total value and how much bait, the rarities on hover.
    /// <pre>
    /// [FOE+] BAITBOX  $8,250  (33 bait)
    /// </pre>
    static Component message(long[] got, long[] prices, int opened) {
        long[] worth = new long[N];
        for (int r = 0; r < Math.min(N, Math.min(got.length, prices.length)); r++) worth[r] = got[r] * prices[r];
        return messageWorth(got, worth, opened);
    }

    /// The same, with what each rarity's bait is worth worked out already (bait with your own prices).
    static Component messageWorth(long[] got, long[] worthByRarity, int opened) {
        long total = 0, count = 0;
        MutableComponent breakdown = Component.literal("What was inside").withStyle(net.minecraft.ChatFormatting.GOLD);
        int[] colors = {dannypx.foe.helper.FoeLocations.rarityColor("common"), dannypx.foe.helper.FoeLocations.rarityColor("rare"), dannypx.foe.helper.FoeLocations.rarityColor("epic"), dannypx.foe.helper.FoeLocations.rarityColor("legendary"), dannypx.foe.helper.FoeLocations.rarityColor("mythical")};
        String[] names = {"Common", "Rare", "Epic", "Legendary", "Mythical"};
        for (int r = 0; r < Math.min(N, got.length); r++) {
            if (got[r] <= 0) continue;
            long worth = worthByRarity[r];
            breakdown.append(Component.literal("\n" + String.format(Locale.ROOT, "%,d ", got[r])).withStyle(net.minecraft.ChatFormatting.WHITE))
                    .append(Component.literal(names[r]).withColor(colors[r]))
                    .append(Component.literal("  " + money(worth)).withStyle(net.minecraft.ChatFormatting.GRAY));
            total += worth;
            count += got[r];
        }
        if (count <= 0) return null;
        MutableComponent line = FoeChat.line(opened > 1 ? "BAITBOXES ×" + opened : "BAITBOX", FoeChat.GOLD)
                .append(Component.literal(money(total)).withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.BOLD))
                .append(Component.literal(String.format(Locale.ROOT, "  (%,d bait)", count)).withStyle(net.minecraft.ChatFormatting.GRAY));
        Component hover = breakdown;
        return line.withStyle(style -> style.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(hover)));
    }
}
