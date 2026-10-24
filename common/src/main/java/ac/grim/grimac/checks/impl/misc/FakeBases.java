package ac.grim.grimac.checks.impl.misc;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.GrimProcessor;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.checks.type.PacketSendListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.fakebase.BaseSchematic;
import ac.grim.grimac.utils.fakebase.FakeBaseSchematics;
import ac.grim.grimac.utils.math.GrimMath;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;

import static com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying.isFlying;

/**
 * Fills the rock around each player with client-side decoy bases (stashes, farms, vaults, spawner rooms...).
 * <p>
 * The bases only exist in packets and are buried in solid rock, so a legit player can't see them. Freecam, xray
 * and block ESP users can, and waste their time on them: once a player's real position comes within
 * {@code fade-distance} the base starts crumbling back into the rock it replaced, a random part of it for every
 * block closer, and at {@code despawn-distance} it is gone, so walking (or digging) up to one only ever finds
 * stone. Freecam doesn't move the player's real position, so flying the camera up to a base doesn't touch it.
 * Only block states are sent, never block entity data or entities, so the chests are empty and nothing in them
 * can be taken.
 * <p>
 * The world is split into cells, columns of them on top of each other, and each can hold a base picked from a
 * seed that changes on every restart, so the same base is in the same place for every player (and after
 * relogging) until the server restarts. Bases too big for a cell (big farms and stashes) use a second, coarser
 * grid, and the small cells under them stay empty. Grim sees the block changes like any other, so its
 * simulation always matches what the client has.
 */
public class FakeBases extends GrimProcessor implements PacketReceiveListener, PacketSendListener {
    private static final long SEED = ThreadLocalRandom.current().nextLong();
    private static final long LARGE_SALT = 0x5DEECE66DL;
    private static final int UPDATE_INTERVAL = 10; // ticks
    private static final int Y_ATTEMPTS = 4;
    private static final int RETRY_REJECTED_TICKS = 600;
    // Bases only spawn this much further out than they despawn, so they don't flicker at the edge
    private static final int SPAWN_MARGIN = 8;
    // Rock left between a base and the edge of its cell
    private static final int CELL_MARGIN = 4;
    private static final Set<StateType> NATURAL_ROCK = Set.of(
            StateTypes.STONE, StateTypes.DEEPSLATE, StateTypes.TUFF, StateTypes.GRANITE, StateTypes.DIORITE,
            StateTypes.ANDESITE, StateTypes.CALCITE, StateTypes.DIRT, StateTypes.COARSE_DIRT, StateTypes.ROOTED_DIRT,
            StateTypes.GRAVEL, StateTypes.SAND, StateTypes.RED_SAND, StateTypes.SANDSTONE, StateTypes.RED_SANDSTONE,
            StateTypes.TERRACOTTA, StateTypes.CLAY, StateTypes.DRIPSTONE_BLOCK, StateTypes.NETHERRACK,
            StateTypes.SOUL_SAND, StateTypes.SOUL_SOIL, StateTypes.BASALT, StateTypes.SMOOTH_BASALT,
            StateTypes.BLACKSTONE, StateTypes.END_STONE);

    private boolean enabled;
    private int cellSize;
    private int layerHeight;
    private double chance;
    private int largeCellSize;
    private double largeChance;
    private int despawnDistance;
    private int fadeDistance;
    private int maxDistance;
    private int maxBases;
    private int maxBlocks;
    private int maxY;
    private int basesPerUpdate;
    private int cellsPerUpdate;

    private final Long2ObjectOpenHashMap<Base> bases = new Long2ObjectOpenHashMap<>();
    // Cells where the base didn't fit, and the tick to try them again
    private final Long2IntOpenHashMap rejected = new Long2IntOpenHashMap();
    private int ticks;
    // Fake blocks this player currently has
    private int blocks;
    // Work done in the current update
    private int evaluated, spawned;

