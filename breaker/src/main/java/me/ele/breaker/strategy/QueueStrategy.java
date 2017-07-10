package me.ele.breaker.strategy;

public class QueueStrategy implements ThreadPoolStrategy {

    public final static String ID = "QUEUE";

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public void release() {
    }

    @Override
    public String id() {
        return ID;
    }
}
