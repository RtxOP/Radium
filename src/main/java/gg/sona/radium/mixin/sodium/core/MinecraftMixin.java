package gg.sona.radium.mixin.sodium.core;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.gl.functions.BufferStorageFunctions;
import net.minecraft.client.Minecraft;
import net.minecraft.profiler.Profiler;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLSync;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    @Final
    public Profiler mcProfiler;
    // LWJGL 2 (MC 1.8.9) represents sync objects as GLSync, not the raw
    // pointer long that newer LWJGL3 Sodium uses. ArrayDeque stands in for
    // the fastutil ObjectArrayFIFOQueue of the reference implementation.
    @Unique
    private final ArrayDeque<GLSync> fences = new ArrayDeque<>();

    @Unique
    private static boolean radium$smokeLogged;

    /**
     * We run this at the beginning of the frame (except for the first frame) to give the previous frame plenty of time
     * to render on the GPU. This allows us to stall on ClientWaitSync for less time.
     *
     * <p>This handler also carries the staged GL smoke + late config registration.
     *
     * <p>IMPORTANT (boot regressions): the smoke originally injected into {@code startGame} at the
     * GuiIngame constructor INVOKE. That selector embeds MCP class names in the {@code @At} target, which
     * mixin 0.7.11's FML remapper cannot translate into the notch-named runtime bytecode of the production
     * client (it remaps method/field names, not class names inside @At selectors), so the injection check
     * failed with 0/1 and the launch died silently (InjectionError -> MixinTransformerError ->
     * NoClassDefFoundError -> launcher ExitTrappedException). runTick@HEAD is a method-name-only selector
     * (remaps fine) and runs after startGame has fully completed, so the GL context is alive for the smoke.</p>
     */
    @Inject(method = "runTick", at = @At("HEAD"))
    private void preRender(CallbackInfo ci) {
        radium$glSmoke();

        if (SodiumClientMod.options().advanced.cpuRenderAhead) {
            this.mcProfiler.startSection("wait_for_gpu");

            while (this.fences.size() > SodiumClientMod.options().advanced.cpuRenderAheadLimit) {
                GLSync fence = this.fences.removeFirst();
                // We do a ClientWaitSync here instead of a WaitSync to not allow the CPU to get too far ahead of the GPU.
                // This is also needed to make sure that our persistently-mapped staging buffers function correctly, rather
                // than being overwritten by data meant for future frames before the current one has finished rendering on
                // the GPU.
                //
                // Because we use GL_SYNC_FLUSH_COMMANDS_BIT, a flush will be inserted at some point in the command stream
                // (the stream of commands the GPU and/or driver (aka. the "server") is processing).
                // In OpenGL 4.4 contexts and below, the flush will be inserted *right before* the call to ClientWaitSync.
                // In OpenGL 4.5 contexts and above, the flush will be inserted *right after* the call to FenceSync (the
                // creation of the fence).
                // The flush, when the server reaches it in the command stream and processes it, tells the server that it
                // must *finish execution* of all the commands that have already been processed in the command stream,
                // and only after everything before the flush is done is it allowed to start processing and executing
                // commands after the flush.
                // Because we are also waiting on the client for the FenceSync to finish, the flush is effectively treated
                // like a Finish command, where we know that once ClientWaitSync returns, it's likely that everything
                // before it has been completed by the GPU.
                GL32.glClientWaitSync(fence, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, Long.MAX_VALUE);
                GL32.glDeleteSync(fence);
            }

            this.mcProfiler.endSection();
        }
    }

    /**
     * Staged GL smoke test (M3 Phase A validation): forces the sodium LWJGL2
     * device layer to instantiate inside the real game GL context and logs
     * what it sees. Non-fatal by design - a failure here must never take the
     * game down, just print the [Radium] GL smoke FAILED marker.
     */
    @Unique
    private static void radium$glSmoke() {
        if (radium$smokeLogged) {
            return;
        }
        radium$smokeLogged = true;

        try {
            // RenderDevice.INSTANCE = new GLRenderDevice(); also picks the
            // DeviceFunctions (pure capability inspection, no GL allocations).
            RenderDevice device = RenderDevice.INSTANCE;
            device.makeActive();
            ContextCapabilities caps = device.getCapabilities();
            int lodBias = device.getMaxTextureLodBias();
            BufferStorageFunctions storage = device.getDeviceFunctions().getBufferStorageFunctions();
            device.makeInactive();

            System.out.println("[Radium] GL smoke: vendor=" + GL11.glGetString(GL11.GL_VENDOR));
            System.out.println("[Radium] GL smoke: renderer=" + GL11.glGetString(GL11.GL_RENDERER));
            System.out.println("[Radium] GL smoke: version=" + GL11.glGetString(GL11.GL_VERSION));
            System.out.println("[Radium] GL smoke device: maxTextureLodBias=" + lodBias
                    + " bufferStorage=" + storage.name()
                    + " openGL44=" + caps.OpenGL44
                    + " arbBufferStorage=" + caps.GL_ARB_buffer_storage);
        } catch (Throwable t) {
            System.out.println("[Radium] GL smoke FAILED: " + t);
            t.printStackTrace();
        }
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void postRender(CallbackInfo ci) {
        if (SodiumClientMod.options().advanced.cpuRenderAhead) {
            GLSync fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);

            if (fence == null) {
                throw new RuntimeException("Failed to create fence object");
            }

            this.fences.addLast(fence);
        }
    }
}
