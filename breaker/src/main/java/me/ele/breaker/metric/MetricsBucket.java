package me.ele.breaker.metric;

import java.util.concurrent.atomic.AtomicLong;

import me.ele.breaker.BreakerStatus;

class MetricsBucket {
    final AtomicLong errorCount = new AtomicLong(0);
    final AtomicLong timeoutCount = new AtomicLong(0);
    final AtomicLong successCount = new AtomicLong(0);
    final AtomicLong breakerRejcetCount = new AtomicLong(0);

    void increment(BreakerStatus type) {
        switch (type) {
        case SUCCESS:
            successCount.getAndIncrement();
            break;
        case ERROR:
            errorCount.getAndIncrement();
            break;
        case TIMEOUT:
            timeoutCount.getAndIncrement();
            break;
        case BREAKER_REJECT:
            breakerRejcetCount.getAndIncrement();
            break;
        default:
            break;
        }
    }

    @Override
    public String toString() {
        return "MetricsBucket{" +
                "errorCount=" + errorCount +
                ", timeoutCount=" + timeoutCount +
                ", successCount=" + successCount +
                ", breakerRejcetCount=" + breakerRejcetCount +
                '}';
    }
}
