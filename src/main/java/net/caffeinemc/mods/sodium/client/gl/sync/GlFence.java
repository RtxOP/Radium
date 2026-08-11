package net.caffeinemc.mods.sodium.client.gl.sync;

import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLSync;

public class GlFence {
    private final GLSync id;
    private boolean disposed;

    public GlFence(GLSync id) {
        this.id = id;
    }

    public boolean isCompleted() {
        this.checkDisposed();

        // LWJGL2's 2-argument form returns the sync status value directly,
        // unlike LWJGL3's 3-argument form which writes into a provided buffer.
        int result = GL32.glGetSynci(this.id, GL32.GL_SYNC_STATUS);

        return result == GL32.GL_SIGNALED;
    }

    public void sync() {
        this.checkDisposed();
        this.sync(Long.MAX_VALUE);
    }

    public void sync(long timeout) {
        this.checkDisposed();
        GL32.glClientWaitSync(this.id, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, timeout);
    }

    public void delete() {
        GL32.glDeleteSync(this.id);
        this.disposed = true;
    }

    private void checkDisposed() {
        if (this.disposed) {
            throw new IllegalStateException("Fence object has been disposed");
        }
    }
}