    public FakeBases(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        enabled = config.getBooleanElse("fake-bases.enabled", true);
        cellSize = Math.max(24, config.getIntElse("fake-bases.cell-size", 32));
        layerHeight = Math.max(16, config.getIntElse("fake-bases.layer-height", 32));
        chance = Math.min(1, Math.max(0, config.getDoubleElse("fake-bases.chance", 0.9)));
        largeCellSize = Math.max(cellSize * 2, config.getIntElse("fake-bases.large-cell-size", 128));
        largeChance = Math.min(1, Math.max(0, config.getDoubleElse("fake-bases.large-chance", 0.8)));
        despawnDistance = Math.max(8, config.getIntElse("fake-bases.despawn-distance", 24));
        fadeDistance = Math.max(despawnDistance, config.getIntElse("fake-bases.fade-distance", 64));
        maxDistance = Math.max(despawnDistance + SPAWN_MARGIN + 16, config.getIntElse("fake-bases.max-distance", 128));
        maxBases = Math.max(1, config.getIntElse("fake-bases.max-bases", 200));
        maxBlocks = Math.max(10_000, config.getIntElse("fake-bases.max-blocks", 500_000));
        maxY = config.getIntElse("fake-bases.max-y", 320);
        basesPerUpdate = Math.max(1, config.getIntElse("fake-bases.bases-per-update", 4));
        cellsPerUpdate = Math.max(1, config.getIntElse("fake-bases.cells-per-update", 32));
        // GrimProcessor's constructor reloads before our field initializers have run
        if (rejected != null) rejected.clear();
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        // The client throws its world away, and our bases with it
        if (event.getPacketType() == PacketType.Play.Server.RESPAWN || event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
            bases.clear();
            rejected.clear();
            blocks = 0;
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!isFlying(event.getPacketType())) return;
        if (!player.getSetbackTeleportUtil().hasAcceptedSpawnTeleport) return;

        if (!enabled || player.gamemode == GameMode.SPECTATOR) {
            if (!bases.isEmpty()) removeAll();
            return;
        }

        // Every movement, so even an elytra can't get close enough to look at one
        for (ObjectIterator<Base> it = bases.values().iterator(); it.hasNext(); ) {
            Base base = it.next();
            int target = fadedCount(base, base.distanceSq(player.x, player.y, player.z));
            if (target <= base.faded) continue;

            restore(base, target);
            if (base.faded == base.size) it.remove();
        }

        if (++ticks % UPDATE_INTERVAL != 0) return;
        validate();
        spawn();
    }

    // How many of the base's blocks should be gone at this distance: none outside fade-distance, all inside despawn-distance
    private int fadedCount(Base base, double distanceSq) {
        if (distanceSq >= (double) fadeDistance * fadeDistance) return 0;
        double distance = Math.sqrt(distanceSq);
        if (distance <= despawnDistance) return base.size;
        double gone = (fadeDistance - distance) / (fadeDistance - despawnDistance);
        return Math.min(base.size, (int) Math.ceil(gone * base.size));
    }

    private void removeAll() {
        for (Base base : bases.values()) restore(base, base.size);
        bases.clear();
        blocks = 0;
    }

    // Forgets bases the client lost or the server overwrote, and makes room by dropping ones far behind the player
    private void validate() {
        for (ObjectIterator<Base> it = bases.values().iterator(); it.hasNext(); ) {
            Base base = it.next();
            if (!player.compensatedWorld.isChunkLoaded(base.minX >> 4, base.minZ >> 4) || !player.compensatedWorld.isChunkLoaded(base.maxX >> 4, base.minZ >> 4)
                    || !player.compensatedWorld.isChunkLoaded(base.minX >> 4, base.maxZ >> 4) || !player.compensatedWorld.isChunkLoaded(base.maxX >> 4, base.maxZ >> 4)) {
                blocks -= base.size - base.faded;
                it.remove(); // unloaded, nothing to restore
                continue;
            }

            boolean overwritten = false;
            if (base.isAcknowledged()) {
                for (int i : base.sentinels) {
                    if (i < base.faded) continue; // already rock again
                    if (player.compensatedWorld.getBlockStateId(base.x(i), base.y(i), base.z(i)) != base.fake(i)) {
                        overwritten = true; // a chunk was resent
                        break;
                    }
                }
            }

            int far = maxDistance + Math.max(base.maxX - base.minX, base.maxZ - base.minZ) + 1;
            if (overwritten || base.distanceSq(player.x, player.y, player.z) > (double) far * far) {
                restore(base, base.size);
                it.remove();
            }
        }
    }

