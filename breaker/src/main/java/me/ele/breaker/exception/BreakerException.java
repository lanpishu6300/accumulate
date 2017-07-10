package me.ele.breaker.exception;


@SuppressWarnings("serial")
public class BreakerException extends Exception {
    public BreakerException(String message) {
        super(message);
    }

    public BreakerException(String message, Throwable cause) {
        super(message, cause);
    }

    public BreakerException(Throwable cause) {
        super(cause);
    }
}
