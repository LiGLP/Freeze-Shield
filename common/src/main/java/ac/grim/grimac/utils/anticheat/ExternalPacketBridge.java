package ac.grim.grimac.utils.anticheat;

import ac.grim.grimac.GrimAPI;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.netty.buffer.UnpooledByteBufAllocationHelper;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.EventCreationUtil;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lets code outside the normal PacketEvents pipeline keep Grim in sync with what the client really received.
 * <p>
 * Grim simulates each player against its own copy of the entities and blocks it has sent them. The embedded
 * anti-ESP hides things by cancelling or editing packets after Grim already processed them, and reveals them by
 * writing packets silently, which skips every listener. Each such packet is replayed here to Grim's listeners only.
 */
public final class ExternalPacketBridge {
    private static final List<PacketListenerAbstract> LISTENERS = new CopyOnWriteArrayList<>();

    private ExternalPacketBridge() {}

    public static void trackListener(PacketListenerAbstract listener) {
        // Pre-via listeners get packets already translated to the client's version; ours are in the server's version
        if (listener.isPreVia()) return;
        LISTENERS.add(listener);
        // Same order PacketEvents calls them in, registration order is kept within a priority
        LISTENERS.sort(Comparator.comparingInt(l -> l.getPriority().ordinal()));
    }

    /**
     * Shows Grim an outgoing packet as if it was just sent. Safe to call from any thread.
     *
     * @param user   the receiving player
     * @param packet the encoded packet, packet id included, in the server's protocol version
     */
    public static void dispatchSend(User user, byte[] packet) {
        Object channel = user.getChannel();
        if (channel == null || LISTENERS.isEmpty()) return;

        // Grim only touches a player from their netty thread; this also runs after any write queued before it
        ChannelHelper.runInEventLoop(channel, () -> process(user, channel, packet));
    }

    private static void process(User user, Object channel, byte[] packet) {
        if (GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(user) == null) return;

        Object buffer = UnpooledByteBufAllocationHelper.buffer(packet.length);
        try {
            ByteBufHelper.writeBytes(buffer, packet);
            PacketSendEvent event = EventCreationUtil.createSendEvent(channel, user, null, buffer, false);
            for (PacketListenerAbstract listener : LISTENERS) {
                listener.onPacketSend(event);
            }
            if (event.hasTasksAfterSend()) {
                for (Runnable task : event.getTasksAfterSend()) {
                    task.run();
                }
            }
        } catch (Exception e) {
            LogUtil.error("Failed to sync an anti-ESP packet for " + user.getName(), e);
        } finally {
            ByteBufHelper.release(buffer);
        }
    }
}
