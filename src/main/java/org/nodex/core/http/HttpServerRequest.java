/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.core.http;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.streams.ReadStream;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HttpServerRequest implements ReadStream {

  private Map<String, List<String>> params;
  private DataHandler dataHandler;
  private Runnable endHandler;
  private final HttpServerConnection conn;
  private final HttpRequest request;

  HttpServerRequest(HttpServerConnection conn,
                    HttpRequest request) {
    this.method = request.getMethod().toString();
    URI theURI;
    try {
      theURI = new URI(request.getUri());
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid uri " + request.getUri()); //Should never happen
    }
    this.path = theURI.getPath();
    this.uri = request.getUri();
    this.conn = conn;
    this.request = request;
  }

  public final String method;
  public final String uri;
  public final String path;

  public String getHeader(String key) {
    return request.getHeader(key);
  }

  public List<String> getHeaders(String key) {
    return request.getHeaders(key);
  }

  public Set<String> getHeaderNames() {
    return request.getHeaderNames();
  }

  public void data(DataHandler dataHandler) {
    this.dataHandler = dataHandler;
  }

  public void pause() {
    conn.pause();
  }

  public void resume() {
    conn.resume();
  }

  public void end(Runnable handler) {
    this.endHandler = handler;
  }

  public String getParam(String param) {
    if (params == null) {
      QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri);
      params = queryStringDecoder.getParameters();
    }
    List<String> list = params.get(param);
    if (list != null) {
      return list.get(0);
    } else {
      return null;
    }
  }

  void handleData(Buffer data) {
    if (dataHandler != null) {
      dataHandler.onData(data);
    }
  }

  void handleEnd() {
    if (endHandler != null) {
      endHandler.run();
    }
  }

}
