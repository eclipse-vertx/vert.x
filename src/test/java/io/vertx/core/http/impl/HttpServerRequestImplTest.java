package io.vertx.core.http.impl;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpTestBase;
import org.junit.Test;

import static io.vertx.test.core.TestUtils.assertIllegalStateException;

public class HttpServerRequestImplTest extends HttpTestBase {

    @Test
    public void testSetExpectMultipartPostMultipartForm() {
        server.requestHandler(req -> {
            assertFalse(req.isExpectMultipart());
            req.setExpectMultipart(true);
            assertTrue(req.isExpectMultipart());
            req.response().end();
        });
        server.listen(DEFAULT_HTTP_PORT, onSuccess(event -> {
            HttpClientRequest req = client.request(HttpMethod.POST,
                                                   DEFAULT_HTTP_PORT,
                                                   DEFAULT_HTTP_HOST,
                                                   DEFAULT_TEST_URI,
                                                   onSuccess(res -> testComplete()));
            req.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.MULTIPART_FORM_DATA);
            req.end();
        }));
        await();
    }

    @Test
    public void testSetExpectMultipartPostUrlEncoded() {
        server.requestHandler(req -> {
            assertFalse(req.isExpectMultipart());
            req.setExpectMultipart(true);
            assertTrue(req.isExpectMultipart());
            req.response().end();
        });
        server.listen(DEFAULT_HTTP_PORT, onSuccess(event -> {
            HttpClientRequest req = client.request(HttpMethod.POST,
                                                   DEFAULT_HTTP_PORT,
                                                   DEFAULT_HTTP_HOST,
                                                   DEFAULT_TEST_URI,
                                                   onSuccess(res -> testComplete()));
            req.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED);
            req.end();
        }));
        await();
    }

    @Test
    public void testSetExpectMultipartPutMultipartForm() {
        server.requestHandler(req -> {
            assertFalse(req.isExpectMultipart());
            req.setExpectMultipart(true);
            assertTrue(req.isExpectMultipart());
            req.response().end();
        });
        server.listen(DEFAULT_HTTP_PORT, onSuccess(event -> {
            HttpClientRequest req = client.request(HttpMethod.PUT,
                                                   DEFAULT_HTTP_PORT,
                                                   DEFAULT_HTTP_HOST,
                                                   DEFAULT_TEST_URI,
                                                   onSuccess(res -> testComplete()));
            req.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.MULTIPART_FORM_DATA);
            req.end();
        }));
        await();
    }

    @Test
    public void testSetExpectMultipartPutUrlEncoded() {
        server.requestHandler(req -> {
            assertFalse(req.isExpectMultipart());
            req.setExpectMultipart(true);
            assertTrue(req.isExpectMultipart());
            req.response().end();
        });
        server.listen(DEFAULT_HTTP_PORT, onSuccess(event -> {
            HttpClientRequest req = client.request(HttpMethod.PUT,
                                                   DEFAULT_HTTP_PORT,
                                                   DEFAULT_HTTP_HOST,
                                                   DEFAULT_TEST_URI,
                                                   onSuccess(res -> testComplete()));
            req.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED);
            req.end();
        }));
        await();
    }

    @Test
    public void testSetExpectMultipartPatchMultipartForm() {
        server.requestHandler(req -> {
            assertFalse(req.isExpectMultipart());
            req.setExpectMultipart(true);
            assertTrue(req.isExpectMultipart());
            req.response().end();
        });
        server.listen(DEFAULT_HTTP_PORT, onSuccess(event -> {
            HttpClientRequest req = client.request(HttpMethod.PATCH,
                                                   DEFAULT_HTTP_PORT,
                                                   DEFAULT_HTTP_HOST,
                                                   DEFAULT_TEST_URI,
                                                   onSuccess(res -> testComplete()));
            req.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.MULTIPART_FORM_DATA);
            req.end();
        }));
        await();
    }

    @Test
    public void testSetExpectMultipartPatchUrlEncoded() {
        server.requestHandler(req -> {
            assertFalse(req.isExpectMultipart());
            req.setExpectMultipart(true);
            assertTrue(req.isExpectMultipart());
            req.response().end();
        });
        server.listen(DEFAULT_HTTP_PORT, onSuccess(event -> {
            HttpClientRequest req = client.request(HttpMethod.PATCH,
                                                   DEFAULT_HTTP_PORT,
                                                   DEFAULT_HTTP_HOST,
                                                   DEFAULT_TEST_URI,
                                                   onSuccess(res -> testComplete()));
            req.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED);
            req.end();
        }));
        await();
    }

    @Test
    public void testSetExpectMultipartDeleteMultipartForm() {
        server.requestHandler(req -> {
            assertFalse(req.isExpectMultipart());
            req.setExpectMultipart(true);
            assertTrue(req.isExpectMultipart());
            req.response().end();
        });
        server.listen(DEFAULT_HTTP_PORT, onSuccess(event -> {
            HttpClientRequest req = client.request(HttpMethod.DELETE,
                                                   DEFAULT_HTTP_PORT,
                                                   DEFAULT_HTTP_HOST,
                                                   DEFAULT_TEST_URI,
                                                   onSuccess(res -> testComplete()));
            req.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.MULTIPART_FORM_DATA);
            req.end();
        }));
        await();
    }

    @Test
    public void testSetExpectMultipartDeleteUrlEncoded() {
        server.requestHandler(req -> {
            assertFalse(req.isExpectMultipart());
            req.setExpectMultipart(true);
            assertTrue(req.isExpectMultipart());
            req.response().end();
        });
        server.listen(DEFAULT_HTTP_PORT, onSuccess(event -> {
            HttpClientRequest req = client.request(HttpMethod.DELETE,
                                                   DEFAULT_HTTP_PORT,
                                                   DEFAULT_HTTP_HOST,
                                                   DEFAULT_TEST_URI,
                                                   onSuccess(res -> testComplete()));
            req.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED);
            req.end();
        }));
        await();
    }

    @Test
    public void testSetExpectMultipartNoContentType() {
        server.requestHandler(req -> {
            assertFalse(req.isExpectMultipart());
            assertIllegalStateException(() -> req.setExpectMultipart(true));
            req.response().end();
        });
        server.listen(DEFAULT_HTTP_PORT, onSuccess(event -> {
            HttpClientRequest req = client.request(HttpMethod.POST,
                                                   DEFAULT_HTTP_PORT,
                                                   DEFAULT_HTTP_HOST,
                                                   DEFAULT_TEST_URI,
                                                   onSuccess(res -> testComplete()));
            req.end();
        }));
        await();
    }

    @Test
    public void testSetExpectMultipartInvalidContentType() {
        server.requestHandler(req -> {
            assertFalse(req.isExpectMultipart());
            assertIllegalStateException(() -> req.setExpectMultipart(true));
            req.response().end();
        });
        server.listen(DEFAULT_HTTP_PORT, onSuccess(event -> {
            HttpClientRequest req = client.request(HttpMethod.POST,
                                                   DEFAULT_HTTP_PORT,
                                                   DEFAULT_HTTP_HOST,
                                                   DEFAULT_TEST_URI,
                                                   onSuccess(res -> testComplete()));
            req.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            req.end();
        }));
        await();
    }

    @Test
    public void testSetExpectMultipartInvalidGet() {
        server.requestHandler(req -> {
            assertFalse(req.isExpectMultipart());
            assertIllegalStateException(() -> req.setExpectMultipart(true));
            req.response().end();
        });
        server.listen(DEFAULT_HTTP_PORT, onSuccess(event -> {
            HttpClientRequest req = client.request(HttpMethod.GET,
                                                   DEFAULT_HTTP_PORT,
                                                   DEFAULT_HTTP_HOST,
                                                   DEFAULT_TEST_URI,
                                                   onSuccess(res -> testComplete()));
            req.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.MULTIPART_FORM_DATA);
            req.end();
        }));
        await();
    }

    @Test
    public void testSetExpectMultipartInvalidConnect() {
        server.requestHandler(req -> {
            assertFalse(req.isExpectMultipart());
            assertIllegalStateException(() -> req.setExpectMultipart(true));
            req.response().end();
        });
        server.listen(DEFAULT_HTTP_PORT, onSuccess(event -> {
            HttpClientRequest req = client.request(HttpMethod.CONNECT,
                                                   DEFAULT_HTTP_PORT,
                                                   DEFAULT_HTTP_HOST,
                                                   DEFAULT_TEST_URI,
                                                   onSuccess(res -> testComplete()));
            req.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.MULTIPART_FORM_DATA);
            req.end();
        }));
        await();
    }

    @Test
    public void testSetExpectMultipartInvalidOptions() {
        server.requestHandler(req -> {
            assertFalse(req.isExpectMultipart());
            assertIllegalStateException(() -> req.setExpectMultipart(true));
            req.response().end();
        });
        server.listen(DEFAULT_HTTP_PORT, onSuccess(event -> {
            HttpClientRequest req = client.request(HttpMethod.OPTIONS,
                                                   DEFAULT_HTTP_PORT,
                                                   DEFAULT_HTTP_HOST,
                                                   DEFAULT_TEST_URI,
                                                   onSuccess(res -> testComplete()));
            req.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.MULTIPART_FORM_DATA);
            req.end();
        }));
        await();
    }

    @Test
    public void testSetExpectMultipartInvalidHead() {
        server.requestHandler(req -> {
            assertFalse(req.isExpectMultipart());
            assertIllegalStateException(() -> req.setExpectMultipart(true));
            req.response().end();
        });
        server.listen(DEFAULT_HTTP_PORT, onSuccess(event -> {
            HttpClientRequest req = client.request(HttpMethod.HEAD,
                                                   DEFAULT_HTTP_PORT,
                                                   DEFAULT_HTTP_HOST,
                                                   DEFAULT_TEST_URI,
                                                   onSuccess(res -> testComplete()));
            req.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.MULTIPART_FORM_DATA);
            req.end();
        }));
        await();
    }

    @Test
    public void testSetExpectMultipartInvalidTrace() {
        server.requestHandler(req -> {
            assertFalse(req.isExpectMultipart());
            assertIllegalStateException(() -> req.setExpectMultipart(true));
            req.response().end();
        });
        server.listen(DEFAULT_HTTP_PORT, onSuccess(event -> {
            HttpClientRequest req = client.request(HttpMethod.TRACE,
                                                   DEFAULT_HTTP_PORT,
                                                   DEFAULT_HTTP_HOST,
                                                   DEFAULT_TEST_URI,
                                                   onSuccess(res -> testComplete()));
            req.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.MULTIPART_FORM_DATA);
            req.end();
        }));
        await();
    }

    @Test
    public void testSetExpectMultipartTwice() {
        server.requestHandler(req -> {
            assertFalse(req.isExpectMultipart());
            req.setExpectMultipart(true);
            assertTrue(req.isExpectMultipart());
            req.setExpectMultipart(true);
            assertTrue(req.isExpectMultipart());
            req.response().end();
        });
        server.listen(DEFAULT_HTTP_PORT, onSuccess(event -> {
            HttpClientRequest req = client.request(HttpMethod.DELETE,
                                                   DEFAULT_HTTP_PORT,
                                                   DEFAULT_HTTP_HOST,
                                                   DEFAULT_TEST_URI,
                                                   onSuccess(res -> testComplete()));
            req.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED);
            req.end();
        }));
        await();
    }
}