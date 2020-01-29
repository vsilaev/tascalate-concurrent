package net.tascalate.concurrent.var;

import java.util.List;
import java.util.concurrent.Callable;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.TaskExecutorService;

public class ContextualTaskExecutorService<D extends TaskExecutorService> 
    extends ContextualExecutorService<D> 
    implements TaskExecutorService {
    protected ContextualTaskExecutorService(D delegate, 
                                            List<ContextVar<?>> contextVars, 
                                            ContextVar.Propagation propagation, 
                                            List<Object> capturedContext) {
        super(delegate, contextVars, propagation, capturedContext);
    }
    
    @Override
    public <T> Promise<T> submit(Callable<T> task) {
        return (Promise<T>)super.submit(task);
    }

    @Override
    public <T> Promise<T> submit(Runnable task, T result) {
        return (Promise<T>)super.submit(task, result);
    }

    @Override
    public Promise<?> submit(Runnable task) {
        return (Promise<?>)super.submit(task);
    }
    
}
