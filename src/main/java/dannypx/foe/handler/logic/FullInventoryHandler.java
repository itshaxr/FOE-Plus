package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.TabOverlayHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/**
 * FOE's "inventory almost full" warning: counts empty slots in your inventory and hotbar, and
 * plays a warning sound every few seconds while you're at or below the threshold.
 * Ported from FOECollab's FullInventoryHandler / NotificationSoundHandler (AGPL-3.0).
 */
public class FullInventoryHandler {
    private static FullInventoryHandler INSTANCE = new FullInventoryHandler();

    private int slotsLeft = 36;
    private long lastSound = 0L;

    public static FullInventoryHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new FullInventoryHandler();
        }
        return INSTANCE;
    }

    public int getSlotsLeft() {
        return slotsLeft;
    }

    public boolean isAlmostFull() {
        return Configs.hudConfig.showInventoryWarning.get()
                && slotsLeft <= Configs.hudConfig.inventoryWarningThreshold.get();
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int empty = 0;
        for (int i = 0; i < 36; i++) { // 0-8 hotbar, 9-35 main inventory
            if (mc.player.getInventory().getNonEquipmentItems().get(i).isEmpty()) empty++;
        }
        slotsLeft = empty;

        // Sound, like FOE: not in the hub or on Crew Island.
        if (isAlmostFull()
                && Configs.hudConfig.inventoryWarningSound.get()
                && TabOverlayHandler.instance().isInInstance()) {
            long now = System.currentTimeMillis();
            if (now - lastSound > Configs.hudConfig.inventoryWarningSoundSeconds.get() * 1000L) {
                lastSound = now;
                // Played twice for extra loudness, as FOE does (FOE boosts to 150%).
                mc.getSoundManager().play(SimpleSoundInstance.forUI(Configs.hudConfig.inventoryWarningSoundType.get().sound.value(), 1f, 1f));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(Configs.hudConfig.inventoryWarningSoundType.get().sound.value(), 1f, 0.5f));
            }
        }
    }
}
