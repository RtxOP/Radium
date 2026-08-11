package net.caffeinemc.mods.sodium.client.gl.attribute;

import net.caffeinemc.mods.sodium.client.render.vertex.VertexFormatAttribute;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides a generic vertex format which contains the attributes defined by a {@link VertexFormatAttribute}. Other code can then retrieve
 * the attributes and work with encoded data in a generic manner without needing to rely on a specific format.
 */
public class GlVertexFormat {
    private final Map<VertexFormatAttribute, GlVertexAttribute> attributesKeyed;

    private final int stride;
    private final GlVertexAttributeBinding[] bindings;

    public GlVertexFormat(Map<VertexFormatAttribute, GlVertexAttribute> attributesKeyed, GlVertexAttributeBinding[] bindings, int stride) {
        this.attributesKeyed = attributesKeyed;
        this.bindings = bindings;
        this.stride = stride;
    }

    public static Builder builder(int stride) {
        return new Builder(stride);
    }

    /**
     * Returns the {@link GlVertexAttribute} of this vertex format bound to the type {@param name}.
     * @throws NullPointerException If the attribute does not exist in this format
     */
    public GlVertexAttribute getAttribute(VertexFormatAttribute name) {
        GlVertexAttribute attr = this.attributesKeyed.get(name);

        if (attr == null) {
            throw new NullPointerException("No attribute exists for " + name.toString());
        }

        return attr;
    }

    /**
     * @return The stride (or the size of) the vertex format in bytes
     */
    public int getStride() {
        return this.stride;
    }

    @Override
    public String toString() {
        return String.format("GlVertexFormat{attributes=%d,stride=%d}",
                this.attributesKeyed.size(), this.stride);
    }

    public GlVertexAttributeBinding[] getShaderBindings() {
        return bindings;
    }

    public static class Builder {
        private final Map<VertexFormatAttribute, GlVertexAttribute> attributes;
        private final Map<GlVertexAttribute, Integer> bindings;
        private final int stride;

        public Builder(int stride) {
            // LinkedHashMaps preserve insertion order, matching the
            // fastutil ArrayMap semantics the upstream project uses.
            this.attributes = new LinkedHashMap<>();
            this.bindings = new LinkedHashMap<>();
            this.stride = stride;
        }

        public Builder addElement(VertexFormatAttribute attribute, int binding, int pointer) {
            return this.addElement(attribute, binding, new GlVertexAttribute(attribute.format(), attribute.count(), attribute.normalized(), pointer, this.stride, attribute.intType()));
        }

        /**
         * Adds an vertex attribute which will be bound to the given generic attribute type.
         *
         * @param type The generic attribute type
         * @param attribute The attribute to bind
         * @throws IllegalStateException If an attribute is already bound to the generic type
         */
        private Builder addElement(VertexFormatAttribute type, int binding, GlVertexAttribute attribute) {
            if (attribute.getPointer() >= this.stride) {
                throw new IllegalArgumentException("Element starts outside vertex format");
            }

            if (attribute.getPointer() + attribute.getSize() > this.stride) {
                throw new IllegalArgumentException("Element extends outside vertex format");
            }

            if (this.attributes.put(type, attribute) != null) {
                throw new IllegalStateException("Generic attribute " + type.name() + " already defined in vertex format");
            }

            if (binding != -1) {
                this.bindings.put(attribute, binding);
            }

            return this;
        }

        /**
         * Creates a {@link GlVertexFormat} from the current builder.
         */
        public GlVertexFormat build() {
            int size = 0;

            for (GlVertexAttribute attribute : this.attributes.values()) {
                size = Math.max(size, attribute.getPointer() + attribute.getSize());
            }

            // The stride must be large enough to cover all attributes. This still allows for additional padding
            // to be added to the end of the vertex to accommodate alignment restrictions.
            if (this.stride < size) {
                throw new IllegalArgumentException("Stride is too small");
            }

            GlVertexAttributeBinding[] bindings = this.bindings.entrySet().stream()
                    .map(entry -> new GlVertexAttributeBinding(entry.getValue(), entry.getKey()))
                    .toArray(GlVertexAttributeBinding[]::new);

            return new GlVertexFormat(this.attributes, bindings, this.stride);
        }
    }
}
