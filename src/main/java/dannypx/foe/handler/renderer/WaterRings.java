package dannypx.foe.handler.renderer;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.FishApproach;
import dannypx.foe.helper.FoeLocations;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Flat rings on the water at your own bobber, from tacklebox by landofif (MIT, used with permission). While a fish
 * swims in, rings at the bobber that come quicker as it nears and one bigger ring on the bite; a wake of small rings
 * behind your fish; one ring where the bobber was when you catch a fish, in its rarity colour. Each ring grows and
 * fades, nothing flashes. Settings: O → Renderer → Water Rings.
 * <p>
 * Drawn at {@code END_MAIN}: its buffers are drawn right after the translucent terrain, so a ring lies on the water
 * and blends with it.
 */
public final class WaterRings {
    private WaterRings() {}

    record Ring(double x, double y, double z, long born, long life, float from, float to, int rgb, float strength) {
        float progress(long now) { return Math.max(0, Math.min(1, (now - born) / (float) life)); }

        /// Grows quickly at first and slows down, like a ripple.
        float radius(long now) {
            float p = progress(now);
            return from + (to - from) * (1 - (1 - p) * (1 - p));
        }

        /// Fades in over the first moment (no pop) and out while it grows.
        float alpha(long now) {
            float p = progress(now);
            return strength * Math.min(1, p / FADE_IN) * (1 - p);
        }

        boolean over(long now) { return now - born >= life; }
    }

    record Spot(double x, double y, double z, long at) {}

    static final int MAX_RINGS = 48, SEGMENTS = 40;
    static final float WIDTH = 0.06f, LIFT = 0.03f, FADE_IN = 0.12f;

    static final long BITE_LIFE = 600, BITE_GAP_MS = 1500;
    static final float BITE_FROM = 0.2f, BITE_TO = 1.0f, BITE_BIG_TO = 1.4f;
    static final double MIN_INTERVAL = 0.25, MAX_INTERVAL = 1.0, INTERVAL_PER_SECOND = 0.35;

    static final long WAKE_LIFE = 700, WAKE_GAP_MS = 120;
    static final float WAKE_FROM = 0.15f, WAKE_TO = 0.6f, WAKE_STRENGTH = 0.55f;
    static final double WAKE_STEP = 0.35;

    static final long CATCH_LIFE = 500, SPOT_KEEP_MS = 2000;
    static final float CATCH_FROM = 0.3f, CATCH_TO = 1.5f, COMMON_STRENGTH = 0.5f;

    private static final List<Ring> rings = new ArrayList<>();
    private static Spot bobber;
    private static long nextBeat, lastBite, wakeAt;
    private static double wakeX = Double.NaN, wakeZ = Double.NaN;

    public static void register() {
        WorldRenderEvents.END_MAIN.register(ctx -> dannypx.foe.helper.Safe.run("water rings", () -> render(ctx)));
    }

    private static boolean on() {
        return Configs.mainConfig.enableMod.get() && Configs.rendererConfig.waterRingsOn.get()
                && dannypx.foe.handler.logic.ConnectionHandler.instance().isOnServer();
    }

    private static int colour() {
        return 0xD8F0FF;
    }

    private static float strength(float kind) {
        return Configs.rendererConfig.waterRingsOpacity.get() / 100f * kind;
    }

    private static float size() {
        return Configs.rendererConfig.waterRingsSize.get();
    }

    /// Seconds between the bite rings: 1 s far out, 0.25 s right before the bite.
    static double interval(double secondsLeft) {
        return Math.max(MIN_INTERVAL, Math.min(MAX_INTERVAL, secondsLeft * INTERVAL_PER_SECOND));
    }

    private static Spot bobber(long now) {
        return bobber != null && now - bobber.at() <= SPOT_KEEP_MS ? bobber : null;
    }

    /// Every tick: your bobber's spot, the bite rings while a fish swims in, rings that are over go.
    public static void tick(Minecraft mc) {
        if (!on()) {
            rings.clear();
            return;
        }
        long now = System.currentTimeMillis();
        FishingHook hook = mc.player == null ? null : mc.player.fishing;
        if (hook != null && mc.level != null) {
            bobber = new Spot(hook.getX(), waterTop(mc.level, hook.getX(), hook.getY(), hook.getZ()), hook.getZ(), now);
        }
        rings.removeIf(r -> r.over(now));
        if (hook == null || !Configs.rendererConfig.waterRingsBite.get()) return;
        float secs = FishApproach.secondsLeft();
        if (secs < 0 || FishApproach.progress() < 0) return;
        Spot at = bobber(now);
        if (at == null || now < nextBeat) return;
        add(new Ring(at.x(), at.y(), at.z(), now, BITE_LIFE, BITE_FROM * size(), BITE_TO * size(), colour(), strength(1)));
        nextBeat = now + Math.round(interval(secs) * 1000);
    }

    /// The BITE! title: one bigger ring at the bobber; the re-sends of the same bite add none.
    public static void onBite() {
        long now = System.currentTimeMillis();
        boolean fresh = now - lastBite > BITE_GAP_MS;
        lastBite = now;
        Spot at = bobber(now);
        if (fresh && on() && Configs.rendererConfig.waterRingsBite.get() && at != null) {
            add(new Ring(at.x(), at.y(), at.z(), now, BITE_LIFE, BITE_FROM * size(), BITE_BIG_TO * size(), colour(), strength(1)));
        }
    }

