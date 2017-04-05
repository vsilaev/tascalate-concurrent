package net.tascalate.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public interface TaskExecutorService extends ExecutorService {

    <T> Promise<T> submit(Callable<T> task);

    <T> Promise<T> submit(Runnable task, T result);

    Promise<?> submit(Runnable task);

}
