/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.http.*;


/**
 * Helper wrapper class which allows to assemble a HttpContent and a HttpRequest into one "packet" and so more
 * efficient write it through the pipeline.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class VertxAssembledHttpRequest extends VertxHttpObject implements HttpContent, HttpRequest {

  private final HttpRequest request;
  protected final HttpContent content;

  VertxAssembledHttpRequest(HttpRequest request, ByteBuf buf) {
    this(request, new DefaultHttpContent(buf), false);
  }

  VertxAssembledHttpRequest(HttpRequest request, HttpContent content, boolean ended) {
    super(ended);
    this.request = request;
    this.content = content;
  }

  @Override
  public VertxAssembledHttpRequest copy() {
    throw new UnsupportedOperationException();
  }

  @Override
  public VertxAssembledHttpRequest duplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpContent retainedDuplicate() {
    throw new UnsupportedMessageTypeException();
  }

  @Override
  public HttpContent replace(ByteBuf content) {
    throw new UnsupportedMessageTypeException();
  }

  @Override
  public VertxAssembledHttpRequest retain() {
    content.retain();
    return this;
  }

  @Override
  public VertxAssembledHttpRequest retain(int increment) {
    content.retain(increment);
    return this;
  }

  @Override
  public VertxAssembledHttpRequest touch(Object hint) {
    content.touch(hint);
    return this;
  }

  @Override
  public VertxAssembledHttpRequest touch() {
    content.touch();
    return this;
  }

  @Override
  public HttpMethod method() {
    return request.method();
  }

  @Override
  public HttpMethod getMethod() {
    return request.method();
  }

  @Override
  public String uri() {
    return request.uri();
  }

  @Override
  public String getUri() {
    return request.uri();
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
  public HttpVersion protocolVersion() {
    return request.protocolVersion();
  }

  @Override
  public HttpVersion getProtocolVersion() {
    return request.protocolVersion();
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
  public DecoderResult decoderResult() {
    return request.decoderResult();
  }

  @Override
  public DecoderResult getDecoderResult() {
    return request.decoderResult();
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
