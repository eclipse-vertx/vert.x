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
package io.vertx.tests.http.http3;

import io.vertx.core.transport.Transport;
import io.vertx.tests.http.fileupload.HttpServerFileUploadTest;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

/**
 */
public class Http3ServerFileUploadTest extends HttpServerFileUploadTest {

  public Http3ServerFileUploadTest() {
    super(new Http3Config());
  }

  @Ignore("Intermittent failure with timeout on CI")
  @Test
  @Override
  public void testBrokenFormUploadLargeFileStreamToDisk() {
    // On my machine, takes about 40secs to complete
    // 40secs correspond to QuicChannel timeout after the connection is closed
    super.testBrokenFormUploadLargeFileStreamToDisk();
  }

  @Ignore("Intermittent failure with timeout on CI")
  @Test
  @Override
  public void testBrokenFormUploadLargeFile() {
    // On my machine, takes about 40secs to complete
    // 40secs correspond to QuicChannel timeout after the connection is closed
    super.testBrokenFormUploadLargeFile();
  }

  @Test
  @Override
  public void testFormUploadVeryLargeFileStreamToDisk() {
    Assume.assumeFalse("Not supported yet with io_uring", TRANSPORT == Transport.IO_URING);
    super.testFormUploadVeryLargeFileStreamToDisk();
  }
}
