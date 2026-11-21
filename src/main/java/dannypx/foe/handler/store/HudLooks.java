package dannypx.foe.handler.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.logic.LoggerHandler;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How each of FOE+'s own HUD panels looks, as set in the HUD Editor: line spacing, padding, the box's colour and
 * see-through-ness, the border's and title's colours, the text alignment, a picture by the title, and the lines
 * you've hidden. Kept per panel in config/fishonmcextras/hud_looks.json. A panel nobody has edited looks as it
 * always did.
 */
public final class HudLooks {
    private HudLooks() {}

    /// One panel's look. The defaults are the panels' own look.
    public static final class Look {
        /// Extra pixels between lines (the panels' own is 2).
        public int lineSpacing = 2;
        /// Extra room inside the box, all round.
        public int padding = 0;
        /// The box colour (RGB) and how solid it is (0-100; -1: the panel's own setting).
        public int boxColor = 0x000000;
        public int opacity = -1;
        /// The border tint (white: the border's own colours) and the title's colour (-1: its own).
        public int borderColor = 0xFFFFFF;
        public int titleColor = -1;
        /// AUTO, LEFT, CENTER or RIGHT.
        public String align = "AUTO";
        /// An item shown by the title ("minecraft:cod"), or "" for none.
        public String picture = "";
        /// The title in your own words ("" for the panel's own), and the lines laid out in columns (names on the
        /// left, numbers lined up on the right) instead of as sentences.
        public String title = "";
        /// No title at all: no name on its border (nor as its first line).
        public boolean noTitle = false;
        public boolean columns = false;
        /// Its lines split into two columns side by side (half on the left, the rest on the right), for HUDs of many
        /// short lines (the quests).
        public boolean twoColumns = false;
        /// Your own picture in the box (a file in config/fishonmcextras/hud_images, "" for none): its size (percent of
        /// the box's width), where it sits (percent across and down the room left), how solid it is, whether it goes
        /// over the text or behind it, and whether it fills the whole box (a background).
        public String image = "";
        public int imageSize = 30;
        public int imageX = 100;
        public int imageY = 0;
        public int imageOpacity = 100;
        public boolean imageInFront = false;
        public boolean imageFill = false;
        /// The lines you've hidden, by their label (see {@link #key}).
        public List<String> hidden = new ArrayList<>();
        /// This HUD's own style ("DEFAULT": the one picked for every HUD in Customize, "FOE_PLUS" or "FOE_R"), and
        /// whether it has its border (off: just the box, its title as the first line).
        public String style = "DEFAULT";
        public boolean border = true;
        /// The border: "DEFAULT" (as Info panel border style says for every HUD: none when that's Off), "ON" (always,
        /// in that style, or Clean rounded when it's Off) or "OFF". Null in older saves: from {@link #border}.
        public String frame = null;

        public String frameMode() {
            if (frame != null) return frame;
            return border ? "DEFAULT" : "OFF";
        }

        public boolean isDefault() {
            return lineSpacing == 2 && padding == 0 && boxColor == 0 && opacity == -1 && borderColor == 0xFFFFFF
                    && titleColor == -1 && "AUTO".equals(align) && picture.isEmpty() && hidden.isEmpty()
                    && (title == null || title.isEmpty()) && !noTitle && !columns && !twoColumns && (image == null || image.isEmpty())
                && (style == null || "DEFAULT".equals(style)) && "DEFAULT".equals(frameMode());
        }

        public dannypx.foe.type.TextAlign textAlign() {
            try {
                return dannypx.foe.type.TextAlign.valueOf(align);
            } catch (IllegalArgumentException e) {
                return dannypx.foe.type.TextAlign.AUTO;
            }
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Look> looks = null;
    private static final Look DEFAULT = new Look();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FishOnMCExtras.MOD_ID).resolve("hud_looks.json");
    }

    private static Map<String, Look> all() {
        if (looks != null) return looks;
        looks = new HashMap<>();
        try {
            Path f = file();
            if (Files.exists(f)) {
                Map<String, Look> read = GSON.fromJson(Files.readString(f), new TypeToken<Map<String, Look>>() {}.getType());
                if (read != null) looks.putAll(read);
            }
        } catch (IOException | RuntimeException e) {
            LoggerHandler.error(e);
        }
        return looks;
    }

    /// The look for a panel (by its id), never null; the panels' own look when unedited.
    public static Look get(String panel) {
        Look l = all().get(panel);
        return l == null ? DEFAULT : l;
    }

    /// The look to edit for a panel: made (and remembered) when needed.
    public static Look edit(String panel) {
        return all().computeIfAbsent(panel, p -> new Look());
    }

    public static void reset(String panel) {
        Look l = all().get(panel);
        if (l != null) copyInto(new Look(), l);     // the same object, so the editor keeps editing what's shown
        save();
    }

    private static void copyInto(Look from, Look to) {
        to.lineSpacing = from.lineSpacing; to.padding = from.padding; to.boxColor = from.boxColor; to.opacity = from.opacity;
        to.borderColor = from.borderColor; to.titleColor = from.titleColor; to.align = from.align; to.picture = from.picture;
        to.title = from.title; to.noTitle = from.noTitle; to.columns = from.columns; to.twoColumns = from.twoColumns; to.hidden = new ArrayList<>(from.hidden);
        to.image = from.image; to.imageSize = from.imageSize; to.imageX = from.imageX; to.imageY = from.imageY;
        to.imageOpacity = from.imageOpacity; to.imageInFront = from.imageInFront; to.imageFill = from.imageFill;
        to.style = from.style; to.border = from.border; to.frame = from.frame;
    }

    /// Writes the edited looks. Unedited ones are left out of the file but kept in memory: the HUD Editor holds on
    /// to the look it's changing, and dropping it here would leave its next changes (the name you're typing) going
    /// nowhere.
    public static void save() {
        try {
            Map<String, Look> out = new java.util.TreeMap<>();
            all().forEach((k, v) -> { if (!v.isDefault()) out.put(k, v); });
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(out));
        } catch (IOException e) {
            LoggerHandler.error(e);
        }
    }

    /// A line's label, what hiding goes by: its words up to the first number or colon ("Total Pets",
    /// "Fish/Hour", "Location"), lower case. The same line keeps its label as its numbers change.
    public static String key(String text) {
        String t = text == null ? "" : text.strip();
        int cut = t.length();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isDigit(c) || c == ':') { cut = i; break; }
        }
        String k = t.substring(0, cut).strip();
        if (k.isEmpty()) k = t.length() > 16 ? t.substring(0, 16) : t;
        return k.toLowerCase(Locale.ROOT);
    }

    /// Each line's label, as {@link #key}, but where several lines share one (eight "Epic 3/30" quests) the second on
    /// are told apart by their place ("epic #2"), so each can be hidden on its own. Blank lines get null.
    public static List<String> keys(List<String> texts) {
        List<String> out = new ArrayList<>(texts.size());
        Map<String, Integer> seen = new java.util.HashMap<>();
        for (String t : texts) {
            if (t == null || t.isBlank()) { out.add(null); continue; }
            String k = key(t);
            int n = seen.merge(k, 1, Integer::sum);
            out.add(n == 1 ? k : k + " #" + n);
        }
        return out;
    }
}
