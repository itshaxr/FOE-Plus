package dannypx.foe.screens;

import dannypx.foe.handler.store.BaitAbuse;
import dannypx.foe.helper.FoeLocations;
import dannypx.foe.helper.WikiBaits;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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
 * Bait Abuse (O → Tools): the best baits at each place, for each goal, best first. Pick a place along the top (or All),
 * search by bait, place, goal or condition, and turn on Edit to change any line, add one or take one away: it's all
 * yours, saved on your computer, so it can follow the game when something changes.
 */
public class BaitAbuseScreen extends Screen {
    private static final int PANEL_W = 440, H = 16, PLACE_H = 18, GOAL_H = 14, ITEM_H = 18;

    private final Screen parent;
    private int left, top, bottom, listTop, listBottom;
    private String place = "";      // "" = all
    private String search = "";
    private boolean searchFocused = false;
    private boolean editing = false;
    private int scroll = 0;         // in pixels
    private List<Row> rows = List.of();
    private final List<AbstractWidget> rowWidgets = new ArrayList<>();

    /// One line of the list: a place's heading, a goal's heading, or a bait.
    private record Row(String place, String goal, BaitAbuse.Entry entry, int height) {
        boolean isPlace() { return goal == null; }
        boolean isGoal() { return goal != null && entry == null; }
    }

