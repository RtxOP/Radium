package net.caffeinemc.mods.sodium.client.gl.buffer;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.arena.staging.MappedStagingBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.gl.util.EnumBitField;
import org.lwjgl.opengl.ARBShaderImageLoadStore;
import org.lwjgl.opengl.GL44;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class GlBufferStreamer {
    private final GlBuffer buffer;
    private final GlBufferMapping mapping;
    private final ByteBuffer writeBuffer;

    private final int stride;
    private final long bufferSize;
    private boolean requiresFlush;

    public GlBufferStreamer(CommandList commands, int initialCapacity, int stride) {
        this.bufferSize = (long) initialCapacity * stride;
        this.stride = stride;

        if (SodiumClientMod.options().advanced.useAdvancedStagingBuffers && MappedStagingBuffer.isSupported(RenderDevice.INSTANCE)) {
            this.buffer = commands.createImmutableBuffer(bufferSize, EnumBitField.of(GlBufferStorageFlags.PERSISTENT, GlBufferStorageFlags.MAP_WRITE));

            this.mapping = commands.mapBuffer(this.buffer, 0, bufferSize,
                    EnumBitField.of(GlBufferMapFlags.PERSISTENT, GlBufferMapFlags.WRITE, GlBufferMapFlags.EXPLICIT_FLUSH));
            this.writeBuffer = this.mapping.getMemoryBuffer();
        } else {
            this.buffer = commands.createMutableBuffer();
            commands.allocateStorage((GlMutableBuffer) this.buffer, bufferSize, GlBufferUsage.STREAM_DRAW);

            this.mapping = null;
            this.writeBuffer = ByteBuffer.allocateDirect((int) this.bufferSize).order(ByteOrder.nativeOrder());
        }

        gg.sona.radium.diag.Diag.once("streamer-path", "GlBufferStreamer path=" + (this.mapping != null ? "PERSISTENT_MAPPED" : "MUTABLE_UPLOAD")
                + " size=" + this.bufferSize);

        // Zero-initialize the staging region. Without this, the reference observed random chunks with no fade.
        // TODO: Check if this is still needed after the mesh check improvements
        for (int i = 0; i < this.bufferSize; i++) {
            this.writeBuffer.put(i, (byte) 0);
        }
    }

    public void writeData(int index, int value) { // right now we only need int values... this could probably become more generic (if we ever need this again?)
        int offset = index * stride;

        if (offset + stride > bufferSize) {
            throw new IndexOutOfBoundsException("Attempted to write beyond the end of the buffer streamer");
        }

        this.writeBuffer.putInt(offset, value);
        this.requiresFlush = true;
    }

    public GlBuffer prepare(CommandList commandList) { // either flushes or uploads data. This could be replaced with a batching system, but I don't see the point with the tiny buffer we currently use it for.
        if (requiresFlush) {
            requiresFlush = false;
            if (this.mapping != null) {
                commandList.flushMappedRange(mapping, 0, (int) bufferSize);
                ARBShaderImageLoadStore.glMemoryBarrier(GL44.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT); // TODO: I don't know yet if this is required.
            } else {
                commandList.uploadDataToOffset((GlMutableBuffer) buffer, 0, writeBuffer, (int) bufferSize);
            }
        }

        return buffer;
    }

    public void delete(CommandList commandList) {
        if (this.mapping != null) {
            commandList.unmap(this.mapping);
        }

        commandList.deleteBuffer(this.buffer);
    }
}
