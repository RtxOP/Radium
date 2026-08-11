package net.caffeinemc.mods.sodium.client.render.vertex;

import net.caffeinemc.mods.sodium.client.gl.attribute.GlVertexAttributeFormat;

import java.util.Objects;

/**
 * Immutable vertex-format attribute descriptor. The reference defines this as a
 * Java {@code record}; the port targets Java 8, so it is an equivalent final
 * class with value semantics (used as a Map key in {@code GlVertexFormat}).
 */
public final class VertexFormatAttribute {
    private final String name;
    private final GlVertexAttributeFormat format;
    private final int count;
    private final boolean normalized;
    private final boolean intType;

    public VertexFormatAttribute(String name, GlVertexAttributeFormat format, int count, boolean normalized, boolean intType) {
        this.name = name;
        this.format = format;
        this.count = count;
        this.normalized = normalized;
        this.intType = intType;
    }

    public String name() {
        return this.name;
    }

    public GlVertexAttributeFormat format() {
        return this.format;
    }

    public int count() {
        return this.count;
    }

    public boolean normalized() {
        return this.normalized;
    }

    public boolean intType() {
        return this.intType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VertexFormatAttribute)) return false;
        VertexFormatAttribute that = (VertexFormatAttribute) o;
        return this.count == that.count &&
                this.normalized == that.normalized &&
                this.intType == that.intType &&
                this.name.equals(that.name) &&
                this.format == that.format;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.format, this.count, this.normalized, this.intType);
    }

    @Override
    public String toString() {
        return "VertexFormatAttribute[name=" + this.name +
                ", format=" + this.format +
                ", count=" + this.count +
                ", normalized=" + this.normalized +
                ", intType=" + this.intType + "]";
    }
}
