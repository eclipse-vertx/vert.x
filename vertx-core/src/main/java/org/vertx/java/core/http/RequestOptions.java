/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.vertx.java.core.http;

import org.vertx.java.core.MultiMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RequestOptions {

  private int port = 80;
  private String host = "localhost";
  private MultiMap headers;
  private String requestURI;
  private String uri;

  private int state = STATE_DEFAULT;
  private static final int STATE_DEFAULT = 0;
  private static final int STATE_CONF_FROM_URI = 1;
  private static final int STATE_CONF_EXPL = 2;

  public int getPort() {
    return port;
  }

  public RequestOptions setPort(int port) {
    if (state == STATE_CONF_FROM_URI) {
      throw new IllegalStateException("The port is determined from the URI you provided");
    }
    this.port = port;
    this.state = STATE_CONF_EXPL;
    return this;
  }

  public String getHost() {
    return host;
  }

  public RequestOptions setHost(String host) {
    if (state == STATE_CONF_FROM_URI) {
      throw new IllegalStateException("The host is determined from the URI you provided");
    }
    this.host = host;
    this.state = STATE_CONF_EXPL;
    return this;
  }

  public MultiMap getHeaders() {
    return headers;
  }

  public RequestOptions setHeaders(MultiMap headers) {
    this.headers = headers;
    return this;
  }

  public String getRequestURI() {
    return requestURI;
  }

  public RequestOptions setRequestURI(String requestURI) {
    this.requestURI = requestURI;
    return this;
  }

  public String getUri() {
    return uri;
  }

  public RequestOptions setUri(String uri) {
    if (state == STATE_CONF_EXPL) {
      throw new IllegalStateException("You have already configured host/port");
    }
    this.uri = uri;
    state = STATE_CONF_FROM_URI;
    return this;
  }

  public RequestOptions putHeader(CharSequence name, CharSequence value) {
    if (headers == null) {
      headers = new CaseInsensitiveMultiMap();
    }
    headers.add(name, value);
    return this;
  }
}
