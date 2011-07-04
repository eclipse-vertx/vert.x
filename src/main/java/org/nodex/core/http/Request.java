package org.nodex.core.http;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;

import java.util.Map;

/**
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:19
 */
public class Request {
  public final String method;
  public final String uri;
  public final Map<String, String> headers;

  protected Request(String method, String uri, Map<String, String> headers) {
    this.method = method;
    this.uri = uri;
    this.headers = headers;
  }

  private Callback<Buffer> dataCallback;

  public void data(Callback<Buffer> dataCallback) {
    this.dataCallback = dataCallback;
  }

  void dataReceived(Buffer data) {
    if (dataCallback != null) {
      dataCallback.onEvent(data);
    }
  }

}
