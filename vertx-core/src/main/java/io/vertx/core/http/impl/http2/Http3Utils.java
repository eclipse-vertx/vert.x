package io.vertx.core.http.impl.http2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http3.*;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;

import java.util.List;
import java.util.function.LongFunction;

public class Http3Utils {
  public static List<String> supportedApplicationProtocols() {
    return List.of(Http3.supportedApplicationProtocols());
  }

  public static io.vertx.core.Future<QuicStreamChannel> newRequestStream(QuicChannel channel,
                                                                         Handler<QuicStreamChannel> handler) {
    PromiseInternal<QuicStreamChannel> listener = (PromiseInternal) Promise.promise();

    Http3.newRequestStream(channel, new ChannelInitializer<QuicStreamChannel>() {
      @Override
      protected void initChannel(QuicStreamChannel quicStreamChannel) {
        handler.handle(quicStreamChannel);
      }
    }).addListener(listener);
    return listener;
  }

  public static Http3ServerConnectionHandlerBuilder newServerConnectionHandlerBuilder() {
    return new Http3ServerConnectionHandlerBuilder();
  }

  public static Http3ClientConnectionHandlerBuilder newClientConnectionHandlerBuilder() {
    return new Http3ClientConnectionHandlerBuilder();
  }

  private abstract static class Http3ConnectionHandlerBuilderBase<T extends Http3ConnectionHandlerBuilderBase<T>> {
    protected String agentType;
    protected Handler<Http3GoAwayFrame> http3GoAwayFrameHandler;
    protected Handler<Http3SettingsFrame> http3SettingsFrameHandler;
    protected LongFunction<ChannelHandler> unknownInboundStreamHandlerFactory;
    protected Http3SettingsFrame localSettings;
    protected boolean disableQpackDynamicTable = true;

    private Http3ConnectionHandlerBuilderBase() {
    }

    public T unknownInboundStreamHandlerFactory(LongFunction<ChannelHandler> unknownInboundStreamHandlerFactory) {
      this.unknownInboundStreamHandlerFactory = unknownInboundStreamHandlerFactory;
      return (T) this;
    }

    public T localSettings(Http3SettingsFrame localSettings) {
      this.localSettings = localSettings;
      return (T) this;
    }

    public T disableQpackDynamicTable(boolean disableQpackDynamicTable) {
      this.disableQpackDynamicTable = disableQpackDynamicTable;
      return (T) this;
    }

    public T agentType(String agentType) {
      this.agentType = agentType;
      return (T) this;
    }

    public T http3GoAwayFrameHandler(Handler<Http3GoAwayFrame> http3GoAwayFrameHandler) {
      this.http3GoAwayFrameHandler = http3GoAwayFrameHandler;
      return (T) this;
    }

    public T http3SettingsFrameHandler(Handler<Http3SettingsFrame> http3SettingsFrameHandler) {
      this.http3SettingsFrameHandler = http3SettingsFrameHandler;
      return (T) this;
    }

    Http3ControlStreamChannelHandler buildHttp3ControlStreamChannelHandler() {
      return new Http3ControlStreamChannelHandler()
        .http3GoAwayFrameHandler(http3GoAwayFrameHandler)
        .http3SettingsFrameHandler(http3SettingsFrameHandler)
        .agentType(agentType);
    }
    protected abstract Http3ConnectionHandler build();
  }

  public static class Http3ServerConnectionHandlerBuilder extends Http3ConnectionHandlerBuilderBase<Http3ServerConnectionHandlerBuilder> {
    private Handler<QuicStreamChannel> requestStreamHandler;

    private Http3ServerConnectionHandlerBuilder() {
    }

    public Http3ServerConnectionHandlerBuilder requestStreamHandler(Handler<QuicStreamChannel> requestStreamHandler) {
      this.requestStreamHandler = requestStreamHandler;
      return this;
    }

    public Http3ServerConnectionHandler build() {
      return new Http3ServerConnectionHandler(new ChannelInitializer<QuicStreamChannel>() {
        @Override
        protected void initChannel(QuicStreamChannel streamChannel) {
          requestStreamHandler.handle(streamChannel);
        }
      }, buildHttp3ControlStreamChannelHandler(), unknownInboundStreamHandlerFactory, localSettings, disableQpackDynamicTable);
    }
  }

