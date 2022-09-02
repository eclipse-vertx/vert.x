package io.vertx5.core.http;

import io.netty5.buffer.api.Buffer;
import io.netty5.handler.codec.DecoderResult;
import io.netty5.handler.codec.http.HttpContent;
import io.netty5.handler.codec.http.HttpHeaders;
import io.netty5.handler.codec.http.HttpMessage;
import io.netty5.handler.codec.http.HttpResponse;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http.HttpVersion;
import io.netty5.util.Send;

class PartialHttpResponse<R extends HttpContent<R>> extends PartialHttpMessage<PartialHttpResponse<R>> implements HttpResponse {

  private final HttpResponse message;
  private final HttpContent<R> content;

  PartialHttpResponse(HttpResponse message, HttpContent<R> content) {
    this.message = message;
    this.content = content;
  }

  @Override
  public HttpResponseStatus status() {
    return message.status();
  }

  @Override
  public HttpResponse setStatus(HttpResponseStatus status) {
    message.setStatus(status);
    return this;
  }

  @Override
  public Buffer payload() {
    return content.payload();
  }

  @Override
  public PartialHttpResponse<R> copy() {
    return new PartialHttpResponse<>(message, content.copy());
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
  public HttpResponse setProtocolVersion(HttpVersion version) {
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
  public Send<PartialHttpResponse<R>> send() {
    Send<R> s = content.send();
    return new Send<PartialHttpResponse<R>>() {
      @Override
      public PartialHttpResponse<R> receive() {
        return new PartialHttpResponse<>(message, s.receive());
      }
      @Override
      public void close() {
        s.close();
      }
      @Override
      public boolean referentIsInstanceOf(Class<?> cls) {
        return cls.isAssignableFrom(PartialHttpResponse.class);
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
