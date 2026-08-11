package net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation;

/**
 * Java 8 port of the reference {@code record JobEffort(...)}.
 */
public final class JobEffort implements ExpDecayLinear2DEstimator.DataPair<Class<?>> {
    private final Class<?> category;
    private final long duration;
    private final long effort;

    public JobEffort(Class<?> category, long duration, long effort) {
        this.category = category;
        this.duration = duration;
        this.effort = effort;
    }

    public static JobEffort untilNowWithEffort(Class<?> effortType, long start, long effort) {
        return new JobEffort(effortType, System.nanoTime() - start, effort);
    }

    @Override
    public long x() {
        return this.effort;
    }

    @Override
    public long y() {
        return this.duration;
    }

    @Override
    public Class<?> category() {
        return this.category;
    }

    public long duration() {
        return this.duration;
    }

    public long effort() {
        return this.effort;
    }
}
