package gg.sona.radium.diag;

import net.caffeinemc.mods.sodium.client.gl.buffer.GlBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One-shot frame diagnostics for the D3 boot-test cycle (temporary; removed after the
 * invisible-chunks/FPS issue is root-caused). All logging is throttled so it cannot
 * meaningfully affect frame times or spam the log.
 */
public final class Diag {
    private static final ConcurrentHashMap<String, Long> lastLogAt = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private static final java.util.Set<String> loggedOnce = java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final java.util.Set<String> reportedErrors = java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    /** Logs at most once per 1000ms per key. */
    public static void throttle(String key, String message) {
        long now = System.currentTimeMillis();
        Long prev = lastLogAt.get(key);
        if (prev == null || now - prev >= 1000L) {
            lastLogAt.put(key, now);
            System.out.println("[RadiumDiag] " + message);
        }
    }

    /** Increments a named counter and logs it at most once per 1000ms. */
    public static void count(String key, String message) {
        AtomicLong c = counters.computeIfAbsent(key, k -> new AtomicLong());
        c.incrementAndGet();
        long now = System.currentTimeMillis();
        Long prev = lastLogAt.get(key);
        if (prev == null || now - prev >= 1000L) {
            lastLogAt.put(key, now);
            System.out.println("[RadiumDiag] " + message + " (count=" + c.get() + ")");
        }
    }

    /** Logs a message exactly once. */
    public static void once(String key, String message) {
        if (loggedOnce.add(key)) {
            System.out.println("[RadiumDiag] " + message);
        }
    }

