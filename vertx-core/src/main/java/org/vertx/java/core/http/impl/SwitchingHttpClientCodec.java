/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * Modified by Tim Fox to introduce switching code
 */

package org.vertx.java.core.http.impl;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.PrematureChannelClosureException;
import org.jboss.netty.handler.codec.http.*;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A combination of {@link HttpRequestEncoder} and {@link HttpResponseDecoder}
 * which enables easier client side HTTP implementation. {@link HttpClientCodec}
 * provides additional state management for <tt>HEAD</tt> and <tt>CONNECT</tt>
 * requests, which {@link HttpResponseDecoder} lacks.  Please refer to
 * {@link HttpResponseDecoder} to learn what additional state management needs
 * to be done for <tt>HEAD</tt> and <tt>CONNECT</tt> and why
 * {@link HttpResponseDecoder} can not handle it by itself.
 *
 * If the {@link Channel} gets closed and there are requests missing for a response
 * a {@link PrematureChannelClosureException} is thrown.
 *
 * @see HttpServerCodec
 *
 * @apiviz.has org.jboss.netty.handler.codec.http.HttpResponseDecoder
 * @apiviz.has org.jboss.netty.handler.codec.http.HttpRequestEncoder
 *
 * This class has been copied and modified from the original Netty version to introduce switching code.
 * These shenanigans are necessary so we can switch out the response decoder to the websocket decoder
 * as soon as the websocket handshake response has been decoded.
 * Its complicated by the fact that the buffer which contained the websocket handshake response may also have the
 * first write received from the server side websocket tacked on to the end, normally a ReplayingDecoder would
 * discard that so we have to catch the last bytes (if any), and resubmit.
 */
