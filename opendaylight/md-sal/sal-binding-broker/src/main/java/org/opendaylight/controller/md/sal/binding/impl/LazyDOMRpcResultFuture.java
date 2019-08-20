/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.md.sal.binding.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.mdsal.binding.dom.adapter.BindingRpcFutureAware;
import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.yangtools.yang.binding.DataContainer;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

final class LazyDOMRpcResultFuture implements CheckedFuture<DOMRpcResult, DOMRpcException>, BindingRpcFutureAware {

    private final ListenableFuture<RpcResult<?>> bindingFuture;
    private final BindingNormalizedNodeSerializer codec;
    private volatile DOMRpcResult result;

    private LazyDOMRpcResultFuture(final ListenableFuture<RpcResult<?>> delegate,
            final BindingNormalizedNodeSerializer codec) {
        this.bindingFuture = requireNonNull(delegate, "delegate");
        this.codec = requireNonNull(codec, "codec");
    }

    static CheckedFuture<DOMRpcResult, DOMRpcException> create(final BindingNormalizedNodeSerializer codec,
            final ListenableFuture<RpcResult<?>> bindingResult) {
        return new LazyDOMRpcResultFuture(bindingResult, codec);
    }

    @Override
    public ListenableFuture<RpcResult<?>> getBindingFuture() {
        return bindingFuture;
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        return bindingFuture.cancel(mayInterruptIfRunning);
    }

    @Override
    public void addListener(final Runnable listener, final Executor executor) {
        bindingFuture.addListener(listener, executor);
    }

    @Override
    public DOMRpcResult get() throws InterruptedException, ExecutionException {
        if (result != null) {
            return result;
        }
        return transformIfNecessary(bindingFuture.get());
    }

    @Override
    public DOMRpcResult get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException,
            TimeoutException {
        if (result != null) {
            return result;
        }
        return transformIfNecessary(bindingFuture.get(timeout, unit));
    }

    @Override
    public DOMRpcResult checkedGet() {
        try {
            return get();
        } catch (InterruptedException | ExecutionException e) {
            // FIXME: Add exception mapping
            throw Throwables.propagate(e);
        }
    }

    @Override
    public DOMRpcResult checkedGet(final long timeout, final TimeUnit unit) throws TimeoutException {
        try {
            return get(timeout, unit);
        } catch (InterruptedException | ExecutionException e) {
            // FIXME: Add exception mapping
            throw Throwables.propagate(e);
        }
    }

    @Override
    public boolean isCancelled() {
        return bindingFuture.isCancelled();
    }

    @Override
    public boolean isDone() {
        return bindingFuture.isDone();
    }

    private synchronized DOMRpcResult transformIfNecessary(final RpcResult<?> input) {
        if (result == null) {
            result = transform(input);
        }
        return result;
    }

    private DOMRpcResult transform(final RpcResult<?> input) {
        if (input.isSuccessful()) {
            final Object inputData = input.getResult();
            if (inputData instanceof DataContainer) {
                return new DefaultDOMRpcResult(codec.toNormalizedNodeRpcData((DataContainer) inputData));
            } else {
                return new DefaultDOMRpcResult((NormalizedNode<?, ?>) null);
            }
        }
        return new DefaultDOMRpcResult(input.getErrors());
    }

}
