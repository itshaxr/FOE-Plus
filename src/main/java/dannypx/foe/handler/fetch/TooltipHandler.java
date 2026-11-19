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

    /// Package-private for tests.
    static void withValue(List<Component> components, long value) {
        Component worth = Component.literal(dannypx.foe.handler.logic.BaitboxHandler.money(value)).withStyle(net.minecraft.ChatFormatting.GOLD);
        for (int i = 0; i < components.size(); i++) {
            if (dannypx.foe.handler.logic.BaitboxHandler.stockOf(components.get(i).getString()) >= 0) {
                components.set(i, components.get(i).copy()
                        .append(Component.literal("  ·  ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
                        .append(worth));
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
