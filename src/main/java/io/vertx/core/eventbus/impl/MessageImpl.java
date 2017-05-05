/*
 * Copyright (c) 2011-2014 The original author or authors
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

package io.vertx.core.eventbus.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.*;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 16/12/20 by zmyer
public class MessageImpl<U, V> implements Message<V> {

    private static final Logger log = LoggerFactory.getLogger(MessageImpl.class);
    //消息编码
    protected MessageCodec<U, V> messageCodec;
    //事件总线对象
    protected EventBusImpl bus;
    //消息发送的目标地址信息
    protected String address;
    //消息应答地址信息
    protected String replyAddress;
    //消息头部
    protected MultiMap headers;
    //消息发送内容
    protected U sentBody;
    //消息应答内容
    protected V receivedBody;
    //标记是否发送
    protected boolean send;

    public MessageImpl() {
    }

    // TODO: 16/12/20 by zmyer
    public MessageImpl(String address, String replyAddress, MultiMap headers, U sentBody,
                       MessageCodec<U, V> messageCodec,
                       boolean send, EventBusImpl bus) {
        this.messageCodec = messageCodec;
        this.address = address;
        this.replyAddress = replyAddress;
        this.headers = headers;
        this.sentBody = sentBody;
        this.send = send;
        this.bus = bus;
    }

    // TODO: 16/12/20 by zmyer
    protected MessageImpl(MessageImpl<U, V> other) {
        this.bus = other.bus;
        this.address = other.address;
        this.replyAddress = other.replyAddress;
        this.messageCodec = other.messageCodec;
        if (other.headers != null) {
            List<Map.Entry<String, String>> entries = other.headers.entries();
            this.headers = new CaseInsensitiveHeaders();
            for (Map.Entry<String, String> entry : entries) {
                this.headers.add(entry.getKey(), entry.getValue());
            }
        }
        if (other.sentBody != null) {
            this.sentBody = other.sentBody;
            this.receivedBody = messageCodec.transform(other.sentBody);
        }
        this.send = other.send;
    }

    // TODO: 16/12/20 by zmyer
    public MessageImpl<U, V> copyBeforeReceive() {
        return new MessageImpl<>(this);
    }

    @Override
    public String address() {
        return address;
    }

    @Override
    public MultiMap headers() {
        // Lazily decode headers
        if (headers == null) {
            headers = new CaseInsensitiveHeaders();
        }
        return headers;
    }

    // TODO: 16/12/20 by zmyer
    @Override
    public V body() {
        if (receivedBody == null && sentBody != null) {
            receivedBody = messageCodec.transform(sentBody);
        }
        return receivedBody;
    }

    @Override
    public String replyAddress() {
        return replyAddress;
    }

    // TODO: 16/12/20 by zmyer
    @Override
    public void fail(int failureCode, String message) {
        if (replyAddress != null) {
            //应答发送失败消息
            sendReply(bus.createMessage(true, replyAddress, null,
                    new ReplyException(ReplyFailure.RECIPIENT_FAILURE, failureCode, message), null), null, null);
        }
    }

    @Override
    public void reply(Object message) {
        reply(message, new DeliveryOptions(), null);
    }

    @Override
    public <R> void reply(Object message, Handler<AsyncResult<Message<R>>> replyHandler) {
        reply(message, new DeliveryOptions(), replyHandler);
    }

    @Override
    public void reply(Object message, DeliveryOptions options) {
        reply(message, options, null);
    }

    // TODO: 16/12/25 by zmyer
    @Override
    public <R> void reply(Object message, DeliveryOptions options, Handler<AsyncResult<Message<R>>> replyHandler) {
        if (replyAddress != null) {
            sendReply(bus.createMessage(true, replyAddress, options.getHeaders(), message, options.getCodecName()), options, replyHandler);
        }
    }

    public void setReplyAddress(String replyAddress) {
        this.replyAddress = replyAddress;
    }

    public boolean send() {
        return send;
    }

    public MessageCodec<U, V> codec() {
        return messageCodec;
    }

    public void setBus(EventBusImpl bus) {
        this.bus = bus;
    }

    // TODO: 16/12/20 by zmyer
    protected <R> void sendReply(MessageImpl msg, DeliveryOptions options, Handler<AsyncResult<Message<R>>> replyHandler) {
        if (bus != null) {
            //向事件总线对象发送消息
            bus.sendReply(msg, this, options, replyHandler);
        }
    }

    protected boolean isLocal() {
        return true;
    }
}
