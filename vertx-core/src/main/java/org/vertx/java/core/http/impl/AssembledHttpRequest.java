/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.java.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;


/**
 * Helper wrapper class which allows to assemble a HttpContent and a HttpRequest into one "packet" and so more
 * efficient write it through the pipeline.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class AssembledHttpRequest implements HttpContent, HttpRequest {
  private final HttpRequest request;
  protected final HttpContent content;

  AssembledHttpRequest(HttpRequest request, ByteBuf buf) {
    this(request, new DefaultHttpContent(buf));
  }

  AssembledHttpRequest(HttpRequest request, HttpContent content) {
    this.request = request;
    this.content = content;
  }

  @Override
  public AssembledHttpRequest copy() {
    throw new UnsupportedOperationException();
  }

  @Override
  public AssembledHttpRequest duplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public AssembledHttpRequest retain() {
    content.retain();
    return this;
  }

  @Override
  public AssembledHttpRequest retain(int increment) {
    content.retain(increment);
    return this;
  }

  @Override
  public HttpMethod getMethod() {
    return request.getMethod();
  }

  @Override
  public String getUri() {
    return request.getUri();
  }

  @Override
  public HttpHeaders headers() {
    return request.headers();
  }

  @Override
  public HttpRequest setMethod(HttpMethod method) {
    return request.setMethod(method);
  }

  @Override
  public HttpVersion getProtocolVersion() {
    return request.getProtocolVersion();
  }

  @Override
  public HttpRequest setUri(String uri) {
    return request.setUri(uri);
  }

  @Override
  public HttpRequest setProtocolVersion(HttpVersion version) {
    return request.setProtocolVersion(version);
  }

  @Override
  public DecoderResult getDecoderResult() {
    return request.getDecoderResult();
  }

  @Override
  public void setDecoderResult(DecoderResult result) {
    request.setDecoderResult(result);
  }

  @Override
  public ByteBuf content() {
    return content.content();
  }

  @Override
  public int refCnt() {
    return content.refCnt();
  }

  @Override
  public boolean release() {
    return content.release();
  }

  @Override
  public boolean release(int decrement) {
    return content.release(decrement);
  }
}
