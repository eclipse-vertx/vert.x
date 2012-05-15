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

package org.vertx.java.core.http.impl;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

/**
 * These shenanigans are necessary so we can switch out the response decoder to the websocket decoder
 * as soon as the websocket handshake response has been decoded.
 * Its complicated by the fact that the buffer which contained the websocket handshake response may also have the
 * first write received from the server side websocket tacked on to the end, normally a ReplayingDecoder would
 * discard that so we have to catch the last bytes (if any), and resubmit.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SwitchingHttpResponseDecoder extends HttpResponseDecoder {

  private static final Logger log = LoggerFactory.getLogger(SwitchingHttpResponseDecoder.class);

  private volatile String name;
  private volatile ChannelHandler switchTo;


  public void setSwitch(String name, ChannelHandler switchTo) {
    this.name = name;
    this.switchTo = switchTo;
  }

  @Override
  protected Object decode(ChannelHandlerContext ctx,
                          Channel ch,
                          ChannelBuffer buf,
                          State state) throws Exception {

    // Decode the first message
    Object firstMessage = super.decode(ctx, ch, buf, state);

    if (firstMessage == null) {
      return null;
    }

    if (switchTo != null) {

      // Need to add it before the client handler
      ctx.getPipeline().addBefore("handler", name, switchTo);
      ctx.getPipeline().remove(this);

      switchTo = null;

      if (buf.readable()) {
        // Hand off the remaining data to the second decoder
        if (firstMessage != null) {
          return new Object[]{firstMessage, buf.readBytes(super.actualReadableBytes())};
        } else {
          return new Object[]{buf.readBytes(super.actualReadableBytes())};
        }
      } else {
        // Nothing to hand off
        return firstMessage;
      }
    } else {
      return firstMessage;
    }
  }

}
