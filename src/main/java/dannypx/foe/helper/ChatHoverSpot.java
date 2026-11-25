package dannypx.foe.helper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Whether the mouse is on an item's own name in chat. The server puts a shared bait's or pet's hover on its whole
 * message, so it showed wherever you pointed on that message; FOE+ shows it only over the item's name ("1x Suspending
 * Jerkbait"). It reads the chat as it's laid out (the same lines and places the game uses to find what's under the
 * mouse), takes the text that carries this hover, finds the name in it, and checks the mouse is on it.
 */
public final class ChatHoverSpot {
    private ChatHoverSpot() {}

    /// One letter on screen: its line (top to bottom), and whether the mouse is on it.
    record Letter(int line, char ch, Style style, boolean underMouse) {}

    /// TRUE: on the name; FALSE: elsewhere on the message; null: can't tell (not in chat, or the name isn't written
    /// there), so it's left as it was.
    public static Boolean onName(HoverEvent hover, String name, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (hover == null || name == null || !(mc.screen instanceof ChatScreen) || mc.gui == null) return null;
        String want = squash(name);
        if (want.length() < 2) return null;
        Collector c = new Collector(mc.font, mouseX, mouseY);
        mc.gui.getChat().captureClickableText(c, mc.getWindow().getGuiScaledHeight(), mc.gui.getGuiTicks(), true);
        if (c.lines.isEmpty()) return null;
        // the text carrying this hover, in reading order
        c.lines.sort(Comparator.comparingInt(l -> l.y));
        List<Letter> region = new ArrayList<>();
        boolean any = false;
        for (int i = 0; i < c.lines.size(); i++) {
            Line l = c.lines.get(i);
            for (Letter t : l.letters) {
                HoverEvent h = t.style().getHoverEvent();
                if (h == hover || (h != null && h.equals(hover))) {
                    region.add(new Letter(i, t.ch(), t.style(), t.underMouse()));
                    any |= t.underMouse();
                }
            }
        }
        if (!any) return null;   // the mouse isn't on this hover's text (another message's): left alone
        return match(region, want);
    }

    /// Package-private for tests: whether the letter under the mouse is in the name (null: the name isn't there).
    static Boolean match(List<Letter> region, String want) {
        // the name, ignoring spaces and case (it may be wrapped onto two lines)
        StringBuilder sq = new StringBuilder();
        List<Integer> at = new ArrayList<>();
        for (int i = 0; i < region.size(); i++) {
            char ch = region.get(i).ch();
            if (Character.isWhitespace(ch)) continue;
            sq.append(Character.toLowerCase(ch));
            at.add(i);
        }
        String s = sq.toString();
        boolean found = false;
        for (int from = s.indexOf(want); from >= 0; from = s.indexOf(want, from + 1)) {
            found = true;
            int start = at.get(from), end = at.get(from + want.length() - 1);
            // its count before it ("1x ") and brackets around it count as the name too
            start = withCount(region, start);
            if (end + 1 < region.size() && region.get(end + 1).ch() == ']') end++;
            for (int i = start; i <= end; i++) if (region.get(i).underMouse()) return Boolean.TRUE;
        }
        return found ? Boolean.FALSE : null;
    }

    private static int withCount(List<Letter> region, int start) {
        int i = start - 1;
        if (i >= 0 && region.get(i).ch() == '[') return i;
        while (i >= 0 && region.get(i).ch() == ' ') i--;
        if (i < 0 || Character.toLowerCase(region.get(i).ch()) != 'x') return start;
        int j = i - 1;
        while (j >= 0 && Character.isDigit(region.get(j).ch())) j--;
        if (j == i - 1) return start;   // no number before the x
        return j + 1;
    }

    /// Lowercase, without spaces, colour codes or brackets.
    static String squash(String name) {
        StringBuilder sb = new StringBuilder();
        String n = name.replaceAll("§.", "").trim();
        if (n.startsWith("[") && n.endsWith("]")) n = n.substring(1, n.length() - 1);
        for (int i = 0; i < n.length(); i++) {
            char ch = n.charAt(i);
            if (!Character.isWhitespace(ch)) sb.append(Character.toLowerCase(ch));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /// An item's name from its hover text: its first line.
    public static String firstLine(Component text) {
        String all = text.getString();
        int nl = all.indexOf('\n');
        return (nl < 0 ? all : all.substring(0, nl)).trim();
    }

    /// The hover's "Sent at ..." line (the server's time on each message), or null.
    public static String sentAt(Component text) {
        for (String l : text.getString().split("\n")) if (l.trim().toLowerCase(Locale.ROOT).startsWith("sent at")) return l.trim();
        return null;
    }

    private static final class Line {
        final int y;
        final List<Letter> letters = new ArrayList<>();

        Line(int y) {
            this.y = y;
        }
    }

    /// Reads the chat's lines where they are on screen (each letter, and whether the mouse is on it).
    private static final class Collector implements ActiveTextCollector {
        private final Font font;
        private final int mouseX, mouseY;
        private Parameters params = new Parameters(new Matrix3x2f());
        final List<Line> lines = new ArrayList<>();

        Collector(Font font, int mouseX, int mouseY) {
            this.font = font;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }

        @Override
        public Parameters defaultParameters() {
            return params;
        }

        @Override
        public void defaultParameters(Parameters p) {
            params = p;
        }

        @Override
        public void accept(TextAlignment alignment, int x, int y, Parameters p, FormattedCharSequence text) {
            int left = alignment.calculateLeft(x, font, text);
            // the mouse, in this line's own units (chat scale, its place)
            Vector2f m = new Matrix3x2f(p.pose()).invert().transformPosition(new Vector2f(mouseX, mouseY));
            // its place on screen, to put the lines in order
            Vector2f top = new Matrix3x2f(p.pose()).transformPosition(new Vector2f(left, y));
            boolean onLine = m.y >= y && m.y < y + font.lineHeight;
            Line line = new Line(Math.round(top.y));
            float[] cx = {left};
            text.accept((index, style, cp) -> {
                float w = font.getSplitter().stringWidth(FormattedCharSequence.codepoint(cp, style));
                boolean under = onLine && m.x >= cx[0] && m.x < cx[0] + w;
                for (char ch : Character.toChars(cp)) line.letters.add(new Letter(0, ch, style, under));
                cx[0] += w;
                return true;
            });
            lines.add(line);
        }

        @Override
        public void acceptScrolling(Component text, int x, int left, int right, int top, int bottom, Parameters p) {
            // the chat has none
        }
    }
}
