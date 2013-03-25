/*
 * Copyright 2011-2012 the original author or authors.
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

package org.vertx.java.core.net.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.CharsetUtil;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.file.impl.PathAdjuster;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.impl.management.JMX;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetSocket;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultNetSocket extends NetSocket {

  @SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(DefaultNetSocket.class);

  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private Handler<Void> drainHandler;
  private Handler<Message<Buffer>> writeHandler;

  protected AtomicLong sent = new AtomicLong(0L);
  protected AtomicLong received = new AtomicLong(0L);

  public DefaultNetSocket(VertxInternal vertx, Channel channel, Context context) {
    super(vertx, channel, String.format("net-%s", UUID.randomUUID().toString()), context);
    writeHandler = new Handler<Message<Buffer>>() {
      public void handle(Message<Buffer> msg) {
        writeBuffer(msg.body);
      }
    };
    vertx.eventBus().registerLocalHandler(writeHandlerID, writeHandler);

    JMX.CORE.registerNetSocket(this);
  }

  public void writeBuffer(Buffer data) {
    doWrite(data.getByteBuf());
  }

  public NetSocket write(Buffer data) {
    doWrite(data.getByteBuf());
    return this;
  }

  public NetSocket write(String str) {
    doWrite(Unpooled.copiedBuffer(str, CharsetUtil.UTF_8));
    return this;
  }

  public NetSocket write(String str, String enc) {
    if (enc == null) {
      write(str);
    } else {
      doWrite(Unpooled.copiedBuffer(str, Charset.forName(enc)));
    }
    return this;
  }

  public NetSocket write(Buffer data, Handler<Void> doneHandler) {
    addFuture(doneHandler, doWrite(data.getByteBuf()));
    return this;
  }

  public NetSocket write(String str, Handler<Void> doneHandler) {
    addFuture(doneHandler, doWrite(Unpooled.copiedBuffer(str, CharsetUtil.UTF_8)));
    return this;
  }

  public NetSocket write(String str, String enc, Handler<Void> doneHandler) {
    if (enc == null) {
      write(str, enc);
    } else {
      addFuture(doneHandler, doWrite(Unpooled.copiedBuffer(str, Charset.forName(enc))));
    }
    return this;
  }

  public void dataHandler(Handler<Buffer> dataHandler) {
    this.dataHandler = dataHandler;
  }

  public void endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
  }

  public void drainHandler(Handler<Void> drainHandler) {
    this.drainHandler = drainHandler;
    vertx.runOnLoop(new SimpleHandler() {
      public void handle() {
        callDrainHandler(); //If the channel is already drained, we want to call it immediately
      }
    });
  }

  public void sendFile(String filename) {
    File f = new File(PathAdjuster.adjust(vertx, filename));
    super.sendFile(f);
  }

  public InetSocketAddress getRemoteAddress() {
    return super.remoteAddress();
  }

  protected Context getContext() {
    return super.getContext();
  }

  protected void handleClosed() {
    setContext();
    if (endHandler != null) {
      try {
        endHandler.handle(null);
      } catch (Throwable t) {
        handleHandlerException(t);
      }
    }
    super.handleClosed();
    if (vertx.eventBus() != null) {
      vertx.eventBus().unregisterHandler(writeHandlerID, writeHandler);
    }
  }

  public void handleInterestedOpsChanged() {
    setContext();
    callDrainHandler();
  }

  void handleDataReceived(Buffer data) {
    if (dataHandler != null) {
      setContext();
      try {
        dataHandler.handle(data);
        received.addAndGet(data.length());
      } catch (Throwable t) {
        handleHandlerException(t);
      }
    }
  }

  //Close without checking thread - used when server is closed
  void internalClose() {
    channel.close();
    JMX.CORE.unregisterNetSocket(this);
  }

  public void setInternalWritable(boolean  writable) {
    setWritable(writable);
  }

  private ChannelFuture doWrite(ByteBuf buff) {
    sent.addAndGet(buff.writableBytes());
    return channel.write(buff);
  }

  private void callDrainHandler() {
    if (drainHandler != null) {
      if (!writeQueueFull()) {
        try {
          drainHandler.handle(null);
        } catch (Throwable t) {
          handleHandlerException(t);
        }
      }
    }
  }

  public final long getReceivedCount() {
    return received.get();
  }

  public final long getSentCount() {
    return sent.get();
  }

}

