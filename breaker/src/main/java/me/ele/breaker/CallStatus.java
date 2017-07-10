package me.ele.breaker;

public enum CallStatus {
    // 非业务异常
    crit,
    // 业务异常
    user_exc,
    // 超时
    timeout,
    // 熔断
    sick,
    // 软超时
    soft_timeout,
    // 降级
    off,
    // 授权不通过
    no_authority,
    // 超出并发
    no_available_thread;
}
