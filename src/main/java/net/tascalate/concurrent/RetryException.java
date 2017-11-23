package net.tascalate.concurrent;

public class RetryException extends Exception {
    private static final long serialVersionUID = 1L;
    
    private final int retry;
    private final long lastCallDuration;
    
    public RetryException() {
        retry = 0;
        lastCallDuration = 0;
    }
    
    public RetryException(int retry, long lastCallDuration, Throwable lastThrowable) {
        super(lastThrowable);
        this.retry = retry;
        this.lastCallDuration = lastCallDuration;
    }
    
    public int getRetryCount() {
        return retry;
    }
    
    public long getLastCallDuration() {
        return lastCallDuration;
    }
}
