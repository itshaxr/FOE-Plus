package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.TitleHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.item.FishTagObject;
import dannypx.foe.item.PetTagObject;
import dannypx.foe.screens.element.Element;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * FOE's catch popup: transparent, centred text with the server's fish icon, the fish name, its
 * size, then weight and length underneath. Ported from FOECollab's TitleHud and
 * FishCatchHandler.sendToTitleHud (AGPL-3.0).
 * <p>
 * The big lines (icon, name, size) are drawn at twice the size of the small ones (weight, length).
 * The element's position is the top-centre of the whole block, so it can be dragged in the
 * Move HUD Elements screen.
 */
public class CatchPopupElement extends Element {
    private static final int LINE_SPACING = 4;
    private static final float BIG = 2.0f;
    private static final float SMALL = 1.0f;

    private static final float MID = 1.5f;

    /// One line of the popup and the size it's drawn at.
    public record Line(Component text, float size) {}

    private static long shownAt = 0L;
    private static List<Line> lines = new ArrayList<>();
    /// Whether what's showing is a pet's popup (drawn where the Pet Popup is placed) or a fish's.
    private static boolean shownIsPet = false;

    /// This box is the pet popup's (its own place and size), not the fish's.
    protected final boolean pet;

    public CatchPopupElement() {
        this(false);
    }

    public CatchPopupElement(boolean isCopy) {
        this(isCopy, false);
    }

    protected CatchPopupElement(boolean isCopy, boolean pet) {
        super(160, 90,
                dannypx.foe.helper.HudFine.get(pet ? Configs.hudConfig.petPopupXPosition : Configs.hudConfig.catchPopupXPosition),
                dannypx.foe.helper.HudFine.get(pet ? Configs.hudConfig.petPopupYPosition : Configs.hudConfig.catchPopupYPosition),
                Alignment.TOP,
                Component.literal(pet ? "Pet Popup" : "Catch Popup"),
                isCopy);
        this.pet = pet;
        Font font = Minecraft.getInstance().font;
        if (font != null) this.updateSize(font, preview());
    }

    private float popupScale() {
        return dannypx.foe.utils.HudScale.crisp(pet ? Configs.hudConfig.petPopupScale.get() : Configs.hudConfig.catchPopupScale.get());
    }

    /// What the box shows in the HUD mover.
    private List<Line> preview() {
        return previewLines(pet);
    }

    /// What each popup looks like with its lines as they're set now (the HUD mover, the HUD Editor's Popups).
    public static List<Line> previewLines(boolean pet) {
        if (pet) return petPreview();
        List<Line> out = fishLines(Component.empty(), Component.literal("Fish Name").withStyle(ChatFormatting.GOLD),
                Component.literal("ᴀᴅᴜʟᴛ").withColor(0x1C7DA0), 1.23f, 12.34f,
                Configs.hudConfig.catchPopupShowSizeBonus.get() ? 3f : null, Configs.hudConfig.catchPopupShowInventoryFull.get());
        return out.isEmpty() ? List.of(new Line(Component.literal("(every line off)").withStyle(ChatFormatting.GRAY), MID)) : out;
    }

