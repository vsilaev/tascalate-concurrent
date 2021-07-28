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
package net.tascalate.concurrent.io;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.NetworkChannel;
import java.nio.channels.spi.AsynchronousChannelProvider;
import java.util.Set;

import net.tascalate.concurrent.Promise;

abstract public class AbstractAsyncServerSocketChannel<S extends AbstractAsyncServerSocketChannel<S,C>, C extends AsynchronousSocketChannel> 
    /*extends AsynchronousServerSocketChannel*/
    implements AsyncChannel, NetworkChannel {
    
    private final AsynchronousServerSocketChannel delegate;
    protected AbstractAsyncServerSocketChannel(AsynchronousServerSocketChannel delegate) {
        /*super(delegate.provider());*/
        this.delegate = delegate;
    }
    
    public AsynchronousChannelProvider provider() {
        return delegate.provider();
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
    
    public S bind(SocketAddress local) throws IOException {
        return bind(local,  0);
    }

    //@Override
    public S bind(SocketAddress local, int backlog) throws IOException {
        delegate.bind(local, backlog);
        return self();
    }

    @Override
    public <T> S setOption(SocketOption<T> name, T value) throws IOException {
        delegate.setOption(name, value);
        return self();
    }
    
    /*
    @Override
    @Deprecated
    public Promise<AsynchronousSocketChannel> accept() {
        @SuppressWarnings("unchecked")
        Promise<AsynchronousSocketChannel> result = (Promise<AsynchronousSocketChannel>)(Promise<?>)accept(0);
        return result;
    }
    */
    
    public Promise<C> accept() {
        AsyncResult<C> asyncResult = new AsyncResult<>();
        accept(null, asyncResult.handler);
        return asyncResult;
    }
    
    /*
    @Override
    @Deprecated
    public <A> void accept(A attachment, CompletionHandler<AsynchronousSocketChannel, ? super A> handler) {
        accept(attachment, handler);
    }
    */
    
    public <A> void accept(A attachment, CompletionHandler<? super C, ? super A> handler) {
        if (null == handler) {
            delegate.accept(attachment, null);
            return;
        }
        delegate.accept(attachment, new CompletionHandler<AsynchronousSocketChannel, A>() {
            @Override
            public void completed(AsynchronousSocketChannel result, A attachment) {
                handler.completed(wrap(result), attachment);
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                handler.failed(exc, attachment);
            }
        });
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return delegate.getLocalAddress();
    }      
    
    abstract protected C wrap(AsynchronousSocketChannel channel);
    
    @SuppressWarnings("unchecked")
    private S self() {
        return (S)this;
    }
}

