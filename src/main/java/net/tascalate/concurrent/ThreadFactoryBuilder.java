/**
 * Copyright 2015-2020 Valery Silaev (http://vsilaev.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.tascalate.concurrent;

import java.lang.Thread.UncaughtExceptionHandler;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class ThreadFactoryBuilder {
    private static final AtomicInteger POOL_COUNTER = new AtomicInteger();
    private static final String DEFAULT_NAME_FORMAT = "pool-%3$d-thread-%1$d";

    private String nameFormat = DEFAULT_NAME_FORMAT;
    private ThreadGroup threadGroup = null;
    private ClassLoader contextClassLoader = null;
    private Boolean isDaemon = null;
    private boolean isPriviledged = false;
    private Integer priority = null;
    private UncaughtExceptionHandler uncaughtExceptionHandler = null;

    protected ThreadFactoryBuilder() {}
    
    public ThreadFactoryBuilder withNameFormat(String nameFormat) {
        this.nameFormat = nameFormat == null || nameFormat.isEmpty() ? DEFAULT_NAME_FORMAT : nameFormat;
        return this;
    }
    
    public ThreadFactoryBuilder withDefaultNameFormat() {
        return withNameFormat(DEFAULT_NAME_FORMAT);
    }
    
    public ThreadFactoryBuilder withThreadGroup(ThreadGroup threadGroup) {
        this.threadGroup = threadGroup;
        return this;
    }

    public ThreadFactoryBuilder withContextClassLoader(ClassLoader contextClassLoader) {
        this.contextClassLoader = contextClassLoader;
        return this;
    }
    
    public ThreadFactoryBuilder withPriviledgedAccess(boolean isPriviledged) {
        this.isPriviledged = isPriviledged;
        return this;
    }
    
    public ThreadFactoryBuilder withDaemonFlag(boolean daemon) {
        this.isDaemon = daemon;
        return this;
    }
    
    public ThreadFactoryBuilder withDefaultDaemonFlag() {
        this.isDaemon = null;
        return this;
    }

    public ThreadFactoryBuilder withPriority(int priority) {
        if (priority < Thread.MIN_PRIORITY || priority > Thread.MAX_PRIORITY ) {
            throw new IllegalArgumentException(String.format(
                "Thread priority (%d) must be within [%d..%d]", 
                priority, Thread.MIN_PRIORITY, Thread.MAX_PRIORITY));
        }
        this.priority = priority;
        return this;
    }
    
    public ThreadFactoryBuilder withDefaultPriority() {
        this.priority = null;
        return this;
    }
    
    public ThreadFactoryBuilder withUncaughtExceptionHandler(UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = Objects.requireNonNull(uncaughtExceptionHandler);
        return this;
    }

    public ThreadFactory build() {
        return 
            makeConfigured(
                makePriviledged(
                    makeDefault(POOL_COUNTER.getAndIncrement(), nameFormat, threadGroup), 
                    isPriviledged, contextClassLoader
                ), 
                isDaemon, priority, uncaughtExceptionHandler
            );
    }
    
    protected Thread createThreadInstance(ThreadGroup threadGroup, Runnable runnable, String name) {
        return new Thread(threadGroup, runnable, name);
    }
    
    protected ThreadFactory makeDefault(int poolCounter, String nameFormat, ThreadGroup threadGroup) {
        ThreadGroup actualThreadGroup;
        if (null == threadGroup) {
            SecurityManager sm = System.getSecurityManager();
            if (null != sm) {
                actualThreadGroup = sm.getThreadGroup();
            } else {
                actualThreadGroup = Thread.currentThread().getThreadGroup();
            }
        } else {
            actualThreadGroup = threadGroup;
        }
        AtomicInteger threadCounter = new AtomicInteger(0);
        return r -> createThreadInstance(
            actualThreadGroup, r,
            String.format(Locale.getDefault(), nameFormat, threadCounter.getAndIncrement(), actualThreadGroup.getName(), poolCounter)
        );
    }
    
    protected ThreadFactory makePriviledged(ThreadFactory origin, boolean isPriviledged, ClassLoader contextClassLoader) {
        if (isPriviledged) {
            ClassLoader actualClassLoader;
            if (null == contextClassLoader) {
                SecurityManager sm = System.getSecurityManager();
                if (sm != null) {
                    // Fail fast
                    sm.checkPermission(new RuntimePermission("getClassLoader"));
                }
                actualClassLoader = Thread.currentThread().getContextClassLoader();
            } else {
                actualClassLoader = contextClassLoader;
            }
            AccessControlContext ctx = AccessController.getContext();
            return makeContextual(
                origin, actualClassLoader, r -> () -> AccessController.doPrivileged(
                    new PrivilegedAction<Void>() {
                        public Void run() {
                            r.run();
                            return null;
                        }
                    }, ctx)
                );
        } else {
            return makeContextual(origin, contextClassLoader);
        }
    }
    
    private ThreadFactory makeContextual(ThreadFactory origin, ClassLoader contextClassLoader) {
        return makeContextual(origin, contextClassLoader, Function.identity());
    }
    
    protected ThreadFactory makeContextual(ThreadFactory origin, 
                                           ClassLoader contextClassLoader, 
                                           Function<? super Runnable, ? extends Runnable> wrapper) {
        
        if (null == contextClassLoader) {
            return origin;
        } else {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                // Fail fast
                sm.checkPermission(new RuntimePermission("setContextClassLoader"));
            }
            return r -> {
                Runnable contextualRunnable = () -> {
                    Thread.currentThread().setContextClassLoader(contextClassLoader);
                     r.run();
                };
                return origin.newThread(wrapper.apply(contextualRunnable)); 
            };
        }
    }
    
    protected ThreadFactory makeConfigured(ThreadFactory origin, 
                                           Boolean isDaemon, 
                                           Integer priority, 
                                           UncaughtExceptionHandler uncaughtExceptionHandler) {
        
        if (null != isDaemon && null == priority && null == uncaughtExceptionHandler) {
            return origin;
        } else {
            return r -> {
              Thread t = origin.newThread(r);
              if (null != priority && priority != t.getPriority()) {
                  t.setPriority(priority);
              }
              if (null != isDaemon && isDaemon != t.isDaemon()) {
                  t.setDaemon(isDaemon);
              }
              if (null != uncaughtExceptionHandler) {
                  t.setUncaughtExceptionHandler(uncaughtExceptionHandler);
              }
              return t;
            };
        }
    }

}
