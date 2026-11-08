package com.gtnhblueprints.wireless;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.PacketBuffer;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class WirelessMEChannelActionsTest {
    @Test void packetsCarryStableChannelIdsAcrossPageChangesAndOnlyExecuteOnServer() throws Exception {
        List<String> calls = new ArrayList<>();
        WirelessMEChannelActions handler = new WirelessMEChannelActions((action, id) -> calls.add(action + ":" + id));
        assertTrue(handler.isAllowC2S());
        for (int action = 0; action < 3; action++) {
            String id = UUID.randomUUID().toString();
            PacketBuffer packet = packet(action, id);
            try {
                handler.readOnClient(0, packet);
                assertEquals(action, calls.size());
                handler.readOnServer(0, packet);
                assertEquals((action + 1), calls.size());
                assertEquals(action + ":" + id, calls.get(action));
            } finally { packet.release(); }
        }
    }

    @Test void invalidActionsAndInvalidIdsDoNotReachChannelOperations() throws Exception {
        WirelessMEChannelActions handler = new WirelessMEChannelActions((action, id) -> fail("Invalid packet was accepted"));
        for (String id : new String[] { "", "bad-id", "0-0-0-0-0", UUID.randomUUID().toString() }) {
            PacketBuffer packet = packet(id.length() == 36 ? 255 : 0, id);
            try { handler.readOnServer(0, packet); }
            finally { packet.release(); }
        }
        PacketBuffer packet = packet(0, UUID.randomUUID().toString());
        try { handler.readOnServer(99, packet); }
        finally { packet.release(); }
    }

    private PacketBuffer packet(int action, String id) throws Exception {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        packet.writeByte(action);
        packet.writeStringToBuffer(id);
        return packet;
    }
}
