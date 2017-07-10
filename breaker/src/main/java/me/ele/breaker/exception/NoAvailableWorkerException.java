package me.ele.breaker.exception;

@SuppressWarnings("serial")
public class NoAvailableWorkerException extends BreakerException {
    public NoAvailableWorkerException(String message) {
        super(message);
    }

    public NoAvailableWorkerException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoAvailableWorkerException(Throwable cause) {
        super(cause);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
