package dannypx.foe.screens.element.hud;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.BossEventHandler;
import dannypx.foe.handler.logic.ContestTrackerHandler;
import net.minecraft.client.Minecraft;
import java.util.Locale;
import dannypx.foe.type.Alignment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/// Contest timer, type, location, top 3 and your rank, in FOE's panel style. Ported from
/// FOECollab's ContestHudHandler (AGPL-3.0). Data comes from the /contest chat output.
public class ContestPanelElement extends PanelElement {
    public ContestPanelElement() {
        this(false);
    }

    public ContestPanelElement(boolean isCopy) {
        super(Configs.hudConfig.contestPanelXPosition.get() / 100f,
                Configs.hudConfig.contestPanelYPosition.get() / 100f,
                Configs.hudConfig.contestPanelAlignment.get(),
                Component.literal("Contest Panel"),
                isCopy);
    }

    @Override protected boolean enabled() { return Configs.hudConfig.showContestPanel.get(); }
    @Override protected float configX() { return Configs.hudConfig.contestPanelXPosition.get() / 100f; }
    @Override protected float configY() { return Configs.hudConfig.contestPanelYPosition.get() / 100f; }
    @Override protected Alignment configAlignment() { return Configs.hudConfig.contestPanelAlignment.get(); }
    @Override protected float scale() { return Configs.hudConfig.contestPanelScale.get(); }
    @Override protected int opacityPercent() { return Configs.hudConfig.contestPanelOpacity.get(); }

    @Override
    protected boolean alignTextToAnchor() {
        return false;
    }

    @Override
    protected Component title() {
        return Component.literal("ᴄᴏɴᴛᴇѕᴛ").withStyle(ChatFormatting.BOLD);
    }

    /// The hub counts as Cypress Lake for contests, like in FOE.
    private static String normalizeLocation(String location) {
        String lower = location.toLowerCase(Locale.ROOT).trim();
        if (lower.contains("spawn") || lower.contains("hub")) return "cypress lake";
        return lower;
    }

