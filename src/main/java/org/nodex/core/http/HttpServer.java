package org.nodex.core.http;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.nodex.core.Nodex;
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
  private HttpConnectHandler connectHandler;
  private Map<Channel, HttpConnection> connectionMap = new ConcurrentHashMap<Channel, HttpConnection>();

  private HttpServer(HttpConnectHandler connectHandler) {
    ChannelFactory factory =
        new NioServerSocketChannelFactory(
            Nodex.instance.getAcceptorPool(),
            Nodex.instance.getCorePool(),
            Nodex.instance.getCoreThreadPoolSize());
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
    this.connectHandler = connectHandler;
  }

  public static HttpServer createServer(HttpConnectHandler connectHandler) {
    return new HttpServer(connectHandler);
  }

  public HttpServer listen(int port) {
    return listen(port, "0.0.0.0");
  }

  public HttpServer listen(int port, String host) {
    try {
      bootstrap.bind(new InetSocketAddress(InetAddress.getByName(host), port));
      System.out.println("HTTP server listening on " + host + ":" + port);
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
    return this;
  }

  public void stop() {
    bootstrap.releaseExternalResources();
  }

  public class HttpRequestHandler extends SimpleChannelUpstreamHandler {

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {

      Channel ch = e.getChannel();
      HttpConnection conn = connectionMap.get(ch);

      if (e.getMessage() instanceof org.jboss.netty.handler.codec.http.HttpRequest) {
        org.jboss.netty.handler.codec.http.HttpRequest request = (org.jboss.netty.handler.codec.http.HttpRequest) e.getMessage();
        //FIXME = what to do here?
//        if (HttpHeaders.is100ContinueExpected((HttpMessage)e)) {
//          send100Continue(e);
//        }
        Map<String, String> headers = new HashMap<String, String>();
        //Why doesn't Netty provide a map?
        for (Map.Entry<String, String> h : request.getHeaders()) {
          headers.put(h.getKey(), h.getValue());
        }
        HttpRequest req = new HttpRequest(request.getMethod().toString(), request.getUri(), headers);
        conn.handleRequest(req);
        ChannelBuffer requestBody = request.getContent();
        if (requestBody.readable()) {
          conn.handleChunk(new Buffer(requestBody));
        }
      } else if (e.getMessage() instanceof HttpChunk) {
        HttpChunk chunk = (HttpChunk) e.getMessage();
        Buffer buff = Buffer.fromChannelBuffer(chunk.getContent());
        conn.handleChunk(buff);
      } else {
        throw new IllegalStateException("Invalid object " + e.getMessage());
      }

      /*
      if (!readingChunks) {
        HttpRequest request = this.request = (HttpRequest)e.getMessage();

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
      */
    }

    /*
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
    */

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

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
      Channel ch = e.getChannel();
      HttpConnection conn = new HttpConnection(ch);
      connectionMap.put(ch, conn);
      connectHandler.onConnect(conn);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
      Channel ch = e.getChannel();
      connectionMap.remove(ch);
    }
  }

  private void send100Continue(MessageEvent e) {
    HttpResponse response = new DefaultHttpResponse(HTTP_1_1, CONTINUE);
    e.getChannel().write(response);
  }
}
