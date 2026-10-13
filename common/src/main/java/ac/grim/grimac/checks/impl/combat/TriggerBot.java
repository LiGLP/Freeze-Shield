package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import ac.grim.grimac.utils.math.Vector3dm;
import ac.grim.grimac.utils.nmsutil.ReachUtils;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAnimation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;

/**
 * TriggerBot (Meteor's and Wurst's TriggerBot) attacks the moment an attack becomes possible: the tick the
 * crosshair lands on a player, or (1.9+) the tick the attack cooldown is full while already aiming at one.
 * Humans need 150+ ms (3+ ticks) to react to either, unless they spam click, which leaves missed swings.
 * <p>
 * Every tick we raytrace the player's look against the players around them (with Grim's position uncertainty,
 * so the crosshair counts as "on" a target as early as it possibly could be). For attacks without missed swings
 * before them, we measure the ticks from "attack possible" to the attack. Flags when nearly all of the last
 * attacks came within a tick.
 */
@CheckData(name = "TriggerBot", stableKey = "grim.combat.triggerbot", description = "Attacked the instant an attack was possible", experimental = true)
public class TriggerBot extends Check implements PacketReceiveListener {
    private static final Verbose V = Verbose.of("instant={sint}, samples={sint}, entry={sint}, cooldown={sint}");
    private static final int SAMPLES = 20;
    private static final int REQUIRED = 19;
    private static final int INSTANT_TICKS = 1;
    private static final int MIN_OFF_TICKS = 6; // the crosshair must have been off the target this long before
    private static final int SPAM_TICKS = 10; // a missed swing this recently means the player is spam clicking
    private static final int FORGET_TICKS = 20;

    private static final class Target {
        long streakStart = -1;
        int offTicks;
        int offBeforeStreak;
        long lastSeen;
    }

    private record PendingAttack(int entityId, boolean clean, int cooldownReadyAgo) {
    }

    private final Int2ObjectOpenHashMap<Target> targets = new Int2ObjectOpenHashMap<>();
    private final List<PendingAttack> pending = new ArrayList<>();
    private final boolean[] samples = new boolean[SAMPLES];
    private int sampleIndex;
    private int sampleCount;
    private int entrySamples;
    private int cooldownSamples;

    private long tick;
    private long lastMissedSwingTick = Long.MIN_VALUE / 2;
    private boolean swingThisTick;
    private boolean attackThisTick;

    public TriggerBot(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        var type = event.getPacketType();

        if (type == PacketType.Play.Client.ANIMATION) {
            if (new WrapperPlayClientAnimation(event).getHand() == InteractionHand.MAIN_HAND) swingThisTick = true;
        } else if (type == PacketType.Play.Client.ATTACK) {
            onAttack(new WrapperPlayClientAttack(event).getEntityId());
        } else if (type == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) onAttack(packet.getEntityId());
        }

