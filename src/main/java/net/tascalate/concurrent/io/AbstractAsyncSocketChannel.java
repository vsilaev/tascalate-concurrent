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
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import net.tascalate.concurrent.Promise;

public class AbstractAsyncSocketChannel<C extends AbstractAsyncSocketChannel<C>> 
    extends AsynchronousSocketChannel 
    implements AsyncByteChannel {
    
    private final AsynchronousSocketChannel delegate;
    
    protected AbstractAsyncSocketChannel(AsynchronousSocketChannel delegate) {
        super(delegate.provider());
        this.delegate = delegate;
    }
    
    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public <T> T getOption(SocketOption<T> name) throws IOException {
        return delegate.getOption(name);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return delegate.supportedOptions();
    }

    @Override
    public C bind(SocketAddress local) throws IOException {
        delegate.bind(local);
        return self();
    }

    @Override
    public <T> C setOption(SocketOption<T> name, T value) throws IOException {
        delegate.setOption(name, value);
        return self();
    }

    @Override
    public C shutdownInput() throws IOException {
        delegate.shutdownInput();
        return self();
    }

    @Override
    public C shutdownOutput() throws IOException {
        delegate.shutdownOutput();
        return self();
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        return delegate.getRemoteAddress();
    }

    @Override
    public <A> void connect(SocketAddress remote, A attachment, CompletionHandler<Void, ? super A> handler) {
        delegate.connect(remote, attachment, handler);
    }

    @Override
    public Promise<Void> connect(SocketAddress remote) {
        return (Promise<Void>)delegate.connect(remote);
    }

    @Override
    public Promise<Integer> read(ByteBuffer dst) {
        return (Promise<Integer>)delegate.read(dst);
    }

    @Override
    public <A> void read(ByteBuffer dst, long timeout, TimeUnit unit, A attachment,
                         CompletionHandler<Integer, ? super A> handler) {
        delegate.read(dst, timeout, unit, attachment, handler);
    }

    @Override
    public <A> void read(ByteBuffer[] dsts, int offset, int length, long timeout, TimeUnit unit, A attachment,
                         CompletionHandler<Long, ? super A> handler) {
        delegate.read(dsts, offset, length, timeout, unit, attachment, handler);
    }

    @Override
    public Promise<Integer> write(ByteBuffer src) {
        return (Promise<Integer>)delegate.write(src);
    }

    @Override
    public <A> void write(ByteBuffer src, long timeout, TimeUnit unit, A attachment,
                          CompletionHandler<Integer, ? super A> handler) {
        delegate.write(src, timeout, unit, attachment, handler);
    }

    @Override
    public <A> void write(ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit, A attachment,
                          CompletionHandler<Long, ? super A> handler) {
        delegate.write(srcs, offset, length, timeout, unit, attachment, handler);
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return delegate.getLocalAddress();
    }
    
    @SuppressWarnings("unchecked")
    private C self() {
        return (C)this;
    }
}
