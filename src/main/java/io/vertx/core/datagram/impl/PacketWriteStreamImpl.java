/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.datagram.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.PacketWritestream;

/**
 * A write stream for packets.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
// TODO: 16/12/18 by zmyer
class PacketWriteStreamImpl implements PacketWritestream, Handler<AsyncResult<DatagramSocket>> {
    //数据报文socket对象
    private DatagramSocketImpl datagramSocket;
    //异常处理对象
    private Handler<Throwable> exceptionHandler;
    //端口号
    private final int port;
    //主机名
    private final String host;

    // TODO: 16/12/18 by zmyer
    PacketWriteStreamImpl(DatagramSocketImpl datagramSocket, int port, String host) {
        this.datagramSocket = datagramSocket;
        this.port = port;
        this.host = host;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public void handle(AsyncResult<DatagramSocket> event) {
        if (event.failed() && exceptionHandler != null) {
            //开始处理异常信息
            exceptionHandler.handle(event.cause());
        }
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public PacketWritestream exceptionHandler(Handler<Throwable> handler) {
        exceptionHandler = handler;
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public PacketWritestream write(Buffer data) {
        //开始发送报文
        datagramSocket.send(data, port, host, this);
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public PacketWritestream setWriteQueueMaxSize(int maxSize) {
        return this;
    }

    @Override
    public boolean writeQueueFull() {
        return false;
    }

    @Override
    public PacketWritestream drainHandler(Handler<Void> handler) {
        return this;
    }

    @Override
    public void end() {
    }
}
