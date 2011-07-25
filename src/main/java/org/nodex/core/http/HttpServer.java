package org.nodex.core.http;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.ChannelGroupFutureListener;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioSocketChannel;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.nodex.core.DoneHandler;
import org.nodex.core.NodexInternal;
import org.nodex.core.ThreadSourceUtils;
import org.nodex.core.buffer.Buffer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class HttpServer {
  private ServerBootstrap bootstrap;
  private HttpServerConnectHandler connectHandler;
  private Map<Channel, HttpServerConnection> connectionMap = new ConcurrentHashMap<Channel, HttpServerConnection>();
  private ChannelGroup serverChannelGroup;

  private HttpServer(HttpServerConnectHandler connectHandler, final boolean ssl) {
    serverChannelGroup = new DefaultChannelGroup("nodex-acceptor-channels");
    ChannelFactory factory =
        new NioServerSocketChannelFactory(
            NodexInternal.instance.getAcceptorPool(),
            NodexInternal.instance.getWorkerPool());
    bootstrap = new ServerBootstrap(factory);
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() {
        ChannelPipeline pipeline = Channels.pipeline();
        if (ssl) {
          //TODO
//          SSLEngine engine = SecureChatSslContextFactory.getServerContext().createSSLEngine();
//          engine.setUseClientMode(false);
//          pipeline.addLast("ssl", new SslHandler(engine));
        }
        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("encoder", new HttpResponseEncoder());
        pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
        pipeline.addLast("handler", new HttpRequestHandler());
        return pipeline;
      }
    });
    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setOption("child.keepAlive", true);
    this.connectHandler = connectHandler;
  }

  public static HttpServer createServer(HttpServerConnectHandler connectHandler) {
    return new HttpServer(connectHandler, false);
  }

  public static HttpServer createSSLServer(HttpServerConnectHandler connectHandler) {
    return new HttpServer(connectHandler, true);
  }

  public HttpServer listen(int port) {
    return listen(port, "0.0.0.0");
  }

  public HttpServer listen(int port, String host) {
    try {
      Channel serverChannel = bootstrap.bind(new InetSocketAddress(InetAddress.getByName(host), port));
      serverChannelGroup.add(serverChannel);
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
    return this;
  }

  public void close() {
    close(null);
  }

  public void close(final DoneHandler done) {
    for (HttpServerConnection conn : connectionMap.values()) {
      conn.close();
    }
    if (done != null) {
      serverChannelGroup.close().addListener(new ChannelGroupFutureListener() {
        public void operationComplete(ChannelGroupFuture channelGroupFuture) throws Exception {
          done.onDone();
        }
      });
    }
  }

  public class HttpRequestHandler extends SimpleChannelUpstreamHandler {

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
      Channel ch = e.getChannel();
      HttpServerConnection conn = connectionMap.get(ch);
      if (e.getMessage() instanceof HttpRequest) {
        HttpRequest request = (HttpRequest) e.getMessage();
        if (HttpHeaders.is100ContinueExpected(request)) {
          ch.write(new DefaultHttpResponse(HTTP_1_1, CONTINUE));
        }
        HttpServerRequest req = new HttpServerRequest(conn, request);
        HttpServerResponse resp = new HttpServerResponse(HttpHeaders.isKeepAlive(request),
            request.getHeader(HttpHeaders.Names.COOKIE), conn);
        conn.handleRequest(req, resp);
        ChannelBuffer requestBody = request.getContent();
        if (requestBody.readable()) {
          conn.handleChunk(new Buffer(requestBody));
        }
        if (!request.isChunked()) {
          conn.handleEnd();
        }
      } else if (e.getMessage() instanceof HttpChunk) {
        HttpChunk chunk = (HttpChunk) e.getMessage();
        Buffer buff = Buffer.fromChannelBuffer(chunk.getContent());
        conn.handleChunk(buff);
        //TODO chunk trailers
        if (chunk.isLast()) {
          conn.handleEnd();
        }
      } else {
        throw new IllegalStateException("Invalid object " + e.getMessage());
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
        throws Exception {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final HttpServerConnection conn = connectionMap.get(ch);
      ch.close();
      final Throwable t = e.getCause();
      if (conn != null && t instanceof Exception) {
        ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
          public void run() {
            conn.handleException((Exception) t);
          }
        });
      } else {
        t.printStackTrace();
      }
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final String contextID = NodexInternal.instance.createContext(ch.getWorker());
      ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
        public void run() {
          final HttpServerConnection conn = new HttpServerConnection(ch, contextID, Thread.currentThread());
          connectionMap.put(ch, conn);
          NodexInternal.instance.setContextID(contextID);
          connectHandler.onConnect(conn);
        }
      });
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
      final NioSocketChannel ch = (NioSocketChannel)e.getChannel();
      final HttpServerConnection conn = connectionMap.remove(ch);
      ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
        public void run() {
          conn.handleClosed();
          NodexInternal.instance.destroyContext(conn.getContextID());
        }
      });

    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      final NioSocketChannel ch = (NioSocketChannel)e.getChannel();
      final HttpServerConnection conn = connectionMap.get(ch);
      ChannelState state = e.getState();
      if (state == ChannelState.INTEREST_OPS) {
        ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
          public void run() {
            conn.handleInterestedOpsChanged();
          }
        });
      }
    }
  }
}
