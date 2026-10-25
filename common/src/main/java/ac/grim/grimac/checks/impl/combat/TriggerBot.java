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
import java.util.Arrays;
import java.util.List;

/**
 * TriggerBot (Meteor's and Wurst's TriggerBot) attacks the moment an attack becomes possible: the tick the
 * crosshair lands on a player, or (1.9+) the tick the attack cooldown is full while already aiming at one.
 * Humans need 150+ ms (3+ ticks) to react to either, unless they spam click, which leaves missed swings.
 * <p>
 * Every tick we raytrace the player's look against the players around them (with Grim's position uncertainty,
 * so the crosshair counts as "on" a target as early as it possibly could be). For attacks without missed swings
 * before them, we measure the ticks from "attack possible" to the attack. Flags when nearly all of the last
 * attacks came within a tick, and several of them were reactions to the crosshair reaching a target (a very
 * consistent human can time the attack cooldown well, but nobody reacts to the crosshair within a tick).
 * <p>
 * The attack cooldown is tracked here instead of using {@link ac.grim.grimac.manager.AttackCooldownHandler}:
 * that one is already reset by the attack itself before checks run, and it also resets on swings that don't
 * reset the client's cooldown. Any swing we can't explain makes the cooldown unknown until the next attack.
 */
@CheckData(name = "TriggerBot", stableKey = "grim.combat.triggerbot", description = "Attacked the instant an attack was possible", experimental = true)
public class TriggerBot extends Check implements PacketReceiveListener {
    private static final Verbose V = Verbose.of("instant={sint}, samples={sint}, entry={sint}, cooldown={sint}");
    private static final int SAMPLES = 20;
    private static final int REQUIRED = 19;
    private static final int MIN_INSTANT_ENTRY = 5; // instant reactions to the crosshair, which humans can't do
    private static final int INSTANT_ENTRY_TICKS = 1;
    private static final int MIN_OFF_TICKS = 6; // the crosshair must have been off the target this long before
    private static final int SPAM_TICKS = 10; // a missed swing this recently means the player is spam clicking
    private static final int FORGET_TICKS = 20;
    private static final int UNKNOWN = Integer.MIN_VALUE;

    private static final byte ENTRY_INSTANT = 1, ENTRY_SLOW = 2, COOLDOWN_INSTANT = 3, COOLDOWN_SLOW = 4;

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
    private final byte[] samples = new byte[SAMPLES];
    private int sampleIndex;
    private int sampleCount;

    private final Vector3d[] eyePositions = new Vector3d[8];
    private final Vector3d[] lookEnds = new Vector3d[8 * 3];

