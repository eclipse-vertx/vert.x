package io.vertx.core.http.impl.http2.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.Promise;
import io.vertx.core.http.impl.Http1xClientConnection;
import io.vertx.core.http.impl.Http2UpgradeClientConnection;
import io.vertx.core.http.impl.HttpClientBase;
import io.vertx.core.http.impl.HttpClientConnection;
import io.vertx.core.http.impl.HttpClientStream;
import io.vertx.core.http.impl.HttpRequestHead;
import io.vertx.core.http.impl.HttpResponseHead;
import io.vertx.core.http.impl.VertxHttp2ClientUpgradeCodec;
import io.vertx.core.http.impl.http2.Http2ClientChannelInitializer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.ClientMetrics;

import java.util.ArrayDeque;
import java.util.Deque;

import static io.vertx.core.http.impl.Http2UpgradeClientConnection.SEND_BUFFERED_MESSAGES_EVENT;

public class Http2CodecClientChannelInitializer implements Http2ClientChannelInitializer {

  private HttpClientBase client;
  private ClientMetrics metrics;
  private boolean pooled;
  private long maxLifetime;
  private HostAndPort authority;

  public Http2CodecClientChannelInitializer(HttpClientBase client, ClientMetrics metrics, boolean pooled, long maxLifetime, HostAndPort authority) {
    this.client = client;
    this.metrics = metrics;
    this.pooled = pooled;
    this.maxLifetime = maxLifetime;
    this.authority = authority;
  }

  @Override
  public Http2UpgradeClientConnection.Http2ChannelUpgrade channelUpgrade(Http1xClientConnection conn) {
    return new CodecChannelUpgrade(client, metrics,
      conn.metric(),
      client.options.getInitialSettings(),
      client.options().getHttp2UpgradeMaxContentLength(), maxLifetime);
  }

  @Override
  public void http2Connected(ContextInternal context, Object metric, Channel ch, PromiseInternal<HttpClientConnection> promise) {
    VertxHttp2ConnectionHandler<Http2ClientConnectionImpl> clientHandler;
    try {
      clientHandler = Http2ClientConnectionImpl.createHttp2ConnectionHandler(client, metrics, context, false, metric, authority, pooled, maxLifetime);
      ch.pipeline().addLast("handler", clientHandler);
      ch.flush();
    } catch (Exception e) {
      connectFailed(ch, e, promise);
      return;
    }
    clientHandler.connectFuture().addListener(promise);
  }

  private void connectFailed(Channel ch, Throwable t, Promise<HttpClientConnection> future) {
    if (ch != null) {
      try {
        ch.close();
      } catch (Exception ignore) {
      }
    }
    future.tryFail(t);
  }

  public static class CodecChannelUpgrade implements Http2UpgradeClientConnection.Http2ChannelUpgrade {

    private final HttpClientBase client;
    private final ClientMetrics clientMetrics;
    private final Object connectionMetric;
    private final int maxContentLength;
    private final io.vertx.core.http.Http2Settings initialSettings;
    private final long maxLifetime;

    public CodecChannelUpgrade(HttpClientBase client,
                               ClientMetrics clientMetrics,
                               Object connectionMetric,
                               io.vertx.core.http.Http2Settings initialSettings,
                               int maxContentLength,
                               long maxLifetime) {
      this.initialSettings = initialSettings;
      this.maxContentLength = maxContentLength;
      this.client = client;
      this.maxLifetime = maxLifetime;
      this.clientMetrics = clientMetrics;
      this.connectionMetric = connectionMetric;
    }

    public void upgrade(HttpClientStream upgradingStream, HttpRequestHead request,
                        ByteBuf content,
                        boolean end,
                        Channel channel,
                        boolean pooled,
                        Http2UpgradeClientConnection.UpgradeResult result) {
      ChannelPipeline pipeline = channel.pipeline();
      HttpClientCodec httpCodec = pipeline.get(HttpClientCodec.class);

      class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
          super.userEventTriggered(ctx, evt);
          ChannelPipeline pipeline = ctx.pipeline();
          if (evt instanceof HttpClientUpgradeHandler.UpgradeEvent) {
            switch ((HttpClientUpgradeHandler.UpgradeEvent)evt) {
              case UPGRADE_SUCCESSFUL:
                // Remove Http1xClientConnection handler
                pipeline.remove("handler");
                // Go through
              case UPGRADE_REJECTED:
                // Remove this handler
                pipeline.remove(this);
                // Upgrade handler will remove itself and remove the HttpClientCodec
                result.upgradeRejected();
                break;
            }
          }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
          if (msg instanceof HttpResponseHead) {
            pipeline.remove(this);
            HttpResponseHead resp = (HttpResponseHead) msg;
            if (resp.statusCode != HttpResponseStatus.SWITCHING_PROTOCOLS.code()) {
              // Insert the close headers to let the HTTP/1 stream close the connection
              resp.headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            }
          }
          super.channelRead(ctx, msg);
        }
      }

