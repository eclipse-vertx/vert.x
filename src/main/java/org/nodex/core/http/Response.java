package org.nodex.core.http;

import java.util.Map;

/**
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:20
 */
public class Response {
  public final Map<String, String> headers;

  protected Response(Map<String, String> headers) {
    this.headers = headers;
  }
}
