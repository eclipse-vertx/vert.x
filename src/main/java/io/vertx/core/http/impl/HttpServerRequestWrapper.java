package io.vertx.core.http.impl;

import io.netty.handler.codec.DecoderResult;
import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.WriteStream;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import java.util.Map;
import java.util.Set;

/**
 * A wrapper class that delegates all method calls to the {@link #delegate} instance.
 *
 * Implementing {@link HttpServerRequest} or extending {@link HttpServerRequestInternal} is not encouraged however if that is necessary,
 * implementations should favor extending this class to ensure minimum breakage when new methods are added to the interface.
 *
 * The delegate instance can be accessed using protected final {@link #delegate} field, any implemented method can be overridden.
 */
public class HttpServerRequestWrapper extends HttpServerRequestInternal {

  protected final HttpServerRequestInternal delegate;

  public HttpServerRequestWrapper(HttpServerRequestInternal delegate) {
    if (delegate == null) {
      throw new NullPointerException("Null delegate not allowed");
    }
    this.delegate = delegate;
  }

  @Override
  public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
    return delegate.exceptionHandler(handler);
  }

  @Override
  public HttpServerRequest handler(Handler<Buffer> handler) {
    return delegate.handler(handler);
  }

  @Override
  public HttpServerRequest pause() {
    return delegate.pause();
  }

  @Override
  public HttpServerRequest resume() {
    return delegate.resume();
  }

  @Override
  public HttpServerRequest fetch(long amount) {
    return delegate.fetch(amount);
  }

  @Override
  public HttpServerRequest endHandler(Handler<Void> endHandler) {
    return delegate.endHandler(endHandler);
  }

  @Override
  public HttpVersion version() {
    return delegate.version();
  }

  @Override
  public HttpMethod method() {
    return delegate.method();
  }

  @Override
  public boolean isSSL() {
    return delegate.isSSL();
  }

  @Override
  @Nullable
  public String scheme() {
    return delegate.scheme();
  }

  @Override
  public String uri() {
    return delegate.uri();
  }

  @Override
  @Nullable
  public String path() {
    return delegate.path();
  }

  @Override
  @Nullable
  public String query() {
    return delegate.query();
  }

  @Override
  @Nullable
  public String host() {
    return delegate.host();
  }

  @Override
  public long bytesRead() {
    return delegate.bytesRead();
  }

  @Override
  @CacheReturn
  public HttpServerResponse response() {
    return delegate.response();
  }

  @Override
  @CacheReturn
  public MultiMap headers() {
    return delegate.headers();
  }

  @Override
  @Nullable
  public String getHeader(String headerName) {
    return delegate.getHeader(headerName);
  }

  @Override
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  public String getHeader(CharSequence headerName) {
    return delegate.getHeader(headerName);
  }

  @Override
  @Fluent
  public HttpServerRequest setParamsCharset(String charset) {
    return delegate.setParamsCharset(charset);
  }

  @Override
  public String getParamsCharset() {
    return delegate.getParamsCharset();
  }

  @Override
  @CacheReturn
  public MultiMap params() {
    return delegate.params();
  }

  @Override
  @Nullable
  public String getParam(String paramName) {
    return delegate.getParam(paramName);
  }

  @Override
  public String getParam(String paramName, String defaultValue) {
    return delegate.getParam(paramName, defaultValue);
  }

  @Override
  @CacheReturn
  public SocketAddress remoteAddress() {
    return delegate.remoteAddress();
  }

  @Override
  @CacheReturn
  public SocketAddress localAddress() {
    return delegate.localAddress();
  }

  @Override
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  public SSLSession sslSession() {
    return delegate.sslSession();
  }

  @Override
  @GenIgnore
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    return delegate.peerCertificateChain();
  }

  @Override
  public String absoluteURI() {
    return delegate.absoluteURI();
  }

  @Override
  @Fluent
  public HttpServerRequest bodyHandler(@Nullable Handler<Buffer> bodyHandler) {
    return delegate.bodyHandler(bodyHandler);
  }

  @Override
  public HttpServerRequest body(Handler<AsyncResult<Buffer>> handler) {
    return delegate.body(handler);
  }

  @Override
  public Future<Buffer> body() {
    return delegate.body();
  }

  @Override
  public void end(Handler<AsyncResult<Void>> handler) {
    delegate.end(handler);
  }

  @Override
  public Future<Void> end() {
    return delegate.end();
  }

  @Override
  public void toNetSocket(Handler<AsyncResult<NetSocket>> handler) {
    delegate.toNetSocket(handler);
  }

  @Override
  public Future<NetSocket> toNetSocket() {
    return delegate.toNetSocket();
  }

  @Override
  @Fluent
  public HttpServerRequest setExpectMultipart(boolean expect) {
    return delegate.setExpectMultipart(expect);
  }

  @Override
  public boolean isExpectMultipart() {
    return delegate.isExpectMultipart();
  }

  @Override
  @Fluent
  public HttpServerRequest uploadHandler(@Nullable Handler<HttpServerFileUpload> uploadHandler) {
    return delegate.uploadHandler(uploadHandler);
  }

  @Override
  @CacheReturn
  public MultiMap formAttributes() {
    return delegate.formAttributes();
  }

  @Override
  @Nullable
  public String getFormAttribute(String attributeName) {
    return delegate.getFormAttribute(attributeName);
  }

  @Override
  @CacheReturn
  public int streamId() {
    return delegate.streamId();
  }

  @Override
  public void toWebSocket(Handler<AsyncResult<ServerWebSocket>> handler) {
    delegate.toWebSocket(handler);
  }

  @Override
  public Future<ServerWebSocket> toWebSocket() {
    return delegate.toWebSocket();
  }

  @Override
  public boolean isEnded() {
    return delegate.isEnded();
  }

  @Override
  @Fluent
  public HttpServerRequest customFrameHandler(Handler<HttpFrame> handler) {
    return delegate.customFrameHandler(handler);
  }

  @Override
  @CacheReturn
  public HttpConnection connection() {
    return delegate.connection();
  }

  @Override
  public StreamPriority streamPriority() {
    return delegate.streamPriority();
  }

  @Override
  @Fluent
  public HttpServerRequest streamPriorityHandler(Handler<StreamPriority> handler) {
    return delegate.streamPriorityHandler(handler);
  }

  @Override
  @GenIgnore
  public DecoderResult decoderResult() {
    return delegate.decoderResult();
  }

  @Override
  public @Nullable Cookie getCookie(String name) {
    return delegate.getCookie(name);
  }

  @Override
  public @Nullable Cookie getCookie(String name, String domain, String path) {
    return delegate.getCookie(name, domain, path);
  }

  @Override
  public int cookieCount() {
    return delegate.cookieCount();
  }

  @Override
  @Deprecated
  public Map<String, Cookie> cookieMap() {
    return delegate.cookieMap();
  }

  @Override
  public Set<Cookie> cookies(String name) {
    return delegate.cookies(name);
  }

  @Override
  public Set<Cookie> cookies() {
    return delegate.cookies();
  }

  @Override
  @Fluent
  public HttpServerRequest routed(String route) {
    return delegate.routed(route);
  }

  @Override
  public Context context() {
    return delegate.context();
  }

  @Override
  public Object metric() {
    return delegate.metric();
  }

  @Override
  public Pipe<Buffer> pipe() {
    return delegate.pipe();
  }

  @Override
  public Future<Void> pipeTo(WriteStream<Buffer> dst) {
    return delegate.pipeTo(dst);
  }

  @Override
  public void pipeTo(WriteStream<Buffer> dst, Handler<AsyncResult<Void>> handler) {
    delegate.pipeTo(dst, handler);
  }
}
