package org.nodex.core.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.nodex.core.DoneHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.streams.WriteStream;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * User: timfox
 * Date: 22/07/2011
 * Time: 12:01
 */
public class HttpClientRequest implements WriteStream {

  HttpClientRequest(HttpClientConnection conn, final String method, final String uri,
                    final HttpResponseHandler respHandler) {
    this.request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), uri);
    this.conn = conn;
    this.method = method;
    this.uri = uri;
    this.respHandler = respHandler;
  }

  private final HttpRequest request;
  private final HttpClientConnection conn;
  private final String method;
  private final String uri;
  private final HttpResponseHandler respHandler;

  private DoneHandler drainHandler;
  private boolean headWritten;
  private boolean sent;

  private Map<String, String> cookies;

  // Public API ---------------------------------------------------------------------------------------------------

  public HttpClientRequest putHeader(String key, Object value) {
    request.setHeader(key, value);
    return this;
  }

  public HttpClientRequest putHeader(String key, Iterable<String> values) {
    request.setHeader(key, values);
    return this;
  }

  public HttpClientRequest addHeader(String key, Object value) {
    request.addHeader(key, value);
    return this;
  }

  public HttpClientRequest putAllHeaders(Map<String,? extends Object> m)  {
    for (Map.Entry<String, ? extends Object> entry: m.entrySet()) {
      request.setHeader(entry.getKey(), entry.getValue());
    }
    return this;
  }

  public String getHeader(String key) {
    return request.getHeader(key);
  }

  public List<String> getHeaders(String key) {
    return request.getHeaders(key);
  }

  public Set<String> getHeaderNames() {
    return request.getHeaderNames();
  }

  public void writeBuffer(Buffer chunk) {
    write(chunk._toChannelBuffer(), null);
  }

  public HttpClientRequest write(Buffer chunk) {
    return write(chunk._toChannelBuffer(), null);
  }

  public HttpClientRequest write(String chunk) {
    return write(Buffer.fromString(chunk)._toChannelBuffer(), null);
  }

  public HttpClientRequest write(String chunk, String enc) {
    return write(Buffer.fromString(chunk, enc)._toChannelBuffer(), null);
  }

  public HttpClientRequest write(Buffer chunk, DoneHandler done) {
    return write(chunk._toChannelBuffer(), done);
  }

  public HttpClientRequest write(String chunk, DoneHandler done) {
    return write(Buffer.fromString(chunk)._toChannelBuffer(), done);
  }

  public HttpClientRequest write(String chunk, String enc, DoneHandler done) {
    return write(Buffer.fromString(chunk, enc)._toChannelBuffer(), done);
  }

  public void end() {
    sent = true;
    if (!headWritten) {
      // No body
      writeHead(false);
    } else {
      //Body written - we use HTTP chunking so must send an empty buffer
      //TODO we could send some trailers at this point
      conn.write(new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER), this);
    }
    conn.endRequest(this);
  }

  public void setWriteQueueMaxSize(int maxSize) {
    conn.setWriteQueueMaxSize(maxSize);
  }

  public boolean writeQueueFull() {
    return conn.writeQueueFull();
  }

  public void drain(DoneHandler handler) {
    this.drainHandler = handler;
    conn.handleInterestedOpsChanged(); //If the channel is already drained, we want to call it immediately
  }

  // Internal API -------------------------------------------------------------------------------------------

  void handleInterestedOpsChanged() {
    if (drainHandler != null) {
      drainHandler.onDone();
    }
  }

  HttpResponseHandler getResponseHandler() {
    return respHandler;
  }

  // Impl ---------------------------------------------------------------------------------------------------

  private void writeHead(boolean chunked) {
    conn.setCurrentRequest(this);
    if (chunked) {
      request.setChunked(true);
      request.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
    }
    request.setHeader(HttpHeaders.Names.HOST, conn.hostHeader);
    request.setHeader(HttpHeaders.Names.CONNECTION, conn.keepAlive ? HttpHeaders.Values.KEEP_ALIVE : HttpHeaders.Values
        .CLOSE);
    if (cookies != null) {
      CookieEncoder httpCookieEncoder = new CookieEncoder(false);
      for (Map.Entry<String, String> cookie : cookies.entrySet()) {
        httpCookieEncoder.addCookie(cookie.getKey(), cookie.getValue());
      }
      request.setHeader(HttpHeaders.Names.COOKIE, httpCookieEncoder.encode());
    }

    conn.write(request, this);
  }

  private HttpClientRequest write(ChannelBuffer buff, DoneHandler done) {
    if (sent) {
      throw new IllegalStateException("Response complete");
    }
    if (!headWritten) {
      writeHead(true);
      headWritten = true;
    }
    ChannelFuture writeFuture = conn.write(new DefaultHttpChunk(buff), this);
    if (done != null) {
      conn.addFuture(done, writeFuture);
    }
    return this;
  }
}
