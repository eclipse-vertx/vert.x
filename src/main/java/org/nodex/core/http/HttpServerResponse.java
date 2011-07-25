package org.nodex.core.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.DefaultFileRegion;
import org.jboss.netty.channel.FileRegion;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieDecoder;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpChunkTrailer;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.stream.ChunkedFile;
import org.jboss.netty.util.CharsetUtil;
import org.nodex.core.DoneHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.streams.WriteStream;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:20
 *
 * TODO common functionality with NetSocket can be put in common base class
 */
public class HttpServerResponse implements WriteStream {
  private static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
  private static final String HTTP_DATE_GMT_TIMEZONE = "GMT";

  public final Map<String, String> headers = new HashMap<String, String>();
  public final Map<String, String> trailers = new HashMap<String, String>();

  private final boolean keepAlive;
  private final String cookieString;
  private final HttpServerConnection conn;

  private boolean headWritten;
  private ChannelFuture writeFuture;
  private boolean written;
  private DoneHandler drainHandler;

  HttpServerResponse(boolean keepAlive, String cookieString, HttpServerConnection conn) {
    this.keepAlive = keepAlive;
    this.cookieString = cookieString;
    this.conn = conn;
  }

  // Public API -----------------------------------------------------------------------------------------------------

  public int statusCode = HttpResponseStatus.OK.getCode();

  public void setWriteQueueMaxSize(int size) {
    conn.setWriteQueueMaxSize(size);
  }

  public boolean writeQueueFull() {
    return conn.writeQueueFull();
  }

  public void drain(DoneHandler handler) {
    this.drainHandler = handler;
    conn.handleInterestedOpsChanged(); //If the channel is already drained, we want to call it immediately
  }

  public void writeBuffer(Buffer chunk) {
    write(chunk._toChannelBuffer(), null);
  }

  public HttpServerResponse write(Buffer chunk) {
    return write(chunk._toChannelBuffer(), null);
  }

  public HttpServerResponse write(String chunk, String enc) {
    return write(Buffer.fromString(chunk, enc)._toChannelBuffer(), null);
  }

  public HttpServerResponse write(String chunk) {
    return write(Buffer.fromString(chunk)._toChannelBuffer(), null);
  }

  public HttpServerResponse write(Buffer chunk, DoneHandler done) {
    return write(chunk._toChannelBuffer(), done);
  }

  public HttpServerResponse write(String chunk, String enc, DoneHandler done) {
    return write(Buffer.fromString(chunk, enc)._toChannelBuffer(), done);
  }

  public HttpServerResponse write(String chunk, DoneHandler done) {
    return write(Buffer.fromString(chunk)._toChannelBuffer(), done);
  }

  public void end() {
    if (!headWritten) {
      //No body
      HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.valueOf(statusCode));
      writeHeaders(response);
      response.setHeader(CONTENT_LENGTH, 0);
      writeFuture = conn.write(response);
    } else {
      //Body written - We use HTTP chunking so we need to write a zero length chunk to signify the end
      HttpChunk nettyChunk;
      if (trailers.isEmpty()) {
        nettyChunk = new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER);
      } else {
        HttpChunkTrailer trailer = new DefaultHttpChunkTrailer();
        for (Map.Entry<String, String> entry : trailers.entrySet()) {
          trailer.setHeader(entry.getKey(), entry.getValue());
        }
        nettyChunk = trailer;
      }
      writeFuture = conn.write(nettyChunk);
    }

    // Close the non-keep-alive connection after the write operation is done.
    if (!keepAlive) {
      writeFuture.addListener(ChannelFutureListener.CLOSE);
    }
    written = true;
    conn.responseComplete();
  }

  //TODO - really this should take a file handle from the file system API
  public HttpServerResponse sendFile(String filename) {
    if (headWritten) {
      throw new IllegalStateException("Response complete");
    }

    RandomAccessFile raf;
    try {
      raf = new RandomAccessFile(filename, "r");
    } catch (FileNotFoundException e) {
      conn.handleException(e);
      return this;
    }
    try {
      File file = new File(filename);
      long fileLength = raf.length();

      HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);

      MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
      headers.put(Names.CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
      headers.put(Names.CONTENT_LENGTH, String.valueOf(fileLength));
      SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
      dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));
      headers.put(Names.LAST_MODIFIED, dateFormatter.format(new Date(file.lastModified())));

      writeHeaders(response);
      conn.write(response);

      // Write the content.
      ChannelFuture writeFuture;
      if (conn.isSSL()) {
        // Cannot use zero-copy with HTTPS.
        writeFuture = conn.write(new ChunkedFile(raf, 0, fileLength, 8192));
      } else {
        // No encryption - use zero-copy.
        final FileRegion region =
            new DefaultFileRegion(raf.getChannel(), 0, fileLength);
        conn.write(region);
      }
    } catch (IOException e) {
      conn.handleException(e);
    }

    written = headWritten = true;
    return this;
  }

  // Internal API ---------------------------------------------------------------------------------------------

  void handleInterestedOpsChanged() {
    if (drainHandler != null) {
      drainHandler.onDone();
    }
  }

  // Impl -----------------------------------------------------------------------------------------------------

  private void writeHeaders(HttpResponse response) {
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      response.setHeader(entry.getKey(), entry.getValue());
    }
    // Encode the cookie.
    if (cookieString != null) {
      CookieDecoder cookieDecoder = new CookieDecoder();
      Set<Cookie> cookies = cookieDecoder.decode(cookieString);
      if (!cookies.isEmpty()) {
        // Reset the cookies if necessary.
        CookieEncoder cookieEncoder = new CookieEncoder(true);
        for (Cookie cookie : cookies) {
          cookieEncoder.addCookie(cookie);
        }
        response.addHeader(HttpHeaders.Names.SET_COOKIE, cookieEncoder.encode());
      }
    }
  }

  /*
  We use HTTP chunked encoding and each write has it's own chunk
  TODO non chunked encoding
  Non chunked encoding does not work well with async writes since normally do not know Content-Length in advance
  and need to know this for non chunked encoding
   */
  private HttpServerResponse write(ChannelBuffer chunk, final DoneHandler done) {
    if (written) {
      throw new IllegalStateException("Response complete");
    }

    if (!headWritten) {
      HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.valueOf(statusCode));
      response.setChunked(true);
      response.setHeader(Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
      writeHeaders(response);
      conn.write(response);
      headWritten = true;
    }

    HttpChunk nettyChunk = new DefaultHttpChunk(chunk);
    writeFuture = conn.write(nettyChunk);
    if (done != null) {
      conn.addFuture(done, writeFuture);
    }

    return this;
  }
}
