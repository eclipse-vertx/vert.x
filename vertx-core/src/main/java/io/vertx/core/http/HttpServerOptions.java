/*
 * Copyright 2014 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version 2.0
 *   (the "License"); you may not use this file except in compliance with the
 *   License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
 */

package io.vertx.core.http;

import io.vertx.codegen.annotations.Options;
import io.vertx.core.ServiceHelper;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetServerOptionsBase;
import io.vertx.core.spi.HttpServerOptionsFactory;

import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public interface HttpServerOptions extends NetServerOptionsBase<HttpServerOptions> {

  static HttpServerOptions options() {
    return factory.newOptions();
  }

  static HttpServerOptions copiedOptions(HttpServerOptions other) {
    return factory.copiedOptions(other);
  }

  static HttpServerOptions optionsFromJson(JsonObject json) {
    return factory.fromJson(json);
  }

  boolean isCompressionSupported();

  HttpServerOptions setCompressionSupported(boolean compressionSupported);

  int getMaxWebsocketFrameSize();

  HttpServerOptions setMaxWebsocketFrameSize(int maxWebsocketFrameSize);

  HttpServerOptions addWebsocketSubProtocol(String subProtocol);

  Set<String> getWebsocketSubProtocols();

  static final HttpServerOptionsFactory factory = ServiceHelper.loadFactory(HttpServerOptionsFactory.class);
}
