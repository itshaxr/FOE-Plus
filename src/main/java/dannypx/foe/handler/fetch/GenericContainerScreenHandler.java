package dannypx.foe.handler.fetch;

import dannypx.foe.handler.Handler;
import dannypx.foe.handler.logic.CodeExecuterHandler;
import dannypx.foe.handler.logic.EventHandler;
import dannypx.foe.handler.renderer.*;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.config.Configs;
import java.util.Map;
import java.util.Objects;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

public class GenericContainerScreenHandler extends Handler {
    private static GenericContainerScreenHandler INSTANCE = new GenericContainerScreenHandler();

    public static GenericContainerScreenHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new GenericContainerScreenHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private String lastContainerScreen = "";

    public String getLastContainerScreen() {
        return lastContainerScreen;
    }

    public static final String QUEST_SCREEN_CONTAINER = "\uEEE4\uD539";
    public static final String STATS_SCREEN_CONTAINER = "\uEEE4\uD532";
    public static final String AUCTION_HOUSE_SCREEN_CONTAINER = "\uEEE4\uD543";
    public static final String PERSONAL_VAULT_SCREEN_CONTAINER = "Personal Vault #";
    public static final String ARMOR_MENU_SCREEN_CONTAINER = "Armor Menu";
    public static final String STORAGE_SCREEN_CONTAINER = " ";
    public static final String GENERIC_SCREEN_CONTAINER = "\uEEE4\uD552";
    public static final String PRESETS_SCREEN_CONTAINER = "Presets\uEEE6\uEEE5\uD572";
    public static final String TACKLE_BOX_SCREEN_CONTAINER = "Tacklebox\uEEE6\uEEE5\uEEE4\uEEE2핖";
    //endregion

    //region Methods
    public void init(ContainerScreen genericContainerScreen) {
        if(!Configs.handlerConfig.genericContainerScreenHandler.get()) {
            return;
        }

        this.checkIsOfTitle(genericContainerScreen);
    }

    private void checkIsOfTitle(ContainerScreen genericContainerScreen) {
        this.lastContainerScreen = genericContainerScreen.getTitle().getString();

        // Quest Screen
        if (Objects.equals(genericContainerScreen.getTitle().getString(), QUEST_SCREEN_CONTAINER)) {
            QuestScreenHandler.instance().init(genericContainerScreen);
        }

        // Main Menu (holds the Daily Missions item for the Daily Quests panel)
        else if (Objects.equals(genericContainerScreen.getTitle().getString(), dannypx.foe.handler.logic.DailyQuestHandler.MAIN_MENU_SCREEN_CONTAINER)) {
            dannypx.foe.handler.logic.DailyQuestHandler.instance().setMenuOpen(true);
            ScreenEvents.remove(genericContainerScreen).register(screen -> dannypx.foe.handler.logic.DailyQuestHandler.instance().setMenuOpen(false));
        }

        // Stats Screen
        else if (Objects.equals(genericContainerScreen.getTitle().getString(), STATS_SCREEN_CONTAINER)) {
            StatsScreenHandler.instance().init(genericContainerScreen);
        }

        // Auction House Screen
        else if (Objects.equals(genericContainerScreen.getTitle().getString(), AUCTION_HOUSE_SCREEN_CONTAINER)) {
            AuctionHouseScreenRenderHandler.instance().init(genericContainerScreen);
            ScreenMouseEvents.afterMouseScroll(genericContainerScreen).register(AuctionHouseScreenRenderHandler.instance()::checkMouseScroll);
            ScreenMouseEvents.afterMouseClick(genericContainerScreen).register(AuctionHouseScreenRenderHandler.instance()::checkMouseClick);
            ScreenEvents.remove(genericContainerScreen).register(AuctionHouseScreenRenderHandler.instance()::onClose);
        }

        // Personal Vault Screen
        else if (genericContainerScreen.getTitle().getString().startsWith(PERSONAL_VAULT_SCREEN_CONTAINER)) {
            PersonalVaultScreenRenderHandler.instance().init(genericContainerScreen);
            ScreenMouseEvents.afterMouseScroll(genericContainerScreen).register(PersonalVaultScreenRenderHandler.instance()::checkMouseScroll);
            ScreenMouseEvents.afterMouseClick(genericContainerScreen).register(PersonalVaultScreenRenderHandler.instance()::checkMouseClick);
            ScreenEvents.remove(genericContainerScreen).register(PersonalVaultScreenRenderHandler.instance()::onClose);
        }

        // Tackle Box Screen
        else if(genericContainerScreen.getTitle().getString().startsWith(TACKLE_BOX_SCREEN_CONTAINER)) {
            TackleBoxScreenRenderHandler.instance().init(genericContainerScreen);
            ScreenMouseEvents.afterMouseClick(genericContainerScreen).register(TackleBoxScreenRenderHandler.instance()::checkMouseClick);
            ScreenEvents.remove(genericContainerScreen).register(TackleBoxScreenRenderHandler.instance()::onClose);
        }

        // Any Container Screen
        else if (Objects.equals(genericContainerScreen.getTitle().getString(), STORAGE_SCREEN_CONTAINER)) {
            ChestScreenRenderHandler.instance().init(genericContainerScreen);
            ScreenEvents.remove(genericContainerScreen).register(ChestScreenRenderHandler.instance()::onClose);
            ScreenMouseEvents.afterMouseClick(genericContainerScreen).register(ChestScreenRenderHandler.instance()::checkMouseClick);
        }

        // Armor Menu Screen
        else if (genericContainerScreen.getTitle().getString().startsWith(ARMOR_MENU_SCREEN_CONTAINER)) {
            ArmorRollScreenHandler.instance().checkArmorRolls(genericContainerScreen.getMenu());
        }

        // Presets Screen
        else if (Objects.equals(genericContainerScreen.getTitle().getString(), PRESETS_SCREEN_CONTAINER)) {
            ScreenMouseEvents.afterMouseScroll(genericContainerScreen).register(PresetsScreenRenderHandler.instance()::checkMouseScroll);
        }

        // Other Generic Screen
        else if(Objects.equals(genericContainerScreen.getTitle().getString(), GENERIC_SCREEN_CONTAINER)) {
            this.checkIsOfItem(genericContainerScreen);
        }
    }

