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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;

import static com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying.isFlying;

/**
 * Fills the rock around each player with client-side decoy bases (stashes, vaults, spawner rooms...).
 * <p>
 * The bases only exist in packets and are buried in solid rock, so a legit player can't see them. Freecam, xray
 * and block ESP users can, and waste their time on them: once a player's real position comes within
 * {@code despawn-distance} the base turns back into the rock it replaced, so walking (or digging) up to one
 * only ever finds stone. Freecam doesn't move the player's real position, so flying the camera up to a base
 * doesn't make it go away.
 * <p>
 * Bases are picked per grid cell from a seed that changes on every restart, so the same base is in the same
 * place for every player (and after relogging) until the server restarts. Grim sees the block changes like any
 * other, so its simulation always matches what the client has.
 */
public class FakeBases extends GrimProcessor implements PacketReceiveListener, PacketSendListener {
    private static final long SEED = ThreadLocalRandom.current().nextLong();
    private static final int UPDATE_INTERVAL = 10; // ticks
    private static final int Y_ATTEMPTS = 4;
    private static final int RETRY_REJECTED_TICKS = 600;
    // Bases only spawn this much further out than they despawn, so they don't flicker at the edge
    private static final int SPAWN_MARGIN = 8;
    private static final Set<StateType> NATURAL_ROCK = Set.of(
            StateTypes.STONE, StateTypes.DEEPSLATE, StateTypes.TUFF, StateTypes.GRANITE, StateTypes.DIORITE,
            StateTypes.ANDESITE, StateTypes.CALCITE, StateTypes.DIRT, StateTypes.NETHERRACK, StateTypes.BASALT,
            StateTypes.SMOOTH_BASALT, StateTypes.BLACKSTONE, StateTypes.END_STONE);

    private boolean enabled;
    private int cellSize;
    private double chance;
    private int despawnDistance;
    private int maxDistance;
    private int maxBases;
    private int maxY;
    private int basesPerUpdate;
    private int cellsPerUpdate;

    private final Long2ObjectOpenHashMap<Base> bases = new Long2ObjectOpenHashMap<>();
    // Cells where the base didn't fit, and the tick to try them again
    private final Long2IntOpenHashMap rejected = new Long2IntOpenHashMap();
    private int ticks;

