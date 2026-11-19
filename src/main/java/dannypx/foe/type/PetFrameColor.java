package dannypx.foe.type;

/// The colour round the pet's picture in the pet panel and the FOE-R pet box: its rating's (the default) or its rarity's.
public enum PetFrameColor implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
    RATING,
    RARITY;

    @Override
    public String prefix() {
        return "fishonmcextras.pet_frame_color";
    }
}
