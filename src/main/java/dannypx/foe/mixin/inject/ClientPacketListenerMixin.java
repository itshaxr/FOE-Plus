package dannypx.foe.mixin.inject;

import com.llamalad7.mixinextras.sugar.Local;
import dannypx.foe.handler.logic.CatchingHandler;
import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.handler.logic.CrewHandler;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.TextDisplayHandler;
import dannypx.foe.nearby_dispatcher.NearbyDispatcher;
import dannypx.foe.nearby_dispatcher.types.HatType;
import dannypx.foe.nearby_dispatcher.types.ModUserType;
import dannypx.foe.nearby_dispatcher.types.RodType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Unique
    private List<UUID> uuids = new ArrayList<>();

    @Inject(method = "handleSetTime", at = @At("TAIL"))
    private void foe$serverTps(net.minecraft.network.protocol.game.ClientboundSetTimePacket packet, CallbackInfo ci) {
        dannypx.foe.helper.Safe.run("server tps", () -> dannypx.foe.handler.logic.ServerTpsHandler.instance().onTime(packet.gameTime()));
    }

    @Inject(method = "handlePlayerInfoUpdate", at = @At("TAIL"))
    private void injectHandlePlayerInfoUpdate(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mixinConfig.clientPacketListenerMixinHandlePlayerInfoUpdate.get()
            ) {
                dannypx.foe.helper.Safe.run("player joined", () -> {
                    for (ClientboundPlayerInfoUpdatePacket.Entry entry : packet.entries()) {
                        if (packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) {
                            CrewHandler.instance().onPlayerJoin(entry.profileId());
                            dannypx.foe.handler.logic.FriendsHandler.instance().onPlayerJoin(entry.profileId());
                        }
                    }
                });
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("ClientPacketListenerMixin.injectHandlePlayerInfoUpdate", foe$t);
        }
    }

    @Inject(method = "handlePlayerInfoRemove", at = @At("TAIL"))
    private void injectHandlePlayerInfoRemove(ClientboundPlayerInfoRemovePacket packet, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mixinConfig.clientPacketListenerMixinHandlePlayerInfoRemove.get()
            ) {
                dannypx.foe.helper.Safe.run("player left", () -> {
                    for (UUID uuid : packet.profileIds()) {
                        CrewHandler.instance().onPlayerLeave(uuid);
                        dannypx.foe.handler.logic.FriendsHandler.instance().onPlayerLeave(uuid);
                        HatType.onPlayerLeave(uuid);
                        RodType.onPlayerLeave(uuid);
                        ModUserType.onPlayerLeave(uuid);
                    }
                });
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("ClientPacketListenerMixin.injectHandlePlayerInfoRemove", foe$t);
        }
    }

    @Inject(method = "handleSetEntityData", at = @At("TAIL"))
    private void injectPostAddEntitySoundInstance(ClientboundSetEntityDataPacket clientboundSetEntityDataPacket, CallbackInfo ci, @Local Entity entity) {
        try {
        if(entity instanceof Display.TextDisplay textDisplay
                && textDisplay.getText().getString().startsWith("CATCH SUMMARY")
                && !uuids.contains(textDisplay.getUUID())
        ) {
            String[] lines = textDisplay.getText().getString().split("\n");

            if(lines.length > 6) {
                Arrays.stream(lines)
                        .filter(line -> {
                            if (line.isEmpty()) return false;
                            char c = line.charAt(line.length() - 1);
                            return c >= 0xE000 && c <= 0xE999;
                        })
                        .findFirst().ifPresent(found -> CatchingHandler.instance().scanFishListener(found));

                uuids.add(textDisplay.getUUID());
                // only the latest catch summaries matter (it used to keep every one for the whole session)
                if (uuids.size() > 64) uuids.subList(0, uuids.size() - 64).clear();
            }
        }
        } catch (Throwable t) {
            dannypx.foe.helper.Safe.report("catch summary", t);
        }
    }

    /// The Luck & Scale auto refresh reads /menu without showing it (see ProfileStatsHandler).
    @Inject(method = "handleOpenScreen", at = @At("HEAD"), cancellable = true)
    private void foe$silentMenuOpen(net.minecraft.network.protocol.game.ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        if (dannypx.foe.helper.Safe.get("menu open (friend info)", () -> dannypx.foe.handler.logic.FriendInfoHandler.instance().onOpenScreen(packet.getContainerId(), packet.getTitle().getString()), false)) { ci.cancel(); return; }
        if (dannypx.foe.helper.Safe.get("menu open (silent)", () -> dannypx.foe.handler.logic.SilentMenuHandler.instance().onOpenScreen(packet.getContainerId(), packet.getTitle().getString()), false)) ci.cancel();
    }

    @Inject(method = "handleContainerContent", at = @At("HEAD"))
    private void foe$silentMenuContent(net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        dannypx.foe.helper.Safe.run("menu content (friend info)", () -> dannypx.foe.handler.logic.FriendInfoHandler.instance().onContainerContent(packet.containerId(), packet.items()));
        dannypx.foe.helper.Safe.run("menu content (silent)", () -> dannypx.foe.handler.logic.SilentMenuHandler.instance().onContainerContent(packet.containerId(), packet.items()));
    }

    @Inject(method = "handleBlockDestruction", at = @At("HEAD"))
    private void injectHandleBlockDestruction(ClientboundBlockDestructionPacket clientboundBlockDestructionPacket, CallbackInfo ci) {
        try {
            if (!Minecraft.getInstance().isSameThread()) return;
            dannypx.foe.helper.Safe.run("block destruction", () -> NearbyDispatcher.onBlockDestruction(clientboundBlockDestructionPacket.getId(), clientboundBlockDestructionPacket.getPos(), clientboundBlockDestructionPacket.getProgress()));
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("ClientPacketListenerMixin.injectHandleBlockDestruction", foe$t);
        }
    }
}
