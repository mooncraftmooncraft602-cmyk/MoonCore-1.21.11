package com.mooncore.companion;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class CompanionProtocol {

    static final byte OP_HELLO = 0x01;
    static final byte OP_WELCOME = 0x02;
    static final byte OP_PUSH_RIG = 0x10;
    static final byte OP_PUSH_ANIM = 0x11;
    static final byte OP_PLAY_ANIM = 0x12;
    static final byte OP_PUSH_ARMOR = 0x13;

    static final byte PROTOCOL_V2 = 2;

    private static final long TRANSFER_TTL_MILLIS = 30_000L;
    private static final int MAX_TOTAL_BYTES = 4 * 1024 * 1024;
    private static final Map<TransferKey, Transfer> TRANSFERS = new ConcurrentHashMap<>();

    private CompanionProtocol() {
    }

    static void handle(byte[] data) {
        if (data == null || data.length == 0) return;
        byte opcode = data[0];
        if (opcode == OP_WELCOME) {
            MoonCoreCompanionClient.connected = true;
            MoonCoreCompanionClient.protocol = data.length >= 2 ? (data[1] & 0xFF) : 1;
            MoonCoreCompanionClient.capabilities = data.length >= 3 ? (data[2] & 0xFF) : 0;
            return;
        }
        if (opcode == OP_PUSH_RIG || opcode == OP_PUSH_ANIM || opcode == OP_PLAY_ANIM || opcode == OP_PUSH_ARMOR) {
            readChunk(opcode, data);
        }
    }

    static void clear() {
        TRANSFERS.clear();
        ClientRigRegistry.clear();
    }

    static void tickCleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<TransferKey, Transfer>> it = TRANSFERS.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().createdAtMillis > TRANSFER_TTL_MILLIS) it.remove();
        }
    }

    private static void readChunk(byte opcode, byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            in.readUnsignedByte();
            int protocol = in.readUnsignedByte();
            if (protocol < PROTOCOL_V2) return;
            UUID transferId = new UUID(in.readLong(), in.readLong());
            int chunkIndex = in.readUnsignedShort();
            int chunkCount = in.readUnsignedShort();
            int totalLength = in.readInt();
            int chunkLength = in.readInt();
            if (chunkCount <= 0 || chunkIndex >= chunkCount || totalLength < 0 || totalLength > MAX_TOTAL_BYTES) return;
            if (chunkLength < 0 || chunkLength > in.available()) return;
            byte[] chunk = new byte[chunkLength];
            in.readFully(chunk);

            TransferKey key = new TransferKey(opcode, transferId);
            Transfer transfer = TRANSFERS.computeIfAbsent(key, ignored -> new Transfer(chunkCount, totalLength));
            if (transfer.chunkCount != chunkCount || transfer.totalLength != totalLength) {
                TRANSFERS.remove(key);
                return;
            }
            if (transfer.chunks[chunkIndex] == null) {
                transfer.chunks[chunkIndex] = chunk;
                transfer.received++;
                transfer.receivedBytes += chunkLength;
            }
            if (transfer.received == transfer.chunkCount) {
                TRANSFERS.remove(key);
                byte[] payload = transfer.join();
                if (payload.length == transfer.totalLength) {
                    ClientRigRegistry.accept(opcode, new String(payload, StandardCharsets.UTF_8));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private record TransferKey(byte opcode, UUID transferId) {
    }

    private static final class Transfer {
        final int chunkCount;
        final int totalLength;
        final byte[][] chunks;
        final long createdAtMillis = System.currentTimeMillis();
        int received;
        int receivedBytes;

        Transfer(int chunkCount, int totalLength) {
            this.chunkCount = chunkCount;
            this.totalLength = totalLength;
            this.chunks = new byte[chunkCount][];
        }

        byte[] join() {
            if (receivedBytes <= 0) return new byte[0];
            byte[] out = new byte[receivedBytes];
            int cursor = 0;
            for (byte[] chunk : chunks) {
                if (chunk == null) return new byte[0];
                System.arraycopy(chunk, 0, out, cursor, chunk.length);
                cursor += chunk.length;
            }
            return totalLength == out.length ? out : Arrays.copyOf(out, Math.min(out.length, totalLength));
        }
    }
}
