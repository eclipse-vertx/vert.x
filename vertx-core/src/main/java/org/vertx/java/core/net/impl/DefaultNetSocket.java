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
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.file.impl.PathAdjuster;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.NetSocket;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.UUID;

public class DefaultNetSocket extends ConnectionBase implements NetSocket {

  private final String writeHandlerID;

  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private Handler<Void> drainHandler;
  private Handler<Message<Buffer>> writeHandler;

  public DefaultNetSocket(VertxInternal vertx, Channel channel, Context context) {
    super(vertx, channel, context);
    this.writeHandlerID = UUID.randomUUID().toString();
    writeHandler = new Handler<Message<Buffer>>() {
      public void handle(Message<Buffer> msg) {
        write(msg.body());
      }
    };
    vertx.eventBus().registerLocalHandler(writeHandlerID, writeHandler);
  }

  @Override
  public String writeHandlerID() {
    return writeHandlerID;
  }

  @Override
  public NetSocket write(Buffer data) {
    doWrite(data.getByteBuf());
    return this;
  }

  @Override
  public NetSocket write(String str) {
    doWrite(Unpooled.copiedBuffer(str, CharsetUtil.UTF_8));
    return this;
  }

  @Override
  public NetSocket write(String str, String enc) {
    if (enc == null) {
      write(str);
    } else {
      doWrite(Unpooled.copiedBuffer(str, Charset.forName(enc)));
    }
    return this;
  }

  @Override
  public NetSocket write(Buffer data, Handler<AsyncResult<Void>> doneHandler) {
    addFuture(doneHandler, doWrite(data.getByteBuf()));
    return this;
  }

  @Override
  public NetSocket write(String str, Handler<AsyncResult<Void>> doneHandler) {
    addFuture(doneHandler, doWrite(Unpooled.copiedBuffer(str, CharsetUtil.UTF_8)));
    return this;
  }

  @Override
  public NetSocket write(String str, String enc, Handler<AsyncResult<Void>> doneHandler) {
    if (enc == null) {
      write(str, enc);
    } else {
      addFuture(doneHandler, doWrite(Unpooled.copiedBuffer(str, Charset.forName(enc))));
    }
    return this;
  }

  @Override
  public NetSocket dataHandler(Handler<Buffer> dataHandler) {
    this.dataHandler = dataHandler;
    return this;
  }

  @Override
  public NetSocket pause() {
    doPause();
    return this;
  }

  @Override
  public NetSocket resume() {
    doResume();
    return this;
  }

  @Override
  public NetSocket setWriteQueueMaxSize(int maxSize) {
    doSetWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return doWriteQueueFull();
  }

  @Override
  public NetSocket endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return this;
  }

  @Override
  public NetSocket drainHandler(Handler<Void> drainHandler) {
    this.drainHandler = drainHandler;
    vertx.runOnLoop(new VoidHandler() {
      public void handle() {
        callDrainHandler(); //If the channel is already drained, we want to call it immediately
      }
    });
    return this;
  }

  @Override
  public NetSocket sendFile(String filename) {
    File f = new File(PathAdjuster.adjust(vertx, filename));
    super.sendFile(f);
    return this;
  }

  @Override
  public InetSocketAddress remoteAddress() {
    return super.remoteAddress();
  }

  @Override
  public NetSocket exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public NetSocket closeHandler(Handler<Void> handler) {
    this.closeHandler = handler;
    return this;
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
      } catch (Throwable t) {
        handleHandlerException(t);
      }
    }
  }

  //Close without checking thread - used when server is closed
  void internalClose() {
    channel.close();
  }

  private ChannelFuture doWrite(ByteBuf buff) {
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

}

