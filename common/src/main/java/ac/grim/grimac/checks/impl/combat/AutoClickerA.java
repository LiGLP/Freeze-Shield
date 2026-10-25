package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAnimation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Clicking faster than the configured limit for several seconds (AutoClicker, Meteor's and Wurst's
 * AutoClicker/KillAura with a high CPS).
 * <p>
 * The client sends an arm swing for every left click, but also for other things that aren't clicks:
 * holding left click on a block swings every tick, right clicks and dropping items swing too.
 * Those swings are filtered out here; what's left are real left clicks, which {@link AutoClickerB}
 * also checks for robotic timing.
 * <p>
 * Clicks are counted per client tick, so lag and packets arriving in bursts don't change the count.
 * Ticks are only counted while the client ticks reliably (always on 1.8 and 1.21.2+, while moving on 1.9-1.21.1).
 */
@CheckData(name = "AutoClickerA", stableKey = "grim.autoclicker.cps", description = "Clicked faster than possible for a human")
public class AutoClickerA extends Check implements PacketReceiveListener {
    private static final Verbose V = Verbose.of("cps={sint}, limit={sint}");
    private static final int WINDOW = 20; // ticks = one second
    // After a dig packet the client keeps swinging while it waits to break the next block (5 tick destroy delay)
    private static final int DIG_COOLDOWN_TICKS = 7;

    private int maxCps;
    private int sustainTicks;

    private final int[] clicksPerTick = new int[WINDOW];
    private int windowIndex;
    private int windowFilled;
    private int windowSum;

    private int clicksThisTick;
    private boolean rightClickThisTick;
    private boolean digging;
    private int digCooldown;
    private int overLimitTicks;
    private int cancelAttacksTicks;
    private int ticksUnderLimit;
    private AutoClickerB autoClickerB; // CheckManager isn't built yet in the constructor

    public AutoClickerA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxCps = Math.max(10, config.getIntElse("autoclicker.max-cps", 22));
        sustainTicks = Math.max(1, config.getIntElse("autoclicker.sustain-seconds", 3)) * 20;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        var type = event.getPacketType();

        if (type == PacketType.Play.Client.ANIMATION) {
            onSwing(new WrapperPlayClientAnimation(event).getHand());
        } else if (type == PacketType.Play.Client.ATTACK) {
            onAttack(event); // 1.21.11+ servers have their own attack packet
        } else if (type == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                onAttack(event);
            } else {
                rightClickThisTick = true;
            }
        } else if (type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT || type == PacketType.Play.Client.USE_ITEM) {
            rightClickThisTick = true;
        } else if (type == PacketType.Play.Client.PLAYER_DIGGING) {
            onDig(new WrapperPlayClientPlayerDigging(event).getAction());
        }

        if (isTickPacket(type)) {
            onTick();
        }
    }

    private void onAttack(PacketReceiveEvent event) {
        // A vanilla client aborts digging before it can hit something; a fake dig must not hide clicks
        digging = false;
        if (cancelAttacksTicks > 0 && shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }

    private void onSwing(InteractionHand hand) {
        // Left clicks always swing the main hand. Right clicks swing after their packet in the same tick.
        if (hand != InteractionHand.MAIN_HAND || rightClickThisTick || digging || digCooldown > 0) return;
        if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return;
        clicksThisTick++;
    }

    private void onDig(DiggingAction action) {
        switch (action) {
            case START_DIGGING -> {
                digging = true;
                digCooldown = DIG_COOLDOWN_TICKS;
            }
            case FINISHED_DIGGING, CANCELLED_DIGGING -> {
                digging = false;
                digCooldown = DIG_COOLDOWN_TICKS;
            }
            // Dropping items (Q) swings the arm
            case DROP_ITEM, DROP_ITEM_STACK -> rightClickThisTick = true;
            default -> {}
        }
    }

    private void onTick() {
        int clicks = clicksThisTick;
        clicksThisTick = 0;
        rightClickThisTick = false;
        if (digCooldown > 0) digCooldown--;
        if (cancelAttacksTicks > 0) cancelAttacksTicks--;

        if (!player.isTickingReliablyFor(3)) {
            // A skipped tick would squeeze more clicks into the window, start over
            resetWindow();
            autoClickerB().reset();
            return;
        }

        autoClickerB().onTick(clicks);

        windowSum += clicks - clicksPerTick[windowIndex];
        clicksPerTick[windowIndex] = clicks;
        windowIndex = (windowIndex + 1) % WINDOW;
        if (windowFilled < WINDOW) {
            windowFilled++;
            return;
        }

        if (windowSum > maxCps) {
            if (++overLimitTicks >= sustainTicks) {
                overLimitTicks = 0;
                if (flag(V.write(verbose()).sint(windowSum).sint(maxCps))) {
                    cancelAttacksTicks = 40;
                }
            }
        } else {
            overLimitTicks = Math.max(0, overLimitTicks - 2);
            if (++ticksUnderLimit >= 20) {
                ticksUnderLimit = 0;
                reward();
            }
        }
    }

    private AutoClickerB autoClickerB() {
        if (autoClickerB == null) autoClickerB = player.checkManager.get(AutoClickerB.class);
        return autoClickerB;
    }

    private void resetWindow() {
        Arrays.fill(clicksPerTick, 0);
        windowIndex = 0;
        windowFilled = 0;
        windowSum = 0;
        overLimitTicks = 0;
    }
}
