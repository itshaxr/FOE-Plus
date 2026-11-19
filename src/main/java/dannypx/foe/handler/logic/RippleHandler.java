package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.Handler;
import dannypx.foe.nearby_dispatcher.types.RippleType;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.projectile.FishingHook;

import java.util.Map;

public class RippleHandler extends Handler {
    private static RippleHandler INSTANCE = new RippleHandler();

    public static RippleHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new RippleHandler();
        }
        return INSTANCE;
    }

    //region Fields
    //endregion

    //region Methods
    public void spawnAtBobber() {
        if(minecraft.level != null && minecraft.player != null && minecraft.player.fishing != null) {
            this.spawnAtBobber(minecraft.player.fishing);
        }
    }

    public void spawnAtBobber(FishingHook fishingHook) {
        if (!(fishingHook.level() instanceof ClientLevel clientLevel)
                || !ConnectionHandler.instance().isOnServer()
                || !Configs.rendererConfig.showRippleOnBite.get()
        ) return;
        double surfaceY = this.findWaterSurfaceY(clientLevel, fishingHook.blockPosition(), fishingHook.getY());
        clientLevel.addParticle(ParticleHandler.RIPPLE, fishingHook.getX(), surfaceY + 0.02D, fishingHook.getZ(), 0.0D, 0.0D, 0.0D);
    }

    private double findWaterSurfaceY(ClientLevel clientLevel, BlockPos blockPos, double fallbackY) {
        if (!clientLevel.getFluidState(blockPos).is(FluidTags.WATER)) blockPos = blockPos.below();
        if (!clientLevel.getFluidState(blockPos).is(FluidTags.WATER)) return fallbackY;
        while (clientLevel.getFluidState(blockPos.above()).is(FluidTags.WATER)) blockPos = blockPos.above();

        return blockPos.getY() + clientLevel.getFluidState(blockPos).getHeight(clientLevel, blockPos);
    }

    public void checkIfBite(Component title) {
        if(title.getString().equals("BITE!")) {
            dannypx.foe.helper.Safe.run("bite sound", CatchSound::onBite);
            dannypx.foe.handler.store.NotifySounds.play("fish_bite");
            this.spawnAtBobber();
            RippleType.sendBite();
        }
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
