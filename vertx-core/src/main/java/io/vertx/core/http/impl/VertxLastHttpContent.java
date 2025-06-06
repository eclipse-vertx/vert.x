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
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.LastHttpContent;

/**
 * Helper wrapper class which allows to assemble a ByteBuf and a HttpHeaders into one "packet" and so more
 * efficient write it through the pipeline.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class VertxLastHttpContent extends VertxHttpObject implements LastHttpContent {

  private final HttpHeaders trailingHeaders;
  private DecoderResult result;
  private ByteBuf content;

  VertxLastHttpContent(ByteBuf content, HttpHeaders trailingHeaders) {
    this(content, trailingHeaders, DecoderResult.SUCCESS);
  }

  VertxLastHttpContent(ByteBuf content, HttpHeaders trailingHeaders, DecoderResult result) {
    super(true);
    this.trailingHeaders = trailingHeaders;
    this.result = result;
    this.content = content;
  }

  @Override
  public HttpHeaders trailingHeaders() {
    return trailingHeaders;
  }

  @Override
  public LastHttpContent copy() {
    throw new UnsupportedOperationException();
  }

  @Override
  public LastHttpContent retain(int increment) {
    content.retain(increment);
    return this;
  }

  @Override
  public LastHttpContent retain() {
    content.retain();
    return this;
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

  @Override
  public LastHttpContent duplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public LastHttpContent replace(ByteBuf content) {
    throw new UnsupportedOperationException();
  }

  @Override
  public LastHttpContent retainedDuplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public DecoderResult decoderResult() {
    return result;
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
  public VertxLastHttpContent touch() {
    content.touch();
    return this;
  }

  @Override
  public VertxLastHttpContent touch(Object hint) {
    content.touch(hint);
    return this;
  }
}
