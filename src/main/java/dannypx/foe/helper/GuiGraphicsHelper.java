package dannypx.foe.helper;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import dannypx.foe.type.StringStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.CommonColors;
import org.joml.Matrix3x2f;

public class GuiGraphicsHelper {
    public static void drawString(GuiGraphics guiGraphics, Font font, Component component, int x, int y, StringStyle... stringStyles) {
        EnumSet<StringStyle> styles = stringStyles.length == 0
                ? EnumSet.noneOf(StringStyle.class)
                : EnumSet.copyOf(Arrays.asList(stringStyles));
        drawString(guiGraphics, font, component, x, y, styles);
    }

    private static int drawString(GuiGraphics guiGraphics, Font font, Component component, int x, int y, EnumSet<StringStyle> styles) {
        List<Component> siblings = component.getSiblings();

        if (siblings.isEmpty()) {
            return drawGlyphs(guiGraphics, font, component.getString(), x, y, component.getStyle(), styles);
        }

        for (Component sibling : siblings) {
            x = drawString(guiGraphics, font, sibling, x, y, styles);
        }
        return x;
    }

    private static int drawGlyphs(GuiGraphics guiGraphics, Font font, String text, int x, int y, Style style, EnumSet<StringStyle> styles) {
        boolean shadow = styles.contains(StringStyle.SHADOW);
        boolean middle = styles.contains(StringStyle.MIDDLE);
        boolean hasCustomFont = styles.contains(StringStyle.HAS_CUSTOM_FONT);
        boolean smallCaps = styles.contains(StringStyle.SMALL_CAPS);

        Deque<Character> characters = text.chars()
                .mapToObj(c -> (char) c)
                .collect(Collectors.toCollection(ArrayDeque::new));

        while (!characters.isEmpty()) {
            String glyph = popNextGlyph(characters);
            if (smallCaps) {
                glyph = TextHelper.smallCaps(glyph);
            }

            int cWidth = font.width(Component.literal(glyph).setStyle(style));
            int yAdjust = computeYAdjustment(glyph, middle, hasCustomFont, smallCaps);

            guiGraphics.guiRenderState.submitText(
                    new GuiTextRenderState(
                            font,
                            Component.literal(glyph).setStyle(style).getVisualOrderText(),
                            new Matrix3x2f(guiGraphics.pose()),
                            x, y - yAdjust, CommonColors.WHITE,
                            0, shadow, false, guiGraphics.scissorStack.peek()
                    )
            );

            x += cWidth;
        }

        return x;
    }

    private static int computeYAdjustment(String glyph, boolean middle, boolean hasCustomFont, boolean smallCaps) {
        int translateY = middle ? -1 : 0;
        int offsetY = 0;

        if (glyph.length() == 1) {
            char c = glyph.charAt(0);
            if (TextHelper.isSmallNumber(c)) {
                offsetY = 1;
            } else if (hasCustomFont && smallCaps && TextHelper.isRank(c)) {
                offsetY = -1;
            } else if (TextHelper.isSmallLetter(c) || (hasCustomFont && TextHelper.isCustomFont(c))) {

            } else {
                translateY = 0;
            }
        }

        return offsetY - translateY;
    }

    private static String popNextGlyph(Deque<Character> characters) {
        if (characters.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        char first = characters.pollFirst();
        sb.append(first);

        if (Character.isHighSurrogate(first) && !characters.isEmpty() && Character.isLowSurrogate(characters.peekFirst())) {
            sb.append(characters.pollFirst());
        }

        while (!characters.isEmpty()) {
            char next = characters.peekFirst();
            int codePoint = (Character.isHighSurrogate(next) && characters.size() > 1 && Character.isLowSurrogate(peekSecond(characters)))
                    ? Character.toCodePoint(next, peekSecond(characters))
                    : next;

            boolean isModifier = next == '\uFE0F' || next == '\uFE0E' || next == '\u200D'
                    || (codePoint >= 0x1_F3FB && codePoint <= 0x1F3FF);

            if (!isModifier) break;

            sb.append(characters.pollFirst());
            if (Character.isSupplementaryCodePoint(codePoint)) {
                sb.append(characters.pollFirst());
            }
        }
        return sb.toString();
    }

    private static Character peekSecond(Deque<Character> characters) {
        Iterator<Character> it = characters.iterator();
        it.next();
        return it.next();
    }

    /// A left-to-right gradient as one vertical gradient turned on its side (one draw instead of
    /// one per column of pixels).
    public static void drawHorizontalGradient(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int leftColor, int rightColor) {
        int width = x2 - x1, height = y2 - y1;
        if (width <= 0 || height <= 0) return;
        guiGraphics.pose().pushMatrix();
        try {
            guiGraphics.pose().translate(x1, y2);
            guiGraphics.pose().rotate((float) (-Math.PI / 2));
            guiGraphics.fillGradient(0, 0, height, width, leftColor, rightColor);
        } finally {
            guiGraphics.pose().popMatrix();
        }
    }

    /// A 1px line as one thin rectangle turned to point from (x1, y1) to (x2, y2) (one draw
    /// instead of one per pixel).
    public static void drawLine(GuiGraphics guiGraphics,
                                int x1, int y1,
                                int x2, int y2,
                                int color) {
        int dx = x2 - x1, dy = y2 - y1;
        if (dx == 0 || dy == 0) {
            guiGraphics.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
            return;
        }
        float length = (float) Math.hypot(dx, dy);
        guiGraphics.pose().pushMatrix();
        try {
            guiGraphics.pose().translate(x1 + 0.5f, y1 + 0.5f);
            guiGraphics.pose().rotate((float) Math.atan2(dy, dx));
            guiGraphics.fill(0, -1, Math.round(length) + 1, 0, color);
        } finally {
            guiGraphics.pose().popMatrix();
        }
    }
}
