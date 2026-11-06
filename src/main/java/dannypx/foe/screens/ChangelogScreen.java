package dannypx.foe.screens;

import dannypx.foe.handler.io.ChangelogFetcherHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChangelogScreen extends Screen implements ChangelogFetcherHandler.Listener {
    private static final int CONTENT_PADDING = 12;
    private static final int TOP_MARGIN = 32;
    private static final int BOTTOM_MARGIN = 30;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int LINE_SPACING = 2;
    /// The notes are drawn a little smaller than normal text, in a column about a page wide,
    /// so long entries stay easy to read; version headings stay full size.
    private static final float BODY_SCALE = 0.8f;
    private static final int MAX_COLUMN_WIDTH = 340;

    private static final int COLOR_HEADER = 0xFFFFD966;
    private static final int COLOR_META = 0xFFAAAAAA;
    private static final int COLOR_BODY = 0xFFE0E0E0;
    private static final int COLOR_BULLET = 0xFF7FCFFF;

    private static final Pattern BULLET_AUTHOR = Pattern.compile("^(.*?)\\s+by\\s+(@\\S+)$");
    private static final Pattern INLINE_MARKDOWN = Pattern.compile("\\*\\*(.+?)\\*\\*|\\*(.+?)\\*|_(.+?)_|`(.+?)`");

    private final Screen parent;

    private final List<RenderLine> renderLines = new ArrayList<>();
    private double scrollAmount = 0;
    private double maxScroll = 0;

    private boolean draggingScrollbar = false;

    public ChangelogScreen(Screen parent) {
        super(Component.literal("Changelog"));
        this.parent = parent;
    }

    private record RenderLine(FormattedCharSequence sequence, int color, int indent, int yOffset, int height, float scale) {
        static RenderLine blank(int yOffset, int height) {
            return new RenderLine(null, 0, 0, yOffset, height, 1.0f);
        }
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.literal("Return"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - BOTTOM_MARGIN + 6, 100, 20)
                .build());

        ChangelogFetcherHandler.instance().addListener(this);
        ChangelogFetcherHandler.instance().fetch(false);

        if (ChangelogFetcherHandler.instance().hasData()) {
            rebuildLines();
        }
    }

    @Override
    public void removed() {
        ChangelogFetcherHandler.instance().removeListener(this);
        super.removed();
    }

    @Override
    public void onChangelogUpdated() {
        rebuildLines();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private int contentLeft() {
        return Math.max(CONTENT_PADDING, (this.width - contentWidth() - SCROLLBAR_WIDTH - 4) / 2);
    }

    private int contentWidth() {
        return Math.min(MAX_COLUMN_WIDTH, this.width - CONTENT_PADDING * 2 - SCROLLBAR_WIDTH - 4);
    }

    private int viewportTop() {
        return TOP_MARGIN;
    }

    private int viewportBottom() {
        return this.height - BOTTOM_MARGIN;
    }

    private static final Pattern LEADING_NOTE = Pattern.compile("(?s)\\A#(?!#)[ \\t]*(.+?)(?=\\n##[ \\t]|\\z)");
    private static final int COLOR_WARNING = 0xFFFF5C5C;
    private static final float WARNING_SCALE = 1.4f;

    private void rebuildLines() {
        renderLines.clear();
        scrollAmount = 0;

        List<ChangelogFetcherHandler.ChangelogEntry> entries = ChangelogFetcherHandler.instance().getEntries();
        if (entries == null) return;

        Font font = this.font;
        int wrapWidth = contentWidth();
        int normalLineHeight = font.lineHeight + LINE_SPACING;
        int bodyLineHeight = Mth.ceil(font.lineHeight * BODY_SCALE) + LINE_SPACING;
        int cursorY = 0;

        for (ChangelogFetcherHandler.ChangelogEntry entry : entries) {
            // "v1.0.46  (2026-11-06)": the Minecraft part and a name that just repeats the version are left out.
            String version = entry.versionNumber().replaceFirst("\\+.*$", "");
            boolean nameRepeats = entry.name().isBlank() || entry.name().contains(version);
            String header = "v" + version
                    + (nameRepeats ? "" : " - " + entry.name())
                    + (entry.datePublished().isBlank() ? "" : "  (" + entry.datePublished() + ")");
            renderLines.add(new RenderLine(FormattedCharSequence.forward(header, Style.EMPTY), COLOR_HEADER, 0, cursorY, normalLineHeight, 1.0f));
            cursorY += normalLineHeight;

            String changelog = entry.changelog().strip();
            String bodyToRender = changelog;

            Matcher noteMatcher = LEADING_NOTE.matcher(changelog);
            if (noteMatcher.find()) {
                String[] noteLines = noteMatcher.group(1).strip().split("\n", -1);
                String noteTitle = noteLines[0].strip();

                MutableComponent titleComponent = Component.literal("! " + noteTitle)
                        .withStyle(Style.EMPTY.withColor(COLOR_WARNING).withBold(true));
                int warnWrapWidth = Mth.floor(wrapWidth / WARNING_SCALE);
                for (FormattedCharSequence seq : font.split(titleComponent, warnWrapWidth)) {
                    int height = Mth.ceil(font.lineHeight * WARNING_SCALE) + LINE_SPACING;
                    renderLines.add(new RenderLine(seq, COLOR_WARNING, 0, cursorY, height, WARNING_SCALE));
                    cursorY += height;
                }

                for (int i = 1; i < noteLines.length; i++) {
                    String detailLine = noteLines[i].strip();
                    if (detailLine.isEmpty()) {
                        renderLines.add(RenderLine.blank(cursorY, normalLineHeight));
                        cursorY += normalLineHeight;
                        continue;
                    }
                    MutableComponent detailComponent = parseInlineMarkdown(detailLine, Style.EMPTY.withColor(COLOR_WARNING));
                    for (FormattedCharSequence seq : font.split(detailComponent, wrapWidth)) {
                        renderLines.add(new RenderLine(seq, COLOR_WARNING, 0, cursorY, normalLineHeight, 1.0f));
                        cursorY += normalLineHeight;
                    }
                }

                renderLines.add(RenderLine.blank(cursorY, normalLineHeight));
                cursorY += normalLineHeight;

                bodyToRender = changelog.substring(noteMatcher.end()).strip();
            }

            for (String rawLine : bodyToRender.split("\n", -1)) {
                String trimmed = rawLine.strip();
                if (trimmed.isEmpty()) {
                    renderLines.add(RenderLine.blank(cursorY, bodyLineHeight));
                    cursorY += bodyLineHeight;
                    continue;
                }

                boolean isBullet = trimmed.startsWith("- ") || trimmed.startsWith("* ");
                boolean isHeading = trimmed.startsWith("#");
                int indent = 0;
                int fallbackColor = COLOR_BODY;
                MutableComponent lineComponent;

                if (isBullet) {
                    indent = 8;
                    fallbackColor = COLOR_BULLET;
                    lineComponent = buildBulletLine(trimmed.substring(2).strip());
                } else if (isHeading) {
                    fallbackColor = COLOR_HEADER;
                    String headingText = trimmed.replaceFirst("^#+\\s*", "");
                    lineComponent = parseInlineMarkdown(headingText, Style.EMPTY.withColor(COLOR_HEADER).withBold(true));
                } else {
                    lineComponent = parseInlineMarkdown(trimmed, Style.EMPTY.withColor(COLOR_BODY));
                }

                int effectiveWidth = Mth.floor((wrapWidth - indent) / BODY_SCALE);
                List<FormattedCharSequence> wrapped = font.split(lineComponent, effectiveWidth);
                for (FormattedCharSequence seq : wrapped) {
                    renderLines.add(new RenderLine(seq, fallbackColor, indent, cursorY, bodyLineHeight, BODY_SCALE));
                    cursorY += bodyLineHeight;
                }
            }
            renderLines.add(RenderLine.blank(cursorY, normalLineHeight));
            cursorY += normalLineHeight;
        }

        int viewportHeight = viewportBottom() - viewportTop();
        maxScroll = Math.max(0, cursorY - viewportHeight);
    }

    private static MutableComponent buildBulletLine(String content) {
        String main = content;
        String author = null;

        Matcher authorMatch = BULLET_AUTHOR.matcher(content);
        if (authorMatch.matches()) {
            main = authorMatch.group(1).strip();
            author = authorMatch.group(2);
        }

        MutableComponent line = Component.literal("• ")
                .withStyle(Style.EMPTY.withColor(COLOR_BULLET))
                .append(parseInlineMarkdown(main, Style.EMPTY.withColor(COLOR_BULLET)));

        if (author != null) {
            line.append(Component.literal(" - " + author)
                    .withStyle(Style.EMPTY.withColor(COLOR_META).withItalic(true)));
        }
        return line;
    }

    private static MutableComponent parseInlineMarkdown(String text, Style baseStyle) {
        MutableComponent result = Component.empty();
        Matcher matcher = INLINE_MARKDOWN.matcher(text);
        int last = 0;

        while (matcher.find()) {
            if (matcher.start() > last) {
                result.append(Component.literal(text.substring(last, matcher.start())).withStyle(baseStyle));
            }
            if (matcher.group(1) != null) {
                result.append(Component.literal(matcher.group(1)).withStyle(baseStyle.withBold(true)));
            } else if (matcher.group(2) != null) {
                result.append(Component.literal(matcher.group(2)).withStyle(baseStyle.withItalic(true)));
            } else if (matcher.group(3) != null) {
                result.append(Component.literal(matcher.group(3)).withStyle(baseStyle.withItalic(true)));
            } else {
                result.append(Component.literal(matcher.group(4))
                        .withStyle(baseStyle.withColor(0xFF88DDFF)));
            }
            last = matcher.end();
        }
        if (last < text.length()) {
            result.append(Component.literal(text.substring(last)).withStyle(baseStyle));
        }
        return result;
    }


    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

        int top = viewportTop();
        int bottom = viewportBottom();
        int left = contentLeft();
        int right = left + contentWidth();

        String error = ChangelogFetcherHandler.instance().getError();
        List<ChangelogFetcherHandler.ChangelogEntry> entries = ChangelogFetcherHandler.instance().getEntries();

        if (error != null) {
            guiGraphics.drawCenteredString(this.font,
                    Component.literal("Failed to load changelog: " + error),
                    this.width / 2, top + 10, 0xFFFF5555);
        } else if (entries == null) {
            guiGraphics.drawCenteredString(this.font, Component.literal("Loading changelog..."), this.width / 2, top + 10, 0xFFAAAAAA);
        } else if (renderLines.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, Component.literal("No changelog entries found."), this.width / 2, top + 10, 0xFFAAAAAA);
        } else {
            guiGraphics.enableScissor(left, top, right, bottom);

            int startY = top - (int) scrollAmount;
            for (RenderLine line : renderLines) {
                int y = startY + line.yOffset;
                if (y + line.height < top || y > bottom) continue; // skip offscreen lines
                if (line.sequence == null) continue;

                if (line.scale == 1.0f) {
                    guiGraphics.drawString(this.font, line.sequence, left + line.indent, y, line.color);
                } else {
                    guiGraphics.pose().pushMatrix();
                    guiGraphics.pose().translate(left + line.indent, y);
                    guiGraphics.pose().scale(line.scale, line.scale);
                    guiGraphics.drawString(this.font, line.sequence, 0, 0, line.color);
                    guiGraphics.pose().popMatrix();
                }
            }

            guiGraphics.disableScissor();

            if (maxScroll > 0) {
                drawScrollbar(guiGraphics, right + 4, top, bottom);
            }
        }

    }

    private void drawScrollbar(GuiGraphics guiGraphics, int x, int top, int bottom) {
        int viewportHeight = bottom - top;
        guiGraphics.fill(x, top, x + SCROLLBAR_WIDTH, bottom, 0x33FFFFFF);

        int contentHeight = viewportHeight + (int) maxScroll;
        int thumbHeight = Math.max(20, (int) ((long) viewportHeight * viewportHeight / Math.max(1, contentHeight)));
        int thumbY = top + (int) ((viewportHeight - thumbHeight) * (scrollAmount / maxScroll));

        guiGraphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xAAFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0) {
            int lineHeight = this.font.lineHeight + LINE_SPACING;
            scrollAmount = Mth.clamp(scrollAmount - scrollY * lineHeight * 3, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        if (maxScroll > 0 && mouseButtonEvent.button() == 0) {
            int right = contentLeft() + contentWidth() + 4;
            int top = viewportTop();
            int bottom = viewportBottom();
            if (mouseButtonEvent.x() >= right && mouseButtonEvent.x() <= right + SCROLLBAR_WIDTH && mouseButtonEvent.y() >= top && mouseButtonEvent.y() <= bottom) {
                draggingScrollbar = true;
                setScrollFromMouseY(mouseButtonEvent.y(), top, bottom);
                return true;
            }
        }
        return super.mouseClicked(mouseButtonEvent, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double x, double y) {
        if (draggingScrollbar) {
            setScrollFromMouseY(mouseButtonEvent.y(), viewportTop(), viewportBottom());
            return true;
        }
        return super.mouseDragged(mouseButtonEvent, x, y);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseButtonEvent);
    }

    private void setScrollFromMouseY(double mouseY, int top, int bottom) {
        double fraction = Mth.clamp((mouseY - top) / (double) (bottom - top), 0, 1);
        scrollAmount = fraction * maxScroll;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}