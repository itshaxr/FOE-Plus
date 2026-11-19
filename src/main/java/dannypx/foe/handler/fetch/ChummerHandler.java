package dannypx.foe.handler.fetch;

import dannypx.foe.handler.Handler;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ChummerHandler extends Handler {
    private static ChummerHandler INSTANCE = new ChummerHandler();

    public static ChummerHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new ChummerHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private final double RADIUS_SQ = 20 * 20;
    private final Map<Integer, ChummerInfo> CHUMMERS = new HashMap<>();

    public Collection<ChummerInfo> getChummers() {
        return CHUMMERS.values();
    }
    //endregion

    //region Methods
    public void onEntityUnload(Entity entity, ClientLevel clientLevel) {
        CHUMMERS.remove(entity.getId());
    }

    public void onLeave() {
        CHUMMERS.clear();
    }

    public void onTextUpdate(Display.TextDisplay entity, Component text) {
        ChummerInfo info = ChummerInfo.tryParse(entity.position(), text);
        if (info == null) {
            CHUMMERS.remove(entity.getId());
        } else {
            // the most time it's been seen with: how long it ran for, for the panel's bar
            ChummerInfo before = CHUMMERS.get(entity.getId());
            info.fullMs = Math.max(info.remainingAtParseMs(), before != null ? before.fullMs : 0L);
            if (before != null && before.pos.distanceToSqr(info.pos) < 1.0) info.icon = before.icon;
            CHUMMERS.put(entity.getId(), info);
        }
    }

    /// The chummer's own picture: the item shown where it sits (an item display, or an armor stand's head, within a few
    /// blocks of its sign), looked for at most once a second until found; a bucket of fish until then.
    public net.minecraft.world.item.ItemStack icon(ChummerInfo info) {
        if (!info.icon.isEmpty()) return info.icon;
        long now = System.currentTimeMillis();
        if (now - info.iconLookedAt >= 1000L && minecraft.level != null) {
            info.iconLookedAt = now;
            net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(info.getPos(), info.getPos()).inflate(3.0);
            double best = Double.MAX_VALUE;
            for (Entity e : minecraft.level.getEntitiesOfClass(Entity.class, around,
                    e -> e instanceof Display.ItemDisplay || e instanceof net.minecraft.world.entity.decoration.ArmorStand)) {
                net.minecraft.world.item.ItemStack stack = e instanceof Display.ItemDisplay d ? d.getItemStack()
                        : ((net.minecraft.world.entity.decoration.ArmorStand) e).getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                double dist = e.position().distanceToSqr(info.getPos());
                if (stack != null && !stack.isEmpty() && dist < best) { best = dist; info.icon = stack.copy(); }
            }
        }
        if (!info.icon.isEmpty()) return info.icon;
        if (fallbackIcon == null) fallbackIcon = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COD_BUCKET);
        return fallbackIcon;
    }

    private static net.minecraft.world.item.ItemStack fallbackIcon = null;

    /// The chummer whose range you're standing in (the nearest), or null.
    public ChummerInfo inRange() {
        ChummerInfo c = getChummer();
        return c != null && c.isInRange() ? c : null;
    }

    public ChummerInfo getChummer() {
        return minecraft.player != null ? closest(minecraft.player.position()) : null;
    }

    public ChummerInfo closest(Vec3 from) {
        ChummerInfo best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (ChummerInfo info : getChummers()) {
            double dx = info.getPos().x - from.x;
            double dz = info.getPos().z - from.z;
            double distSq = dx * dx + dz * dz;

            info.setInRange(distSq <= RADIUS_SQ);

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = info;
            }
        }
        return best;
    }
    //endregion

    //region Model
    public static final class ChummerInfo {
        private final Vec3 pos;
        private final String rarity;
        private final int minutes;
        private final int seconds;
        private final String player;
        private boolean inRange;
        /// When its text was read, its bonus line ("+130 Bite Speed", "" if it has none), and the most time it's
        /// been seen with.
        private final long parsedAt = System.currentTimeMillis();
        private String bonus = "";
        long fullMs = 0L;
        net.minecraft.world.item.ItemStack icon = net.minecraft.world.item.ItemStack.EMPTY;
        long iconLookedAt = 0L;

        private ChummerInfo(Vec3 pos, String rarity, int minutes, int seconds, String player) {
            this.pos = pos;
            this.rarity = rarity;
            this.minutes = minutes;
            this.seconds = seconds;
            this.player = player;
        }

        long remainingAtParseMs() {
            return (minutes * 60L + seconds) * 1000L;
        }

        /// The time left now: counted down from what its text last said, so it moves every second.
        public long remainingMs() {
            return Math.max(0L, remainingAtParseMs() - (System.currentTimeMillis() - parsedAt));
        }

        /// The time left as a share of the most it's been seen with (1 when only seen once).
        public float remainingFraction() {
            if (fullMs <= 0L) return 1f;
            return Math.max(0f, Math.min(1f, remainingMs() / (float) fullMs));
        }

        public String getBonus() {
            return bonus;
        }

        public static ChummerInfo tryParse(Vec3 pos, Component raw) {
            String rarity = null;
            int minutes = 0;
            int seconds = 0;
            String player = null;
            String bonus = null;

            if(!raw.getString().contains("Chummer™")) return null;

            String[] lines = raw.getString().split("\n");
            if (lines.length > 1) rarity = lines[1].trim();

            for (String line : lines) {
                line = line.trim();

                if (line.startsWith("Activated by ")) {
                    player = line.substring("Activated by ".length()).trim();
                } else if (line.startsWith("+") && bonus == null) {
                    bonus = line;
                } else if (line.endsWith(" Remaining")) {
                    String[] parts = line.substring(0, line.length() - " Remaining".length()).split(" ");
                    try {
                        for (String part : parts) {
                            int value = Integer.parseInt(part.substring(0, part.length() - 1));
                            switch (part.charAt(part.length() - 1)) {
                                case 'm' -> minutes = value;
                                case 's' -> seconds = value;
                            }
                        }
                    } catch (NumberFormatException | StringIndexOutOfBoundsException ignored) {}
                }
            }

            if (rarity == null  || player == null) return null;
            ChummerInfo info = new ChummerInfo(pos, rarity, minutes, seconds, player);
            info.bonus = bonus == null ? "" : bonus.replaceAll("[\\uE000-\\uF8FF]", "").trim();
            return info;
        }

        public String getPlayer() {
            return player;
        }

        public String getRarity() {
            return rarity;
        }

        public int getMinutes() {
            return minutes;
        }

        public int getSeconds() {
            return seconds;
        }

        public Vec3 getPos() {
            return pos;
        }

        public boolean isInRange() {
            return inRange;
        }

        public void setInRange(boolean inRange) {
            this.inRange = inRange;
        }
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
