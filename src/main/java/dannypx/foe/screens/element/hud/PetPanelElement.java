package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.InventoryHandler;
import dannypx.foe.item.PetHeldItem;
import dannypx.foe.item.PetTagObject;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/// Equipped pet in FOE's panel style: pet icon, name and level, XP progress and rating.
/// Ported from FOECollab's PetEquipHud / PetEquipHudHandler (AGPL-3.0); the pet data comes
/// from FOE-R's inventory tracking.
public class PetPanelElement extends PanelElement {
    public PetPanelElement() {
        this(false);
    }

    public PetPanelElement(boolean isCopy) {
        super(Configs.hudConfig.petPanelXPosition.get() / 100f,
                Configs.hudConfig.petPanelYPosition.get() / 100f,
                Configs.hudConfig.petPanelAlignment.get(),
                Component.literal("Pet Panel"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showPetPanel.get(); }
    @Override protected float configX() { return Configs.hudConfig.petPanelXPosition.get() / 100f; }
    @Override protected float configY() { return Configs.hudConfig.petPanelYPosition.get() / 100f; }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.petPanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.petPanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.petPanelOpacity.get(); }

    @Override
    protected boolean alignTextToAnchor() {
        return false;
    }

    @Override
    protected Component title() {
        return Component.literal("ᴘᴇᴛ").withStyle(ChatFormatting.BOLD);
    }

    private static boolean hasPet() {
        return InventoryHandler.instance().hasPet();
    }

    private static final int UNKNOWN_BORDER = 0xFFAAAAAA;

    /// The pet's rarity id ("common"...), from its data or, failing that, the rarity badge in
    /// its description. Package-private for tests.
    static String petRarity(PetTagObject pet) {
        String rarity = pet.getRarity();
        if (dannypx.foe.helper.FoeLocations.rarityColor(rarity) >= 0) return rarity;
        try {
            String badge = pet.getRarityComponent().getString();
            return rarityFromText(badge);
        } catch (RuntimeException e) {
            return rarity;
        }
    }

    static String rarityFromText(String text) {
        if (text == null) return "";
        String t = text.toLowerCase(java.util.Locale.ROOT);
        if (t.indexOf('\uF033') >= 0 || t.contains("common")) return "common";
        if (t.indexOf('\uF034') >= 0 || t.contains("rare")) return "rare";
        if (t.indexOf('\uF035') >= 0 || t.contains("epic")) return "epic";
        if (t.indexOf('\uF036') >= 0 || t.contains("legendary")) return "legendary";
        if (t.indexOf('\uF037') >= 0 || t.contains("mythic")) return "mythical";
        if (t.contains("special")) return "special";
        return "";
    }
    private static final int HELD_ITEM_BORDER = 0xFFD9A441;

    /// The pet, then the item it holds (an empty box when it holds nothing).
    @Override
    protected List<PanelIcon> icons() {
        if (!hasPet()) return List.of();
        PetTagObject pet = InventoryHandler.instance().getCurrentPet();
        if (!dannypx.foe.config.Configs.hudConfig.petPanelItemBox.get()) return List.of(new PanelIcon(pet.getItemStack(), frameColor(pet)));
        return List.of(new PanelIcon(pet.getItemStack(), frameColor(pet)),
                new PanelIcon(PetHeldItem.of(pet), HELD_ITEM_BORDER));
    }

    /// The pet's frame: its rating's colour (default) or its rarity's; whichever it can't read, the other; else grey.
    static int frameColor(PetTagObject pet) {
        int rarity = dannypx.foe.helper.FoeLocations.rarityColor(petRarity(pet));
        int rating = -1;
        try {
            rating = ratingColor(pet.getRatingComponent());
        } catch (RuntimeException ignored) {
            // no rating to read
        }
        boolean byRating = frameByRating();
        int color = byRating ? (rating >= 0 ? rating : rarity) : (rarity >= 0 ? rarity : rating);
        return color < 0 ? UNKNOWN_BORDER : 0xFF000000 | color;
    }

    /// The colour of a rating's letters ("ᴇxᴄᴇʟʟᴇɴᴛ" in aqua), or -1. Package-private for tests.
    static int ratingColor(Component rating) {
        if (rating == null) return -1;
        return rating.visit((style, text) -> text.isBlank() || style.getColor() == null
                ? java.util.Optional.<Integer>empty() : java.util.Optional.of(style.getColor().getValue() & 0xFFFFFF),
                net.minecraft.network.chat.Style.EMPTY).orElse(-1);
    }

    private static boolean frameByRating() {
        try {
            return Configs.hudConfig.petPanelFrameColor.get() == dannypx.foe.type.PetFrameColor.RATING;
        } catch (Throwable e) {
            return true;
        }
    }

    // A little bigger than the other panels' boxes, close together.
    @Override protected int iconSize() { return 22; }
    @Override protected int iconGap() { return 2; }

    /// "Max Luck" / "Max Scale" / "Perfect" just above the pet, when its rolls here are as good as they get.
    @Override
    protected Component iconCaption() {
        if (!hasPet()) return null;
        return maxTag(InventoryHandler.instance().getCurrentPet());
    }

    private static ChatFormatting progressColor(double value) {
        return value <= 25 ? ChatFormatting.RED
                : value <= 50 ? ChatFormatting.GOLD
                : value <= 75 ? ChatFormatting.YELLOW
                : ChatFormatting.GREEN;
    }

    @Override
    protected List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        if (!hasPet()) {
            lines.add(Component.literal("No pet equipped").withStyle(ChatFormatting.RED));
            return lines;
        }

        PetTagObject pet = InventoryHandler.instance().getCurrentPet();
        float current = pet.contains(PetTagObject.XP_CURRENT) ? pet.getFloat(PetTagObject.XP_CURRENT) : 0f;
        float needed = pet.contains(PetTagObject.XP_NEED) ? pet.getFloat(PetTagObject.XP_NEED) : 0f;
        Component rating = pet.getRatingComponent();
        ItemStack held = PetHeldItem.of(pet);
        return petLines(pet.getRarityComponent(), pet.getName(), pet.getLevel(), current, needed,
                rating.getString().isBlank() ? null : rating, pet.getTotalPercent(),
                held.isEmpty() || !dannypx.foe.config.Configs.hudConfig.petPanelItemLine.get() ? null : held.getHoverName(), xpStyle());
    }

