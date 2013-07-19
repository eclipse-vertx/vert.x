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
 * Helper wrapper class which allows to assemble a HttpContent and a HttpResponse into one "packet" and so more
 * efficient write it through the pipeline.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class AssembledHttpResponse implements HttpResponse, HttpContent {

  private final HttpResponse response;
  protected final HttpContent content;

  AssembledHttpResponse(HttpResponse response, HttpContent content) {
    this.response = response;
    this.content = content;
  }

  AssembledHttpResponse(HttpResponse response, ByteBuf buf) {
    this(response, new DefaultHttpContent(buf));
  }

  @Override
  public HttpContent copy() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpContent duplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public AssembledHttpResponse retain() {
    content.retain();
    return this;
  }

  @Override
  public AssembledHttpResponse retain(int increment) {
    content.retain(increment);
    return this;
  }

  @Override
  public HttpResponseStatus getStatus() {
    return response.getStatus();
  }

  @Override
  public AssembledHttpResponse setStatus(HttpResponseStatus status) {
    response.setStatus(status);
    return this;
  }

  @Override
  public AssembledHttpResponse setProtocolVersion(HttpVersion version) {
    response.setProtocolVersion(version);
    return this;
  }

  @Override
  public HttpVersion getProtocolVersion() {
    return response.getProtocolVersion();
  }

  @Override
  public HttpHeaders headers() {
    return response.headers();
  }

  @Override
  public DecoderResult getDecoderResult() {
    return response.getDecoderResult();
  }

  @Override
  public void setDecoderResult(DecoderResult result) {
    response.setDecoderResult(result);
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
