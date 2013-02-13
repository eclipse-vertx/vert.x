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
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
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
    String uri = getRepoURI(moduleName);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Buffer> mod = new AtomicReference<>();
    getModule(moduleName, repoHost, repoPort, uri, latch, mod);
    while (true) {
      try {
        if (!latch.await(300, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting to download module");
        }
        break;
      } catch (InterruptedException ignore) {
      }
    }
    return mod.get();
  }

  public void getModule(final String moduleName,
                        final String host, final int port, String uri, final CountDownLatch latch, final AtomicReference<Buffer> mod) {
    final HttpClient client = vertx.createHttpClient();
    if (proxyHost != null) {
      client.setHost(proxyHost);
      if (proxyPort != 80) {
        client.setPort(proxyPort);
      } else {
        client.setPort(80);
      }
    } else {
      client.setHost(host);
      client.setPort(port);
    }
    client.exceptionHandler(new Handler<Exception>() {
      public void handle(Exception e) {
        log.error("Unable to connect to repository");
        end(client, latch);
      }
    });
    if (proxyHost != null) {
      // We use an absolute URI
      // FIXME - check this!
      uri = new StringBuilder("http://").append(host).append(":").append(port).append(uri).toString();
    }
    final String theURI = uri;

    System.out.println("Attempting to get from " + uri + " host: " + host + " port: " + port);
    HttpClientRequest req = client.get(uri, new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        if (resp.statusCode == 200) {
          String msg= "Downloading ";
          if (proxyHost == null) {
            msg += "http://" + repoHost + ":" + repoPort + theURI;
          } else {
            msg += theURI;
            msg += " Using proxy host " + proxyHost + ":" + proxyPort;
          }
          log.info(msg);
          final Buffer buff = new Buffer();
          final int contentLength = Integer.valueOf(resp.headers().get("content-length"));
          resp.dataHandler(new Handler<Buffer>() {
            long lastPercent = 0;
            public void handle(Buffer event) {
              buff.appendBuffer(event);
              long percent = Math.round(100 * (double)buff.length() / contentLength);
              if (percent >= lastPercent + 1) {
                System.out.print("\rDownloading " + percent + "%");
                lastPercent = percent;
              }
            }
          });
          resp.endHandler(new SimpleHandler() {
            @Override
            protected void handle() {
              System.out.println("");
              mod.set(buff);
              end(client, latch);
            }
          });
          return;
        } else if (resp.statusCode == 404) {
          // NOOP
          System.out.println("404");
        } else if (resp.statusCode == 302) {
          System.out.println("302");
          // follow redirects
          String location = resp.headers().get("location");
          if (location == null) {
            log.error("HTTP redirect with no location header");
          } else {
            URI redirectURI;
            try {
              redirectURI = new URI(location);
              getModule(moduleName, redirectURI.getHost(), redirectURI.getPort() != -1 ? redirectURI.getPort() : 80, redirectURI.getPath(), latch, mod);
              return;
            } catch (URISyntaxException e) {
              log.error("Invalid redirect URI: " + location);
            } finally {
              client.close();
            }
          }
        } else {
          log.error("Failed to query repository: " + resp.statusCode);
        }
        end(client, latch);
      }
    });
    if (proxyHost != null){
      req.putHeader("host", proxyHost);
    } else {
      req.putHeader("host", host);
    }
    req.putHeader("user-agent", "Vert.x Module Installer");
    req.end();
  }

  private void end(HttpClient client, CountDownLatch latch)  {
    client.close();
    latch.countDown();
  }

}
