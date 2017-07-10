package me.ele.breaker;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.*;

import me.ele.breaker.concurrent.ExecutorFactory;
import me.ele.breaker.exception.NoAvailableWorkerException;
import me.ele.breaker.exception.RequestRejectedException;
import me.ele.breaker.exception.RequestTimeoutException;
import me.ele.breaker.metric.BreakerMetrics;
import me.ele.breaker.strategy.QueueStrategy;
import me.ele.breaker.strategy.SemaphoreStrategy;
import me.ele.breaker.strategy.ThreadPoolStrategy;


public class Breaker implements TaskExecutor {
    private final BreakerProperty property;
    private final ThreadPoolStrategy strategy;

    private Map<Method, BreakerMetrics> metrics = new HashMap<Method, BreakerMetrics>();
    private ExecutorService service;
    private Thread calculator;

    private static int common_pool_size = 0;

    public Breaker(BreakerProperty property) {
        this.property = property;
        this.strategy = property.getStrategy();
        initThreadPool();
        initBreakerMetric();
        initCalculator();
    }

    private void initThreadPool() {
        switch (strategy.id()) {
        case QueueStrategy.ID:
            service = ExecutorFactory.newThreadPool(property.getPoolSize(), Executors.defaultThreadFactory());
            break;
        case SemaphoreStrategy.ID:
            common_pool_size += property.getPoolSize();
            service = ExecutorFactory.commonPool(common_pool_size);
            break;
        default:
            throw new IllegalArgumentException("thread pool strategy is wrong, can't create thread pool.");
        }
    }

    private void initBreakerMetric() {
        property.getIfaces().forEach(iface -> {
            for (Method method : iface.getMethods()) {
                metrics.put(method, new BreakerMetrics(iface.getName() + "." + method.getName(), property));
            }
        });
    }

    private void initCalculator() {
        this.calculator = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(property.getCheckWindowInMillis());
                    } catch (InterruptedException e) {
                        break;
                    }

                    metrics.values().forEach((metric) -> {
                        metric.calc();
                    });
                }
            }
        });
        this.calculator.start();
    }

    @Override
    public Object execute(Task task) throws Throwable {
        BreakerMetrics metric = metrics.get(task.getMethod());
        if (metric.isOpen()) {
            metric.increment(BreakerStatus.BREAKER_REJECT);
            task.setStatus(CallStatus.sick);
            if (task.supportsFallback()) {
                return task.callFallback();
            } else {
                throw new RequestRejectedException(String.format("Service(%s)'s circuit-breaker is open!", property.getService()));
            }
        }

        if (strategy.isBusy()) {
            task.setStatus(CallStatus.no_available_thread);
            if (task.supportsFallback()) {
                return task.callFallback();
            } else {
                throw new NoAvailableWorkerException(String.format("Service(%s) has no available worker in client!", property.getService()));
            }
        } else {
            try {
                return callCommand(task, metric);
            } finally {
                strategy.release();
            }
        }
    }

    private Object callCommand(Task task, BreakerMetrics metric) throws Throwable {
        Future<?> result = null;
        try {
            result = service.submit(task);
            long timeout = task.getTimeoutInMillis();
            Object ret = result.get(timeout > 0 ? timeout : property.getTimeout(), TimeUnit.MILLISECONDS);
            metric.increment(BreakerStatus.SUCCESS);
            if (metric.inTestPhase()) {
                metric.singleTestPass(true);
            }
            return ret;
        } catch (InterruptedException | TimeoutException e) {
            metric.increment(BreakerStatus.TIMEOUT);
            if (result != null) {
                result.cancel(true);
            }
            task.cancel();
            task.setStatus(CallStatus.timeout);
            throw new RequestTimeoutException(String.format("Service(%s) occurs a request execution timeout!", property.getService()));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ServiceException) {
                metric.increment(BreakerStatus.SUCCESS);
                if (metric.inTestPhase()) {
                    metric.singleTestPass(true);
                }
            } else {
                metric.increment(BreakerStatus.ERROR);
            }
            throw e.getCause();
        } finally {
            if (metric.inTestPhase()) {
                metric.singleTestPass(false);
            }
        }
    }

    public void stop() {
        this.calculator.interrupt();
    }

    @SuppressWarnings("serial")
//    @Override
    public Object dumpInfo() {
        return new LinkedHashMap<String, Object>() {
            {
//                put("strategy", IServiceDumper.dumpInfo(strategy));
                put("metrics", new TreeMap<String, Object>() {
                    {
                        metrics.forEach((key, value) -> put(String.format("%s$%s", key.getDeclaringClass().getName(), key.getName()), value.dumpInfo()));
                    }
                });
            }
        };
    }
}
