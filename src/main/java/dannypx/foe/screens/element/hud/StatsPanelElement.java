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

    /// FOE+'s own little icons (font fishonmcextras:stat_icons), white so the line's colour tints them.
    private static final net.minecraft.network.chat.FontDescription ICON_FONT = new net.minecraft.network.chat.FontDescription.Resource(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(dannypx.foe.FishOnMCExtras.MOD_ID, "stat_icons"));
    static final String ROD = "\uE010", PAW = "\uE011", SHARD = "\uE012", CAPSULE = "\uE013", BOLT = "\uE014";
    static final int CAUGHT_COLOR = 0x00AA00, PET_COLOR = 0xFF55FF, SHARD_COLOR = 0xFFAA00, CAPSULE_COLOR = 0xFF5555, BOTTLE_COLOR = 0xFFFF55;

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

        StatsDataHandler handler = StatsDataHandler.instance();
        int capsules = handler.rareCatchAmount(dannypx.foe.handler.logic.RareCatchHandler.INFUSION_CAPSULE);
        int bottles = handler.rareCatchAmount(dannypx.foe.handler.logic.RareCatchHandler.LIGHTNING_BOTTLE);

        // Total Caught, with the share of your catches that were fish (pets, capsules and bottles
        // are the other things you pull).
        lines.add(join(total("Caught", CAUGHT_COLOR, ROD), white(String.valueOf(fishTotal) + " "),
                Component.literal(fishShare(fishTotal, stats.petTotal + capsules + bottles)).withStyle(ChatFormatting.GREEN)));
        lines.add(join(plain("Fish/Hour "), white(String.format("%.1f", session.getFishPerHour()))));
        lines.add(join(plain("Fish Time "), white(formatTime(session.getActiveTimeMs()))));
        lines.add(join(plain("Total Quests "), white(String.valueOf(stats.questsCompleted))));
        lines.add(Component.empty());

        lines.add(totalLine("Pets", PET_COLOR, PAW, stats.petTotal, handler.petDryStreak()));
        lines.add(totalLine("Shards", SHARD_COLOR, SHARD, handler.shardTotal(), handler.shardDryStreak()));
        if (Configs.hudConfig.statsPanelShowCapsules.get()) {
            lines.add(totalLine("Infusion", CAPSULE_COLOR, CAPSULE, capsules,
                    handler.rareCatchStreak(dannypx.foe.handler.logic.RareCatchHandler.INFUSION_CAPSULE)));
        }
        if (Configs.hudConfig.statsPanelShowBottles.get()) {
            lines.add(totalLine("Bottle", BOTTLE_COLOR, BOLT, bottles,
                    handler.rareCatchStreak(dannypx.foe.handler.logic.RareCatchHandler.LIGHTNING_BOTTLE)));
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

    /// "Total Pets 🐾 " : "Total" in grey, the name and its icon in the item's colour.
    private static Component total(String name, int color, String icon) {
        return join(plain("Total "), Component.literal(name + " ").withColor(color),
                Component.literal(icon).withStyle(style -> style.withFont(ICON_FONT).withColor(color)),
                Component.literal(" "));
    }

    /// "Total Pets 🐾 1721 (657)": amount, and the dry streak in brackets ("?" until it's known).
    static Component totalLine(String name, int color, String icon, int amount, int dryStreak) {
        return join(total(name, color, icon), white(amount + " "),
                Component.literal("(" + (dryStreak < 0 ? "?" : String.valueOf(dryStreak)) + ")").withStyle(ChatFormatting.GRAY));
    }

    private static Component plain(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    /// 99.73%: fish out of everything you've pulled. Package-private for tests.
    static String fishShare(int fish, int other) {
        int all = fish + Math.max(0, other);
        if (all <= 0) return "0%";
        return String.format(java.util.Locale.ROOT, "%.2f%%", fish * 100.0 / all);
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