    private void spawn() {
        if (bases.size() >= maxBases || blocks >= maxBlocks) return;
        evaluated = 0;
        spawned = 0;

        List<BaseSchematic> schematics = FakeBaseSchematics.get();
        int largeCell = largeCellSize(schematics);
        int worldMin = player.compensatedWorld.getMinHeight();
        int worldMax = Math.min(maxY, player.compensatedWorld.getMaxHeight() - 2);

        if (largeCell > 0) {
            for (int cellX = minCell(player.x, largeCell); cellX <= maxCell(player.x, largeCell); cellX++) {
                for (int cellZ = minCell(player.z, largeCell); cellZ <= maxCell(player.z, largeCell); cellZ++) {
                    long key = key(cellX, cellZ, 0, true);
                    if (!isFree(key)) continue;
                    Plan plan = plan(true, cellX, cellZ, 0, largeCell, schematics);
                    if (!trySpawn(key, plan, worldMin + 3, worldMax)) return;
                }
            }
        }

        // Only the layers a base in them could be close enough to show
        int minLayer = Math.max(Math.floorDiv(worldMin, layerHeight), Math.floorDiv(GrimMath.floor(player.y) - maxDistance, layerHeight));
        int maxLayer = Math.min(Math.floorDiv(worldMax, layerHeight), Math.floorDiv(GrimMath.floor(player.y) + maxDistance, layerHeight));

        for (int cellX = minCell(player.x, cellSize); cellX <= maxCell(player.x, cellSize); cellX++) {
            for (int cellZ = minCell(player.z, cellSize); cellZ <= maxCell(player.z, cellSize); cellZ++) {
                if (largeCell > 0 && isUnderLargeBase(cellX, cellZ, largeCell, schematics)) continue;

                for (int layer = minLayer; layer <= maxLayer; layer++) {
                    long key = key(cellX, cellZ, layer, false);
                    if (!isFree(key)) continue;
                    // A block of rock between the layers too
                    int floor = Math.max(worldMin + 3, layer * layerHeight + 1);
                    int ceiling = Math.min(worldMax, (layer + 1) * layerHeight - 1);
                    if (!trySpawn(key, plan(false, cellX, cellZ, layer, cellSize, schematics), floor, ceiling)) return;
                }
            }
        }
    }

    // 24 bits each for the cell's X and Z (cells are at least 24 blocks, so that covers the whole world), 15 for the layer
    private static long key(int cellX, int cellZ, int layer, boolean large) {
        return ((long) (cellX & 0xFFFFFF) << 40) | ((long) (cellZ & 0xFFFFFF) << 16) | ((long) (layer & 0x7FFF) << 1) | (large ? 1 : 0);
    }

    private int minCell(double pos, int size) {
        return Math.floorDiv(GrimMath.floor(pos) - maxDistance, size);
    }

    private int maxCell(double pos, int size) {
        return Math.floorDiv(GrimMath.floor(pos) + maxDistance, size);
    }

    // Not already shown, and not waiting to retry
    private boolean isFree(long key) {
        if (bases.containsKey(key)) return false;
        if (rejected.containsKey(key)) {
            if (ticks < rejected.get(key)) return false;
            rejected.remove(key);
        }
        return true;
    }

    /** Where a cell's base goes, the same for every player. Null if the cell has none. */
    private Plan plan(boolean large, int cellX, int cellZ, int layer, int size, List<BaseSchematic> schematics) {
        long seed = SEED ^ (large ? LARGE_SALT : 0) ^ (cellX * 0x9E3779B97F4A7C15L) ^ (cellZ * 0xC2B2AE3D27D4EB4FL)
                ^ (layer * 0x632BE59BD9B4E019L);
        SplittableRandom random = new SplittableRandom(seed);
        if (random.nextDouble() >= (large ? largeChance : chance)) return null; // this cell never has a base

        BaseSchematic schematic = pick(random, large, schematics);
        if (schematic == null) return null;
        int rotation = random.nextInt(4);
        int width = schematic.width(rotation);
        int depth = schematic.depth(rotation);
        // Keep a wall of untouched rock between neighbouring cells' bases
        int x = cellX * size + 1 + random.nextInt(Math.max(1, size - width - 2));
        int z = cellZ * size + 1 + random.nextInt(Math.max(1, size - depth - 2));
        return new Plan(schematic, rotation, x, z, width, depth, random);
    }

