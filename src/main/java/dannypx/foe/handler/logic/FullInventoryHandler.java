package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.TabOverlayHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/**
 * FOE's "inventory almost full" warning: counts empty slots in your inventory and hotbar, and plays a warning sound
 * when you get to the threshold, then again only when something happens while it's that full (a catch, a slot
 * filling): never on a timer.
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

    /// The free slots at the last big warning (MAX_VALUE: none shown since it was last fine).
    private int flashedAt = Integer.MAX_VALUE;

    /// "Your inventory is almost full: 2 slots left" / "Your inventory is full!", big and red like the daily quest
    /// warning. Package-private for tests.
    static net.minecraft.network.chat.Component message(int slots) {
        var red = new net.minecraft.ChatFormatting[]{net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD};
        if (slots <= 0) return net.minecraft.network.chat.Component.literal("Your inventory is full!").withStyle(red);
        return net.minecraft.network.chat.Component.literal("Your inventory is almost full: ").withStyle(red)
                .append(net.minecraft.network.chat.Component.literal(String.valueOf(slots)).withStyle(net.minecraft.ChatFormatting.WHITE, net.minecraft.ChatFormatting.BOLD))
                .append(net.minecraft.network.chat.Component.literal(slots == 1 ? " slot left" : " slots left").withStyle(red));
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

        // The big warning at the top (like the daily quest one): when it gets low, and each time another slot fills.
        if (!isAlmostFull() || !TabOverlayHandler.instance().isInInstance()) flashedAt = Integer.MAX_VALUE;
        else if (slotsLeft < flashedAt) {
            flashedAt = slotsLeft;
            if (Configs.hudConfig.inventoryWarningBig.get()) dannypx.foe.handler.renderer.QuestWarning.flash(message(slotsLeft));
        }

        // Sound: not in the hub or on Crew Island. When you get to the warning, and again when a slot fills.
        boolean low = isAlmostFull() && TabOverlayHandler.instance().isInInstance();
        if (!low) {
            wasLow = false;
            lastSlots = slotsLeft;
            return;
        }
        if (!wasLow) {
            wasLow = true;
            lastSlots = slotsLeft;
            sound();
        } else if (slotsLeft < lastSlots) {
            lastSlots = slotsLeft;
            if (Configs.hudConfig.inventoryWarningRepeat.get()) sound();
        } else {
            lastSlots = slotsLeft;
        }
    }

    private boolean wasLow = false;
    private int lastSlots = 36;

    /// You caught something: while the inventory is that full, the warning sounds again (once per catch).
    public void onCatch() {
        if (wasLow && isAlmostFull() && Configs.hudConfig.inventoryWarningRepeat.get()) sound();
    }

    private void sound() {
        long now = System.currentTimeMillis();
        if (!Configs.hudConfig.inventoryWarningSound.get() || now - lastSound < 1_000L) return;
        lastSound = now;
        // which sound: O → Sounds
        dannypx.foe.handler.store.NotifySounds.play("inventory_full");
    }
}
