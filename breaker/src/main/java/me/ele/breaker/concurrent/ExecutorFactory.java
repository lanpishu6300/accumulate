package me.ele.breaker.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;


/**
 * @author xiehao
 * @since 2016年3月16日
 *
 */
public class ExecutorFactory {

    private static ThreadPoolExecutor commonPool = new ThreadPoolExecutor(1, Executors.defaultThreadFactory(),
            new LinkedBlockingQueue<Runnable>());

    public static ExecutorService commonPool() {
        return commonPool;
    }


    /**
     * 
     * @param poolSize
     *            the thread numbers
     * @return
     */
    public static ExecutorService commonPool(int poolSize) {
        commonPool.setMaxPoolSize(poolSize);
        return commonPool;
    }

    public static ExecutorService newThreadPool(int poolSize, ThreadFactory factory) {
        return Executors.newFixedThreadPool(poolSize, factory);
    }
}
