package dannypx.foe.handler.logic;

import dannypx.foe.handler.Handler;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.type.tuple.Triplet;
import dannypx.foe.config.Configs;
import dannypx.foe.mixin.accessor.LevelRendererAccessor;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.BlockAndTintGetter;

public class LightHandler extends Handler {
    private static LightHandler INSTANCE = new LightHandler();

    public static LightHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new LightHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private BlockPos prevPos = null;
    //endregion

    //region Methods
    @Override
    public void tick() {
        this.updateFishingHookLight();
    }

    private int getRadius() {
        return Configs.rendererConfig.lightRadiusFishingHook.get();
    }

    private int getMaxLight() {
        return Configs.rendererConfig.maxLightLevelFishingHook.get();
    }

    public void updateFishingHookLight() {
        if(minecraft.level != null
                && minecraft.player != null
                && Configs.rendererConfig.showLightFishingHook.get()
        ) {
            FishingHook fishingHook = minecraft.player.fishing;

            if (fishingHook != null) {
                BlockPos currentPos = fishingHook.blockPosition();
                // Only when the bobber reaches another block. This used to mark the 27 sections
                // around it for a rebuild every tick, even with the bobber sitting still, which
                // kept the game rebuilding chunk meshes nonstop while fishing (very heavy with
                // Sodium and shaders).
                if (currentPos.equals(prevPos)) return;

                if (prevPos != null) {
                    this.scheduleSectionUpdate(prevPos);
                }

                this.scheduleSectionUpdate(currentPos);

                prevPos = currentPos;
            } else {
                if (prevPos != null) {
                    this.scheduleSectionUpdate(prevPos);
                }
                prevPos = null;
            }
        }
    }

    private void scheduleSectionUpdate(BlockPos pos) {
        this.setSectionDirtyWithNeighbors(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getY()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private void setSectionDirtyWithNeighbors(int x, int y, int z) {
        this.setSectionDirty(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
    }

    private void setSectionDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkY = minY; chunkY <= maxY; chunkY++) {
                for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                    ((LevelRendererAccessor) minecraft.levelRenderer).foer$setSectionDirty(chunkX, chunkY, chunkZ, false);
                }
            }
        }
    }

    public int calculateFishingHookLight(long blockPos, int lightLevel) {
        if(minecraft.player != null
                && Configs.rendererConfig.showLightFishingHook.get()
        ) {
            FishingHook fishingHook = minecraft.player.fishing;
            if(fishingHook != null) {
                BlockPos pos = BlockPos.of(blockPos);

                Triplet<Boolean, Double, Double> fishingHookInRange = calculateIfWithinDistance(pos, fishingHook);

                if (fishingHookInRange.value1()) return lightLevel;

                double factor = 1.0 - (fishingHookInRange.value2() / fishingHookInRange.value3());
                int dynamicLightLevel = (int)(getMaxLight() * factor);

                if(dynamicLightLevel > lightLevel) {
                    return dynamicLightLevel;
                }
            }
        }
        return lightLevel;
    }

    public int calculateFishingHookLight(BlockPos pos, int lightMap) {
        if(minecraft.player != null
                && Configs.rendererConfig.showLightFishingHook.get()
        ) {
            FishingHook fishingHook = minecraft.player.fishing;
            if(fishingHook != null) {
                Triplet<Boolean, Double, Double> fishingHookInRange = calculateIfWithinDistance(pos, fishingHook);

                if (fishingHookInRange.value1()) return lightMap;

                double factor = 1.0 - (fishingHookInRange.value2() / fishingHookInRange.value3());
                int dynamicLightlevel = (int)(getMaxLight() * factor);

                if(dynamicLightlevel > 0) {
                    int blockLevel = LightTexture.block(lightMap);
                    if (dynamicLightlevel > blockLevel) {
                        int luminance = (int) (dynamicLightlevel * 16.0);
                        lightMap &= 0xfff00000;
                        lightMap |= luminance & 0x000fffff;
                    }
                }
            }
        }
        return lightMap;
    }

    private Triplet<Boolean, Double, Double> calculateIfWithinDistance(BlockPos pos, FishingHook fishingHook) {
        double dx = pos.getX() + 0.5 - fishingHook.getX();
        double dy = pos.getY() + 0.5 - fishingHook.getY();
        double dz = pos.getZ() + 0.5 - fishingHook.getZ();

        double distSq = dx * dx + dy * dy + dz * dz;
        double maxDistSq = getRadius() * getRadius();

        if (distSq > maxDistSq) return Triplet.of(true, distSq, maxDistSq);
        else return Triplet.of(false, distSq, maxDistSq);
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
