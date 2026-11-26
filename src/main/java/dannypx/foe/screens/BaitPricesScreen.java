package dannypx.foe.screens;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.store.BaitPriceOverrides;
import dannypx.foe.helper.WikiBaits;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bait Prices: what a bait is worth to you. Each rarity has a price (250 / 500 / 1,000 / 5,000 to start with), and any
 * single bait can have its own (an event making one bait dearer at one place): find it by name or by its group and
 * type its price; leave it empty and it goes by its rarity again. Opening a baitbox adds these up in chat. Bait packs
 * (Standard, Advanced and each location's, one per rarity, with the server's pictures) have no price of their own
 * yet: you type what each is worth to you.
 */
public class BaitPricesScreen extends Screen {
    private static final int PANEL_W = 396, H = 18, ROW = 20, PRICE_W = 74;

    record Rarity(String name, String letter, int color, ValidatedInt price, int start) {}

    private static List<Rarity> rarities() {
        var c = Configs.mainConfig;
        return List.of(new Rarity("Common", "C", dannypx.foe.helper.FoeLocations.rarityColor("common"), c.baitPriceCommon, 250), new Rarity("Rare", "R", dannypx.foe.helper.FoeLocations.rarityColor("rare"), c.baitPriceRare, 500),
                new Rarity("Epic", "E", dannypx.foe.helper.FoeLocations.rarityColor("epic"), c.baitPriceEpic, 1000), new Rarity("Legendary", "L", dannypx.foe.helper.FoeLocations.rarityColor("legendary"), c.baitPriceLegendary, 5000),
                new Rarity("Mythical", "M", dannypx.foe.helper.FoeLocations.rarityColor("mythical"), c.baitPriceMythical, 10000));
    }

    /// The groups across the top of the list.
    enum Group {
        ALL("All"), COMMON("Common"), RARE("Rare"), EPIC("Epic"), LEGENDARY("Legend"), MYTHICAL("Mythic"), LURES("Lures"), PACKS("Packs"), MINE("Yours");
        final String label;
        Group(String label) { this.label = label; }
    }

    private final Screen parent;
    private int left, top, listTop, listBottom;
    private Group group = Group.ALL;
    private String search = "";
    private boolean searchFocused = false;
    private int scroll = 0;
    private List<WikiBaits.Bait> shown = List.of();
    /// The list's own widgets (each bait's price box and × button), swapped on their own when the search or the
    /// scroll changes so the search box keeps your typing.
    private final List<net.minecraft.client.gui.components.AbstractWidget> rowWidgets = new ArrayList<>();
    private final List<EditBox> priceBoxes = new ArrayList<>();

    public BaitPricesScreen(Screen parent) {
        super(Component.literal("Bait Prices"));
        this.parent = parent;
    }

    /// A bait pack: its name, its rarity, and its picture (the server's own: a baked potato with this model number).
    record Pack(String name, String rarity, int model) {}

    /// Standard packs come Common to Epic; Advanced and the location packs Common to Mythical.
    static final List<Pack> PACKS = packs();

    private static List<Pack> packs() {
        List<Pack> out = new ArrayList<>();
        String[] all = {"Common", "Rare", "Epic", "Legendary", "Mythical"};
        for (int i = 0; i < 3; i++) out.add(new Pack("Standard " + all[i] + " Pack", all[i], 1));
        for (String r : all) out.add(new Pack("Advanced " + r + " Pack", r, 2));
        String[] places = {"Cypress Lake", "Kenai River", "Lake Biwa", "Murray River", "Everglades", "Key West", "Toledo Bend",
                "Great Lakes", "Danube River", "Amazon River", "Mediterranean Sea", "Cape Cod", "Hawaii", "Cairns", "Oil Rig",
                "Lofoten Islands", "Nile River"};
        for (int p = 0; p < places.length; p++)
            for (String r : all) out.add(new Pack(places[p] + " " + r + " Pack", r, 3 + p));
        return List.copyOf(out);
    }

    static Pack packOf(WikiBaits.Bait b) {
        if (!b.texture().equals("pack")) return null;
        for (Pack p : PACKS) if (p.name().equals(b.name())) return p;
        return null;
    }

