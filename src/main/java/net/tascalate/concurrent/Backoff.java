package net.tascalate.concurrent;

public interface Backoff {
    long delayMillis(RetryContext context);
}
