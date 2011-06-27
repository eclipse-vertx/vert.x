package org.nodex.core.http;

/**
 * Created by IntelliJ IDEA.
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:16
 * To change this template use File | Settings | File Templates.
 */
public abstract class HttpCallback {
  abstract void onRequest(Request req, Response resp);
}
