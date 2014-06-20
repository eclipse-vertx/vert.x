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

package org.vertx.java.core.eventbus.impl;

import io.netty.util.CharsetUtil;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.ReplyException;
import org.vertx.java.core.eventbus.ReplyFailure;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.net.impl.ServerID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BaseMessage<T> implements Message<T> {

  protected T body;
  protected ServerID sender;
  protected EventBusImpl bus;
  protected String address;
  protected String replyAddress;
  protected boolean send; // Is it a send or a publish?

  protected BaseMessage(boolean send, String address, T body) {
    this.send = send;
    this.body = body;
    this.address = address;
  }

  @Override
  public String address() {
    return address;
  }

  @Override
  public T body() {
    return body;
  }

  @Override
  public String replyAddress() {
    return replyAddress;
  }

  @Override
  public void reply() {
    sendReply(EventBusImpl.createMessage(true, replyAddress, null), null);
  }

  @Override
  public void reply(Object message) {
    reply(message, null);
  }

  @Override
  public <R> void reply(Handler<Message<R>> replyHandler) {
    sendReply(EventBusImpl.createMessage(true, replyAddress, null), replyHandler);
  }

  @Override
  public <R> void replyWithTimeout(long timeout, Handler<AsyncResult<Message<R>>> replyHandler) {
    sendReplyWithTimeout(EventBusImpl.createMessage(true, replyAddress, null), timeout, replyHandler);
  }

  @Override
  public <R> void reply(Object message, Handler<Message<R>> replyHandler) {
    sendReply(EventBusImpl.createMessage(true, replyAddress, message), replyHandler);
  }

  @Override
  public <R> void replyWithTimeout(Object message, long timeout, Handler<AsyncResult<Message<R>>> replyHandler) {
    sendReplyWithTimeout(EventBusImpl.createMessage(true, replyAddress, message), timeout, replyHandler);
  }

  @Override
  public void fail(int failureCode, String message) {
    sendReply(new ReplyFailureMessage(replyAddress, new ReplyException(ReplyFailure.RECIPIENT_FAILURE, failureCode, message)), null);
  }

  protected BaseMessage(Buffer readBuff) {
    int pos = 1;
    byte bsend = readBuff.getByte(pos);
    send = bsend == 0;
    pos += 1;
    int addressLength = readBuff.getInt(pos);
    pos += 4;
    byte[] addressBytes = readBuff.getBytes(pos, pos + addressLength);
    pos += addressLength;
    address = new String(addressBytes, CharsetUtil.UTF_8);
    int port = readBuff.getInt(pos);
    pos += 4;
    int hostLength = readBuff.getInt(pos);
    pos += 4;
    byte[] hostBytes = readBuff.getBytes(pos, pos + hostLength);
    pos += hostLength;
    String host = new String(hostBytes, CharsetUtil.UTF_8);
    sender = new ServerID(port, host);
    int replyAddressLength = readBuff.getInt(pos);
    pos += 4;
    if (replyAddressLength > 0) {
      byte[] replyAddressBytes = readBuff.getBytes(pos, pos + replyAddressLength);
      pos += replyAddressLength;
      replyAddress = new String(replyAddressBytes, CharsetUtil.UTF_8);
    } else {
      replyAddress = null;
    }
    readBody(pos, readBuff);
  }

  protected void write(NetSocket socket) {
    int length = 1 + 1 + 4 + address.length() + 1 + 4 * sender.host.length() +
        4 + (replyAddress == null ? 0 : replyAddress.length()) +
        getBodyLength();
    Buffer totBuff = new Buffer(length);
    totBuff.appendInt(0);
    totBuff.appendByte(type());
    totBuff.appendByte(send ? (byte)0 : (byte)1);
    writeString(totBuff, address);
    totBuff.appendInt(sender.port);
    writeString(totBuff, sender.host);
    if (replyAddress != null) {
      writeString(totBuff, replyAddress);
    } else {
      totBuff.appendInt(0);
    }
    writeBody(totBuff);
    totBuff.setInt(0, totBuff.length() - 4);
    socket.write(totBuff);
  }

  protected void writeString(Buffer buff, String str) {
    byte[] strBytes = str.getBytes(CharsetUtil.UTF_8);
    buff.appendInt(strBytes.length);
    buff.appendBytes(strBytes);
  }

  protected abstract byte type();

  protected abstract Message<T> copy();

  protected abstract void readBody(int pos, Buffer readBuff);

  protected abstract void writeBody(Buffer buff);

  protected abstract int getBodyLength();

  private <T> void sendReply(BaseMessage msg, Handler<Message<T>> replyHandler) {
    if (bus != null && replyAddress != null) {
      bus.sendReply(sender, msg, replyHandler);
    }
  }

  private <T> void sendReplyWithTimeout(BaseMessage msg, long timeout, Handler<AsyncResult<Message<T>>> replyHandler) {
    if (bus != null) {
      bus.sendReplyWithTimeout(sender, msg, timeout, replyHandler);
    }
  }
}
