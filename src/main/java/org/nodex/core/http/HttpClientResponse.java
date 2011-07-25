package org.nodex.core.http;

import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.nodex.core.DoneHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.streams.ReadStream;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: timfox
 * Date: 22/07/2011
 * Time: 12:01
 */
public class HttpClientResponse implements ReadStream {

  private final HttpClientConnection conn;
  private DataHandler dataHandler;
  private DoneHandler endHandler;
  private final HttpResponse response;
  private HttpChunkTrailer trailer;

  // Public API --------------------------------------------------------------------------------------------------

  public final int statusCode;

  public String getHeader(String key) {
    return response.getHeader(key);
  }

  public List<String> getHeaders(String key) {
    return response.getHeaders(key);
  }

  public Set<String> getHeaderNames() {
    return response.getHeaderNames();
  }

  public String getTrailer(String key) {
    return trailer.getHeader(key);
  }

  public List<String> getTrailers(String key) {
    return trailer.getHeaders(key);
  }

  public Set<String> getTrailerNames() {
    return trailer.getHeaderNames();
  }

  public void data(DataHandler dataHandler) {
    this.dataHandler = dataHandler;
  }

  public void end(DoneHandler end) {
    this.endHandler = end;
  }

  public void pause() {
    conn.pause();
  }

  public void resume() {
    conn.resume();
  }

  // Internal ----------------------------------------------------------------------------------------------------

  HttpClientResponse(HttpClientConnection conn, int statusCode, HttpResponse response) {
    this.conn = conn;
    this.statusCode = statusCode;
    this.response = response;
  }

  void handleChunk(Buffer data) {
    if (dataHandler != null) {
      dataHandler.onData(data);
    }
  }

  void handleEnd(HttpChunkTrailer trailer) {
    this.trailer = trailer;
    if (endHandler != null) {
      endHandler.onDone();
    }
  }
}
