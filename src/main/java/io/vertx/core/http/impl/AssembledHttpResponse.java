/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;


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
