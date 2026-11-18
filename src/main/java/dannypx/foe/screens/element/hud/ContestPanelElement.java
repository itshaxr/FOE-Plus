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
                        label("⌚ "), Component.literal(timer).withStyle(timerColor(contest.timeLeft))));
                lines.add(label("ᴡᴀɪᴛɪɴɢ ɴᴇxᴛ ᴜᴘᴅᴀᴛᴇ..."));
                lines.add(join(label("ᴏʀ ᴅᴏ "), Component.literal("/contest").withStyle(ChatFormatting.AQUA)));
            } else {
                lines.add(join(label("ɴᴇxᴛ ᴄᴏɴᴛᴇѕᴛ ɪɴ: "), Component.literal(timer).withStyle(ChatFormatting.GREEN)));
            }
            return lines;
        }

        lines.addAll(contestLines(contest.type, contest.typeStyled, contest.location, contest.isContest, contest.timeLeft,
                contest.levelLow, contest.levelHigh, targetFish(contest),
                normalizeLocation(contest.location).equals(normalizeLocation(BossEventHandler.instance().getLocation().getString())),
                new String[][]{{contest.firstName, contest.firstStat}, {contest.secondName, contest.secondStat}, {contest.thirdName, contest.thirdStat}},
                Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getGameProfile().name(), contest.rank, contest.totalParticipants, contest.rankStat,
                Configs.hudConfig.showContestBestBait.get() ? bestBaits(contest) : List.of(),
                contest.isContest && contest.lastUpdated > 0 ? System.currentTimeMillis() - contest.lastUpdated : -1));
        return lines;
    }

    static final int GOLD_1 = 0xF2C230, SILVER_2 = 0xC8D0DA, BRONZE_3 = 0xD08A4E;

    /// Package-private for tests: draw the best baits as pictures (else by name).
    static java.util.function.BooleanSupplier PICTURES = () -> Configs.hudConfig.contestBaitPictures.get();
    /// Package-private for tests: list every best bait (else only the best, the rest on hover).
    static java.util.function.BooleanSupplier ALL_BAITS = () -> Configs.hudConfig.contestAllBaits.get();

    /// The contest, kept small:
    /// <pre>
    /// 239-999 ⌚ 03:58
    ///
    /// ʟᴏᴄᴀᴛɪᴏɴ: Toledo ⚠
    /// ᴛʏᴘᴇ: Heaviest Pikes
    /// ʙᴀɪᴛ: Cut Shad › French Fries › Chicken Nugget     (or their pictures, in a row)
    ///
    /// 🥇 Aidanqt (6.22lb)
    /// 🥈 Lumivix7 (5.4lb)
    /// 🥉 Tiek (2.7lb)
    ///
    /// ʏᴏᴜʀ ʀᴀɴᴋ: 🥇 #1 (6.22lb) /11
    ///
    /// ᴜᴘᴅᴀᴛᴇᴅ 00:38 ᴀɢᴏ
    /// </pre>
    /// `youName`: your name, or null to leave your rank out.
    static List<Component> contestLines(String type, String location, boolean running, long timeLeft, int levelLow, int levelHigh,
                                        Component target, boolean atLocation, String[][] top, String youName, String rank,
                                        int participants, String rankStat, List<String> baits, long sinceUpdate) {
        return contestLines(type, null, location, running, timeLeft, levelLow, levelHigh, target, atLocation, top, youName, rank,
                participants, rankStat, baits, sinceUpdate);
    }

    /// `styledType`: the type in the chat's colours, or null for plain white.
    static List<Component> contestLines(String type, Component styledType, String location, boolean running, long timeLeft, int levelLow, int levelHigh,
                                        Component target, boolean atLocation, String[][] top, String youName, String rank,
                                        int participants, String rankStat, List<String> baits, long sinceUpdate) {
        List<Component> lines = new ArrayList<>();
        // levels, then the time
        List<Component> head = new ArrayList<>();
        if (levelLow > 0 && levelHigh > 0) {
            head.add(label("ʟᴠ "));
            head.add(Component.literal(levelLow + "-" + levelHigh).withStyle(ChatFormatting.LIGHT_PURPLE));
            // a little further from the levels (two spaces and two pixels)
            head.add(label("  "));
            head.add(dannypx.foe.helper.NameTagMarker.GAP_WIDE);
            head.add(label("⌚ "));
        } else head.add(label("⌚ "));
        head.add(running ? Component.literal(formatMinSec(timeLeft)).withStyle(timerColor(timeLeft)) : label("ʀᴇѕᴜʟᴛѕ"));
        lines.add(join(head.toArray(new Component[0])));
        lines.add(Component.empty());

        // where, what, and with which bait
        lines.add(join(label("ʟᴏᴄᴀᴛɪᴏɴ: "), dannypx.foe.helper.FoeLocations.styledName(MiniEventPanelElement.shortPlaces(location))));
        lines.add(join(label("ᴛʏᴘᴇ: "), styledType != null && styledType.getString().equals(type) ? styledType : Component.literal(type).withStyle(ChatFormatting.WHITE)));
        int fishAt = lines.size();
        int baitAt = lines.size();
        lines.add(Component.empty());

        // the top three, with the server's medals
        for (int i = 0; i < top.length; i++) lines.add(place(MEDALS[Math.min(i, MEDALS.length - 1)] + " ", top[i][0] == null ? "" : top[i][0], top[i][1] == null ? "" : top[i][1]));

        if (youName != null) {
            lines.add(Component.empty());
            boolean ranked = rank != null && !rank.isBlank() && !rank.equalsIgnoreCase("unranked") && !rank.equals("-");
            List<Component> you = new ArrayList<>();
            you.add(label("ʏᴏᴜʀ ʀᴀɴᴋ: "));
            if (ranked) {
                String n = rank.startsWith("#") ? rank.substring(1).trim() : rank.trim();
                int place = -1;
                try { place = Integer.parseInt(n); } catch (NumberFormatException ignored) {}
                if (place >= 1 && place <= 3) you.add(Component.literal(MEDALS[place - 1] + " ").withStyle(ChatFormatting.WHITE));
                you.add(Component.literal("#" + n).withStyle(ChatFormatting.WHITE));
                if (rankStat != null && !rankStat.isEmpty()) {
                    you.add(Component.literal(" (").withStyle(ChatFormatting.GRAY));
                    you.add(weight(rankStat));
                    you.add(Component.literal(")").withStyle(ChatFormatting.GRAY));
                }
            } else {
                you.add(label("ᴜɴʀᴀɴᴋᴇᴅ"));
            }
            if (participants > 0) you.add(Component.literal(" /" + participants).withStyle(ChatFormatting.DARK_GRAY));
            lines.add(join(you.toArray(new Component[0])));
        }

        if (sinceUpdate >= 0) {
            lines.add(Component.empty());
            lines.add(join(label("ᴜᴘᴅᴀᴛᴇᴅ "), Component.literal(formatMinSec(sinceUpdate)).withStyle(ChatFormatting.GREEN), label(" ᴀɢᴏ")));
        }
        // the fish and baits wrap to the width the rest needs, so the box stays narrow
        int widest = MIN_WIDTH;
        for (Component l : lines) widest = Math.max(widest, WIDTH.applyAsInt(l));
        if (!baits.isEmpty())
            lines.addAll(baitAt, ALL_BAITS.getAsBoolean() || baits.size() == 1 ? baitLines(baits, widest) : List.of(bestBaitLine(baits)));
        if (target != null) lines.addAll(fishAt, fishLines(target, widest));
        return lines;
    }

    /// Package-private for tests: how wide a line is drawn, in pixels.
    static java.util.function.ToIntFunction<Component> WIDTH = c -> Minecraft.getInstance().font.width(c);
    static final int MIN_WIDTH = 120;

    /// The best baits on as few lines as fit `maxWidth`: the first after "ʙᴀɪᴛ:", the rest lined up under it. A
    /// bait and its alternatives ("Worm/Salmon Head") are never split.
    static List<Component> baitLines(List<String> baits, int maxWidth) {
        boolean pictures = PICTURES.getAsBoolean();
        Component head = label("ʙᴀɪᴛ: ");
        int headW = WIDTH.applyAsInt(head);
        int spaceW = Math.max(1, WIDTH.applyAsInt(Component.literal(" ")));
        Component indent = Component.literal(" ".repeat(Math.max(1, Math.round(headW / (float) spaceW))));
        List<Component> out = new ArrayList<>();
        List<Component> line = new ArrayList<>(List.of(head));
        int lineW = headW;
        boolean empty = true;
        for (int i = 0; i < baits.size(); i++) {
            Component unit = baitUnit(baits.get(i), pictures, i == 0);
            int unitW = WIDTH.applyAsInt(unit);
            Component sep = Component.literal(pictures ? " " : " › ").withStyle(ChatFormatting.DARK_GRAY);
            int sepW = WIDTH.applyAsInt(sep);
            if (!empty && lineW + sepW + unitW > maxWidth) {
                out.add(join(line.toArray(new Component[0])));
                line = new ArrayList<>(List.of(indent));
                lineW = WIDTH.applyAsInt(indent);
                empty = true;
            }
            if (!empty) { line.add(sep); lineW += sepW; }
            line.add(unit);
            lineW += unitW;
            empty = false;
        }
        out.add(join(line.toArray(new Component[0])));
        return out;
    }

    /// The best baits on one line: by name in their rarity's colours ("ʙᴀɪᴛ: Cut Shad › French Fries"), or as their
    /// pictures in a row. One or the other, never both; a bait with no picture yet is named, in pictures mode.
    /// Only the best bait, and how many more ("ʙᴀɪᴛ: Small Cut Squid +3"); hovering it (chat open) lists them all.
    static Component bestBaitLine(List<String> baits) {
        boolean pictures = PICTURES.getAsBoolean();
        List<Component> parts = new ArrayList<>();
        parts.add(label("ʙᴀɪᴛ: "));
        parts.add(baitUnit(baits.get(0), pictures, true));
        if (baits.size() > 1) parts.add(Component.literal(" +" + (baits.size() - 1)).withStyle(ChatFormatting.DARK_GRAY));
        net.minecraft.network.chat.MutableComponent tip = Component.empty().append(Component.literal("Best baits").withStyle(ChatFormatting.GOLD));
        for (int i = 0; i < baits.size(); i++) {
            tip.append(Component.literal("\n" + (i + 1) + ". ").withStyle(ChatFormatting.DARK_GRAY));
            tip.append(baitUnit(baits.get(i), false, i == 0));
        }
        Component hover = tip;
        return Component.empty().append(join(parts.toArray(new Component[0])))
                .withStyle(style -> style.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(hover)));
    }

    static Component baitLine(List<String> baits) {
        return baitLines(baits, Integer.MAX_VALUE).get(0);
    }

    /// One bait and its alternatives: names in their rarity's colours, or pictures (a bait with no picture yet is
    /// named). Never both.
    private static Component baitUnit(String bait, boolean pictures, boolean first) {
        List<Component> parts = new ArrayList<>();
        String[] options = bait.split(" / ");
        for (int o = 0; o < options.length; o++) {
            if (o > 0) parts.add(Component.literal("/").withStyle(ChatFormatting.DARK_GRAY));
            String name = options[o].trim();
            Component pic = pictures ? picture(name) : Component.empty();
            if (!pic.getString().isEmpty()) { parts.add(pic); continue; }
            int rgb = baitColor(name);
            parts.add(rgb >= 0 ? Component.literal(name).withColor(rgb)
                    : Component.literal(name).withStyle(first ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        }
        return join(parts.toArray(new Component[0]));
    }

    /// A rarity's colour, made light enough to read on the panel: a deep one (mythical's red) is brightened in its
    /// own hue, so it stays red rather than turning pink.
    static int readable(int rgb) {
        if (rgb < 0) return rgb;
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        if (lum(r, g, b) >= 100) return rgb & 0xFFFFFF;
        double up = 255.0 / Math.max(1, Math.max(r, Math.max(g, b)));
        r = (int) Math.round(r * up); g = (int) Math.round(g * up); b = (int) Math.round(b * up);
        double l = lum(r, g, b);
        if (l < 100) {
            // still dark (a deep blue): just enough white to read
            double t = (100 - l) / (255 - l);
            r = (int) Math.round(r + (255 - r) * t); g = (int) Math.round(g + (255 - g) * t); b = (int) Math.round(b + (255 - b) * t);
        }
        return r << 16 | g << 8 | b;
    }

    private static double lum(int r, int g, int b) {
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    /// The colour for a rarity word ("Rare", "mythical") (the game's exact colour) or -1 when it isn't one.
    static int rarityColor(String rarity) {
        return dannypx.foe.helper.FoeLocations.rarityColor(rarity == null ? "" : rarity.trim()); // the game's exact colour
    }

    /// What FOE+ knows about a bait it has seen: its picture and its rarity. Swappable for tests.
    static java.util.function.Function<String, Component> BAIT_PICTURE = name -> {
        try {
            return inlineItem(dannypx.foe.handler.store.BaitPictures.pictureFor(name));
        } catch (Throwable t) {
            return Component.empty();
        }
    };
    static java.util.function.Function<String, String> BAIT_RARITY = name -> {
        try {
            return dannypx.foe.handler.store.BaitPictures.rarityFor(name);
        } catch (Throwable t) {
            return "";
        }
    };

    private static Component picture(String bait) {
        Component c = BAIT_PICTURE.apply(bait);
        return c == null ? Component.empty() : c;
    }

    private static int baitColor(String bait) {
        return rarityColor(BAIT_RARITY.apply(bait));
    }

    /// "FISH: Blue Marlin, Sailfish": the fish the contest wants, in their colours, wrapped to
    /// `maxWidth` (later lines indented under the first name). Empty when there are none.
    static List<Component> fishLines(Component target, int maxWidth) {
        List<Component> names = new ArrayList<>();
        target.visit((style, str) -> {
            for (String part : str.replace("(", "").replace(")", "").split(",")) {
                String t = part.trim();
                if (!t.isEmpty()) names.add(Component.literal(t).withStyle(style));
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        List<Component> out = new ArrayList<>();
        if (names.isEmpty()) return out;
        Component head = label("ꜰɪѕʜ: ");
        int indent = WIDTH.applyAsInt(head);
        String pad = " ".repeat(Math.max(0, indent / Math.max(1, WIDTH.applyAsInt(Component.literal(" ")))));
        Component comma = Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY);
        List<Component> line = new ArrayList<>();
        line.add(head);
        int w = indent;
        boolean first = true;
        for (int i = 0; i < names.size(); i++) {
            Component n = names.get(i);
            int nw = WIDTH.applyAsInt(n) + (i < names.size() - 1 ? WIDTH.applyAsInt(comma) : 0);
            if (!first && w + nw > maxWidth) {
                out.add(join(line.toArray(new Component[0])));
                line = new ArrayList<>();
                line.add(Component.literal(pad));
                w = WIDTH.applyAsInt(Component.literal(pad));
            }
            line.add(n);
            if (i < names.size() - 1) line.add(comma);
            w += nw;
            first = false;
        }
        out.add(join(line.toArray(new Component[0])));
        return out;
    }

    /// " (Walleye)" without its brackets, keeping the fish's colours.
    private static Component withoutBrackets(Component target) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        target.visit((style, str) -> {
            String t = str.replace("(", "").replace(")", "");
            if (out.getSiblings().isEmpty()) t = t.stripLeading();
            if (!t.isEmpty()) out.append(Component.literal(t).withStyle(style));
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }

    /// The bait sheet's order for this contest as a plain list (best first; "=" options one after another).
    private static List<String> bestBaits(ContestTrackerHandler contest) {
        List<String> out = new ArrayList<>();
        for (var row : dannypx.foe.handler.logic.ContestBaitSheetHandler.instance().lookup(contest.location, contest.type)) {
            for (String step : row.strategy().split(">")) {
                // equally good options ("A = B = C") each get their own number
                for (String option : step.replace("$", "").split("=")) {
                    String t = option.trim();
                    if (t.equalsIgnoreCase("none")) t = "No bait helps";
                    if (!t.isEmpty() && !out.contains(t)) out.add(t);
                }
            }
            if (out.size() >= 5) break;
        }
        return out.size() > 5 ? out.subList(0, 5) : out;
    }

    /// " (Walleye)": the fish this contest wants at its location, from the bait sheet's Fish
    /// column, each in its rarity's colour (the sheet's Rarity column). Empty when the sheet doesn't know, or the
    /// type already names a single fish.
    private static Component targetFish(ContestTrackerHandler contest) {
        return targetFish(dannypx.foe.handler.logic.ContestBaitSheetHandler.instance().lookup(contest.location, contest.type), contest.type);
    }

    /// Package-private for tests.
    static Component targetFish(List<dannypx.foe.handler.logic.ContestBaitSheetHandler.Row> rows, String type) {
        List<String> fish = new ArrayList<>();
        List<String> rarity = new ArrayList<>();
        for (var row : rows) {
            String name = row.fish().trim();
            if (!name.isEmpty() && !fish.contains(name) && !type.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
                fish.add(name);
                rarity.add(row.rarity());
            }
        }
        if (fish.isEmpty()) return Component.empty();
        List<Component> parts = new ArrayList<>();
        parts.add(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY));
        for (int i = 0; i < fish.size(); i++) {
            if (i > 0) parts.add(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
            int rgb = dannypx.foe.helper.FoeLocations.rarityColor(rarity.get(i) == null ? "" : rarity.get(i).trim()); // the exact rarity colour
            parts.add(rgb >= 0 ? Component.literal(fish.get(i)).withColor(rgb) : Component.literal(fish.get(i)).withStyle(ChatFormatting.AQUA));
        }
        parts.add(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
        return join(parts.toArray(new Component[0]));
    }

    private static String currentArea() {
        String here = BossEventHandler.instance().getLocation().getString().replaceAll("[\\uE000-\\uF8FF]", "").trim();
        return here.isEmpty() ? "another area" : here;
    }

    /// "BEST BAIT: Cut Shad", then the fallbacks, from the bait sheet.
    private static List<Component> bestBaitLines(ContestTrackerHandler contest) {
        List<Component> out = new ArrayList<>();
        dannypx.foe.handler.logic.ContestBaitSheetHandler sheet = dannypx.foe.handler.logic.ContestBaitSheetHandler.instance();
        List<dannypx.foe.handler.logic.ContestBaitSheetHandler.Row> rows = sheet.lookup(contest.location, contest.type);
        if (rows.isEmpty()) {
            if (!sheet.hasData() && !sheet.getLastError().isEmpty()) {
                out.add(join(label("ʙᴇѕᴛ ʙᴀɪᴛ: "), Component.literal(sheet.getLastError()).withStyle(ChatFormatting.RED)));
            }
            return out;
        }
        for (dannypx.foe.handler.logic.ContestBaitSheetHandler.Row row : rows) {
            // Strategy is "best > next > ...", with "=" for equally good options.
            String[] steps = row.strategy().split(">");
            Component best = baitText(steps[0].trim());
            List<Component> header = new ArrayList<>();
            header.add(label("ʙᴇѕᴛ ʙᴀɪᴛ"));
            if (rows.size() > 1 && !row.fish().isBlank()) {
                header.add(Component.literal(" (" + row.fish() + ")").withStyle(ChatFormatting.DARK_GRAY));
            }
            header.add(label(": "));
            header.add(best);
            out.add(join(header.toArray(new Component[0])));
            if (steps.length > 1) {
                List<Component> rest = new ArrayList<>();
                rest.add(label("  ᴛʜᴇɴ: "));
                for (int i = 1; i < steps.length; i++) {
                    if (i > 1) rest.add(Component.literal(" > ").withStyle(ChatFormatting.DARK_GRAY));
                    rest.add(baitText(steps[i].trim()));
                }
                out.add(join(rest.toArray(new Component[0])));
            }
        }
        return out;
    }

    /// Bait names from the sheet, with its $ marks removed.
    private static Component baitText(String text) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        String[] options = text.split("=");
        for (int i = 0; i < options.length; i++) {
            if (i > 0) out.append(Component.literal(" = ").withStyle(ChatFormatting.DARK_GRAY));
            // The sheet's $ marks are ignored.
            String option = options[i].replace("$", "").trim();
            if (option.equalsIgnoreCase("none")) {
                out.append(Component.literal("No bait helps").withStyle(ChatFormatting.GRAY));
            } else {
                out.append(Component.literal(option).withStyle(ChatFormatting.WHITE));
            }
        }
        return out;
    }

    /// The server's 1st, 2nd and 3rd place medals (in its resource pack's default font).
    static final String[] MEDALS = {"\uF060", "\uF061", "\uF062"};

    /// A catch's weight or length ("6.22lb"): bright, so it reads easily next to the name.
    static final int WEIGHT_COLOR = 0xFFE27A;

    private static Component weight(String stat) {
        return Component.literal(stat).withColor(WEIGHT_COLOR);
    }

    private static Component place(String glyph, String name, String stat) {
        if (name.isEmpty()) {
            return join(Component.literal(glyph).withStyle(ChatFormatting.WHITE), label("ᴜɴᴄʟᴀɪᴍᴇᴅ"));
        }
        return join(Component.literal(glyph).withStyle(ChatFormatting.WHITE),
                Component.literal(name).withStyle(ChatFormatting.WHITE),
                Component.literal(" (").withStyle(ChatFormatting.GRAY),
                weight(stat),
                Component.literal(")").withStyle(ChatFormatting.GRAY));
    }

    private static ChatFormatting timerColor(long timeLeft) {
        if (timeLeft < 5 * 60 * 1000) return ChatFormatting.DARK_RED;
        if (timeLeft <= 10 * 60 * 1000) return ChatFormatting.RED;
        if (timeLeft <= 15 * 60 * 1000) return ChatFormatting.GOLD;
        return ChatFormatting.GREEN;
    }

    @Override
    protected List<Component> previewLines() {
        return contestLines("Heaviest Flatfishes", "Lofoten Islands", true, 17 * 60_000L + 57_000L, 0, 0,
                Component.empty(), true, new String[][]{{"Aidanqt", "0.21lb"}, {"", ""}, {"", ""}}, "You", "Unranked", 1, "",
                List.of("Small Cut Squid", "Sand Shrimp", "Salmon Head"), -1);
    }
}
