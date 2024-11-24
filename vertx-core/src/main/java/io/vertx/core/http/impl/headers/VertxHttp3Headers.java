/*
package io.vertx.core.http.impl.headers;

import io.netty.incubator.codec.http3.DefaultHttp3Headers;
import io.netty.incubator.codec.http3.Http3Headers;
import io.vertx.core.MultiMap;

*/
/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 *//*

public class VertxHttp3Headers extends VertxHttpHeadersBase<Http3Headers> implements VertxHttpHeaders {

  public VertxHttp3Headers() {
    this(new DefaultHttp3Headers());
  }

  public VertxHttp3Headers(Http3Headers headers) {
    super(headers, new Http3HeadersAdaptor(headers));
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
    return new Http3HeadersAdaptor(headers);
  }

}
*/
