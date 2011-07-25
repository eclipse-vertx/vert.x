package org.nodex.core.http;

import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.nodex.core.DoneHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.streams.ReadStream;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

/**
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:19
 */
public class HttpServerRequest implements ReadStream {

  private Map<String, List<String>> params;
  private DataHandler dataHandler;
  private DoneHandler endHandler;
  private final HttpServerConnection conn;

  HttpServerRequest(String method, String uri, Map<String, String> headers, HttpServerConnection conn) {
    this.method = method;
    URI theURI;
    try {
      theURI = new URI(uri);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid uri " + uri); //Should never happen
    }
    this.path = theURI.getPath();
    this.uri = uri;
    this.headers = headers;
    this.conn = conn;
  }

  // Public API ----------------------------------------------------------------------------------------------

  public final String method;
  public final String uri;
  public final String path;
  public final Map<String, String> headers;

  public void data(DataHandler dataHandler) {
    this.dataHandler = dataHandler;
  }

  public void pause() {
    conn.pause();
  }

  public void resume() {
    conn.resume();
  }

  public void end(DoneHandler handler) {
    this.endHandler = handler;
  }

  public String getParam(String param) {
    if (params == null) {
      QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri);
      params = queryStringDecoder.getParameters();
    }
    List<String> list = params.get(param);
    if (list != null) {
      return list.get(0);
    } else {
      return null;
    }
  }

  // Internal API ---------------------------------------------------------------------------------------------

  void handleData(Buffer data) {
    if (dataHandler != null) {
      dataHandler.onData(data);
    }
  }

  void handleEnd() {
    if (endHandler != null) {
      endHandler.onDone();
    }
  }

}
