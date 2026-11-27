package dannypx.foe.handler.fetch;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.Handler;
import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.handler.logic.KeyBindHandler;
import dannypx.foe.helper.KeyBindHelper;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.item.ArmorTagObject;
import dannypx.foe.item.TagObject;
import dannypx.foe.item.ValidateItem;
import dannypx.foe.handler.logic.SearchHandler;
import dannypx.foe.search.field.SearchContext;
import dannypx.foe.type.tuple.Pair;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

public class TooltipHandler extends Handler {
    private static TooltipHandler INSTANCE = new TooltipHandler();

    public static TooltipHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new TooltipHandler();
        }
        return INSTANCE;
    }

    //region Fields
    //endregion

    //region Methods
    public void fetchTooltip(ItemStack itemStack, Item.TooltipContext tooltipContext, TooltipFlag tooltipFlag, List<Component> components) {
        if(ConnectionHandler.instance().isOnServer()) {
            Pair<Boolean, TagObject> validatedItem = ValidateItem.isServerItem(itemStack, true);
            if(validatedItem.value1()) {
                Pair<Boolean, ArmorTagObject> validatedArmor = ValidateItem.isArmor(validatedItem.value2());
                if(validatedArmor.value1()) this.setArmorRolls(validatedArmor.value2(), components);
            } else {
                if(itemStack.getItem() == Items.ENDER_EYE
                        && itemStack.get(DataComponents.LORE) != null
                        && !itemStack.get(DataComponents.LORE).lines().isEmpty()
                        && itemStack.get(DataComponents.LORE).lines().getFirst().getString().contains("Bonus Slot")
                ) {
                    this.setArmorRoll(itemStack, components);
                }
            }

            if (Configs.mainConfig.baitTooltipValue.get()) addBaitValue(itemStack, components);
            if (Configs.mainConfig.baitTooltipValue.get()) addChummerValue(itemStack, components);

            if(SearchHandler.instance().isOnScreen()
                    && !SearchHandler.instance().getQuery().isEmpty()
                    && !new SearchContext(itemStack).tooltipHidden()
                    && !components.isEmpty()
            ) {
                components.add(Component.empty());
                components.addAll(SearchHandler.instance().getMatchLines(itemStack));
            }
        }
    }

    /// A bait's tooltip: what its stack is worth at your bait prices, on its "Stock: 18x" line
    /// ("Stock: 18x · $4,500"); a lure's on its "Remaining Uses: 86" line.
    static void addBaitValue(ItemStack itemStack, List<Component> components) {
        long value = dannypx.foe.handler.logic.BaitboxHandler.stackValue(itemStack);
        if (value < 0) return;
        withValue(components, value);
    }

    /// A chummer's tooltip: what it's worth at your price for its rarity (Bait Prices → Chummers), after its name
    /// ("Epic Chummer  ·  $50,000"; for a stack, the stack's worth). Nothing when you have no price for it: chummers
    /// have no set price. Never a line of its own (the server's tooltip frame is drawn line by line).
    static void addChummerValue(ItemStack itemStack, List<Component> components) {
        if (components.isEmpty()) return;
        String name = itemStack.getHoverName().getString();
        int at = -1;
        for (int i = 0; i < components.size(); i++) if (isChummerText(components.get(i).getString())) { at = i; break; }
        if (at < 0 && !isChummerText(name)) return;
        if (at < 0) at = 0;
        // its rarity: the server's badge or word, on its name or anywhere in its tooltip
        String rarity = dannypx.foe.screens.element.hud.PetPanelElement.rarityFromText(name);
        for (int i = 0; i < components.size() && (rarity.isEmpty() || rarity.equals("special")); i++)
            rarity = dannypx.foe.screens.element.hud.PetPanelElement.rarityFromText(components.get(i).getString());
        if (rarity.isEmpty() || rarity.equals("special")) return;
        Long each = dannypx.foe.handler.store.BaitPriceOverrides.get(Character.toUpperCase(rarity.charAt(0)) + rarity.substring(1) + " Chummer");
        if (each == null) return;
        long value = each * Math.max(1, itemStack.getCount());
        components.set(at, components.get(at).copy()
                .append(Component.literal("  ·  ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
                .append(Component.literal(dannypx.foe.handler.logic.BaitboxHandler.money(value)).withStyle(net.minecraft.ChatFormatting.GOLD)));
    }

    /// Package-private for tests: whether a line names a chummer (in plain or small letters).
    static boolean isChummerText(String text) {
        if (text == null) return false;
        String t = text.toLowerCase(java.util.Locale.ROOT);
        return t.contains("chummer") || t.contains("ᴄʜᴜᴍᴍᴇʀ");
    }

    /// Package-private for tests.
    static void withValue(List<Component> components, long value) {
        Component worth = Component.literal(dannypx.foe.handler.logic.BaitboxHandler.money(value)).withStyle(net.minecraft.ChatFormatting.GOLD);
        for (int i = 0; i < components.size(); i++) {
            long stock = dannypx.foe.handler.logic.BaitboxHandler.stockOf(components.get(i).getString());
            if (stock >= 0) {
                components.set(i, components.get(i).copy()
                        .append(Component.literal("  ·  ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
                        .append(worth));
                if (stock > 1) eachUnder(components, i, Math.round(value / (double) stock));
                return;
            }
        }
        // A lure: on its "Remaining Uses: 86" line. A line of its own is never added: the server's tooltip
        // frame is drawn line by line, and an extra line pushed the item's name out of sight.
        for (int i = 0; i < components.size(); i++) {
            if (components.get(i).getString().contains("Remaining Uses:")) {
                components.set(i, components.get(i).copy()
                        .append(Component.literal("  ·  ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
                        .append(worth));
                return;
            }
        }
    }

    /// A bait shared in chat as text only (its name on the first line, "Stock: 18x" further down): the same text
    /// with its worth on the Stock line and one bait's under it, at your price for it (Bait Prices); null when it
    /// isn't a bait or you have no price for it.
    private static String lastChatHover = "";

    /// What a hover in chat held, written to the log once per different hover (FOE+ could put no price on it), so
    /// the way the server sends shared items can be seen in a log.
    public static void noteChatHover(String kind, String what) {
        String key = kind + ":" + what;
        if (key.equals(lastChatHover)) return;
        lastChatHover = key;
        String shown = what.length() > 600 ? what.substring(0, 600) + "…" : what;
        dannypx.foe.handler.logic.LoggerHandler.info("Chat hover (" + kind + "): " + shown.replace("\n", " / "));
    }

    public static Component baitTextWithValue(Component text) {
        if (text == null) return null;
        List<Component> lines = new java.util.ArrayList<>();
        for (String part : text.getString().split("\n", -1)) lines.add(Component.literal(part));
        // the lines with their colours: split the styled text at its line breaks
        List<Component> styled = splitLines(text);
        if (styled.size() == lines.size()) lines = styled;
        if (lines.isEmpty()) return null;
        long stock = -1;
        for (Component l : lines) { stock = dannypx.foe.handler.logic.BaitboxHandler.stockOf(l.getString()); if (stock >= 0) break; }
        // a lure: its uses left
        if (stock < 0) for (Component l : lines) { stock = dannypx.foe.handler.logic.BaitboxHandler.usesOf(l.getString()); if (stock >= 0) break; }
        if (stock < 0) return null;
        String name = lines.get(0).getString().replaceAll("[\\uE000-\\uF8FF]", "").trim();
        List<String> plain = new java.util.ArrayList<>();
        for (Component l : lines) plain.add(l.getString());
        // your price for it, else its rarity's (as in your inventory)
        long each = dannypx.foe.handler.logic.BaitboxHandler.priceOfText(name, plain);
        if (each < 0) return null;
        List<Component> out = new java.util.ArrayList<>(lines);
        withValue(out, stock * each);
        net.minecraft.network.chat.MutableComponent joined = Component.empty();
        for (int i = 0; i < out.size(); i++) {
            if (i > 0) joined.append("\n");
            joined.append(out.get(i));
        }
        return joined;
    }

    private static final java.util.regex.Pattern PET_RATING = java.util.regex.Pattern.compile("\\[(\\d+(?:\\.\\d+)?)%\\]");
    private static final java.util.regex.Pattern STAT_PERCENT = java.util.regex.Pattern.compile("\\((\\d+(?:\\.\\d+)?)%\\)\\s*$");

    /// A pet shared in chat as text only (its name, "ᴘᴇᴛ ʟᴇᴠᴇʟ", its Luck and Scale lines, "ʀᴀᴛɪɴɢ: ... [92.5%]",
    /// in the server's frame): the same text with its price from your Pet Prices sheet at the end of its name's line;
    /// null when it isn't a pet or can't be priced.
    public static Component petTextWithPrice(Component text) {
        if (text == null) return null;
        String all = text.getString();
        if (!all.contains("ʀᴀᴛɪɴɢ") || !all.contains("ᴘᴇᴛ")) return null;
        List<Component> lines = splitLines(text);
        List<String> plain = new java.util.ArrayList<>();
        for (Component l : lines) plain.add(l.getString());
        long[] price = petPriceOfText(plain);
        if (price == null) return null;
        // the name's line: the first with words on it
        int nameRow = -1;
        for (int i = 0; i < plain.size(); i++) if (plain.get(i).codePoints().anyMatch(cp -> cp < 0x3000 && Character.isLetter(cp))) { nameRow = i; break; }
        if (nameRow < 0) return null;
        String money = "$" + dannypx.foe.handler.store.PetPrices.format(price[0])
                + (price[1] > price[0] ? "–" + dannypx.foe.handler.store.PetPrices.format(price[1]) : "");
        List<Component> out = new java.util.ArrayList<>(lines);
        out.set(nameRow, lines.get(nameRow).copy()
                .append(Component.literal("  ·  ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
                .append(Component.literal(money).withStyle(net.minecraft.ChatFormatting.GOLD)));
        net.minecraft.network.chat.MutableComponent joined = Component.empty();
        for (int i = 0; i < out.size(); i++) {
            if (i > 0) joined.append("\n");
            joined.append(out.get(i));
        }
        return joined;
    }

    /// Package-private for tests: {low, high} for a pet from its tooltip's lines, or null. Its rarity is the
    /// colour of its frame (the piece each row starts with), its rating the "[92.5%]" on its rating line, and it's
    /// Max Luck (Max Scale) when every Luck (Scale) line says 100%.
    static long[] petPriceOfText(List<String> plain) {
        int rarity = -1;
        int[] middles = dannypx.foe.handler.renderer.PetTooltipPrice.MIDDLE_PIECES;
        outer:
        for (String l : plain) {
            for (int k = 0; k < middles.length; k++) if (l.indexOf(middles[k]) >= 0) { rarity = k; break outer; }
        }
        double rating = -1;
        boolean luckSeen = false, scaleSeen = false, maxLuck = true, maxScale = true;
        for (String l : plain) {
            if (l.contains("ʀᴀᴛɪɴɢ")) {
                java.util.regex.Matcher m = PET_RATING.matcher(l);
                if (m.find()) rating = Double.parseDouble(m.group(1));
            }
            java.util.regex.Matcher pm = STAT_PERCENT.matcher(l.trim());
            if (l.contains("ʟᴜᴄᴋ:") && pm.find()) { luckSeen = true; maxLuck &= Double.parseDouble(pm.group(1)) >= 99.95; }
            else if (l.contains("sᴄᴀʟᴇ:") && pm.find()) { scaleSeen = true; maxScale &= Double.parseDouble(pm.group(1)) >= 99.95; }
        }
        if (rarity < 0 || rating < 0) return null;
        int row = dannypx.foe.handler.store.PetPrices.rowFor(rating);
        if (row < 0) return null;
        return dannypx.foe.handler.store.PetPrices.price(row, rarity, luckSeen && maxLuck, scaleSeen && maxScale);
    }

    /// A text's lines, each with its own colours.
    private static List<Component> splitLines(Component text) {
        List<Component> out = new java.util.ArrayList<>();
        net.minecraft.network.chat.MutableComponent[] cur = {Component.empty()};
        text.visit((style, part) -> {
            String[] bits = part.split("\n", -1);
            for (int i = 0; i < bits.length; i++) {
                if (i > 0) { out.add(cur[0]); cur[0] = Component.empty(); }
                if (!bits[i].isEmpty()) cur[0].append(Component.literal(bits[i]).withStyle(style));
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        out.add(cur[0]);
        return out;
    }

    /// What one bait is worth ("$225 each"), under the Stock line: on the empty row the tooltip has there (its frame
    /// is drawn row by row, so a new row would push its name out of sight), lined up with "Stock"; with no empty row
    /// there, at the Stock line's end. Package-private for tests.
    static void eachUnder(List<Component> components, int stockRow, long each) {
        Component each_ = Component.literal(dannypx.foe.handler.logic.BaitboxHandler.money(each)).withStyle(net.minecraft.ChatFormatting.GOLD)
                .append(Component.literal(" each").withStyle(net.minecraft.ChatFormatting.GRAY));
        String stockLine = components.get(stockRow).getString();
        int next = stockRow + 1;
        if (next < components.size() && components.get(next).getString().codePoints().noneMatch(cp -> cp < 0x3000 && Character.isLetterOrDigit(cp))) {
            // (the frame's pieces are pictures in the Hangul and private ranges, not words)
            // as far in as "Stock" is: its row's spaces less the empty row's own
            int at = stockLine.indexOf("Stock");
            int want = at < 0 ? 0 : (int) stockLine.substring(0, at).chars().filter(c -> c == ' ').count();
            int have = (int) components.get(next).getString().chars().filter(c -> c == ' ').count();
            components.set(next, components.get(next).copy().append(Component.literal(" ".repeat(Math.max(0, want - have)))).append(each_));
            return;
        }
        components.set(stockRow, components.get(stockRow).copy()
                .append(Component.literal("  (").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)).append(each_)
                .append(Component.literal(")").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)));
    }

    private void setArmorRoll(ItemStack itemStack, List<Component> components) {
        for (int i = 0; i < ArmorRollScreenHandler.instance().getRollList().size(); i++) {
            ItemStack listItem = ArmorRollScreenHandler.instance().getRollList().get(i);

            if(ItemStack.isSameItemSameComponents(listItem, itemStack)) {
                Component border = TagObject.getBorderComponent(itemStack);
                Component borderComponent = Component.literal(border.getString().trim())
                        .setStyle(border.getStyle())
                        .append("   ");

                int sizeOfLine = -1;
                for (int j = 0; j < components.size(); j++) {
                    if(components.get(j).getString().contains("(+")
                            && components.get(j).getString().contains("%)")
                    ) sizeOfLine = j;
                }

                if(sizeOfLine != -1) {
                    ArmorTagObject armor = ArmorRollScreenHandler.instance().getArmor();

                    int rolls = armor.getArmorRollRolls(i);
                    int tier = i + 1;
                    int money = ArmorTagObject.calculateMoneyRolls(rolls, tier);

                    Component moneyRoll = TextHelper.concat(
                            borderComponent,
                            Component.literal(TextHelper.smallCaps("rolls: ")).withStyle(ChatFormatting.GRAY),
                            Component.literal(String.valueOf(rolls - 1)).withStyle(ChatFormatting.YELLOW),
                            Component.literal("x ").withStyle(ChatFormatting.WHITE),
                            Component.literal(TextHelper.smallCaps("| spent: ")).withStyle(ChatFormatting.GRAY),
                            Component.literal("$" + TextHelper.shortenNumber(money, 2)).withStyle(ChatFormatting.GREEN)
                    );

                    components.add(sizeOfLine + 2, moneyRoll);
                }
            }
        }
    }

    private void setArmorRolls(ArmorTagObject armorNbtObject, List<Component> components) {
        Component border = Component.literal(armorNbtObject.getBorderComponent().getString().trim())
                .setStyle(armorNbtObject.getBorderComponent().getStyle())
                .append("   ");

        int tierLine = -1;
        int rightClickLine = -1;
        int qualityLine = -1;
        Component seperatorComponent = Component.empty();

        for (int i = 0; i < components.size(); i++) {
            if(components.get(i).getString().contains("Tier: ")) tierLine = i;
            if(components.get(i).getString().contains("ʀɪɢʜᴛ ᴄʟɪᴄᴋ ᴛᴏ ʀᴏʟʟ ʙᴏɴᴜsᴇs")) rightClickLine = i;
            if(components.get(i).getString().contains("ꞯᴜᴀʟɪᴛʏ: ")
                    || components.get(i).getString().contains("ᴜᴀʟɪᴛʏ ")
            ) qualityLine = i;
            if(Objects.equals(seperatorComponent, Component.empty())
                    && components.get(i).getString().contains("                                               ")
                    && components.get(i).getSiblings().stream().anyMatch(part -> part.getStyle().isStrikethrough())
            ) seperatorComponent = components.get(i);
        }

        if(rightClickLine != -1
                && armorNbtObject.isIdentified()
                && !KeyBindHandler.inspectAlways().covers(dannypx.foe.type.InspectAlways.Kind.ARMOR)
        ) {
            Component inspectComponent = TextHelper.concat(
                    Component.literal(TextHelper.smallCaps("Hold ")),
                    Component.literal(TextHelper.smallCaps(KeyBindHelper.getKeyString(Configs.keyBindConfig.inspectKeybind))),
                    Component.literal(TextHelper.smallCaps(" to see more info"))
            ).withStyle(ChatFormatting.DARK_GRAY);
            Component rollHintComponent = TextHelper.concat(
                    border,
                    inspectComponent
            );

            components.add(rightClickLine + 1, rollHintComponent);
        }

        if(KeyBindHandler.instance().isInspecting(dannypx.foe.type.InspectAlways.Kind.ARMOR)) {
            if(tierLine != -1) {
                for (int i = 4; i >= 0; i--) {
                    if(armorNbtObject.isArmorRollUnlocked(i)
                            && armorNbtObject.isArmorRollRolled(i)
                    ) {
                        int rolls = armorNbtObject.getArmorRollRolls(i);
                        int tier = i + 1;
                        int money = ArmorTagObject.calculateMoneyRolls(rolls, tier);

                        Component moneyRoll = TextHelper.concat(
                                border,
                                Component.literal(TextHelper.smallCaps("  └ rolls: ")).withStyle(ChatFormatting.GRAY),
                                Component.literal(String.valueOf(rolls - 1)).withStyle(ChatFormatting.YELLOW),
                                Component.literal("x ").withStyle(ChatFormatting.WHITE),
                                Component.literal(TextHelper.smallCaps("| spent: ")).withStyle(ChatFormatting.GRAY),
                                Component.literal("$" + TextHelper.shortenNumber(money, 2)).withStyle(ChatFormatting.GREEN)
                        );

                        components.add(tierLine + tier + 1, moneyRoll);
                    }
                }
            }

            if(qualityLine != -1
                    && armorNbtObject.isIdentified()
            ) {
                String username = null;

                if(armorNbtObject.getPlayerUUID() != null) {
                    username = ProfileHandler.instance().getUsernameFromId(armorNbtObject.getPlayerUUID());
                }

                Component identifierComponent = TextHelper.concat(
                        border,
                        Component.literal("Identifier:").withStyle(ChatFormatting.GRAY)
                );

                Component usernameComponent = TextHelper.concat(
                        border,
                        Component.literal(TextHelper.smallCaps("  Player: ")).withStyle(ChatFormatting.GRAY),
                        username != null ? Component.literal(username).withStyle(ChatFormatting.YELLOW)
                                : Component.literal("Loading").withStyle(ChatFormatting.DARK_GRAY)
                );

                components.add(qualityLine + 1, usernameComponent);
                components.add(qualityLine + 1, identifierComponent);
                components.add(qualityLine + 1, seperatorComponent);
            }
        }
    }
    //endregion

    //region Dev
    /// Field, Pair<Value, Tooltip>
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
                "key", Pair.of(Component.literal("value"), Component.empty())
        );
    }
    //endregion
}
