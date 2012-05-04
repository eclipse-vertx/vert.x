/*
 * Copyright 2011-2012 the original author or authors.
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

import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpDataHandler;
import org.vertx.java.core.http.HttpMultipartFormDataHandler;
import org.vertx.java.core.http.HttpMultipartFormElementHandler;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.http.impl.netty.codec.http.HttpPostRequestDecoder;
import org.vertx.java.core.http.impl.netty.codec.http.HttpPostRequestDecoder.ErrorDataDecoderException;
import org.vertx.java.core.http.impl.netty.codec.http.HttpPostRequestDecoder.IncompatibleDataDecoderException;

public class PostHttpServerRequest extends DefaultHttpServerRequest {

  private final HttpRequest request;
  private HttpPostRequestDecoder decoder;
  private HttpMultipartFormElementHandler elementHandler = 
      new DefaultHttpMultipartFormElementHandler();
  private NettyHttpDataFactory dataFactory;

  PostHttpServerRequest(ServerConnection conn, String method, String uri, String path, String query,
                        HttpServerResponse response, HttpRequest request) {
    super(conn, method, uri, path, query, response, request);
    this.request = request;
  }

  @Override
  void handleEnd() {
    super.handleEnd();
  }

  @Override
  void handleChunk(HttpChunk chunk) {
    initDecoder();
    try {
      this.decoder.offer(chunk);
    }
    catch (ErrorDataDecoderException e) {
      throw new IllegalStateException(e);
    }
  }

  private void initDecoder() {
    if (elementHandler != null) {
      this.dataFactory = new NettyHttpDataFactory(elementHandler);
      try {
        this.decoder = new HttpPostRequestDecoder(dataFactory, request);
      }
      catch (ErrorDataDecoderException e) {
        throw new IllegalStateException(e);
      }
      catch (IncompatibleDataDecoderException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  @Override
  void handleData(Buffer buf) {
    initDecoder();
  }

  public HttpMultipartFormDataHandler singleElementHandler(String name, HttpDataHandler handler) {
    DefaultHttpMultipartFormElementHandler defaultHandler;
    if (this.elementHandler == null) {
      this.elementHandler = defaultHandler =
          new DefaultHttpMultipartFormElementHandler();
    } else if (this.elementHandler instanceof DefaultHttpMultipartFormElementHandler) {
      defaultHandler = (DefaultHttpMultipartFormElementHandler) this.elementHandler;
    } else {
      throw new IllegalStateException("you can register single-element-handler or " +
      		"element-handler, but not both");
    }
    HttpMultipartFormDataHandler dataHandler;
    defaultHandler.registerHandler(name, handler,
        dataHandler = new DefaultHttpMultipartFormDataHandler(conn));
    return dataHandler;
  }

  public void elementHandler(HttpMultipartFormElementHandler elementHandler) {
    if (this.elementHandler != null)
      throw new IllegalStateException("you can register single-element-handler or " +
          "element-handler, but not both (or element-handler twice)");
    this.elementHandler = elementHandler;
  }

}
