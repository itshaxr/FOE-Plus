package dannypx.foe.screens;

import dannypx.foe.handler.store.NotifySounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * O → Customize → Sounds: every sound FOE+ plays on the left (event alerts, server events, catches, social lines,
 * warnings, and your own sound triggers), each with On/Off, its sound, volume and pitch and a ▶ to hear it; the
 * one you click opens on the right: quick picks, a search of every game and resource-pack sound, volume, pitch,
 * which of Minecraft's volume sliders it plays under, and Test. "Every notifier" changes all of FOE+'s own at once.
 * <p>
 * The quick-pick lists and the picker's idea come from ChatNotify by TerminalMC
 * (https://github.com/TerminalMC/ChatNotify, Apache-2.0).
 */
public class SoundsScreen extends Screen {
    private static final int ROW_H = 22, ROW_GAP = 3;
    static final String ALL = "*all*";

    /// ChatNotify's quick picks (by group), with names to read.
    static final String[][] QUICK = {
            {"Note blocks", "block.note_block.bell", "Bell", "block.note_block.chime", "Chime", "block.note_block.pling", "Pling",
                    "block.note_block.didgeridoo", "Didgeridoo", "block.note_block.bit", "Bit", "block.note_block.flute", "Flute",
                    "block.note_block.harp", "Harp", "block.note_block.xylophone", "Xylophone", "block.note_block.iron_xylophone", "Iron Xylophone",
                    "block.note_block.cow_bell", "Cow Bell", "block.note_block.guitar", "Guitar", "block.note_block.bass", "Bass",
                    "block.note_block.banjo", "Banjo", "block.note_block.basedrum", "Bass Drum", "block.note_block.snare", "Snare",
                    "block.note_block.hat", "Hat"},
            {"Power", "block.beacon.activate", "Beacon On", "block.beacon.deactivate", "Beacon Off", "block.conduit.activate", "Conduit On",
                    "block.beacon.power_select", "Beacon Power", "block.portal.trigger", "Portal", "block.end_portal_frame.fill", "Eye Placed",
                    "entity.enderman.teleport", "Enderman Teleport", "item.trident.return", "Trident Return",
                    "entity.elder_guardian.curse", "Elder Guardian", "entity.warden.sonic_boom", "Sonic Boom",
                    "entity.evoker.cast_spell", "Evoker Spell", "entity.zombie_villager.converted", "Villager Cured"},
            {"Explosions", "entity.firework_rocket.blast", "Firework Blast", "entity.firework_rocket.large_blast", "Large Firework",
                    "entity.firework_rocket.twinkle", "Firework Twinkle", "entity.lightning_bolt.thunder", "Thunder",
                    "entity.tnt.primed", "TNT Primed", "entity.generic.explode", "Explosion", "item.firecharge.use", "Fire Charge",
                    "block.fire.extinguish", "Fire Out"},
            {"Villagers", "entity.villager.ambient", "Villager", "entity.villager.yes", "Villager Yes", "entity.villager.no", "Villager No",
                    "entity.villager.trade", "Villager Trade", "entity.pillager.ambient", "Pillager", "entity.vindicator.celebrate", "Vindicator"},
            {"Misc", "block.bell.use", "Bell (village)", "block.amethyst_block.hit", "Amethyst", "block.amethyst_cluster.place", "Amethyst Cluster",
                    "entity.player.levelup", "Level Up", "item.totem.use", "Totem", "entity.experience_orb.pickup", "XP Orb",
                    "entity.arrow.hit_player", "Arrow Ding", "entity.allay.item_thrown", "Allay", "entity.iron_golem.repair", "Golem Repair",
                    "block.anvil.land", "Anvil", "item.shield.block", "Shield", "entity.goat.screaming.prepare_ram", "Goat Scream",
                    "ui.button.click", "Click"}};

    private final Screen parent;
    private int px, py, pw, ph, lx, lw, rx, rw, listTop, listBottom;
    private int scroll = 0, rightScroll = 0;
    /// The open row: a sound id, ALL, or "custom:<index>".
    private String selected = "witching_hour";
    private EditBox soundBox, nameBox, matchBox;
    private String typed = null;
    private final List<int[]> quickButtons = new ArrayList<>();

    public SoundsScreen(Screen parent) {
        super(Component.literal("Sounds"));
        this.parent = parent;
    }

    //region What's in the list
    /// A line of the list: a group heading (kind == null, custom == null), one of FOE+'s sounds, or one of yours.
    record Row(String heading, NotifySounds.Kind kind, NotifySounds.Custom custom, int customIndex, boolean all, boolean add) {}

    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        out.add(new Row("All at once", null, null, -1, false, false));
        out.add(new Row(null, null, null, -1, true, false));
        NotifySounds.Group group = null;
        for (NotifySounds.Kind k : NotifySounds.KINDS) {
            if (k.group() != group) {
                group = k.group();
                out.add(new Row(group.label, null, null, -1, false, false));
            }
            out.add(new Row(null, k, null, -1, false, false));
        }
        out.add(new Row(NotifySounds.Group.CUSTOM.label, null, null, -1, false, false));
        List<NotifySounds.Custom> mine = NotifySounds.custom();
        for (int i = 0; i < mine.size(); i++) out.add(new Row(null, null, mine.get(i), i, false, false));
        out.add(new Row(null, null, null, -1, false, true));
        return out;
    }

    private static int rowHeight(Row r) {
        return r.heading() != null ? 12 : ROW_H + ROW_GAP;
    }

    /// The settings the right side changes.
    private NotifySounds.Setting current() {
        if (selected.equals(ALL)) return allProxy();
        if (selected.startsWith("custom:")) {
            int i = Integer.parseInt(selected.substring(7));
            List<NotifySounds.Custom> mine = NotifySounds.custom();
            return i >= 0 && i < mine.size() ? mine.get(i) : NotifySounds.get("witching_hour");
        }
        return NotifySounds.get(selected);
    }

    private NotifySounds.Custom currentCustom() {
        return current() instanceof NotifySounds.Custom c ? c : null;
    }

    /// "Every notifier": shows the first of FOE+'s sounds; whatever you change on it goes to all of them.
    private NotifySounds.Setting allProxy() {
        NotifySounds.Setting first = NotifySounds.get(NotifySounds.KINDS.getFirst().id());
        NotifySounds.Setting p = new NotifySounds.Setting();
        p.on = NotifySounds.KINDS.stream().anyMatch(k -> NotifySounds.get(k.id()).on);
        p.sound = allSame(s -> s.sound) ? first.sound : first.sound;
        p.volume = first.volume;
        p.pitch = first.pitch;
        p.source = first.source;
        return p;
    }

    private static boolean allSame(java.util.function.Function<NotifySounds.Setting, Object> f) {
        Object v = f.apply(NotifySounds.get(NotifySounds.KINDS.getFirst().id()));
        for (NotifySounds.Kind k : NotifySounds.KINDS) if (!java.util.Objects.equals(f.apply(NotifySounds.get(k.id())), v)) return false;
        return true;
    }

    /// Applies a change to the open row (or to all of FOE+'s sounds for "Every notifier") and saves.
    private void change(java.util.function.Consumer<NotifySounds.Setting> edit) {
        if (selected.equals(ALL)) {
            for (NotifySounds.Kind k : NotifySounds.KINDS) edit.accept(NotifySounds.get(k.id()));
        } else {
            edit.accept(current());
        }
        NotifySounds.save();
    }

    /// Picking a sound: it's set, played, and turned on; FOE+'s old played-twice echo goes (your sound plays once).
    private void pick(String id) {
        change(s -> {
            s.sound = id;
            s.on = !id.isEmpty();
            s.echoPitch = 0f;
            s.echoVolume = 0f;
        });
        typed = null;
        if (!id.isEmpty()) NotifySounds.play(current(), true);
        rebuildWidgets();
    }
    //endregion

    //region Names
    static String soundName(String id) {
        if (id == null || id.isBlank()) return "None";
        String bare = id.startsWith("minecraft:") ? id.substring(10) : id;
        for (String[] g : QUICK) for (int i = 1; i + 1 < g.length; i += 2) if (g[i].equals(bare)) return g[i + 1];
        String last = bare.substring(bare.lastIndexOf('.') + 1).replace('_', ' ');
        return last.isEmpty() ? bare : Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }

    private static String sourceName(String source) {
        String s = NotifySounds.sourceOf(source).getName();
        return Component.translatable("soundCategory." + s).getString();
    }
    //endregion

    //region Layout
    @Override
    protected void init() {
        pw = Math.min(width - 16, 720);
        ph = Math.min(height - 16, 460);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        lx = px + 10;
        lw = Math.min(330, (pw - 30) * 47 / 100);
        rx = lx + lw + 12;
        rw = px + pw - 10 - rx;
        listTop = py + 42;
        listBottom = py + ph - 10;
        quickButtons.clear();

        NotifySounds.Setting s = current();
        NotifySounds.Custom custom = currentCustom();
        int y = py + 60 + (selected.equals(ALL) ? 12 : 0);

        if (custom != null) {
            nameBox = new EditBox(font, rx + 60, y, rw - 68, 14, Component.literal("Name"));
            nameBox.setMaxLength(40);
            nameBox.setValue(custom.name);
            nameBox.setResponder(v -> { custom.name = v; NotifySounds.save(); });
            addRenderableWidget(nameBox);
            y += 18;
            matchBox = new EditBox(font, rx + 60, y, rw - 68 - 64, 14, Component.literal("Chat text"));
            matchBox.setMaxLength(200);
            matchBox.setValue(custom.match);
            matchBox.setHint(Component.literal("text in the chat line").withStyle(ChatFormatting.DARK_GRAY));
            matchBox.setResponder(v -> { custom.match = v; NotifySounds.save(); });
            addRenderableWidget(matchBox);
            addRenderableWidget(Button.builder(Component.literal(custom.regex ? "Pattern" : "Contains"), b -> {
                custom.regex = !custom.regex;
                NotifySounds.save();
                rebuildWidgets();
            }).bounds(rx + rw - 8 - 60, y, 60, 14).build());
            y += 20;
        }

        // the sound: type to search every sound, or pick one below
        soundBox = new EditBox(font, rx + 8, y, rw - 16 - 44, 16, Component.literal("Sound"));
        soundBox.setMaxLength(240);
        soundBox.setValue(typed != null ? typed : (s.sound == null ? "" : s.sound));
        soundBox.setHint(Component.literal("none: type to search every sound").withStyle(ChatFormatting.DARK_GRAY));
        soundBox.setResponder(v -> {
            String was = current().sound == null ? "" : current().sound;
            if (v.equals(was)) return;
            typed = v;
            rightScroll = 0;
        });
        addRenderableWidget(soundBox);
        addRenderableWidget(Button.builder(Component.literal("None"), b -> pick("")).bounds(rx + rw - 8 - 40, y, 40, 16).build());
        y += 22;

        int bottom = listBottom - 2;
        int slidersY = bottom - 3 * 19;
        int bw = (rw - 20) / 2;
        if (typed != null && !typed.isBlank()) {
            // search results, under their count
            y += 10;
            List<String> found = search(typed);
            int max = Math.max(1, (slidersY - 4 - y) / 15) * 2;
            for (int i = rightScroll * 2; i < Math.min(found.size(), rightScroll * 2 + max); i++) {
                int col = (i - rightScroll * 2) % 2, row = (i - rightScroll * 2) / 2;
                String id = found.get(i);
                addRenderableWidget(new QuickButton(rx + 8 + col * (bw + 4), y + row * 15, bw, 13, id.replace("minecraft:", ""), id, this));
            }
        } else {
            // quick picks, in their groups
            int yy = y - rightScroll * 15;
            for (String[] g : QUICK) {
                if (yy >= y && yy < slidersY - 12) quickButtons.add(new int[]{yy, java.util.Arrays.asList(QUICK).indexOf(g)});
                yy += 10;
                for (int i = 1; i + 1 < g.length; i += 2) {
                    int k = (i - 1) / 2, col = k % 2;
                    if (yy >= y && yy + 13 <= slidersY - 4) {
                        addRenderableWidget(new QuickButton(rx + 8 + col * (bw + 4), yy, bw, 13, g[i + 1], g[i], this));
                    }
                    if (col == 1 || i + 2 >= g.length) yy += 15;
                }
                yy += 3;
            }
        }

        // volume, pitch, plays as, test
        int sx = rx + 8, sw = rw - 16;
        addRenderableWidget(new Slider(sx, slidersY, sw, 15, "Volume", s.volume, 0, 1, v -> change(t -> t.volume = v), true));
        addRenderableWidget(new Slider(sx, slidersY + 19, sw, 15, "Pitch", s.pitch, 0.5f, 2, v -> change(t -> t.pitch = v), false));
        addRenderableWidget(Button.builder(Component.literal("Plays as: " + sourceName(s.source)), b -> {
            SoundSource[] all = SoundSource.values();
            SoundSource next = all[(NotifySounds.sourceOf(current().source).ordinal() + 1) % all.length];
            change(t -> t.source = next.name());
            rebuildWidgets();
        }).bounds(sx, slidersY + 38, sw / 2 - 2, 15).build());
        addRenderableWidget(Button.builder(Component.literal("▶ Test"), b -> NotifySounds.play(current(), true))
                .bounds(sx + sw / 2 + 2, slidersY + 38, sw - sw / 2 - 2, 15).build());

        if (custom != null) {
            addRenderableWidget(Button.builder(Component.literal("Delete").withStyle(ChatFormatting.RED), b -> {
                NotifySounds.removeCustom(custom);
                selected = "witching_hour";
                rebuildWidgets();
            }).bounds(rx + rw - 8 - 50, py + 44, 50, 13).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(px + pw - 60, py + 8, 50, 16).build());
    }

    /// Every game and resource-pack sound with {@code text} in its name.
    private static List<String> search(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        // the game's own sounds, and every sound resource packs add
        java.util.Set<String> all = new java.util.TreeSet<>();
        for (Identifier id : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.keySet()) all.add(id.toString());
        for (Identifier id : Minecraft.getInstance().getSoundManager().getAvailableSounds()) all.add(id.toString());
        List<String> out = new ArrayList<>();
        for (String s : all) if (s.contains(t)) out.add(s);
        return out;
    }
    //endregion

    //region Drawing
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);
        FoeMainScreen.box(g, px, py, pw, ph, FoeMainScreen.RIM, 0xE0081320);
        FoeMainScreen.box(g, rx, listTop, rw, listBottom - listTop, FoeMainScreen.TILE_EDGE, 0xFF0D1A28);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawString(font, Component.literal("Notification Sounds").withStyle(ChatFormatting.BOLD), px + 12, py + 10, FoeMainScreen.TITLE, true);
        g.drawString(font, "Every sound FOE+ plays, and your own: pick any game or resource-pack sound", px + 12, py + 24, FoeMainScreen.DIM, false);
        g.fill(px + 10, py + 36, px + pw - 10, py + 37, FoeMainScreen.RULE);

        // the list
        g.enableScissor(lx, listTop, lx + lw, listBottom);
        int y = listTop - scroll;
        for (Row r : rows()) {
            int h = rowHeight(r);
            if (y + h > listTop && y < listBottom) drawRow(g, r, lx, y, lw, mouseX, mouseY);
            y += h;
        }
        g.disableScissor();

        // the right side's heading
        NotifySounds.Setting s = current();
        NotifySounds.Custom custom = currentCustom();
        Component title = selected.equals(ALL) ? Component.literal("Every notifier").withColor(0xFFD966)
                : custom != null ? Component.literal(custom.name).withColor(0xFFFFFF)
                : Component.literal(kindOf(selected).label()).withColor(kindOf(selected).color());
        g.drawString(font, Component.empty().append(title).append(Component.literal("  sound").withColor(FoeMainScreen.DIM & 0xFFFFFF)), rx + 8, listTop + 6, -1, true);
        if (selected.equals(ALL)) g.drawString(font, "Changes here go to every one of FOE+'s sounds", rx + 8, listTop + 18, FoeMainScreen.DIM, false);
        if (custom != null) {
            g.drawString(font, "Name", rx + 8, nameBox.getY() + 3, FoeMainScreen.TEXT, false);
            g.drawString(font, "Chat text", rx + 8, matchBox.getY() + 3, FoeMainScreen.TEXT, false);
        }
        if (typed == null || typed.isBlank()) {
            for (int[] q : quickButtons) g.drawString(font, QUICK[q[1]][0].toUpperCase(Locale.ROOT), rx + 8, q[0] + 1, FoeMainScreen.DIM, false);
        } else {
            int n = search(typed).size();
            g.drawString(font, n == 0 ? "No sound has \"" + typed + "\" in its name" : n + " sounds (scroll for more)", rx + 8, soundBox.getY() + 19, FoeMainScreen.DIM, false);
        }
    }

    private static NotifySounds.Kind kindOf(String id) {
        return NotifySounds.KINDS.stream().filter(k -> k.id().equals(id)).findFirst().orElse(NotifySounds.KINDS.getFirst());
    }

    private void drawRow(GuiGraphics g, Row r, int x, int y, int w, int mx, int my) {
        if (r.heading() != null) {
            g.drawString(font, r.heading().toUpperCase(Locale.ROOT), x + 1, y + 2, FoeMainScreen.DIM, false);
            return;
        }
        if (r.add()) {
            boolean hot = mx >= x && mx < x + w && my >= y && my < y + ROW_H;
            FoeMainScreen.box(g, x, y, w, ROW_H, hot ? FoeMainScreen.ACCENT : FoeMainScreen.TILE_EDGE, hot ? FoeMainScreen.TILE_HOT : FoeMainScreen.TILE);
            g.drawCenteredString(font, "+ Add a sound trigger (a sound when chat says…)", x + w / 2, y + 7, FoeMainScreen.ACCENT);
            return;
        }
        String id = rowId(r);
        NotifySounds.Setting s = r.all() ? allProxy() : r.custom() != null ? r.custom() : NotifySounds.get(r.kind().id());
        boolean sel = id.equals(selected);
        FoeMainScreen.box(g, x, y, w, ROW_H, sel ? FoeMainScreen.ACCENT : FoeMainScreen.TILE_EDGE, sel ? FoeMainScreen.TILE_HOT : FoeMainScreen.TILE);
        String icon = r.all() ? "★" : "♦";
        int color = r.all() ? 0xFFD966 : r.custom() != null ? 0xFFFFFF : r.kind().color();
        String name = r.all() ? "Every notifier" : r.custom() != null ? r.custom().name : r.kind().label();
        g.drawString(font, Component.literal(icon).withColor(color), x + 6, y + 7, -1, true);
        int nameW = w - 6 - 12 - 26 - 4 - 92 - 4 - 18 - 4;
        g.drawString(font, font.plainSubstrByWidth(name, nameW), x + 18, y + 7, FoeMainScreen.TITLE, true);
        // on / off
        int bx = x + w - 4 - 18 - 4 - 92 - 4 - 26;
        FoeMainScreen.box(g, bx, y + 4, 26, 14, s.on ? 0xFF2E7D32 : 0xFF6B2B2B, s.on ? 0xFF1E5A22 : 0xFF4A1C1C);
        g.drawCenteredString(font, s.on ? "On" : "Off", bx + 13, y + 7, s.on ? 0xFF8BF08F : 0xFFF08B8B);
        // the sound, its volume and pitch
        int sx = bx + 30;
        FoeMainScreen.box(g, sx, y + 4, 92, 14, FoeMainScreen.RULE, 0xFF0D1A28);
        String label = r.all() && !allSame(t -> t.sound) ? "(mixed)" : soundName(s.sound);
        // the volume only when it isn't full
        String tail = s.sound == null || s.sound.isBlank() || s.volume >= 0.995f ? "" : " " + Math.round(s.volume * 100) + "%";
        g.drawString(font, Component.literal("♪ ").withColor(FoeMainScreen.ACCENT & 0xFFFFFF)
                .append(Component.literal(font.plainSubstrByWidth(label, 92 - 6 - font.width("♪ ") - font.width(tail))).withColor((s.sound == null || s.sound.isBlank() ? FoeMainScreen.DIM : FoeMainScreen.TEXT) & 0xFFFFFF))
                .append(Component.literal(tail).withColor(FoeMainScreen.DIM & 0xFFFFFF)), sx + 3, y + 7, -1, false);
        // ▶
        FoeMainScreen.box(g, x + w - 22, y + 4, 18, 14, FoeMainScreen.TILE_EDGE, 0xFF203A55);
        g.drawCenteredString(font, "▶", x + w - 13, y + 7, FoeMainScreen.ACCENT);
    }

    private static String rowId(Row r) {
        return r.all() ? ALL : r.custom() != null ? "custom:" + r.customIndex() : r.kind() != null ? r.kind().id() : "";
    }
    //endregion

    //region Input
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (mx >= lx && mx < lx + lw && my >= listTop && my < listBottom) {
            int y = listTop - scroll;
            for (Row r : rows()) {
                int h = rowHeight(r);
                if (r.heading() == null && my >= y && my < y + ROW_H) {
                    click(r, mx, y);
                    return true;
                }
                y += h;
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void click(Row r, double mx, int y) {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
        if (r.add()) {
            NotifySounds.addCustom();
            selected = "custom:" + (NotifySounds.custom().size() - 1);
            typed = null;
            rebuildWidgets();
            return;
        }
        String id = rowId(r);
        int x = lx, w = lw;
        int bx = x + w - 4 - 18 - 4 - 92 - 4 - 26;
        if (mx >= x + w - 22) {
            // ▶
            NotifySounds.Setting s = r.all() ? NotifySounds.get(NotifySounds.KINDS.getFirst().id()) : r.custom() != null ? r.custom() : NotifySounds.get(r.kind().id());
            NotifySounds.play(s, true);
            return;
        }
        if (mx >= bx && mx < bx + 26) {
            // on / off
            if (r.all()) {
                boolean to = !allProxy().on;
                for (NotifySounds.Kind k : NotifySounds.KINDS) NotifySounds.get(k.id()).on = to;
            } else {
                NotifySounds.Setting s = r.custom() != null ? r.custom() : NotifySounds.get(r.kind().id());
                s.on = !s.on;
            }
            NotifySounds.save();
            return;
        }
        selected = id;
        typed = null;
        rightScroll = 0;
        rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (mx >= lx && mx < lx + lw) {
            int total = 0;
            for (Row r : rows()) total += rowHeight(r);
            int max = Math.max(0, total - (listBottom - listTop));
            scroll = Math.max(0, Math.min(max, scroll + (v > 0 ? -25 : 25)));
            return true;
        }
        if (mx >= rx && mx < rx + rw) {
            rightScroll = Math.max(0, rightScroll + (v > 0 ? -1 : 1));
            rightScroll = Math.min(rightScroll, typed != null && !typed.isBlank() ? Math.max(0, search(typed).size() / 2) : 40);
            String keep = soundBox == null ? null : soundBox.getValue();
            rebuildWidgets();
            if (typed != null && soundBox != null && keep != null) {
                soundBox.setValue(keep);
                setFocused(soundBox);
                soundBox.setFocused(true);
            }
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public void tick() {
        // a search as you type: rebuild the results (keeping the box focused)
        if (soundBox != null && typed != null && !typed.equals(shownSearch)) {
            shownSearch = typed;
            String keep = soundBox.getValue();
            int cursor = soundBox.getCursorPosition();
            rebuildWidgets();
            soundBox.setValue(keep);
            soundBox.setCursorPosition(cursor);
            setFocused(soundBox);
            soundBox.setFocused(true);
        }
    }

    private String shownSearch = null;

    @Override
    public void onClose() {
        NotifySounds.save();
        Minecraft.getInstance().setScreen(parent);
    }
    //endregion

    //region Widgets
    /// A quick pick or a search result: picks the sound and plays it.
    static final class QuickButton extends Button {
        final String id;
        QuickButton(int x, int y, int w, int h, String label, String id, SoundsScreen screen) {
            super(x, y, w, h, Component.literal(label), b -> screen.pick(id), DEFAULT_NARRATION);
            this.id = id;
        }

        @Override
        protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            SoundsScreen s = Minecraft.getInstance().screen instanceof SoundsScreen ss ? ss : null;
            String cur = s == null ? "" : s.current().sound;
            boolean sel = cur != null && (cur.equals(id) || ("minecraft:" + cur).equals(id) || cur.equals("minecraft:" + id));
            boolean hot = isHovered();
            FoeMainScreen.box(g, getX(), getY(), width, height, sel || hot ? FoeMainScreen.ACCENT : FoeMainScreen.TILE_EDGE, sel ? FoeMainScreen.TILE_HOT : FoeMainScreen.TILE);
            var font = Minecraft.getInstance().font;
            g.drawCenteredString(font, font.plainSubstrByWidth(getMessage().getString(), width - 4), getX() + width / 2, getY() + 3, sel ? FoeMainScreen.TITLE : FoeMainScreen.TEXT);
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager manager) {
            // the pick plays its own sound
        }
    }

    /// Volume (0–100%) or pitch (0.5–2.0).
    static final class Slider extends AbstractSliderButton {
        final String label;
        final float min, max;
        final java.util.function.Consumer<Float> apply;
        final boolean percent;

        Slider(int x, int y, int w, int h, String label, float value, float min, float max, java.util.function.Consumer<Float> apply, boolean percent) {
            super(x, y, w, h, Component.empty(), (Math.max(min, Math.min(max, value)) - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.apply = apply;
            this.percent = percent;
            updateMessage();
        }

        float real() {
            float v = min + (float) value * (max - min);
            return percent ? Math.round(v * 100f) / 100f : Math.round(v * 20f) / 20f;
        }

        @Override
        protected void updateMessage() {
            float v = real();
            setMessage(Component.literal(label + ": " + (percent ? Math.round(v * 100) + "%" : String.format(Locale.ROOT, "%.2f", v))));
        }

        @Override
        protected void applyValue() {
            apply.accept(real());
        }
    }
    //endregion
}
