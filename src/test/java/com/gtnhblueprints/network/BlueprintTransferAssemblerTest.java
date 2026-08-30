package com.gtnhblueprints.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

class BlueprintTransferAssemblerTest {

    @Test
    void reassemblesLargePayloadOutOfOrderAndKeepsEveryPacketBelow32KiB() {
        byte[] source = new byte[BlueprintChunkMessage.CHUNK_BYTES * 2 + 731];
        for (int i = 0; i < source.length; i++) source[i] = (byte) (i * 31);
        int count = 3;
        BlueprintUploadChunkMessage[] messages = new BlueprintUploadChunkMessage[count];
        for (int index = 0; index < count; index++) {
            int start = index * BlueprintChunkMessage.CHUNK_BYTES;
            int end = Math.min(source.length, start + BlueprintChunkMessage.CHUNK_BYTES);
            messages[index] = new BlueprintUploadChunkMessage(
                991,
                "oc-test",
                source.length,
                count,
                index,
                Arrays.copyOfRange(source, start, end));
            ByteBuf encoded = Unpooled.buffer();
            messages[index].toBytes(encoded);
            assertTrue(encoded.readableBytes() < 32 * 1024);
        }

        assertNull(BlueprintTransferAssembler.accept("test-player", messages[2]));
        assertNull(BlueprintTransferAssembler.accept("test-player", messages[0]));
        assertArrayEquals(source, BlueprintTransferAssembler.accept("test-player", messages[1]));
    }
}
