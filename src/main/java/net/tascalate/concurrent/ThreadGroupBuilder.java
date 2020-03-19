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

public final class ThreadGroupBuilder {
    
    private String name;
    private Boolean isDaemon;
    private ThreadGroup parent;
    private Integer maxPriority;
    
    ThreadGroupBuilder() {
    }
    
    public ThreadGroupBuilder withName(String name) {
        this.name = name;
        return this;
    }
    
    public ThreadGroupBuilder withDaemonFlag(boolean isDaemon) {
        this.isDaemon = isDaemon;
        return this;
    }
    
    public ThreadGroupBuilder withDefaultDaemonFlag() {
        this.isDaemon = null;
        return this;
    }

    public ThreadGroupBuilder withMaxPriority(int maxPriority) {
        if (maxPriority < Thread.MIN_PRIORITY || maxPriority > Thread.MAX_PRIORITY ) {
            throw new IllegalArgumentException(String.format(
                "ThreadGroup max priority (%d) must be within [%d..%d]", 
                maxPriority, Thread.MIN_PRIORITY, Thread.MAX_PRIORITY));
        }
        this.maxPriority = maxPriority;
        return this;
    }
    
    public ThreadGroupBuilder withDefaultMaxPriority() {
        this.maxPriority = null;
        return this;
    }
    
    public ThreadGroupBuilder withParent(ThreadGroup parent) {
        this.parent = parent;
        return this;
    }
    
    public ThreadGroup build() {
        ThreadGroup g = new ThreadGroup(parent == null ? Thread.currentThread().getThreadGroup() : parent, name);
        if (null != isDaemon && g.isDaemon() != isDaemon) {
            g.setDaemon(isDaemon);
        }
        if (null != maxPriority && g.getMaxPriority() != maxPriority) { 
            g.setMaxPriority(maxPriority);
        }
        return g;
    }
}