    public FakeBases(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        enabled = config.getBooleanElse("fake-bases.enabled", true);
        cellSize = Math.max(24, config.getIntElse("fake-bases.cell-size", 32));
        chance = Math.min(1, Math.max(0, config.getDoubleElse("fake-bases.chance", 0.8)));
        despawnDistance = Math.max(8, config.getIntElse("fake-bases.despawn-distance", 24));
        maxDistance = Math.max(despawnDistance + SPAWN_MARGIN + 16, config.getIntElse("fake-bases.max-distance", 128));
        maxBases = Math.max(1, config.getIntElse("fake-bases.max-bases", 100));
        maxY = config.getIntElse("fake-bases.max-y", 40);
        basesPerUpdate = Math.max(1, config.getIntElse("fake-bases.bases-per-update", 3));
        cellsPerUpdate = Math.max(1, config.getIntElse("fake-bases.cells-per-update", 8));
        rejected.clear();
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        // The client throws its world away, and our bases with it
        if (event.getPacketType() == PacketType.Play.Server.RESPAWN || event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
            bases.clear();
            rejected.clear();
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
        long despawnSq = (long) despawnDistance * despawnDistance;
        for (ObjectIterator<Base> it = bases.values().iterator(); it.hasNext(); ) {
            Base base = it.next();
            if (base.distanceSq(player.x, player.y, player.z) < despawnSq) {
                restore(base);
                it.remove();
            }
        }

        if (++ticks % UPDATE_INTERVAL != 0) return;
        validate();
        spawn();
    }

    private void removeAll() {
        for (Base base : bases.values()) restore(base);
        bases.clear();
    }

    // Forgets bases the client lost or the server overwrote, and makes room by dropping ones far behind the player
    private void validate() {
        double farSq = (double) (maxDistance + cellSize) * (maxDistance + cellSize);
        for (ObjectIterator<Base> it = bases.values().iterator(); it.hasNext(); ) {
            Base base = it.next();
            if (!player.compensatedWorld.isChunkLoaded(base.minX >> 4, base.minZ >> 4) || !player.compensatedWorld.isChunkLoaded(base.maxX >> 4, base.minZ >> 4)
                    || !player.compensatedWorld.isChunkLoaded(base.minX >> 4, base.maxZ >> 4) || !player.compensatedWorld.isChunkLoaded(base.maxX >> 4, base.maxZ >> 4)) {
                it.remove(); // unloaded, nothing to restore
                continue;
            }

            boolean overwritten = false;
            if (base.isAcknowledged()) {
                for (int i : base.sentinels) {
                    if (player.compensatedWorld.getBlockStateId(base.xs[i], base.ys[i], base.zs[i]) != base.fake[i]) {
                        overwritten = true; // a chunk was resent
                        break;
                    }
                }
            }

            if (overwritten || base.distanceSq(player.x, player.y, player.z) > farSq) {
                restore(base);
                it.remove();
            }
        }
    }

    private void spawn() {
        if (bases.size() >= maxBases) return;

        int minCellX = Math.floorDiv(GrimMath.floor(player.x) - maxDistance, cellSize);
        int maxCellX = Math.floorDiv(GrimMath.floor(player.x) + maxDistance, cellSize);
        int minCellZ = Math.floorDiv(GrimMath.floor(player.z) - maxDistance, cellSize);
        int maxCellZ = Math.floorDiv(GrimMath.floor(player.z) + maxDistance, cellSize);
        double minSq = (double) (despawnDistance + SPAWN_MARGIN) * (despawnDistance + SPAWN_MARGIN);
        double maxSq = (double) maxDistance * maxDistance;

        int evaluated = 0;
        int spawned = 0;
        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                long key = ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
                if (bases.containsKey(key)) continue;
                if (rejected.containsKey(key)) {
                    if (ticks < rejected.get(key)) continue;
                    rejected.remove(key);
                }

                SplittableRandom random = new SplittableRandom(SEED ^ (cellX * 0x9E3779B97F4A7C15L) ^ (cellZ * 0xC2B2AE3D27D4EB4FL));
                if (random.nextDouble() >= chance) continue; // this cell never has a base

                BaseSchematic schematic = pick(random);
                if (schematic == null) continue;
                int rotation = random.nextInt(4);
                int width = schematic.width(rotation);
                int depth = schematic.depth(rotation);
                // Keep a wall of untouched rock between neighbouring cells' bases
                int x = cellX * cellSize + 1 + random.nextInt(Math.max(1, cellSize - width - 2));
                int z = cellZ * cellSize + 1 + random.nextInt(Math.max(1, cellSize - depth - 2));

                double horizontalSq = Base.axisDistance(player.x, x, x + width - 1) * Base.axisDistance(player.x, x, x + width - 1)
                        + Base.axisDistance(player.z, z, z + depth - 1) * Base.axisDistance(player.z, z, z + depth - 1);
                if (horizontalSq > maxSq) continue;

                // Need the margin around it too
                if (!player.compensatedWorld.isChunkLoaded((x - 1) >> 4, (z - 1) >> 4) || !player.compensatedWorld.isChunkLoaded((x + width) >> 4, (z - 1) >> 4)
                        || !player.compensatedWorld.isChunkLoaded((x - 1) >> 4, (z + depth) >> 4) || !player.compensatedWorld.isChunkLoaded((x + width) >> 4, (z + depth) >> 4))
                    continue;

                int lowestY = player.compensatedWorld.getMinHeight() + 3;
                int highestY = Math.min(maxY, player.compensatedWorld.getMaxHeight() - 2) - schematic.sizeY;
                if (highestY < lowestY) continue;

                if (++evaluated > cellsPerUpdate) return;

                Base base = null;
                for (int attempt = 0; attempt < Y_ATTEMPTS && base == null; attempt++) {
                    int y = lowestY + random.nextInt(highestY - lowestY + 1);
                    double dy = Base.axisDistance(player.y, y, y + schematic.sizeY - 1);
                    if (horizontalSq + dy * dy < minSq) continue; // too close to show right now, try again later
                    if (isBuriedInRock(x, y, z, width, schematic.sizeY, depth)) {
                        base = build(schematic, rotation, x, y, z);
                    }
                }

                if (base == null) {
                    rejected.put(key, ticks + RETRY_REJECTED_TICKS);
                    continue;
                }

                bases.put(key, base);
                send(base, base.fake);
                player.sendTransaction();
                base.transaction = player.lastTransactionSent.get();

                if (++spawned >= basesPerUpdate || bases.size() >= maxBases) return;
            }
        }
    }

    // Weighted pick among the bases that fit in a cell with a wall of rock to spare
    private BaseSchematic pick(SplittableRandom random) {
        List<BaseSchematic> schematics = FakeBaseSchematics.get();
        int total = 0;
        for (BaseSchematic schematic : schematics) {
            if (fitsCell(schematic)) total += schematic.weight;
        }
        if (total == 0) return null;

        int roll = random.nextInt(total);
        for (BaseSchematic schematic : schematics) {
            if (!fitsCell(schematic)) continue;
            roll -= schematic.weight;
            if (roll < 0) return schematic;
        }
        return null;
    }

    private boolean fitsCell(BaseSchematic schematic) {
        return Math.max(schematic.sizeX, schematic.sizeZ) <= cellSize - 4;
    }

    // The base and a one block shell around it must all be rock, or a cave or tunnel would show it to legit players
    private boolean isBuriedInRock(int x, int y, int z, int width, int height, int depth) {
        for (int bx = x - 1; bx <= x + width; bx++) {
            for (int by = y - 1; by <= y + height; by++) {
                for (int bz = z - 1; bz <= z + depth; bz++) {
                    if (!isRock(player.compensatedWorld.getBlock(bx, by, bz).getType())) return false;
                }
            }
        }
        return true;
    }

    private static boolean isRock(StateType type) {
        return NATURAL_ROCK.contains(type) || type.getName().toLowerCase(Locale.ROOT).endsWith("_ore");
    }

    private Base build(BaseSchematic schematic, int rotation, int x, int y, int z) {
        int width = schematic.width(rotation);
        int depth = schematic.depth(rotation);
        int volume = width * schematic.sizeY * depth;
        Base base = new Base(x, y, z, x + width - 1, y + schematic.sizeY - 1, z + depth - 1, volume);

        // One block per chunk column to check the base is still there, since chunks are what gets resent
        LongOpenHashSet chunks = new LongOpenHashSet();
        IntArrayList sentinels = new IntArrayList();
        for (int bx = 0; bx < width; bx++) {
            for (int bz = 0; bz < depth; bz++) {
                for (int by = 0; by < schematic.sizeY; by++) {
                    int fake = schematic.blockAt(bx, by, bz, rotation);
                    if (fake == BaseSchematic.KEEP) continue;
                    int original = player.compensatedWorld.getBlockStateId(x + bx, y + by, z + bz);
                    if (fake == original) continue;

                    int i = base.add(x + bx, y + by, z + bz, fake, original);
                    if (chunks.add(((long) ((x + bx) >> 4) << 32) | (((z + bz) >> 4) & 0xFFFFFFFFL))) sentinels.add(i);
                }
            }
        }
        base.sentinels = sentinels.toIntArray();
        return base;
    }

    private void restore(Base base) {
        if (base.size == 0) return;

        int[] ids = new int[base.size];
        boolean acknowledged = base.isAcknowledged();
        for (int i = 0; i < base.size; i++) {
            // Leave alone what the server changed since; before the client confirmed the base, everything is ours
            ids[i] = !acknowledged || player.compensatedWorld.getBlockStateId(base.xs[i], base.ys[i], base.zs[i]) == base.fake[i]
                    ? base.original[i] : -1;
        }
        send(base, ids);
    }

    // One multi block change per chunk section, -1 ids are skipped
    private void send(Base base, int[] ids) {
        Long2ObjectOpenHashMap<List<WrapperPlayServerMultiBlockChange.EncodedBlock>> sections = new Long2ObjectOpenHashMap<>();
        for (int i = 0; i < base.size; i++) {
            if (ids[i] == -1) continue;
            long section = ((long) (base.xs[i] >> 4) & 0x3FFFFF) << 42 | ((long) (base.zs[i] >> 4) & 0x3FFFFF) << 20 | ((long) (base.ys[i] >> 4) & 0xFFFFF);
            sections.computeIfAbsent(section, k -> new ArrayList<>())
                    .add(new WrapperPlayServerMultiBlockChange.EncodedBlock(ids[i], base.xs[i], base.ys[i], base.zs[i]));
        }

        for (List<WrapperPlayServerMultiBlockChange.EncodedBlock> blocks : sections.values()) {
            WrapperPlayServerMultiBlockChange.EncodedBlock first = blocks.get(0);
            Vector3i sectionPos = new Vector3i(first.getX() >> 4, first.getY() >> 4, first.getZ() >> 4);
            player.user.sendPacket(new WrapperPlayServerMultiBlockChange(sectionPos, true,
                    blocks.toArray(new WrapperPlayServerMultiBlockChange.EncodedBlock[0])));
        }
    }

    private final class Base {
        final int minX, minY, minZ, maxX, maxY, maxZ;
        final int[] xs, ys, zs, fake, original;
        int size;
        int[] sentinels;
        int transaction;

        Base(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int capacity) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            xs = new int[capacity];
            ys = new int[capacity];
            zs = new int[capacity];
            fake = new int[capacity];
            original = new int[capacity];
        }

        int add(int x, int y, int z, int fakeId, int originalId) {
            xs[size] = x;
            ys[size] = y;
            zs[size] = z;
            fake[size] = fakeId;
            original[size] = originalId;
            return size++;
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
