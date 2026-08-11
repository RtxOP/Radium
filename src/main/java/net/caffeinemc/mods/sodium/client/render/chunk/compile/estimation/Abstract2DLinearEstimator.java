package net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Port divergence: the reference extends fastutil's {@code ObjectArrayList}; this port uses
 * {@link ArrayList} so no fastutil dependency is introduced into the mod jar.
 */
public abstract class Abstract2DLinearEstimator<
        C,
        TBatch extends Estimator.DataBatch<Abstract2DLinearEstimator.DataPair<C>>,
        TModel extends Abstract2DLinearEstimator.LinearFunction<C, TBatch>
        > extends Estimator<
        C,
        Abstract2DLinearEstimator.DataPair<C>,
        TBatch,
        Long,
        Long,
        TModel> {
    protected final long initialOutput;

    public Abstract2DLinearEstimator(long initialOutput) {
        this.initialOutput = initialOutput;
    }

    public interface DataPair<C> extends Estimator.DataPoint<C> {
        long x();

        long y();
    }

    protected abstract static class LinearRegressionBatch<C> extends ArrayList<Abstract2DLinearEstimator.DataPair<C>> implements Estimator.DataBatch<Abstract2DLinearEstimator.DataPair<C>> {
        @Override
        public void addDataPoint(Abstract2DLinearEstimator.DataPair<C> input) {
            this.add(input);
        }
    }

    protected abstract static class LinearFunction<C, TBatch extends Estimator.DataBatch<Abstract2DLinearEstimator.DataPair<C>>> implements Estimator.Model<Long, Long, TBatch> {
        protected final long initialOutput;
        protected float yIntercept;
        protected float slope;
        protected int gatheredSamples = 0;

        public LinearFunction(long initialOutput) {
            this.initialOutput = initialOutput;
        }

        @Override
        public Long predict(Long input) {
            if (this.gatheredSamples == 0) {
                return this.initialOutput;
            }

            return (long) (this.yIntercept + this.slope * input);
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "s=%.2f,y=%.0f", this.slope, this.yIntercept);
        }
    }
}
