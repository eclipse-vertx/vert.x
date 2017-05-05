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

package io.vertx.core.eventbus.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.*;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
// TODO: 16/12/18 by zmyer
public class MessageProducerImpl<T> implements MessageProducer<T> {

    public static final String CREDIT_ADDRESS_HEADER_NAME = "__vertx.credit";
    //vertx对象
    private final Vertx vertx;
    //事件总线对象
    private final EventBus bus;
    //是否发送标记
    private final boolean send;
    //消息生产者地址
    private final String address;
    //待发送对象的挂起队列
    private final Queue<T> pending = new ArrayDeque<>();
    //消息消费者对象
    private final MessageConsumer<Integer> creditConsumer;
    //消息传递配置对象
    private DeliveryOptions options;
    //默认的最大写入量
    private int maxSize = DEFAULT_WRITE_QUEUE_MAX_SIZE;
    private int credits = DEFAULT_WRITE_QUEUE_MAX_SIZE;
    private Handler<Void> drainHandler;

    // TODO: 16/12/18 by zmyer
    public MessageProducerImpl(Vertx vertx, String address, boolean send, DeliveryOptions options) {
        this.vertx = vertx;
        this.bus = vertx.eventBus();
        this.address = address;
        this.send = send;
        this.options = options;
        if (send) {
            String creditAddress = UUID.randomUUID().toString() + "-credit";
            creditConsumer = bus.consumer(creditAddress, msg -> {
                doReceiveCredit(msg.body());
            });
            options.addHeader(CREDIT_ADDRESS_HEADER_NAME, creditAddress);
        } else {
            creditConsumer = null;
        }
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public synchronized MessageProducer<T> deliveryOptions(DeliveryOptions options) {
        if (creditConsumer != null) {
            options = new DeliveryOptions(options);
            options.addHeader(CREDIT_ADDRESS_HEADER_NAME, this.options.getHeaders().get(CREDIT_ADDRESS_HEADER_NAME));
        }
        this.options = options;
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public MessageProducer<T> send(T message) {
        //开始发送消息
        doSend(message, null);
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public <R> MessageProducer<T> send(T message, Handler<AsyncResult<Message<R>>> replyHandler) {
        doSend(message, replyHandler);
        return this;
    }

    @Override
    public MessageProducer<T> exceptionHandler(Handler<Throwable> handler) {
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public synchronized MessageProducer<T> setWriteQueueMaxSize(int s) {
        int delta = s - maxSize;
        maxSize = s;
        credits += delta;
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public synchronized MessageProducer<T> write(T data) {
        if (send) {
            //直接发送消息
            doSend(data, null);
        } else {
            //开始向事件总线中写入消息
            bus.publish(address, data, options);
        }
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public synchronized boolean writeQueueFull() {
        return credits == 0;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public synchronized MessageProducer<T> drainHandler(Handler<Void> handler) {
        this.drainHandler = handler;
        return this;
    }

    @Override
    public String address() {
        return address;
    }

    @Override
    public void end() {
        close();
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public void close() {
        if (creditConsumer != null) {
            creditConsumer.unregister();
        }
    }

    // Just in case user forget to call close()
    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    // TODO: 16/12/18 by zmyer
    private synchronized <R> void doSend(T data, Handler<AsyncResult<Message<R>>> replyHandler) {
        if (credits > 0) {
            credits--;
            if (replyHandler == null) {
                //开始向事件总线对象发送消息
                bus.send(address, data, options);
            } else {
                bus.send(address, data, options, replyHandler);
            }
        } else {
            //将消息插入到挂起队列中
            pending.add(data);
        }
    }

    // TODO: 16/12/18 by zmyer
    private synchronized void doReceiveCredit(int credit) {
        credits += credit;
        while (credits > 0) {
            //从挂起的消息列表中读取第一个消息对象
            T data = pending.poll();
            if (data == null) {
                break;
            } else {
                credits--;
                //开始发送消息
                bus.send(address, data, options);
            }
        }
        final Handler<Void> theDrainHandler = drainHandler;
        if (theDrainHandler != null && credits >= maxSize / 2) {
            this.drainHandler = null;
            vertx.runOnContext(v -> theDrainHandler.handle(null));
        }
    }
}
