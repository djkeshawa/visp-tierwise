package io.github.tierwise.core.bench;

import java.util.Arrays;

/**
 * The percentiles of a set of latency samples, in nanoseconds.
 *
 * <p>A percentile is the nearest-rank value: the sample at position {@code ceil(p * n)} of the sorted samples, counting
 * from one. Every sample is kept, so no percentile is estimated.
 *
 * @param count the number of samples
 * @param p50 the median
 * @param p90 the 90th percentile
 * @param p99 the 99th percentile
 * @param p999 the 99.9th percentile
 * @param max the largest sample
 * @param mean the arithmetic mean
 */
public record LatencySummary(int count, long p50, long p90, long p99, long p999, long max, double mean) {

  /**
   * Summarises the samples. The array is sorted in place.
   *
   * @param nanos the samples, each the duration of one operation in nanoseconds; at least one
   * @return the summary
   * @throws IllegalArgumentException if there is no sample
   */
  public static LatencySummary of(long[] nanos) {
    if (nanos.length == 0) {
      throw new IllegalArgumentException("no sample");
    }
    long[] sorted = nanos.clone();
    Arrays.sort(sorted);
    double sum = 0;
    for (long value : sorted) {
      sum += value;
    }
    return new LatencySummary(sorted.length, rank(sorted, 0.50), rank(sorted, 0.90), rank(sorted, 0.99),
        rank(sorted, 0.999), sorted[sorted.length - 1], sum / sorted.length);
  }

  private static long rank(long[] sorted, double p) {
    int position = (int) Math.ceil(p * sorted.length);
    return sorted[Math.max(0, position - 1)];
  }
}
