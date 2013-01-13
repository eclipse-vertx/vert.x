/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.deploy.impl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.impl.DefaultHttpClient;
import org.vertx.java.core.impl.ActionFuture;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.lang.Args;

/**
 * A customized HttpClient making (file) downloads more easy. Also supporting proxy hosts.
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author Juergen Donnerstag
 */
public class DownloadHttpClient extends DefaultHttpClient {

	private static final Logger log = LoggerFactory.getLogger(DownloadHttpClient.class);
	
	private final VertxInternal vertx;
	
	// Proxy Host and Port
	private final String proxyHost;
  private final int proxyPort;

  // Download server and port
  private String host;
  private int port;

  // User provided exception handler
  private Handler<Exception> exceptionHandler;

  // Wait until download finished (successful or unsuccessful)
  private final ActionFuture<Void> latch = new ActionFuture<>();
  
  // The data (e.g. file content) being downloaded
  private final AtomicReference<Buffer> mod = new AtomicReference<>();

  private Handler<Buffer> backgroundHandler;
  private AsyncResultHandler<Void> doneHandler;
  
	/**
	 * Constructor
	 */
	public DownloadHttpClient(final VertxInternal vertx) {
		this(vertx, null, 0);
	}
	
	/**
	 * Constructor
	 */
	public DownloadHttpClient(final VertxInternal vertx, final String proxyHost, final int proxyPort) {
		super(vertx);
		
		this.vertx = Args.notNull(vertx, "vertx");
		this.proxyHost = proxyHost;
		this.proxyPort = proxyPort;
	}

	@Override
	public void exceptionHandler(Handler<Exception> handler) {
		this.exceptionHandler = handler;
	}

	public void backgroundHandler(Handler<Buffer> handler) {
		this.backgroundHandler = handler;
	}

  public void doneHandler(AsyncResultHandler<Void> handler) {
  	this.doneHandler = handler;
  }

  public final ActionFuture<Void> future() {
  	return this.latch;
  }
  
	@Override
	public DefaultHttpClient setHost(String host) {
		this.host = host;
		return this;
	}
	
	@Override
	public DefaultHttpClient setPort(int port) {
		this.port = port;
		return this;
	}
	
	@Override
	public HttpClientRequest get(String uri, final Handler<HttpClientResponse> responseHandler) {

		// Extract host and port from URI, if present
		String protocol = null;
		try {
			URL url = new URL(uri);
			if (url.getHost() != null) {
				setHost(url.getHost());
			}
			if (url.getPort() != -1) {
				setPort(url.getPort());
			}
			protocol = url.getProtocol();
		} catch (MalformedURLException ignore) {
		}

    final String url = (protocol == null ? "http" : protocol) + "://" + host + (port == 0 ? "" : ":" + port) + uri;

    if (proxyHost != null) {
      super.setHost(proxyHost);
      super.setPort(proxyPort);
      // The proxy needs the host and port in the url  to forward the request
      uri = url;
    } else {
    	super.setHost(host);
    	super.setPort(port);
    }

    // Register our exception handler, and forward to user provided handler if present
    exceptionHandler(new Handler<Exception>() {
      public void handle(Exception e) {
      	log.error("Error while downloading", e);
      	if (exceptionHandler != null) {
      		try {
      			exceptionHandler.handle(e);
      		} catch (Exception ex) {
      			log.error("Exception during exception handling => ignore", ex);
      		}
      	}
      	DownloadHttpClient.this.callDoneHandler(null, e);
      }
    });
    
    HttpClientRequest req = super.get(uri, new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
      	// forward to the user provided handler, if present
      	if (responseHandler != null) {
      		responseHandler.handle(resp);
      	}
        if (resp.statusCode == 200) {
          resp.bodyHandler(new Handler<Buffer>() {
            public void handle(final Buffer buffer) {
              // Make sure we can access the buffer outside the handler
            	mod.set(buffer);
              handleData(buffer);
            }
          });
        } else if (resp.statusCode == 404) {
        	throw new HttpException(404, url, "File not found");
        } else {
          throw new HttpException(resp.statusCode, url, "Download failure");
        }
      }
    });
    
    req.putHeader(HttpHeaders.Names.HOST, proxyHost != null ? proxyHost : host);
    return req;
	}

	private void handleData(final Buffer buffer) {
  	log.info("Thread: " + Thread.currentThread().getName() + " " + Thread.currentThread().getId() + "; Context: " + vertx.getContext());
		if (backgroundHandler != null) {
			final Context ctx = vertx.getContext();
			ctx.executeOnWorker(new Runnable() {
				@Override
				public void run() {
					Exception e = null;
					try {
						log.error(">> blocking action: unzip");
			    	log.info("Thread: " + Thread.currentThread().getName() + " " + Thread.currentThread().getId() + "; Context: " + vertx.getContext());
						backgroundHandler.handle(buffer);
						log.error("<< blocking action: unzip");
					} catch (Exception ex) {
						e = ex;
						log.error("Error while executing background job on downloaded data", ex);
					} finally {
						DownloadHttpClient.this.callDoneHandler(ctx, e);
					}
				}
			});
		} else {
			DownloadHttpClient.this.callDoneHandler(null, null);
		}
	}

	private void callDoneHandler(final Context context, final Exception ex) {
		final AsyncResult<Void> res = new AsyncResult<Void>(ex);
		if (doneHandler != null) {
			if (context != null) {
				context.execute(new Runnable() {
					@Override
					public void run() {
						doneHandler.handle(res);
					}
				});
			} else {
				doneHandler.handle(res);
			}
		}
		latch.countDown(res);
	}
	
	/**
	 * 
	 */
	public static class HttpException extends RuntimeException {

		private static final long serialVersionUID = 1L;
		
		private final int statusCode;
		private final String url;
		
		public HttpException(int statusCode, String url, String msg) {
			super(statusCode + ": " + msg + " [" + url + "]");
			this.statusCode = statusCode;
			this.url = url;
		}
		
		public int statusCode() {
			return statusCode;
		}
		
		public String url() {
			return url;
		}
	}
}
