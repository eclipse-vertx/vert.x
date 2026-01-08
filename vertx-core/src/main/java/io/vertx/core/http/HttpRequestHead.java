package io.vertx.core.http;

import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.MultiMap;

/**
 * The state of the HTTP request head:
 *
 * <ul>
 *   <li>Method / URI</li>
 *   <li>Headers</li>
 * </ul>
 */
@VertxGen
public interface HttpRequestHead {

  /**
   * @return the HTTP method for the request.
   */
  HttpMethod method();

  /**
   * @return the URI of the request. This is usually a relative URI
   */
  String uri();

  /**
   * Returns the path component of the HTTP request URI.
   * <p>
   * This is the raw, non-normalized path as received from the client.
   * It may contain duplicated separators or traversal segments such as {@code ".."}.
   * <p>
   * For security-sensitive logic (for example access control, routing or
   * filesystem checks), applications should prefer using a normalized path
   * provided by the framework instead of relying on this raw value.
   *
   * @return the raw path component of the request URI
   */
  @Nullable
  String path();

  /**
   * @return the query part of the uri. For example {@code someparam=32&amp;someotherparam=x}
   */
  @Nullable
  String query();

  /**
   * @return the headers
   */
  MultiMap headers();

  /**
   * Return the first header value with the specified name
   *
   * @param headerName  the header name
   * @return the header value
   */
  @Nullable
  default String getHeader(String headerName) {
    return headers().get(headerName);
  }

  /**
   * Return the first header value with the specified name
   *
   * @param headerName  the header name
   * @return the header value
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default String getHeader(CharSequence headerName) {
    return headers().get(headerName);
  }
}