    @Override
    protected List<Component> lines() {
        ContestTrackerHandler contest = ContestTrackerHandler.instance();
        List<Component> lines = new ArrayList<>();
        String timer = formatMinSec(contest.timeLeft);

        if (!contest.hasData()) {
            if (contest.isContest) {
                lines.add(join(Component.literal("ᴄᴏɴᴛᴇѕᴛ ").withStyle(ChatFormatting.GOLD),
                        label("⏱ "), Component.literal(timer).withStyle(timerColor(contest.timeLeft))));
                lines.add(label("ᴡᴀɪᴛɪɴɢ ɴᴇxᴛ ᴜᴘᴅᴀᴛᴇ..."));
                lines.add(join(label("ᴏʀ ᴅᴏ "), Component.literal("/contest").withStyle(ChatFormatting.AQUA)));
            } else {
                lines.add(join(label("ɴᴇxᴛ ᴄᴏɴᴛᴇѕᴛ ɪɴ: "), Component.literal(timer).withStyle(ChatFormatting.GREEN)));
            }
            return lines;
        }

        // Header: CONTEST (low-high) ⏱ mm:ss, or RESULTS after it ended
        ChatFormatting headerColor = contest.isContest ? ChatFormatting.GOLD : ChatFormatting.GRAY;
        List<Component> header = new ArrayList<>();
        header.add(Component.literal("ᴄᴏɴᴛᴇѕᴛ ").withStyle(headerColor));
        if (contest.levelLow > 0 && contest.levelHigh > 0) {
            header.add(Component.literal("(").withStyle(headerColor));
            header.add(Component.literal(String.valueOf(contest.levelLow)).withStyle(ChatFormatting.LIGHT_PURPLE));
            header.add(label("-"));
            header.add(Component.literal(String.valueOf(contest.levelHigh)).withStyle(ChatFormatting.LIGHT_PURPLE));
            header.add(Component.literal(") ").withStyle(headerColor));
        }
        if (contest.isContest) {
            header.add(label("⏱ "));
            header.add(Component.literal(timer).withStyle(timerColor(contest.timeLeft)));
        } else {
            header.add(label("ʀᴇѕᴜʟᴛѕ"));
        }
        lines.add(join(header.toArray(new Component[0])));
        lines.add(Component.empty());

        Component location = Component.literal(contest.location).withStyle(ChatFormatting.AQUA);
        boolean atLocation = normalizeLocation(contest.location)
                .equals(normalizeLocation(BossEventHandler.instance().getLocation().getString()));

        lines.add(join(label("ᴛʏᴘᴇ: "), Component.literal(contest.type).withStyle(ChatFormatting.WHITE)));
        if (!atLocation) {
            lines.add(Component.literal("⚠ ɴᴏᴛ ɪɴ ᴀᴄᴛɪᴠᴇ ᴀʀᴇᴀ").withStyle(ChatFormatting.YELLOW));
            lines.add(join(label("ᴛᴏ ᴠɪᴇᴡ ᴄᴏɴᴛᴇѕᴛ ᴅᴇᴛᴀɪʟѕ, ɢᴏ ᴛᴏ "), location));
        } else {
            lines.add(join(label("ʟᴏᴄᴀᴛɪᴏɴ: "), location));
            lines.add(Component.empty());
            lines.add(place("\uF060 ", contest.firstName, contest.firstStat));
            lines.add(place("\uF061 ", contest.secondName, contest.secondStat));
            lines.add(place("\uF062 ", contest.thirdName, contest.thirdStat));

            if (Minecraft.getInstance().player != null) {
                lines.add(Component.empty());
                List<Component> rank = new ArrayList<>();
                rank.add(label("ʏᴏᴜʀ ʀᴀɴᴋ: "));
                rank.add(Component.literal(contest.rank).withStyle(ChatFormatting.WHITE));
                if (contest.totalParticipants > 0) {
                    rank.add(Component.literal(" (ᴏᴜᴛ ᴏғ ").withStyle(ChatFormatting.DARK_GRAY));
                    rank.add(label(String.valueOf(contest.totalParticipants)));
                    rank.add(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
                }
                if (!contest.rankStat.isEmpty()) {
                    rank.add(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY));
                    rank.add(label(contest.rankStat));
                    rank.add(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
                }
                lines.add(join(rank.toArray(new Component[0])));
            }
        }

        if (contest.isContest && contest.lastUpdated > 0) {
            lines.add(join(label("ʟᴀѕᴛ ᴜᴘᴅᴀᴛᴇ ᴡᴀѕ "),
                    Component.literal(formatMinSec(System.currentTimeMillis() - contest.lastUpdated)).withStyle(ChatFormatting.GREEN),
                    label(" ᴀɢᴏ")));
        }
        return lines;
    }

    private static Component place(String glyph, String name, String stat) {
        if (name.isEmpty()) {
            return join(Component.literal(glyph).withStyle(ChatFormatting.WHITE), label("ᴜɴᴄʟᴀɪᴍᴇᴅ"));
        }
        return join(Component.literal(glyph).withStyle(ChatFormatting.WHITE),
                Component.literal(name).withStyle(ChatFormatting.WHITE),
                Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY),
                label(stat),
                Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static ChatFormatting timerColor(long timeLeft) {
        if (timeLeft < 5 * 60 * 1000) return ChatFormatting.DARK_RED;
        if (timeLeft <= 10 * 60 * 1000) return ChatFormatting.RED;
        if (timeLeft <= 15 * 60 * 1000) return ChatFormatting.GOLD;
        return ChatFormatting.GREEN;
    }

    @Override
    protected List<Component> previewLines() {
        return List.of(join(label("ɴᴇxᴛ ᴄᴏɴᴛᴇѕᴛ ɪɴ: "), Component.literal("12:34").withStyle(ChatFormatting.GREEN)));
    }
}
