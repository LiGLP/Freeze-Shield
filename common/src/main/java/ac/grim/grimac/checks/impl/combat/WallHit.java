package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.CollisionData;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.HitData;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import ac.grim.grimac.utils.math.GrimMath;
import ac.grim.grimac.utils.math.Vector3dm;
import ac.grim.grimac.utils.nmsutil.ReachUtils;
import ac.grim.grimac.utils.nmsutil.WorldRayTrace;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;

/**
 * Attacking an entity when every possible line of sight to it passes through a solid block.
 * The vanilla client picks the block first and can't attack through it, but KillAura modules
 * (e.g. Meteor's "walls range", CrystalAura breaking through walls) don't raytrace.
 * <p>
 * Inspired by NoCheatPlus' fight.visible. Only full solid blocks count, shrunk slightly so grazing an edge
 * doesn't count, which keeps glass panes, fences, foliage and partial blocks from causing false flags.
 * The ray tracing itself happens in {@link Reach}, which already knows every possible eye position and look.
 */
@CheckData(name = "WallHit", stableKey = "grim.combat.wall_hit", description = "Attacked an entity through a solid block", decay = 0.01)
public class WallHit extends Check {
    private static final Verbose V = Verbose.of("distance={f64}, type={entity}");
    private static final double BLOCK_SHRINK = 0.1;
    private static final double BUFFER_LIMIT = 2;

    private double buffer;

    public WallHit(GrimPlayer player) {
        super(player);
    }

    void onWallHit(PacketEntity target, double distance) {
        if (++buffer > BUFFER_LIMIT) {
            buffer = BUFFER_LIMIT;
            flag(V.write(verbose()).f64(distance).uint(Math.max(0, target.getType().getId(PacketEvents.getAPI().getServerManager().getVersion().toClientVersion()))));
        }
    }

    void onCleanHit() {
        buffer = Math.max(0, buffer - 0.5);
    }

    static boolean isBlocked(GrimPlayer player, Vector3d eye, Vector3d hit) {
        final int eyeX = GrimMath.floor(eye.x), eyeY = GrimMath.floor(eye.y), eyeZ = GrimMath.floor(eye.z);
        final int hitX = GrimMath.floor(hit.x), hitY = GrimMath.floor(hit.y), hitZ = GrimMath.floor(hit.z);

        HitData blocked = WorldRayTrace.traverseBlocks(player, eye, hit, (state, pos) -> {
            // A player with their head in a block, or a target inside one, is a vanilla edge case we don't judge
            if (pos.x == eyeX && pos.y == eyeY && pos.z == eyeZ) return null;
            if (pos.x == hitX && pos.y == hitY && pos.z == hitZ) return null;

            if (!CollisionData.getData(state.getType()).getMovementCollisionBox(player, player.getClientVersion(), state, pos.x, pos.y, pos.z).isFullBlock()) {
                return null;
            }

            SimpleCollisionBox inner = new SimpleCollisionBox(pos.x + BLOCK_SHRINK, pos.y + BLOCK_SHRINK, pos.z + BLOCK_SHRINK,
                    pos.x + 1 - BLOCK_SHRINK, pos.y + 1 - BLOCK_SHRINK, pos.z + 1 - BLOCK_SHRINK);
            Vector3d intercept = ReachUtils.calculateIntercept(inner, eye, hit).first();
            return intercept == null ? null : new HitData(new Vector3i(pos.x, pos.y, pos.z), Vector3dm.from(intercept), BlockFace.OTHER, state);
        });

        return blocked != null;
    }
}
