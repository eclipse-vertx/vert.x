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

package io.vertx.core.datagram;


import io.vertx.codegen.annotations.Options;
import io.vertx.core.ServiceHelper;
import io.vertx.core.spi.DatagramSocketOptionsFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetworkOptions;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public interface DatagramSocketOptions extends NetworkOptions<DatagramSocketOptions> {

  static DatagramSocketOptions options() {
    return factory.newOptions();
  }

  static DatagramSocketOptions copiedOptions(DatagramSocketOptions other) {
    return factory.copiedOptions(other);
  }

  static DatagramSocketOptions optionsFromJson(JsonObject json) {
    return factory.fromJson(json);
  }

  boolean isBroadcast();

  DatagramSocketOptions setBroadcast(boolean broadcast);

  boolean isLoopbackModeDisabled();

  DatagramSocketOptions setLoopbackModeDisabled(boolean loopbackModeDisabled);

  int getMulticastTimeToLive();

  DatagramSocketOptions setMulticastTimeToLive(int multicastTimeToLive);

  String getMulticastNetworkInterface();

  DatagramSocketOptions setMulticastNetworkInterface(String multicastNetworkInterface);

  boolean isIpV6();

  DatagramSocketOptions setIpV6(boolean ipV6);

  static final DatagramSocketOptionsFactory factory = ServiceHelper.loadFactory(DatagramSocketOptionsFactory.class);
}