  public static class Http3ClientConnectionHandlerBuilder extends Http3ConnectionHandlerBuilderBase<Http3ClientConnectionHandlerBuilder> {
    private LongFunction<ChannelHandler> pushStreamHandlerFactory;

    private Http3ClientConnectionHandlerBuilder() {
    }

    public Http3ClientConnectionHandlerBuilder pushStreamHandlerFactory(LongFunction<ChannelHandler> pushStreamHandlerFactory) {
      this.pushStreamHandlerFactory = pushStreamHandlerFactory;
      return this;
    }

    public Http3ClientConnectionHandler build() {
      return new Http3ClientConnectionHandler(buildHttp3ControlStreamChannelHandler(), pushStreamHandlerFactory,
        unknownInboundStreamHandlerFactory, localSettings, disableQpackDynamicTable);
    }
  }

  private final static class Http3ControlStreamChannelHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(Http3ControlStreamChannelHandler.class);

    private Http3SettingsFrame http3SettingsFrame;
    private boolean settingsRead;
    private String agentType;
    private Handler<Http3SettingsFrame> http3SettingsFrameHandler;
    private Handler<Http3GoAwayFrame> http3GoAwayFrameHandler;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
      log.debug(String.format("%s - Received event for channelId: %s, event: %s", agentType, ctx.channel().id(),
        evt.getClass().getSimpleName()));
      super.userEventTriggered(ctx, evt);
    }

    @Override
    public synchronized void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      log.debug(String.format("%s - channelRead() called with msg type: %s", agentType, msg.getClass().getSimpleName()));

      if (msg instanceof DefaultHttp3SettingsFrame) {
        if (http3SettingsFrame == null) {
          http3SettingsFrame = (DefaultHttp3SettingsFrame) msg;
        }
        ReferenceCountUtil.release(msg);
      } else if (msg instanceof DefaultHttp3GoAwayFrame) {
        super.channelRead(ctx, msg);
        DefaultHttp3GoAwayFrame http3GoAwayFrame = (DefaultHttp3GoAwayFrame) msg;
        if (http3GoAwayFrameHandler != null) {
          http3GoAwayFrameHandler.handle(http3GoAwayFrame);
        }
        ReferenceCountUtil.release(msg);
      } else if (msg instanceof DefaultHttp3UnknownFrame) {
        if (log.isDebugEnabled()) {
          log.debug(String.format("%s - Received unknownFrame : %s", agentType, msg));
        }
        ReferenceCountUtil.release(msg);
        super.channelRead(ctx, msg);
      } else {
        super.channelRead(ctx, msg);
      }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
      log.debug(String.format("%s - ChannelReadComplete called for channelId: %s, streamId: %s", agentType,
        ctx.channel().id(), ((QuicStreamChannel) ctx.channel()).streamId()));

      synchronized (this) {
        if (http3SettingsFrame != null && !settingsRead) {
          settingsRead = true;

          if (http3SettingsFrameHandler != null) {
            http3SettingsFrameHandler.handle(http3SettingsFrame);
          }
        }
      }
      super.channelReadComplete(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      log.debug(String.format("%s - Caught exception on channelId : %s!", agentType, ctx.channel().id()), cause);
      super.exceptionCaught(ctx, cause);
    }

    public Http3ControlStreamChannelHandler agentType(String agentType) {
      this.agentType = agentType;
      return this;
    }

    public Http3ControlStreamChannelHandler http3SettingsFrameHandler(Handler<Http3SettingsFrame> http3SettingsFrameHandler) {
      this.http3SettingsFrameHandler = http3SettingsFrameHandler;
      return this;
    }

    public Http3ControlStreamChannelHandler http3GoAwayFrameHandler(Handler<Http3GoAwayFrame> http3GoAwayFrameHandler) {
      this.http3GoAwayFrameHandler = http3GoAwayFrameHandler;
      return this;
    }
  }
}