    public BaitAbuseScreen(Screen parent) {
        super(Component.literal("Bait Abuse"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = width / 2 - PANEL_W / 2;
        top = Math.max(4, height / 2 - 170);
        bottom = Math.min(height - 4, height / 2 + 170);

        // search, and the edit switch
        EditBox find = new EditBox(font, left + 12, top + 34, PANEL_W - 24 - 110, H, Component.literal("Search"));
        find.setHint(Component.literal("Search baits, places, goals...").withStyle(ChatFormatting.DARK_GRAY));
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
        addRenderableWidget(Button.builder(Component.literal(editing ? "Editing" : "Edit").withStyle(editing ? ChatFormatting.YELLOW : ChatFormatting.WHITE), b -> {
                    editing = !editing;
                    searchFocused = false;
                    rebuildWidgets();
                }).bounds(left + PANEL_W - 12 - 106, top + 34, 106, H)
                .tooltip(Tooltip.create(Component.literal("Change any line, add one (+) or take one away (×). Saved as you go"))).build());

        // the places, wrapped over as many rows as they need
        List<String> places = new ArrayList<>();
        places.add("");
        places.addAll(BaitAbuse.places());
        if (!place.isEmpty() && places.stream().noneMatch(p -> p.equalsIgnoreCase(place))) place = "";
        int x = left + 12, y = top + 54, maxX = left + PANEL_W - 12;
        for (String p : places) {
            String label = p.isEmpty() ? "All" : p;
            int w = font.width(label) + 10;
            if (x + w > maxX) { x = left + 12; y += 16; }
            boolean on = p.equalsIgnoreCase(place);
            Component text = p.isEmpty() ? Component.literal(label).withStyle(on ? ChatFormatting.YELLOW : ChatFormatting.GRAY)
                    : on ? Component.literal(label).withStyle(ChatFormatting.YELLOW) : FoeLocations.styledName(p);
            addRenderableWidget(Button.builder(text, b -> { place = p; scroll = 0; searchFocused = false; rebuildWidgets(); })
                    .bounds(x, y, w, 14).build());
            x += w + 2;
        }
        listTop = y + 20;
        listBottom = bottom - (editing ? 48 : 26);

        rowWidgets.clear();
        buildRows();

        // bottom
        int by = bottom - 22;
        if (editing) {
            // a new place (its first bait under "Epic", to be changed)
            EditBox newPlace = new EditBox(font, left + 12, by - 22, 150, H, Component.literal("New place"));
            newPlace.setHint(Component.literal("New place...").withStyle(ChatFormatting.DARK_GRAY));
            addRenderableWidget(newPlace);
            addRenderableWidget(Button.builder(Component.literal("+ Place"), b -> {
                String name = newPlace.getValue().trim();
                if (name.isEmpty()) return;
                BaitAbuse.add(new BaitAbuse.Entry(name, "Epic", "", 1, "New bait", false));
                place = name;
                rebuildWidgets();
            }).bounds(left + 166, by - 22, 60, H).build());
            addRenderableWidget(Button.builder(Component.literal("Reset to FOE+'s list"), b -> {
                BaitAbuse.reset();
                place = "";
                rebuildWidgets();
            }).bounds(left + PANEL_W - 12 - 150, by - 22, 150, H)
                    .tooltip(Tooltip.create(Component.literal("Every change you made goes: back to the list FOE+ came with"))).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 - 50, by, 100, 18).build());
    }

    /// Package-private for tests: whether an entry shows for this search (every word in its bait, place, goal or
    /// condition).
    static boolean matches(BaitAbuse.Entry e, String search) {
        if (search == null || search.isBlank()) return true;
        String all = (e.bait + " " + e.place + " " + e.goal + " " + e.condition).toLowerCase(Locale.ROOT);
        for (String w : search.toLowerCase(Locale.ROOT).trim().split("\\s+")) if (!w.isEmpty() && !all.contains(w)) return false;
        return true;
    }

    private List<Row> layout() {
        List<Row> out = new ArrayList<>();
        for (String p : BaitAbuse.places()) {
            if (!place.isEmpty() && !p.equalsIgnoreCase(place)) continue;
            List<Row> inPlace = new ArrayList<>();
            for (String g : BaitAbuse.goals(p)) {
                List<Row> inGoal = new ArrayList<>();
                for (BaitAbuse.Entry e : BaitAbuse.of(p, g)) if (matches(e, search)) inGoal.add(new Row(p, g, e, ITEM_H));
                if (inGoal.isEmpty() && !(editing && search.isBlank())) continue;
                inPlace.add(new Row(p, g, null, GOAL_H));
                inPlace.addAll(inGoal);
            }
            if (inPlace.isEmpty()) continue;
            out.add(new Row(p, null, null, PLACE_H));
            out.addAll(inPlace);
        }
        return out;
    }

    private int contentHeight() {
        int h = 0;
        for (Row r : rows) h += r.height();
        return h;
    }

    /// The rows' own widgets (only in Edit), made again when the list changes.
    private void buildRows() {
        for (AbstractWidget w : rowWidgets) {
            if (getFocused() == w) setFocused(null);
            removeWidget(w);
        }
        rowWidgets.clear();
        rows = layout();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight() - (listBottom - listTop))));
        if (!editing) return;
        int y = listTop - scroll;
        int right = left + PANEL_W - 14;
        for (Row r : rows) {
            int ry = y;
            y += r.height();
            if (ry < listTop || ry + r.height() > listBottom) continue;
            if (r.isPlace()) {
                // a new goal here
                EditBox goal = new EditBox(font, right - 16 - 4 - 100, ry + 1, 100, PLACE_H - 4, Component.literal("New goal"));
                goal.setHint(Component.literal("New goal...").withStyle(ChatFormatting.DARK_GRAY));
                rowWidgets.add(addRenderableWidget(goal));
                rowWidgets.add(addRenderableWidget(Button.builder(Component.literal("+"), b -> {
                    String g = goal.getValue().trim();
                    if (g.isEmpty()) return;
                    BaitAbuse.add(new BaitAbuse.Entry(r.place(), g, "", 1, "New bait", false));
                    buildRows();
                }).bounds(right - 16, ry + 1, 16, PLACE_H - 4).tooltip(Tooltip.create(Component.literal("Add this goal here"))).build()));
            } else if (r.isGoal()) {
                rowWidgets.add(addRenderableWidget(Button.builder(Component.literal("+"), b -> {
                    int next = 1;
                    for (BaitAbuse.Entry e : BaitAbuse.of(r.place(), r.goal())) next = Math.max(next, e.priority + 1);
                    BaitAbuse.add(new BaitAbuse.Entry(r.place(), r.goal(), "", next, "New bait", false));
                    buildRows();
                }).bounds(right - 16, ry, 16, GOAL_H - 1).tooltip(Tooltip.create(Component.literal("Add a bait to " + r.goal()))).build()));
            } else {
                BaitAbuse.Entry e = r.entry();
                int x = left + 16;
                EditBox pri = new EditBox(font, x, ry + 1, 24, ITEM_H - 4, Component.literal("Order"));
                pri.setMaxLength(2);
                pri.setFilter(t -> t.matches("\\d*"));
                pri.setValue(String.valueOf(e.priority));
                pri.setTooltip(Tooltip.create(Component.literal("Its place in the order (1: the best)")));
                pri.setResponder(t -> {
                    if (t.isEmpty()) return;
                    try {
                        e.priority = Math.max(1, Integer.parseInt(t));
                        BaitAbuse.save();
                    } catch (NumberFormatException ignored) {}
                });
                rowWidgets.add(addRenderableWidget(pri));
                x += 28 + 20;   // the bait's picture
                EditBox bait = new EditBox(font, x, ry + 1, 150, ITEM_H - 4, Component.literal("Bait"));
                bait.setMaxLength(60);
                bait.setValue(e.bait);
                bait.setResponder(t -> { e.bait = t; BaitAbuse.save(); });
                rowWidgets.add(addRenderableWidget(bait));
                x += 154;
                EditBox cond = new EditBox(font, x, ry + 1, 96, ITEM_H - 4, Component.literal("Condition"));
                cond.setMaxLength(40);
                cond.setValue(e.condition);
                cond.setHint(Component.literal("Always").withStyle(ChatFormatting.DARK_GRAY));
                cond.setTooltip(Tooltip.create(Component.literal("Only when... (1k+ Luck, Booster). Empty: always")));
                cond.setResponder(t -> { e.condition = t; BaitAbuse.save(); });
                rowWidgets.add(addRenderableWidget(cond));
                x += 100;
                rowWidgets.add(addRenderableWidget(Button.builder(Component.literal("Tie").withStyle(e.tied ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY), b -> {
                    e.tied = !e.tied;
                    BaitAbuse.save();
                    buildRows();
                }).bounds(x, ry + 1, 26, ITEM_H - 4).tooltip(Tooltip.create(Component.literal("Tied: as good as the others at its number"))).build()));
                rowWidgets.add(addRenderableWidget(Button.builder(Component.literal("×").withStyle(ChatFormatting.RED), b -> {
                    BaitAbuse.remove(e);
                    buildRows();
                }).bounds(right - 16, ry + 1, 16, ITEM_H - 4).tooltip(Tooltip.create(Component.literal("Take it away"))).build()));
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseY >= listTop && mouseY < listBottom && mouseX >= left && mouseX < left + PANEL_W && vertical != 0) {
            int max = Math.max(0, contentHeight() - (listBottom - listTop));
            int next = Math.max(0, Math.min(scroll - (int) Math.signum(vertical) * 24, max));
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
        g.fill(left, top, left + PANEL_W, bottom, 0xC0101418);
        g.renderOutline(left, top, PANEL_W, bottom - top, 0xFF5A6770);
        g.fill(left + 10, listTop - 1, left + PANEL_W - 10, listBottom + 1, 0x60000000);
    }

    private static int goalColour(String goal) {
        String g = goal.toLowerCase(Locale.ROOT);
        for (String r : new String[]{"common", "rare", "epic", "legendary", "mythical"}) {
            if (g.startsWith(r.substring(0, 4))) {
                int c = FoeLocations.rarityColor(r);
                if (c >= 0) return c;
            }
        }
        return 0x55FF55;   // pet leveling and the rest
    }

    /// A bait's picture: by its name, else by the bait that name means ("Shiner/Minnow": Shiner Live Bait's).
    private static ItemStack picture(String bait) {
        ItemStack pic = dannypx.foe.handler.store.BaitPictures.pictureFor(bait);
        if (pic != null && !pic.isEmpty()) return pic;
        WikiBaits.Bait b = WikiBaits.findAny(bait);
        return b == null || b.name().equalsIgnoreCase(bait) ? ItemStack.EMPTY : dannypx.foe.handler.store.BaitPictures.pictureFor(b.name());
    }

    private static int baitColour(String bait) {
        WikiBaits.Bait b = WikiBaits.findAny(bait);
        int c = b == null || b.rarity().isEmpty() ? -1 : FoeLocations.rarityColor(b.rarity().toLowerCase(Locale.ROOT));
        return c >= 0 ? c : 0xE0E0E0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, Component.literal("BAIT ABUSE").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), width / 2, top + 8, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.literal("The best baits at each place, best first").withStyle(ChatFormatting.GRAY), width / 2, top + 20, 0xFFFFFFFF);

