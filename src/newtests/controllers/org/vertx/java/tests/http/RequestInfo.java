package org.vertx.java.tests.http;

import org.vertx.java.core.Immutable;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RequestInfo implements Immutable {
  public final String method;
  public final String uri;
  public final String path;
  public final String query;
  public final Map<String, String> params;
  public final Map<String, String> headers;
  public String body;

  public RequestInfo(String method, String uri, String path, String query, Map<String, String> params, Map<String, String> headers, String body) {
    this.method = method;
    this.uri = uri;
    this.path = path;
    this.query = query;
    this.params = params;
    this.headers = headers;
    this.body = body;
  }
}