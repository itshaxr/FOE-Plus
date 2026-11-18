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
        // The pet's frame takes its rarity colour: from its rarity, else its rarity badge, else grey.
        int rarityColor = dannypx.foe.helper.FoeLocations.rarityColor(petRarity(pet));
        int border = rarityColor < 0 ? UNKNOWN_BORDER : 0xFF000000 | rarityColor;
        return List.of(new PanelIcon(pet.getItemStack(), border),
                new PanelIcon(PetHeldItem.of(pet), HELD_ITEM_BORDER));
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
                held.isEmpty() ? null : held.getHoverName(), maxTag(pet));
    }

    /// "♣ Max Luck" / "⚓ Max Scale" / "♣ Max" when the pet's rolls here are as good as they get.
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
        // "♣ Max Luck" all in one colour: the green clover's green (default), or the Luck colour
        int scaleColor = LuckScalePanelElement.SCALE_COLOR;
        int luckColor = greenClover() ? LuckScalePanelElement.GREEN_LUCK_COLOR : LuckScalePanelElement.LUCK_COLOR;
        // the green clover icon (default), or a ♣ in the Luck colour
        Component clover = greenClover() ? join(dannypx.foe.helper.NameTagMarker.clover(), Component.literal(" "))
                : join(dannypx.foe.helper.NameTagMarker.clover(luckColor), Component.literal(" "));
        if (luck && scale) return join(clover, Component.literal("Max").withColor(luckColor));
        if (luck) return join(clover, Component.literal("Max Luck").withColor(luckColor));
        if (scale) return join(Component.literal("⚓ ").withColor(scaleColor), Component.literal("Max Scale").withColor(scaleColor));
        return null;
    }

    static boolean greenClover() {
        try {
            return Configs.hudConfig.petPanelGreenCloverIcon.get();
        } catch (Throwable e) {
            return true;
        }
    }

    /// Package-private for tests: both of a pet's rolls of one stat (climate, location) at their most.
    static boolean maxed(float climate, float location) {
        return climate >= 0.999f && location >= 0.999f;
    }

    /// The bar's line: nothing to read, the bar is drawn behind it (see beforeLine).
    static final String BAR = " ".repeat(34);

    /// The pet panel:
    /// <pre>
    /// [EPIC] Lynx Pet              ♣ Max Luck
    /// LVL 17                     1.2M / 4.5M
    /// ███████████░░░░░░░░░░░░░░░░░░  (fills up)
    /// RATING EXCELLENT (82%)
    /// ITEM Cryptid Amulet
    /// </pre>
    static List<Component> petLines(Component rarity, Component name, int level, float current, float needed,
                                    Component rating, float ratingPercent, Component held, Component maxTag) {
        List<Component> lines = new ArrayList<>();
        Component head = Component.empty().append(rarity).append(Component.literal(" ")).append(name);
        lines.add(maxTag == null ? head : join(head, TAB, maxTag));
        boolean max = level >= 100 || needed <= 0;
        float fraction = max ? 1f : needed <= 0 ? 0f : Math.max(0f, Math.min(1f, current / needed));
        if (GameXpBar.gameStyle()) {
            // as the pet's own tooltip shows it: "ᴘᴇᴛ ʟᴇᴠᴇʟ: [100]", then the bar and "(99.98M/MAX)"
            lines.add(join(Component.literal("ᴘᴇᴛ ʟᴇᴠᴇʟ: ").withStyle(ChatFormatting.DARK_GRAY),
                    Component.literal("[").withStyle(ChatFormatting.DARK_GRAY),
                    Component.literal(String.valueOf(level)).withStyle(ChatFormatting.GREEN),
                    Component.literal("]").withStyle(ChatFormatting.DARK_GRAY)));
            lines.add(GameXpBar.line(fraction, GameXpBar.CHARS, GameXpBar.num(current), max ? "MAX" : GameXpBar.num(needed)));
        } else {
        lines.add(join(Component.literal("ʟᴠʟ ").withStyle(ChatFormatting.GRAY),
                Component.literal(String.valueOf(level)).withStyle(progressColor(level)), TAB,
                max ? Component.literal("MAX").withStyle(ChatFormatting.GOLD)
                        : join(Component.literal(compact(current)).withStyle(ChatFormatting.AQUA),
                        Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY),
                        Component.literal(compact(needed)).withStyle(ChatFormatting.BLUE))));
        lines.add(Component.literal(BAR));
        }
        if (rating != null) {
            lines.add(join(Component.literal("ʀᴀᴛɪɴɢ ").withStyle(ChatFormatting.GRAY), rating,
                    Component.literal(String.format(" (%.0f%%)", ratingPercent * 100f)).withStyle(ChatFormatting.WHITE)));
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
        if (!line.getString().equals(BAR)) return;
        long now = System.nanoTime();
        float dt = lastDrawNs == 0 ? 0.016f : Math.min(0.1f, (now - lastDrawNs) / 1e9f);
        lastDrawNs = now;
        shownFraction = slide(shownFraction, lastFraction, dt);
        // a slim bar, 2 pixels, in the middle of its line
        drawBar(g, x, y + 3, font.width(line), shownFraction, 0xFF7CF07C, 0xFF2EA043, true, 2);
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
                Component.literal("Cryptid Amulet").withColor(0xD6A2EC), join(Component.literal("♣ ").withColor(greenClover() ? 0x55FF55 : LuckScalePanelElement.LUCK_COLOR),
                        Component.literal("Max ").withStyle(ChatFormatting.WHITE), Component.literal("Luck").withColor(LuckScalePanelElement.LUCK_COLOR)));
    }
}
