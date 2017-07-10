package me.ele.breaker;


import java.lang.reflect.Method;
import java.util.concurrent.Callable;

public interface Task extends Callable<Object> {
    Method getMethod();

    default long getTimeoutInMillis() {
        return 0;
    }

    CallStatus getStatus();

    void setStatus(CallStatus status);

    boolean supportsFallback();

    Object callFallback() throws Throwable;

    void cancel();
}
