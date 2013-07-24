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
import io.netty.handler.codec.http.*;

/**
 * Helper wrapper class which allows to assemble a LastHttpContent and a HttpRequest into one "packet" and so more
 * efficient write it through the pipeline.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class AssembledFullHttpRequest extends AssembledHttpRequest implements FullHttpRequest {

  public AssembledFullHttpRequest(HttpRequest request, LastHttpContent content) {
    super(request, content);
  }

  public AssembledFullHttpRequest(HttpRequest request) {
    super(request, LastHttpContent.EMPTY_LAST_CONTENT);
  }

  public AssembledFullHttpRequest(HttpRequest request, ByteBuf buf) {
    super(request, toLastContent(buf));
  }

  private static LastHttpContent toLastContent(ByteBuf buf) {
    if (buf.isReadable()) {
      return new DefaultLastHttpContent(buf);
    } else {
      return LastHttpContent.EMPTY_LAST_CONTENT;
    }
  }

  @Override
  public AssembledFullHttpRequest setUri(String uri) {
    super.setUri(uri);
    return this;
  }

  @Override
  public AssembledFullHttpRequest setProtocolVersion(HttpVersion version) {
    super.setProtocolVersion(version);
    return this;
  }

  @Override
  public AssembledFullHttpRequest setMethod(HttpMethod method) {
    super.setMethod(method);
    return this;
  }

  @Override
  public AssembledFullHttpRequest duplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public AssembledFullHttpRequest copy() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpHeaders trailingHeaders() {
    return ((LastHttpContent) content).trailingHeaders();
  }

  @Override
  public AssembledFullHttpRequest retain() {
    super.retain();
    return this;
  }

  @Override
  public AssembledFullHttpRequest retain(int increment) {
    super.retain(increment);
    return this;
  }
}
