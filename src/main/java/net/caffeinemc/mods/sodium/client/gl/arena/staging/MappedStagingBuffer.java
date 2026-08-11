package net.caffeinemc.mods.sodium.client.gl.arena.staging;

import net.caffeinemc.mods.sodium.client.gl.buffer.GlBuffer;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBufferMapping;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlImmutableBuffer;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBufferStorageFlags;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBufferMapFlags;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.gl.functions.BufferStorageFunctions;
import net.caffeinemc.mods.sodium.client.gl.sync.GlFence;
import net.caffeinemc.mods.sodium.client.gl.util.EnumBitField;
import net.caffeinemc.mods.sodium.client.util.MathUtil;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class MappedStagingBuffer implements StagingBuffer {
    private static final float UPLOAD_LIMIT_MARGIN = 0.8f;

    private static final EnumBitField<GlBufferStorageFlags> STORAGE_FLAGS =
            EnumBitField.of(GlBufferStorageFlags.PERSISTENT, GlBufferStorageFlags.CLIENT_STORAGE, GlBufferStorageFlags.MAP_WRITE);

    private static final EnumBitField<GlBufferMapFlags> MAP_FLAGS =
            EnumBitField.of(GlBufferMapFlags.PERSISTENT, GlBufferMapFlags.INVALIDATE_BUFFER, GlBufferMapFlags.WRITE, GlBufferMapFlags.EXPLICIT_FLUSH);

    private final FallbackStagingBuffer fallbackStagingBuffer;

    private final MappedBuffer mappedBuffer;
    private final ArrayDeque<CopyCommand> pendingCopies = new ArrayDeque<>();
    private final ArrayDeque<FencedMemoryRegion> fencedRegions = new ArrayDeque<>();

    private int start = 0;
    private int pos = 0;

    private final int capacity;
    private int remaining;

    public MappedStagingBuffer(CommandList commandList) {
        this(commandList, 1024 * 1024 * 16 /* 16 MB */);
    }

    public MappedStagingBuffer(CommandList commandList, int capacity) {
        GlImmutableBuffer buffer = commandList.createImmutableBuffer(capacity, STORAGE_FLAGS);
        GlBufferMapping map = commandList.mapBuffer(buffer, 0, capacity, MAP_FLAGS);

        this.mappedBuffer = new MappedBuffer(buffer, map);
        this.fallbackStagingBuffer = new FallbackStagingBuffer(commandList);
        this.capacity = capacity;
        this.remaining = this.capacity;
    }

    public static boolean isSupported(RenderDevice instance) {
        return instance.getDeviceFunctions().getBufferStorageFunctions() != BufferStorageFunctions.NONE;
    }

    @Override
    public void enqueueCopy(CommandList commandList, ByteBuffer data, GlBuffer dst, long writeOffset) {
        int length = data.remaining();

        if (length > this.remaining) {
            this.fallbackStagingBuffer.enqueueCopy(commandList, data, dst, writeOffset);

            return;
        }

        int remaining = this.capacity - this.pos;

        // Split the transfer in two if we have enough available memory at the end and start of the buffer
        if (length > remaining) {
            int split = length - remaining;

            this.addTransfer(slice(data, 0, remaining), dst, this.pos, writeOffset);
            this.addTransfer(slice(data, remaining, split), dst, 0, writeOffset + remaining);

            this.pos = split;
        } else {
            this.addTransfer(data, dst, this.pos, writeOffset);
            this.pos += length;
        }

        this.remaining -= length;
    }

    // LWJGL2/Java8 has no ByteBuffer.slice(int, int); slice a positioned duplicate instead.
    private static ByteBuffer slice(ByteBuffer data, int offset, int length) {
        ByteBuffer slice = data.duplicate();
        slice.position(offset);
        slice.limit(offset + length);
        return slice.slice();
    }

    private void addTransfer(ByteBuffer data, GlBuffer dst, long readOffset, long writeOffset) {
        this.mappedBuffer.map.write(data, (int) readOffset);
        this.pendingCopies.addLast(new CopyCommand(dst, readOffset, writeOffset, data.remaining()));
    }

    @Override
    public void flush(CommandList commandList) {
        if (this.pendingCopies.isEmpty()) {
            return;
        }

        if (this.pos < this.start) {
            commandList.flushMappedRange(this.mappedBuffer.map, this.start, this.capacity - this.start);
            commandList.flushMappedRange(this.mappedBuffer.map, 0, this.pos);
        } else {
            commandList.flushMappedRange(this.mappedBuffer.map, this.start, this.pos - this.start);
        }

        int bytes = 0;

        for (CopyCommand command : consolidateCopies(this.pendingCopies)) {
            bytes += command.bytes;

            commandList.copyBufferSubData(this.mappedBuffer.buffer, command.buffer, command.readOffset, command.writeOffset, command.bytes);
        }

        this.fencedRegions.addLast(new FencedMemoryRegion(commandList.createFence(), bytes));

        this.start = this.pos;
    }

    private static List<CopyCommand> consolidateCopies(ArrayDeque<CopyCommand> queue) {
        List<CopyCommand> merged = new ArrayList<>();
        CopyCommand last = null;

        while (!queue.isEmpty()) {
            CopyCommand command = queue.removeFirst();

            if (last != null) {
                if (last.buffer == command.buffer &&
                        last.writeOffset + last.bytes == command.writeOffset &&
                        last.readOffset + last.bytes == command.readOffset) {
                    last.bytes += command.bytes;
                    continue;
                }
            }

            merged.add(last = new CopyCommand(command));
        }

        return merged;
    }

    @Override
    public void delete(CommandList commandList) {
        while (!this.fencedRegions.isEmpty()) {
            FencedMemoryRegion region = this.fencedRegions.removeFirst();
            GlFence fence = region.fence;
            fence.sync();
            fence.delete();
        }

        this.mappedBuffer.delete(commandList);
        this.fallbackStagingBuffer.delete(commandList);
        this.pendingCopies.clear();
    }

    @Override
    public void flip() {
        while (!this.fencedRegions.isEmpty()) {
            FencedMemoryRegion region = this.fencedRegions.peekFirst();
            GlFence fence = region.fence;

            if (!fence.isCompleted()) {
                break;
            }

            fence.delete();

            this.fencedRegions.removeFirst();
            this.remaining += region.length;
        }
    }

    @Override
    public long getUploadSizeLimit(long frameDuration) {
        return (long) (this.capacity * UPLOAD_LIMIT_MARGIN);
    }

    private static final class CopyCommand {
        private final GlBuffer buffer;
        private final long readOffset;
        private final long writeOffset;

        private long bytes;

        private CopyCommand(GlBuffer buffer, long readOffset, long writeOffset, long bytes) {
            this.buffer = buffer;
            this.readOffset = readOffset;
            this.writeOffset = writeOffset;
            this.bytes = bytes;
        }

        public CopyCommand(CopyCommand command) {
            this.buffer = command.buffer;
            this.writeOffset = command.writeOffset;
            this.readOffset = command.readOffset;
            this.bytes = command.bytes;
        }
    }

    private static final class MappedBuffer {
        private final GlImmutableBuffer buffer;
        private final GlBufferMapping map;

        private MappedBuffer(GlImmutableBuffer buffer, GlBufferMapping map) {
            this.buffer = buffer;
            this.map = map;
        }

        public void delete(CommandList commandList) {
            commandList.unmap(this.map);
            commandList.deleteBuffer(this.buffer);
        }
    }

    private static final class FencedMemoryRegion {
        private final GlFence fence;
        private final int length;

        private FencedMemoryRegion(GlFence fence, int length) {
            this.fence = fence;
            this.length = length;
        }
    }

    @Override
    public String toString() {
        return String.format("Mapped (%s/%s MiB)", MathUtil.toMib(this.remaining), MathUtil.toMib(this.capacity));
    }
}