    /**
     * Drains glGetError and logs the first error per stage-key once. Returns true if any error was pending.
     *
     * <p>Gated off by default: glGetError forces a full GPU pipeline flush on Mesa/Intel drivers and is
     * called multiple times per frame (once per render pass + per setup phase). Even with no errors pending
     * that sync cost was a large share of the frame budget. Flip GL_PROBE_ENABLED to true to re-enable.</p>
     */
    public static boolean glProbe(String stageKey) {
        if (!GL_PROBE_ENABLED) {
            return false;
        }

        int err = GL11.glGetError();
        if (err == GL11.GL_NO_ERROR) {
            return false;
        }
        if (reportedErrors.add(stageKey)) {
            System.out.println("[RadiumDiag] GL ERROR after stage " + stageKey + ": " + err + " (0x" + Integer.toHexString(err) + ")");
        }
        // drain any further pending errors so the next stage's probe is clean
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            // drain
        }
        return true;
    }

    private static final boolean GL_PROBE_ENABLED = false;

    // ==========================================================================================
    // D4 probe batch: draw-time GL state, shader uniform readback, full-frame timing.
    // Throttled to 1 log line per second per key; the underlying calls are cheap.
    // ==========================================================================================

    private static long frameHeadNanos = 0L;
    private static long worldSegStartNanos = 0L;

    /** Call at the HEAD of EntityRenderer.updateCameraAndRender each frame. Measures frame interval + fps. */
    public static void frameTick() {
        long now = System.nanoTime();
        if (frameHeadNanos != 0L) {
            long ms = (now - frameHeadNanos) / 1_000_000L;
            float fps = 1000.0f / ms;
            throttle("frameInterval", String.format("frameInterval=%dms fps=%.1f", ms, fps));
        }
        frameHeadNanos = now;
    }

    /** Call at the RETURN of EntityRenderer.updateCameraAndRender each frame. Logs the full render-pass time. */
    public static void frameDone() {
        if (frameHeadNanos != 0L) {
            long ms = (System.nanoTime() - frameHeadNanos) / 1_000_000L;
            throttle("frameDuration", "frameDuration=" + ms + "ms");
        }
    }

    /** Call on the first (SOLID) block-layer pass: starts the world-render segment timer. */
    public static void worldSegmentStart() {
        worldSegStartNanos = System.nanoTime();
    }

    /** Call on the last (TRANSLUCENT) block-layer pass: logs the world-render segment time. */
    public static void worldSegmentEnd() {
        if (worldSegStartNanos != 0L) {
            long ms = (System.nanoTime() - worldSegStartNanos) / 1_000_000L;
            throttle("worldSegment", "worldSegment=" + ms + "ms");
        }
        worldSegStartNanos = 0L;
    }

    /** Camera/geometry truth, logged from SodiumWorldRenderer.setupTerrain once per second. */
    public static void cameraProbe(String message) {
        throttle("cameraProbe", message);
    }

    /** Extracts every uniform of the currently bound program by name into one log line (1/sec). */
    public static void uniformProbe() {
        long now = System.currentTimeMillis();
        Long prev = lastLogAt.get("uniformProbe");
        if (prev != null && now - prev < 1000L) {
            return;
        }
        lastLogAt.put("uniformProbe", now);

        int program = GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
        if (program == 0) {
            return;
        }
        FloatBuffer fb = BufferUtils.createFloatBuffer(16);
        IntBuffer ib = BufferUtils.createIntBuffer(4);
        String[] floatNames = { "u_RegionOffset", "u_ModelViewMatrix", "u_ProjectionMatrix",
                "u_FogColor", "u_FogStart", "u_FogEnd", "u_FadePeriodInv" };
        StringBuilder sb = new StringBuilder();
        for (String name : floatNames) {
            int loc = org.lwjgl.opengl.GL20.glGetUniformLocation(program, name);
            sb.append(' ').append(name).append('=');
            if (loc < 0) {
                sb.append("NA");
            } else {
                fb.clear();
                org.lwjgl.opengl.GL20.glGetUniform(program, loc, fb);
                fb.rewind();
                int n = name.contains("Matrix") ? 16 : (name.endsWith("Color") ? 4 : (name.equals("u_RegionOffset") ? 3 : 1));
                sb.append('[');
                for (int i = 0; i < n; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(String.format("%.3f", fb.get(i)));
                }
                sb.append(']');
            }
        }
        for (String name : new String[] { "u_FogShape", "u_CurrentTime" }) {
            int loc = org.lwjgl.opengl.GL20.glGetUniformLocation(program, name);
            sb.append(' ').append(name).append('=');
            if (loc < 0) {
                sb.append("NA");
            } else {
                ib.clear();
                org.lwjgl.opengl.GL20.glGetUniform(program, loc, ib);
                sb.append(ib.get(0));
            }
        }
        System.out.println("[RadiumDiag] program=" + program + sb);
    }

    /** Snapshot of the GL state that determines whether chunk draws become visible (1/sec). */
    public static void glStateProbe() {
        glStateProbe("glStateProbe");
    }

    public static void glStateProbe(String key) {
        long now = System.currentTimeMillis();
        Long prev = lastLogAt.get(key);
        if (prev != null && now - prev < 1000L) {
            return;
        }
        lastLogAt.put(key, now);

        IntBuffer ib = BufferUtils.createIntBuffer(16);
        ByteBuffer bb = BufferUtils.createByteBuffer(16);

        int[] texBind = new int[2];
        int activeBefore = GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        for (int i = 0; i < 2; i++) {
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + i);
            texBind[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        org.lwjgl.opengl.GL13.glActiveTexture(activeBefore);

        ib.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, ib);
        int vp0 = ib.get(0), vp1 = ib.get(1), vp2 = ib.get(2), vp3 = ib.get(3);
        ib.clear();
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, ib);
        int sc0 = ib.get(0), sc1 = ib.get(1), sc2 = ib.get(2), sc3 = ib.get(3);

        bb.clear();
        GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK, bb);
        boolean depthWrite = bb.get(0) != 0;
        bb.clear();
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, bb);
        String colorMask = (bb.get(0) != 0 ? "1" : "0") + (bb.get(1) != 0 ? "1" : "0") + (bb.get(2) != 0 ? "1" : "0") + (bb.get(3) != 0 ? "1" : "0");

        // Chunk-fade UBO readback: if u_chunkFades stays 0, color *= fadeFactor hides everything
        int uboBinding = GL11.glGetInteger(org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER_BINDING);
        StringBuilder ubo = new StringBuilder();
        if (uboBinding != 0) {
            ByteBuffer ubb = BufferUtils.createByteBuffer(64);
            org.lwjgl.opengl.GL15.glGetBufferSubData(org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER, 0L, ubb);
            for (int i = 0; i < 16; i++) {
                if (i > 0) ubo.append(',');
                ubo.append(ubb.getInt());
            }
        }

        System.out.println("[RadiumDiag] state prog=" + GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM)
                + " vao=" + GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_VERTEX_ARRAY_BINDING)
                + " arrBuf=" + GL11.glGetInteger(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING)
                + " elemBuf=" + GL11.glGetInteger(org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING)
                + " fbo=" + GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING)
                + " drawFbo=" + GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                + " readFbo=" + GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING)
                + " activeTex=" + activeBefore
                + " tex0=" + texBind[0] + " tex1=" + texBind[1]
                + " depthTest=" + GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                + " depthFunc=" + GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
                + " depthWrite=" + depthWrite
                + " cull=" + GL11.glIsEnabled(GL11.GL_CULL_FACE)
                + " cullMode=" + GL11.glGetInteger(GL11.GL_CULL_FACE_MODE)
                + " blend=" + GL11.glIsEnabled(GL11.GL_BLEND)
                + " colorMask=" + colorMask
                + " scissor=" + GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) + "(" + sc0 + "," + sc1 + "," + sc2 + "," + sc3 + ")"
                + " viewport=(" + vp0 + "," + vp1 + "," + vp2 + "," + vp3 + ")"
                + " ubo=" + uboBinding + "[" + ubo + "]");
    }

    /**
     * Read back the pixel colours in the centre of the current read framebuffer (1/sec). If the ground should be
     * visible at screen centre and the readback still shows the fog/sky colour, chunk geometry is not reaching the
     * framebuffer; if it shows grass/dirt colours, geometry IS drawing (so the problem is elsewhere, e.g. a colour
     * pipeline issue).
     */
    public static void pixelProbe() {
        long now = System.currentTimeMillis();
        Long prev = lastLogAt.get("pixelProbe");
        if (prev != null && now - prev < 1000L) {
            return;
        }
        lastLogAt.put("pixelProbe", now);

        IntBuffer ib = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, ib);
        int vw = ib.get(2), vh = ib.get(3);
        int cx = vw / 2, cy = vh / 2;

        StringBuilder sb = new StringBuilder();
        sb.append("pixels center=(").append(cx).append(',').append(cy).append(')');
        int[] dx = { 0, -vw / 4, vw / 4, 0, 0, 0, vw / 6, -vw / 6 };
        int[] dy = { 0, 0, 0, -vh / 4, vh / 4, (int) (vh * 0.90), (int) (vh * 0.85), (int) (vh * 0.85) };
        ByteBuffer px = BufferUtils.createByteBuffer(4);
        FloatBuffer dz = BufferUtils.createFloatBuffer(1);
        for (int i = 0; i < dx.length; i++) {
            int px0 = cx + dx[i], py0 = cy + dy[i];
            px.clear();
            GL11.glReadPixels(px0, py0, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
            int r = px.get(0) & 0xFF, g = px.get(1) & 0xFF, b = px.get(2) & 0xFF, a = px.get(3) & 0xFF;
            dz.clear();
            GL11.glReadPixels(px0, py0, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, dz);
            float z = dz.get(0);
            sb.append(String.format(" [%d,%d,%d,%d z=%.3f]", r, g, b, a, z));
        }
        System.out.println("[RadiumDiag] " + sb);
    }

    /**
     * Decode the first few vertices of a freshly-built section mesh (1/sec). Positions are decoded from the
     * CompactChunkVertex 20-bit packed layout: p = (q / 2^20) * 32.0 - 8.0. If positions are sane (0..16 within a
     * section) and colours are non-black, the mesh content is correct and any invisibility is downstream (texture,
     * lightmap, GL state).
     */
    public static void meshDump(ByteBuffer buf, int stride, int vertexTotal) {
        long now = System.currentTimeMillis();
        Long prev = lastLogAt.get("meshDump");
        if (prev != null && now - prev < 1000L) {
            return;
        }
        lastLogAt.put("meshDump", now);

        StringBuilder sb = new StringBuilder();
        sb.append("mesh verts=").append(vertexTotal);
        int n = Math.min(vertexTotal, 6);
        for (int i = 0; i < n; i++) {
            int base = i * stride;
            int hi = buf.getInt(base);
            int lo = buf.getInt(base + 4);
            int argb = buf.getInt(base + 8);
            int tex = buf.getInt(base + 12);
            int light = buf.getInt(base + 16);
            int x = ((hi & 0x3FF) << 10) | (lo & 0x3FF);
            int y = (((hi >>> 10) & 0x3FF) << 10) | ((lo >>> 10) & 0x3FF);
            int z = (((hi >>> 20) & 0x3FF) << 10) | ((lo >>> 20) & 0x3FF);
            float fx = (x / 1048576.0f) * 32.0f - 8.0f;
            float fy = (y / 1048576.0f) * 32.0f - 8.0f;
            float fz = (z / 1048576.0f) * 32.0f - 8.0f;
            sb.append(String.format(" v%d=(%.2f,%.2f,%.2f) c=%08X t=%08X l=%08X", i, fx, fy, fz, argb, tex, light));
        }
        System.out.println("[RadiumDiag] " + sb);
    }

    /**
     * Always-on GL error check for the boot-test (not gated by GL_PROBE_ENABLED). Logs the first error per
     * key once, then drains any further pending errors so later probes start clean.
     */
    public static void drawError(String key, String detail) {
        int err = GL11.glGetError();
        if (err != GL11.GL_NO_ERROR && reportedErrors.add(key)) {
            System.out.println("[RadiumDiag] GL ERROR after " + detail + ": " + err + " (0x" + Integer.toHexString(err) + ")");
        }
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            // drain
        }
    }

    /**
     * Boot-test probe: read back the GPU geometry at the first draw command's baseVertex, decode the vertices, and
     * project them to NDC on the CPU using the live shader uniforms. If the GPU data at baseVertex matches the CPU
     * mesh (see meshDump) and the projected NDC is inside the viewport, the geometry should be visible and the bug
     * is in rasterisation state (cull/depth); if the GPU data is zeros/garbage or the NDC is off-screen/behind the
     * camera, the upload or transform path is broken. Run right after executeDrawBatch.
     */
    public static void drawProbe(GlBuffer geometryBuffer, GlBuffer indexBuffer, MultiDrawBatch batch) {
        long now = System.currentTimeMillis();
        Long prev = lastLogAt.get("drawProbe");
        if (prev != null && now - prev < 1000L) {
            return;
        }
        lastLogAt.put("drawProbe", now);

        StringBuilder sb = new StringBuilder("drawProbe");
        try {
            int gSize = bufferSize(GL15.GL_ARRAY_BUFFER, geometryBuffer.handle());
            int iSize = bufferSize(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer.handle());
            int maxVertex = gSize / CompactChunkVertex.STRIDE;
            int maxIndex = iSize / 4;
            sb.append(" geomSize=").append(gSize).append(" idxBufSize=").append(iSize);

            int program = GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
            float[] proj = uniformMat4(program, "u_ProjectionMatrix");
            float[] mv = uniformMat4(program, "u_ModelViewMatrix");
            float[] reg = uniformVec3(program, "u_RegionOffset");

            int outOfRange = 0;
            for (int i = 0; i < batch.size; i++) {
                int count = batch.elementCounts.get(i);
                int base = batch.baseVertices.get(i);
                long elemOff = batch.elementOffsets.get(i);
                int vertexSpan = (count / 6) * 4;
                if (maxVertex > 0 && base + vertexSpan > maxVertex) outOfRange++;
                if (maxIndex > 0 && (elemOff / 4L) + count > maxIndex) outOfRange++;
            }
            sb.append(" draws=").append(batch.size).append(" outOfRange=").append(outOfRange);

            if (batch.size > 0) {
                int count0 = batch.elementCounts.get(0);
                int base0 = batch.baseVertices.get(0);
                long off0 = batch.elementOffsets.get(0);
                sb.append(" draw0=(").append(count0).append(',').append(base0).append(',').append(off0).append(')');

                int readOffset = base0 * CompactChunkVertex.STRIDE;
                if (readOffset >= 0 && readOffset + 6 * CompactChunkVertex.STRIDE <= gSize) {
                    ByteBuffer gbuf = BufferUtils.createByteBuffer(6 * CompactChunkVertex.STRIDE);
                    readbackAt(GL15.GL_ARRAY_BUFFER, geometryBuffer.handle(), readOffset, gbuf);
                    decodeAndProject(sb, gbuf, reg, mv, proj);
                } else {
                    sb.append(" GEOMREAD-OOB(").append(readOffset).append('/').append(gSize).append(')');
                }
            }

            if (iSize > 0) {
                ByteBuffer ibuf = BufferUtils.createByteBuffer(Math.min(iSize, 12 * 4));
                readbackAt(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer.handle(), 0, ibuf);
                IntBuffer idx = ibuf.asIntBuffer();
                sb.append(" idx[");
                for (int i = 0; i < idx.remaining(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(idx.get(i));
                }
                sb.append(']');
            }
        } catch (Throwable t) {
            sb.append(" EX=").append(t);
        }
        System.out.println("[RadiumDiag] " + sb);
    }

    private static float[] uniformMat4(int program, String name) {
        float[] out = new float[16];
        int loc = org.lwjgl.opengl.GL20.glGetUniformLocation(program, name);
        if (loc >= 0) {
            FloatBuffer fb = BufferUtils.createFloatBuffer(16);
            org.lwjgl.opengl.GL20.glGetUniform(program, loc, fb);
            fb.rewind();
            fb.get(out);
        }
        return out;
    }

    private static float[] uniformVec3(int program, String name) {
        float[] out = new float[3];
        int loc = org.lwjgl.opengl.GL20.glGetUniformLocation(program, name);
        if (loc >= 0) {
            FloatBuffer fb = BufferUtils.createFloatBuffer(3);
            org.lwjgl.opengl.GL20.glGetUniform(program, loc, fb);
            fb.rewind();
            fb.get(out);
        }
        return out;
    }

    /** Decode vertices and project to NDC using the column-major shader matrices and the shader's _draw_id translation. */
    private static void decodeAndProject(StringBuilder sb, ByteBuffer buf, float[] reg, float[] mv, float[] proj) {
        int n = Math.min(4, buf.capacity() / CompactChunkVertex.STRIDE);
        for (int i = 0; i < n; i++) {
            int base = i * CompactChunkVertex.STRIDE;
            int hi = buf.getInt(base);
            int lo = buf.getInt(base + 4);
            int argb = buf.getInt(base + 8);
            int lightAndData = buf.getInt(base + 16);
            int x = ((hi & 0x3FF) << 10) | (lo & 0x3FF);
            int y = (((hi >>> 10) & 0x3FF) << 10) | ((lo >>> 10) & 0x3FF);
            int z = (((hi >>> 20) & 0x3FF) << 10) | ((lo >>> 20) & 0x3FF);
            float fx = (x / 1048576.0f) * 32.0f - 8.0f;
            float fy = (y / 1048576.0f) * 32.0f - 8.0f;
            float fz = (z / 1048576.0f) * 32.0f - 8.0f;

            int drawId = (lightAndData >>> 24) & 0xFF;
            int rx = (drawId >>> 5) & 7, ry = drawId & 3, rz = (drawId >>> 2) & 7;
            float wx = reg[0] + rx * 16.0f + fx;
            float wy = reg[1] + ry * 16.0f + fy;
            float wz = reg[2] + rz * 16.0f + fz;
            float vx = mv[0] * wx + mv[4] * wy + mv[8] * wz + mv[12];
            float vy = mv[1] * wx + mv[5] * wy + mv[9] * wz + mv[13];
            float vz = mv[2] * wx + mv[6] * wy + mv[10] * wz + mv[14];
            float vw = mv[3] * wx + mv[7] * wy + mv[11] * wz + mv[15];
            float cx = proj[0] * vx + proj[4] * vy + proj[8] * vz + proj[12] * vw;
            float cy = proj[1] * vx + proj[5] * vy + proj[9] * vz + proj[13] * vw;
            float cz = proj[2] * vx + proj[6] * vy + proj[10] * vz + proj[14] * vw;
            float cw = proj[3] * vx + proj[7] * vy + proj[11] * vz + proj[15] * vw;
            String ndc = "off";
            if (cw != 0) {
                float nx = cx / cw, ny = cy / cw, nz = cz / cw;
                boolean in = Math.abs(nx) <= 1.0f && Math.abs(ny) <= 1.0f && nz > 0.0f && nz <= 1.0f;
                ndc = String.format("(%.2f,%.2f,%.3f)%s", nx, ny, nz, in ? "IN" : "OUT");
            } else {
                ndc = "(cw=0)";
            }
            sb.append(String.format(" v%d=(%.2f,%.2f,%.2f)c=%08X sec=%d->%s", i, fx, fy, fz, argb, drawId, ndc));
        }
    }

    private static int bufferSize(int target, int handle) {
        GL15.glBindBuffer(target, handle);
        int size = GL15.glGetBufferParameteri(target, GL15.GL_BUFFER_SIZE);
        GL15.glBindBuffer(target, 0);
        return size;
    }

    private static void readbackAt(int target, int handle, long offset, ByteBuffer out) {
        GL15.glBindBuffer(target, handle);
        GL15.glGetBufferSubData(target, offset, out);
        GL15.glBindBuffer(target, 0);
    }

    /** Boot-test overrides: force GL state that would otherwise hide geometry, keyed off -D system properties. */
    public static void overrideState(boolean noCull, boolean noDepth, boolean noBlend) {
        if (noCull && GL11.glIsEnabled(GL11.GL_CULL_FACE)) {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
        if (noDepth && GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        if (noBlend && GL11.glIsEnabled(GL11.GL_BLEND)) {
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    public static void restoreState(boolean noCull, boolean noDepth, boolean noBlend) {
        if (noCull) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        }
        if (noDepth) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        if (noBlend) {
            GL11.glEnable(GL11.GL_BLEND);
        }
    }
}
