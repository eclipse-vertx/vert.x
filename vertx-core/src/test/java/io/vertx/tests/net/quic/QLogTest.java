/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.net.quic;

import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QLogTest extends VertxTestBase {

  private QuicServer server;
  private QuicClient client;

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    client.close().await();
    if (server != null) {
      server.close().await();
    }
    super.tearDown();
  }

  private QLogConfig qlogConfig(File logFile, String title, String desc) {
    QLogConfig qlog = new QLogConfig();
    qlog.setPath(logFile.getAbsolutePath());
    qlog.setDescription(desc);
    qlog.setTitle(title);
    return qlog;
  }

  private static List<JsonObject> parseQLog(File file) throws IOException {
    String s = Files.readString(file.toPath());
    Pattern pattern = Pattern.compile("\\x1E([^\\n]*)\\n", Pattern.MULTILINE);
    Matcher matcher = pattern.matcher(s);
    List<JsonObject> list = new ArrayList<>();
    while (matcher.find()) {
      list.add(new JsonObject(matcher.group(1)));
    }
    return list;
  }

  @Test
  public void testClientQLog() throws Exception {
    File dir = TestUtils.createTmpDirectory("client-qlog");
    testQLog(new QuicServerConfig(), new QuicClientConfig().setQLogConfig(qlogConfig(dir, "the title", "the description")));
    int count =  checkQLog(dir);
    assertEquals(10, count);
  }

  @Test
  public void testServerQLog() throws Exception {
    File dir = TestUtils.createTmpDirectory("server-qlog");
    testQLog(new QuicServerConfig().setQLogConfig(qlogConfig(dir, "the title", "the description")), new QuicClientConfig());
    int count =  checkQLog(dir);
    assertEquals(10, count);
  }

  private void testQLog(QuicServerConfig serverConfig,  QuicClientConfig clientConfig) throws Exception {
    server = vertx.createQuicServer(serverConfig, QuicServerTest.SSL_OPTIONS);
    server.connectHandler(conn -> {
    });
    client = vertx.createQuicClient(clientConfig, QuicClientTest.SSL_OPTIONS);
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    for (int i = 0;i < 10;i++) {
      QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
      connection.close().await();
    }
    server.close().await();
    server = null;
  }

  private int checkQLog(File dir) throws IOException {
    int count = 0;
    for (File qlogFile : dir.listFiles()) {
      if (qlogFile.getName().endsWith(".qlog")) {
        count++;
        List<JsonObject> qlog = parseQLog(qlogFile);
        assertTrue(!qlog.isEmpty());
        JsonObject data = qlog.get(0);
        assertEquals("the title", data.getString("title"));
        assertTrue(data.getString("description").contains("the description"));
      }
    }
    return count;
  }
}
