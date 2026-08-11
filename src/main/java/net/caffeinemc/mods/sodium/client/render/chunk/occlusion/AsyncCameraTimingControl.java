package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;

public class AsyncCameraTimingControl {
    private static final double ENTER_SYNC_STEP_THRESHOLD = 32;
    private static final double EXIT_SYNC_STEP_THRESHOLD = 20;

    private Vec3 previousPosition;
    private boolean isSyncRendering = false;

    public boolean getShouldRenderSync() {
        // Port note: MCP 1.8.9 has no Camera class; the render view entity's position is the
        // semantic equivalent of the reference's Camera#getPosition().
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        Vec3 cameraPosition = new Vec3(camera.posX, camera.posY, camera.posZ);

        if (this.previousPosition == null) {
            this.previousPosition = cameraPosition;
            return true;
        }

        // if the camera moved too much, use sync rendering until it stops
        double distance = Math.max(
                Math.abs(cameraPosition.xCoord - this.previousPosition.xCoord),
                Math.max(
                        Math.abs(cameraPosition.yCoord - this.previousPosition.yCoord),
                        Math.abs(cameraPosition.zCoord - this.previousPosition.zCoord)
                )
        );
        if (this.isSyncRendering && distance <= EXIT_SYNC_STEP_THRESHOLD) {
            this.isSyncRendering = false;
        } else if (!this.isSyncRendering && distance >= ENTER_SYNC_STEP_THRESHOLD) {
            this.isSyncRendering = true;
        }

        this.previousPosition = cameraPosition;

        return this.isSyncRendering;
    }
}
