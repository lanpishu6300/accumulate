package me.ele.breaker;

import java.util.List;

import lombok.Data;
import me.ele.breaker.exception.BreakerException;
import me.ele.breaker.strategy.ThreadPoolStrategy;

@Data
public class BreakerProperty {
    private final String service;
    private final int poolSize;
    private final long timeout;
    private final ThreadPoolStrategy strategy;
    private final List<Class<?>> ifaces;
    private float errorPercentageThreshold = 0.5f;
    private long checkWindowInMillis = 30l;
    private long calculateWindowInMillis = 30l;
    private int bucketNumber = 10;
    private int requestCountThreshold = 60;
    private long singleTestWindowInMillis = 20000l;

    public BreakerProperty(String service, int size, long timeout, ThreadPoolStrategy strategy, List<Class<?>> ifaces) throws BreakerException {
        this.service = service;
        if (size < 1) {
            throw new BreakerException("Pool size should be bigger than 0!");
        }

        this.poolSize = size;
        if (timeout < 1) {
            throw new BreakerException("Timeout should be bigger than 0!");
        }
        this.timeout = timeout;
        this.strategy = strategy;
        this.ifaces = ifaces;
    }
}
