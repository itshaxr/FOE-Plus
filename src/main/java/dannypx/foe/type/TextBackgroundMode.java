package dannypx.foe.type;

/// Backgrounds behind floating server text (NPC names, signs like "FISH MERCHANT").
/// Some shader packs (Photon) draw them as a solid black box.
public enum TextBackgroundMode {
    /// Hidden only while a shader pack is on.
    AUTO,
    /// Always hidden.
    HIDE,
    /// Always shown, as the server sends them.
    SHOW
}
