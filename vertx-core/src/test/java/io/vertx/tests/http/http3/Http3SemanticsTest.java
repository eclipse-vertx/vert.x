/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http.http3;

import io.netty.handler.codec.http3.Http3ErrorCode;
import io.vertx.core.http.StreamResetException;
import io.vertx.tests.http.HttpSemanticsTest;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.fail;

public class Http3SemanticsTest extends HttpSemanticsTest {

  public Http3SemanticsTest() {
    super(Http3Configurator.INSTANCE);
  }

  // The request without authority never reaches the server: the Netty codec rejects it as
  // malformed (https://www.rfc-editor.org/rfc/rfc9114#section-4.3.1) and the stream terminates
  // with an H3_MESSAGE_ERROR stream error
  @Test
  @Override
  public void testMissingAuthority() throws Exception {
    server.requestHandler(req -> fail());
    startServer(testAddress);
    assertThatThrownBy(() -> client.request(requestOptions)
      .compose(request -> request
        .authority(null)
        .send())
      .await(20, TimeUnit.SECONDS))
      .isInstanceOfSatisfying(StreamResetException.class, err ->
        org.junit.Assert.assertEquals(Http3ErrorCode.H3_MESSAGE_ERROR.code(), err.getCode()));
  }
}
