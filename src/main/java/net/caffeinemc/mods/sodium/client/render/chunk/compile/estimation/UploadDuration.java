package net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation;

/**
 * Java 8 port of the reference {@code record UploadDuration(long uploadDuration, long size)}.
 */
public final class UploadDuration implements ExpDecayLinear2DEstimator.DataPair<Void> {
    private final long uploadDuration;
    private final long size;

    public UploadDuration(long uploadDuration, long size) {
        this.uploadDuration = uploadDuration;
        this.size = size;
    }

    @Override
    public long x() {
        return this.size;
    }

    @Override
    public long y() {
        return this.uploadDuration;
    }

    @Override
    public Void category() {
        return null;
    }

    public long uploadDuration() {
        return this.uploadDuration;
    }

    public long size() {
        return this.size;
    }
}
