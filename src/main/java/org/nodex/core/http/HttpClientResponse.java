package org.nodex.core.http;

import org.nodex.core.DoneHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.streams.ReadStream;

import java.util.Map;

/**
 * User: timfox
 * Date: 22/07/2011
 * Time: 12:01
 */
public class HttpClientResponse implements ReadStream {

  private final HttpClientConnection conn;
  private DataHandler dataHandler;
  private DoneHandler endHandler;

  // Public API --------------------------------------------------------------------------------------------------

  public final Map<String, String> headers;
  public Map<String, String> trailers;
  public final int statusCode;

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

  HttpClientResponse(HttpClientConnection conn, int statusCode, Map<String, String> headers) {
    this.conn = conn;
    this.statusCode = statusCode;
    this.headers = headers;
  }

  void handleChunk(Buffer data) {
    if (dataHandler != null) {
      dataHandler.onData(data);
    }
  }

  void handleEnd(Map<String, String> trailers) {
    this.trailers = trailers;

    if (endHandler != null) {
      endHandler.onDone();
    }
  }
}
