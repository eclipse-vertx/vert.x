/*
package io.vertx.core.http.impl.headers;

import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.core.MultiMap;

*/
/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 *//*

public class VertxHttp2Headers extends VertxHttpHeadersBase<Http2Headers> implements VertxHttpHeaders {

  public VertxHttp2Headers() {
    this(new DefaultHttp2Headers());
  }

  public VertxHttp2Headers(Http2Headers headers) {
    super(headers, new Http2HeadersAdaptor(headers));
  }

  @Override
  public void method(CharSequence value) {
    this.headers.method(value);
  }

  @Override
  public void authority(CharSequence authority) {
    this.headers.authority(authority);
  }

  @Override
  public CharSequence authority() {
    return this.headers.authority();
  }

  @Override
  public void path(CharSequence value) {
    this.headers.path(value);
  }

  @Override
  public void scheme(CharSequence value) {
    this.headers.scheme(value);
  }

  @Override
  public CharSequence path() {
    return this.headers.path();
  }

  @Override
  public CharSequence method() {
    return this.headers.method();
  }

  @Override
  public CharSequence status() {
    return this.headers.status();
  }

  @Override
  public void status(CharSequence status) {
    this.headers.status(status);
  }

  @Override
  public CharSequence scheme() {
    return this.headers.scheme();
  }

  @Override
  public MultiMap toHeaderAdapter() {
    return new Http2HeadersAdaptor(headers);
  }

}
*/
