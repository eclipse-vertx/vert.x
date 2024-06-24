/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.vertx.core.http.impl.HttpRequestHead;

public class HttpHeaderWriteContext {

    private HttpRequestHead request;

    private boolean chunked;

    private io.netty.buffer.ByteBuf buf;

    private boolean end;

    private boolean connect;

    public HttpRequestHead getRequest() {
        return request;
    }

    public boolean getChunked() {
        return chunked;
    }

    public io.netty.buffer.ByteBuf getBuf() {
        return buf;
    }

    public boolean getEnd() {
        return end;
    }

    public boolean getConnect() {
        return connect;
    }

    public void setRequest(HttpRequestHead request) {
        this.request = request;
    }

    public void setChunked(boolean chunked) {
        this.chunked = chunked;
    }

    public void setBuf(io.netty.buffer.ByteBuf buf) {
        this.buf = buf;
    }

    public void setEnd(boolean end) {
        this.end = end;
    }

    public void setConnect(boolean connect) {
        this.connect = connect;
    }

    public HttpHeaderWriteContext(HttpRequestHead request, boolean chunked,
            io.netty.buffer.ByteBuf buf, boolean end, boolean connect) {
        this.request = request;
        this.chunked = chunked;
        this.buf = buf;
        this.end = end;
        this.connect = connect;
    }
}
