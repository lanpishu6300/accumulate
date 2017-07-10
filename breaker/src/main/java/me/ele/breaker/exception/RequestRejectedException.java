package me.ele.breaker.exception;

@SuppressWarnings("serial")
public class RequestRejectedException extends BreakerException {
    public RequestRejectedException(String message) {
        super(message);
    }

    public RequestRejectedException(String message, Throwable cause) {
        super(message, cause);
    }

    public RequestRejectedException(Throwable cause) {
        super(cause);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
