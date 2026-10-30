package dannypx.foe.type;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/// Note-block sounds FOE offers for warnings (ported from FOECollab's NotificationSoundHandler).
public enum WarningSound {
    PLING(SoundEvents.NOTE_BLOCK_PLING),
    BASS(SoundEvents.NOTE_BLOCK_BASS),
    BELL(SoundEvents.NOTE_BLOCK_BELL),
    BIT(SoundEvents.NOTE_BLOCK_BIT),
    CHIME(SoundEvents.NOTE_BLOCK_CHIME),
    DIDGERIDOO(SoundEvents.NOTE_BLOCK_DIDGERIDOO),
    COW_BELL(SoundEvents.NOTE_BLOCK_COW_BELL),
    FLUTE(SoundEvents.NOTE_BLOCK_FLUTE),
    GUITAR(SoundEvents.NOTE_BLOCK_GUITAR),
    HARP(SoundEvents.NOTE_BLOCK_HARP);

    public final Holder<SoundEvent> sound;

    WarningSound(Holder<SoundEvent> sound) {
        this.sound = sound;
    }
}
