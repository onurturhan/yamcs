package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.yamcs.logging.Log;
import org.yamcs.parameter.Value;
import org.yamcs.parameter.ValueArray;
import org.yamcs.parameterarchive.ParameterValueArray;
import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.UnsignedLong;

/**
 * One-pass downsampler for time-series data (i.e. numeric archived parameters), where the number of recorded data
 * points are not known upfront.
 * <p>
 * The output is not a bunch of parameter values, but instead a range of values limited to n, which should be fit for
 * inclusion in plots.
 */
public class Downsampler implements Consumer<ParameterValueArray> {

    private static final Log log = new Log(Downsampler.class);
    private static final int DEFAULT_SAMPLE_COUNT = 500;
    private static long DEFAULT_GAP_TIME = 120000;

    private TreeMap<Long, Sample> samplesByTime = new TreeMap<>();
    private long start;
    private long stop;
    private boolean useRawValue;
    private long lastSampleTime;

    public Downsampler(long start, long stop) {
        this(start, stop, DEFAULT_SAMPLE_COUNT, false);
    }

    public Downsampler(long start, long stop, int sampleCount) {
        this(start, stop, sampleCount, false);
    }

    public Downsampler(long start, long stop, int sampleCount, boolean useRawValue) {
        if (start > stop) {
            throw new IllegalArgumentException("start (" + start + ") should be smaller than stop (" + stop + ")");
        }
        this.start = start;
        this.stop = stop;
        this.useRawValue = useRawValue;

        // Initialize intervals
        long step = (stop - start) / sampleCount;
        if (step == 0) {
            step = 1;
        }
        for (long i = start; i < stop; i += step) {
            samplesByTime.put(i, null);
        }
    }

    public void process(org.yamcs.parameter.ParameterValue pval) {
        Value value = useRawValue ? pval.getRawValue() : pval.getEngValue();
        if (value == null) {
            return;
        }

        switch (value.getType()) {
        case DOUBLE:
            process(pval.getGenerationTime(), value.getDoubleValue(), pval.getExpireMills());
            break;
        case FLOAT:
            process(pval.getGenerationTime(), value.getFloatValue(), pval.getExpireMills());
            break;
        case SINT32:
            process(pval.getGenerationTime(), value.getSint32Value(), pval.getExpireMills());
            break;
        case SINT64:
            process(pval.getGenerationTime(), value.getSint64Value(), pval.getExpireMills());
            break;
        case UINT32:
            process(pval.getGenerationTime(), value.getUint32Value() & 0xFFFFFFFFL, pval.getExpireMills());
            break;
        case UINT64:
            process(pval.getGenerationTime(), value.getUint64Value(), pval.getExpireMills());
            break;
        default:
            process(pval.getGenerationTime(), Double.NaN, pval.getExpireMills());
        }
    }

    @Override
    public void accept(ParameterValueArray t) {
        ValueArray va = useRawValue ? t.getRawValues() : t.getEngValues();
        long[] timestamps = t.getTimestamps();
        ParameterStatus[] statuses = t.getStatuses();

        // Consider expireMillis, but only from the last value
        long expireMillis = -1;
        if (statuses != null && statuses.length > 0) {
            ParameterStatus lastStatus = statuses[statuses.length - 1];
            if (lastStatus != null && lastStatus.hasExpireMillis()) {
                expireMillis = lastStatus.getExpireMillis();
            }
        }

        int n = timestamps.length;
        Type type = useRawValue ? t.getRawType() : t.getEngType();

        switch (type) {
        case FLOAT:
            float[] fv = va.getFloatArray();
            for (int i = 0; i < n; i++) {
                process(timestamps[i], fv[i], expireMillis);
            }
            break;
        case DOUBLE:
            double[] dv = va.getDoubleArray();
            for (int i = 0; i < n; i++) {
                process(timestamps[i], dv[i], expireMillis);
            }
            break;
        case UINT32:
            int[] iv = va.getIntArray();
            for (int i = 0; i < n; i++) {
                process(timestamps[i], iv[i] & 0xFFFFFFFFL, expireMillis);
            }
            break;
        case SINT32:
            iv = va.getIntArray();
            for (int i = 0; i < n; i++) {
                process(timestamps[i], iv[i], expireMillis);
            }
            break;
        case UINT64:
            long[] lv = va.getLongArray();
            for (int i = 0; i < n; i++) {
                process(timestamps[i], UnsignedLong.toDouble(lv[i]), expireMillis);
            }
            break;
        case SINT64:
            lv = va.getLongArray();
            for (int i = 0; i < n; i++) {
                process(timestamps[i], lv[i], expireMillis);
            }
            break;
        case NONE:
            // No value (for example: pval without raw). Do nothing.
            break;
        default:
            for (int i = 0; i < n; i++) {
                process(timestamps[i], Double.NaN, expireMillis);
            }
        }
    }

    public void process(long time, double value, long expireMillis) {
        if (time > stop || time < start) {
            return;
        }

        Entry<Long, Sample> entry = samplesByTime.floorEntry(time);
        if (entry == null) {
            log.warn("No interval for value {}", value);
            return;
        }

        lastSampleTime = entry.getKey();
        Sample sample = entry.getValue();
        if (sample == null) {
            samplesByTime.put(entry.getKey(), new Sample(entry.getKey(), value, expireMillis));
        } else {
            sample.process(value, expireMillis);
        }
    }

    public List<Sample> collect() {
        if (samplesByTime == null) {
            return Collections.emptyList();
        }
        List<Sample> r = new ArrayList<>(DEFAULT_SAMPLE_COUNT);
        Sample prev = null;
        for (Entry<Long, Sample> e : samplesByTime.entrySet()) {
            Sample s = e.getValue();
            if (s == null) {
                long t = e.getKey();
                if (prev != null) { // Maybe generate a gap
                    long gapTime = (prev.expireMillis != -1) ? prev.expireMillis : DEFAULT_GAP_TIME;
                    if (t - prev.t > gapTime) {
                        r.add(new Sample(t));
                    }
                }
            } else {
                r.add(s);
                prev = s;
            }
        }

        return r;
    }

    public long lastSampleTime() {
        return lastSampleTime;
    }

    /**
     * A cumulative sample that keeps track of a rolling average among others.
     */
    public static class Sample {
        final long t;
        double min;
        double max;
        double avg;
        int n;
        long expireMillis; // Matching the 'last' value for this sample.

        // construct a gap
        Sample(long t) {
            this.t = t;
            min = avg = max = Double.NaN;
            n = 0;
            expireMillis = -1;
        }

        // sample with one value
        public Sample(long t, double value, long expireMillis) {
            this.t = t;
            this.expireMillis = expireMillis;
            min = avg = max = value;
            n = 1;
        }

        public void process(double value, long expireMillis) {
            this.expireMillis = expireMillis;
            if (value < min) {
                min = value;
            }
            if (value > max) {
                max = value;
            }
            n++;
            avg -= (avg / n);
            avg += (value / n);
        }

        @Override
        public String toString() {
            return String.format("%s (min=%s, max=%s, n=%s)", avg, min, max, n);
        }
    }
}
