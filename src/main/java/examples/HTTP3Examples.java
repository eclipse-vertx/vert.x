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

package examples;

import io.netty.incubator.codec.http3.DefaultHttp3SettingsFrame;
import io.netty.incubator.codec.http3.Http3SettingsFrame;
import io.netty.util.NetUtil;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class HTTP3Examples {

  public void example01(Vertx vertx) {

    DefaultHttp3SettingsFrame settings = new DefaultHttp3SettingsFrame();
    settings.put(Http3SettingsFrame.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE,
      100000000000L);

    HttpClientOptions options = new HttpClientOptions().
      setSsl(true).
      setUseAlpn(true).
      setHttp3InitialSettings(settings).
      setTrustAll(true);
    options.setProtocolVersion(HttpVersion.HTTP_3);

    HttpClient client = vertx.createHttpClient(options);

    client.request(HttpMethod.GET, 443, "www.google.com", "/")
//    client.request(HttpMethod.GET, 9999, NetUtil.LOCALHOST4.getHostAddress(), "/")
//    client.request(HttpMethod.GET, 443, "www.mozilla.org", "/")
//    client.request(HttpMethod.GET, 443, "www.bing.com", "/")
//    client.request(HttpMethod.GET, 443, "www.yahoo.com", "/")
//    client.request(HttpMethod.GET, 443, "http3.is", "/")
      .compose(req -> req
        .end()
        .compose(res -> req
          .response()
          .onSuccess(resp -> {
            System.out.println("resp.headers() = " + resp.headers());
            System.out.println("Alt-Svc = " + resp.headers().get("Alt-Svc"));
          }).compose(HttpClientResponse::body).onSuccess(body ->
            System.out.println("response = " + body.toString()))
        ))
      .onFailure(Throwable::printStackTrace)
      .onComplete(event -> vertx.close())
    ;
  }

  public static void main(String[] args) {
    new HTTP3Examples().example01(Vertx.vertx());
  }
}