    // Small cells a large base covers stay empty in every layer, so the two never overlap
    private boolean isUnderLargeBase(int cellX, int cellZ, int largeCell, List<BaseSchematic> schematics) {
        int minX = cellX * cellSize, maxX = minX + cellSize - 1;
        int minZ = cellZ * cellSize, maxZ = minZ + cellSize - 1;
        for (int largeX = Math.floorDiv(minX, largeCell); largeX <= Math.floorDiv(maxX, largeCell); largeX++) {
            for (int largeZ = Math.floorDiv(minZ, largeCell); largeZ <= Math.floorDiv(maxZ, largeCell); largeZ++) {
                Plan plan = plan(true, largeX, largeZ, 0, largeCell, schematics);
                if (plan != null && plan.x - 1 <= maxX && plan.x + plan.width >= minX && plan.z - 1 <= maxZ && plan.z + plan.depth >= minZ) {
                    return true;
                }
            }
        }
        return false;
    }

    // Places the plan with its bottom between floor and its top below ceiling. Returns false once this update's budget is used up
    private boolean trySpawn(long key, Plan plan, int floor, int ceiling) {
        if (plan == null) return true;
        BaseSchematic schematic = plan.schematic;
        int x = plan.x, z = plan.z, width = plan.width, depth = plan.depth;

        int highestY = ceiling - schematic.sizeY;
        if (highestY < floor) return true;

        double dx = Base.axisDistance(player.x, x, x + width - 1);
        double dz = Base.axisDistance(player.z, z, z + depth - 1);
        double horizontalSq = dx * dx + dz * dz;
        double dyMin = Base.axisDistance(player.y, floor, ceiling);
        if (horizontalSq + dyMin * dyMin > (double) maxDistance * maxDistance) return true;

        // Need the margin around it too
        if (!player.compensatedWorld.isChunkLoaded((x - 1) >> 4, (z - 1) >> 4) || !player.compensatedWorld.isChunkLoaded((x + width) >> 4, (z - 1) >> 4)
                || !player.compensatedWorld.isChunkLoaded((x - 1) >> 4, (z + depth) >> 4) || !player.compensatedWorld.isChunkLoaded((x + width) >> 4, (z + depth) >> 4))
            return true;

        // A smaller base may still fit in what's left
        if (blocks + width * schematic.sizeY * depth > maxBlocks) return true;

        if (++evaluated > cellsPerUpdate) return false;

        double minSq = (double) (despawnDistance + SPAWN_MARGIN) * (despawnDistance + SPAWN_MARGIN);
        Base base = null;
        for (int attempt = 0; attempt < Y_ATTEMPTS && base == null; attempt++) {
            int y = floor + plan.random.nextInt(highestY - floor + 1);
            double dy = Base.axisDistance(player.y, y, y + schematic.sizeY - 1);
            if (horizontalSq + dy * dy < minSq) continue; // too close to show right now, try again later
            if (isBuriedInRock(x, y, z, width, schematic.sizeY, depth)) {
                base = build(schematic, plan.rotation, x, y, z);
            }
        }

        if (base == null) {
            rejected.put(key, ticks + RETRY_REJECTED_TICKS);
            return true;
        }

        // Inside fade-distance already, so part of it never shows up
        base.faded = fadedCount(base, base.distanceSq(player.x, player.y, player.z));
        bases.put(key, base);
        blocks += base.size - base.faded;
        send(base, base.faded, base.size, true);
        player.sendTransaction();
        base.transaction = player.lastTransactionSent.get();

        return ++spawned < basesPerUpdate && bases.size() < maxBases && blocks < maxBlocks;
    }

