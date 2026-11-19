package dannypx.foe.type;

/// What the pet panel shows of the pet's XP, on its level line.
public enum PetXpStyle {
    /// The level, the bar and "(251.50K/470.00K)".
    BAR_AND_NUMBERS,
    /// The level and the bar.
    BAR,
    /// No XP: the level goes after the pet's name.
    HIDDEN
}
