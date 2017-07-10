package me.ele.breaker;


public interface TaskExecutor {
    Object execute(Task task) throws Throwable;
}
