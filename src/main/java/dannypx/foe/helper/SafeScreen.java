package dannypx.foe.helper;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

/// FOE+'s screen listeners behind the safety net (see [Safe]): a failure in one is logged once and
/// skipped; clicks and scrolls carry on as if FOE+ weren't there.
public final class SafeScreen {
    private SafeScreen() {}

    public static ScreenEvents.AfterRender render(String where, ScreenEvents.AfterRender r) {
        return (s, g, mx, my, d) -> Safe.draw(where, g, () -> r.afterRender(s, g, mx, my, d));
    }

    public static ScreenEvents.AfterTick tick(String where, ScreenEvents.AfterTick r) {
        return s -> Safe.run(where, () -> r.afterTick(s));
    }

    public static ScreenEvents.Remove remove(String where, ScreenEvents.Remove r) {
        return s -> Safe.run(where, () -> r.onRemove(s));
    }

    public static ScreenMouseEvents.AfterMouseClick click(String where, ScreenMouseEvents.AfterMouseClick r) {
        return (s, e, consumed) -> Safe.get(where, () -> r.afterMouseClick(s, e, consumed), consumed);
    }

    public static ScreenMouseEvents.AfterMouseScroll scroll(String where, ScreenMouseEvents.AfterMouseScroll r) {
        return (s, x, y, h, v, consumed) -> Safe.get(where, () -> r.afterMouseScroll(s, x, y, h, v, consumed), consumed);
    }

    public static ScreenMouseEvents.AllowMouseClick allowClick(String where, ScreenMouseEvents.AllowMouseClick r) {
        return (s, e) -> Safe.get(where, () -> r.allowMouseClick(s, e), true);
    }

    public static ScreenMouseEvents.AllowMouseRelease allowRelease(String where, ScreenMouseEvents.AllowMouseRelease r) {
        return (s, e) -> Safe.get(where, () -> r.allowMouseRelease(s, e), true);
    }

    public static ScreenMouseEvents.AllowMouseDrag allowDrag(String where, ScreenMouseEvents.AllowMouseDrag r) {
        return (s, e, dx, dy) -> Safe.get(where, () -> r.allowMouseDrag(s, e, dx, dy), true);
    }
}
