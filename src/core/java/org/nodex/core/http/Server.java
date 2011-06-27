package org.nodex.core.http;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieDecoder;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jboss.netty.util.CharsetUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.COOKIE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.getHost;
import static org.jboss.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class Server {
  private ServerBootstrap bootstrap;
  private HttpCallback httpCallback;

  private Server(HttpCallback httpCallback) {
    ChannelFactory factory =
        new NioServerSocketChannelFactory(
            Executors.newCachedThreadPool(),
            Executors.newCachedThreadPool());
    bootstrap = new ServerBootstrap(factory);
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = Channels.pipeline();

        // Uncomment the following line if you want HTTPS
        //SSLEngine engine = SecureChatSslContextFactory.getServerContext().createSSLEngine();
        //engine.setUseClientMode(false);
        //pipeline.addLast("ssl", new SslHandler(engine));

        pipeline.addLast("decoder", new HttpRequestDecoder());
        // Uncomment the following line if you don't want to handle HttpChunks.
        //pipeline.addLast("aggregator", new HttpChunkAggregator(1048576));
        pipeline.addLast("encoder", new HttpResponseEncoder());
        // Remove the following line if you don't want automatic content compression.
        //pipeline.addLast("deflater", new HttpContentCompressor());
        pipeline.addLast("handler", new HttpRequestHandler());
        return pipeline;
      }
    });
    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setOption("child.keepAlive", true);
    this.httpCallback = httpCallback;
  }

  public static Server createServer(HttpCallback httpCallback) {
    return new Server(httpCallback);
  }

  public Server listen(int port) {
    return listen(port, "0.0.0.0");
  }

  public Server listen(int port, String host) {
    try {
      bootstrap.bind(new InetSocketAddress(InetAddress.getByName(host), port));
      System.out.println("Net server listening on " + host + ":" + port);
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
    return this;
  }

  public void stop() {
    bootstrap.releaseExternalResources();
  }

  public class HttpRequestHandler extends SimpleChannelUpstreamHandler {

    private HttpRequest request;
    private boolean readingChunks;
    /**
     * Buffer that stores the response content
     */
    private final StringBuilder buf = new StringBuilder();

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
      if (!readingChunks) {
        HttpRequest request = this.request = (HttpRequest) e.getMessage();

        if (is100ContinueExpected(request)) {
          send100Continue(e);
        }

        buf.setLength(0);
        buf.append("WELCOME TO THE WILD WILD WEB SERVER\r\n");
        buf.append("===================================\r\n");

        buf.append("VERSION: " + request.getProtocolVersion() + "\r\n");
        buf.append("HOSTNAME: " + getHost(request, "unknown") + "\r\n");
        buf.append("REQUEST_URI: " + request.getUri() + "\r\n\r\n");

        for (Map.Entry<String, String> h : request.getHeaders()) {
          buf.append("HEADER: " + h.getKey() + " = " + h.getValue() + "\r\n");
        }
        buf.append("\r\n");

        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.getUri());
        Map<String, List<String>> params = queryStringDecoder.getParameters();
        if (!params.isEmpty()) {
          for (Map.Entry<String, List<String>> p : params.entrySet()) {
            String key = p.getKey();
            List<String> vals = p.getValue();
            for (String val : vals) {
              buf.append("PARAM: " + key + " = " + val + "\r\n");
            }
          }
          buf.append("\r\n");
        }

        if (request.isChunked()) {
          readingChunks = true;
        } else {
          ChannelBuffer content = request.getContent();
          if (content.readable()) {
            buf.append("CONTENT: " + content.toString(CharsetUtil.UTF_8) + "\r\n");
          }
          writeResponse(e);
        }
      } else {
        HttpChunk chunk = (HttpChunk) e.getMessage();
        if (chunk.isLast()) {
          readingChunks = false;
          buf.append("END OF CONTENT\r\n");

          HttpChunkTrailer trailer = (HttpChunkTrailer) chunk;
          if (!trailer.getHeaderNames().isEmpty()) {
            buf.append("\r\n");
            for (String name : trailer.getHeaderNames()) {
              for (String value : trailer.getHeaders(name)) {
                buf.append("TRAILING HEADER: " + name + " = " + value + "\r\n");
              }
            }
            buf.append("\r\n");
          }

          writeResponse(e);
        } else {
          buf.append("CHUNK: " + chunk.getContent().toString(CharsetUtil.UTF_8) + "\r\n");
        }
      }
    }

    private void writeResponse(MessageEvent e) {
      // Decide whether to close the connection or not.
      boolean keepAlive = isKeepAlive(request);

      // Build the response object.
      HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
      response.setContent(ChannelBuffers.copiedBuffer(buf.toString(), CharsetUtil.UTF_8));
      response.setHeader(CONTENT_TYPE, "text/plain; charset=UTF-8");

      if (keepAlive) {
        // Add 'Content-Length' header only for a keep-alive connection.
        response.setHeader(CONTENT_LENGTH, response.getContent().readableBytes());
      }

      // Encode the cookie.
      String cookieString = request.getHeader(COOKIE);
      if (cookieString != null) {
        CookieDecoder cookieDecoder = new CookieDecoder();
        Set<Cookie> cookies = cookieDecoder.decode(cookieString);
        if (!cookies.isEmpty()) {
          // Reset the cookies if necessary.
          CookieEncoder cookieEncoder = new CookieEncoder(true);
          for (Cookie cookie : cookies) {
            cookieEncoder.addCookie(cookie);
          }
          response.addHeader(SET_COOKIE, cookieEncoder.encode());
        }
      }

      // Write the response.
      ChannelFuture future = e.getChannel().write(response);

      // Close the non-keep-alive connection after the write operation is done.
      if (!keepAlive) {
        future.addListener(ChannelFutureListener.CLOSE);
      }
    }

    private void send100Continue(MessageEvent e) {
      HttpResponse response = new DefaultHttpResponse(HTTP_1_1, CONTINUE);
      e.getChannel().write(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
        throws Exception {
      e.getCause().printStackTrace();
      e.getChannel().close();
    }
  }

  private void send100Continue(MessageEvent e) {
    HttpResponse response = new DefaultHttpResponse(HTTP_1_1, CONTINUE);
    e.getChannel().write(response);
  }
}
