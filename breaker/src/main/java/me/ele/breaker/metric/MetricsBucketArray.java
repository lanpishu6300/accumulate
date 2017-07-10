package me.ele.breaker.metric;

import lombok.*;

import java.util.Arrays;

public class MetricsBucketArray {
    private MetricsBucket[] buckets;
    private volatile int currentIndex;
    private int size;
    @Getter
    private MetricSnapshot lastCheck;

    /**
     * fixed capacity
     *
     * @param size
     *            > 2
     */
    MetricsBucketArray(int size) {
        this.size = size;
        this.buckets = new MetricsBucket[size + 1];
        for (int i = 0; i <= size; i++) {
            buckets[i] = new MetricsBucket();
        }
        this.currentIndex = 0;
        this.lastCheck = new MetricSnapshot();
    }

    /**
     * thread not safe (make sure the invoke context is concurrent safe)
     * 
     * @return
     */
    MetricSnapshot calcCheck() {
        long sum = 0L;
        long error = 0L;
        long circuitBreak = 0L;
        int temp = currentIndex == size ? 0 : currentIndex + 1;
        buckets[temp] = new MetricsBucket();
        currentIndex = temp;

        for (int i = 0; i <= size; i++) {
            if (i == currentIndex) {
                continue;
            }

            MetricsBucket bucket = buckets[i];
            error += (bucket.errorCount.get() + bucket.timeoutCount.get());
            circuitBreak += bucket.breakerRejcetCount.get();
            sum += (bucket.errorCount.get() + bucket.timeoutCount.get() + bucket.successCount.get());
        }

        MetricSnapshot check = new MetricSnapshot();
        check.error = (float) error;
        check.total = (float) sum;
        check.circuitBreak = (float) circuitBreak;
        lastCheck = check;
        return check;
    }

    MetricsBucket peek() {
        return buckets[currentIndex];
    }

    @Getter
    class MetricSnapshot {
        private float total;
        private float error;
        private float circuitBreak;

        @Override
        public String toString() {
            return "MetricSnapshot{" +
                    "total=" + total +
                    ", error=" + error +
                    ", circuitBreak=" + circuitBreak +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "MetricsBucketArray{" +
                "buckets=" + Arrays.toString(buckets) +
                ", currentIndex=" + currentIndex +
                ", size=" + size +
                ", lastCheck=" + lastCheck +
                '}';
    }
}
