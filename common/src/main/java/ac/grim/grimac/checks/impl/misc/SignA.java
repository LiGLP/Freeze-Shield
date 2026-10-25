package ac.grim.grimac.checks.impl.misc;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.checks.type.PacketSendListener;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenSignEditor;

/**
 * Wrote text on a sign faster than a human could, as done by Meteor's and Wurst's AutoSign which reply to the
 * sign editor the moment it opens. Inspired by NoCheatPlus' blockplace.autosign.
 * <p>
 * The timer starts once the client acknowledges a transaction sent right after the editor, so ping doesn't count.
 * AutoSign answers while still handling the editor packet, which is before that acknowledgement arrives.
 */
@CheckData(name = "SignA", stableKey = "grim.misc.auto_sign", description = "Wrote a sign faster than humanly possible")
public class SignA extends Check implements PacketSendListener, PacketReceiveListener {
    private static final Verbose V = Verbose.of("time={f64}ms, characters={uint}");
    // Below human reaction time even for closing the editor right away
    private static final long MIN_HUMAN_NANOS = 150_000_000L;

    private Vector3i editorPosition;
    private long editorOpenedAt;

    public SignA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.OPEN_SIGN_EDITOR) return;

        Vector3i position = new WrapperPlayServerOpenSignEditor(event).getPosition();
        // The transaction has to follow the editor, sending it from here would put it in front
        event.getTasksAfterSend().add(() -> {
            player.sendTransaction();
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                if (position.equals(editorPosition)) { // Not answered yet
                    editorOpenedAt = System.nanoTime();
                }
            });
        });

        // Until the client acknowledges the editor, any reply for this sign is instant
        editorPosition = position;
        editorOpenedAt = Long.MAX_VALUE;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.UPDATE_SIGN || event.isCancelled() || editorPosition == null) return;

        WrapperPlayClientUpdateSign packet = new WrapperPlayClientUpdateSign(event);
        if (!packet.getBlockPosition().equals(editorPosition)) return;

        long elapsed = editorOpenedAt == Long.MAX_VALUE ? 0 : System.nanoTime() - editorOpenedAt;
        editorPosition = null;

        int characters = 0;
        for (String line : packet.getTextLines()) {
            if (line != null) characters += line.length();
        }

        if (characters == 0 || elapsed >= MIN_HUMAN_NANOS) return;

        if (flag(V.write(verbose()).f64(elapsed / 1_000_000.0).uint(characters)) && shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }
}