    /// "♣ Max Luck" / "⚓ Max Scale" / "♣⚓ Perfect" when the pet's rolls here are as good as they get.
    private static Component maxTag(PetTagObject pet) {
        try {
            // max only when both its luck stats are (the climate one and the location one), and the same for scale
            boolean luck = maxed(pet.getClimatePercentMaxLuck(), pet.getLocationPercentMaxLuck());
            boolean scale = maxed(pet.getClimatePercentMaxScale(), pet.getLocationPercentMaxScale());
            return maxTag(luck, scale);
        } catch (RuntimeException ignored) {}
        return null;
    }

    /// Public for the screenshot harness: the tag for these max stats (null for none).
    public static Component maxTag(boolean luck, boolean scale) {
        // "♣ Max Luck" all in one colour: a green ♣ and green text (default), or the clover and text in the Luck
        // colour. The ♣ and the ⚓ are FOE+'s own, the same size as each other.
        int scaleColor = LuckScalePanelElement.SCALE_COLOR;
        int luckColor = luckColor();
        Component club = greenClover() ? dannypx.foe.helper.NameTagMarker.club(GREEN_LUCK)
                : dannypx.foe.helper.NameTagMarker.clover(luckColor);
        Component anchor = dannypx.foe.helper.NameTagMarker.anchor(scaleColor);
        if (luck && scale) return join(club, anchor, Component.literal(" Perfect").withColor(PERFECT_COLOR));
        if (luck) return join(club, Component.literal(" Max Luck").withColor(luckColor));
        if (scale) return join(anchor, Component.literal(" Max Scale").withColor(scaleColor));
        return null;
    }

