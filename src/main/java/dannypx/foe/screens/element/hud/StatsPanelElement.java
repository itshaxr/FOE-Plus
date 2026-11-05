package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.FishSessionHandler;
import dannypx.foe.handler.store.ConstantDataHandler;
import dannypx.foe.handler.store.StatsDataHandler;
import dannypx.foe.item.TagObject;
import dannypx.foe.item.FishTagObject;
import java.util.Map;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/// All-time fishing stats in FOE's panel style: fish caught, fish/hour, fish time, quests, pets,
/// shards, then counts per rarity and variant with their share and dry streak. Layout ported from
/// FOECollab's FishTrackerHudHandler (AGPL-3.0); the numbers come from FOE-R's stats tracker.
public class StatsPanelElement extends PanelElement {
    private static final List<String> RARITIES = List.of("common", "rare", "epic", "legendary", "mythical");
    /// The GIGANTIC badge: a glyph image in this mod's tag font, drawn like the server's rarity
    /// badges (the text shadow gives it the same dark drop shadow).
    public static final Component GIGANTIC_BADGE = Component.literal("\uE910").withStyle(style -> style
            .withColor(ChatFormatting.WHITE)
            .withFont(new net.minecraft.network.chat.FontDescription.Resource(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(dannypx.foe.FishOnMCExtras.MOD_ID, "tags"))));
    private Component giganticLine = null;

    private static final List<String> VARIANTS = List.of("albino", "melanistic", "trophy", "fabled");

    public StatsPanelElement() {
        this(false);
    }

    public StatsPanelElement(boolean isCopy) {
        super(Configs.hudConfig.statsPanelXPosition.get() / 100f,
                Configs.hudConfig.statsPanelYPosition.get() / 100f,
                Configs.hudConfig.statsPanelAlignment.get(),
                Component.literal("All-Time Stats Panel"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showStatsPanel.get(); }
    @Override protected float configX() { return Configs.hudConfig.statsPanelXPosition.get() / 100f; }
    @Override protected float configY() { return Configs.hudConfig.statsPanelYPosition.get() / 100f; }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.statsPanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.statsPanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.statsPanelOpacity.get(); }

    @Override
    protected Component title() {
        return Component.literal("ᴀʟʟ-ᴛɪᴍᴇ ѕᴛᴀᴛѕ").withStyle(ChatFormatting.BOLD);
    }

    @Override
    protected List<Component> lines() {
        StatsDataHandler.StatsDataModel stats = StatsDataHandler.instance().getStatsData();
        FishSessionHandler session = FishSessionHandler.instance();
        List<Component> lines = new ArrayList<>();
        int fishTotal = stats.fishTotal;

        lines.add(join(label("ꜰɪѕʜ ᴄᴀᴜɢʜᴛ: "), white(String.valueOf(fishTotal))));
        lines.add(join(label("ꜰɪѕʜ/ʜᴏᴜʀ: "), white(String.format("%.1f", session.getFishPerHour()))));
        lines.add(join(label("ꜰɪѕʜ ᴛɪᴍᴇ: "), white(formatTime(session.getActiveTimeMs()))));
        lines.add(Component.empty());

        int shards = 0;
        for (Map.Entry<String, StatsDataHandler.Stat<Integer, Integer>> entry : stats.itemData.entrySet()) {
            if (entry.getKey().toLowerCase().contains("shard")) shards += entry.getValue().amount();
        }
        lines.add(join(label("ꞯᴜᴇѕᴛѕ: "), white(String.valueOf(stats.questsCompleted))));
        lines.add(join(label("ᴘᴇᴛѕ: "), white(String.valueOf(stats.petTotal))));
        lines.add(join(label("ѕʜᴀʀᴅѕ: "), white(String.valueOf(shards))));
        // Infusion Capsules, counted from the server's RARE CATCH line. Just the amount for now:
        // the capsule dry streak on this panel wasn't counting up properly.
        if (Configs.hudConfig.statsPanelShowCapsules.get()) {
            StatsDataHandler.Stat<Integer, Integer> capsules = stats.rareCatchData == null ? null : stats.rareCatchData.get("infusion_capsule");
            int amount = capsules == null || capsules.amount() == null ? 0 : capsules.amount();
            lines.add(join(label("ᴄᴀᴘѕᴜʟᴇѕ: "), white(String.valueOf(amount))));
        }
        lines.add(Component.empty());

        Map<String, StatsDataHandler.Stat<Integer, Integer>> rarities = stats.fishData.getOrDefault(TagObject.RARITY, Map.of());
        for (String rarity : RARITIES) {
            int amount = rarities.containsKey(rarity) ? rarities.get(rarity).amount() : 0;
            lines.add(join(ConstantDataHandler.instance().getConstantFishComponent(rarity),
                    white(" " + amount + " "),
                    label("(" + percent(amount, fishTotal) + "%)")));
        }
        // Gigantic size: count, share and dry streak, like the variants.
        if (Configs.hudConfig.statsPanelShowGigantic.get()) {
            StatsDataHandler.Stat<Integer, Integer> gigantic = stats.fishData
                    .getOrDefault(FishTagObject.FISH_SIZE, Map.of()).get("gigantic");
            int amount = gigantic == null ? 0 : gigantic.amount();
            int dryStreak = gigantic == null ? fishTotal : fishTotal - gigantic.caughtOn();
            giganticLine = join(GIGANTIC_BADGE,
                    white(" " + amount + " "),
                    label("(" + percent(amount, fishTotal) + "%) "),
                    Component.literal("(" + dryStreak + ")").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(giganticLine);
        }
        lines.add(Component.empty());

        Map<String, StatsDataHandler.Stat<Integer, Integer>> variants = stats.fishData.getOrDefault(FishTagObject.VARIANT, Map.of());
        for (String variant : VARIANTS) {
            StatsDataHandler.Stat<Integer, Integer> stat = variants.get(variant);
            int amount = stat == null ? 0 : stat.amount();
            int dryStreak = stat == null ? fishTotal : fishTotal - stat.caughtOn();
            lines.add(join(ConstantDataHandler.instance().getConstantFishComponent(variant),
                    white(" " + amount + " "),
                    label("(" + percent(amount, fishTotal) + "%) "),
                    Component.literal("(" + dryStreak + ")").withStyle(ChatFormatting.DARK_GRAY)));
        }

        return lines;
    }

    private static Component white(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    /// 39.7, 0.02, 0.002: more decimals for rarer things, like FOE.
    private static String percent(int amount, int total) {
        if (total <= 0) return "0";
        double pct = amount * 100.0 / total;
        if (pct == 0) return "0";
        if (pct < 0.01) return String.format("%.3f", pct);
        if (pct < 1) return String.format("%.2f", pct);
        return String.format("%.1f", pct);
    }

    @Override
    protected List<Component> previewLines() {
        return lines();
    }
}
