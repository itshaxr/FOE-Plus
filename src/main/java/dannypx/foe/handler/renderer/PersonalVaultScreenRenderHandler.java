package dannypx.foe.handler.renderer;

import dannypx.foe.handler.ScreenHandler;
import dannypx.foe.handler.logic.LoadingHandler;
import dannypx.foe.handler.logic.SearchHandler;
import dannypx.foe.handler.logic.VaultSortHandler;
import dannypx.foe.search.editbox.SearchBarEditBox;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.config.Configs;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PersonalVaultScreenRenderHandler extends ScreenHandler {
    private static PersonalVaultScreenRenderHandler INSTANCE = new PersonalVaultScreenRenderHandler();

    public static PersonalVaultScreenRenderHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new PersonalVaultScreenRenderHandler();
        }
        return INSTANCE;
    }

    //region Fields
    SearchBarEditBox searchBarWidget;
    //endregion

    //region Methods
    public void init(Screen screen) {
        SearchHandler.instance().setFocused(false);
        SearchHandler.instance().setOnScreen(true);
        this.initWidgets(screen);
    }

    private void initWidgets(Screen screen) {
        if(LoadingHandler.instance().isLoadingDone()
                && Configs.mainConfig.enableMod.get()
        ) {
            List<AbstractWidget> widgets = new ArrayList<>();

            searchBarWidget = SearchHandler.getSearchBar(
                    minecraft.getWindow().getGuiScaledWidth() / 2 - 80,
                    minecraft.getWindow().getGuiScaledHeight() / 2 - 133,
                    160,
                    20
            );

            widgets.add(searchBarWidget);

            // Page buttons 1-7 under the vault, ported from FOECollab's PersonalVaultScreenHandler.
            int currentPage = getCurrentPage(screen);
            int buttonSize = 22 + 1;
            for (int i = 1; i <= 7; i++) {
                final int page = i;
                boolean isCurrent = page == currentPage;
                Component label = isCurrent
                        ? Component.literal("" + page).withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD)
                        : Component.literal("" + page).withStyle(ChatFormatting.BOLD);
                Button button = Button.builder(label, b -> {
                            if (minecraft.player != null) minecraft.player.connection.sendCommand("pv " + page);
                        })
                        .bounds(minecraft.getWindow().getGuiScaledWidth() / 2 - 22 / 2 - buttonSize * 4 + buttonSize * page,
                                minecraft.getWindow().getGuiScaledHeight() / 2 + 111,
                                22, 20)
                        .tooltip(Tooltip.create(pageTooltip(page)))
                        .build();
                button.active = !isCurrent;
                widgets.add(button);
            }

            // Bait Sorter (left of the vault) and Pet Sorter (right), each in a small titled box
            // (FOE+). Shift-click for the reverse order.
            int left = minecraft.getWindow().getGuiScaledWidth() / 2 - 88;
            int right = minecraft.getWindow().getGuiScaledWidth() / 2 + 88;
            int top = minecraft.getWindow().getGuiScaledHeight() / 2 - 111;
            int sortWidth = 64;
            int boxWidth = sortWidth + 8;
            int boxHeight = 18 + 3 * 24 + 2;
            int baitBoxX = left - 4 - boxWidth;
            int petBoxX = right + 4;

            widgets.add(new dannypx.foe.screens.widget.TitledBoxWidget(baitBoxX, top, boxWidth, boxHeight, Component.literal("Bait Sorter")));
            widgets.add(sortButton("⇅ Amount", VaultSortHandler.Mode.AMOUNT,
                    "Sort this page's baits by amount, most first.\nShift-click: fewest first.\nPets and other items stay put.\nThe bait sorter (B) keeps this order.", baitBoxX + 4, top + 18, sortWidth));
            widgets.add(sortButton("⇅ Rarity", VaultSortHandler.Mode.RARITY,
                    "Sort this page's baits by rarity, rarest first.\nShift-click: most common first.\nPets and other items stay put.\nThe bait sorter (B) keeps this order.", baitBoxX + 4, top + 42, sortWidth));
            widgets.add(sortButton("⇅ Name", VaultSortHandler.Mode.NAME,
                    "Sort this page's baits by name, grouped by kind\n(every Crankbait together), A to Z.\nShift-click: Z to A.\nPets and other items stay put.\nThe bait sorter (B) keeps this order.", baitBoxX + 4, top + 66, sortWidth));

            widgets.add(new dannypx.foe.screens.widget.TitledBoxWidget(petBoxX, top, boxWidth, boxHeight, Component.literal("Pet Sorter")));
            widgets.add(petSortButton("⇅ Percent", VaultSortHandler.Mode.PERCENT,
                    "Sort this page's pets by percent, highest first.\nShift-click: lowest first.\nBaits and other items stay put.", petBoxX + 4, top + 18, sortWidth));
            widgets.add(petSortButton("⇅ Rarity", VaultSortHandler.Mode.RARITY,
                    "Sort this page's pets by rarity, rarest first\n(same rarity: highest percent first).\nShift-click: most common first.\nBaits and other items stay put.", petBoxX + 4, top + 42, sortWidth));
            widgets.add(petSortButton("⇅ Name", VaultSortHandler.Mode.NAME,
                    "Sort this page's pets by name, A to Z.\nShift-click: Z to A.\nBaits and other items stay put.", petBoxX + 4, top + 66, sortWidth));

            widgets.forEach(Screens.getButtons(screen)::add);
        }
    }

    private Button petSortButton(String label, VaultSortHandler.Mode mode, String tooltip, int x, int y, int width) {
        return Button.builder(Component.literal(label), b -> VaultSortHandler.sortPets(mode, minecraft.hasShiftDown()))
                .bounds(x, y, width, 20)
                .tooltip(Tooltip.create(Component.literal(tooltip)))
                .build();
    }

    private Button sortButton(String label, VaultSortHandler.Mode mode, String tooltip, int x, int y, int width) {
        return Button.builder(Component.literal(label), b -> VaultSortHandler.sort(mode, minecraft.hasShiftDown()))
                .bounds(x, y, width, 20)
                .tooltip(Tooltip.create(Component.literal(tooltip)))
                .build();
    }

    private static int getCurrentPage(Screen screen) {
        String title = screen.getTitle().getString();
        try {
            return Integer.parseInt(title.substring(title.lastIndexOf('#') + 1).trim());
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return -1;
        }
    }

    private static Component pageTooltip(int page) {
        // The server's rank badges (resource pack glyphs), the same ones shown in chat and tab.
        String[] rankBadges = {"", "", "\uF032", "\uF031", "\uF030", "\uF029", "\uF028", "\uF028"}; // angler, sailor, mariner, captain, admiral
        MutableComponent tooltip = Component.literal("Page " + page).withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE);
        if (page > 1) {
            tooltip.append(Component.literal("\n\nRequires at least ").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC))
                    .append(Component.literal(rankBadges[page]).withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE).withBold(false).withItalic(false)));
        }
        return tooltip;
    }

    @Override
    public void render(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
        super.render(screen, guiGraphics, mouseX, mouseY, tickDelta);

        if(searchBarWidget != null) searchBarWidget.renderHelp(guiGraphics, mouseX, mouseY, tickDelta);
    }

    public boolean checkMouseScroll(Screen screen, double mouseX, double mouseY, double horizontalAmount, double verticalAmount, boolean consumed) {
        if(screen instanceof ContainerScreen containerScreen && Configs.keyBindConfig.scrollWheelScrolling.get()) {
            int syncId = containerScreen.getMenu().containerId;
            int moveSlot = -1;

            if(verticalAmount > 0) moveSlot = 46;
            else if (verticalAmount < 0) moveSlot = 52;

            if(moveSlot != -1
                    && !containerScreen.getMenu().getSlot(moveSlot).getItem().isEmpty()
                    && containerScreen.getMenu().getSlot(moveSlot).getItem().getItem() == Items.GUNPOWDER
                    && minecraft.gameMode != null
            ) {
                minecraft.gameMode.handleInventoryMouseClick(
                        syncId,
                        moveSlot,
                        0,
                        ClickType.PICKUP,
                        minecraft.player
                );
            }
        }
        return false;
    }

    public boolean checkMouseClick(Screen screen, MouseButtonEvent context, boolean consumed) {
        searchBarWidget.setFocused(searchBarWidget.isMouseOver(context.x(), context.y()));
        SearchHandler.instance().setFocused(searchBarWidget.isMouseOver(context.x(), context.y()));

        return consumed;
    }

    public void onClose(Screen screen) {
        screen.setFocused(null);
        Screens.getButtons(screen).remove(searchBarWidget);
        SearchHandler.instance().setFocused(false);
        SearchHandler.instance().setOnScreen(false);
        searchBarWidget = null;
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
