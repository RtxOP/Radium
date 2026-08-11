package gg.sona.radium.diag;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

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

    /** Drains glGetError and logs the first error per stage-key once. Returns true if any error was pending. */
    public static boolean glProbe(String stageKey) {
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
}
