/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;


/**
 * Helper wrapper class which allows to assemble a HttpContent and a HttpResponse into one "packet" and so more
 * efficient write it through the pipeline.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class AssembledHttpResponse implements HttpResponse, HttpContent {

  private boolean head;
  private HttpResponseStatus status;
  private HttpVersion version;
  private HttpHeaders headers;
  private final ByteBuf content;
  private DecoderResult result = DecoderResult.SUCCESS;

  AssembledHttpResponse(boolean head, HttpVersion version, HttpResponseStatus status, HttpHeaders headers) {
    this(head, version, status, headers, Unpooled.EMPTY_BUFFER);
  }

  AssembledHttpResponse(boolean head, HttpVersion version, HttpResponseStatus status, HttpHeaders headers, ByteBuf content) {
    this.head = head;
    this.status = status;
    this.version = version;
    this.headers = headers;
    this.content = content;
  }

  boolean head() {
    return head;
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
  public HttpContent retainedDuplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpContent replace(ByteBuf content) {
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
    return status;
  }

  @Override
  public AssembledHttpResponse setStatus(HttpResponseStatus status) {
    this.status = status;
    return this;
  }

  @Override
  public AssembledHttpResponse setProtocolVersion(HttpVersion version) {
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
  public AssembledHttpResponse touch() {
    content.touch();
    return this;
  }

  @Override
  public AssembledHttpResponse touch(Object hint) {
    content.touch(hint);
    return this;
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

  @Override
  public ByteBuf content() {
    return content;
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
