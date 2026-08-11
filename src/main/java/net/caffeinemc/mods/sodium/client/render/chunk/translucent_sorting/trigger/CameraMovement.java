package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger;

import org.joml.Vector3dc;

public class CameraMovement {
    private final Vector3dc start;
    private final Vector3dc end;
    public CameraMovement(Vector3dc start, Vector3dc end) {
        this.start = start;
        this.end = end;
        }

    public Vector3dc start() {
        return this.start;
    }

    public Vector3dc end() {
        return this.end;
    }

    public boolean hasChanged() {
        return !this.start.equals(this.end);
    }

}
