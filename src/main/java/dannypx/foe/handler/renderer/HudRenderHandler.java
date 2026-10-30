package dannypx.foe.handler.renderer;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.Handler;
import dannypx.foe.handler.fetch.LocalPlayerHandler;
import dannypx.foe.handler.fetch.ScoreboardHandler;
import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.handler.logic.LoadingHandler;
import dannypx.foe.handler.logic.LoggerHandler;
import dannypx.foe.handler.fetch.HitResultHandler;
import dannypx.foe.handler.store.CustomHudDataHandler;
import dannypx.foe.handler.store.CustomHudIconDataHandler;
import dannypx.foe.helper.GuiGraphicsHelper;
import dannypx.foe.item.TagObject;
import dannypx.foe.item.ValidateItem;
import dannypx.foe.config.Configs;
import dannypx.foe.screens.element.*;
import dannypx.foe.type.StringStyle;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.screens.element.hud.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HudRenderHandler extends Handler {
    private static HudRenderHandler INSTANCE = new HudRenderHandler();

    public static HudRenderHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new HudRenderHandler();
        }
        return INSTANCE;
    }

    //region Fields
    List<Pair<String, Element>> elements = new ArrayList<>();
    List<Pair<String, Element>> customHudElements = new ArrayList<>();
    //endregion

    //region Methods
    public void initializeHudRenderer() {
        registerElements();
    }

    public void tick() {
        if(CustomHudDataHandler.instance().needsRenderUpdate || CustomHudIconDataHandler.instance().needsRenderUpdate) {
            customHudElements.clear();
            CustomHudDataHandler.instance().getCustomHudData().customHudRawDataList.forEach((key, hud) -> {
                LoggerHandler.info("Register Custom Element: " + key);
                customHudElements.add(Pair.of(key, new CustomHudElement(hud, Component.literal(key))));
            });

            CustomHudIconDataHandler.instance().getCustomHudIconData().customHudIconDataList.forEach((key, icon) -> {
                LoggerHandler.info("Register Custom Icon Element: " + key);
                customHudElements.add(Pair.of(key, new CustomHudIconElement(icon, Component.literal(key))));
            });

            CustomHudDataHandler.instance().needsRenderUpdate = false;
            CustomHudIconDataHandler.instance().needsRenderUpdate = false;
        }
    }

    private void registerElements() {
        if(elements.isEmpty()) {
            elements.add(Pair.of("profile_hud", new ProfileElement()));
            elements.add(Pair.of("location_hud", new LocationElement()));
            elements.add(Pair.of("hotbar_hud", new HotbarElement()));
            elements.add(Pair.of("pet_hud", new PetElement()));
            elements.add(Pair.of("notifier_hud", new NotifierElement()));
            // FOE-style info panels (ported from FOECollab)
            elements.add(Pair.of("contest_panel", new ContestPanelElement()));
            elements.add(Pair.of("daily_quest_panel", new DailyQuestPanelElement()));
            elements.add(Pair.of("stats_panel", new StatsPanelElement()));
            elements.add(Pair.of("quest_panel", new QuestPanelElement()));
            elements.add(Pair.of("debug_field_hud", new _DebugField()));
        }


        LoggerHandler.info("Register Elements");
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "foer_hud_foreground"), (guiGraphics, deltaTracker) -> {
            elements.forEach(element -> {
                if (Configs.mainConfig.enableMod.get()) element.value2().render(guiGraphics);
            });

            if (Configs.mainConfig.enableMod.get()) this.render(guiGraphics);
        });

        LoggerHandler.info("Register Priority Elements");
        HudElementRegistry.attachElementAfter(VanillaHudElements.EXPERIENCE_LEVEL, Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "foer_hud_priority"), (guiGraphics, deltaTracker) -> {
            if (Configs.mainConfig.enableMod.get()) this.renderAfterSubtitles(guiGraphics);
        });
    }

    private void render(GuiGraphics guiGraphics) {
        customHudElements.forEach(element -> element.value2().render(guiGraphics));
    }

    private void renderAfterSubtitles(GuiGraphics guiGraphics) {
        this.renderTooltip(guiGraphics);

        if((!LoadingHandler.instance().isLoadingDone()
                || ScoreboardHandler.instance().isNoScoreboard()
                || !ScoreboardHandler.instance().getLevel().getString().trim().equals(String.valueOf(LocalPlayerHandler.instance().getExperienceLevel()))) &&
                ConnectionHandler.instance().isOnServer()
        ) this.renderLoading(guiGraphics);

//        if(LoadingHandler.instance().isLoadingDone()) {
//            int posX = minecraft.getWindow().getGuiScaledWidth() / 2 - 200;
//            int posY = minecraft.getWindow().getGuiScaledHeight() / 2;
//
//            GuiGraphicsHelper.drawString(guiGraphics, minecraft.font, Component.literal("Test"), posX, posY, StringStyle.SHADOW);
//
//            List<PlaceholderResult> results = List.of(
//                    PlaceholderHandlerV2.instance().resolve("%test.segment_first% %test.segment_first%"),
//                    PlaceholderHandlerV2.instance().resolve("&7fps&8: &e%player.fps%"),
//                    PlaceholderHandlerV2.instance().resolve("&7x&8: &e%player.pos.x% &8/ &7y&8: &e%player.pos.y% &8/ &7z&8: &e%player.pos.z%"),
//                    PlaceholderHandlerV2.instance().resolve("%boss_bar.location% | %boss_bar.sub_location%"),
//                    PlaceholderHandlerV2.instance().resolve("%expression.(<expression.(<player.fps>*10)>*10)%")
//            );
//
//            AtomicInteger line = new AtomicInteger(0);
//            for (PlaceholderResult result : results) {
//                GuiGraphicsHelper.drawString(guiGraphics, minecraft.font,
//                        Component.empty().append(result.success() + " " + result.errors().size() + ": ").append(result.text()),
//                        posX, posY + (minecraft.font.lineHeight + 2) * line.incrementAndGet(), StringStyle.SHADOW
//                );
//            }
//        }
    }

    private void renderLoading(GuiGraphics guiGraphics) {
        long time = System.currentTimeMillis();
        int dotCount = (int)((time / 1000) % 4);

        Component loadingComponent = Component.literal("Loading FOER" + ".".repeat(dotCount));

        GuiGraphicsHelper.drawString(guiGraphics, minecraft.font, loadingComponent,
                minecraft.getWindow().getGuiScaledWidth() - minecraft.font.width(loadingComponent) - 8,
                minecraft.getWindow().getGuiScaledHeight() - minecraft.font.lineHeight - 8,
                StringStyle.SHADOW, StringStyle.MIDDLE, StringStyle.SMALL_CAPS
        );
    }

    private void renderTooltip(GuiGraphics guiGraphics) {
        if (Configs.mainConfig.enableMod.get()
                && LoadingHandler.instance().isLoadingDone()
                && HitResultHandler.instance().getItemFrameItem() != ItemStack.EMPTY) {
            Pair<Boolean, TagObject> validatedItem = ValidateItem.isServerItem(HitResultHandler.instance().getItemFrameItem(), true);
            if (validatedItem.value1()) {
                int itemX = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2;
                int itemY = Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2;
                ItemStack itemStack = validatedItem.value2().getItemStack();

                List<Component> tooltipComponents = Screen.getTooltipFromItem(minecraft, itemStack);
                List<ClientTooltipComponent> clientTooltipComponents = tooltipComponents.stream()
                        .map(Component::getVisualOrderText)
                        .map(ClientTooltipComponent::create)
                        .collect(Util.toMutableList());
                itemStack.getTooltipImage()
                        .ifPresent(tooltipComponent ->
                                clientTooltipComponents.add(clientTooltipComponents.isEmpty() ? 0 : 1, ClientTooltipComponent.create(tooltipComponent))
                        );

                guiGraphics.renderTooltip(minecraft.font, clientTooltipComponents, itemX, itemY, DefaultTooltipPositioner.INSTANCE, itemStack.get(DataComponents.TOOLTIP_STYLE));
            }
        }
    }
    //endregion

    //region Dev
    /// Field, Pair<Value, Tooltip>
    @Override
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(

        );
    }
    //endregion
}
