/*
 * Copyright 2013 the original author or authors.
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

package org.vertx.java.core.impl;

import io.netty.buffer.BufType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;


/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class FlowControlHandler extends ChannelOperationHandlerAdapter {

  private final FlowControlStateEvent state = new FlowControlStateEvent();
  private final ChannelFutureListener listener = new ChannelFutureListener() {
    @Override
    public void operationComplete(ChannelFuture future) throws Exception {
      if (!state.isWritable()) {
        if (!outboundBuf.isReadable(lowMark)) {
          state.updateWritable(true);
          ctx.fireUserEventTriggered(state);
        } else {
          if (future.isSuccess() && outboundBuf.isReadable()) {
            // there is something left to flush so try to flush it now!
            ctx.flush().addListener(this);
          }
        }
      }
    }
  };
  private volatile int lowMark;
  private ChannelHandlerContext ctx;
  private ByteBuf outboundBuf;
  private volatile int highMark;

  /**
   * Create a new instance
   */
  public FlowControlHandler(int lowMark, int highMark) {
    if (lowMark < 1) {
      throw new IllegalArgumentException("minWritable must be >= 1");
    }
    if (lowMark > highMark) {
      throw new IllegalArgumentException("lowMark must be >= highMark");
    }
    this.lowMark = lowMark;
    this.highMark = highMark;
  }

  public FlowControlHandler() {
    this(32 * 1024, 64 * 1024);
  }

  public void setLimit(int lowMark, int highMark) {
    if (lowMark < 1) {
      throw new IllegalArgumentException("minWritable must be >= 1");
    }
    if (lowMark > highMark) {
      throw new IllegalArgumentException("lowMark must be >= highMark");
    }
    this.lowMark = lowMark;
    this.highMark = highMark;

    ctx.executor().execute(new Runnable() {
      @Override
      public void run() {
        if (state.isWritable() && outboundBuf.isReadable(FlowControlHandler.this.highMark)) {
          state.updateWritable(false);
          ctx.fireUserEventTriggered(state);
        } else if (!state.isWritable() && outboundBuf.isReadable(FlowControlHandler.this.lowMark)) {
          state.updateWritable(true);
          ctx.fireUserEventTriggered(state);
        }
      }
    });
  }

  @Override
  public void flush(final ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
    boolean writable = state.isWritable();
    if (writable) {
      writable = !outboundBuf.isReadable(highMark);
    }
    if (!writable) {
      if (state.isWritable()) {
        state.updateWritable(false);
        ctx.fireUserEventTriggered(state);
      }
      promise.addListener(listener);

    }
    ctx.flush(promise);
  }

  @Override
  public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
    this.ctx = ctx;
    Channel channel = ctx.channel();
    if (channel.metadata().bufferType() == BufType.BYTE) {
      outboundBuf = channel.unsafe().headContext().outboundByteBuffer();
    } else {
      throw new IllegalStateException("Only supported for Channels which handle bytes");
    }
    super.handlerAdded(ctx);
  }
}
