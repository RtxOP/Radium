package net.caffeinemc.mods.sodium.client.gl.tessellation;

import net.caffeinemc.mods.sodium.client.gl.attribute.GlVertexAttributeBinding;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBuffer;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBufferTarget;

public final class TessellationBinding {
    private final GlBufferTarget target;
    private final GlBuffer buffer;
    private final GlVertexAttributeBinding[] attributeBindings;

    public TessellationBinding(GlBufferTarget target,
                               GlBuffer buffer,
                               GlVertexAttributeBinding[] attributeBindings) {
        this.target = target;
        this.buffer = buffer;
        this.attributeBindings = attributeBindings;
    }

    public static TessellationBinding forVertexBuffer(GlBuffer buffer, GlVertexAttributeBinding[] attributes) {
        return new TessellationBinding(GlBufferTarget.ARRAY_BUFFER, buffer, attributes);
    }

    public static TessellationBinding forElementBuffer(GlBuffer buffer) {
        return new TessellationBinding(GlBufferTarget.ELEMENT_BUFFER, buffer, new GlVertexAttributeBinding[0]);
    }

    public GlBufferTarget target() {
        return this.target;
    }

    public GlBuffer buffer() {
        return this.buffer;
    }

    public GlVertexAttributeBinding[] attributeBindings() {
        return this.attributeBindings;
    }
}
