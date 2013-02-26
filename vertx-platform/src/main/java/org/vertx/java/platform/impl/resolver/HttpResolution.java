package org.vertx.java.platform.impl.resolver;

import org.vertx.java.core.*;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/*
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
public abstract class HttpResolution {

  private static final Logger log = LoggerFactory.getLogger(HttpResolution.class);

  private static final String HTTP_PROXY_HOST_PROP_NAME = "http.proxyHost";
  private static final String HTTP_PROXY_PORT_PROP_NAME = "http.proxyPort";

  public static boolean suppressDownloadCounter = true;

  private final CountDownLatch latch = new CountDownLatch(1);
  private final Vertx vertx;
  protected final String repoHost;
  protected final int repoPort;
  protected final String moduleName;
  protected final String filename;
  protected final String proxyHost = getProxyHost();
  protected final int proxyPort = getProxyPort();
  private final Map<Integer, Handler<HttpClientResponse>> handlers = new HashMap<>();
  protected HttpClient client;
  private boolean result;

  public boolean waitResult() {
    while (true) {
      try {
        if (!latch.await(300, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting to download module");
        }
        break;
      } catch (InterruptedException ignore) {
      }
    }
    return result;
  }

  public HttpResolution(Vertx vertx, String repoHost, int repoPort, String moduleName, String filename) {
    this.vertx = vertx;
    this.repoHost = repoHost;
    this.repoPort = repoPort;
    this.moduleName = moduleName;
    this.filename = filename;
  }

  protected HttpClient createClient(String host, int port) {
    if (client != null) {
      throw new IllegalStateException("Client already created");
    }
    client = vertx.createHttpClient();
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
        end(false);
      }
    });
    return client;
  }

  protected void sendRequest(String host, int port, String uri, Handler<HttpClientResponse> respHandler) {
    final String proxyHost = getProxyHost();
    if (proxyHost != null) {
      // We use an absolute URI
      uri = new StringBuilder("http://").append(host).append(":").append(port).append(uri).toString();
    }
    HttpClientRequest req = client.get(uri, respHandler);
    if (proxyHost != null){
      req.putHeader("host", proxyHost);
    } else {
      req.putHeader("host", host);
    }
    req.putHeader("user-agent", "Vert.x Module Installer");
    req.end();
  }

  protected abstract void getModule();

  protected void makeRequest(String host, int port, String uri) {
    sendRequest(host, port, uri, new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        Handler<HttpClientResponse> handler = handlers.get(resp.statusCode);
        if (handler != null) {
          handler.handle(resp);
        } else {
          log.error("Failed to query repository: " + resp.statusCode);
          end(false);
        }
      }
    });
  }

  protected void end(boolean ok)  {
    client.close();
    result = ok;
    latch.countDown();
  }

  protected void addHandler(int statusCode, Handler<HttpClientResponse> handler) {
    handlers.put(statusCode, handler);
  }

  // Standard handlers

  protected void downloadToFile(String file, HttpClientResponse resp) {
    final OutputStream os;
    log.info("Downloading " + moduleName + ". Please wait...");
    try {
      os = new BufferedOutputStream(new FileOutputStream(file));
    } catch (IOException e) {
      log.error("Failed to open file", e);
      end(false);
      return;
    }
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
          end(false);
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
          end(true);
        } catch (IOException e) {
          log.error("Failed to flush file", e);
          end(false);
        }
      }
    });
  }

  private String getProxyHost() {
    return System.getProperty(HTTP_PROXY_HOST_PROP_NAME);
  }

  private int getProxyPort() {
    return Integer.valueOf(System.getProperty(HTTP_PROXY_PORT_PROP_NAME, "80"));
  }

}
