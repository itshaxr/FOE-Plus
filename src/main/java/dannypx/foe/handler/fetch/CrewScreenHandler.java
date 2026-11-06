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
    /// What the last /crew screen held, for /foe crew dump (read only: doesn't change the crew).
    private String lastDump = "";

    public String lastDump() {
        return lastDump;
    }

    public void checkCrewInfo(ChestMenu chestMenu) {
        ItemStack crewInfoStack = chestMenu.getSlot(13).getItem();
        lastDump = dump(chestMenu);
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
                        // The server's heads now carry only a skin (all-zero id, no name); the
                        // player is in the label: "GhostZland (Offline)".
                        var profile = itemStack.get(DataComponents.PROFILE).partialProfile();
                        String name = memberName(itemStack.getHoverName().getString());
                        if (name.isEmpty()) name = profile.name() == null ? "" : profile.name();
                        if (name.isEmpty()) continue;
                        UUID id = profile.id();
                        if (id == null || NIL.equals(id)) id = idForName(name);
                        crewMembers.put(id, Pair.of(name, itemStack));
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
    private static final UUID NIL = new UUID(0L, 0L);
    private static final java.util.regex.Pattern MEMBER = java.util.regex.Pattern.compile(
            "([A-Za-z0-9_]{1,16})\\s*\\((?:online|offline)\\)", java.util.regex.Pattern.CASE_INSENSITIVE);

    /// " 퀁    GhostZland (Offline)" → "GhostZland" ("" when it isn't a member's label).
    /// Public for tests.
    public static String memberName(String label) {
        if (label == null) return "";
        String plain = net.minecraft.ChatFormatting.stripFormatting(label);
        if (plain == null) return "";
        java.util.regex.Matcher m = MEMBER.matcher(plain);
        return m.find() ? m.group(1) : "";
    }

    /// The player's id: from the tab list when they're on, else the one saved before, else one
    /// made from the name (swapped for the real one once they come online).
    private static UUID idForName(String name) {
        var conn = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (conn != null) {
            var info = conn.getPlayerInfo(name);
            if (info != null) return info.getProfile().id();
        }
        for (var e : CrewDataHandler.instance().getCrewData().crewList.entrySet()) {
            if (name.equalsIgnoreCase(e.getValue().value1()) && !NIL.equals(e.getKey())) return e.getKey();
        }
        return nameId(name);
    }

    /// The made-up id for a name (the same one Minecraft uses for offline players).
    public static UUID nameId(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /// Everything on the /crew screen's info item and member heads, as text.
    private static String dump(ChestMenu menu) {
        StringBuilder out = new StringBuilder("FOE+ /crew dump\n");
        for (int i = 0; i < menu.slots.size() && i < 54; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.isEmpty() || (i != 13 && stack.getItem() != Items.PLAYER_HEAD)) continue;
            out.append("\n[slot ").append(i).append("] ").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    .append("\nName: ").append(stack.getHoverName().getString());
            var lore = stack.get(DataComponents.LORE);
            if (lore != null) lore.lines().forEach(l -> out.append("\nLore: ").append(l.getString()));
            var profile = stack.get(DataComponents.PROFILE);
            if (profile != null) {
                var p = profile.partialProfile();
                out.append("\nProfile: id=").append(p.id()).append(" name='").append(p.name())
                        .append("' optName=").append(profile.name().orElse("(none)"))
                        .append(" properties=").append(p.properties().size());
            }
            var data = stack.get(DataComponents.CUSTOM_DATA);
            if (data != null) out.append("\nData: ").append(data.copyTag());
            out.append('\n');
        }
        return out.toString();
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
