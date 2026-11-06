package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.BossEventHandler;
import dannypx.foe.handler.fetch.TabOverlayHandler;
import dannypx.foe.helper.FoeLocations;
import dannypx.foe.item.PetTagObject;
import dannypx.foe.item.ValidateItem;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Checks that the equipped pet is the exact pet of the location you are fishing at. Every
 * location has its own pet (Great Lakes: Eagle, Danube River: Wolf, ...), and a pet only gives
 * its location bonus there, so sharing a climate is not enough: an Eagle at the Danube is wrong.
 * <p>
 * The gear warning panel shows the result live; on top of that, every time you arrive at a new
 * location a chat line names the pet to use when yours is wrong.
 */
public class PetLocationHandler {
    private static PetLocationHandler INSTANCE = new PetLocationHandler();

    /// Pet and boss bar data settle a moment after you arrive, so the chat check waits a bit.
    private static final long ARRIVAL_DELAY_MS = 3_000L;

    private FoeLocations.Location lastLocation = null;
    private long checkAt = 0L;

    public static PetLocationHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new PetLocationHandler();
        }
        return INSTANCE;
    }

    /// The fishing location you are at, or null in the hub/crew island/unknown places.
    public static FoeLocations.Location currentLocation() {
        if (!TabOverlayHandler.instance().isInInstance()) return null;
        return FoeLocations.fromName(BossEventHandler.instance().getLocation().getString());
    }

    /// True at Cypress Lake's spawn hub (FOE's box), before the first water.
    public static boolean inCypressHub() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return currentLocation() == FoeLocations.Location.CYPRESS_LAKE
                && FoeLocations.isCypressHub(mc.player.getX(), mc.player.getZ());
    }

    /// Where your gear has to fit: the current location, except Cypress Lake's spawn hub (no
    /// warnings until you reach the water, like FOE).
    public static FoeLocations.Location fishingLocation() {
        return inCypressHub() ? null : currentLocation();
    }

    /// The location a pet belongs to, from its NBT ("location", or its species as a fallback).
    public static FoeLocations.Location locationOf(PetTagObject pet) {
        FoeLocations.Location byId = FoeLocations.fromId(pet.getString("location"));
        if (byId != null) return byId;
        String species = pet.getString("pet");
        for (FoeLocations.Location location : FoeLocations.Location.values()) {
            if (location.petId.equalsIgnoreCase(species)) return location;
        }
        return null;
    }

    /// True when a pet is equipped and it is not the pet of {@code location}.
    public static boolean isWrongPet(FoeLocations.Location location) {
        if (location == null || !InventoryHandler.instance().hasPet()) return false;
        FoeLocations.Location petLocation = locationOf(InventoryHandler.instance().getCurrentPet());
        return petLocation != null && petLocation != location;
    }

    /// True when the right pet for {@code location} is somewhere in your inventory or hotbar.
    public static boolean hasPetFor(FoeLocations.Location location) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || location == null) return false;
        for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) continue;
            Pair<Boolean, PetTagObject> pet = ValidateItem.isPet(stack);
            if (pet.value1() && locationOf(pet.value2()) == location) return true;
        }
        return false;
    }

    /// "Use a Wolf Pet (you have one in your inventory)." style hint.
    public static Component suggestion(FoeLocations.Location location) {
        return Component.literal("Use a ").withStyle(ChatFormatting.RED)
                .append(Component.literal(location.petName + " Pet").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(hasPetFor(location) ? " (you have one in your inventory)." : " (none in your inventory).")
                        .withStyle(ChatFormatting.RED));
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        FoeLocations.Location here = fishingLocation();
        long now = System.currentTimeMillis();
        if (here != lastLocation) {
            lastLocation = here;
            checkAt = here == null ? 0L : now + ARRIVAL_DELAY_MS;
        }
        if (checkAt != 0L && now >= checkAt) {
            checkAt = 0L;
            if (Configs.hudConfig.showGearWarning.get() && isWrongPet(here)) {
                PetTagObject pet = InventoryHandler.instance().getCurrentPet();
                // [FOE+] WRONG PET Eagle Pet · Danube River needs a Wolf Pet (in inventory)
                dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.line("WRONG PET", dannypx.foe.helper.FoeChat.RED)
                        .append(pet.getName())
                        .append(dannypx.foe.helper.FoeChat.sep())
                        .append(dannypx.foe.helper.FoeChat.place(displayName(here)))
                        .append(dannypx.foe.helper.FoeChat.text(" needs a "))
                        .append(Component.literal(here.petName + " Pet").withStyle(ChatFormatting.GOLD))
                        .append(dannypx.foe.helper.FoeChat.text(hasPetFor(here) ? " (in inventory)" : " (not in inventory)")));
            }
        }
    }

    private static String displayName(FoeLocations.Location location) {
        String name = BossEventHandler.instance().getLocation().getString().replaceAll("[\\uE000-\\uF8FF]", "").trim();
        return name.isEmpty() ? location.name() : name;
    }
}