    /// A splash of your own fish's trail: a small ring once it moved a bit from the last one.
    public static void onWake(double x, double y, double z) {
        long now = System.currentTimeMillis();
        if (!on() || !Configs.rendererConfig.waterRingsWake.get() || now - wakeAt < WAKE_GAP_MS) return;
        double dx = x - wakeX, dz = z - wakeZ;
        if (!Double.isNaN(dx) && dx * dx + dz * dz < WAKE_STEP * WAKE_STEP) return;
        Minecraft mc = Minecraft.getInstance();
        double top = mc.level == null ? y : waterTop(mc.level, x, y, z);
        add(new Ring(x, top, z, now, WAKE_LIFE, WAKE_FROM * size(), WAKE_TO * size(), colour(), strength(WAKE_STRENGTH)));
        wakeX = x;
        wakeZ = z;
        wakeAt = now;
    }

    /// You caught a fish of this rarity: one ring where the bobber was, and sparkles for the rarest.
    public static void onCatch(String rarity) {
        long now = System.currentTimeMillis();
        Spot at = bobber(now);
        if (!on() || at == null) return;
        String r = rarity == null ? "" : rarity.toLowerCase(Locale.ROOT);
        int rgb = FoeLocations.rarityColor(r);
        boolean faint = rgb < 0 || r.equals("common");
        if (Configs.rendererConfig.waterRingsCatch.get()) {
            add(new Ring(at.x(), at.y(), at.z(), now, CATCH_LIFE, CATCH_FROM * size(), CATCH_TO * size(),
                    faint ? 0xFFFFFF : rgb, strength(faint ? COMMON_STRENGTH : 1)));
        }
        int count = switch (r) {
            case "legendary" -> 6;
            case "mythical", "mythic", "special" -> 10;
            default -> 0;
        };
        Minecraft mc = Minecraft.getInstance();
        if (count > 0 && Configs.rendererConfig.waterRingsSparkles.get() && mc.level != null) {
            DustParticleOptions dust = new DustParticleOptions(faint ? 0xFFFFFF : rgb, 1.0f);
            java.util.Random random = new java.util.Random();
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2 * i / count + random.nextDouble() * 0.5, d = 0.3 + random.nextDouble() * 0.6;
                mc.level.addParticle(dust, at.x() + Math.cos(angle) * d, at.y() + 0.1 + random.nextDouble() * 0.2,
                        at.z() + Math.sin(angle) * d, 0, 1, 0);
            }
        }
    }

    private static void add(Ring ring) {
        if (rings.size() >= MAX_RINGS) rings.remove(0);
        rings.add(ring);
    }

    public static void reset() {
        rings.clear();
        bobber = null;
        nextBeat = lastBite = wakeAt = 0;
        wakeX = wakeZ = Double.NaN;
    }

    /// The water's top at a spot: the fluid height of the block the spot is in or the one under it, else y.
    static double waterTop(Level level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        for (int i = 0; i < 2; i++) {
            pos.setY(Mth.floor(y) - i);
            FluidState fluid = level.getFluidState(pos);
            if (!fluid.isEmpty()) return pos.getY() + fluid.getHeight(level, pos);
        }
        return y;
    }

    /// Vertices are relative to the camera, like every other quad the world renderer draws.
    private static void render(WorldRenderContext context) {
        if (rings.isEmpty() || !on()) return;
        long now = System.currentTimeMillis();
        Vec3 camera = context.worldState().cameraRenderState.pos;
        VertexConsumer buffer = context.consumers().getBuffer(RenderTypes.debugQuads());
        float width = WIDTH * Configs.rendererConfig.waterRingsWidth.get();
        for (Ring ring : List.copyOf(rings)) {
            int alpha = Math.round(ring.alpha(now) * 255);
            if (alpha <= 0 || ring.over(now)) continue;
            int color = alpha << 24 | (ring.rgb() & 0xFFFFFF);
            emit((float) (ring.x() - camera.x), (float) (ring.y() + LIFT - camera.y), (float) (ring.z() - camera.z),
                    ring.radius(now), width, buffer, color);
        }
    }

    /// A flat ring around (cx, y, cz): SEGMENTS quads, `width` wide, centred on `radius`.
    private static void emit(float cx, float y, float cz, float radius, float width, VertexConsumer buffer, int color) {
        float in = Math.max(0, radius - width / 2), out = radius + width / 2;
        for (int i = 0; i < SEGMENTS; i++) {
            double a = Math.PI * 2 * i / SEGMENTS, b = Math.PI * 2 * (i + 1) / SEGMENTS;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a), cb = (float) Math.cos(b), sb = (float) Math.sin(b);
            buffer.addVertex(cx + ca * in, y, cz + sa * in).setColor(color);
            buffer.addVertex(cx + ca * out, y, cz + sa * out).setColor(color);
            buffer.addVertex(cx + cb * out, y, cz + sb * out).setColor(color);
            buffer.addVertex(cx + cb * in, y, cz + sb * in).setColor(color);
        }
    }
}
