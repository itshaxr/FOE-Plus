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
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> dannypx.foe.helper.Safe.run("join", () -> this.onJoin(handler, sender, mc)));
        ClientPlayConnectionEvents.DISCONNECT.register(this::onLeave);
        ClientTickEvents.END_CLIENT_TICK.register(safeTick(this::onEndClientTick));
        // Test builds: the map scan (/foe map scan), wherever you are.
        ClientTickEvents.END_CLIENT_TICK.register(safeTick(mc -> dannypx.foe.handler.logic.FoeProfiler.tick()));
        ClientTickEvents.END_CLIENT_TICK.register(safeTick(mc -> dannypx.foe.handler.logic.SpotHandler.instance().tick(mc)));
        // FOE+'s data files: changes written together every half minute, and when you leave or close the game
        ClientTickEvents.END_CLIENT_TICK.register(safeTick(mc -> dannypx.foe.handler.io.DataFileHandler.instance().flushIfDue()));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> dannypx.foe.handler.io.DataFileHandler.instance().flush(true));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> dannypx.foe.helper.Safe.run("tick MapScan", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.logic.MapScanHandler.instance().tick(mc); dannypx.foe.handler.logic.FoeProfiler.end("tick MapScan", _p); }));
        // "[item]" in chat: Tab completes it.
        ClientTickEvents.END_CLIENT_TICK.register(safeTick(dannypx.foe.handler.logic.ChatCompletionsHandler::tick));
        ClientTickEvents.END_CLIENT_TICK.register(safeTick(mc -> dannypx.foe.handler.logic.MinigameDumpHandler.instance().tick(mc)));
        ClientTickEvents.END_CLIENT_TICK.register(safeTick(mc -> { if (mc.player != null) dannypx.foe.handler.logic.CommunityRateHandler.instance().tick(); }));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> dannypx.foe.helper.Safe.run("tick Baitbox", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.logic.BaitboxHandler.instance().tick(mc); dannypx.foe.handler.logic.FoeProfiler.end("tick Baitbox", _p); }));
        ClientTickEvents.END_CLIENT_TICK.register(safeTick(dannypx.foe.handler.logic.MemoryGuard::tick));
        ClientTickEvents.END_CLIENT_TICK.register(safeTick(mc -> dannypx.foe.handler.logic.InstanceJoinHandler.instance().tick(mc)));
        // where a new pet came from (fished, a contest, an event): every message, before anything hides one
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
                overlay || dannypx.foe.helper.Safe.get("chat pet source", () -> dannypx.foe.handler.logic.PetSourceHandler.instance().onGameMessage(message.getString()), true));
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            long _p = dannypx.foe.handler.logic.FoeProfiler.mark();
            // a reader that fails shows the line rather than crashing (or hiding it)
            boolean keep = overlay || dannypx.foe.helper.Safe.get("chat FOE+ readers", () -> (!dannypx.foe.handler.logic.SilentMenuHandler.instance().hideCooldownReply(message.getString())
                        && !dannypx.foe.handler.logic.CommunityEventHandler.instance().onGameMessage(message.getString())
                        && !dannypx.foe.handler.logic.EventTimesHandler.instance().onGameMessage(message)
                        && !dannypx.foe.handler.logic.FindHandler.instance().onGameMessage(message)
                        && !dannypx.foe.handler.logic.FriendInfoHandler.instance().onGameMessage(message)
                        && !dannypx.foe.handler.logic.ActiveBoostersHandler.instance().onGameMessage(message)
                        && !dannypx.foe.handler.logic.MiniEventHandler.instance().onGameMessage(message)
                        && ContestTrackerHandler.instance().filterGameMessage(message)), true);
            dannypx.foe.handler.logic.FoeProfiler.end("chat FOE+ readers", _p);
            return keep;
        });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> dannypx.foe.helper.Safe.run("player chat", () -> {
            ContestTrackerHandler.instance().onChatMessageShown();
            // your own chat message: how your level is coloured (for the top bar)
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) dannypx.foe.handler.logic.LevelStyleHandler.instance().onChat(message, player.getGameProfile().name());
        }));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> dannypx.foe.helper.Safe.run("game message", () -> this.receiveGameMessage(message, overlay)));
        ClientSendMessageEvents.MODIFY_CHAT.register(message -> dannypx.foe.helper.Safe.get("chat send", () -> this.modifyChatMessage(message), message));
        ClientSendMessageEvents.COMMAND.register(command -> dannypx.foe.handler.logic.SilentMenuHandler.instance().onMessageSent());
        ClientSendMessageEvents.CHAT.register(message -> dannypx.foe.handler.logic.SilentMenuHandler.instance().onMessageSent());
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> dannypx.foe.helper.Safe.run("entity unload", () -> this.onEntityUnload(entity, level)));
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> dannypx.foe.helper.Safe.run("screen " + screen.getClass().getSimpleName(), () -> this.onAfterInitScreen(mc, screen, w, h)));
        ScreenEvents.BEFORE_INIT.register((minecraft, screen, w, h) -> {
            // FOE title screen (ported from FOECollab)
            if (screen instanceof net.minecraft.client.gui.screens.TitleScreen
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mainConfig.useCustomTitleScreen.get()) {
                minecraft.setScreen(new dannypx.foe.screens.FoeTitleScreen());
            }
        });
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            long _p = dannypx.foe.handler.logic.FoeProfiler.mark();
            dannypx.foe.helper.Safe.run("item tooltip", () -> this.onItemTooltip(stack, context, type, lines));
            dannypx.foe.helper.Safe.run("item dump", () -> dannypx.foe.handler.logic.ItemDumpHandler.instance().onTooltip(stack));
            dannypx.foe.handler.logic.FoeProfiler.end("items: tooltips", _p);
        });
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
        long _p = dannypx.foe.handler.logic.FoeProfiler.mark();
        ChatHandler.instance().onReceiveMessage(message);
        if (!overlay) dannypx.foe.handler.logic.RareCatchHandler.onGameMessage(message.getString());
        if (!overlay) dannypx.foe.handler.logic.StoreTimerHandler.instance().onGameMessage(message.getString());
        dannypx.foe.handler.logic.FoeProfiler.end("chat triggers", _p);
    }

    private String modifyChatMessage(String text) {
        return ChatHandler.instance().onModifyChatMessage(text);
    }

    private void onAfterInitScreen(Minecraft minecraft, Screen screen, int scaledWidth, int scaledHeight) {
        ScreenHander.instance().onAfterInitScreen(minecraft, screen, scaledWidth, scaledHeight);
        if (ConnectionHandler.instance().isOnServer()) dannypx.foe.handler.logic.StoreTimerHandler.instance().onScreenOpened(screen.getTitle().getString());
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
        // FOE+ updates: ask GitHub early so the answer is ready by the time you join.
        if (Configs.mainConfig.updateCheck.get()) dannypx.foe.handler.io.UpdateCheckHandler.instance().fetch();

        NearbyDispatcher.init();
    }

    private void onLeave(ClientPacketListener clientPacketListener, Minecraft minecraft) {
        dannypx.foe.handler.io.DataFileHandler.instance().flush(true);   // nothing left unwritten
        ConnectionHandler.instance().onLeave();
        LoadingHandler.instance().onLeave();
        dannypx.foe.handler.logic.SessionStatsHandler.instance().reset(); // a session ends at logout

        InventoryHandler.instance().onLeave();
        ChummerHandler.instance().onLeave();
        TextDisplayHandler.instance().onLeave();
        dannypx.foe.handler.io.UpdateCheckHandler.instance().onLeave();
    }

    private void onJoin(ClientPacketListener clientPacketListener, PacketSender packetSender, Minecraft minecraft) {
        ConnectionHandler.instance().init();
        if(ConnectionHandler.instance().isOnServer()) {
            ProfileDataHandler.instance().init();
            StatsDataHandler.instance().init();
            dannypx.foe.handler.logic.ProfileStatsHandler.instance().onJoin();
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

    /// A tick listener that can't crash the game: a failure is logged once and skipped (Safe).
    private static ClientTickEvents.EndTick safeTick(ClientTickEvents.EndTick tick) {
        return mc -> dannypx.foe.helper.Safe.run("tick " + tick.getClass().getSimpleName(), () -> tick.onEndTick(mc));
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
                dannypx.foe.helper.Safe.run("tick FriendsHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.logic.FriendsHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick FriendsHandler", _p); });
                dannypx.foe.helper.Safe.run("tick UpdateCheckHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.io.UpdateCheckHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick UpdateCheckHandler", _p); });
                // Fetch
                dannypx.foe.helper.Safe.run("tick TabOverlayHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); if(Configs.handlerConfig.tabOverlayHandler.get()) TabOverlayHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick TabOverlayHandler", _p); });
                dannypx.foe.helper.Safe.run("tick ScoreboardHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); if(Configs.handlerConfig.scoreboardHandler.get()) ScoreboardHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick ScoreboardHandler", _p); });
                dannypx.foe.helper.Safe.run("tick LocalPlayerHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); if(Configs.handlerConfig.localPlayerHandler.get()) LocalPlayerHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick LocalPlayerHandler", _p); });
                dannypx.foe.helper.Safe.run("tick BossEventHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); if(Configs.handlerConfig.bossEventHandler.get()) BossEventHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick BossEventHandler", _p); });
                dannypx.foe.helper.Safe.run("tick InventoryHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); if(Configs.handlerConfig.inventoryHandler.get()) InventoryHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick InventoryHandler", _p); });
                dannypx.foe.helper.Safe.run("tick NetworkHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); if(Configs.handlerConfig.networkHandler.get()) NetworkHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick NetworkHandler", _p); });

                // IO
                dannypx.foe.helper.Safe.run("tick DataFileHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); if(Configs.handlerConfig.dataFileHandler.get()) DataFileHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick DataFileHandler", _p); });

                // Logic
                dannypx.foe.helper.Safe.run("tick KeyBindHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); if(Configs.handlerConfig.keyBindHandler.get()) KeyBindHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick KeyBindHandler", _p); });
                dannypx.foe.helper.Safe.run("tick BaitSorterHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); BaitSorterHandler.instance().tick(minecraft); dannypx.foe.handler.logic.FoeProfiler.end("tick BaitSorterHandler", _p); });
                dannypx.foe.helper.Safe.run("tick ContestTrackerHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); ContestTrackerHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick ContestTrackerHandler", _p); });
                dannypx.foe.helper.Safe.run("tick ContestBaitSheetHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); ContestBaitSheetHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick ContestBaitSheetHandler", _p); });
                dannypx.foe.helper.Safe.run("tick BaitPictures", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.store.BaitPictures.tick(minecraft); dannypx.foe.handler.logic.FoeProfiler.end("tick BaitPictures", _p); });
                dannypx.foe.helper.Safe.run("tick AutotipHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); AutotipHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick AutotipHandler", _p); });
                dannypx.foe.helper.Safe.run("tick FullInventoryHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); FullInventoryHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick FullInventoryHandler", _p); });
                dannypx.foe.helper.Safe.run("tick PetPriceSheetHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); if (dannypx.foe.screens.PetPriceScreen.ENABLED) PetPriceSheetHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick PetPriceSheetHandler", _p); });
                dannypx.foe.helper.Safe.run("tick DailyQuestHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); DailyQuestHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick DailyQuestHandler", _p); });
                dannypx.foe.helper.Safe.run("tick ProfileStatsHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.logic.ProfileStatsHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick ProfileStatsHandler", _p); });
                dannypx.foe.helper.Safe.run("tick SilentMenuHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.logic.SilentMenuHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick SilentMenuHandler", _p); });
                dannypx.foe.helper.Safe.run("tick AlertHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.logic.AlertHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick AlertHandler", _p); });
                dannypx.foe.helper.Safe.run("tick CommunityEventHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.logic.CommunityEventHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick CommunityEventHandler", _p); });
                dannypx.foe.helper.Safe.run("tick EventTimesHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.logic.EventTimesHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick EventTimesHandler", _p); });
                dannypx.foe.helper.Safe.run("tick WeatherEventHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.logic.WeatherEventHandler.instance().tick(dannypx.foe.handler.fetch.BossEventHandler.instance().weatherIcons(), dannypx.foe.handler.logic.WeatherEventHandler.here(), System.currentTimeMillis()); dannypx.foe.handler.logic.FoeProfiler.end("tick WeatherEventHandler", _p); });
                dannypx.foe.helper.Safe.run("tick FindHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.logic.FindHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick FindHandler", _p); });
                dannypx.foe.helper.Safe.run("tick FriendInfoHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.logic.FriendInfoHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick FriendInfoHandler", _p); });
                dannypx.foe.helper.Safe.run("tick ActiveBoostersHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.logic.ActiveBoostersHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick ActiveBoostersHandler", _p); });
                dannypx.foe.helper.Safe.run("tick MiniEventHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.logic.MiniEventHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick MiniEventHandler", _p); });
                dannypx.foe.helper.Safe.run("tick BobberTimerHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.logic.BobberTimerHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick BobberTimerHandler", _p); });
                dannypx.foe.helper.Safe.run("tick SessionStatsHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.logic.SessionStatsHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick SessionStatsHandler", _p); });
                dannypx.foe.helper.Safe.run("tick FishSessionHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); FishSessionHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick FishSessionHandler", _p); });
                dannypx.foe.helper.Safe.run("tick FoerStatsImporter", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.io.FoerStatsImporter.tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick FoerStatsImporter", _p); });
                dannypx.foe.helper.Safe.run("tick FoeStatsImporter", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.io.FoeStatsImporter.tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick FoeStatsImporter", _p); });
                dannypx.foe.helper.Safe.run("tick PetLocationHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); dannypx.foe.handler.logic.PetLocationHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick PetLocationHandler", _p); });
                dannypx.foe.helper.Safe.run("tick CatchingHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); if(Configs.handlerConfig.catchingHandler.get()) CatchingHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick CatchingHandler", _p); });
                dannypx.foe.helper.Safe.run("tick HitResultHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); if(Configs.handlerConfig.rayCastHandler.get()) HitResultHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick HitResultHandler", _p); });
                dannypx.foe.helper.Safe.run("tick NotifierHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); if(Configs.handlerConfig.notifierHandler.get()) NotifierHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick NotifierHandler", _p); });
                dannypx.foe.helper.Safe.run("tick CrewHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); if(Configs.handlerConfig.crewHandler.get()) CrewHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick CrewHandler", _p); });
                dannypx.foe.helper.Safe.run("tick LightHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); if(Configs.handlerConfig.lightHandler.get()) LightHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick LightHandler", _p); });
                dannypx.foe.helper.Safe.run("tick TimerHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); if(Configs.handlerConfig.timerHandler.get()) TimerHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick TimerHandler", _p); });
                dannypx.foe.helper.Safe.run("tick QuestHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); if(Configs.handlerConfig.questHandler.get()) QuestHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick QuestHandler", _p); });

                // Renderer
                dannypx.foe.helper.Safe.run("tick HudRenderHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); if(Configs.handlerConfig.hudRenderHandler.get()) HudRenderHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick HudRenderHandler", _p); });

                // Placeholder Engine
                dannypx.foe.helper.Safe.run("tick PlaceholderHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); PlaceholderHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick PlaceholderHandler", _p); });

            } else {
                dannypx.foe.helper.Safe.run("tick LoadingHandler", () -> { long _p = dannypx.foe.handler.logic.FoeProfiler.mark(); if(Configs.handlerConfig.loadingHandler.get()) LoadingHandler.instance().tick(); dannypx.foe.handler.logic.FoeProfiler.end("tick LoadingHandler", _p); });
            }
        }
    }

    private void registerEntityModels() {
        EntityModelLayerRegistry.registerModelLayer(FishingHookEntityModel.MODEL_LAYER, FishingHookEntityModel::generateModel);
    }
}
