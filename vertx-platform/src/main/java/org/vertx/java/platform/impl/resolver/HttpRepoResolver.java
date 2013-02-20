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

import org.vertx.java.core.*;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.file.AsyncFile;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public abstract class HttpRepoResolver implements RepoResolver {

  private static final Logger log = LoggerFactory.getLogger(HttpRepoResolver.class);

  private static final String HTTP_PROXY_HOST_PROP_NAME = "http.proxyHost";
  private static final String HTTP_PROXY_PORT_PROP_NAME = "http.proxyPort";

  private final Vertx vertx;
  protected final String repoHost;
  protected final int repoPort;
  protected final String contentRoot;

  public static boolean suppressDownloadCounter = true;

  public HttpRepoResolver(Vertx vertx,String repoID) {
    this.vertx = vertx;
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

  private String getProxyHost() {
    return System.getProperty(HTTP_PROXY_HOST_PROP_NAME);
  }

  private int getProxyPort() {
    return Integer.valueOf(System.getProperty(HTTP_PROXY_PORT_PROP_NAME, "80"));
  }

  public boolean getModule(String filename, String moduleName) {
    String uri = getRepoURI(moduleName);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Boolean> res = new AtomicReference<>();
    getModule(filename, repoHost, repoPort, uri, latch, res);
    while (true) {
      try {
        if (!latch.await(300, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting to download module");
        }
        break;
      } catch (InterruptedException ignore) {
      }
    }
    return res.get();
  }

  public void getModule(final String filename, final String host, final int port, String uri, final CountDownLatch latch,
                        final AtomicReference<Boolean> res) {
    final HttpClient client = vertx.createHttpClient();
    final String proxyHost = getProxyHost();
    final int proxyPort = getProxyPort();
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
        end(res, false, client, latch);
      }
    });
    if (proxyHost != null) {
      // We use an absolute URI
      uri = new StringBuilder("http://").append(host).append(":").append(port).append(uri).toString();
    }
    final String theURI = uri;

    HttpClientRequest req = client.get(uri, new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        final OutputStream os;
        if (resp.statusCode == 200) {
          String msg= "Downloading ";
          try {
            os = new BufferedOutputStream(new FileOutputStream(filename));
          } catch (IOException e) {
            log.error("Failed to open file", e);
            end(res, false, client, latch);
            return;
          }
          if (proxyHost == null) {
            msg += "http://" + repoHost + ":" + repoPort + theURI;
          } else {
            msg += theURI;
            msg += " Using proxy host " + proxyHost + ":" + proxyPort;
          }
          log.info(msg + ". Please wait.");
          final AtomicInteger written = new AtomicInteger();
          final int contentLength = Integer.valueOf(resp.headers().get("content-length"));
          resp.dataHandler(new Handler<Buffer>() {
            long lastPercent = 0;
            public void handle(Buffer data) {
              int bytesWritten = written.get();
              try {
                os.write(data.getBytes());
              } catch (IOException e) {
                log.error("Failed to write to file", e);
                end(res, false, client, latch);
                return;
              }
              if (!suppressDownloadCounter) {
                written.addAndGet(data.length());
                long percent = Math.round(100 * (double)bytesWritten / contentLength);
                if (percent > lastPercent) {
                  System.out.print("\rDownloading " + percent + "%");
                  lastPercent = percent;
                }
              }
            }
          });
          resp.endHandler(new SimpleHandler() {
            @Override
            protected void handle() {
              if (!suppressDownloadCounter) {
                System.out.println("");
              }
              try {
                os.flush();
                end(res, true, client, latch);
              } catch (IOException e) {
                log.error("Failed to flush file", e);
                end(res, false, client, latch);
              }
            }
          });
          return;
        } else if (resp.statusCode == 404) {
          // NOOP
        } else if (resp.statusCode == 302) {
          // follow redirects
          String location = resp.headers().get("location");
          if (location == null) {
            log.error("HTTP redirect with no location header");
          } else {
            URI redirectURI;
            try {
              redirectURI = new URI(location);
              getModule(filename, redirectURI.getHost(), redirectURI.getPort() != -1 ? redirectURI.getPort() : 80,
                        redirectURI.getPath(), latch, res);
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
        end(res, false, client, latch);
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

  private void end(AtomicReference<Boolean> res, boolean ok, HttpClient client, CountDownLatch latch)  {
    client.close();
     res.set(ok);
    latch.countDown();
  }

}
