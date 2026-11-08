package com.gtnhblueprints.wireless;

import java.io.IOException;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.network.PacketBuffer;
import com.cleanroommc.modularui.value.sync.SyncHandler;

/** One fixed handler for all channel rows; rebuilding the list never changes MUI's handler registry. */
final class WirelessMEChannelActions extends SyncHandler<WirelessMEChannelActions> {
    static final int JOIN = 0;
    static final int FAVORITE = 1;
    static final int DELETE = 2;
    private final BiConsumer<Integer, String> serverAction;

    WirelessMEChannelActions(BiConsumer<Integer, String> serverAction) {
        this.serverAction = serverAction;
        allowC2S();
    }

    void send(int action, String channelId) {
        syncToServer(0, packet -> {
            packet.writeByte(action);
            packet.writeStringToBuffer(channelId);
        });
    }

    @Override public void readOnClient(int id, PacketBuffer packet) {}

    @Override public void readOnServer(int id, PacketBuffer packet) throws IOException {
        if (id != 0) return;
        int action = packet.readUnsignedByte();
        String channelId = packet.readStringFromBuffer(36);
        if (action < JOIN || action > DELETE) return;
        try {
            if (!UUID.fromString(channelId).toString().equals(channelId)) return;
        } catch (IllegalArgumentException invalidId) { return; }
        serverAction.accept(action, channelId);
    }
}
