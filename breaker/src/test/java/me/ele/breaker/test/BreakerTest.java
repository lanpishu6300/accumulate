package me.ele.breaker.test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.ele.breaker.*;
import me.ele.breaker.exception.BreakerException;
import me.ele.breaker.exception.NoAvailableWorkerException;
import me.ele.breaker.strategy.QueueStrategy;
import me.ele.breaker.strategy.SemaphoreStrategy;


import org.testng.Assert;
import org.testng.annotations.Test;

public class BreakerTest {
    private static final String SERVICE = "service";
    private static final int THREAD_POOL_SIZE = 1;
    private static final int TIMEOUT = 100;
    private static final List<Class<?>> IFACES = Arrays.asList(TestInterface.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    private final TaskExecutor semaPhoreBreaker = new Breaker(new BreakerProperty(SERVICE, THREAD_POOL_SIZE, TIMEOUT, new SemaphoreStrategy(10), IFACES));
    private final TaskExecutor queueBreaker = new Breaker(new BreakerProperty(SERVICE, THREAD_POOL_SIZE, TIMEOUT, new QueueStrategy(), IFACES));
    private Task tsk;

    public BreakerTest() throws BreakerException {
    }

    @Test(enabled = true)
    public void testBreakerExecute() throws Throwable {
        tsk = new BreakerTestTask(TestInterface.class.getMethod("testWithFallBack"), null);
        semaPhoreBreaker.execute(tsk);
        String result = (String) semaPhoreBreaker.execute(tsk);
        Assert.assertEquals(result, "result");
    }

    @Test(enabled = true)
    public void testBreakerMetric() throws NoSuchMethodException, SecurityException,Exception {
        tsk = new BreakerTestTask(TestInterface.class.getMethod("testWithFallBack"), null);
        ((BreakerTestTask) tsk).setTaskThrow(true);
        String fallbackMessage = "";
        while (tsk.getStatus() == null) {
            try {
                fallbackMessage = (String) semaPhoreBreaker.execute(tsk);
            } catch (Throwable e) {
            }
        }
        Assert.assertEquals(fallbackMessage, "fallback");
        Assert.assertEquals(tsk.getStatus(), CallStatus.sick);
    }

    @Test(enabled = true)
    public void testSemaphoreStrategy() throws Exception {
        tsk = new BreakerTestTask(TestInterface.class.getMethod("testWithoutFallBack"), null);
        for (int i = 0; i < 3; i++) {
            executor.submit(() -> {
                boolean stop = false;
                while (!stop) {
                    try {
                        semaPhoreBreaker.execute(tsk);
                    } catch (NoAvailableWorkerException t) {
                        stop = true;
                    } catch (Throwable t) {
                    }
                }
                Assert.assertEquals(tsk.getStatus(), CallStatus.no_available_thread);
            });
        }
    }

    @Test(enabled = true)
    public void testQueueStrategy() throws Exception {
        tsk = new BreakerTestTask(TestInterface.class.getMethod("testWithoutFallBack"), null);
        tsk.setStatus(null);
        long current = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
           // executor.submit(() -> {
           //     boolean stop = false;
           //     while (!stop) {
                    try {
                        queueBreaker.execute(tsk);
                    } catch (NoAvailableWorkerException t) {
             //           stop = true;
                    } catch (Throwable t) {
                    }
              //  }
           // });
        }



        Thread.sleep(3000);
        System.out.println("total cost " + (System.currentTimeMillis() - current)/1000);
        Assert.assertEquals(tsk.getStatus(), null);
    }
}