    /// A pack's picture: the server pack draws it from the model number (a plain baked potato without the pack).
    private static ItemStack packPicture(Pack p) {
        ItemStack s = new ItemStack(net.minecraft.world.item.Items.BAKED_POTATO);
        s.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(List.of((float) p.model()), List.of(), List.of(), List.of()));
        return s;
    }

    static boolean isLure(WikiBaits.Bait b) {
        return b.texture().startsWith("lure_");
    }

    /// Package-private for tests: the baits in a group whose name has every word of the search in it.
    static List<WikiBaits.Bait> filter(List<WikiBaits.Bait> all, Group group, String search, java.util.function.Predicate<String> yours) {
        String[] words = search == null ? new String[0] : search.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<WikiBaits.Bait> out = new ArrayList<>();
        if (group == Group.PACKS) {
            all = new ArrayList<>();
            for (Pack p : PACKS) all.add(new WikiBaits.Bait(p.name(), p.rarity(), "pack"));
        }
        for (WikiBaits.Bait b : all) {
            boolean in = switch (group) {
                case ALL, PACKS -> true;
                case LURES -> isLure(b);
                case MINE -> yours.test(b.name());
                // lures too: a mythical lure is listed under Mythical (and under Lures)
                default -> !b.rarity().isEmpty() && b.rarity().toLowerCase(Locale.ROOT).startsWith(group.label.toLowerCase(Locale.ROOT));
            };
            if (!in) continue;
            String name = b.name().toLowerCase(Locale.ROOT);
            boolean match = true;
            for (String w : words) if (!w.isEmpty() && !name.contains(w)) { match = false; break; }
            if (match) out.add(b);
        }
        return out;
    }

    private static Rarity rarityOf(WikiBaits.Bait b) {
        for (Rarity r : rarities()) if (r.name().equalsIgnoreCase(b.rarity())) return r;
        return null;
    }

    /// Every bait and lure for the list: the wiki's, with the rarity FOE+ has seen on yours where the
    /// wiki has none (most lures), and the ones you've had that the wiki doesn't list (mythical baits).
    static List<WikiBaits.Bait> everything() {
        List<WikiBaits.Bait> out = new ArrayList<>();
        java.util.Set<String> known = new java.util.HashSet<>();
        for (WikiBaits.Bait b : WikiBaits.all()) {
            String rarity = b.rarity();
            String seen = dannypx.foe.handler.store.BaitPictures.rarityFor(b.name());
            if (seen != null && !seen.isEmpty()) rarity = seen;
            out.add(new WikiBaits.Bait(b.name(), rarity, b.texture()));
            known.add(b.name().toLowerCase(Locale.ROOT));
        }
        for (String[] s : dannypx.foe.handler.store.BaitPictures.seen()) {
            if (known.contains(s[0].toLowerCase(Locale.ROOT)) || WikiBaits.find(s[0]) != null) continue;
            out.add(new WikiBaits.Bait(s[0], s[1], (s[2].equals("lure") ? "lure_" : "bait_") + "seen"));
            known.add(s[0].toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private int rowsFit() {
        return Math.max(1, (listBottom - listTop) / ROW);
    }

    @Override
    protected void init() {
        left = width / 2 - PANEL_W / 2;
        top = Math.max(6, height / 2 - 150);
        int bottom = Math.min(height - 6, height / 2 + 150);
        listTop = top + 104;
        listBottom = bottom - 52;

        // the rarity prices, side by side
        int x = left + 12, w = (PANEL_W - 24) / 5;
        for (Rarity r : rarities()) {
            EditBox box = new EditBox(font, x + 14, top + 38, w - 20, H, Component.literal(r.name()));
            box.setMaxLength(9);
            box.setFilter(t -> t.matches("\\d*"));
            box.setValue(String.valueOf(r.price().get()));
            box.setTooltip(Tooltip.create(Component.literal("What one " + r.name() + " bait is worth, unless it has its own price")));
            box.setResponder(t -> {
                if (t.isEmpty()) return;
                try {
                    r.price().accept(Integer.parseInt(t));
                    Configs.mainConfig.save();
                } catch (NumberFormatException ignored) {}
            });
            addRenderableWidget(box);
            x += w;
        }

        // search
        EditBox find = new EditBox(font, left + 12, top + 62, PANEL_W - 24, H, Component.literal("Search"));
        find.setHint(Component.literal("Search " + everything().size() + " baits and lures...").withStyle(ChatFormatting.DARK_GRAY));
        find.setValue(search);
        find.setResponder(t -> {
            if (t.equals(search)) return;
            search = t;
            scroll = 0;
            buildRows();
        });
        addRenderableWidget(find);
        if (searchFocused) {
            setFocused(find);
            find.setFocused(true);
            find.moveCursorToEnd(false);
        }

        // groups
        int gx = left + 12, gw = (PANEL_W - 24) / Group.values().length;
        for (Group g : Group.values()) {
            Button b = Button.builder(Component.literal(g.label).withStyle(g == group ? ChatFormatting.YELLOW : ChatFormatting.GRAY),
                    btn -> { group = g; scroll = 0; searchFocused = false; rebuildWidgets(); }).bounds(gx, top + 84, gw - 2, 16).build();
            addRenderableWidget(b);
            gx += gw;
        }

        // the list
        rowWidgets.clear();
        priceBoxes.clear();
        buildRows();

        // bottom
        int by = listBottom + 8, half = (PANEL_W - 24) / 2 - 2;
        addRenderableWidget(Button.builder(Component.literal("Chat total: ").append(onOff(Configs.mainConfig.baitboxValueChat.get())),
                b -> { Configs.mainConfig.baitboxValueChat.accept(!Configs.mainConfig.baitboxValueChat.get()); Configs.mainConfig.save(); rebuildWidgets(); })
                .bounds(left + 12, by, half, H).build());
        addRenderableWidget(Button.builder(Component.literal("Tooltip value: ").append(onOff(Configs.mainConfig.baitTooltipValue.get())),
                b -> { Configs.mainConfig.baitTooltipValue.accept(!Configs.mainConfig.baitTooltipValue.get()); Configs.mainConfig.save(); rebuildWidgets(); })
                .bounds(left + 12 + half + 4, by, half, H).build());
        by += H + 4;
        addRenderableWidget(Button.builder(Component.literal("Reset all prices"), b -> {
            for (Rarity r : rarities()) r.price().accept(r.start());
            Configs.mainConfig.save();
            BaitPriceOverrides.clear();
            rebuildWidgets();
        }).bounds(left + 12, by, half, H).tooltip(Tooltip.create(Component.literal("Rarities back to 250 / 500 / 1,000 / 5,000 / 10,000, every bait back to its rarity, bait pack prices cleared"))).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(left + 12 + half + 4, by, half, H).build());
    }

    /// The rows the list shows now: their price boxes and × buttons (the old ones taken away first).
    private void buildRows() {
        for (var w : rowWidgets) {
            if (getFocused() == w) setFocused(null);
            removeWidget(w);
        }
        rowWidgets.clear();
        priceBoxes.clear();
        shown = filter(everything(), group, search, n -> BaitPriceOverrides.get(n) != null);
        scroll = Math.max(0, Math.min(scroll, shown.size() - rowsFit()));
        int y = listTop + 1;
        for (int i = scroll; i < Math.min(shown.size(), scroll + rowsFit()); i++) {
            WikiBaits.Bait bait = shown.get(i);
            Rarity r = rarityOf(bait);
            int px = left + PANEL_W - 12 - 16 - 2 - PRICE_W;
            EditBox price = new EditBox(font, px, y + 1, PRICE_W, ROW - 4, Component.literal(bait.name()));
            price.setMaxLength(9);
            price.setFilter(t -> t.matches("\\d*"));
            Long own = BaitPriceOverrides.get(bait.name());
            price.setValue(own == null ? "" : String.valueOf(own));
            Pack pack = packOf(bait);
            price.setHint(Component.literal(hintFor(bait, r)).withStyle(ChatFormatting.DARK_GRAY));
            price.setTooltip(Tooltip.create(pack != null ? packTooltip(pack)
                    : Component.literal("Your price for one " + bait.name() + ". Empty: its rarity's price.")));
            price.setResponder(t -> {
                try {
                    BaitPriceOverrides.set(bait.name(), t.isEmpty() ? null : Long.parseLong(t));
                } catch (NumberFormatException ignored) {}
            });
            rowWidgets.add(addRenderableWidget(price));
            priceBoxes.add(price);
            rowWidgets.add(addRenderableWidget(Button.builder(Component.literal("×"), btn -> {
                        BaitPriceOverrides.set(bait.name(), null);
                        price.setValue("");
                        setFocused(null);
                    }).bounds(left + PANEL_W - 12 - 16, y + 1, 16, ROW - 4)
                    .tooltip(Tooltip.create(Component.literal(pack != null ? "Clear your price" : "Back to its rarity's price"))).build()));
            y += ROW;
        }
    }

    /// The grey hint in an empty price box: a bait's rarity price, or "price?" for a pack (no price until you set one).
    private static String hintFor(WikiBaits.Bait bait, Rarity r) {
        if (packOf(bait) != null) return "price?";
        return r == null ? "by rarity" : String.format(Locale.ROOT, "%,d", r.price().get());
    }

    /// A pack's box tooltip.
    private static Component packTooltip(Pack p) {
        return Component.literal("Your price for one " + p.name() + ". Packs have no set price: fill it in yourself.");
    }

    private static Component onOff(boolean on) {
        return Component.literal(on ? "On" : "Off").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseY >= listTop && mouseY < listBottom && mouseX >= left && mouseX < left + PANEL_W && vertical != 0) {
            int next = Math.max(0, Math.min(scroll - (int) Math.signum(vertical) * 2, shown.size() - rowsFit()));
            if (next != scroll) {
                scroll = next;
                buildRows();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);
        int bottom = listBottom + 52;
        g.fill(left, top, left + PANEL_W, bottom, 0xC0101418);
        g.renderOutline(left, top, PANEL_W, bottom - top, 0xFF5A6770);
        // the list's well, its rows striped
        g.fill(left + 10, listTop - 1, left + PANEL_W - 10, listBottom + 1, 0x60000000);
        int y = listTop + 1;
        for (int i = scroll; i < Math.min(shown.size(), scroll + rowsFit()); i++) {
            if ((i & 1) == 1) g.fill(left + 10, y, left + PANEL_W - 10, y + ROW, 0x18FFFFFF);
            y += ROW;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, Component.literal("BAIT PRICES").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), width / 2, top + 8, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.literal("Rarity prices, and your own for any bait").withStyle(ChatFormatting.GRAY), width / 2, top + 20, 0xFFFFFFFF);

        int x = left + 12, w = (PANEL_W - 24) / 5;
        for (Rarity r : rarities()) {
            g.drawString(font, Component.literal(r.letter()).withColor(r.color()).withStyle(ChatFormatting.BOLD), x + 3, top + 43, 0xFFFFFFFF, true);
            x += w;
        }

        int y = listTop + 1;
        for (int i = scroll; i < Math.min(shown.size(), scroll + rowsFit()); i++) {
            WikiBaits.Bait bait = shown.get(i);
            Rarity r = rarityOf(bait);
            try {
                Pack pack = packOf(bait);
                ItemStack pic = pack != null ? packPicture(pack) : dannypx.foe.handler.store.BaitPictures.pictureFor(bait.name());
                if (pic != null && !pic.isEmpty()) g.renderItem(pic, left + 14, y + 2);
            } catch (RuntimeException ignored) {}
            boolean own = BaitPriceOverrides.get(bait.name()) != null;
            Component name = Component.literal(bait.name()).withColor(r == null ? 0xBBBBBB : r.color());
            int nameRoom = PANEL_W - 12 - 16 - 2 - PRICE_W - 10 - 34;
            String text = font.plainSubstrByWidth(bait.name(), nameRoom);
            if (!text.equals(bait.name())) name = Component.literal(text + "…").withColor(r == null ? 0xBBBBBB : r.color());
            g.drawString(font, name, left + 34, y + 6, 0xFFFFFFFF, true);
            if (own) g.drawString(font, Component.literal("●").withStyle(ChatFormatting.YELLOW), left + PANEL_W - 12 - 16 - 2 - PRICE_W - 9, y + 6, 0xFFFFFFFF, false);
            y += ROW;
        }
        for (EditBox box : priceBoxes) {
            if (!box.isFocused() || !box.getValue().isEmpty()) continue;
            Rarity r = null;
            int i = scroll + priceBoxes.indexOf(box);
            if (i < shown.size()) r = rarityOf(shown.get(i));
            String hint = i < shown.size() ? hintFor(shown.get(i), r) : "by rarity";
            g.drawString(font, Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY), box.getX() + 4 + font.width("_"), box.getY() + (box.getHeight() - 8) / 2, 0xFFFFFFFF, false);
        }
        if (shown.isEmpty()) g.drawCenteredString(font, Component.literal("No bait matches").withStyle(ChatFormatting.DARK_GRAY), width / 2, listTop + 10, 0xFFFFFFFF);
        // scrollbar
        int fit = rowsFit();
        if (shown.size() > fit) {
            int trackH = listBottom - listTop;
            int barH = Math.max(10, trackH * fit / shown.size());
            int barY = listTop + (trackH - barH) * scroll / Math.max(1, shown.size() - fit);
            g.fill(left + PANEL_W - 8, listTop, left + PANEL_W - 6, listBottom, 0x40FFFFFF);
            g.fill(left + PANEL_W - 8, barY, left + PANEL_W - 6, barY + barH, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        Configs.mainConfig.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    static String money(long v) {
        return String.format(Locale.ROOT, "$%,d", v);
    }
}
