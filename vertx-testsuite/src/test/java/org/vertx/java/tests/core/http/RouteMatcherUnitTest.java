package org.vertx.java.tests.core.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;

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
public class RouteMatcherUnitTest {
	RouteMatcher m;
	TestHandler h;
	TestRequest r;

	@Before
	public void setUp() throws Exception {
		m = new RouteMatcher();
		h = new TestHandler();
		r = new TestRequest();
	}

	@Test
	public void testNonMatchingGet() {
		m.get("/foo/", h);
		m.handle(r.setPath("/blah/").setMethod("GET"));
		assertEquals(404, r.response.getStatusCode());
	}
	
	@Test
	public void testNonMatchingHandlerGet() {
		m.noMatch(h);
		m.handle(r.setPath("/blah/").setMethod("GET"));
		assertTrue(h.called);
	}

	@Test
	public void testMatchingGet() {
		final String path = "/foo/";
		m.get(path, h);
		m.handle(r.setPath(path).setMethod("GET"));
		assertTrue(h.called);
	}

	@Test
	public void testMatchingPut() {
		final String path = "/foo/";
		m.put(path, h);
		m.handle(r.setPath(path).setMethod("PUT"));
		assertTrue(h.called);
	}

	@Test
	public void testMatchingPost() {
		final String path = "/foo/";
		m.post(path, h);
		m.handle(r.setPath(path).setMethod("POST"));
		assertTrue(h.called);
	}

	@Test
	public void testMatchingDelete() {
		final String path = "/foo/";
		m.delete(path, h);
		m.handle(r.setPath(path).setMethod("DELETE"));
		assertTrue(h.called);
	}

	@Test
	public void testMatchingOptions() {
		final String path = "/foo/";
		m.options(path, h);
		m.handle(r.setPath(path).setMethod("OPTIONS"));
		assertTrue(h.called);
	}

	@Test
	public void testMatchingHead() {
		final String path = "/foo/";
		m.head(path, h);
		m.handle(r.setPath(path).setMethod("HEAD"));
		assertTrue(h.called);
	}

	@Test
	public void testMatchingTrace() {
		final String path = "/foo/";
		m.trace(path, h);
		m.handle(r.setPath(path).setMethod("TRACE"));
		assertTrue(h.called);
	}

	@Test
	public void testMatchingPatch() {
		final String path = "/foo/";
		m.patch(path, h);
		m.handle(r.setPath(path).setMethod("PATCH"));
		assertTrue(h.called);
	}

	@Test
	public void testMatchingConnect() {
		final String path = "/foo/";
		m.connect(path, h);
		m.handle(r.setPath(path).setMethod("CONNECT"));
		assertTrue(h.called);
	}
	
	@Test
	public void testRemovePatternGet() {
		final String path = "/foo/";
		m.get(path, h);
		assertEquals(1, m.removePattern(path, "GET"));
		m.handle(r.setPath(path).setMethod("GET"));
		assertEquals(404, r.response.getStatusCode());
	}
	
	@Test
	public void testRemoveMultiPatternGet() {
		final String path = "/foo/";
		m.get(path, h);
		m.get(path, h);
		assertEquals(2, m.removePattern(path, "GET"));
		m.handle(r.setPath(path).setMethod("GET"));
		assertEquals(404, r.response.getStatusCode());
	}
	
	@Test
	public void testRemovePatternPut() {
		final String path = "/foo/";
		m.put(path, h);
		assertEquals(1, m.removePattern(path, "PUT"));
		m.handle(r.setPath(path).setMethod("PUT"));
		assertEquals(404, r.response.getStatusCode());
	}
	
	@Test
	public void testRemovePatternPost() {
		final String path = "/foo/";
		m.post(path, h);
		assertEquals(1, m.removePattern(path, "POST"));
		m.handle(r.setPath(path).setMethod("POST"));
		assertEquals(404, r.response.getStatusCode());
	}
	
	@Test
	public void testRemovePatternDelete() {
		final String path = "/foo/";
		m.delete(path, h);
		assertEquals(1, m.removePattern(path, "DELETE"));
		m.handle(r.setPath(path).setMethod("DELETE"));
		assertEquals(404, r.response.getStatusCode());
	}
	
	@Test
	public void testRemovePatternOptions() {
		final String path = "/foo/";
		m.options(path, h);
		assertEquals(1, m.removePattern(path, "OPTIONS"));
		m.handle(r.setPath(path).setMethod("OPTIONS"));
		assertEquals(404, r.response.getStatusCode());
	}
	
	@Test
	public void testRemovePatternHead() {
		final String path = "/foo/";
		m.head(path, h);
		assertEquals(1, m.removePattern(path, "HEAD"));
		m.handle(r.setPath(path).setMethod("HEAD"));
		assertEquals(404, r.response.getStatusCode());
	}
	
	@Test
	public void testRemovePatternTrace() {
		final String path = "/foo/";
		m.trace(path, h);
		assertEquals(1, m.removePattern(path, "TRACE"));
		m.handle(r.setPath(path).setMethod("TRACE"));
		assertEquals(404, r.response.getStatusCode());
	}
	
	@Test
	public void testRemovePatternPatch() {
		final String path = "/foo/";
		m.patch(path, h);
		assertEquals(1, m.removePattern(path, "PATCH"));
		m.handle(r.setPath(path).setMethod("PATCH"));
		assertEquals(404, r.response.getStatusCode());
	}
	
