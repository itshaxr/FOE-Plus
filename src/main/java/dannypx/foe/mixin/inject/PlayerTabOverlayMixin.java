package dannypx.foe.mixin.inject;

import com.llamalad7.mixinextras.sugar.Local;
import dannypx.foe.handler.fetch.ScoreboardHandler;
import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.handler.logic.CrewHandler;
import dannypx.foe.handler.renderer.TabRendererHandler;
import dannypx.foe.handler.store.ProfileDataHandler;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.config.Configs;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Shadow @Final private Minecraft minecraft;

    @Shadow protected abstract List<PlayerInfo> getPlayerInfos();

    @Shadow @Final private static Comparator<PlayerInfo> PLAYER_COMPARATOR;

    @Unique
    private int indexPlayerEntry;

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void redirectRender(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        if(ConnectionHandler.instance().isOnServer()
                && Configs.mainConfig.enableMod.get()
                && Configs.mixinConfig.playerTabOverlayMixinRedirectRender.get()
                && color == this.minecraft.options.getBackgroundColor(0x20ffffff)) {
            TabRendererHandler.instance().renderCrewTab(guiGraphics, x1, y1, x2, y2, color, indexPlayerEntry, this.getPlayerInfos());
        }

        guiGraphics.fill(x1, y1, x2, y2, color);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"))
    private void injectRender(GuiGraphics guiGraphics, int scaledWindowWidth, Scoreboard scoreboard, Objective objective, CallbackInfo ci, @Local(ordinal = 13) int w) {
        if(ConnectionHandler.instance().isOnServer()
                && Configs.mainConfig.enableMod.get()
                && Configs.mixinConfig.playerTabOverlayMixinInjectRender.get()
        ) {
            indexPlayerEntry = w;
        }
    }

    // FOE's clean tab list: rank tags drawn as compact lettered squares (ported from FOECollab).
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void injectCleanTabTags(net.minecraft.client.multiplayer.PlayerInfo playerInfo, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.network.chat.Component> cir) {
        if (ConnectionHandler.instance().isOnServer()
                && Configs.mainConfig.enableMod.get()
                && Configs.rendererConfig.cleanTabListTags.get()
        ) {
            cir.setReturnValue(dannypx.foe.helper.SimpleTagFont.apply(cir.getReturnValue(), true, true));
        }
    }

    @Inject(method = "getPlayerInfos", at = @At("RETURN"), cancellable = true)
    private void injectGetPlayerInfos(@NotNull CallbackInfoReturnable<List<PlayerInfo>> cir) {
        if(ConnectionHandler.instance().isOnServer()
                && Configs.mainConfig.enableMod.get()
                && Configs.rendererConfig.showOnlineCrewMembers.get()
                && Configs.mixinConfig.playerTabOverlayMixinCollectPlayerEntries.get()
                && ProfileDataHandler.instance().getProfileData().hasImportedCrew
                && !ScoreboardHandler.instance().getCrew().getString().isBlank()
        ) {
            Set<UUID> priorityUUIDs = CrewHandler.instance().getOnlineMembers().stream()
                    .map(Pair::value1)
                    .collect(Collectors.toSet());

            Stream<PlayerInfo> sorted = this.minecraft.player.connection.getListedOnlinePlayers().stream().sorted(PLAYER_COMPARATOR);

            sorted = sorted.sorted(Comparator.comparing(
                    e -> !priorityUUIDs.contains(e.getProfile().id())
            ));

            cir.setReturnValue(sorted.limit(80L).toList());
        } else {
            cir.setReturnValue(cir.getReturnValue());
        }
    }
}
