package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.TitleHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.item.FishTagObject;
import dannypx.foe.screens.element.Element;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
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

    private static long shownAt = 0L;
    private static List<Component> bigLines = new ArrayList<>();
    private static List<Component> smallLines = new ArrayList<>();

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
        if (font != null) this.updateSize(font, previewBig(), previewSmall());
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

        bigLines = big;
        smallLines = small;
        shownAt = System.currentTimeMillis();
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

    private void updateSize(Font font, List<Component> big, List<Component> small) {
        float scale = Configs.hudConfig.catchPopupScale.get();
        int widest = 0;
        for (Component line : big) widest = Math.max(widest, Math.round(font.width(line) * BIG));
        for (Component line : small) widest = Math.max(widest, Math.round(font.width(line) * SMALL));
        int total = big.size() * lineHeight(font, BIG) + small.size() * lineHeight(font, SMALL);
        this.width = Math.max(20, Math.round(widest * scale));
        this.height = Math.max(20, Math.round(total * scale));
    }

    @Override
    public void render(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        long elapsed = System.currentTimeMillis() - shownAt;
        boolean showing = shownAt > 0 && elapsed < Configs.hudConfig.catchPopupTime.get() * 1000L;

        List<Component> big = showing ? bigLines : (isCopy ? previewBig() : List.of());
        List<Component> small = showing ? smallLines : (isCopy ? previewSmall() : List.of());
        if (big.isEmpty() && small.isEmpty()) return;

        if (!isCopy) {
            xPos = Configs.hudConfig.catchPopupXPosition.get() / 100f;
            yPos = Configs.hudConfig.catchPopupYPosition.get() / 100f;
            // Hide the server's own catch title while ours is up, like FOE.
            if (showing && elapsed < 3000L) mc.gui.clearTitles();
        }
        this.updateSize(font, big, small);

        float scale = Configs.hudConfig.catchPopupScale.get();
        float centerX = mc.getWindow().getGuiScaledWidth() * xPos / scale;
        float y = mc.getWindow().getGuiScaledHeight() * yPos / scale;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(scale, scale);
        for (Component line : big) {
            drawCentered(guiGraphics, font, line, centerX, y, BIG);
            y += lineHeight(font, BIG);
        }
        for (Component line : small) {
            drawCentered(guiGraphics, font, line, centerX, y, SMALL);
            y += lineHeight(font, SMALL);
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