    private static List<Line> petPreview() {
        var cfg = Configs.hudConfig;
        List<Line> out = new ArrayList<>();
        if (cfg.petPopupName.get()) out.add(new Line(Component.literal("Pet Name").withStyle(ChatFormatting.GOLD), BIG));
        MutableComponent level = Component.empty();
        if (cfg.petPopupRarity.get()) level.append(Component.literal("ʀᴀʀɪᴛʏ").withColor(0x55FFFF));
        if (cfg.petPopupLevel.get()) level.append(Component.literal(cfg.petPopupRarity.get() ? " Lv. " : "Lv. ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("0").withStyle(ChatFormatting.GREEN));
        if (!level.getString().isEmpty()) out.add(new Line(level, BIG));
        if (cfg.petPopupRating.get()) out.add(new Line(Component.literal("Rating 97%").withStyle(ChatFormatting.LIGHT_PURPLE), BIG));
        if (cfg.petPopupStats.get()) out.add(new Line(Component.literal("12%  8%    15%  6%").withStyle(ChatFormatting.WHITE), BIG));
        if (cfg.petPopupStatNames.get()) out.add(new Line(Component.literal("Luck Scale    Luck Scale").withStyle(ChatFormatting.GRAY), MID));
        if (cfg.petPopupShowInventoryFull.get()) out.add(new Line(Component.literal("Inventory Full!").withStyle(ChatFormatting.RED), BIG));
        return out.isEmpty() ? List.of(new Line(Component.literal("(every line off)").withStyle(ChatFormatting.GRAY), MID)) : out;
    }

    /// Called when a fish is caught.
    public static void show(FishTagObject fish) {
        var cfg = Configs.hudConfig;
        Component icon = TitleHandler.instance().getTitle();
        Component name = TitleHandler.instance().getSubTitle();
        if (name.getString().isBlank()) {
            name = TextHelper.concat(fish.getRarityComponent(), Component.literal(" "), fish.getName());
        }
        Float bonus = null;
        if (cfg.catchPopupShowSizeBonus.get() && (!cfg.catchPopupSizeBonusContestOnly.get() || contestHere())) {
            try {
                bonus = dannypx.foe.helper.SizeBonus.current();
            } catch (RuntimeException e) {
                bonus = null;
            }
        }
        lines = fishLines(icon, name, fish.getFishSizeComponent(), fish.getWeight(), fish.getLength(), bonus, emptySlots() == 0);
        shownIsPet = false;
        shownAt = System.currentTimeMillis();
    }

    /// The fish popup's lines, each only when it's switched on. Package-private for the preview.
    static List<Line> fishLines(Component icon, Component name, Component size, float weight, float length, Float sizeBonus, boolean full) {
        var cfg = Configs.hudConfig;
        List<Component> big = new ArrayList<>();
        if (cfg.catchPopupShowIcon.get() && icon != null && !icon.getString().isBlank()) {
            big.add(icon.copy().withStyle(ChatFormatting.WHITE));
            big.add(Component.empty());
        }
        if (cfg.catchPopupShowName.get()) big.add(name);
        if (cfg.catchPopupShowSize.get()) big.add(size);
        if (full && cfg.catchPopupShowInventoryFull.get()) big.add(Component.literal("Inventory Full!").withStyle(ChatFormatting.RED));

        List<Component> small = new ArrayList<>();
        var units = cfg.catchPopupUnits.get();
        if (cfg.catchPopupShowWeight.get()) {
            small.add(Component.literal("ᴡᴇɪɢʜᴛ").withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE));
            small.add(measurement(weight, "ʟʙ", weight * 0.453592f, "ᴋɢ", units));
        }
        if (cfg.catchPopupShowLength.get()) {
            small.add(Component.literal("ʟᴇɴɢᴛʜ").withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE));
            small.add(measurement(length, "ɪɴ", length * 2.54f, "ᴄᴍ", units));
        }
        if (sizeBonus != null) {
            small.add(Component.literal("ꜱɪᴢᴇ ʙᴏɴᴜꜱ").withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE));
            float b = Math.round(sizeBonus * 10f) / 10f;
            String txt = (b >= 0 ? "+" : "") + (b == Math.floor(b) ? String.valueOf((int) b) : String.valueOf(b)) + "%";
            small.add(Component.literal(txt).withColor(b > 0 ? 0x55FF55 : b < 0 ? 0xFF5555 : 0xAAAAAA));
        }
        return sized(big, small);
    }

    /// A contest on now where you're fishing (or anywhere, when it isn't known where).
    private static boolean contestHere() {
        var contest = dannypx.foe.handler.logic.ContestTrackerHandler.instance();
        if (!contest.isContest) return false;
        if (contest.location == null || contest.location.isBlank()) return true;
        var at = dannypx.foe.helper.FoeLocations.fromName(contest.location);
        var here = dannypx.foe.helper.FoeLocations.fromName(dannypx.foe.handler.fetch.BossEventHandler.instance().getLocation().getString());
        return at == null || here == null || at == here;
    }

    /// Called when a pet is fished up: its name, rarity and level, rating and overall percent,
    /// then its four stats (climate luck and scale, location luck and scale) with what each is,
    /// and the item it holds.
    public static void showPet(PetTagObject pet) {
        lines = petLines(pet);
        shownIsPet = true;
        shownAt = System.currentTimeMillis();
    }

    static List<Line> petLines(PetTagObject pet) {
        // the badge from the pet's own tooltip, as the Pet panel shows it; else the server's badge for its rarity
        Component rarity = pet.getRarityComponent();
        if (rarity.getString().isBlank()) rarity = dannypx.foe.handler.logic.NotifierHandler.rarityBadge(pet);
        return petLines(pet, rarity);
    }

    /// Package-private for tests and previews.
    static List<Line> petLines(PetTagObject pet, Component rarity) {
        var cfg = Configs.hudConfig;
        List<Line> out = new ArrayList<>();
        if (cfg.petPopupName.get()) out.add(new Line(pet.getName().copy(), BIG));

        // its rarity and (if wanted) level: "Lv. 0" for every pet just fished up, so off unless you turn it on
        MutableComponent level = Component.empty();
        if (cfg.petPopupRarity.get()) level.append(rarity);
        if (cfg.petPopupLevel.get()) {
            level.append(Component.literal(cfg.petPopupRarity.get() ? " Lv. " : "Lv. ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(String.valueOf(pet.getLevel())).withStyle(ChatFormatting.GREEN));
        }
        if (!level.getString().isEmpty()) out.add(new Line(level, BIG));

        if (cfg.petPopupRating.get()) {
            Component rating = pet.getRatingComponent();
            int ratingColor = firstColor(rating, 0xFFFFFF);
            MutableComponent ratingLine = Component.empty();
            if (!rating.getString().isBlank()) ratingLine.append(rating).append(Component.literal(" "));
            ratingLine.append(Component.literal(percent(pet.getTotalPercent())).withColor(ratingColor));
            out.add(new Line(ratingLine, BIG));
        }

        // the four stats, then under them which is which: climate luck, climate scale, location luck, location scale
        int luck = PetPanelElement.luckColor();
        int scale = LuckScalePanelElement.SCALE_COLOR;
        float[] stats = {pet.getClimatePercentMaxLuck(), pet.getClimatePercentMaxScale(),
                pet.getLocationPercentMaxLuck(), pet.getLocationPercentMaxScale()};
        MutableComponent row = Component.empty();
        for (int i = 0; i < stats.length; i++) {
            if (i > 0) row.append(Component.literal(i == 2 ? "    " : "  "));
            row.append(Component.literal(percent(stats[i])).withColor(stats[i] >= 0.9995f ? MAX_COLOR : (i % 2 == 0 ? luck : scale)));
        }
        if (cfg.petPopupStats.get()) out.add(new Line(row, BIG));

        dannypx.foe.helper.FoeLocations.Climate climate = dannypx.foe.helper.FoeLocations.climateFromId(pet.getString("climate"));
        dannypx.foe.helper.FoeLocations.Location location = dannypx.foe.handler.logic.PetLocationHandler.locationOf(pet);
        if (climate == null && location != null) climate = location.climate;
        MutableComponent key = Component.empty();
        if (climate != null) key.append(climate.tag()).append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
        key.append(Component.literal("Luck").withColor(luck)).append(Component.literal(" ")).append(Component.literal("Scale").withColor(scale));
        key.append(Component.literal("    "));
        String place = location == null ? null : dannypx.foe.helper.FoeLocations.displayName(location);
        if (place != null) key.append(dannypx.foe.helper.FoeLocations.styledName(place)).append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
        key.append(Component.literal("Luck").withColor(luck)).append(Component.literal(" ")).append(Component.literal("Scale").withColor(scale));
        if (cfg.petPopupStatNames.get()) out.add(new Line(key, MID));

        if (emptySlots() == 0 && cfg.petPopupShowInventoryFull.get()) out.add(new Line(Component.literal("Inventory Full!").withStyle(ChatFormatting.RED), BIG));
        return out;
    }

    /// Purple, as on the rod part icons: a stat rolled at its max.
    static final int MAX_COLOR = 0xB04CFF;

    static String percent(float fraction) {
        float p = Math.round(fraction * 1000f) / 10f;
        return (p == Math.floor(p) ? String.valueOf((int) p) : String.valueOf(p)) + "%";
    }

    private static int firstColor(Component c, int fallback) {
        return c.visit((style, text) -> text.isBlank() || style.getColor() == null
                ? java.util.Optional.<Integer>empty() : java.util.Optional.of(style.getColor().getValue()), net.minecraft.network.chat.Style.EMPTY).orElse(fallback);
    }

    private static List<Line> sized(List<Component> big, List<Component> small) {
        List<Line> out = new ArrayList<>();
        for (Component c : big) out.add(new Line(c, BIG));
        for (Component c : small) out.add(new Line(c, SMALL));
        return out;
    }

    private static Component measurement(float value, String unit, float metric, String metricUnit, dannypx.foe.type.PopupUnits units) {
        if (units == dannypx.foe.type.PopupUnits.IMPERIAL) {
            return Component.literal(TextHelper.floatToString(value, 2)).withStyle(ChatFormatting.WHITE).append(Component.literal(unit).withColor(0xAAAAAA));
        }
        if (units == dannypx.foe.type.PopupUnits.METRIC) {
            return Component.literal(TextHelper.floatToString(metric, 2)).withStyle(ChatFormatting.WHITE).append(Component.literal(metricUnit).withColor(0xAAAAAA));
        }
        return Component.literal(TextHelper.floatToString(value, 2)).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(unit).withColor(0xAAAAAA))
                .append(Component.literal(" (").withColor(0x555555))
                .append(Component.literal(TextHelper.floatToString(metric, 2)).withColor(0xFFFFFF))
                .append(Component.literal(metricUnit).withColor(0xAAAAAA))
                .append(Component.literal(")").withColor(0x555555));
    }

    private static int emptySlots() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        int empty = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getNonEquipmentItems().get(i);
            if (stack.isEmpty()) empty++;
        }
        return empty;
    }

    private static int lineHeight(Font font, float size) {
        return Math.round((font.lineHeight + LINE_SPACING / size) * size);
    }

    private void updateSize(Font font, List<Line> lines) {
        float scale = popupScale();
        int widest = 0;
        int total = 0;
        for (Line line : lines) {
            widest = Math.max(widest, Math.round(font.width(line.text()) * line.size()));
            total += lineHeight(font, line.size());
        }
        this.width = Math.max(20, Math.round(widest * scale));
        this.height = Math.max(20, Math.round(total * scale));
    }

    @Override
    public void render(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        long elapsed = System.currentTimeMillis() - shownAt;
        // each box draws its own kind: the fish's popup where the Catch Popup is, a pet's where the Pet Popup is
        long time = (pet ? Configs.hudConfig.petPopupTime.get() : Configs.hudConfig.catchPopupTime.get()) * 1000L;
        boolean showing = shownAt > 0 && elapsed < time && shownIsPet == pet;

        List<Line> shown = isCopy ? preview() : showing ? lines : List.of();
        if (shown.isEmpty()) return;

        if (!isCopy) {
            xPos = dannypx.foe.helper.HudFine.get(pet ? Configs.hudConfig.petPopupXPosition : Configs.hudConfig.catchPopupXPosition);
            yPos = dannypx.foe.helper.HudFine.get(pet ? Configs.hudConfig.petPopupYPosition : Configs.hudConfig.catchPopupYPosition);
            // Hide the server's own catch title while ours is up, like FOE.
            if (showing && elapsed < 3000L) mc.gui.clearTitles();
        }
        this.updateSize(font, shown);

        float scale = popupScale();
        float centerX = mc.getWindow().getGuiScaledWidth() * xPos / scale;
        float y = mc.getWindow().getGuiScaledHeight() * yPos / scale;

        int alpha = isCopy ? 255 : fadeAlpha(elapsed, time, pet ? Configs.hudConfig.petPopupFade.get() : Configs.hudConfig.catchPopupFade.get());
        if (alpha < 4) return;
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(scale, scale);
        int border = GameXpBar.colour(pet ? Configs.hudConfig.petPopupBorderColor::get : Configs.hudConfig.catchPopupBorderColor::get);
        if (border >= 0) {
            // a box behind it, its border in the colour picked
            float widest = 0, total = 0;
            for (Line line : shown) {
                widest = Math.max(widest, font.width(line.text()) * line.size());
                total += lineHeight(font, line.size());
            }
            int bx0 = Math.round(centerX - widest / 2f) - 6, bx1 = Math.round(centerX + widest / 2f) + 6;
            int by0 = Math.round(y) - 5, by1 = Math.round(y + total) + 2;
            guiGraphics.fill(bx0, by0, bx1, by1, (0xB0 * alpha / 255) << 24 | 0x101418);
            guiGraphics.renderOutline(bx0, by0, bx1 - bx0, by1 - by0, alpha << 24 | border);
        }
        for (Line line : shown) {
            drawCentered(guiGraphics, font, line.text(), centerX, y, line.size(), alpha);
            y += lineHeight(font, line.size());
        }
        guiGraphics.pose().popMatrix();
    }

    /// Draws a popup's preview centred at (centerX, top), `scale` times its size, with its border box if it has one;
    /// returns how tall it was drawn.
    public static int drawPreview(GuiGraphics g, boolean pet, float centerX, float top, float scale) {
        Font font = Minecraft.getInstance().font;
        List<Line> shown = previewLines(pet);
        float widest = 0, total = 0;
        for (Line line : shown) {
            widest = Math.max(widest, font.width(line.text()) * line.size());
            total += lineHeight(font, line.size());
        }
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        float cx = centerX / scale, y = top / scale;
        int border = GameXpBar.colour(pet ? Configs.hudConfig.petPopupBorderColor::get : Configs.hudConfig.catchPopupBorderColor::get);
        if (border >= 0) {
            int bx0 = Math.round(cx - widest / 2f) - 6, bx1 = Math.round(cx + widest / 2f) + 6;
            int by0 = Math.round(y) - 5, by1 = Math.round(y + total) + 2;
            g.fill(bx0, by0, bx1, by1, 0xB0101418);
            g.renderOutline(bx0, by0, bx1 - bx0, by1 - by0, 0xFF000000 | border);
        }
        for (Line line : shown) {
            drawCentered(g, font, line.text(), cx, y, line.size(), 255);
            y += lineHeight(font, line.size());
        }
        g.pose().popMatrix();
        return Math.round(total * scale);
    }

    /// How solid the popup is `elapsed` ms into its `total`: fully, or with Fade away on, fading in over its first
    /// fifth of a second and out over its last second (a third of it, if it's shorter). Package-private for tests.
    static int fadeAlpha(long elapsed, long total, boolean fade) {
        if (!fade || total <= 0) return 255;
        long out = Math.min(1000L, total / 3);
        float a = 1f;
        if (elapsed < 200L) a = elapsed / 200f;
        long left = total - elapsed;
        if (left < out) a = Math.min(a, left / (float) out);
        return Math.max(0, Math.min(255, Math.round(a * 255f)));
    }

    /// Shows the popup for a pet whenever you like (/foe showpet), to show it off.
    public static void showOff(PetTagObject pet) {
        showPet(pet);
    }

    private static void drawCentered(GuiGraphics guiGraphics, Font font, Component line, float centerX, float y, float size, int alpha) {
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(centerX, y);
        guiGraphics.pose().scale(size, size);
        guiGraphics.drawString(font, line, -font.width(line) / 2, 0, alpha << 24 | 0xFFFFFF, true);
        guiGraphics.pose().popMatrix();
    }
}