    // Weighted pick among the bases for this kind of cell
    private BaseSchematic pick(SplittableRandom random, boolean large, List<BaseSchematic> schematics) {
        int total = 0;
        for (BaseSchematic schematic : schematics) {
            if (isSmall(schematic) != large) total += schematic.weight;
        }
        if (total == 0) return null;

        int roll = random.nextInt(total);
        for (BaseSchematic schematic : schematics) {
            if (isSmall(schematic) == large) continue;
            roll -= schematic.weight;
            if (roll < 0) return schematic;
        }
        return null;
    }

    // Fits a small cell and a layer with a wall of rock to spare
    private boolean isSmall(BaseSchematic schematic) {
        return Math.max(schematic.sizeX, schematic.sizeZ) <= cellSize - CELL_MARGIN && schematic.sizeY <= layerHeight - 2;
    }

    // Big enough for the widest large base, or 0 if there are none
    private int largeCellSize(List<BaseSchematic> schematics) {
        int widest = 0;
        for (BaseSchematic schematic : schematics) {
            if (!isSmall(schematic)) widest = Math.max(widest, Math.max(schematic.sizeX, schematic.sizeZ));
        }
        return widest == 0 ? 0 : Math.max(largeCellSize, widest + CELL_MARGIN);
    }

    // A one block shell of rock around the base, or a cave or tunnel would show it to legit players. The inside
    // is replaced anyway, and with a solid shell nobody can see into it
    private boolean isBuriedInRock(int x, int y, int z, int width, int height, int depth) {
        for (int bx = x - 1; bx <= x + width; bx++) {
            for (int by = y - 1; by <= y + height; by++) {
                boolean side = bx == x - 1 || bx == x + width || by == y - 1 || by == y + height;
                for (int bz = z - 1; bz <= z + depth; bz += side ? 1 : depth + 1) {
                    if (!isRock(player.compensatedWorld.getBlock(bx, by, bz).getType())) return false;
                }
            }
        }
        return true;
    }

    private static boolean isRock(StateType type) {
        if (NATURAL_ROCK.contains(type)) return true;
        String name = type.getName().toLowerCase(Locale.ROOT);
        // Badlands terracotta, but not the glazed kind players place
        return name.endsWith("_ore") || (name.endsWith("_terracotta") && !name.contains("glazed"));
    }

    private Base build(BaseSchematic schematic, int rotation, int x, int y, int z) {
        int width = schematic.width(rotation);
        int depth = schematic.depth(rotation);
        int volume = width * schematic.sizeY * depth;
        int[] local = new int[volume];
        int[] original = new int[volume];
        int size = 0;

        for (int by = 0; by < schematic.sizeY; by++) {
            for (int bz = 0; bz < depth; bz++) {
                for (int bx = 0; bx < width; bx++) {
                    int fake = schematic.blockAt(bx, by, bz, rotation);
                    if (fake == BaseSchematic.KEEP) continue;
                    int id = player.compensatedWorld.getBlockStateId(x + bx, y + by, z + bz);
                    if (fake == id) continue;

                    local[size] = bx + width * (bz + depth * by);
                    original[size] = id;
                    size++;
                }
            }
        }

        // Random order, so fading takes away blocks from all over the base instead of one layer at a time
        SplittableRandom random = new SplittableRandom(ThreadLocalRandom.current().nextLong());
        for (int i = size - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = local[i];
            local[i] = local[j];
            local[j] = swap;
            swap = original[i];
            original[i] = original[j];
            original[j] = swap;
        }

        Base base = new Base(schematic, rotation, x, y, z, Arrays.copyOf(local, size), Arrays.copyOf(original, size));

        // One block per chunk column to check the base is still there, since chunks are what gets resent. The
        // last ones to fade, so they stay useful the longest
        LongOpenHashSet chunks = new LongOpenHashSet();
        IntArrayList sentinels = new IntArrayList();
        for (int i = size - 1; i >= 0; i--) {
            if (chunks.add(((long) (base.x(i) >> 4) << 32) | ((base.z(i) >> 4) & 0xFFFFFFFFL))) sentinels.add(i);
        }
        base.sentinels = sentinels.toIntArray();
        return base;
    }

