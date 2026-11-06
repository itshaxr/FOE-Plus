package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.RareCatchHandler;
import dannypx.foe.handler.logic.SessionStatsHandler;
import dannypx.foe.handler.store.ConstantDataHandler;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static dannypx.foe.screens.element.hud.StatsPanelElement.*;

/// The All-Time Stats panel for this session only: fish caught (and the share that were fish),
/// fish/hour and fish time this session, quests, pets, shards, capsules and bottles, then each
/// rarity, gigantic and each variant with their share and the fish since the last one this
/// session. Counted from the catches themselves and cleared when you log out.
public class SessionStatsPanelElement extends PanelElement {
    public SessionStatsPanelElement() {
        this(false);
    }

    public SessionStatsPanelElement(boolean isCopy) {
        super(Configs.hudConfig.sessionStatsPanelXPosition.get() / 100f,
                Configs.hudConfig.sessionStatsPanelYPosition.get() / 100f,
                Configs.hudConfig.sessionStatsPanelAlignment.get(),
                Component.literal("Session Stats Panel"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showSessionStatsPanel.get(); }
    @Override protected float configX() { return Configs.hudConfig.sessionStatsPanelXPosition.get() / 100f; }
    @Override protected float configY() { return Configs.hudConfig.sessionStatsPanelYPosition.get() / 100f; }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.sessionStatsPanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.sessionStatsPanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.sessionStatsPanelOpacity.get(); }

    @Override
    protected Component title() {
        return Component.literal("ѕᴇѕѕɪᴏɴ ѕᴛᴀᴛѕ").withStyle(ChatFormatting.BOLD);
    }

    @Override
    protected List<Component> lines() {
        SessionStatsHandler s = SessionStatsHandler.instance();
        List<Component> lines = new ArrayList<>();
        int fish = s.fish();
        int capsules = s.rare(RareCatchHandler.INFUSION_CAPSULE);
        int bottles = s.rare(RareCatchHandler.LIGHTNING_BOTTLE);

        lines.add(join(total("Caught", CAUGHT_COLOR, ROD), white(fish + " "),
                Component.literal(fishShare(fish, s.pets() + capsules + bottles)).withStyle(ChatFormatting.GREEN)));
        lines.add(join(plain("Fish/Hour "), white(String.format("%.1f", s.fishPerHour()))));
        lines.add(join(plain("Fish Time "), white(formatTime(s.fishTimeMs()))));
        lines.add(join(plain("Total Quests "), white(String.valueOf(s.quests()))));
        lines.add(Component.empty());

        lines.add(totalLine("Pets", PET_COLOR, PAW, s.pets(), s.since("pet")));
        lines.add(totalLine("Shards", SHARD_COLOR, SHARD, s.shards(), s.since("shard")));
        if (Configs.hudConfig.statsPanelShowCapsules.get()) {
            lines.add(totalLine("Infusion", CAPSULE_COLOR, CAPSULE, capsules, s.since("rare:" + RareCatchHandler.INFUSION_CAPSULE)));
        }
        if (Configs.hudConfig.statsPanelShowBottles.get()) {
            lines.add(totalLine("Bottle", BOTTLE_COLOR, BOLT, bottles, s.since("rare:" + RareCatchHandler.LIGHTNING_BOTTLE)));
        }
        lines.add(Component.empty());

        for (String rarity : RARITIES) {
            int amount = s.rarity(rarity);
            lines.add(join(ConstantDataHandler.instance().getConstantFishComponent(rarity),
                    white(" " + amount + " "), label("(" + percent(amount, fish) + "%)")));
        }
        if (Configs.hudConfig.statsPanelShowGigantic.get()) {
            int amount = s.size("gigantic");
            lines.add(join(GIGANTIC_BADGE, white(" " + amount + " "), label("(" + percent(amount, fish) + "%) "),
                    Component.literal("(" + s.since("size:gigantic") + ")").withStyle(ChatFormatting.DARK_GRAY)));
        }
        lines.add(Component.empty());

        for (String variant : VARIANTS) {
            int amount = s.variant(variant);
            lines.add(join(ConstantDataHandler.instance().getConstantFishComponent(variant),
                    white(" " + amount + " "), label("(" + percent(amount, fish) + "%) "),
                    Component.literal("(" + s.since("variant:" + variant) + ")").withStyle(ChatFormatting.DARK_GRAY)));
        }
        return lines;
    }

    @Override
    protected List<Component> previewLines() {
        return lines();
    }
}
