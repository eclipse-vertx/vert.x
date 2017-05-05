/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.datagram.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramPacket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.SocketAddressImpl;

import java.net.InetSocketAddress;


/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
// TODO: 16/12/18 by zmyer
final class DatagramPacketImpl implements DatagramPacket {
    //数据包发送者地址
    private final InetSocketAddress sender;
    //缓冲区对象
    private final Buffer buffer;
    //发送者socket地址
    private SocketAddress senderAddress;

    // TODO: 16/12/18 by zmyer
    DatagramPacketImpl(InetSocketAddress sender, Buffer buffer) {
        this.sender = sender;
        this.buffer = buffer;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public SocketAddress sender() {
        if (senderAddress == null) {
            //如果发送者地址为空,则需要重新创建
            senderAddress = new SocketAddressImpl(sender.getPort(), sender.getAddress().getHostAddress());
        }
        return senderAddress;
    }

    @Override
    public Buffer data() {
        return buffer;
    }
}
