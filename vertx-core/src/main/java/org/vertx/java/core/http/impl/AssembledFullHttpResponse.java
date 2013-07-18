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
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;

/**
 * Helper wrapper class which allows to assemble a LastHttpContent and a HttpResponse into one "packet" and so more
 * efficient write it through the pipeline.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class AssembledFullHttpResponse extends AssembledHttpResponse implements FullHttpResponse {

  public AssembledFullHttpResponse(HttpResponse response, LastHttpContent content) {
    this(response, content.content(), content.trailingHeaders());
  }

  public AssembledFullHttpResponse(HttpResponse response) {
    this(response, Unpooled.EMPTY_BUFFER);
  }

  public AssembledFullHttpResponse(HttpResponse response, ByteBuf buf) {
    super(response, toLastContent(buf, null));
  }

  public AssembledFullHttpResponse(HttpResponse response, ByteBuf buf, HttpHeaders trailingHeaders) {
    super(response, toLastContent(buf, trailingHeaders));
  }

  private static LastHttpContent toLastContent(ByteBuf buf, HttpHeaders trailingHeaders) {
    if (buf.isReadable()) {
      if (trailingHeaders == null) {
        return new DefaultLastHttpContent(buf);
      } else {
        return new AssembledLastHttpContent(buf, trailingHeaders);
      }
    } else {
      if (trailingHeaders == null) {
        return LastHttpContent.EMPTY_LAST_CONTENT;
      } else {
        return new AssembledLastHttpContent(Unpooled.EMPTY_BUFFER, trailingHeaders);
      }
    }
  }

  @Override
  public AssembledFullHttpResponse setStatus(HttpResponseStatus status) {
    super.setStatus(status);
    return this;
  }

  @Override
  public AssembledFullHttpResponse retain(int increment) {
    super.retain(increment);
    return this;
  }

  @Override
  public AssembledFullHttpResponse retain() {
    super.retain();
    return this;
  }

  @Override
  public AssembledFullHttpResponse duplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public AssembledFullHttpResponse copy() {
    throw new UnsupportedOperationException();
  }

  @Override
  public AssembledFullHttpResponse setProtocolVersion(HttpVersion version) {
    super.setProtocolVersion(version);
    return this;
  }

  @Override
  public HttpHeaders trailingHeaders() {
    return ((LastHttpContent) content).trailingHeaders();
  }
}
