/*
 * Copyright (C) 2017 Julien Viet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.vertx.tests.jdbc;

import org.junit.rules.ExternalResource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.InternetProtocol;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Postgresql test database based on https://www.testcontainers.org
 * Require Docker
 * Testing can also be done on an external database by setting the system properties :
 *  - connection.uri
 *  - tls.connection.uri
 */
public class ContainerPgRule extends ExternalResource {

  private static final int DEFAULT_PORT = 5432;

  private ServerContainer<?> server;
  private Callable<Connection> connection;
  private String databaseVersion;
  private String user = "postgres";

  public Callable<Connection> connectionFactory() {
    return connection;
  }

  private void initServer(String version) {

    server = new ServerContainer<>("postgres:" + version)
      .withEnv("POSTGRES_DB", "postgres")
      .withEnv("POSTGRES_USER", user)
      .withEnv("POSTGRES_PASSWORD", "postgres")
      .waitingFor(new LogMessageWaitStrategy()
        .withRegEx(".*database system is ready to accept connections.*\\s")
        .withTimes(2)
        .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS)))
      .withCommand("postgres", "-c", "fsync=off");

    server.withExposedPorts(DEFAULT_PORT);
  }

  public synchronized Callable<Connection> startServer(String databaseVersion) throws Exception {
    initServer(databaseVersion);
    server.start();
    return () -> {
      return DriverManager.getConnection("jdbc:postgresql://localhost:" + server.getMappedPort(DEFAULT_PORT) + "/", "postgres", "postgres");
    };
  }

  private static String getPostgresVersion() {
    String specifiedVersion = System.getProperty("embedded.postgres.version");
    String version;
    if (specifiedVersion == null || specifiedVersion.isEmpty()) {
      // if version is not specified then V10.10 will be used by default
      version = "10.10";
    } else {
      version = specifiedVersion;
    }

    return version;
  }

  public synchronized void stopServer() {
    if (server != null) {
      try {
        server.stop();
      } finally {
        server = null;
      }
    }
  }

  @Override
  protected void before() throws Throwable {

    // We do not need to launch another server if it's a shared instance
    if (this.server != null) {
      return;
    }

    this.databaseVersion =  getPostgresVersion();
    connection = startServer(databaseVersion);
  }

  @Override
  protected void after() {
    try {
      stopServer();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static class ServerContainer<SELF extends ServerContainer<SELF>> extends GenericContainer<SELF> {

    public ServerContainer(String dockerImageName) {
      super(dockerImageName);
    }

    public SELF withFixedExposedPort(int hostPort, int containerPort) {
      super.addFixedExposedPort(hostPort, containerPort, InternetProtocol.TCP);
      return self();
    }
  }
}
