package org.nodex.core.http;

/**
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:16
 */
public abstract class HttpRequestHandler {
  public abstract void onRequest(HttpServerRequest req, HttpServerResponse resp);
}
