package dannypx.foe.type;

import me.fzzyhmstrs.fzzy_config.util.EnumTranslatable;

import java.util.List;

public enum Alignment implements EnumTranslatable {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    CENTER;

    /// The four corners plus top-centre and bottom-centre (the box stays centred on its spot).
    public static List<Alignment> getCornersAndCenters() {
        return List.of(TOP_LEFT, TOP, TOP_RIGHT, BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT);
    }

    public static List<Alignment> getHorizontal() {
        return List.of(LEFT, RIGHT);
    }

    public static List<Alignment> getVertical() {
        return List.of(TOP, BOTTOM);
    }

    public static List<Alignment> getTopCorners() {
        return List.of(TOP_LEFT, TOP_RIGHT);
    }

    public static List<Alignment> getBottomHCorners() {
        return List.of(BOTTOM_LEFT, BOTTOM_RIGHT);
    }

    public static List<Alignment> getTop() {
        return List.of(TOP_LEFT, TOP, TOP_RIGHT);
    }
    public static List<Alignment> getBottom() {
        return List.of(BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT);
    }

    public static List<Alignment> getCorners() {
        return List.of(TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT);
    }

    public static List<Alignment> getVerticalSides() {
        return List.of(TOP_LEFT, TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT, LEFT);
    }

    public static List<Alignment> getSides() {
        return List.of(LEFT, TOP, RIGHT, BOTTOM);
    }

    public static List<Alignment> getAll() {
        return List.of(TOP_LEFT, TOP, TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT, LEFT);
    }
}
