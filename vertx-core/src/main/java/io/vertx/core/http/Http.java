package io.vertx.core.http;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Http {

  HttpServerOptions serverOptions();

  HttpClientOptions clientOptions();

  /**
   * Create an HTTP/HTTPS server
   */
  HttpServer createHttpServer();

  /**
   * Create a HTTP/HTTPS client
   */
  HttpClient createHttpClient(HttpClientOptions options);

}
