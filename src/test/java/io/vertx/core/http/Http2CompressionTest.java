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

package io.vertx.core.http;

import io.netty.util.internal.StringUtil;
import org.junit.Test;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class Http2CompressionTest extends GzipHttpCompressionTestBase {

  public Http2CompressionTest(int compressionLevel) {
    super(compressionLevel);
  }

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return Http2TestBase.createHttp2ServerOptions(HttpTestBase.DEFAULT_HTTPS_PORT, HttpTestBase.DEFAULT_HTTPS_HOST);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return Http2TestBase.createHttp2ClientOptions().setDefaultPort(DEFAULT_HTTPS_PORT).setDefaultHost(DEFAULT_HTTPS_HOST);
  }

  @Test
  public void testServerCompressionSkipsEncodedPushResponse() throws Exception {
    server.close();
    HttpServerOptions options = createBaseServerOptions();
    configureServerCompression(options);
    server = vertx.createHttpServer(options);
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/pushed").onComplete(onSuccess(pushed -> {
        pushed
          .putHeader(HttpHeaders.CONTENT_ENCODING, encoding())
          .end(compressedTestString);
        req.response().end();
      }));
    });
    startServer();
    client.request(new RequestOptions()
      .addHeader(HttpHeaders.ACCEPT_ENCODING, encoding()))
      .onComplete(onSuccess(req -> {
        req
          .pushHandler(pushReq -> pushReq.response().onComplete(onSuccess(pushResp -> {
            assertEquals(encoding(), pushResp.getHeader(HttpHeaders.CONTENT_ENCODING));
            pushResp.body().onComplete(onSuccess(body -> {
              assertEquals(StringUtil.toHexString(compressedTestString.getBytes()), StringUtil.toHexString(body.getBytes()));
              testComplete();
            }));
          })))
          .send();
      }));
    await();
  }
}
