package io.vertx5.core.http;

import io.netty5.buffer.api.Buffer;
import io.netty5.handler.codec.DecoderResult;
import io.netty5.handler.codec.http.HttpContent;
import io.netty5.handler.codec.http.HttpHeaders;
import io.netty5.handler.codec.http.HttpMessage;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.handler.codec.http.HttpVersion;
import io.netty5.util.Send;

class PartialHttpRequest<R extends HttpContent<R>> extends PartialHttpMessage<PartialHttpRequest<R>> implements HttpRequest {

  private final HttpRequest message;
  private final HttpContent<R> content;

  PartialHttpRequest(HttpRequest message, HttpContent<R> content) {
    this.message = message;
    this.content = content;
  }

  @Override
  public HttpMethod method() {
    return message.method();
  }

  @Override
  public HttpRequest setMethod(HttpMethod method) {
    message.setMethod(method);
    return this;
  }

  @Override
  public String uri() {
    return message.uri();
  }

  @Override
  public HttpRequest setUri(String uri) {
    message.setUri(uri);
    return this;
  }

  @Override
  public Buffer payload() {
    return content.payload();
  }

  @Override
  public PartialHttpRequest<R> copy() {
    return new PartialHttpRequest<>(message, content.copy());
  }

  @Override
  public HttpVersion getProtocolVersion() {
    return message.getProtocolVersion();
  }

  @Override
  public HttpVersion protocolVersion() {
    return message.protocolVersion();
  }

  @Override
  public HttpRequest setProtocolVersion(HttpVersion version) {
    message.setProtocolVersion(version);
    return this;
  }

  @Override
  public HttpHeaders headers() {
    return message.headers();
  }

  @Override
  public DecoderResult decoderResult() {
    return message.decoderResult();
  }

  @Override
  public void setDecoderResult(DecoderResult result) {
    message.setDecoderResult(result);
  }

  @Override
  public Send<PartialHttpRequest<R>> send() {
    Send<R> s = content.send();
    return new Send<PartialHttpRequest<R>>() {
      @Override
      public PartialHttpRequest<R> receive() {
        return new PartialHttpRequest<>(message, s.receive());
      }
      @Override
      public void close() {
        s.close();
      }
      @Override
      public boolean referentIsInstanceOf(Class<?> cls) {
        return cls.isAssignableFrom(PartialHttpRequest.class);
      }
    };
  }

  @Override
  public void close() {
    content.close();
  }

  @Override
  public boolean isAccessible() {
    return content.isAccessible();
  }
}
