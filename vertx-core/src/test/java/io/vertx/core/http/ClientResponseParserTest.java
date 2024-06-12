/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http;

import io.vertx.core.parsetools.RecordParser;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class ClientResponseParserTest extends HttpTestBase {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return super.createBaseServerOptions()
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_PEM.get());
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return super.createBaseClientOptions()
      .setSsl(true)
      .setTrustOptions(Trust.SERVER_PEM.get());
  }

  @Test
  public void shouldParseHttpsStream() throws Exception {
    vertx.exceptionHandler(this::fail);

    char[] firstLine = new char[1024 * 32];
    Arrays.fill(firstLine, 'a');
    char[] secondLine = new char[1024 * 64];
    Arrays.fill(secondLine, 'b');

    server.requestHandler(req -> {
      HttpServerResponse response = req.response();
      response.setChunked(true);
      response.write(new String(firstLine), UTF_8.name());
      response.write("\n");
      response.end(new String(secondLine), UTF_8.name());
    });
    startServer();

    List<String> lines = Collections.synchronizedList(new ArrayList<>());
    waitFor(3);
    client.request(requestOptions.setPort(server.actualPort())).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        resp.exceptionHandler(this::fail);
        RecordParser.newDelimited("\n", resp).endHandler(v -> complete()).handler(buff -> {
          lines.add(buff.toString(UTF_8));
          complete();
        });
      }));
    }));

    await();

    assertEquals(2, lines.size());
    assertEquals(new String(firstLine), lines.get(0));
    assertEquals(new String(secondLine), lines.get(1));
  }
}
