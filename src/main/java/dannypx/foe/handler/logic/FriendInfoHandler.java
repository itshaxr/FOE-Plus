package dannypx.foe.handler.logic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A friend's balance and crew, for their card in the Friends menu: FOE+ asks quietly with /bal &lt;name&gt; and
 * /crew info &lt;name&gt; while the card is open (once a minute at most per friend), and reads the answers. Replies to
 * its own asks are hidden; everything else shows as usual. The lines it reads are written to the log, so the reading
 * can be fitted to the server's exact wording.
 */
public final class FriendInfoHandler {
    private static final FriendInfoHandler INSTANCE = new FriendInfoHandler();

    public static FriendInfoHandler instance() {
        return INSTANCE;
    }

    /// What we know of someone: balance ("$11.64M") and crew ("Reel Gang", or "No crew"), null while unknown.
    public record Info(String balance, String crew, long at) {}

    private final Map<String, Info> known = new HashMap<>();
    { MemoryGuard.register("friendInfo", known::size, known::clear); }
    private final Map<String, Long> askedAt = new HashMap<>();

    /// Where the replies it read are written (the log; nothing in tests).
    static java.util.function.Consumer<String> LOG = LoggerHandler::info;

    static final long ASK_EVERY_MS = 60_000L, ANSWER_MS = 3_000L, BETWEEN_MS = 1_500L;

    /// The ask on the way: whose, which ("bal" or "crew"), since when.
    private String asking = null, askingWhat = null;
    private long askingAt = 0L;
    /// The crew ask still to send after the balance one.
    private String crewNext = null;
    private long crewAt = 0L;

    public Info of(String name) {
        return name == null ? null : known.get(name.toLowerCase(Locale.ROOT));
    }

