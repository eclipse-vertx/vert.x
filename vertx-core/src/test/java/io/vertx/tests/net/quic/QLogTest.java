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
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.vertx.tests.net.quic.QuicClientTest.clientOptions;
import static io.vertx.tests.net.quic.QuicServerTest.serverOptions;

@RunWith(LinuxOrOsx.class)
public class QLogTest extends VertxTestBase {

  private QuicServer server;
  private QuicClient client;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    server = QuicServer.create(vertx, serverOptions(), QuicServerTest.SSL_OPTIONS);
    client = QuicClient.create(vertx, clientOptions(), QuicClientTest.SSL_OPTIONS);
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
    server.handler(conn -> {
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    // Test client with a qlog file
    File qlogFile = File.createTempFile("vertx", ".qlog");
    assertTrue(qlogFile.delete());
    QuicConnectOptions connectOptions = new QuicConnectOptions().setQLogConfig(qlogConfig(qlogFile, "the title", "the description"));
    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost"), connectOptions).await();
    connection.close().await();
    List<JsonObject> qlog = parseQLog(qlogFile);
    assertTrue(!qlog.isEmpty());
    JsonObject data = qlog.get(0);
    assertEquals("the title", data.getString("title"));
    assertTrue(data.getString("description").contains("the description"));
  }

  @Test
  public void testServerQLog() throws Exception {
    // Test server with a qlog dir
    File qlogDir = File.createTempFile("vertx", "qlog");
    assertTrue(qlogDir.delete());
    assertTrue(qlogDir.mkdirs());
    server = QuicServer.create(vertx, serverOptions().setQLogConfig(qlogConfig(qlogDir, "the title", "the description")), QuicServerTest.SSL_OPTIONS);
    server.handler(conn -> {
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    connection.close().await();
    server.close().await();
    server = null;
    String[] a = qlogDir.list();
    int count = 0;
    for (File qlogFile : qlogDir.listFiles()) {
      if (qlogFile.getName().endsWith(".qlog")) {
        count++;
        List<JsonObject> qlog = parseQLog(qlogFile);
        assertTrue(!qlog.isEmpty());
        JsonObject data = qlog.get(0);
        assertEquals("the title", data.getString("title"));
        assertTrue(data.getString("description").contains("the description"));
      }
    }
    assertTrue(count > 0);
  }
}
