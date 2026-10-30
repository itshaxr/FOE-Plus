package dannypx.foe;

import dannypx.foe.command.CommandRegistry;
import dannypx.foe.entity.FishingHookEntityModel;
import dannypx.foe.entity.RodCubeLayer;
import dannypx.foe.handler.fetch.*;
import dannypx.foe.handler.io.ChangelogFetcherHandler;
import dannypx.foe.handler.logic.*;
import dannypx.foe.handler.renderer.*;
import dannypx.foe.handler.store.*;
import dannypx.foe.handler.io.DataFileHandler;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.PlaceholderHandler;
import dannypx.foe.nearby_dispatcher.types.ModUserType;
import dannypx.foe.nearby_dispatcher.types.NearbyBigType;
import dannypx.foe.nearby_dispatcher.NearbyDispatcher;
import dannypx.foe.nearby_dispatcher.types.HatType;
import dannypx.foe.nearby_dispatcher.types.RodType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class FishOnMCExtrasClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        this.onInit();

        ClientLifecycleEvents.CLIENT_STARTED.register(this::onClientStarted);
        ClientPlayConnectionEvents.JOIN.register(this::onJoin);
        ClientPlayConnectionEvents.DISCONNECT.register(this::onLeave);
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
                overlay || !ContestTrackerHandler.instance().hideAutoRefreshMessage(message));
        ClientReceiveMessageEvents.GAME.register(this::receiveGameMessage);
        ClientSendMessageEvents.MODIFY_CHAT.register(this::modifyChatMessage);
        ClientEntityEvents.ENTITY_UNLOAD.register(this::onEntityUnload);
        ScreenEvents.AFTER_INIT.register(this::onAfterInitScreen);
        ScreenEvents.BEFORE_INIT.register((minecraft, screen, w, h) -> {
            // FOE title screen (ported from FOECollab)
            if (screen instanceof net.minecraft.client.gui.screens.TitleScreen
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mainConfig.useCustomTitleScreen.get()) {
                minecraft.setScreen(new dannypx.foe.screens.FoeTitleScreen());
            }
        });
        ItemTooltipCallback.EVENT.register(this::onItemTooltip);
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(this::onLivingEntityFeatureRenderingRegistrationCallback);

        this.initHudRenderer();
    }

    private void onLivingEntityFeatureRenderingRegistrationCallback(EntityType<? extends LivingEntity> entityType, LivingEntityRenderer<?,?,?> livingEntityRenderer, LivingEntityFeatureRendererRegistrationCallback.RegistrationHelper registrationHelper, EntityRendererProvider.Context context) {
        if (entityType == EntityType.PLAYER && livingEntityRenderer instanceof AvatarRenderer<?> avatarRenderer) {
            registrationHelper.register(RodCubeLayer.create(avatarRenderer));
        }
    }

    private void onItemTooltip(ItemStack itemStack, Item.TooltipContext tooltipContext, TooltipFlag tooltipType, List<Component> lines) {
        TooltipHandler.instance().fetchTooltip(itemStack, tooltipContext, tooltipType, lines);
    }

    private void onClientStarted(Minecraft minecraftClient) {
        if(minecraftClient.options.guiScale().get() == 0) {
            minecraftClient.options.guiScale().set(3);
            minecraftClient.options.save();
            minecraftClient.resizeDisplay();
        }
    }

    private void receiveGameMessage(Component message, boolean overlay) {
        ChatHandler.instance().onReceiveMessage(message);
    }

    private String modifyChatMessage(String text) {
        return ChatHandler.instance().onModifyChatMessage(text);
    }

    private void onAfterInitScreen(Minecraft minecraft, Screen screen, int scaledWidth, int scaledHeight) {
        ScreenHander.instance().onAfterInitScreen(minecraft, screen, scaledWidth, scaledHeight);
    }

    private void onEntityUnload(Entity entity, ClientLevel clientLevel) {
        ChummerHandler.instance().onEntityUnload(entity, clientLevel);
    }

    private void initHudRenderer() {
        HudRenderHandler.instance().initializeHudRenderer();
    }

    private void onInit() {
        this.registerEntityModels();
        CodeExecuterHandler.instance().init();
        CommandRegistry.init();
        ParticleHandler.instance().init();
        PlaceholderHandler.instance().init();
        ChangelogFetcherHandler.instance().fetch(false);

        NearbyDispatcher.init();
    }

    private void onLeave(ClientPacketListener clientPacketListener, Minecraft minecraft) {
        ConnectionHandler.instance().onLeave();
        LoadingHandler.instance().onLeave();

        InventoryHandler.instance().onLeave();
        ChummerHandler.instance().onLeave();
        TextDisplayHandler.instance().onLeave();
    }

    private void onJoin(ClientPacketListener clientPacketListener, PacketSender packetSender, Minecraft minecraft) {
        ConnectionHandler.instance().init();
        if(ConnectionHandler.instance().isOnServer()) {
            ProfileDataHandler.instance().init();
            StatsDataHandler.instance().init();
            ConstantDataHandler.instance().init();
            QuestDataHandler.instance().init();
            CrewDataHandler.instance().init();
            TextDisplayHandler.instance().init();
            CustomHudDataHandler.instance().init();
            CustomHudIconDataHandler.instance().init();
            CustomButtonDataHandler.instance().init();
            CustomNotificationDataHandler.instance().init();
            CustomChatTriggerDataHandler.instance().init();
            CustomChatNotificationDataHandler.instance().init();
            CustomTimerDataHandler.instance().init();
            CustomEventTriggerDataHandler.instance().init();
            CustomTrackerDataHandler.instance().init();
            CustomSnippetDataHandler.instance().init();

            ScoreboardHandler.instance().init();
            CrewHandler.instance().init();

            DataFileHandler.instance().init();
            LoadingHandler.instance().init();

            ChatHandler.instance().init();
            NotifierHandler.instance().init();
            TimerHandler.instance().init();
        }
    }

    private void onEndClientTick(Minecraft minecraft) {
        if(!LoadingHandler.instance().isError()
                && minecraft.getCurrentServer() != null
                // Check if on server before ticking
                && ConnectionHandler.instance().isOnServer()
                && Configs.mainConfig.enableMod.get()
        ) {
            // Check if done loading
            if(LoadingHandler.instance().isLoadingDone()) {
                // Fetch
                if(Configs.handlerConfig.tabOverlayHandler.get()) TabOverlayHandler.instance().tick();
                if(Configs.handlerConfig.scoreboardHandler.get()) ScoreboardHandler.instance().tick();
                if(Configs.handlerConfig.localPlayerHandler.get()) LocalPlayerHandler.instance().tick();
                if(Configs.handlerConfig.bossEventHandler.get()) BossEventHandler.instance().tick();
                if(Configs.handlerConfig.inventoryHandler.get()) InventoryHandler.instance().tick();
                if(Configs.handlerConfig.networkHandler.get()) NetworkHandler.instance().tick();

                // IO
                if(Configs.handlerConfig.dataFileHandler.get()) DataFileHandler.instance().tick();

                // Logic
                if(Configs.handlerConfig.keyBindHandler.get()) KeyBindHandler.instance().tick();
                BaitSorterHandler.instance().tick(minecraft);
                ContestTrackerHandler.instance().tick();
                ContestBaitSheetHandler.instance().tick();
                AutotipHandler.instance().tick();
                DailyQuestHandler.instance().tick();
                FishSessionHandler.instance().tick();
                if(Configs.handlerConfig.catchingHandler.get()) CatchingHandler.instance().tick();
                if(Configs.handlerConfig.rayCastHandler.get()) HitResultHandler.instance().tick();
                if(Configs.handlerConfig.notifierHandler.get()) NotifierHandler.instance().tick();
                if(Configs.handlerConfig.crewHandler.get()) CrewHandler.instance().tick();
                if(Configs.handlerConfig.lightHandler.get()) LightHandler.instance().tick();
                if(Configs.handlerConfig.timerHandler.get()) TimerHandler.instance().tick();
                if(Configs.handlerConfig.questHandler.get()) QuestHandler.instance().tick();

                // Renderer
                if(Configs.handlerConfig.hudRenderHandler.get()) HudRenderHandler.instance().tick();

                // Placeholder Engine
                PlaceholderHandler.instance().tick();

            } else {
                if(Configs.handlerConfig.loadingHandler.get()) LoadingHandler.instance().tick();
            }
        }
    }

    private void registerEntityModels() {
        EntityModelLayerRegistry.registerModelLayer(FishingHookEntityModel.MODEL_LAYER, FishingHookEntityModel::generateModel);
    }
}