    // Turns the base's blocks up to (not including) index `to` back into what the server has
    private void restore(Base base, int to) {
        if (to <= base.faded) return;
        send(base, base.faded, to, false);
        blocks -= to - base.faded;
        base.faded = to;
    }

    // One multi block change per chunk section, of either the fake blocks or the originals in [from, to)
    private void send(Base base, int from, int to, boolean fake) {
        boolean acknowledged = base.isAcknowledged();
        Long2ObjectOpenHashMap<List<WrapperPlayServerMultiBlockChange.EncodedBlock>> sections = new Long2ObjectOpenHashMap<>();
        for (int i = from; i < to; i++) {
            int x = base.x(i), y = base.y(i), z = base.z(i);
            int id;
            if (fake) {
                id = base.fake(i);
            } else {
                // Leave alone what the server changed since; before the client confirmed the base, everything is ours
                if (acknowledged && player.compensatedWorld.getBlockStateId(x, y, z) != base.fake(i)) continue;
                id = base.original[i];
            }
            long section = ((long) (x >> 4) & 0x3FFFFF) << 42 | ((long) (z >> 4) & 0x3FFFFF) << 20 | ((long) (y >> 4) & 0xFFFFF);
            sections.computeIfAbsent(section, k -> new ArrayList<>())
                    .add(new WrapperPlayServerMultiBlockChange.EncodedBlock(id, x, y, z));
        }

        for (List<WrapperPlayServerMultiBlockChange.EncodedBlock> sectionBlocks : sections.values()) {
            WrapperPlayServerMultiBlockChange.EncodedBlock first = sectionBlocks.get(0);
            Vector3i sectionPos = new Vector3i(first.getX() >> 4, first.getY() >> 4, first.getZ() >> 4);
            player.user.sendPacket(new WrapperPlayServerMultiBlockChange(sectionPos, true,
                    sectionBlocks.toArray(new WrapperPlayServerMultiBlockChange.EncodedBlock[0])));
        }
    }

    private record Plan(BaseSchematic schematic, int rotation, int x, int z, int width, int depth, SplittableRandom random) {}

    private final class Base {
        final BaseSchematic schematic;
        final int rotation;
        final int minX, minY, minZ, maxX, maxY, maxZ;
        final int width, depth;
        // Changed blocks in fade order, as indexes into the base's box (x first, then z, then y); the fake block
        // comes from the schematic, so big bases only cost 8 bytes a block
        final int[] local, original;
        final int size;
        // Blocks [0, faded) are rock again
        int faded;
        int[] sentinels;
        int transaction;

        Base(BaseSchematic schematic, int rotation, int minX, int minY, int minZ, int[] local, int[] original) {
            this.schematic = schematic;
            this.rotation = rotation;
            this.width = schematic.width(rotation);
            this.depth = schematic.depth(rotation);
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = minX + width - 1;
            this.maxY = minY + schematic.sizeY - 1;
            this.maxZ = minZ + depth - 1;
            this.local = local;
            this.original = original;
            this.size = local.length;
        }

        int x(int i) {
            return minX + local[i] % width;
        }

        int y(int i) {
            return minY + local[i] / (width * depth);
        }

        int z(int i) {
            return minZ + (local[i] / width) % depth;
        }

        int fake(int i) {
            return schematic.blockAt(local[i] % width, local[i] / (width * depth), (local[i] / width) % depth, rotation);
        }

        // Grim's world only shows the base once the client got it
        boolean isAcknowledged() {
            return player.lastTransactionReceived.get() >= transaction;
        }

        double distanceSq(double x, double y, double z) {
            double dx = axisDistance(x, minX, maxX);
            double dy = axisDistance(y, minY, maxY);
            double dz = axisDistance(z, minZ, maxZ);
            return dx * dx + dy * dy + dz * dz;
        }

        static double axisDistance(double pos, int min, int max) {
            if (pos < min) return min - pos;
            if (pos > max + 1) return pos - (max + 1);
            return 0;
        }
    }
}
