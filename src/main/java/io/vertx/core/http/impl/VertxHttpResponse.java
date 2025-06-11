package io.vertx.core.http.impl;

import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

class VertxHttpResponse implements io.netty.handler.codec.http.HttpResponse {

  private boolean head;
  private HttpResponseStatus status;
  private HttpVersion version;
  private HttpHeaders headers;
  private DecoderResult result = DecoderResult.SUCCESS;

  VertxHttpResponse(boolean head, HttpVersion version, HttpResponseStatus status, HttpHeaders headers) {
    this.head = head;
    this.status = status;
    this.version = version;
    this.headers = headers;
  }

  boolean head() {
    return head;
  }

  @Override
  public HttpResponseStatus getStatus() {
    return status;
  }

  @Override
  public VertxHttpResponse setStatus(HttpResponseStatus status) {
    this.status = status;
    return this;
  }

  @Override
  public VertxHttpResponse setProtocolVersion(HttpVersion version) {
    this.version = version;
    return this;
  }

  @Override
  public HttpVersion getProtocolVersion() {
    return version;
  }

  @Override
  public HttpVersion protocolVersion() {
    return version;
  }

  @Override
  public HttpResponseStatus status() {
    return status;
  }

  @Override
  public DecoderResult decoderResult() {
    return result;
  }

  @Override
  public HttpHeaders headers() {
    return headers;
  }

  @Override
  public DecoderResult getDecoderResult() {
    return result;
  }

  @Override
  public void setDecoderResult(DecoderResult result) {
    this.result = result;
  }

}
