/**
 * Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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

import java.io.IOException;
import java.io.Writer;

class PaddedWriter extends Writer {
    private final Writer delegate;
    private final char[] padding;
    
    private volatile boolean needPadding = false;
    
    public PaddedWriter(Writer delegate, String padding) {
        this.delegate = delegate;
        this.padding = padding.toCharArray();
    }

    private void writePaddingIfNecessary() throws IOException {
        if (needPadding) {
            delegate.write(padding);
            needPadding = false;
        }
    }
    
    @Override
    public void write(int b) throws IOException {
        if (b != 0xD && b!= 0xA) {
            writePaddingIfNecessary();
        }
        if (b == 0xD || b == 0xA) {
            needPadding = true;
        }
        delegate.write(b);
    }

    @Override
    public void write(char[] b, int off, int len) throws IOException {
        writePaddingIfNecessary();
        int from = off;
        boolean needPadding = false;
        for (int idx = off; idx < len; idx++) {
            if (b[idx] == 0xD || b[idx] == 0xA) {
                needPadding = true;
            } else if (needPadding) {
                if (from < idx) {
                    delegate.write(b, from, idx - from + 1);
                }
                delegate.write(padding);
                needPadding = false;
                from = idx + 1;
            }
        }
        if (from < len) {
            delegate.write(b, from, len);
        }
        this.needPadding = needPadding;
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        try (Writer writer = delegate) {
            flush();
        }
    }
}
