package me.ele.breaker.test;

import java.lang.reflect.Method;

import lombok.Getter;
import lombok.Setter;
import me.ele.breaker.CallStatus;
import me.ele.breaker.Fallback;
import me.ele.breaker.Task;


public class BreakerTestTask implements Task {
    private final Fallback fallback;
    @Getter
    private final Method method;
    private final Object[] args;
    @Setter
    @Getter
    private boolean taskThrow = false;
    @Setter
    @Getter
    private CallStatus status;

    public BreakerTestTask(Method method, Object[] args) throws Exception {
        this.fallback = new Fallback(method);
        this.method = method;
        this.args = args;
    }

    @Override
    public Object call() throws Exception {
        if (taskThrow) {
            throw new Exception();
        }
        return "result";
    }

    @Override
    public void cancel() {
    }

    @Override
    public boolean supportsFallback() {
        return fallback.supportsFallback();
    }

    @Override
    public Object callFallback() throws Throwable {
        return fallback.callFallback(args);
    }
}
