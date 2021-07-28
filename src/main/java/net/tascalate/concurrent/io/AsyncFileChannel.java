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
package net.tascalate.concurrent.io;

import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class AsyncFileChannel extends AbstractAsyncFileChannel<AsyncFileChannel> {
    
    protected AsyncFileChannel(AsynchronousFileChannel delegate) {
        super(delegate);
    }
    
    public static AsyncFileChannel open(Path file, ExecutorService executor, OpenOption... options) throws IOException {
        Set<OpenOption> set;
        if (options.length == 0) {
            set = Collections.emptySet();
        } else {
            set = new HashSet<>();
            Collections.addAll(set, options);
        }
        return open(file, executor, set, NO_ATTRIBUTES);
    }

    public static AsyncFileChannel open(Path file,
                                        ExecutorService executor,
                                        Set<? extends OpenOption> options,
                                        FileAttribute<?>... attrs) throws IOException {
        Objects.requireNonNull(executor, "Executor should be specified");
        return new AsyncFileChannel(AsynchronousFileChannel.open(file, options, executor, attrs));
    }
    

    private static final FileAttribute<?>[] NO_ATTRIBUTES = new FileAttribute[0];
}
