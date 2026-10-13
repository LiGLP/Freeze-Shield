package ac.grim.grimac.checks.impl.inventory;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.checks.type.PostPredictionListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow.WindowClickType;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory automation (Meteor's AutoTotem, Offhand, AutoArmor, ChestSwap, AutoReplenish and Wurst's AutoTotem)
 * moves items with two PICKUP clicks on different slots in a single tick: pick the item up, put it down.
 * A human has to click, move the mouse to another slot and click again, which doesn't fit in one tick.
 * <p>
 * Very low FPS can batch two human clicks into one tick, so we only flag once it keeps happening.
 */
@CheckData(name = "InventoryA", stableKey = "grim.inventory.multiple_pickups", description = "Moved items between multiple slots in the same tick")
public class InventoryA extends Check implements PacketReceiveListener, PostPredictionListener {
    private static final Verbose V = Verbose.of("window={sint}, first={sint}, second={sint}");
    private static final double BUFFER_LIMIT = 2;
    private static final double BUFFER_DECAY = 0.005; // per tick, one point every 10 seconds

    private final List<int[]> pending = new ArrayList<>();
    private int lastWindow = Integer.MIN_VALUE;
    private int lastSlot = Integer.MIN_VALUE;
    private double buffer;

    public InventoryA(final GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            WrapperPlayClientClickWindow packet = new WrapperPlayClientClickWindow(event);
            onClick(event, packet);
        }

        if (isTickPacket(event.getPacketType())) {
            lastWindow = Integer.MIN_VALUE;
            lastSlot = Integer.MIN_VALUE;
            buffer = Math.max(0, buffer - BUFFER_DECAY);
        }
    }

    private void onClick(PacketReceiveEvent event, WrapperPlayClientClickWindow packet) {
        // Creative inventories sync differently, and -999 is dropping the cursor outside the window
        if (player.gamemode == GameMode.CREATIVE || packet.getWindowClickType() != WindowClickType.PICKUP || packet.getSlot() < 0) {
            return;
        }

        int window = packet.getWindowId();
        int slot = packet.getSlot();
        int previousSlot = lastSlot;
        boolean suspicious = lastWindow == window && previousSlot != slot;

        lastWindow = window;
        lastSlot = slot;

        if (!suspicious) return;

        if (player.canSkipTicks()) {
            // Without tick end packets an idle tick can hide a tick boundary, confirm once the player ticks reliably
            pending.add(new int[]{window, previousSlot, slot});
            return;
        }

        if (++buffer > BUFFER_LIMIT && flag(V.write(verbose()).sint(window).sint(previousSlot).sint(slot)) && shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!player.canSkipTicks() || pending.isEmpty()) return;

        if (player.isTickingReliablyFor(3)) {
            for (int[] data : pending) {
                if (++buffer > BUFFER_LIMIT) {
                    flag(V.write(verbose()).sint(data[0]).sint(data[1]).sint(data[2]));
                }
            }
        }

        pending.clear();
    }
}