        g.enableScissor(left + 10, listTop, left + PANEL_W - 10, listBottom);
        int y = listTop - scroll;
        int right = left + PANEL_W - 14;
        boolean stripe = false;
        for (Row r : rows) {
            int ry = y;
            y += r.height();
            if (ry + r.height() < listTop || ry > listBottom) continue;
            if (r.isPlace()) {
                // the place: its name in its colours, a line under it
                g.fill(left + 12, ry + 2, right, ry + PLACE_H - 1, 0x40FFFFFF);
                g.drawString(font, FoeLocations.styledName(r.place()).copy().withStyle(ChatFormatting.BOLD), left + 16, ry + 6, 0xFFFFFFFF, true);
                int count = 0;
                for (String goal : BaitAbuse.goals(r.place())) count += BaitAbuse.of(r.place(), goal).size();
                if (!editing) {
                    String n = count + (count == 1 ? " bait" : " baits");
                    g.drawString(font, Component.literal(n).withStyle(ChatFormatting.DARK_GRAY), right - 4 - font.width(n), ry + 6, 0xFFFFFFFF, false);
                }
                stripe = false;
            } else if (r.isGoal()) {
                g.drawString(font, Component.literal(r.goal().toUpperCase(Locale.ROOT)).withColor(goalColour(r.goal())).withStyle(ChatFormatting.BOLD),
                        left + 20, ry + 3, 0xFFFFFFFF, true);
                stripe = false;
            } else {
                BaitAbuse.Entry e = r.entry();
                if (stripe) g.fill(left + 12, ry, right, ry + ITEM_H, 0x14FFFFFF);
                stripe = !stripe;
                int x = left + 16;
                if (!editing) {
                    // its number: gold for 1, silver for 2, bronze for 3; "=" when tied
                    int c = e.priority == 1 ? 0xFFD700 : e.priority == 2 ? 0xC0C0C0 : e.priority == 3 ? 0xCD7F32 : 0x9A9A9A;
                    String num = (e.tied ? "=" : "#") + e.priority;
                    g.drawString(font, Component.literal(num).withColor(c).withStyle(ChatFormatting.BOLD), x, ry + 5, 0xFFFFFFFF, true);
                }
                x += 28;
                try {
                    ItemStack pic = picture(e.bait);
                    if (pic != null && !pic.isEmpty()) g.renderItem(pic, x, ry + 1);
                } catch (RuntimeException ignored) {}
                x += 20;
                if (!editing) {
                    g.drawString(font, Component.literal(e.bait).withColor(baitColour(e.bait)), x, ry + 5, 0xFFFFFFFF, true);
                    int tx = right - 4;
                    if (!e.condition.isBlank()) {
                        // its condition, as a little tag on the right
                        String c = e.condition;
                        int w = font.width(c) + 8;
                        tx -= w;
                        g.fill(tx, ry + 3, tx + w, ry + ITEM_H - 3, 0xFF2A3540);
                        g.drawString(font, Component.literal(c).withColor(0xFFD866), tx + 4, ry + 5, 0xFFFFFFFF, false);
                        tx -= 4;
                    }
                    if (e.tied) {
                        String t = "tied";
                        tx -= font.width(t);
                        g.drawString(font, Component.literal(t).withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC), tx, ry + 5, 0xFFFFFFFF, false);
                    }
                }
            }
        }
        g.disableScissor();
        if (rows.isEmpty()) g.drawCenteredString(font, Component.literal("Nothing matches").withStyle(ChatFormatting.DARK_GRAY), width / 2, listTop + 10, 0xFFFFFFFF);

        // scrollbar
        int total = contentHeight(), view = listBottom - listTop;
        if (total > view) {
            int barH = Math.max(10, view * view / total);
            int barY = listTop + (view - barH) * scroll / Math.max(1, total - view);
            g.fill(left + PANEL_W - 8, listTop, left + PANEL_W - 6, listBottom, 0x40FFFFFF);
            g.fill(left + PANEL_W - 8, barY, left + PANEL_W - 6, barY + barH, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        BaitAbuse.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
