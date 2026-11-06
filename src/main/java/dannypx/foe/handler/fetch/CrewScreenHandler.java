package dannypx.foe.handler.fetch;

import dannypx.foe.handler.Handler;
import dannypx.foe.handler.logic.CrewHandler;
import dannypx.foe.handler.logic.NotifierHandler;
import dannypx.foe.handler.store.CrewDataHandler;
import dannypx.foe.handler.store.ProfileDataHandler;
import dannypx.foe.type.tuple.Pair;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CrewScreenHandler extends Handler {
    private static CrewScreenHandler INSTANCE = new CrewScreenHandler();

    public static CrewScreenHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new CrewScreenHandler();
        }
        return INSTANCE;
    }

    //region Fields
    //endregion

    //region Methods
    public void checkCrewInfo(ChestMenu chestMenu) {
        ItemStack crewInfoStack = chestMenu.getSlot(13).getItem();
        if(!ScoreboardHandler.instance().getCrew().getString().isBlank()
                && crewInfoStack.get(DataComponents.LORE) != null
        ) {
            List<Component> componentList = crewInfoStack.get(DataComponents.LORE).lines();
            if(componentList.size() > 3
                    && componentList.get(2).getSiblings().size() > 4
                    && Objects.equals(componentList.get(2).getSiblings().get(3).getString(), ScoreboardHandler.instance().getCrew().getString())
            ) {
                Map<UUID, Pair<String, ItemStack>> crewMembers = new HashMap<>();

                for (int i = 28; i < 44; i++) {
                    ItemStack itemStack = chestMenu.getSlot(i).getItem();
                    if(itemStack.getItem() == Items.PLAYER_HEAD
                            && itemStack.get(DataComponents.PROFILE) != null
                    ) {
                        var profile = itemStack.get(DataComponents.PROFILE).partialProfile();
                        String name = profile.name();
                        // Some heads come without a name in their profile: use the one on the item.
                        if (name == null || name.isBlank()) name = nameFromText(itemStack.getHoverName().getString());
                        UUID id = profile.id();
                        // A head with only a skin has the all-zero id: every such head would land on
                        // one entry (one blank member, the rest lost). Find the player by name instead.
                        if (id == null || net.minecraft.util.Util.NIL_UUID.equals(id)) {
                            if (name == null || name.isBlank()) continue;
                            id = idForName(name);
                        }
                        crewMembers.put(id, Pair.of(name == null ? "" : name, itemStack));
                    }
                }

                if(!crewMembers.isEmpty()) {
                    CrewDataHandler.instance().updateCrewList(crewMembers);
                    CrewHandler.instance().updateCrewOrderedList(crewMembers);

                    if(!ProfileDataHandler.instance().getProfileData().hasImportedCrew) {
                        NotifierHandler.instance().notifyImportCrewCompleted();
                    }

                    ProfileDataHandler.instance().updateImportCrew(true);
                }
            }
        }
    }
    /// The player's id from the tab list, else from the crew saved before, else one made from the
    /// name (swapped for the real one when they come online).
    private static UUID idForName(String name) {
        var conn = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (conn != null) {
            var info = conn.getPlayerInfo(name);
            if (info != null) return info.getProfile().id();
        }
        for (var e : CrewDataHandler.instance().getCrewData().crewList.entrySet()) {
            String saved = e.getValue().value1();
            if (saved != null && saved.equalsIgnoreCase(name) && !net.minecraft.util.Util.NIL_UUID.equals(e.getKey())) return e.getKey();
        }
        return nameId(name);
    }

    /// The made-up id for a name (the same one Minecraft uses for offline players).
    public static UUID nameId(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static final java.util.regex.Pattern MC_NAME = java.util.regex.Pattern.compile("[A-Za-z0-9_]{3,16}");

    /// "[ADMIRAL] WatDaFrickle" (or with rank glyphs and colours) → "WatDaFrickle": the last
    /// Minecraft-style name in the text, or "" when there's none. Public for the crew list.
    public static String nameFromText(String text) {
        if (text == null) return "";
        String plain = net.minecraft.ChatFormatting.stripFormatting(text);
        if (plain == null) return "";
        plain = plain.replaceAll("[\\uE000-\\uF8FF]", " ");
        java.util.regex.Matcher m = MC_NAME.matcher(plain);
        String last = "";
        while (m.find()) last = m.group();
        return last;
    }
    //endregion

    //region Dev
    /// Field, Pair<Value, Tooltip>
    @Override
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
                "key", Pair.of(Component.literal("value"), Component.empty())
        );
    }
    //endregion
}
