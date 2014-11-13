/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.platform.impl.resolver;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.impl.Base64;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.impl.ModuleIdentifier;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public abstract class HttpResolution {

  private static final Logger log = LoggerFactory.getLogger(HttpResolution.class);

  private static final String HTTP_PROXY_HOST_PROP_NAME = "http.proxyHost";
  private static final String HTTP_PROXY_PORT_PROP_NAME = "http.proxyPort";

  private static final String HTTP_BASIC_AUTH_USER_PROP_NAME ="http.authUser";
  private static final String HTTP_BASIC_AUTH_PASSWORD_PROP_NAME ="http.authPass";

  private static final String HTTP_USE_DESTINATION_PROXY_HOST_PROP_NAME = "http.useDestinationProxyHost";

  public static boolean suppressDownloadCounter = true;

  private final CountDownLatch latch = new CountDownLatch(1);
  private final Vertx vertx;
  protected final String repoHost;
  protected final int repoPort;
  protected final String repoScheme;
  protected final String repoPassword;
  protected final String repoUsername;
  protected final ModuleIdentifier modID;
  protected final String filename;
  protected final String proxyHost = getProxyHost();
  protected final int proxyPort = getProxyPort();
  private final Map<Integer, Handler<HttpClientResponse>> handlers = new HashMap<>();

  protected HttpClient client;

  private boolean result;

  public boolean waitResult() {
    while (true) {
      try {
        if (!latch.await(3600, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting to download module");
        }
        break;
      } catch (InterruptedException ignore) {
      }
    }
    return result;
  }

  public HttpResolution(Vertx vertx, String repoScheme, String repoUsername, String repoPassword, String repoHost, int repoPort, ModuleIdentifier modID, String filename) {
    this.vertx = vertx;
    this.repoHost = repoHost;
    this.repoPort = repoPort;
    this.repoUsername = repoUsername;
    this.repoPassword = repoPassword;
    this.modID = modID;
    this.filename = filename;
    this.repoScheme = repoScheme;
  }

  protected HttpClient createClient(String scheme, String host, int port) {
    if (client != null) {
      throw new IllegalStateException("Client already created");
    }

    client = vertx.createHttpClient();
    client.setKeepAlive(false); // Not all servers will allow keep alive connections
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
    if (scheme.equals("https")) {
      client.setSSL(true);
    }

    client.exceptionHandler(new Handler<Throwable>() {
      public void handle(Throwable t) {
        end(false);
      }
    });
    return client;
  }

  protected void sendRequest(String scheme, String host, int port, String uri, Handler<HttpClientResponse> respHandler) {
    final String proxyHost = getProxyHost();
    if (proxyHost != null) {
      // We use an absolute URI
      uri = scheme + "://" + host + ":" + port + uri;
    }

    HttpClientRequest req = client.get(uri, respHandler);
    if (proxyHost != null){
      if (isUseDestinationHostHeaderForProxy()) {
        req.putHeader("host", host);
      } else {
        req.putHeader("host", proxyHost);
      }
    } else {
      req.putHeader("host", host);
    }

    if (getBasicAuth() != null) {
      log.debug("Using HTTP Basic Authorization");
      req.putHeader("Authorization","Basic " + getBasicAuth());
    }

    req.putHeader("user-agent", "Vert.x Module Installer");
    req.end();
  }

  protected abstract void getModule();

  protected void makeRequest(String scheme, String host, int port, String uri) {
    sendRequest(scheme, host, port, uri, new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        Handler<HttpClientResponse> handler = handlers.get(resp.statusCode());
        if (handler != null) {
          handler.handle(resp);
        } else {
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

  protected void removeHandler(int statusCode) {
    handlers.remove(statusCode);
  }

  // Standard handlers

  protected void downloadToFile(String file, HttpClientResponse resp) {
    final OutputStream os;
    log.info("Downloading " + modID + ". Please wait...");
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
    resp.endHandler(new VoidHandler() {
      @Override
      protected void handle() {
        if (!suppressDownloadCounter) {
          System.out.println("\rDownloading 100%");
        }
        try {
          os.close();
          end(true);
        } catch (IOException e) {
          log.error("Failed to flush file", e);
          end(false);
        }
      }
    });
  }

  protected void handleRedirect(HttpClientResponse resp) {
    // follow redirects
    String location = resp.headers().get("location");
    if (location == null) {
      log.error("HTTP redirect with no location header");
    } else {
      URI redirectURI;
      try {
        redirectURI = new URI(location);
        client.close();
        client = null;
        int redirectPort = redirectURI.getPort();
        if (redirectPort == -1) {
          redirectPort = 80;
        }
        // Use raw values from location header
        String uri = redirectURI.getRawPath();
        String query = redirectURI.getRawQuery();
        if (query != null) {
          uri = uri + "?" + query; // Include query in URL
        }
        createClient(redirectURI.getScheme(), redirectURI.getHost(), redirectPort);
        makeRequest(redirectURI.getScheme(), redirectURI.getHost(), redirectPort, uri);
      } catch (URISyntaxException e) {
        log.error("Invalid redirect URI: " + location);
      }
    }
  }

  protected void addRedirectHandlers() {
    Handler<HttpClientResponse> handler = new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        handleRedirect(resp);
      }
    };
    addHandler(301, handler);
    addHandler(302, handler);
    addHandler(303, handler);
    addHandler(308, handler);
  }


  private static String getProxyHost() {
    return System.getProperty(HTTP_PROXY_HOST_PROP_NAME);
  }

  // See https://bugs.eclipse.org/bugs/show_bug.cgi?id=445753
  private static boolean isUseDestinationHostHeaderForProxy() {
    return System.getProperty(HTTP_USE_DESTINATION_PROXY_HOST_PROP_NAME) != null;
  }

  private String getBasicAuth() {
    if (repoUsername != null && repoPassword != null) {
      return autoInfo(repoUsername, repoPassword);
    } else {
      String user = System.getProperty(HTTP_BASIC_AUTH_USER_PROP_NAME);
      if (user != null) {
        String pass = System.getProperty(HTTP_BASIC_AUTH_PASSWORD_PROP_NAME);
        if (pass != null) {
          return autoInfo(user, pass);
        }
      }
    }
    return null;
  }

  private String autoInfo(String user, String pass) {
    return Base64.encodeBytes((user + ":" + pass).getBytes());
  }

  private static int getProxyPort() {
    return Integer.parseInt(System.getProperty(HTTP_PROXY_PORT_PROP_NAME, "80"));
  }
}
