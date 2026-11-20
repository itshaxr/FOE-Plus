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
    record Line(Component text, float size) {}

    private static long shownAt = 0L;
    private static List<Line> lines = new ArrayList<>();

    public CatchPopupElement() {
        this(false);
    }

    public CatchPopupElement(boolean isCopy) {
        super(160, 90,
                Configs.hudConfig.catchPopupXPosition.get() / 100f,
                Configs.hudConfig.catchPopupYPosition.get() / 100f,
                Alignment.TOP,
                Component.literal("Catch Popup"),
                isCopy);
        Font font = Minecraft.getInstance().font;
        if (font != null) this.updateSize(font, sized(previewBig(), previewSmall()));
    }

    /// Called when a fish is caught.
    public static void show(FishTagObject fish) {
        List<Component> big = new ArrayList<>();
        Component icon = TitleHandler.instance().getTitle();
        if (!icon.getString().isBlank()) {
            big.add(icon.copy().withStyle(ChatFormatting.WHITE));
            big.add(Component.empty());
        }

        Component name = TitleHandler.instance().getSubTitle();
        if (name.getString().isBlank()) {
            name = TextHelper.concat(fish.getRarityComponent(), Component.literal(" "), fish.getName());
        }
        big.add(name);
        big.add(fish.getFishSizeComponent());
        if (emptySlots() == 0) big.add(Component.literal("Inventory Full!").withStyle(ChatFormatting.RED));

        List<Component> small = new ArrayList<>();
        if (Configs.hudConfig.catchPopupShowWeight.get()) {
            small.add(Component.literal("ᴡᴇɪɢʜᴛ").withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE));
            small.add(measurement(fish.getWeight(), "ʟʙ", fish.getWeight() * 0.453592f, "ᴋɢ"));
        }
        if (Configs.hudConfig.catchPopupShowLength.get()) {
            small.add(Component.literal("ʟᴇɴɢᴛʜ").withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE));
            small.add(measurement(fish.getLength(), "ɪɴ", fish.getLength() * 2.54f, "ᴄᴍ"));
        }

        lines = sized(big, small);
        shownAt = System.currentTimeMillis();
    }

    /// Called when a pet is fished up: its name, rarity and level, rating and overall percent,
    /// then its four stats (climate luck and scale, location luck and scale) with what each is,
    /// and the item it holds.
    public static void showPet(PetTagObject pet) {
        lines = petLines(pet);
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
        List<Line> out = new ArrayList<>();
        out.add(new Line(pet.getName().copy(), BIG));

        MutableComponent level = Component.empty()
                .append(rarity)
                .append(Component.literal(" Lv. ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.valueOf(pet.getLevel())).withStyle(ChatFormatting.GREEN));
        out.add(new Line(level, BIG));

        Component rating = pet.getRatingComponent();
        int ratingColor = firstColor(rating, 0xFFFFFF);
        MutableComponent ratingLine = Component.empty();
        if (!rating.getString().isBlank()) ratingLine.append(rating).append(Component.literal(" "));
        ratingLine.append(Component.literal(percent(pet.getTotalPercent())).withColor(ratingColor));
        out.add(new Line(ratingLine, BIG));

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
        out.add(new Line(row, BIG));

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
        out.add(new Line(key, MID));

        ItemStack held = dannypx.foe.item.PetHeldItem.decodeUncached(pet);
        if (!held.isEmpty()) {
            out.add(new Line(Component.literal("Item: ").withStyle(ChatFormatting.GRAY).append(held.getHoverName().copy()), MID));
        }
        if (emptySlots() == 0) out.add(new Line(Component.literal("Inventory Full!").withStyle(ChatFormatting.RED), BIG));
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

    private static Component measurement(float value, String unit, float metric, String metricUnit) {
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

    private static List<Component> previewBig() {
        return List.of(
                Component.literal("Fish Name").withStyle(ChatFormatting.GOLD),
                Component.literal("ᴀᴅᴜʟᴛ").withColor(0x1C7DA0));
    }

    private static List<Component> previewSmall() {
        List<Component> small = new ArrayList<>();
        small.add(Component.literal("ᴡᴇɪɢʜᴛ").withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE));
        small.add(measurement(1.23f, "ʟʙ", 0.56f, "ᴋɢ"));
        small.add(Component.literal("ʟᴇɴɢᴛʜ").withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE));
        small.add(measurement(12.34f, "ɪɴ", 31.34f, "ᴄᴍ"));
        return small;
    }

    private static int lineHeight(Font font, float size) {
        return Math.round((font.lineHeight + LINE_SPACING / size) * size);
    }

    private void updateSize(Font font, List<Line> lines) {
        float scale = dannypx.foe.utils.HudScale.crisp(Configs.hudConfig.catchPopupScale.get());
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
        boolean showing = shownAt > 0 && elapsed < Configs.hudConfig.catchPopupTime.get() * 1000L;

        List<Line> shown = showing ? lines : (isCopy ? sized(previewBig(), previewSmall()) : List.of());
        if (shown.isEmpty()) return;

        if (!isCopy) {
            xPos = Configs.hudConfig.catchPopupXPosition.get() / 100f;
            yPos = Configs.hudConfig.catchPopupYPosition.get() / 100f;
            // Hide the server's own catch title while ours is up, like FOE.
            if (showing && elapsed < 3000L) mc.gui.clearTitles();
        }
        this.updateSize(font, shown);

        float scale = dannypx.foe.utils.HudScale.crisp(Configs.hudConfig.catchPopupScale.get());
        float centerX = mc.getWindow().getGuiScaledWidth() * xPos / scale;
        float y = mc.getWindow().getGuiScaledHeight() * yPos / scale;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(scale, scale);
        int border = GameXpBar.colour(Configs.hudConfig.catchPopupBorderColor::get);
        if (border >= 0) {
            // a box behind it, its border in the colour picked
            float widest = 0, total = 0;
            for (Line line : shown) {
                widest = Math.max(widest, font.width(line.text()) * line.size());
                total += lineHeight(font, line.size());
            }
            int bx0 = Math.round(centerX - widest / 2f) - 6, bx1 = Math.round(centerX + widest / 2f) + 6;
            int by0 = Math.round(y) - 5, by1 = Math.round(y + total) + 2;
            guiGraphics.fill(bx0, by0, bx1, by1, 0xB0101418);
            guiGraphics.renderOutline(bx0, by0, bx1 - bx0, by1 - by0, 0xFF000000 | border);
        }
        for (Line line : shown) {
            drawCentered(guiGraphics, font, line.text(), centerX, y, line.size());
            y += lineHeight(font, line.size());
        }
        guiGraphics.pose().popMatrix();
    }

    private static void drawCentered(GuiGraphics guiGraphics, Font font, Component line, float centerX, float y, float size) {
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(centerX, y);
        guiGraphics.pose().scale(size, size);
        guiGraphics.drawString(font, line, -font.width(line) / 2, 0, 0xFFFFFFFF, true);
        guiGraphics.pose().popMatrix();
    }
}
