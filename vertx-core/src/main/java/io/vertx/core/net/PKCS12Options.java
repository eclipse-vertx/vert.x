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
package io.vertx.core.net;

import io.vertx.codegen.annotations.Options;
import io.vertx.core.ServiceHelper;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.PKCS12OptionsFactory;

/**
 * Key or trust store options configuring private key and/or certificates based on PKCS#12 files.<p>
 * When used as a key store, it should point to a store containing a private key and its certificate.
 * When used as a trust store, it should point to a store containing a list of accepted certificates.<p>
 *
 * The store can either be loaded by Vert.x from the filesystem:<p>
 * <pre>
 * HttpServerOptions options = HttpServerOptions.httpServerOptions();
 * options.setKeyStore(PKCS12Options.options().setPath("/mykeystore.p12").setPassword("foo"));
 * </pre>
 *
 * Or directly provided as a buffer:<p>
 *
 * <pre>
 * Buffer store = vertx.fileSystem().readFileSync("/mykeystore.p12");
 * options.setKeyStore(PKCS12Options.options().setValue(store).setPassword("foo"));
 * </pre>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Options
public interface PKCS12Options extends KeyStoreOptions, TrustStoreOptions {

  static PKCS12Options options() {
    return factory.newOptions();
  }

  static PKCS12Options copiedOptions(PKCS12Options other) {
    return factory.copiedOptions(other);
  }

  static PKCS12Options optionsFromJson(JsonObject json) {
    return factory.optionsFromJson(json);
  }

  String getPassword();

  PKCS12Options setPassword(String password);

  String getPath();

  PKCS12Options setPath(String path);

  Buffer getValue();

  PKCS12Options setValue(Buffer value);

  @Override
  PKCS12Options clone();

  static final PKCS12OptionsFactory factory = ServiceHelper.loadFactory(PKCS12OptionsFactory.class);

}
