package org.vertx.java.tests.core.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;

import org.junit.Before;
import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerFileUpload;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.http.HttpVersion;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.http.impl.HttpHeadersAdapter;
import org.vertx.java.core.net.NetSocket;

/**
 * @author Troy Collinsworth
 */
public class RouteMatcherConcurrencyTest {
	long endTime;
	RouteMatcher m;
	TestHandler h;
	TestRequest r;
	Exception exception;

	@Before
	public void setUp() throws Exception {
		endTime = System.currentTimeMillis() + 1000;
		m = new RouteMatcher();
		h = new TestHandler();
		r = new TestRequest();
	}

	@Test
	public void test() throws InterruptedException {
		ExecutorService execService = Executors.newFixedThreadPool(2);
		execService.submit(new RouteMatching());
		Thread.sleep(500);
		execService.submit(new RouteUpdater());
		while (System.currentTimeMillis() < endTime) {
			if (exception != null) {
				execService.shutdownNow();
				exception.printStackTrace();
				fail("Expected no exception, found " + exception);
			}
			Thread.sleep(1);
		}
		execService.awaitTermination(2, TimeUnit.SECONDS);
		execService.shutdownNow();
	}

	public class RouteUpdater implements Runnable {
		@Override
		public void run() {
			while (System.currentTimeMillis() < endTime) {
				try {
					final String path = "/boo/";
					m.setUpdatable(true);
					Thread.sleep(100);
					m.get(path, h);
					m.setUpdatable(false);
					Thread.sleep(100);
					m.setUpdatable(true);
					assertEquals(1, m.removePattern(path, "GET"));
					m.setUpdatable(false);
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
						// Ignore
					}
				} catch (Exception e) {
					exception = e;
				}
			}
		}
	}

	public class RouteMatching implements Runnable {
		@Override
		public void run() {
			for (int i = 0; i < 100; i++) {
				m.get("/boo/", h);
				m.get("/bar/", h);
				m.get("/baz/", h);
			}
			final String path = "/foo/";
			m.get(path, h);
			while (System.currentTimeMillis() < endTime) {
				try {
					m.handle(r.setPath(path).setMethod("GET"));
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
						// Ignore
					}
				} catch (Exception e) {
					exception = e;
				}
			}
		}
	}

	class TestHandler implements Handler<HttpServerRequest> {
		public boolean called = false;

		@Override
		public void handle(HttpServerRequest event) {
			called = true;
		}
	}

	class TestRequest implements HttpServerRequest {
		TestResponse response = new TestResponse();
		MultiMap params = new HttpHeadersAdapter(null);
		String path;
		String method;

		public TestRequest setMethod(String method) {
			this.method = method;
			return this;
		}

		public TestRequest setPath(String path) {
			this.path = path;
			return this;
		}

		@Override
		public HttpServerRequest endHandler(Handler<Void> endHandler) {
			return null;
		}

		@Override
		public HttpServerRequest dataHandler(Handler<Buffer> handler) {
			return null;
		}

		@Override
		public HttpServerRequest pause() {
			return null;
		}

		@Override
		public HttpServerRequest resume() {
			return null;
		}

		@Override
		public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
			return null;
		}

		@Override
		public HttpVersion version() {
			return null;
		}

		@Override
		public String method() {
			return method;
		}

		@Override
		public String uri() {
			return null;
		}

		@Override
		public String path() {
			return path;
		}

		@Override
		public String query() {
			return null;
		}

		@Override
		public HttpServerResponse response() {
			return response;
		}

		@Override
		public MultiMap headers() {
			return null;
		}

		@Override
		public MultiMap params() {
			return params;
		}

		@Override
		public InetSocketAddress remoteAddress() {
			return null;
		}

		@Override
		public InetSocketAddress localAddress() {
			return null;
		}

		@Override
		public X509Certificate[] peerCertificateChain()
				throws SSLPeerUnverifiedException {
			return null;
		}

		@Override
		public URI absoluteURI() {
			return null;
		}

		@Override
		public HttpServerRequest bodyHandler(Handler<Buffer> bodyHandler) {
			return null;
		}

		@Override
		public NetSocket netSocket() {
			return null;
		}

		@Override
		public HttpServerRequest expectMultiPart(boolean expect) {
			return null;
		}

		@Override
		public HttpServerRequest uploadHandler(
				Handler<HttpServerFileUpload> uploadHandler) {
			return null;
		}

		@Override
		public MultiMap formAttributes() {
			return null;
		}
	}

	class TestResponse implements HttpServerResponse {
		int statusCode = -1;

		@Override
		public HttpServerResponse exceptionHandler(Handler<Throwable> handler) {
			return null;
		}

		@Override
		public HttpServerResponse setWriteQueueMaxSize(int maxSize) {
			return null;
		}

		@Override
		public boolean writeQueueFull() {
			return false;
		}

		@Override
		public HttpServerResponse drainHandler(Handler<Void> handler) {
			return null;
		}

		@Override
		public int getStatusCode() {
			return statusCode;
		}

		@Override
		public HttpServerResponse setStatusCode(int statusCode) {
			this.statusCode = statusCode;
			return this;
		}

		@Override
		public String getStatusMessage() {
			return null;
		}

		@Override
		public HttpServerResponse setStatusMessage(String statusMessage) {
			return null;
		}

		@Override
		public HttpServerResponse setChunked(boolean chunked) {
			return null;
		}

		@Override
		public boolean isChunked() {
			return false;
		}

		@Override
		public MultiMap headers() {
			return null;
		}

		@Override
		public HttpServerResponse putHeader(String name, String value) {
			return null;
		}

		@Override
		public HttpServerResponse putHeader(CharSequence name,
				CharSequence value) {
			return null;
		}

		@Override
		public HttpServerResponse putHeader(String name, Iterable<String> values) {
			return null;
		}

		@Override
		public HttpServerResponse putHeader(CharSequence name,
				Iterable<CharSequence> values) {
			return null;
		}

		@Override
		public MultiMap trailers() {
			return null;
		}

		@Override
		public HttpServerResponse putTrailer(String name, String value) {
			return null;
		}

		@Override
		public HttpServerResponse putTrailer(CharSequence name,
				CharSequence value) {
			return null;
		}

		@Override
		public HttpServerResponse putTrailer(String name,
				Iterable<String> values) {
			return null;
		}

		@Override
		public HttpServerResponse putTrailer(CharSequence name,
				Iterable<CharSequence> value) {
			return null;
		}

		@Override
		public HttpServerResponse closeHandler(Handler<Void> handler) {
			return null;
		}

		@Override
		public HttpServerResponse write(Buffer chunk) {
			return null;
		}

		@Override
		public HttpServerResponse write(String chunk, String enc) {
			return null;
		}

		@Override
		public HttpServerResponse write(String chunk) {
			return null;
		}

		@Override
		public void end(String chunk) {
		}

		@Override
		public void end(String chunk, String enc) {
		}

		@Override
		public void end(Buffer chunk) {
		}

		@Override
		public void end() {
		}

		@Override
		public HttpServerResponse sendFile(String filename) {
			return null;
		}

		@Override
		public HttpServerResponse sendFile(String filename, String notFoundFile) {
			return null;
		}

		@Override
		public HttpServerResponse sendFile(String filename,
				Handler<AsyncResult<Void>> resultHandler) {
			return null;
		}

		@Override
		public HttpServerResponse sendFile(String filename,
				String notFoundFile, Handler<AsyncResult<Void>> resultHandler) {
			return null;
		}

		@Override
		public void close() {
		}
	}
}
