package me.ele.breaker.strategy;

import java.util.LinkedHashMap;
import java.util.concurrent.Semaphore;
public class SemaphoreStrategy implements ThreadPoolStrategy {

    public final static String ID = "SEMAPHORE";

    private final Semaphore semaphore;

    public SemaphoreStrategy(int poolSize) {
        semaphore = new Semaphore(poolSize);
    }

    @Override
    public boolean isBusy() {
        return !semaphore.tryAcquire();
    }

    @Override
    public void release() {
        semaphore.release();
    }

    @SuppressWarnings("serial")

    public Object dumpInfo() {
        return new LinkedHashMap<String, Object>() {
            {
                put("availablePermits", semaphore.availablePermits());
            }
        };
    }

    @Override
    public String id() {
        return ID;
    }
}
