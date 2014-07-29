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

package io.vertx.core.net;

import io.vertx.codegen.annotations.Options;
import io.vertx.core.ServiceHelper;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.NetServerOptionsFactory;

import java.util.List;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public interface NetServerOptions extends NetServerOptionsBase<NetServerOptions> {

  static NetServerOptions options() {
    return factory.newOptions();
  }

  static NetServerOptions copiedOptions(NetServerOptions other) {
    return factory.copiedOptions(other);
  }

  static NetServerOptions optionsFromJson(JsonObject json) {
    return factory.fromJson(json);
  }


  boolean isClientAuthRequired();

  NetServerOptions setClientAuthRequired(boolean clientAuthRequired);

  List<String> getCrlPaths();

  NetServerOptions addCrlPath(String crlPath);

  List<Buffer> getCrlValues();

  NetServerOptions addCrlValue(Buffer crlValue);

  int getAcceptBacklog();

  NetServerOptions setAcceptBacklog(int acceptBacklog);

  int getPort();

  NetServerOptions setPort(int port);

  String getHost();

  NetServerOptions setHost(String host);

  static final NetServerOptionsFactory factory = ServiceHelper.loadFactory(NetServerOptionsFactory.class);

}