public class SwitchingHttpClientCodec implements ChannelUpstreamHandler,
    ChannelDownstreamHandler {

  /** A queue that is used for correlating a request and a response. */
  final Queue<HttpMethod> queue = new ConcurrentLinkedQueue<HttpMethod>();

  /** If true, decoding stops (i.e. pass-through) */
  volatile boolean done;

  private final HttpRequestEncoder encoder = new Encoder();
  private final HttpResponseDecoder decoder;
  private final AtomicLong requestResponseCounter = new AtomicLong(0);

  private final boolean failOnMissingResponse;

  private volatile String name;
  private volatile ChannelHandler switchTo;

  public void setSwitch(String name, ChannelHandler switchTo) {
    this.name = name;
    this.switchTo = switchTo;
  }

  /**
   * Creates a new instance with the default decoder options
   * ({@code maxInitialLineLength (4096}}, {@code maxHeaderSize (8192)}, and
   * {@code maxChunkSize (8192)}).
   *
   */
  public SwitchingHttpClientCodec() {
    this(4096, 8192, 8192, false);
  }

  /**
   * Creates a new instance with the specified decoder options.
   */
  public SwitchingHttpClientCodec(
      int maxInitialLineLength, int maxHeaderSize, int maxChunkSize) {
    this(maxInitialLineLength, maxHeaderSize, maxChunkSize, false);
  }

  /**
   * Creates a new instance with the specified decoder options.
   */
  public SwitchingHttpClientCodec(
      int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean failOnMissingResponse) {
    decoder = new Decoder(maxInitialLineLength, maxHeaderSize, maxChunkSize);
    this.failOnMissingResponse = failOnMissingResponse;
  }

  public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
      throws Exception {
    decoder.handleUpstream(ctx, e);
  }

  public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e)
      throws Exception {
    encoder.handleDownstream(ctx, e);
  }

  private final class Encoder extends HttpRequestEncoder {

    Encoder() {
    }

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel,
                            Object msg) throws Exception {
      if (msg instanceof HttpRequest && !done) {
        queue.offer(((HttpRequest) msg).getMethod());
      }

      Object obj =  super.encode(ctx, channel, msg);

      if (failOnMissingResponse) {
        // check if the request is chunked if so do not increment
        if (msg instanceof HttpRequest && !((HttpRequest) msg).isChunked()) {
          requestResponseCounter.incrementAndGet();
        } else if (msg instanceof HttpChunk && ((HttpChunk) msg).isLast()) {
          // increment as its the last chunk
          requestResponseCounter.incrementAndGet();
        }
      }
      return obj;
    }
  }

  private final class Decoder extends HttpResponseDecoder {

    Decoder(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize) {
      super(maxInitialLineLength, maxHeaderSize, maxChunkSize);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel ch,
                            ChannelBuffer buf, State state) throws Exception {

      if (done) {
        return buf.readBytes(actualReadableBytes());
      } else {
        Object msg = super.decode(ctx, ch, buf, state);
        if (failOnMissingResponse) {
          decrement(msg);
        }
        if (msg == null) {
          return null;
        }
        if (switchTo != null) {

          // Need to add it before the client handler
          ctx.getPipeline().addBefore("handler", name, switchTo);
          ctx.getPipeline().remove(SwitchingHttpClientCodec.this);

          switchTo = null;

          if (buf.readable()) {
            // Hand off the remaining data to the second decoder
            if (msg != null) {
              return new Object[]{msg, buf.readBytes(super.actualReadableBytes())};
            } else {
              return new Object[]{buf.readBytes(super.actualReadableBytes())};
            }
          } else {
            // Nothing to hand off
            return msg;
          }
        } else {
          return msg;
        }
      }
    }

    private void decrement(Object msg) {
      if (msg == null) {
        return;
      }

      // check if its a HttpMessage and its not chunked
      if (msg instanceof HttpMessage && !((HttpMessage) msg).isChunked()) {
        requestResponseCounter.decrementAndGet();
      } else if (msg instanceof HttpChunk && ((HttpChunk) msg).isLast()) {
        requestResponseCounter.decrementAndGet();
      } else if (msg instanceof Object[]) {
        // we just decrement it here as we only use this if the end of the chunk is reached
        // It would be more safe to check all the objects in the array but would also be slower
        requestResponseCounter.decrementAndGet();
      }
    }
    @Override
    protected boolean isContentAlwaysEmpty(HttpMessage msg) {
      final int statusCode = ((HttpResponse) msg).getStatus().getCode();
      if (statusCode == 100) {
        // 100-continue response should be excluded from paired comparison.
        return true;
      }

      // Get the method of the HTTP request that corresponds to the
      // current response.
      HttpMethod method = queue.poll();

      char firstChar = method.getName().charAt(0);
      switch (firstChar) {
        case 'H':
          // According to 4.3, RFC2616:
          // All responses to the HEAD request method MUST NOT include a
          // message-body, even though the presence of entity-header fields
          // might lead one to believe they do.
          if (HttpMethod.HEAD.equals(method)) {
            return true;

            // The following code was inserted to work around the servers
            // that behave incorrectly.  It has been commented out
            // because it does not work with well behaving servers.
            // Please note, even if the 'Transfer-Encoding: chunked'
            // header exists in the HEAD response, the response should
            // have absolutely no content.
            //
            //// Interesting edge case:
            //// Some poorly implemented servers will send a zero-byte
            //// chunk if Transfer-Encoding of the response is 'chunked'.
            ////
            //// return !msg.isChunked();
          }
          break;
        case 'C':
          // Successful CONNECT request results in a response with empty body.
          if (statusCode == 200) {
            if (HttpMethod.CONNECT.equals(method)) {
              // Proxy connection established - Not HTTP anymore.
              done = true;
              queue.clear();
              return true;
            }
          }
          break;
      }

      return super.isContentAlwaysEmpty(msg);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      super.channelClosed(ctx, e);
      if (failOnMissingResponse) {
        long missingResponses = requestResponseCounter.get();
        if (missingResponses > 0) {
          throw new PrematureChannelClosureException(
              "Channel closed but still missing " + missingResponses + " response(s)");
        }
      }
    }
  }
}
