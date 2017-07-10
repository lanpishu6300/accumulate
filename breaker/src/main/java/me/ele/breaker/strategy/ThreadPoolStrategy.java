package me.ele.breaker.strategy;


public interface ThreadPoolStrategy   {

    String id();

    boolean isBusy();

    void release();
}
