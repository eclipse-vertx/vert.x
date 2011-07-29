package org.nodex.core.http;

/**
 * User: timfox
 * Date: 22/07/2011
 * Time: 12:19
 */
public interface HttpResponseHandler {
  void onResponse(HttpClientResponse response);
}
