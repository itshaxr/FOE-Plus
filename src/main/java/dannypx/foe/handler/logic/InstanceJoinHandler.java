package dannypx.foe.handler.logic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * "Join instance" on a friend's card: opens /instances and clicks the friend's instance in it for you. If that
 * instance isn't in the menu, the menu stays open so you can pick.
 */
public final class InstanceJoinHandler {
    private static final InstanceJoinHandler INSTANCE = new InstanceJoinHandler();

    public static InstanceJoinHandler instance() {
        return INSTANCE;
    }

    private int wanted = -1;
    private long until = 0L;
    private Object triedOn = null;

    /// The instance's number in "Instance 5" / "instance5", or -1.
    public static int numberOf(String where) {
        if (where == null) return -1;
        java.util.regex.Matcher m = Pattern.compile("(?i)instance\\s*#?\\s*(\\d+)").matcher(where);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    public void join(int number) {
        Minecraft mc = Minecraft.getInstance();
        if (number < 0 || mc.getConnection() == null) return;
        wanted = number;
        until = System.currentTimeMillis() + 6_000L;
        triedOn = null;
        mc.setScreen(null);
        mc.getConnection().sendCommand("instances");
    }

    /// Package-private for tests: the slot showing instance `n` among these {name, lore...} texts, or -1: by its name
    /// ("Instance 5", "instance5", "#5"), else by a lore line naming it.
    static int slotFor(List<List<String>> items, int n) {
        Pattern exact = Pattern.compile("(?i)(?:instance\\s*#?\\s*|#)" + n + "(?!\\d)");
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < items.size(); i++) {
                List<String> texts = items.get(i);
                if (texts.isEmpty()) continue;
                List<String> look = pass == 0 ? texts.subList(0, 1) : texts;
                for (String t : look) if (exact.matcher(t).find()) return i;
            }
        }
        return -1;
    }

    public void tick(Minecraft mc) {
        if (wanted < 0) return;
        if (System.currentTimeMillis() > until || mc.player == null) { wanted = -1; return; }
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs) || cs == triedOn) return;
        var menu = cs.getMenu();
        java.util.List<List<String>> texts = new java.util.ArrayList<>();
        java.util.List<Slot> slots = new java.util.ArrayList<>();
        for (Slot s : menu.slots) {
            if (s.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack st = s.getItem();
            java.util.List<String> t = new java.util.ArrayList<>();
            if (!st.isEmpty()) {
                t.add(plain(st.getHoverName().getString()));
                var lore = st.get(net.minecraft.core.component.DataComponents.LORE);
                if (lore != null) for (Component l : lore.lines()) t.add(plain(l.getString()));
            }
            texts.add(t);
            slots.add(s);
        }
        if (texts.stream().allMatch(List::isEmpty)) return;      // not filled in yet
        triedOn = cs;
        int i = slotFor(texts, wanted);
        int n = wanted;
        wanted = -1;
        if (i < 0) {
            dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.line("INSTANCE", dannypx.foe.helper.FoeChat.GOLD)
                    .append(dannypx.foe.helper.FoeChat.text("Couldn't find Instance " + n + " here, pick it yourself")));
            return;
        }
        mc.gameMode.handleInventoryMouseClick(menu.containerId, menu.slots.indexOf(slots.get(i)), 0, ClickType.PICKUP, mc.player);
    }

    private static String plain(String s) {
        String t = ChatFormatting.stripFormatting(s);
        return t == null ? "" : t.replaceAll("[\\uE000-\\uF8FF]", "").trim().toLowerCase(Locale.ROOT);
    }
}
