package ac.grim.grimac.checks.impl.aim;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.impl.aim.processor.AimProcessor;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

/**
 * Mouse input can only turn the camera in multiples of a step derived from the player's sensitivity.
 * Combat modules (Meteor's KillAura, CrystalAura, BowAimbot, AnchorAura and Wurst's KillAura, AimAssist, ...)
 * set rotations to computed angles without snapping them to that step, so their rotations land off the grid.
 * <p>
 * Only checked while fighting, since that's when those modules rotate. Cinematic camera and some trackpads also
 * produce off-grid rotations, which is why this is experimental and buffered.
 */
@CheckData(name = "AimA", stableKey = "grim.aim.off_grid_rotation", description = "Rotated by amounts the player's mouse sensitivity cannot produce while fighting", experimental = true, decay = 0.005)
public class AimA extends Check implements RotationListener, PacketReceiveListener {
    private static final Verbose V = Verbose.of("delta={f64}, step={f64}, error={f64}");

    private static final int COMBAT_ROTATIONS = 40;
    // Small rotations are where sensitivity estimation is reliable; large flicks amplify estimation errors
    private static final double MIN_DELTA = 1;
    private static final double MAX_DELTA = 20;
    private static final double MAX_GRID_ERROR = 0.15;
    private static final double BUFFER_LIMIT = 8;

    private int combatRotations;
    private double buffer;

    public AimA(final GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            combatRotations = COMBAT_ROTATIONS;
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY
                && new WrapperPlayClientInteractEntity(event).getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            combatRotations = COMBAT_ROTATIONS;
        }
    }

    @Override
    public void process(final RotationUpdate rotationUpdate) {
        if (combatRotations <= 0) return;
        combatRotations--;

        // Server or vehicle driven rotation changes are not mouse input
        if (player.packetStateData.lastPacketWasTeleport || player.vehicleData.wasVehicleSwitch
                || player.packetStateData.horseInteractCausedForcedRotation) {
            return;
        }

        AimProcessor processor = player.checkManager.get(AimProcessor.class);
        double step = processor.modeYaw;
        if (step <= 0) return; // Sensitivity not known yet

        double delta = rotationUpdate.deltaYawABS();
        if (delta < MIN_DELTA || delta > MAX_DELTA) return;

        double dots = delta / step;
        double error = Math.abs(dots - Math.rint(dots));

        if (error > MAX_GRID_ERROR) {
            if (++buffer > BUFFER_LIMIT) {
                flag(V.write(verbose()).f64(delta).f64(step).f64(error));
            }
        } else {
            buffer = Math.max(0, buffer - 0.25);
            reward();
        }
    }
}
