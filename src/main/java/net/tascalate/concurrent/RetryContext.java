package net.tascalate.concurrent;

public class RetryContext {
    private final RetryPolicy policy;
    private final int retry;
    private final long lastCallDuartion;
    private final Throwable lastThrowable;
    
    public RetryContext(RetryPolicy policy, int retry, long lastCallDuration, Throwable lastThrowable) {
        this.policy = policy;
        this.retry = retry;
        this.lastCallDuartion = lastCallDuration; 
        this.lastThrowable = lastThrowable;
    }
    

    public static RetryContext initial(RetryPolicy policy) {
        return new RetryContext(policy, 0, 0, null);
    }
    
    public long executionDelayMillis() {
        if (policy.shouldContinue(this) ) {
            return policy.delayInMillis(this);
        } else {
            return -1;
        }
    }
    
    public int getRetryCount() {
        return retry;
    }
    
    public long getLastCallDuration() {
        return lastCallDuartion;
    }

    public Throwable getLastThrowable() {
        return lastThrowable;
    }
    
    public RetryContext getNextRetry(long callDuration) {
        return new RetryContext(policy, retry + 1, callDuration, null);
    }
    
    public RetryContext getNextRetry(long callDuration, Throwable throwable) {
        return new RetryContext(policy, retry + 1, callDuration, throwable);
    }

}
