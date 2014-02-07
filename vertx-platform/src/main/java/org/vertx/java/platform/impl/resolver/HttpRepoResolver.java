/*
 * Copyright (c) 2011-2013 Red Hat Inc.
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
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.impl.ModuleIdentifier;
import org.vertx.java.platform.impl.resolver.requesters.GetRequest;
import org.vertx.java.platform.impl.resolver.requesters.HeadRequest;
import org.vertx.java.platform.impl.resolver.requesters.ResolverRequest;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class HttpRepoResolver implements RepoResolver {
  protected static final Logger log = LoggerFactory.getLogger(HttpRepoResolver.class);
  private static final int FIND_TIMEOUT_SEC = 15;
  private static final int DOWNLOAD_TIMEOUT_SEC = 300;

  public static boolean suppressDownloadCounter = true;

  protected final Vertx vertx;
  protected final String repoScheme;
  protected final String repoHost;
  protected final String repoUsername;
  protected final String repoPassword;
  protected final int repoPort;
  protected final String contentRoot;

  protected HttpClient client;
  protected BlockingWaiter<ResolverResult> findWaiter;
  protected BlockingWaiter<Boolean> downloadWaiter;

  public HttpRepoResolver(Vertx vertx, String repoID) {
    this.vertx = vertx;
    try {
      URI uri = new URI(repoID);
      if (uri.getUserInfo() != null) {
        int i = uri.getUserInfo().indexOf(":");
        if (i > 0) {
          repoUsername = uri.getUserInfo().substring(0, i);
          repoPassword = uri.getUserInfo().substring(i + 1);
        } else {
          repoUsername = null;
          repoPassword = null;
        }
      } else {
        repoUsername = null;
        repoPassword = null;
      }

      repoScheme = uri.getScheme();
      repoHost = uri.getHost();
      int port = uri.getPort();
      if (port == -1) {
        port = (repoScheme.equals("https")) ? 443 : 80;
      }
      repoPort = port;
      contentRoot = uri.getPath();

      client = ResolverRequest.createClient(vertx, repoScheme, repoHost, repoPort);
    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalArgumentException(repoID + " is not a valid repository identifier");
    }
  }

  public abstract void findResource(ModuleIdentifier moduleIdentifier);

  @Override
  public final ResolverResult findModule(ModuleIdentifier moduleIdentifier) {
    findWaiter = new BlockingWaiter<>();
    findResource(moduleIdentifier);
    return findWaiter.await(FIND_TIMEOUT_SEC, TimeUnit.SECONDS, ResolverResult.FAILED);
  }

  // Standard handlers
  @Override
  public boolean obtainModule(final ResolverResult resourceData, final Path targetPath) {
    downloadWaiter = new BlockingWaiter<>();
    downloadModule(resourceData, targetPath);
    return downloadWaiter.await(DOWNLOAD_TIMEOUT_SEC, TimeUnit.SECONDS, false);
  }

  private void downloadModule(ResolverResult resourceData, Path targetPath) {
    GetRequest getRequest = createGetRequest();
    getRequest.addHandler(200, createDownloadedResourceHandler(resourceData, targetPath));
    getRequest.setUnknownStatusHandler(createFailOnUnknownStatusDownloadHandler());
    getRequest.send(resourceData.getOriginURI());
  }

  private Handler<HttpClientResponse> createDownloadedResourceHandler(final ResolverResult resourceData,
                                                                      final Path targetPath) {
    return new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        handleDownloadedResource(resp);
      }

      private void handleDownloadedResource(HttpClientResponse resp) {
        final OutputStream os = createTargetFileOutputStream(resourceData, targetPath);
        if (os == null) {
          downloadWaiter.end(false);
          return;
        }
        AtomicInteger written = new AtomicInteger();
        int contentLength = Integer.valueOf(resp.headers().get("content-length"));
        resp.dataHandler(createDownloadDataHandler(os, written, contentLength));
        resp.endHandler(createDownloadEndHandler(os));
      }
    };
  }

  @Override
  public void close() {
    client.close();
    client = null;
  }

  private VoidHandler createDownloadEndHandler(final OutputStream os) {
    return new VoidHandler() {
      @Override
      protected void handle() {
        handleDownloadEnd();
      }

      private void handleDownloadEnd() {
        if (!suppressDownloadCounter) {
          System.out.println("\rDownloading 100%");
        }
        try {
          os.close();
          downloadWaiter.end(true);
        } catch (IOException e) {
          log.error("Failed to flush targetPath", e);
          downloadWaiter.end(false);
        }
      }
    };
  }

  private Handler<Buffer> createDownloadDataHandler(final OutputStream os, final AtomicInteger written,
                                                    final int contentLength) {
    return new Handler<Buffer>() {
      long lastPercent = 0;

      public void handle(Buffer data) {
        handleDownloadData(data);
      }

      private void handleDownloadData(Buffer data) {
        int bytesWritten = written.get();
        try {
          os.write(data.getBytes());
        } catch (IOException e) {
          log.error("Failed to write to targetPath", e);
          downloadWaiter.end(false);
          return;
        }
        if (!suppressDownloadCounter) {
          written.addAndGet(data.length());
          long percent = Math.round(100 * (double) bytesWritten / contentLength);
          if (percent > lastPercent) {
            System.out.print("\rDownloading " + percent + "%");
            lastPercent = percent;
          }
        }
      }
    };
  }

  private OutputStream createTargetFileOutputStream(ResolverResult resourceData, Path targetPath) {
    final OutputStream os;
    log.info("Downloading " + resourceData.getModuleIdentifier() + ". Please wait...");
    try {
      os = new BufferedOutputStream(new FileOutputStream(targetPath.toAbsolutePath().toFile()));
    } catch (IOException e) {
      log.error("Failed to open targetPath", e);
      return null;
    }
    return os;
  }

  protected final Handler<HttpClientResponse> createFailOnUnknownStatusFindHandler() {
    return new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        log.debug(resp.statusCode() + " - " + resp.statusMessage());
        findWaiter.end(ResolverResult.FAILED);
      }
    };
  }

  protected final Handler<HttpClientResponse> createFailOnUnknownStatusDownloadHandler() {
    return new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        log.debug(resp.statusCode() + " - " + resp.statusMessage());
        downloadWaiter.end(false);
      }
    };
  }

  protected final GetRequest createGetRequest() {
    GetRequest getRequest = new GetRequest(client);
    addRedirectHandlers(getRequest);
    return getRequest;
  }

  protected final HeadRequest createHeadRequest() {
    HeadRequest headRequest = new HeadRequest(client);
    addRedirectHandlers(headRequest);
    return headRequest;
  }

  protected final void addRedirectHandlers(final ResolverRequest request) {
    Handler<HttpClientResponse> handler = new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        handleRedirect(request, resp);
      }

      private void handleRedirect(ResolverRequest request, HttpClientResponse resp) {
        // follow redirects
        String location = resp.headers().get("location");
        if (location == null) {
          log.error("HTTP redirect with no location header");
        } else {
          try {
            URI redirectURI = new URI(location);
            int redirectPort = redirectURI.getPort();
            if (redirectPort == -1) {
              redirectPort = 80;
            }

            client.close();
            client = ResolverRequest.createClient(vertx, redirectURI.getScheme(), redirectURI.getHost(), redirectPort);

            request.cloneWithNewClient(client);

            // We only allow 1 redirect
            request.removeHandlersWithStatusCode(301);
            request.removeHandlersWithStatusCode(302);
            request.removeHandlersWithStatusCode(303);
            request.removeHandlersWithStatusCode(308);

            request.send(redirectURI.getPath());
          } catch (URISyntaxException e) {
            log.error("Invalid redirect URI: " + location);
          }
        }
      }
    };
    request.addHandler(301, handler);
    request.addHandler(302, handler);
    request.addHandler(303, handler);
    request.addHandler(308, handler);
  }
}
