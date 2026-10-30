package dannypx.foe.helper;

import dannypx.foe.FishOnMCExtras;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * FOE's "clean" tags: redraws the server's rank and rarity tag glyphs as compact coloured squares
 * with a letter, using this mod's own {@code fishonmcextras:tags} font. The server resource pack
 * overrides the default font, but it can't override a font only this mod defines, so the glyph
 * stays the same and only the font drawing it changes. Ported from FOECollab's SimpleTagFont (AGPL-3.0).
 */
public final class SimpleTagFont {
    private SimpleTagFont() {}

    private static final FontDescription FONT = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "tags"));

    /// Player ranks (staff and the angler → admiral progression).
    private static final Set<Integer> RANK_CHARS = Set.of(
            0xF021, 0xF022, 0xF023, 0xF024, 0xF026, 0xF027, // owner, admin, manager, staff, designer, builder
            0xF028, 0xF029, 0xF030, 0xF031, 0xF032,         // admiral, captain, mariner, sailor, angler
            0xF066, 0xF088);                                // media, community manager

    /// Item rarity and event-variant tags.
    private static final Set<Integer> RARITY_CHARS = Set.of(
            0xF033, 0xF034, 0xF035, 0xF036, 0xF037,         // common … mythical
            0xF092, 0xF098, 0xF102, 0xF179);                // special, alternate, spooky, frozen

    /// Returns {@code text} with the chosen tag glyphs drawn in the clean font (unchanged if none).
    public static Component apply(Component text, boolean ranks, boolean rarities) {
        if (text == null || (!ranks && !rarities)) return text;

        List<String> pieces = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        boolean[] changed = {false};
        text.visit((style, content) -> {
            StringBuilder run = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                boolean target = (ranks && RANK_CHARS.contains((int) c)) || (rarities && RARITY_CHARS.contains((int) c));
                if (target) {
                    if (run.length() > 0) {
                        pieces.add(run.toString());
                        styles.add(style);
                        run.setLength(0);
                    }
                    pieces.add(String.valueOf(c));
                    styles.add(style.withFont(FONT));
                    changed[0] = true;
                } else {
                    run.append(c);
                }
            }
            if (run.length() > 0) {
                pieces.add(run.toString());
                styles.add(style);
            }
            return Optional.empty();
        }, Style.EMPTY);

        if (!changed[0]) return text;
        MutableComponent out = Component.empty();
        for (int i = 0; i < pieces.size(); i++) out.append(Component.literal(pieces.get(i)).setStyle(styles.get(i)));
        return out;
    }
}