        if (isTickPacket(type)) onTick();
    }

    private void onAttack(int entityId) {
        attackThisTick = true;
        if (player.disableGrim || player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return;

        boolean clean = tick - lastMissedSwingTick > SPAM_TICKS;
        int cooldownReadyAgo = Integer.MIN_VALUE; // no attack cooldown before 1.9
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9)) {
            double attackSpeed = player.compensatedEntities.self.getAttributeValue(Attributes.ATTACK_SPEED);
            int fullAfter = (int) Math.ceil(20 / attackSpeed - 0.5);
            // Read before the swing that follows the attack resets it
            cooldownReadyAgo = player.attackCooldown.getTicksSinceLastSwing() - fullAfter;
        }
        pending.add(new PendingAttack(entityId, clean, cooldownReadyAgo));
    }

    private void onTick() {
        tick++;
        if (swingThisTick && !attackThisTick) lastMissedSwingTick = tick;
        swingThisTick = false;
        attackThisTick = false;

        if (!player.isTickingReliablyFor(3) || !player.cameraEntity.isSelf()) {
            // A skipped tick would make reactions look faster than they were
            targets.clear();
            pending.clear();
            return;
        }

        updateTargets();
        for (PendingAttack attack : pending) evaluate(attack);
        pending.clear();
    }

    private void updateTargets() {
        double range = player.compensatedEntities.self.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) + 1;
        double margin = 0.05 + (player.getClientVersion().isOlderThan(ClientVersion.V_1_9) ? 0.1 : 0)
                + (player.canSkipTicks() ? player.getMovementThreshold() : 0);

        List<Vector3dm> looks = new ArrayList<>(3);
        looks.add(ReachUtils.getLook(player, player.yaw, player.pitch));
        looks.add(ReachUtils.getLook(player, player.lastYaw, player.pitch));
        looks.add(ReachUtils.getLook(player, player.lastYaw, player.lastPitch));
        for (Vector3dm look : looks) look.multiply(range);
        double[] eyeHeights = player.getPossibleEyeHeights();

        for (var entry : player.compensatedEntities.entityMap.int2ObjectEntrySet()) {
            PacketEntity entity = entry.getValue();
            if (entity.getType() != EntityTypes.PLAYER) continue;
            Vector3d pos = entity.trackedServerPosition.getPos();
            double dx = pos.getX() - player.x, dy = pos.getY() - player.y, dz = pos.getZ() - player.z;
            if (dx * dx + dy * dy + dz * dz > (range + 3) * (range + 3)) continue;

            SimpleCollisionBox box = entity.getPossibleCollisionBoxes();
            box.expand(margin);
            boolean onTarget = false;
            outer:
            for (double eye : eyeHeights) {
                Vector3d eyePos = new Vector3d(player.x, player.y + eye, player.z);
                if (ReachUtils.isVecInside(box, eyePos)) {
                    onTarget = true;
                    break;
                }
                for (Vector3dm look : looks) {
                    Vector3d end = eyePos.add(look.getX(), look.getY(), look.getZ());
                    if (ReachUtils.calculateIntercept(box, eyePos, end).first() != null) {
                        onTarget = true;
                        break outer;
                    }
                }
            }

            Target target = targets.computeIfAbsent(entry.getIntKey(), id -> new Target());
            if (target.lastSeen != tick - 1) {
                // Out of range or new, we don't know where the crosshair was before
                target.streakStart = -1;
                target.offTicks = 0;
            }
            target.lastSeen = tick;
            if (onTarget) {
                if (target.streakStart < 0) {
                    target.streakStart = tick;
                    target.offBeforeStreak = target.offTicks;
                }
                target.offTicks = 0;
            } else {
                target.streakStart = -1;
                target.offTicks++;
            }
        }

        targets.int2ObjectEntrySet().removeIf(e -> tick - e.getValue().lastSeen > FORGET_TICKS);
    }

    private void evaluate(PendingAttack attack) {
        if (!attack.clean()) return;
        Target target = targets.get(attack.entityId());
        if (target == null || target.streakStart < 0 || target.lastSeen != tick) return;

        int entryDelay = (int) (tick - target.streakStart);
        int delay;
        boolean entryEvent;
        if (attack.cooldownReadyAgo() == Integer.MIN_VALUE || entryDelay <= attack.cooldownReadyAgo()) {
            // The crosshair reaching the target is what made the attack possible
            if (attack.cooldownReadyAgo() != Integer.MIN_VALUE && attack.cooldownReadyAgo() < 0) return;
            if (target.offBeforeStreak < MIN_OFF_TICKS) return;
            delay = entryDelay;
            entryEvent = true;
        } else {
            // Already aiming at it, the cooldown filling up made the attack possible
            if (attack.cooldownReadyAgo() < 0) return; // attacked before the cooldown was full
            delay = attack.cooldownReadyAgo();
            entryEvent = false;
        }

        addSample(delay <= INSTANT_TICKS, entryEvent);
    }

    private void addSample(boolean instant, boolean entryEvent) {
        samples[sampleIndex] = instant;
        sampleIndex = (sampleIndex + 1) % SAMPLES;
        if (sampleCount < SAMPLES) sampleCount++;
        if (entryEvent) entrySamples++;
        else cooldownSamples++;
        if (sampleCount < SAMPLES) return;

        int instantCount = 0;
        for (boolean s : samples) if (s) instantCount++;
        if (instantCount >= REQUIRED) {
            flag(V.write(verbose()).sint(instantCount).sint(SAMPLES).sint(entrySamples).sint(cooldownSamples));
            sampleCount = 0;
            sampleIndex = 0;
            entrySamples = 0;
            cooldownSamples = 0;
        }
    }
}
