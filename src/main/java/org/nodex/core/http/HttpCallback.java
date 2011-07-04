package org.nodex.core.http;

/**
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:16
 */
public abstract class HttpCallback {
  public abstract void onRequest(Request req, Response resp);
}
