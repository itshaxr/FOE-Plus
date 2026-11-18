package dannypx.foe.handler.logic;

import dannypx.foe.FishOnMCExtras;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.zip.GZIPOutputStream;

/**
 * TEST BUILDS ONLY. Scans the places you walk through, for building FOE+'s travel map from the
 * places themselves: for every column of blocks your game has loaded, what is on top and what shows
 * down its sides (so buildings come with their roofs, walls, windows and heights), with each
 * block's real colour from its texture. It also notes where each teleport spot lands you.
 * Read only: it looks at what the game has already loaded (like a minimap) and writes one file on
 * your computer; nothing is sent anywhere and nothing is clicked.
 *
 * {@code /foe map scan} starts and stops it, {@code /foe map export} writes the file,
 * {@code /foe map clear} forgets what was scanned.
 */
public final class MapScanHandler {
    private static MapScanHandler INSTANCE;

    public static MapScanHandler instance() {
        if (INSTANCE == null) INSTANCE = new MapScanHandler();
        return INSTANCE;
    }

    /// Only in test builds (and the development client).
    public static boolean available() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) return true;
        return FabricLoader.getInstance().getModContainer(FishOnMCExtras.MOD_ID)
                .map(m -> m.getMetadata().getVersion().getFriendlyString().contains("test")).orElse(false);
    }

    //region The file (package-private for tests)
    static final String MAGIC = "FOESCAN1";
    /// How far down a column is followed at most (a tall tower's wall, a cliff, deep water).
    static final int MAX_DEPTH = 96;
    static final int FLAG_OPAQUE = 1, FLAG_FLUID = 2, FLAG_TINTED = 4;

    /// One kind of block: its name and state, the game's map colour, its top and side texture
    /// colours (ARGB, 0 = unknown), and flags.
    record Kind(String name, int mapColor, int topColor, int sideColor, int flags) {}

    /// One 16 x 16 chunk: per column (index z * 16 + x) the height of its top block, the top block's
    /// tint (0 = none; grass, leaves and water take their colour from the place), and what it is made of
    /// from the top down, as pairs {kind, how many}.
    static final class Chunk {
        final int cx, cz;
        final short[] top = new short[256];
        final int[] tint = new int[256];
        final int[][] runs = new int[256][];

        Chunk(int cx, int cz) { this.cx = cx; this.cz = cz; }
    }

    /// Everything scanned in one place (the places are separate worlds, each with its own coordinates).
    static final class Area {
        final String location, dimension;
        final Map<Long, Chunk> chunks = new LinkedHashMap<>();

        Area(String location, String dimension) { this.location = location; this.dimension = dimension; }
    }

    /// Where a teleport spot put you.
    record Poi(String location, String name, double x, double y, double z) {}

    /// A column from its top block down to `floorY`, as pairs {kind, how many}: at most 255 pairs, each
    /// at most 255 long. `kindAt` gives the kind at a height (0 = air).
    static int[] runsOf(IntUnaryOperator kindAt, int topY, int floorY) {
        List<int[]> runs = new ArrayList<>();
        int current = -1, length = 0;
        for (int y = topY; y >= floorY; y--) {
            int kind = kindAt.applyAsInt(y);
            if (kind == current && length < 255) { length++; continue; }
            if (length > 0) runs.add(new int[]{current, length});
            if (runs.size() == 255) { length = 0; break; }
            current = kind;
            length = 1;
        }
        if (length > 0) runs.add(new int[]{current, length});
        int[] out = new int[runs.size() * 2];
        for (int i = 0; i < runs.size(); i++) { out[i * 2] = runs.get(i)[0]; out[i * 2 + 1] = runs.get(i)[1]; }
        return out;
    }

    /// Package-private for tests: the height of the ground in a column topped at {@code topY}: the first solid,
    /// natural ground block with more natural ground under it, and something solid under that. Failing that, the first solid block (a building's
    /// floor, a roof over the void), as before. Never more than {@code maxDepth} down or below {@code minY}.
    static int groundBelow(java.util.function.IntPredicate solidAt, java.util.function.IntPredicate groundAt, int topY, int minY, int maxDepth) {
        int firstSolid = Integer.MIN_VALUE;
        for (int y = topY; y >= minY && topY - y < maxDepth; y--) {
            if (!solidAt.test(y)) continue;
            if (firstSolid == Integer.MIN_VALUE) firstSolid = y;
            // ground on ground: a block of moss or a pack's block up in a tree's crown sits on leaves, not on more ground
            if (groundAt.test(y) && y - 2 >= minY && groundAt.test(y - 1) && solidAt.test(y - 2)) return y;
        }
        if (firstSolid != Integer.MIN_VALUE) return firstSolid;
        return Math.max(minY, topY - maxDepth + 1);
    }

    /// Ground as nature made it: grass, dirt, mud and moss, sand, gravel, stone, snow.
    private static boolean isNaturalGround(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(net.minecraft.tags.BlockTags.SAND)
                || state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || state.is(net.minecraft.world.level.block.Blocks.GRAVEL)
                || state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK) || state.is(net.minecraft.world.level.block.Blocks.PACKED_MUD)
                || state.is(net.minecraft.world.level.block.Blocks.CLAY);
    }

    /// How far down a column is worth following: to the first solid block (the ground under water, plants
    /// and glass), and on down to the lowest column next to it (a wall or cliff shows that far), but never
    /// more than {@link #MAX_DEPTH} or below the world.
    static int floorOf(int topY, int firstSolidY, int lowestNeighbourTop, int minY) {
        int floor = Math.min(firstSolidY, lowestNeighbourTop + 1);
        return Math.max(Math.max(floor, topY - MAX_DEPTH + 1), minY);
    }

    static void write(OutputStream to, int minY, List<Kind> kinds, Collection<Area> areas, List<Poi> pois) throws IOException {
        GZIPOutputStream zip = new GZIPOutputStream(to);
        DataOutputStream out = new DataOutputStream(zip);
        out.writeBytes(MAGIC);
        out.writeInt(minY);
        out.writeInt(kinds.size());
        for (Kind k : kinds) {
            out.writeUTF(k.name());
            out.writeInt(k.mapColor());
            out.writeInt(k.topColor());
            out.writeInt(k.sideColor());
            out.writeByte(k.flags());
        }
        out.writeInt(areas.size());
        for (Area a : areas) {
            out.writeUTF(a.location);
            out.writeUTF(a.dimension);
            out.writeInt(a.chunks.size());
            for (Chunk c : a.chunks.values()) {
                out.writeInt(c.cx);
                out.writeInt(c.cz);
                for (int i = 0; i < 256; i++) {
                    out.writeShort(c.top[i]);
                    out.writeInt(c.tint[i]);
                    int[] runs = c.runs[i] == null ? new int[0] : c.runs[i];
                    out.writeByte(runs.length / 2);
                    for (int r = 0; r < runs.length; r += 2) {
                        out.writeShort(runs[r]);
                        out.writeByte(runs[r + 1]);
                    }
                }
            }
        }
        out.writeInt(pois.size());
        for (Poi p : pois) {
            out.writeUTF(p.location());
            out.writeUTF(p.name());
            out.writeDouble(p.x());
            out.writeDouble(p.y());
            out.writeDouble(p.z());
        }
        out.flush();
        zip.finish();
    }
    //endregion

    private boolean scanning = false;
    private final Map<String, Area> areas = new LinkedHashMap<>();
    private final List<Poi> pois = new ArrayList<>();
    private final List<Kind> kinds = new ArrayList<>();
    private final Map<BlockState, Integer> kindOf = new HashMap<>();
    private final Map<String, Integer> spriteColors = new HashMap<>();
    private int minY = -64;

    private ClientLevel lastLevel;
    private Vec3 lastPos;
    private long pauseUntil;
    private String pendingSpot;
    private long pendingSpotAt, recordSpotAt;
    private int scannedThisSecond, lastRate;
    private long rateAt;

    public boolean scanning() { return scanning; }

    public int chunkCount() {
        int n = 0;
        for (Area a : areas.values()) n += a.chunks.size();
        return n;
    }

    public int poiCount() { return pois.size(); }

    public boolean toggle() {
        scanning = !scanning;
        if (scanning) { lastLevel = null; lastPos = null; }
        return scanning;
    }

    public void clear() {
        areas.clear();
        pois.clear();
        kinds.clear();
        kindOf.clear();
        spriteColors.clear();
    }

    /// A teleport spot was picked on the travel map: where you land next is that spot.
    public void noteSpot(String name) {
        if (!scanning || name == null || name.isBlank() || name.equalsIgnoreCase("air")) return;
        pendingSpot = name.trim();
        pendingSpotAt = System.currentTimeMillis();
    }

    private static String locationName() {
        try {
            dannypx.foe.helper.FoeLocations.Location l = PetLocationHandler.currentLocation();
            String n = l == null ? null : dannypx.foe.helper.FoeLocations.displayName(l);
            return n == null ? "unknown" : n;
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private Area area(ClientLevel level) {
        String location = locationName(), dimension = level.dimension().identifier().toString();
        return areas.computeIfAbsent(location + "|" + dimension, k -> new Area(location, dimension));
    }

    /// Each tick while scanning: the nearest loaded chunks not scanned yet (two a tick), and where a
    /// picked spot landed you.
    public void tick(Minecraft mc) {
        if (!scanning || mc.player == null || mc.level == null) { lastLevel = null; return; }
        long now = System.currentTimeMillis();
        ClientLevel level = mc.level;
        Vec3 pos = mc.player.position();
        boolean jumped = level != lastLevel || (lastPos != null && lastPos.distanceToSqr(pos) > 32.0 * 32.0);
        lastLevel = level;
        lastPos = pos;
        if (jumped) {
            // A teleport: wait for the new place to load (and for FOE+ to know where you are).
            pauseUntil = now + 5000L;
            if (pendingSpot != null && now - pendingSpotAt < 20_000L) recordSpotAt = now + 2500L;
        }
        if (recordSpotAt != 0L && now >= recordSpotAt) {
            // Every spot once: teleporting to it again moves it.
            pois.removeIf(p -> p.name().equals(pendingSpot) && p.location().equals(locationName()));
            pois.add(new Poi(locationName(), pendingSpot, pos.x, pos.y, pos.z));
            dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.line("MAP SCAN", dannypx.foe.helper.FoeChat.GREEN)
                    .append(dannypx.foe.helper.FoeChat.text("Spot saved: " + pendingSpot + " (" + locationName() + ")")));
            pendingSpot = null;
            recordSpotAt = 0L;
        }
        if (now < pauseUntil) return;
        if (now - rateAt >= 1000L) { lastRate = scannedThisSecond; scannedThisSecond = 0; rateAt = now; }

        minY = level.getMinY();
        Area area = area(level);
        int pcx = mc.player.chunkPosition().x, pcz = mc.player.chunkPosition().z;
        int radius = mc.options.getEffectiveRenderDistance() + 1;
        for (int budget = 0; budget < 2; budget++) {
            int bestX = 0, bestZ = 0, best = Integer.MAX_VALUE;
            for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
                int d = dx * dx + dz * dz;
                if (d >= best) continue;
                int cx = pcx + dx, cz = pcz + dz;
                if (area.chunks.containsKey(ChunkPos.asLong(cx, cz))) continue;
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null || chunk.isEmpty()) continue;
                best = d; bestX = cx; bestZ = cz;
            }
            if (best == Integer.MAX_VALUE) return;
            LevelChunk chunk = level.getChunkSource().getChunk(bestX, bestZ, ChunkStatus.FULL, false);
            if (chunk == null) return;
            area.chunks.put(ChunkPos.asLong(bestX, bestZ), scan(mc, level, chunk));
            scannedThisSecond++;
        }
    }

    private Chunk scan(Minecraft mc, ClientLevel level, LevelChunk chunk) {
        int cx = chunk.getPos().x, cz = chunk.getPos().z;
        Chunk out = new Chunk(cx, cz);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int min = level.getMinY();
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            int x = (cx << 4) + lx, z = (cz << 4) + lz, i = lz * 16 + lx;
            int top = topOf(level, chunk, x, z, at);
            out.top[i] = (short) top;
            if (top < min) { out.runs[i] = new int[0]; continue; }       // nothing in this column
            BlockState topState = chunk.getBlockState(at.set(x, top, z));
            int tint;
            try { tint = mc.getBlockColors().getColor(topState, level, at, 0); } catch (RuntimeException e) { tint = -1; }
            out.tint[i] = tint == -1 ? 0 : (tint | 0xFF000000);

            // Down to the real ground: past water and plants as before, and now on through trees too (a crown
            // can be made of anything a pack draws as leaves, with moss or stone in it), to natural ground
            // that has more ground under it.
            final int gx = x, gz = z;
            int firstSolid = groundBelow(y -> chunk.getBlockState(at.set(gx, y, gz)).isSolidRender(),
                    y -> isNaturalGround(chunk.getBlockState(at.set(gx, y, gz))), top, min, MAX_DEPTH);
            int lowest = top;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int nx = x + d.getStepX(), nz = z + d.getStepZ();
                LevelChunk n = (nx >> 4) == cx && (nz >> 4) == cz ? chunk : level.getChunkSource().getChunk(nx >> 4, nz >> 4, ChunkStatus.FULL, false);
                if (n == null || n.isEmpty()) continue;                  // not loaded: nothing known about that side
                lowest = Math.min(lowest, topOf(level, n, nx, nz, at));
            }
            int floor = floorOf(top, firstSolid, Math.max(lowest, min - 1), min);
            final int fx = x, fz = z;
            out.runs[i] = runsOf(y -> kind(mc, level, chunk.getBlockState(at.set(fx, y, fz)), at), top, floor);
        }
        return out;
    }

    /// The height of the highest block in a column that isn't air (below the world when there is none).
    private static int topOf(ClientLevel level, LevelChunk chunk, int x, int z, BlockPos.MutableBlockPos at) {
        int min = level.getMinY(), max = level.getMaxY();
        int y = Math.max(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15), chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15));
        // The height maps can be missing or off: with none, start from the top of the highest part of the
        // chunk that has blocks; then settle on the real top block, up or down.
        if (y <= min) {
            y = min;
            var sections = chunk.getSections();
            for (int i = sections.length - 1; i >= 0; i--) {
                if (!sections[i].hasOnlyAir()) { y = chunk.getSectionYFromSectionIndex(i) * 16 + 15; break; }
            }
        }
        y = Math.min(y + 1, max);
        while (y < max && !chunk.getBlockState(at.set(x, y + 1, z)).isAir()) y++;
        while (y >= min && chunk.getBlockState(at.set(x, y, z)).isAir()) y--;
        return y;
    }

    private int kind(Minecraft mc, ClientLevel level, BlockState state, BlockPos pos) {
        if (state.isAir()) return 0;
        Integer known = kindOf.get(state);
        if (known != null) return known;
        if (kinds.isEmpty()) kinds.add(new Kind("air", 0, 0, 0, 0));
        int flags = (state.isSolidRender() ? FLAG_OPAQUE : 0) | (state.getFluidState().isEmpty() ? 0 : FLAG_FLUID);
        int mapColor = 0, top = 0, side = 0;
        try { mapColor = state.getMapColor(level, pos).col; } catch (RuntimeException e) { /* unknown */ }
        try {
            BlockStateModel model = mc.getBlockRenderer().getBlockModel(state);
            List<BlockModelPart> parts = model.collectParts(RandomSource.create(42L));
            int[] t = faceColor(parts, Direction.UP, model), s = faceColor(parts, Direction.NORTH, model);
            top = t[0]; side = s[0];
            if (t[1] != 0 || s[1] != 0) flags |= FLAG_TINTED;
        } catch (RuntimeException e) {
            // no model colours: the map colour stands in
        }
        kinds.add(new Kind(state.toString(), mapColor, top, side, flags));
        kindOf.put(state, kinds.size() - 1);
        return kinds.size() - 1;
    }

    /// {the average colour of the texture on that face (ARGB, 0 = none), 1 when the face is tinted}.
    private int[] faceColor(List<BlockModelPart> parts, Direction face, BlockStateModel model) {
        for (BlockModelPart part : parts) {
            for (BakedQuad q : part.getQuads(face)) return new int[]{spriteColor(q.sprite()), q.isTinted() ? 1 : 0};
        }
        for (BlockModelPart part : parts) {
            for (BakedQuad q : part.getQuads(null)) if (q.direction() == face) return new int[]{spriteColor(q.sprite()), q.isTinted() ? 1 : 0};
        }
        return new int[]{spriteColor(model.particleIcon()), 0};
    }

    private int spriteColor(TextureAtlasSprite sprite) {
        if (sprite == null) return 0;
        SpriteContents contents = sprite.contents();
        String key = contents.name().toString();
        Integer known = spriteColors.get(key);
        if (known != null) return known;
        int color = 0;
        try {
            com.mojang.blaze3d.platform.NativeImage image = ((dannypx.foe.mixin.accessor.SpriteContentsAccessor) contents).getOriginalImage();
            int w = Math.min(contents.width(), image.getWidth()), h = Math.min(contents.height(), image.getHeight());
            long a = 0, r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int p = image.getPixel(x, y);
                int pa = p >>> 24;
                if (pa < 24) continue;
                a += pa; r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF; n++;
            }
            if (n > 0) color = ((int) (a / n) << 24) | ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
        } catch (RuntimeException | Error e) {
            color = 0;
        }
        spriteColors.put(key, color);
        return color;
    }

    /// Writes everything scanned to one file in the game folder; returns it.
    public Path export(Minecraft mc) throws IOException {
        Path dir = mc.gameDirectory.toPath().resolve("foe-plus-scans");
        Files.createDirectories(dir);
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = dir.resolve("foe-plus-mapscan-" + stamp + ".foescan");
        List<Kind> all = kinds.isEmpty() ? List.of(new Kind("air", 0, 0, 0, 0)) : kinds;
        try (OutputStream to = Files.newOutputStream(file)) {
            write(to, minY, all, areas.values(), pois);
        }
        return file;
    }

    public List<String> summary() {
        List<String> out = new ArrayList<>();
        for (Area a : areas.values()) {
            int spots = 0;
            for (Poi p : pois) if (p.location().equals(a.location)) spots++;
            out.add(a.location + ": " + a.chunks.size() + " chunks, " + spots + " spots");
        }
        return out;
    }

    /// While scanning: a small picture of what has been scanned round you (2 pixels a chunk), and the count.
    public void renderHud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (!scanning || mc.player == null || mc.level == null || mc.screen != null) return;
        Area area = areas.get(locationName() + "|" + mc.level.dimension().identifier());
        int span = 16, size = (span * 2 + 1) * 2;
        int x0 = 4, y0 = g.guiHeight() / 2 - size / 2;
        g.fill(x0 - 2, y0 - 12, x0 + size + 2, y0 + size + 22, 0xB0101010);
        g.drawString(mc.font, "FOE+ map scan", x0, y0 - 10, 0xFFFFD873, false);
        int pcx = mc.player.chunkPosition().x, pcz = mc.player.chunkPosition().z;
        if (area != null) {
            for (Chunk c : area.chunks.values()) {
                int dx = c.cx - pcx, dz = c.cz - pcz;
                if (Math.abs(dx) > span || Math.abs(dz) > span) continue;
                int px = x0 + (dx + span) * 2, py = y0 + (dz + span) * 2;
                g.fill(px, py, px + 2, py + 2, 0xFF4CD06A);
            }
        }
        g.fill(x0 + span * 2, y0 + span * 2, x0 + span * 2 + 2, y0 + span * 2 + 2, 0xFFFFFFFF);
        String place = area == null ? locationName() : area.location;
        g.drawString(mc.font, place, x0, y0 + size + 2, 0xFFE8D8B8, false);
        g.drawString(mc.font, (area == null ? 0 : area.chunks.size()) + " chunks  " + pois.size() + " spots" + (lastRate > 0 ? "  ..." : ""), x0, y0 + size + 12, 0xFFB8A888, false);
    }
}
