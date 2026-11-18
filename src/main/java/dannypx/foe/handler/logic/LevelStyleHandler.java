package dannypx.foe.handler.logic;

import com.google.gson.Gson;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Your level exactly as chat shows it ("[193]" in front of your name, a gradient or an animated
 * colour at some levels), so the top bar can show it 1:1.
 *
 * <p>Learned from your own chat messages: the full style of each character of "[193]" (colour,
 * font, shadow, bold...), which is what makes the server's animated colours move, so drawn with
 * the same styles it moves on the top bar too. Saved, so it's there from the start next time.
 * When the level has more or fewer digits than the one learned from, the styles are spread over it
 * the same way (a gradient stays a gradient).
 */
public final class LevelStyleHandler {
    private static final LevelStyleHandler INSTANCE = new LevelStyleHandler();
    public static LevelStyleHandler instance() { return INSTANCE; }

    /// "[193]" near the start of a chat line (after an icon or two), as player messages begin.
    static final Pattern LEVEL = Pattern.compile("^[^\\[»:]{0,8}\\[(\\d{1,4})]");

    /// One style per character of the bracketed level ("[", digits, "]"); empty until learned.
    private List<Style> styles = new ArrayList<>();
    private boolean loaded = false;

    public static Supplier<Path> FILE = () -> net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve(dannypx.foe.FishOnMCExtras.MOD_ID).resolve("level_style.json");
    private static final Gson GSON = new Gson();

    /// Whether `text` is your own chat message: "[193] ... phckd » hi" (badges and a rank may sit
    /// between the level and your name), with your name before the message starts.
    static Matcher yours(String text, String myName) {
        Matcher m = LEVEL.matcher(text);
        if (!m.find()) return null;
        String after = text.substring(m.end());
        int cut = after.length();
        for (String sep : new String[]{"»", ":"}) {
            int i = after.indexOf(sep);
            if (i >= 0) cut = Math.min(cut, i);
        }
        if (cut > 80) return null;
        Matcher name = Pattern.compile("(?<![\\w])" + Pattern.quote(myName) + "(?![\\w])", Pattern.CASE_INSENSITIVE)
                .matcher(after.substring(0, cut));
        return name.find() ? m : null;
    }

    /// A chat line: when it's your own message, remember how your level is styled.
    public synchronized void onChat(Component message, String myName) {
        if (message == null || myName == null || myName.isEmpty()) return;
        String text = message.getString();
        Matcher m = yours(text, myName);
        if (m == null) return;
        int from = m.start(1) - 1, to = m.end(1) + 1; // "[" digits "]"
        List<Style> found = new ArrayList<>();
        for (Style s : stylesAt(message, from, to)) found.add(visual(s));
        if (found.size() != to - from || found.stream().allMatch(s -> s.getColor() == null)) return;
        load();
        if (!found.equals(styles)) {
            styles = found;
            save();
        }
    }

    /// Only how it looks: no click, hover or insertion carried over.
    static Style visual(Style s) {
        return s.withClickEvent(null).withHoverEvent(null).withInsertion(null);
    }

    /// The style of each character from `from` to `to` (exclusive) of `message`'s text.
    static List<Style> stylesAt(Component message, int from, int to) {
        List<Style> out = new ArrayList<>();
        int[] at = {0};
        message.visit((style, str) -> {
            for (int i = 0; i < str.length(); i++) {
                int pos = at[0] + i;
                if (pos >= from && pos < to) out.add(style);
            }
            at[0] += str.length();
            return at[0] >= to ? Optional.of(Boolean.TRUE) : Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /// "[193]" styled as in your chat, or null when that isn't known yet.
    public synchronized Component level(String level) {
        load();
        if (styles.isEmpty() || level == null || level.isEmpty()) return null;
        return paint("[" + level + "]", styles);
    }

    /// `text` styled by `styles` spread over its length (the ends kept on the ends).
    static Component paint(String text, List<Style> styles) {
        MutableComponent out = Component.empty();
        int n = text.length(), k = styles.size();
        for (int i = 0; i < n; i++) {
            int idx = n == 1 || k == 1 ? 0 : Math.round(i * (k - 1) / (float) (n - 1));
            out.append(Component.literal(String.valueOf(text.charAt(i))).withStyle(styles.get(Math.min(k - 1, idx))));
        }
        return out;
    }

    //region Saving
    /// How one character looks, saved as plain values (works without the game's registries).
    private static final class Saved {
        Integer color, shadow;
        String font;
        boolean bold, italic, underlined, strikethrough, obfuscated;

        static Saved of(Style st) {
            Saved v = new Saved();
            v.color = st.getColor() == null ? null : st.getColor().getValue();
            v.shadow = st.getShadowColor();
            if (st.getFont() instanceof net.minecraft.network.chat.FontDescription.Resource r) v.font = r.id().toString();
            v.bold = st.isBold(); v.italic = st.isItalic(); v.underlined = st.isUnderlined();
            v.strikethrough = st.isStrikethrough(); v.obfuscated = st.isObfuscated();
            return v;
        }

        Style style() {
            Style st = Style.EMPTY.withBold(bold).withItalic(italic).withUnderlined(underlined)
                    .withStrikethrough(strikethrough).withObfuscated(obfuscated);
            if (color != null) st = st.withColor(color);
            if (shadow != null) st = st.withShadowColor(shadow);
            if (font != null) {
                net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(font);
                if (id != null) st = st.withFont(new net.minecraft.network.chat.FontDescription.Resource(id));
            }
            return st;
        }
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        try {
            Path f = FILE.get();
            if (f == null || !Files.exists(f)) return;
            Saved[] arr = GSON.fromJson(Files.readString(f), Saved[].class);
            List<Style> out = new ArrayList<>();
            if (arr != null) for (Saved v : arr) out.add(v.style());
            if (!out.isEmpty()) styles = out;
        } catch (Throwable ignored) {
            // nothing learned yet (or an older file): learned again from your next message
        }
    }

    private void save() {
        try {
            Path f = FILE.get();
            if (f == null) return;
            List<Saved> arr = new ArrayList<>();
            for (Style st : styles) arr.add(Saved.of(st));
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(arr));
        } catch (Throwable ignored) {
            // kept for this session only
        }
    }

    /// For tests.
    synchronized void reset() { styles = new ArrayList<>(); loaded = true; }
    //endregion
}
