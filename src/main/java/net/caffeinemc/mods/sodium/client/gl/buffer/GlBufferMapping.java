package net.caffeinemc.mods.sodium.client.gl.buffer;

import java.nio.ByteBuffer;

public class GlBufferMapping {
    private final GlBuffer buffer;
    private final ByteBuffer map;

    protected boolean disposed;

    public GlBufferMapping(GlBuffer buffer, ByteBuffer map) {
        this.buffer = buffer;
        this.map = map;
    }

    public void write(ByteBuffer data, int writeOffset) {
        // LWJGL2 has no MemoryUtil.memCopy; a relative put from a positioned
        // duplicate copies exactly data.remaining() bytes to the write offset.
        ByteBuffer dst = this.map.duplicate();
        dst.position(writeOffset);
        dst.put(data);
    }

    public GlBuffer getBufferObject() {
        return this.buffer;
    }

    public void dispose() {
        this.disposed = true;
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    public ByteBuffer getMemoryBuffer() {
        return this.map;
    }
}
