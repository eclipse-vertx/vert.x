package org.vertx.java.platform.impl.resolver;/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public abstract class HttpRepoResolver implements RepoResolver {

  private static final Logger log = LoggerFactory.getLogger(HttpRepoResolver.class);

  private final Vertx vertx;
  private final String proxyHost;
  private final int proxyPort;
  protected final String repoHost;
  protected final int repoPort;
  protected final String contentRoot;

  public HttpRepoResolver(Vertx vertx, String proxyHost, int proxyPort, String repoID) {
    this.vertx = vertx;
    this.proxyHost = proxyHost;
    this.proxyPort = proxyPort;
    try {
      URI uri = new URI(repoID);
      repoHost = uri.getHost();
      int port = uri.getPort();
      if (port == -1) {
        port = 80;
      }
      repoPort = port;
      contentRoot = uri.getPath();
    } catch (Exception e) {
      throw new IllegalArgumentException(repoID + " is not a valid repository identifier");
    }
  }

  protected abstract String getRepoURI(String moduleName);

  public Buffer getModule(final String moduleName) {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Buffer> mod = new AtomicReference<>();
    HttpClient client = vertx.createHttpClient();
    if (proxyHost != null) {
      client.setHost(proxyHost);
      if (proxyPort != 80) {
        client.setPort(proxyPort);
      } else {
        client.setPort(80);
      }
    } else {
      client.setHost(repoHost);
      client.setPort(repoPort);
    }
    client.exceptionHandler(new Handler<Exception>() {
      public void handle(Exception e) {
        log.error("Unable to connect to repository");
        latch.countDown();
      }
    });
    String uri = getRepoURI(moduleName);
    String msg = "Attempting to install module " + moduleName + " from http://"
        + repoHost + ":" + repoPort + uri;
    if (proxyHost != null) {
      msg += " Using proxy host " + proxyHost + ":" + proxyPort;
    }
    log.info(msg);
    if (proxyHost != null) {
      // We use an absolute URI
      uri = new StringBuilder("http://").append(repoHost).append(uri).toString();
    }
    HttpClientRequest req = client.get(uri, new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        if (resp.statusCode == 200) {
          log.info("Downloading module...");
          resp.bodyHandler(new Handler<Buffer>() {
            public void handle(Buffer buffer) {
              mod.set(buffer);
              latch.countDown();
            }
          });
        } else if (resp.statusCode == 404) {
          log.error("Can't find module " + moduleName + " in repository");
          latch.countDown();
        } else {
          log.error("Failed to download module: " + resp.statusCode);
          latch.countDown();
        }
      }
    });
    if (proxyHost != null){
      req.putHeader("host", proxyHost);
    } else {
      req.putHeader("host", repoHost);
    }
    req.putHeader("user-agent", "Vert.x Module Installer");
    req.end();
    while (true) {
      try {
        if (!latch.await(30, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting to download module");
        }
        break;
      } catch (InterruptedException ignore) {
      }
    }
    return mod.get();
  }
}