    /// The card for this friend is open: ask about them if it's been a while.
    public void want(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) return;
        long now = System.currentTimeMillis();
        String k = name.toLowerCase(Locale.ROOT);
        Long at = askedAt.get(k);
        if (at != null && now - at < ASK_EVERY_MS) return;
        if (asking != null && now - askingAt < ANSWER_MS) return;
        if (crewNext != null) return;
        if (!SilentMenuHandler.instance().isQuietForChatCommand(BETWEEN_MS)) return;
        askedAt.put(k, now);
        send(name, "bal");
        crewNext = name;
        crewAt = now + BETWEEN_MS;
    }

    public void tick() {
        // a hidden crew menu whose items never came: closed anyway, so the server doesn't think it's still open
        if (crewMenuId != -1 && System.currentTimeMillis() - askingAt > ANSWER_MS * 2) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(crewMenuId));
            crewMenuId = -1;
            crewMenuFor = null;
        }
        if (crewNext == null || System.currentTimeMillis() < crewAt) return;
        if (!SilentMenuHandler.instance().isQuietForChatCommand(BETWEEN_MS)) return;
        String n = crewNext;
        crewNext = null;
        send(n, "crew");
    }

    /// /crew info opens a menu: the one that opens just after our ask is read and closed, never shown.
    private long crewMenuUntil = 0L;
    private String crewMenuFor = null;
    private int crewMenuId = -1;

    /// From the packet listener: true when this menu answers our /crew info (it isn't opened).
    public boolean onOpenScreen(int id, String title) {
        if (crewMenuFor == null || System.currentTimeMillis() > crewMenuUntil) return false;
        crewMenuId = id;
        crewMenuTitle = title;
        crewMenuUntil = 0L;
        return true;
    }

    private String crewMenuTitle = "";

    /// From the packet listener: the hidden crew menu's items: the crew's name read from them, then the menu closed.
    public void onContainerContent(int id, java.util.List<net.minecraft.world.item.ItemStack> items) {
        if (crewMenuId == -1 || id != crewMenuId) return;
        java.util.List<String> texts = new java.util.ArrayList<>();
        texts.add(crewMenuTitle);
        int own = Math.max(0, items.size() - 36);
        for (int i = 0; i < own; i++) {
            var st = items.get(i);
            if (st.isEmpty()) continue;
            texts.add(st.getHoverName().getString());
            var lore = st.get(net.minecraft.core.component.DataComponents.LORE);
            if (lore != null) for (Component l : lore.lines()) texts.add(l.getString());
        }
        LOG.accept("[FriendInfo] crew menu for " + crewMenuFor + ": " + String.join(" | ", texts));
        String crew = crewFromMenu(texts);
        String k = crewMenuFor.toLowerCase(Locale.ROOT);
        Info old = known.getOrDefault(k, new Info(null, null, 0L));
        known.put(k, new Info(old.balance(), crew == null ? "?" : crew, System.currentTimeMillis()));
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(id));
        crewMenuId = -1;
        crewMenuFor = null;
    }

    /// Package-private for tests: the crew's name among a crew menu's texts (its title, then its items' names and
    /// lore): a "Crew: Name" line, else a title that names it; null when none does.
    static String crewFromMenu(java.util.List<String> texts) {
        for (String t : texts) {
            String plain = clean0(t);
            Matcher m = Pattern.compile("(?i)\\bcrew(?:\\s*name)?\\s*[:»>]\\s*(.+)$").matcher(plain);
            if (m.find()) {
                String c = clean(m.group(1));
                if (c != null) return c;
            }
            if (NO_CREW.matcher(plain).find()) return "No crew";
        }
        String title = texts.isEmpty() ? "" : clean0(texts.get(0)).replaceAll("(?i)\\b(crew|info|menu)\\b", "").replaceAll("[^\\p{L}\\p{N} '&_-]", "").trim();
        return title.length() >= 2 ? clean(title) : null;
    }

    private static String clean0(String s) {
        String t = ChatFormatting.stripFormatting(s == null ? "" : s);
        return t == null ? "" : t.replaceAll("[\\uE000-\\uF8FF]", "").trim();
    }

    private void send(String name, String what) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        String cmd = what.equals("bal") ? "bal " + name : "crew info " + name;
        asking = name;
        askingWhat = what;
        askingAt = System.currentTimeMillis();
        if (what.equals("crew")) {
            crewMenuFor = name;
            crewMenuUntil = askingAt + ANSWER_MS;
        }
        SilentMenuHandler.instance().noteBackgroundCommand(cmd);
        mc.getConnection().sendCommand(cmd);
    }

    static final Pattern BALANCE_OF = Pattern.compile("(?i)^\\s*balance of\\s+(\\w+)\\s*[»>:]\\s*\\$?\\s*([\\d,]+(?:\\.\\d+)?)");

    /// Package-private for tests: "249,985,365.68" → "$249.99M".
    static String shortMoney(String digits) {
        try {
            double v = Double.parseDouble(digits.replace(",", ""));
            if (v >= 1e12) return String.format(Locale.ROOT, "$%.2fT", v / 1e12);
            if (v >= 1e9) return String.format(Locale.ROOT, "$%.2fB", v / 1e9);
            if (v >= 1e6) return String.format(Locale.ROOT, "$%.2fM", v / 1e6);
            if (v >= 1e3) return String.format(Locale.ROOT, "$%.2fK", v / 1e3);
            return String.format(Locale.ROOT, "$%,.0f", v);
        } catch (NumberFormatException e) {
            return "$" + digits;
        }
    }

    /// "$11.64M", "$1,234,567", "$ 52.10K".
    static final Pattern MONEY = Pattern.compile("\\$\\s?([\\d,]+(?:\\.\\d+)?\\s?[KMBTkmbt]?)");
    /// "Crew: Reel Gang", "Name » Reel Gang", "Crew Name - Reel Gang".
    static final Pattern CREW_NAMED = Pattern.compile("(?i)^\\s*(?:crew\\s*)?(?:name|crew)\\s*[:»>\\-]\\s*(.+?)\\s*$");
    static final Pattern NO_CREW = Pattern.compile("(?i)(not in a crew|no crew|isn'?t in a crew|doesn'?t have a crew|not a member)");
    /// "[Reel Gang] Crew Info", "Reel Gang's crew", "CREW » Reel Gang".
    static final Pattern CREW_TITLE = Pattern.compile("(?i)^\\s*(?:\\[(.+?)\\]\\s*crew\\s*info|crew\\s*[»>]\\s*(.+?))\\s*$");
    static final Pattern SERVER_TAG = Pattern.compile("^\\s*([A-Z][A-Z ]{1,15})\\s*[»>]");

    /// From chat: true when the line answers one of our own asks (hidden).
    public boolean onGameMessage(Component message) {
        String text = ChatFormatting.stripFormatting(message.getString());
        return text != null && onLine(text.replaceAll("[\\uE000-\\uF8FF]", "").trim(), System.currentTimeMillis());
    }

    /// Package-private for tests.
    boolean onLine(String text, long now) {
        if (asking == null || now - askingAt > ANSWER_MS || text.isEmpty()) return false;
        String k = asking.toLowerCase(Locale.ROOT);
        Info old = known.getOrDefault(k, new Info(null, null, 0L));
        Matcher tag = SERVER_TAG.matcher(text);
        String tagName = tag.find() ? tag.group(1).trim().toUpperCase(Locale.ROOT) : "";
        if (askingWhat.equals("bal")) {
            // "Balance of Aidanqt » $249,985,365.68 dollars"
            Matcher of = BALANCE_OF.matcher(text);
            if (of.find() && of.group(1).equalsIgnoreCase(asking)) {
                LOG.accept("[FriendInfo] bal reply: " + text);
                known.put(k, new Info(shortMoney(of.group(2)), old.crew(), now));
                asking = null;
                return true;
            }
            Matcher m = MONEY.matcher(text);
            // the server's answer, never someone's chat (a "»" after a player's name)
            boolean about = tagName.startsWith("BAL") || tagName.startsWith("ECONOMY") || tagName.startsWith("MONEY")
                    || (text.toLowerCase(Locale.ROOT).contains(k) && !text.contains("»"));
            if (!m.find() || !about) return false;
            LOG.accept("[FriendInfo] bal reply: " + text);
            known.put(k, new Info("$" + m.group(1).replace(" ", ""), old.crew(), now));
            asking = null;
            return true;
        }
        // crew info: several lines may come; ours are the CREW ones in the window
        boolean crewLine = tagName.startsWith("CREW") || (text.toLowerCase(Locale.ROOT).contains("crew") && !text.contains("»"));
        if (!crewLine) return false;
        LOG.accept("[FriendInfo] crew reply: " + text);
        String crew = crewIn(text);
        if (crew != null) known.put(k, new Info(old.balance(), crew, now));
        return true;
    }

    /// Package-private for tests: the crew's name in a line of /crew info, "No crew", or null when it isn't there.
    static String crewIn(String text) {
        if (NO_CREW.matcher(text).find()) return "No crew";
        Matcher m = CREW_NAMED.matcher(text);
        if (m.matches()) return clean(m.group(1));
        m = CREW_TITLE.matcher(text);
        if (m.matches()) return clean(m.group(1) != null ? m.group(1) : m.group(2));
        return null;
    }

    private static String clean(String s) {
        String t = s == null ? "" : s.replaceAll("^[\\[(]|[\\])]$", "").trim();
        return t.isEmpty() || t.length() > 32 ? null : t;
    }

    /// For tests: as if we'd just asked.
    void asked(String name, String what, long now) {
        asking = name;
        askingWhat = what;
        askingAt = now;
    }
}
