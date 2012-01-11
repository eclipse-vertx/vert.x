package org.vertx.java.tests.http;

import org.vertx.java.core.Immutable;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ResponseInfo implements Immutable {
  public final int statusCode;
  public final String statusMessage;
  public final String body;
  public final Map<String, String> headers;

  public ResponseInfo(int statusCode, String statusMessage, String body, Map<String, String> headers) {
    this.statusCode = statusCode;
    this.statusMessage = statusMessage;
    this.body = body;
    this.headers = headers;
  }
}
