package dannypx.foe.screens.element.hud;

import dannypx.foe.FishOnMCExtras;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * HUD decorations: little pictures that sit on a HUD's corners (a heart, a fox in the grass, a flower pot...), each
 * corner picked on its own in the HUD Editor (Image tab). Ported from FOECollab's flair (AGPL-3.0), its pictures too.
 * Each picture is 64 x 64 with the box's corner at (24, 24) for the left / top ones and (40, 40) for the right / bottom
 * ones, as FOECollab draws them.
 */
public final class HudFlair {
    private HudFlair() {}

    public enum Corner {
        TOP_LEFT("top_left", "Top left"), TOP_RIGHT("top_right", "Top right"),
        BOTTOM_LEFT("bottom_left", "Bottom left"), BOTTOM_RIGHT("bottom_right", "Bottom right");

        public final String folder, displayName;

        Corner(String folder, String displayName) {
            this.folder = folder;
            this.displayName = displayName;
        }
    }

    public record Option(String id, String name) {}

    /// The pictures each corner can have ("" first: none).
    public static List<Option> options(Corner corner) {
        return switch (corner) {
            case TOP_LEFT -> List.of(new Option("", "None"), new Option("heart", "Heart"), new Option("grass_rat", "Rat in the grass"),
                    new Option("grass_duck", "Duck in the grass"), new Option("grass_fox", "Sleeping fox"), new Option("cat_tree", "Cat tree"), new Option("cat", "Cat"),
                    new Option("snow", "Snow"), new Option("mushroom", "Mushroom"), new Option("star", "Star"));
            case TOP_RIGHT -> List.of(new Option("", "None"), new Option("heart", "Heart"), new Option("foe_sign", "FOE+ sign"),
                    new Option("tape", "Tape"), new Option("grass_tree", "Tree in the grass"), new Option("sand_crab", "Crab in the sand"),
                    new Option("grass_fox", "Sleeping fox"), new Option("cat_tree", "Cat tree"),
                    new Option("snow", "Snow"), new Option("star", "Star"));
            case BOTTOM_LEFT -> List.of(new Option("", "None"), new Option("heart", "Heart"), new Option("crow_idle", "Crow"),
                    new Option("bat", "Bat"), new Option("flower_pot_1", "Flower pot"), new Option("cat_tree", "Cat tree"),
                    new Option("bobber", "Bobber"), new Option("pumpkin", "Pumpkin"));
            case BOTTOM_RIGHT -> List.of(new Option("", "None"), new Option("heart", "Heart"), new Option("chicken_idle", "Chicken"),
                    new Option("crow_walk", "Walking crow"), new Option("flower_pot_1", "Flower pots"), new Option("vines_1", "Vines"),
                    new Option("cat_tree", "Cat tree"), new Option("fish", "Fish"), new Option("pumpkin", "Pumpkin"));
        };
    }

    /// The name of a corner's picture ("None" for none or one this version doesn't have).
    public static String nameOf(Corner corner, String id) {
        for (Option o : options(corner)) if (o.id().equals(id == null ? "" : id)) return o.name();
        return "None";
    }

    /// The next (or previous) picture for a corner.
    public static String step(Corner corner, String id, int by) {
        List<Option> all = options(corner);
        int at = 0;
        for (int i = 0; i < all.size(); i++) if (all.get(i).id().equals(id == null ? "" : id)) at = i;
        return all.get(Math.floorMod(at + by, all.size())).id();
    }

    private static boolean known(Corner corner, String id) {
        if (id == null || id.isEmpty()) return false;
        for (Option o : options(corner)) if (o.id().equals(id)) return true;
        return false;
    }

    /// The four corners' pictures round a box whose border's outer corners are (left, top) and (right, bottom).
    public static void draw(GuiGraphics g, String topLeft, String topRight, String bottomLeft, String bottomRight,
                            int left, int top, int right, int bottom) {
        piece(g, Corner.TOP_LEFT, topLeft, left - 24, top - 24);
        piece(g, Corner.TOP_RIGHT, topRight, right - 40, top - 24);
        piece(g, Corner.BOTTOM_LEFT, bottomLeft, left - 24, bottom - 40);
        piece(g, Corner.BOTTOM_RIGHT, bottomRight, right - 40, bottom - 40);
    }

    private static void piece(GuiGraphics g, Corner corner, String id, int x, int y) {
        if (!known(corner, id)) return;
        Identifier sprite = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "flair/" + corner.folder + "/" + id);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 64, 64);
    }
}
