package com.gtnhblueprints.network;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.gtnhblueprints.BlueprintConfig;

/** Reassembles bounded blueprint chunks and rejects inconsistent or stale transfers. */
final class BlueprintTransferAssembler {

    private static final long TIMEOUT_MS = 60_000L;
    private static final int MAX_PENDING_TRANSFERS = 128;
    private static final Map<Key, Pending> PENDING = new ConcurrentHashMap<>();

    private BlueprintTransferAssembler() {}

    static byte[] accept(String owner, BlueprintChunkMessage message) {
        validate(message);
        cleanupExpired();
        Key key = new Key(owner, message.transferId, message.kind);
        Pending candidate = new Pending(message);
        Pending pending = PENDING.putIfAbsent(key, candidate);
        if (pending == null) pending = candidate;
        if (PENDING.size() > MAX_PENDING_TRANSFERS) {
            PENDING.remove(key, pending);
            throw new IllegalArgumentException("Too many pending blueprint transfers");
        }

        synchronized (pending) {
            if (!pending.matches(message)) {
                PENDING.remove(key, pending);
                throw new IllegalArgumentException("Blueprint chunk metadata changed during transfer");
            }
            if (pending.chunks[message.chunkIndex] != null) {
                if (Arrays.equals(pending.chunks[message.chunkIndex], message.chunk)) return null;
                PENDING.remove(key, pending);
                throw new IllegalArgumentException("Conflicting duplicate blueprint chunk");
            }
            pending.chunks[message.chunkIndex] = message.chunk;
            pending.receivedChunks++;
            pending.receivedBytes += message.chunk.length;
            if (pending.receivedChunks != pending.chunks.length) return null;
            if (pending.receivedBytes != pending.totalSize) {
                PENDING.remove(key, pending);
                throw new IllegalArgumentException("Blueprint chunks do not match declared size");
            }

            byte[] result = new byte[pending.totalSize];
            int offset = 0;
            for (byte[] chunk : pending.chunks) {
                System.arraycopy(chunk, 0, result, offset, chunk.length);
                offset += chunk.length;
            }
            PENDING.remove(key, pending);
            return result;
        }
    }

    private static void validate(BlueprintChunkMessage message) {
        if (message.name == null || message.name.length() > 101 || message.totalSize < 1
            || message.totalSize > BlueprintConfig.maxTransferBytes) {
            throw new IllegalArgumentException("Invalid blueprint transfer metadata");
        }
        int expectedCount = (message.totalSize + BlueprintChunkMessage.CHUNK_BYTES - 1)
            / BlueprintChunkMessage.CHUNK_BYTES;
        if (message.chunkCount != expectedCount || message.chunkIndex < 0 || message.chunkIndex >= expectedCount
            || message.chunk == null) {
            throw new IllegalArgumentException("Invalid blueprint chunk index");
        }
        int expectedSize = Math.min(
            BlueprintChunkMessage.CHUNK_BYTES,
            message.totalSize - message.chunkIndex * BlueprintChunkMessage.CHUNK_BYTES);
        if (message.chunk.length != expectedSize) throw new IllegalArgumentException("Invalid blueprint chunk size");
    }

    private static void cleanupExpired() {
        long cutoff = System.currentTimeMillis() - TIMEOUT_MS;
        for (Map.Entry<Key, Pending> entry : PENDING.entrySet()) {
            if (entry.getValue().createdAt < cutoff) PENDING.remove(entry.getKey(), entry.getValue());
        }
    }

    private static final class Pending {

        final String name;
        final int totalSize;
        final byte[][] chunks;
        final long createdAt = System.currentTimeMillis();
        int receivedChunks;
        int receivedBytes;

        Pending(BlueprintChunkMessage message) {
            name = message.name;
            totalSize = message.totalSize;
            chunks = new byte[message.chunkCount][];
        }

        boolean matches(BlueprintChunkMessage message) {
            return name.equals(message.name) && totalSize == message.totalSize && chunks.length == message.chunkCount;
        }
    }

    private static final class Key {

        final String owner;
        final int transferId;
        final byte kind;

        Key(String owner, int transferId, byte kind) {
            this.owner = owner;
            this.transferId = transferId;
            this.kind = kind;
        }

        @Override
        public int hashCode() {
            int result = owner.hashCode();
            result = 31 * result + transferId;
            return 31 * result + kind;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key)) return false;
            Key that = (Key) other;
            return transferId == that.transferId && kind == that.kind && owner.equals(that.owner);
        }
    }
}
