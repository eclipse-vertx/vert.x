package org.nodex.core.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.nodex.core.buffer.Buffer;

import java.util.HashMap;
import java.util.Map;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:20
 */
public class Response {
  public final Map<String, String> headers = new HashMap<String, String>();
  public int statusCode;

  private Channel channel;

  private boolean headWritten;
  private final boolean keepAlive;
  private ChannelFuture writeFuture;

  Response(Channel channel, boolean keepAlive) {
    this.channel = channel;
    this.keepAlive = keepAlive;
  }

  public void setHeader(String key, String value) {
    headers.put(key, value);
  }

  public String getHeader(String key) {
    return headers.get(key);
  }

  public void removeHeader(String key) {
    headers.remove(key);
  }

  public void write(Buffer chunk) {
    write(chunk._toChannelBuffer());
  }

  public void write(String chunk, String enc) {
    write(Buffer.fromString(chunk, enc)._toChannelBuffer());
  }

  public void end() {
    // Close the non-keep-alive connection after the write operation is done.
    if (!keepAlive) {
      writeFuture.addListener(ChannelFutureListener.CLOSE);
    }
  }

  private void write(ChannelBuffer chunk) {

    if (!headWritten) {

      HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
      response.setContent(chunk);
      //response.setHeader(CONTENT_TYPE, "text/plain; charset=UTF-8");

      if (keepAlive) {
        // Add 'Content-Length' header only for a keep-alive connection.
        response.setHeader(CONTENT_LENGTH, response.getContent().readableBytes());
      }

      /*
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
      */

      // Write the response.
      writeFuture = channel.write(response);
      headWritten = true;
    } else {
      //FIXME??
      //HttpChunk nettychunk = new HttpChunk(chunk._toChannelBuffer());
    }
  }



}