	@Test
	public void testRemovePatternConnect() {
		final String path = "/foo/";
		m.connect(path, h);
		assertEquals(1, m.removePattern(path, "CONNECT"));
		m.handle(r.setPath(path).setMethod("CONNECT"));
		assertEquals(404, r.response.getStatusCode());
	}
	
	@Test
	public void testRemoveMultiplePattern() {
		final String path = "/foo/";
		m.all(path, h);
		assertEquals(9, m.removePattern(path, "ALL"));
		m.handle(r.setPath(path).setMethod("GET"));
		assertEquals(404, r.response.getStatusCode());
	}

	@Test
	public void testMatchingAll() {
		final String path = "/foo/";
		m.all(path, h);
		m.handle(r.setPath(path).setMethod("GET"));
		assertTrue(h.called);
		h.called = false;
		m.handle(r.setPath(path).setMethod("PUT"));
		assertTrue(h.called);
		h.called = false;
		m.handle(r.setPath(path).setMethod("POST"));
		assertTrue(h.called);
		h.called = false;
		m.handle(r.setPath(path).setMethod("DELETE"));
		assertTrue(h.called);
		h.called = false;
		m.handle(r.setPath(path).setMethod("OPTIONS"));
		assertTrue(h.called);
		h.called = false;
		m.handle(r.setPath(path).setMethod("HEAD"));
		assertTrue(h.called);
		h.called = false;
		m.handle(r.setPath(path).setMethod("TRACE"));
		assertTrue(h.called);
		h.called = false;
		m.handle(r.setPath(path).setMethod("PATCH"));
		assertTrue(h.called);
		h.called = false;
		m.handle(r.setPath(path).setMethod("CONNECT"));
		assertTrue(h.called);
	}
	
	@Test
	public void testNonMatchingRegexGet() {
		m.getWithRegEx("/f../", h);
		m.handle(r.setPath("/blah/").setMethod("GET"));
		assertEquals(404, r.response.getStatusCode());
	}

	@Test
	public void testMatchingRegexGet() {
		final String path = "/foo/";
		m.getWithRegEx("/f../", h);
		m.handle(r.setPath(path).setMethod("GET"));
		assertTrue(h.called);
	}

	@Test
	public void testMatchingRegexPut() {
		final String path = "/foo/";
		m.putWithRegEx("/f../", h);
		m.handle(r.setPath(path).setMethod("PUT"));
		assertTrue(h.called);
	}

	@Test
	public void testMatchingRegexPost() {
		final String path = "/foo/";
		m.postWithRegEx("/f../", h);
		m.handle(r.setPath(path).setMethod("POST"));
		assertTrue(h.called);
	}

	@Test
	public void testMatchingRegexDelete() {
		final String path = "/foo/";
		m.deleteWithRegEx("/f../", h);
		m.handle(r.setPath(path).setMethod("DELETE"));
		assertTrue(h.called);
	}

	@Test
	public void testMatchingRegexOptions() {
		final String path = "/foo/";
		m.optionsWithRegEx("/f../", h);
		m.handle(r.setPath(path).setMethod("OPTIONS"));
		assertTrue(h.called);
	}

	@Test
	public void testMatchingRegexHead() {
		final String path = "/foo/";
		m.headWithRegEx("/f../", h);
		m.handle(r.setPath(path).setMethod("HEAD"));
		assertTrue(h.called);
	}

	@Test
	public void testMatchingRegexTrace() {
		final String path = "/foo/";
		m.traceWithRegEx("/f../", h);
		m.handle(r.setPath(path).setMethod("TRACE"));
		assertTrue(h.called);
	}

	@Test
	public void testMatchingRegexPatch() {
		final String path = "/foo/";
		m.patchWithRegEx("/f../", h);
		m.handle(r.setPath(path).setMethod("PATCH"));
		assertTrue(h.called);
	}

	@Test
	public void testMatchingRegexConnect() {
		final String path = "/foo/";
		m.connectWithRegEx("/f../", h);
		m.handle(r.setPath(path).setMethod("CONNECT"));
		assertTrue(h.called);
	}

	@Test
	public void testMatchingRegexAll() {
		final String path = "/foo/";
		m.allWithRegEx("/f../", h);
		m.handle(r.setPath(path).setMethod("GET"));
		assertTrue(h.called);
		h.called = false;
		m.handle(r.setPath(path).setMethod("PUT"));
		assertTrue(h.called);
		h.called = false;
		m.handle(r.setPath(path).setMethod("POST"));
		assertTrue(h.called);
		h.called = false;
		m.handle(r.setPath(path).setMethod("DELETE"));
		assertTrue(h.called);
		h.called = false;
		m.handle(r.setPath(path).setMethod("OPTIONS"));
		assertTrue(h.called);
		h.called = false;
		m.handle(r.setPath(path).setMethod("HEAD"));
		assertTrue(h.called);
		h.called = false;
		m.handle(r.setPath(path).setMethod("TRACE"));
		assertTrue(h.called);
		h.called = false;
		m.handle(r.setPath(path).setMethod("PATCH"));
		assertTrue(h.called);
		h.called = false;
		m.handle(r.setPath(path).setMethod("CONNECT"));
		assertTrue(h.called);
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
