/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * Modified for Freeze Shield (https://github.com/LiGLP/Freeze-Shield).
 * This file is part of RaycastedAntiESP. RaycastedAntiESP is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at
 * https://www.gnu.org/licenses/agpl-3.0.html. See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Freeze Shield integration: tells the anticheat about every packet the anti-ESP changes behind its back.
 * <p>
 * The anticheat simulates each player against its own copy of what the client received. Packets written silently
 * (reveals and hides) skip its listeners, and packets cancelled or edited here were already processed by it,
 * so without this it would simulate against entities and blocks the client doesn't have.
 */
public final class GrimSync {
    // (User, encoded packet bytes including the packet id), set by Freeze Shield when it loads the anti-ESP
    private static volatile BiConsumer<Object, byte[]> sink;

    /**
     * Blocks hidden while parsing the current chunk on this thread, as {x, y, z, blockId}. The chunk parser fills it
     * and the block view controller hands them to the anticheat once the chunk is sent.
     */
    public static final ThreadLocal<List<int[]>> HIDDEN_BLOCKS = ThreadLocal.withInitial(ArrayList::new);

    private GrimSync() {}

    public static void setSink(BiConsumer<Object, byte[]> newSink) {
        sink = newSink;
    }

    /** Replacement for {@code viewer.writePacketSilently(packet)} that also shows the packet to the anticheat. */
    public static void writeSilently(User viewer, PacketWrapper<?> packet) {
        BiConsumer<Object, byte[]> currentSink = sink;
        if (currentSink == null) {
            viewer.writePacketSilently(packet);
            return;
        }

        Object channel = viewer.getChannel();
        ProtocolManager protocolManager = PacketEvents.getAPI().getProtocolManager();
        Object[] buffers = protocolManager.transformWrappers(packet, channel, true);
        // Copy before writing, netty releases the buffers once they're flushed
        byte[][] copies = new byte[buffers.length][];
        for (int i = 0; i < buffers.length; i++) {
            copies[i] = ByteBufHelper.copyBytes(buffers[i]);
        }
        protocolManager.writePacketsSilently(channel, buffers);
        for (byte[] copy : copies) {
            currentSink.accept(viewer, copy);
        }
    }

    /** Shows the anticheat a packet the client never receives, e.g. to undo a spawn that was cancelled after it saw it. */
    public static void notifyOnly(User viewer, PacketWrapper<?> packet) {
        BiConsumer<Object, byte[]> currentSink = sink;
        if (currentSink == null) return;

        packet.prepareForSend(viewer.getChannel(), true);
        try {
            currentSink.accept(viewer, ByteBufHelper.copyBytes(packet.getBuffer()));
        } finally {
            ByteBufHelper.release(packet.getBuffer());
        }
    }

    public static boolean isActive() {
        return sink != null;
    }
}