    private void checkIsOfItem(ContainerScreen genericContainerScreen) {
        CodeExecuterHandler.runLater(2, () -> {
            ChestMenu genericContainerScreenHandler = genericContainerScreen.getMenu();

            // Crew Info
            ItemStack crewInfoStack = genericContainerScreenHandler.getSlot(13).getItem();
            if(Objects.equals(crewInfoStack.getHoverName().getString(), "Crew Info")) {
                CrewScreenHandler.instance().checkCrewInfo(genericContainerScreenHandler);
            }
        });
    }
    //endregion

    //region Dev
    /// Field, Pair<Value, Tooltip>
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
                "lastContainerScreen", Pair.of(Component.literal(getLastContainerScreen()), Component.empty())
        );
    }

    public void render(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
        if(screen instanceof ContainerScreen genericContainerScreen) {
            if (Objects.equals(genericContainerScreen.getTitle().getString(), AUCTION_HOUSE_SCREEN_CONTAINER)) {
                AuctionHouseScreenRenderHandler.instance().renderButtonHelp(guiGraphics, true, true);
                AuctionHouseScreenRenderHandler.instance().render(screen, guiGraphics, mouseX, mouseY, tickDelta);
            } else if (genericContainerScreen.getTitle().getString().startsWith(PERSONAL_VAULT_SCREEN_CONTAINER)) {
                PersonalVaultScreenRenderHandler.instance().renderButtonHelp(guiGraphics, true, true);
                PersonalVaultScreenRenderHandler.instance().render(screen, guiGraphics, mouseX, mouseY, tickDelta);
            } else if (Objects.equals(genericContainerScreen.getTitle().getString(), STORAGE_SCREEN_CONTAINER)) {
                ChestScreenRenderHandler.instance().renderButtonHelp(guiGraphics, true, false);
                ChestScreenRenderHandler.instance().render(screen, guiGraphics, mouseX, mouseY, tickDelta);
            } else if (Objects.equals(genericContainerScreen.getTitle().getString(), PRESETS_SCREEN_CONTAINER)) {
                PresetsScreenRenderHandler.instance().renderButtonHelp(guiGraphics, true, true);
            } else if(Objects.equals(genericContainerScreen.getTitle().getString(), TACKLE_BOX_SCREEN_CONTAINER)) {
                TackleBoxScreenRenderHandler.instance().renderButtonHelp(guiGraphics, true, false);
                TackleBoxScreenRenderHandler.instance().render(screen, guiGraphics, mouseX, mouseY, tickDelta);
            }
        }
    }
    //endregion
}