      VertxHttp2ClientUpgradeCodec upgradeCodec = new VertxHttp2ClientUpgradeCodec(initialSettings) {
        @Override
        public void upgradeTo(ChannelHandlerContext ctx, FullHttpResponse upgradeResponse) throws Exception {

          // Now we need to upgrade this to an HTTP2
          VertxHttp2ConnectionHandler<Http2ClientConnectionImpl> handler = Http2ClientConnectionImpl.createHttp2ConnectionHandler(
            client,
            clientMetrics,
            upgradingStream.context(),
            true,
            connectionMetric,
            request.authority,
            pooled,
            maxLifetime);
          channel.pipeline().addLast(handler);
          handler.connectFuture().addListener(future -> {
            if (!future.isSuccess()) {
              // Handle me
              // log.error(future.cause().getMessage(), future.cause());
            } else {
              Http2ClientConnectionImpl connection = (Http2ClientConnectionImpl) future.getNow();
              HttpClientStream upgradedStream;
              try {
                upgradedStream = connection.upgradeStream(upgradingStream.metric(), upgradingStream.trace(), upgradingStream.context());
                result.upgradeAccepted(connection, upgradedStream);
              } catch (Exception e) {
                result.upgradeFailure(e);
              }
            }
          });
          handler.clientUpgrade(ctx);
        }
      };
      HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(httpCodec, upgradeCodec, maxContentLength) {

        private long bufferedSize = 0;
        private Deque<Object> buffered = new ArrayDeque<>();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
          if (buffered != null) {
            // Buffer all messages received from the server until the HTTP request is fully sent.
            //
            // Explanation:
            //
            // It is necessary that the client only starts to process the response when the request
            // has been fully sent because the current HTTP2 implementation will not be able to process
            // the server preface until the client preface has been sent.
            //
            // Adding the VertxHttp2ConnectionHandler to the pipeline has two effects:
            // - it is required to process the server preface
            // - it will send the request preface to the server
            //
            // As we are adding this handler to the pipeline when we receive the 101 response from the server
            // this might send the client preface before the initial HTTP request (doing the upgrade) is fully sent
            // resulting in corrupting the protocol (the server might interpret it as an corrupted connection preface).
            //
            // Therefore we must buffer all pending messages until the request is fully sent.

            int maxContent = maxContentLength();
            boolean lower = bufferedSize < maxContent;
            if (msg instanceof ByteBufHolder) {
              bufferedSize += ((ByteBufHolder)msg).content().readableBytes();
            } else if (msg instanceof ByteBuf) {
              bufferedSize += ((ByteBuf)msg).readableBytes();
            }
            buffered.add(msg);

            if (bufferedSize >= maxContent && lower) {
              ctx.fireExceptionCaught(new TooLongFrameException("Max content exceeded " + maxContentLength() + " bytes."));
            }
          } else {
            super.channelRead(ctx, msg);
          }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
          if (SEND_BUFFERED_MESSAGES_EVENT == evt) {
            Deque<Object> messages = buffered;
            buffered = null;
            Object msg;
            while ((msg = messages.poll()) != null) {
              super.channelRead(ctx, msg);
            }
          } else {
            super.userEventTriggered(ctx, evt);
          }
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
          if (buffered != null) {
            Deque<Object> messages = buffered;
            buffered = null;
            Object msg;
            while ((msg = messages.poll()) != null) {
              ReferenceCountUtil.release(msg);
            }
          }
          super.handlerRemoved(ctx);
        }

      };
      pipeline.addAfter("codec", null, new UpgradeRequestHandler());
      pipeline.addAfter("codec", null, upgradeHandler);
    }
  }
}