    /// Just the ♣ / ⚓ (both when perfect) for a pet whose rolls here are as good as they get, or null: for the FOE-R
    /// style's pet box, which has no room for the words.
    static Component maxIcons(PetTagObject pet) {
        try {
            boolean luck = maxed(pet.getClimatePercentMaxLuck(), pet.getLocationPercentMaxLuck());
            boolean scale = maxed(pet.getClimatePercentMaxScale(), pet.getLocationPercentMaxScale());
            if (!luck && !scale) return null;
            Component club = greenClover() ? dannypx.foe.helper.NameTagMarker.club(GREEN_LUCK)
                    : dannypx.foe.helper.NameTagMarker.clover(luckColor());
            Component anchor = dannypx.foe.helper.NameTagMarker.anchor(LuckScalePanelElement.SCALE_COLOR);
            return luck && scale ? join(club, anchor) : luck ? club : anchor;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /// Purple, as for maxed rolls on the rod part icons and the pet catch popup.
    static final int PERFECT_COLOR = 0xB04CFF;

    /// The green of the default luck ♣ (the game's own green).
    static final int GREEN_LUCK = 0x55FF55;

    /// Luck's colour on the pet: green by default, else the Luck colour.
    static int luckColor() {
        return greenClover() ? GREEN_LUCK : LuckScalePanelElement.LUCK_COLOR;
    }

    static boolean greenClover() {
        try {
            return Configs.hudConfig.petPanelGreenLuck.get();
        } catch (Throwable e) {
            return true;
        }
    }

    /// Package-private for tests: both of a pet's rolls of one stat (climate, location) at their most.
    /// {Max Luck, Max Scale}: whether the pet's luck rolls (climate and location) are both at their best here, and
    /// the same for scale ({false, false} when they can't be read). For the pet prices.
    public static boolean[] maxedLuckScale(PetTagObject pet) {
        try {
            return new boolean[]{maxed(pet.getClimatePercentMaxLuck(), pet.getLocationPercentMaxLuck()),
                    maxed(pet.getClimatePercentMaxScale(), pet.getLocationPercentMaxScale())};
        } catch (RuntimeException e) {
            return new boolean[]{false, false};
        }
    }

    /// The pet's rarity ("legendary"), from its rarity or else its badge. For the pet prices.
    public static String rarityOf(PetTagObject pet) {
        return petRarity(pet);
    }

    static boolean maxed(float climate, float location) {
        return climate >= 0.999f && location >= 0.999f;
    }

    /// The bar's room in the level line: nothing to read, the bar is drawn there (see beforeLine), sliding
    /// smoothly as XP comes in, and stretching to the panel's edge when nothing comes after it.
    static final String BAR = " ".repeat(16);

    static dannypx.foe.type.PetXpStyle xpStyle() {
        try {
            return Configs.hudConfig.petPanelXp.get();
        } catch (Throwable e) {
            return dannypx.foe.type.PetXpStyle.BAR;
        }
    }

    /// The pet panel (the pet and its item to the side, "Max Luck" above them):
    /// <pre>
    /// [EPIC] Lynx Pet
    /// ʟᴠʟ [17] ━━━━━━━━━━━━── (1.24M/2.00M)     (the numbers and the bar can be hidden)
    /// RATING EXCELLENT (82%)
    /// ITEM Cryptid Amulet
    /// </pre>
    static List<Component> petLines(Component rarity, Component name, int level, float current, float needed,
                                    Component rating, float ratingPercent, Component held, dannypx.foe.type.PetXpStyle xp) {
        List<Component> lines = new ArrayList<>();
        boolean max = level >= 100 || needed <= 0;
        float fraction = max ? 1f : needed <= 0 ? 0f : Math.max(0f, Math.min(1f, current / needed));
        boolean game = GameXpBar.gameStyle();
        Component levelTag = game
                ? join(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY),
                        Component.literal(String.valueOf(level)).withStyle(ChatFormatting.GREEN),
                        Component.literal("]").withStyle(ChatFormatting.DARK_GRAY))
                : Component.literal(String.valueOf(level)).withStyle(progressColor(level));

        Component head = Component.empty().append(rarity).append(Component.literal(" ")).append(name);
        if (xp == dannypx.foe.type.PetXpStyle.HIDDEN) head = join(head, Component.literal(" "), levelTag);
        lines.add(head);

        if (xp != dannypx.foe.type.PetXpStyle.HIDDEN) {
            // the room for the bar comes straight after the level (its own spaces are the gaps)
            Component line = join(Component.literal("ʟᴠʟ ").withStyle(game ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY),
                    levelTag, Component.literal(BAR));
            if (xp == dannypx.foe.type.PetXpStyle.BAR_AND_NUMBERS) {
                if (game) {
                    line = join(line, Component.literal("(").withStyle(ChatFormatting.DARK_GRAY),
                            Component.literal(GameXpBar.num(current)).withStyle(ChatFormatting.AQUA),
                            Component.literal("/").withStyle(ChatFormatting.DARK_GRAY),
                            Component.literal(max ? "MAX" : GameXpBar.num(needed)).withStyle(ChatFormatting.BLUE),
                            Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
                } else {
                    line = join(line, max ? Component.literal("MAX").withStyle(ChatFormatting.GOLD)
                            : join(Component.literal(compact(current)).withStyle(ChatFormatting.AQUA),
                            Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY),
                            Component.literal(compact(needed)).withStyle(ChatFormatting.BLUE)));
                }
            }
            lines.add(line);
        }
        if (rating != null) {
            int ratingColor = ratingColor(rating);
            // the percentage in the rating's own colour, its brackets grey
            lines.add(join(Component.literal("ʀᴀᴛɪɴɢ ").withStyle(ChatFormatting.GRAY), rating,
                    Component.literal(" (").withStyle(ChatFormatting.GRAY),
                    Component.literal(String.format("%.0f%%", ratingPercent * 100f)).withColor(ratingColor < 0 ? 0xFFFFFF : ratingColor),
                    Component.literal(")").withStyle(ChatFormatting.GRAY)));
        }
        lines.add(join(Component.literal("ɪᴛᴇᴍ ").withStyle(ChatFormatting.GRAY),
                held == null ? Component.literal("None").withStyle(ChatFormatting.DARK_GRAY) : held.copy()));
        lastFraction = fraction;
        return lines;
    }

    /// Where the bar is heading, and where it's drawn now (it slides there, and starts again after a level up).
    private static float lastFraction = 0f, shownFraction = 0f;
    private static long lastDrawNs = 0L;

    /// Package-private for tests: one step of the bar's slide from `shown` towards `target` over `dtSeconds`.
    static float slide(float shown, float target, float dtSeconds) {
        if (target < shown - 0.02f) return 0f;          // levelled up: fill again from empty
        float k = 1f - (float) Math.exp(-dtSeconds * 4f);
        float next = shown + (target - shown) * k;
        return Math.abs(target - next) < 0.001f ? target : next;
    }

    /// A progress bar `w` wide, 5 tall, filled to `fraction` with a gradient and a soft shine running along it.
    static void drawBar(GuiGraphics g, int x, int top, int w, float fraction, int light, int dark) {
        drawBar(g, x, top, w, fraction, light, dark, true);
    }

    /// The same, with or without the shine running along it.
    static void drawBar(GuiGraphics g, int x, int top, int w, float fraction, int light, int dark, boolean shine) {
        drawBar(g, x, top, w, fraction, light, dark, shine, 5);
    }

    /// The same, `h` pixels tall.
    static void drawBar(GuiGraphics g, int x, int top, int w, float fraction, int light, int dark, boolean shine, int h) {
        g.fill(x - 1, top - 1, x + w + 1, top + h + 1, 0xFF0B0B0B);
        g.fill(x, top, x + w, top + h, 0xFF2A2A2A);
        int fill = Math.round(w * Math.max(0f, Math.min(1f, fraction)));
        if (fill <= 0) return;
        g.fillGradient(x, top, x + fill, top + h, light, dark);
        if (!shine) {
            g.fill(x, top, x + fill, top + 1, 0x50FFFFFF);
            return;
        }
        // a soft glint now and then: one slow pass every 7 seconds, then still
        long cycle = System.currentTimeMillis() % 7000L;
        if (cycle < 1600L) {
            float t = cycle / 1600f;
            t = t * t * (3 - 2 * t);                  // eases in and out
            int sx = x + Math.round(t * (fill + 10)) - 5;
            for (int i = 0; i < 4; i++) {
                int px = sx + i;
                if (px >= x && px < x + fill) g.fill(px, top, px + 1, top + h, (0x10 + (i == 1 || i == 2 ? 0x18 : 0)) << 24 | 0xFFFFFF);
            }
        }
        g.fill(x, top, x + fill, top + 1, 0x50FFFFFF);
    }

    @Override
    protected void beforeLine(GuiGraphics g, Font font, Component line, int x, int y) {
        // FOE+ bars: the bar goes in its room in the level line
        String text = line.getString();
        int at = text.indexOf(BAR);
        if (at < 0) return;
        long now = System.nanoTime();
        float dt = lastDrawNs == 0 ? 0.016f : Math.min(0.1f, (now - lastDrawNs) / 1e9f);
        lastDrawNs = now;
        shownFraction = slide(shownFraction, lastFraction, dt);
        // a slim bar, 2 pixels, in the middle of its line
        // inset by a space's width each side, so it never touches the level or the numbers; with nothing after
        // it, it reaches the panel's edge
        int gap = font.width(" ");
        int start = x + font.width(text.substring(0, at)) + gap;
        int width = font.width(BAR) - gap * 2;
        if (text.length() == at + BAR.length()) width = Math.max(width, lineRightEdge - start);
        if (GameXpBar.gameStyle()) drawGameBar(g, start, y, width, shownFraction);
        else drawBar(g, start, y + 3, width, shownFraction, 0xFF7CF07C, 0xFF2EA043, true, 2);
    }

    /// The game's own XP bar, as in the pet's tooltip (a struck-through line, teal over dark grey, with the
    /// text's shadow), but filled to the exact pixel so it slides instead of jumping a space at a time.
    static void drawGameBar(GuiGraphics g, int x, int y, int w, float fraction) {
        int fill = Math.round(w * Math.max(0f, Math.min(1f, fraction)));
        int teal = 0xFF000000 | GameXpBar.FILL, grey = 0xFF555555;
        g.pose().pushMatrix();
        g.pose().translate(0f, 0.5f);               // where the font draws a strikethrough
        g.fill(x + 1, y + 4, x + w + 1, y + 5, shadow(grey));
        if (fill > 0) g.fill(x + 1, y + 4, x + fill + 1, y + 5, shadow(teal));
        g.fill(x, y + 3, x + w, y + 4, grey);
        if (fill > 0) g.fill(x, y + 3, x + fill, y + 4, teal);
        g.pose().popMatrix();
    }

    /// A colour's text shadow (a quarter as bright), as the font draws it.
    private static int shadow(int argb) {
        return (argb & 0xFF000000) | ((argb >> 2) & 0x3F3F3F);
    }

    /// 950 → "950", 23500 → "23.5k", 201000 → "201k", 1500000 → "1.5m".
    static String compact(float value) {
        double v = Math.round(value);
        String suffix = "";
        if (Math.abs(v) >= 1_000_000_000) { v /= 1_000_000_000; suffix = "b"; }
        else if (Math.abs(v) >= 1_000_000) { v /= 1_000_000; suffix = "m"; }
        else if (Math.abs(v) >= 1_000) { v /= 1_000; suffix = "k"; }
        if (suffix.isEmpty()) return String.valueOf((long) v);
        String text = v >= 100 ? String.valueOf((long) Math.floor(v)) : String.format(java.util.Locale.ROOT, "%.1f", Math.floor(v * 10) / 10);
        if (text.endsWith(".0")) text = text.substring(0, text.length() - 2);
        return text + suffix;
    }

    @Override
    protected List<Component> previewLines() {
        return petLines(Component.literal("ᴇᴘɪᴄ").withColor(dannypx.foe.helper.FoeLocations.rarityColor("epic")).withStyle(ChatFormatting.BOLD), Component.literal("Lynx Pet").withStyle(ChatFormatting.WHITE),
                17, 1_240_000f, 2_000_000f, Component.literal("EXCELLENT").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), 0.82f,
                Component.literal("Cryptid Amulet").withColor(0xD6A2EC), xpStyle());
    }
}