    private long tick;
    private long lastMissedSwingTick = Long.MIN_VALUE / 2;
    private boolean swingThisTick;
    private boolean attackThisTick;
    // Client attack strength ticker, as of the next attack
    private int ticksSinceStrengthReset;
    private boolean cooldownKnown;

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
        boolean firstThisTick = !attackThisTick;
        // 1.9+ swings after the attack, so a swing before it in the same tick was a missed click (cooldown reset)
        boolean swungBefore = swingThisTick && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9);
        attackThisTick = true;
        if (player.disableGrim || player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return;
        if (pending.size() > 10) return;

        boolean clean = tick - lastMissedSwingTick > SPAM_TICKS;
        int cooldownReadyAgo = UNKNOWN; // no attack cooldown before 1.9
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9)) {
            float delay = (float) (1.0 / player.compensatedEntities.self.getAttributeValue(Attributes.ATTACK_SPEED) * 20.0);
            int fullAfter = (int) Math.ceil(delay - 0.5F);
            if (!firstThisTick || swungBefore) {
                cooldownReadyAgo = -Math.max(1, fullAfter); // the cooldown was just reset, this attack is early
            } else if (cooldownKnown) {
                cooldownReadyAgo = ticksSinceStrengthReset - fullAfter;
            }
        }
        pending.add(new PendingAttack(entityId, clean, cooldownReadyAgo));
    }

    private void onTick() {
        tick++;
        boolean reliable = player.isTickingReliablyFor(3) && player.cameraEntity.isSelf();

        if (attackThisTick) {
            ticksSinceStrengthReset = 1;
            cooldownKnown = reliable;
        } else {
            if (ticksSinceStrengthReset < 1_000_000) ticksSinceStrengthReset++;
            if (swingThisTick) {
                // A missed click resets the cooldown, a dig or use swing doesn't; we can't always tell them apart
                lastMissedSwingTick = tick;
                cooldownKnown = false;
            }
            if (!reliable) cooldownKnown = false;
        }
        swingThisTick = false;
        attackThisTick = false;

        if (!reliable) {
            // A skipped tick would make reactions look faster than they were
            targets.clear();
            pending.clear();
            return;
        }

        updateTargets();
        if (!pending.isEmpty()) {
            for (PendingAttack attack : pending) evaluate(attack);
            pending.clear();
        }
    }

    private void updateTargets() {
        double range = player.compensatedEntities.self.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) + 1;
        double margin = 0.05 + (player.getClientVersion().isOlderThan(ClientVersion.V_1_9) ? 0.1 : 0)
                + (player.canSkipTicks() ? player.getMovementThreshold() : 0);
        double maxDistSq = (range + 3) * (range + 3);

        boolean anyCandidate = false;
        for (PacketEntity entity : player.compensatedEntities.entityMap.values()) {
            if (entity.getType() != EntityTypes.PLAYER) continue;
            Vector3d pos = entity.trackedServerPosition.getPos();
            double dx = pos.getX() - player.x, dy = pos.getY() - player.y, dz = pos.getZ() - player.z;
            if (dx * dx + dy * dy + dz * dz <= maxDistSq) {
                anyCandidate = true;
                break;
            }
        }

        if (anyCandidate) {
            // Build the rays once per tick, not once per entity
            Vector3dm look0 = ReachUtils.getLook(player, player.yaw, player.pitch).multiply(range);
            Vector3dm look1 = ReachUtils.getLook(player, player.lastYaw, player.pitch).multiply(range);
            Vector3dm look2 = ReachUtils.getLook(player, player.lastYaw, player.lastPitch).multiply(range);
            double[] eyeHeights = player.getPossibleEyeHeights();
            int eyes = Math.min(eyeHeights.length, eyePositions.length);
            for (int i = 0; i < eyes; i++) {
                Vector3d eyePos = new Vector3d(player.x, player.y + eyeHeights[i], player.z);
                eyePositions[i] = eyePos;
                lookEnds[i * 3] = eyePos.add(look0.getX(), look0.getY(), look0.getZ());
                lookEnds[i * 3 + 1] = eyePos.add(look1.getX(), look1.getY(), look1.getZ());
                lookEnds[i * 3 + 2] = eyePos.add(look2.getX(), look2.getY(), look2.getZ());
            }

            for (var entry : player.compensatedEntities.entityMap.int2ObjectEntrySet()) {
                PacketEntity entity = entry.getValue();
                if (entity.getType() != EntityTypes.PLAYER) continue;
                Vector3d pos = entity.trackedServerPosition.getPos();
                double dx = pos.getX() - player.x, dy = pos.getY() - player.y, dz = pos.getZ() - player.z;
                if (dx * dx + dy * dy + dz * dz > maxDistSq) continue;

                SimpleCollisionBox box = entity.getPossibleCollisionBoxes();
                box.expand(margin);
                boolean onTarget = false;
                outer:
                for (int i = 0; i < eyes; i++) {
                    Vector3d eyePos = eyePositions[i];
                    if (ReachUtils.isVecInside(box, eyePos)) {
                        onTarget = true;
                        break;
                    }
                    for (int j = 0; j < 3; j++) {
                        if (ReachUtils.calculateIntercept(box, eyePos, lookEnds[i * 3 + j]).first() != null) {
                            onTarget = true;
                            break outer;
                        }
                    }
                }

                Target target = targets.get(entry.getIntKey());
                if (target == null) {
                    target = new Target();
                    targets.put(entry.getIntKey(), target);
                }
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
        }

        if (!targets.isEmpty()) {
            targets.int2ObjectEntrySet().removeIf(e -> tick - e.getValue().lastSeen > FORGET_TICKS);
        }
    }

    private void evaluate(PendingAttack attack) {
        if (!attack.clean()) return;
        Target target = targets.get(attack.entityId());
        if (target == null || target.streakStart < 0 || target.lastSeen != tick) return;

        int entryDelay = (int) (tick - target.streakStart);
        int readyAgo = attack.cooldownReadyAgo();
        if (readyAgo == UNKNOWN || readyAgo < 0 || entryDelay <= readyAgo) {
            // The cooldown didn't hold the attack back, the crosshair reaching the target made it possible
            if (entryDelay > INSTANT_ENTRY_TICKS) {
                addSample(ENTRY_SLOW);
            } else if (target.offBeforeStreak >= MIN_OFF_TICKS) {
                addSample(ENTRY_INSTANT);
            }
        } else {
            // Already aiming at it, the cooldown filling up made the attack possible.
            // Only the very first tick counts, a steady human's rhythm can land within two ticks.
            addSample(readyAgo == 0 ? COOLDOWN_INSTANT : COOLDOWN_SLOW);
        }
    }

    private void addSample(byte sample) {
        samples[sampleIndex] = sample;
        sampleIndex = (sampleIndex + 1) % SAMPLES;
        if (sampleCount < SAMPLES) sampleCount++;
        if (sample == ENTRY_SLOW || sample == COOLDOWN_SLOW) reward();
        if (sampleCount < SAMPLES) return;

        int instant = 0, instantEntry = 0, entry = 0, cooldown = 0;
        for (byte s : samples) {
            switch (s) {
                case ENTRY_INSTANT -> {
                    instant++;
                    instantEntry++;
                    entry++;
                }
                case ENTRY_SLOW -> entry++;
                case COOLDOWN_INSTANT -> {
                    instant++;
                    cooldown++;
                }
                case COOLDOWN_SLOW -> cooldown++;
                default -> {
                }
            }
        }

        if (instant >= REQUIRED && instantEntry >= MIN_INSTANT_ENTRY) {
            flag(V.write(verbose()).sint(instant).sint(SAMPLES).sint(entry).sint(cooldown));
            sampleCount = 0;
            sampleIndex = 0;
            Arrays.fill(samples, (byte) 0);
        }
    }
}
