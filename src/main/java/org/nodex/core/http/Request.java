package org.nodex.core.http;

import java.util.Map;

/**
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:19
 */
public class Request {
  public final String method;
  public final String url;
  public final Map<String, String> headers;

  protected Request(String method, String url, Map<String, String> headers) {
    this.method = method;
    this.url = url;
    this.headers = headers;
  }


}
