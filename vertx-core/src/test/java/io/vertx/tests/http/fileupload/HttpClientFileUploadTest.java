package io.vertx.tests.http.fileupload;

import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.HttpTestBase;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

public class HttpClientFileUploadTest extends HttpTestBase {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @Test
  public void testFormUrlEncoded() throws Exception {
    server.requestHandler(req -> {
      req.setExpectMultipart(true);
      req.endHandler(v -> {
        assertEquals("param1_value", req.getFormAttribute("param1"));
        req.response().end();
      });
    });
    startServer(testAddress);
    MultiMap form = MultiMap.caseInsensitiveMultiMap();
    form.add("param1", "param1_value");
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST))
      .compose(req -> req
        .send(ClientForm.form(form))
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .await();
  }

  @Test
  public void testFormUrlEncodedWithCharset() throws Exception {
    String str = "ø";
    String expected = URLDecoder.decode(URLEncoder.encode(str, StandardCharsets.ISO_8859_1.name()), StandardCharsets.UTF_8.name());
    server.requestHandler(req -> {
      req.setExpectMultipart(true);
      req.endHandler(v -> {
        String val = req.getFormAttribute("param1");
        assertEquals(expected, val);
        req.response().end();
      });
    });
    startServer();
    MultiMap form = MultiMap.caseInsensitiveMultiMap();
    form.add("param1", str);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST))
      .compose(req -> req
        .send(ClientForm.form(form).charset(StandardCharsets.ISO_8859_1))
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .await();
  }

  @Test
  public void testFormUrlEncodedUnescaped() throws Exception {
    server.requestHandler(req -> {
      req.setExpectMultipart(true);
      req.bodyHandler(body -> {
        assertEquals("grant_type=client_credentials&resource=https%3A%2F%2Fmanagement.core.windows.net%2F", body.toString());
        req.response().end();
      });
    });
    startServer();
    MultiMap form = MultiMap.caseInsensitiveMultiMap();
    form
      .set("grant_type", "client_credentials")
      .set("resource", "https://management.core.windows.net/");
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST))
      .compose(req -> req
        .send(ClientForm.form(form))
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .await();
  }

  @Test
  public void testFormUrlEncodedMultipleHeaders() throws Exception {
    server.requestHandler(req -> {
      req.setExpectMultipart(true);
      req.endHandler(v -> {
        assertEquals(Arrays.asList("1", "2"), req.headers().getAll("bla"));
        req.response().end();
      });
    });
    startServer();
    MultiMap form = MultiMap.caseInsensitiveMultiMap();
    client.request(new RequestOptions(requestOptions).putHeader("bla", Arrays.asList("1", "2")).setMethod(HttpMethod.POST))
      .compose(req -> req
        .send(ClientForm.form(form))
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .await();
  }

  @Test
  public void testFormMultipart() throws Exception {
    server.requestHandler(req -> {
      assertTrue(req.getHeader(HttpHeaders.CONTENT_TYPE).startsWith("multipart/form-data"));
      req.setExpectMultipart(true);
      req.endHandler(v -> {
        assertEquals("param1_value", req.getFormAttribute("param1"));
        req.response().end();
      });
    });
    startServer();
    MultiMap form = MultiMap.caseInsensitiveMultiMap();
    form.add("param1", "param1_value");
    client.request(new RequestOptions(requestOptions).putHeader("content-type", "multipart/form-data").setMethod(HttpMethod.POST))
      .compose(req -> req
        .send(ClientForm.form(form))
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .await();
  }

  @Test
  public void testFormMultipartWithCharset() throws Exception {
    server.requestHandler(req -> {
      req.body().onComplete(onSuccess(body -> {
        assertTrue(body.toString().contains("content-type: text/plain; charset=ISO-8859-1"));
        req.response().end();
      }));
    });
    startServer();
    MultiMap form = MultiMap.caseInsensitiveMultiMap();
    form.add("param1", "param1_value");
    client.request(new RequestOptions(requestOptions).putHeader("content-type", "multipart/form-data").setMethod(HttpMethod.POST))
      .compose(req -> req
        .send(ClientForm.form(form).charset(StandardCharsets.ISO_8859_1))
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .await();
  }

  @Test
  public void testFileUploadFormMultipart32B() throws Exception {
    testFileUploadFormMultipart(32, false);
  }

  @Test
  public void testFileUploadFormMultipart32K() throws Exception {
    testFileUploadFormMultipart(32 * 1024, false);
  }

  @Test
  public void testFileUploadFormMultipart32M() throws Exception {
    testFileUploadFormMultipart(32 * 1024 * 1024, false);
  }

  @Test
  public void testMemoryFileUploadFormMultipart() throws Exception {
    testFileUploadFormMultipart(32 * 1024, true);
  }

  private void testFileUploadFormMultipart(int size, boolean memory) throws Exception {
    Buffer content = Buffer.buffer(TestUtils.randomAlphaString(size));
    Upload upload;
    if (memory) {
      upload = Upload.memoryUpload("test", "test.txt", content);
    } else {
      upload = Upload.fileUpload("test", "test.txt", content);
    }
    ClientMultipartForm form = ClientMultipartForm.multipartForm()
      .attribute("toolkit", "vert.x")
      .attribute("runtime", "jvm");
    testFileUploadFormMultipart(form, Collections.singletonList(upload), (req, uploads) -> {
      assertEquals("vert.x", req.getFormAttribute("toolkit"));
      assertEquals("jvm", req.getFormAttribute("runtime"));
      assertEquals(1, uploads.size());
      assertEquals("test", uploads.get(0).name);
      assertEquals("test.txt", uploads.get(0).filename);
      assertEquals(content, uploads.get(0).data);
    });
  }

  @Test
  public void testFileUploadsFormMultipart() throws Exception {
    Buffer content1 = Buffer.buffer(TestUtils.randomAlphaString(16));
    Buffer content2 = Buffer.buffer(TestUtils.randomAlphaString(16));
    List<Upload> toUpload = Arrays.asList(
      Upload.fileUpload("test1", "test1.txt", content1),
      Upload.fileUpload("test2", "test2.txt", content2)
    );
    ClientMultipartForm form = ClientMultipartForm.multipartForm();
    testFileUploadFormMultipart(form, toUpload, (req, uploads) -> {
      assertEquals(2, uploads.size());
      assertEquals("test1", uploads.get(0).name);
      assertEquals("test1.txt", uploads.get(0).filename);
      assertEquals("UTF-8", uploads.get(0).charset);
      assertEquals(content1, uploads.get(0).data);
      assertEquals("test2", uploads.get(1).name);
      assertEquals("test2.txt", uploads.get(1).filename);
      assertEquals("UTF-8", uploads.get(1).charset);
      assertEquals(content2, uploads.get(1).data);
    });
  }

    @Test
    public void testFileUploadsFormMultipartWithCharset() throws Exception {
      Buffer content = Buffer.buffer(TestUtils.randomAlphaString(16));
      List<Upload> toUpload = Collections.singletonList(Upload.fileUpload("test1", "test1.txt", content));
      ClientMultipartForm form = ClientMultipartForm.multipartForm().charset(StandardCharsets.ISO_8859_1);
      testFileUploadFormMultipart(form, toUpload, (req, uploads) -> {
        assertEquals(1, uploads.size());
        assertEquals("test1", uploads.get(0).name);
        assertEquals("test1.txt", uploads.get(0).filename);
        assertEquals("ISO-8859-1", uploads.get(0).charset);
      });
    }

    @Test
    public void testFileUploadsSameNameFormMultipart() throws Exception {
      Buffer content1 = Buffer.buffer(TestUtils.randomAlphaString(16));
      Buffer content2 = Buffer.buffer(TestUtils.randomAlphaString(16));
      List<Upload> toUpload = Arrays.asList(
        Upload.fileUpload("test", "test1.txt", content1),
        Upload.fileUpload("test", "test2.txt", content2)
      );
      ClientMultipartForm form = ClientMultipartForm.multipartForm();
      testFileUploadFormMultipart(form, toUpload, (req, uploads) -> {
        assertEquals(2, uploads.size());
        assertEquals("test", uploads.get(0).name);
        assertEquals("test1.txt", uploads.get(0).filename);
        assertEquals(content1, uploads.get(0).data);
        assertEquals("test", uploads.get(1).name);
        assertEquals("test2.txt", uploads.get(1).filename);
        assertEquals(content2, uploads.get(1).data);
      });
    }

  @Test
  public void testFileUploadsSameNameFormMultipartDisableMultipartMixed() throws Exception {
    Buffer content1 = Buffer.buffer(TestUtils.randomAlphaString(16));
    Buffer content2 = Buffer.buffer(TestUtils.randomAlphaString(16));
    List<Upload> toUpload = Arrays.asList(
      Upload.fileUpload("test", "test1.txt", content1),
      Upload.fileUpload("test", "test2.txt", content2)
    );
    ClientMultipartForm form = ClientMultipartForm.multipartForm().mixed(false);
    testFileUploadFormMultipart(form, toUpload, (req, uploads) -> {
      assertEquals(2, uploads.size());
      assertEquals("test", uploads.get(0).name);
      assertEquals("test1.txt", uploads.get(0).filename);
      assertEquals(content1, uploads.get(0).data);
      assertEquals("test", uploads.get(1).name);
      assertEquals("test2.txt", uploads.get(1).filename);
      assertEquals(content2, uploads.get(1).data);
    });
  }

  private void testFileUploadFormMultipart(
    ClientMultipartForm form,
    List<Upload> toUpload,
    BiConsumer<HttpServerRequest,
      List<Upload>> checker) throws Exception {
    File[] testFiles = new File[toUpload.size()];
    for (int i = 0;i < testFiles.length;i++) {
      Upload upload = toUpload.get(i);
      if (upload.file) {
        String name = upload.filename;
        testFiles[i] = testFolder.newFile(name);
        vertx.fileSystem().writeFileBlocking(testFiles[i].getPath(), upload.data);
        form.textFileUpload(toUpload.get(i).name, toUpload.get(i).filename, "text/plain", testFiles[i].getPath());
      } else {
        form.textFileUpload(toUpload.get(i).name, toUpload.get(i).filename, "text/plain", upload.data);
      }
    }

    server.requestHandler(req -> {
      req.setExpectMultipart(true);
      List<Upload> uploads = new ArrayList<>();
      req.uploadHandler(upload -> {
        Buffer fileBuffer = Buffer.buffer();
        assertEquals("text/plain", upload.contentType());
        upload.handler(fileBuffer::appendBuffer);
        upload.endHandler(v -> {
          uploads.add(new Upload(upload.name(), upload.filename(), true, upload.charset(), fileBuffer));
        });
      });
      req.endHandler(v -> {
        checker.accept(req, uploads);
        req.response().end();
      });
    });
    startServer();

    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST))
      .compose(req -> req
        .send(form)
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .await();
  }

  static class Upload {
    final String name;
    final String filename;
    final String charset;
    final Buffer data;
    final boolean file;
    private Upload(String name, String filename, boolean file, String charset, Buffer data) {
      this.name = name;
      this.filename = filename;
      this.charset = charset;
      this.data = data;
      this.file = file;
    }
    static Upload fileUpload(String name, String filename, Buffer data) {
      return new Upload(name, filename, true, null, data);
    }
    static Upload memoryUpload(String name, String filename, Buffer data) {
      return new Upload(name, filename, false, null, data);
    }
  }

  @Test
  public void testMultipartFormMultipleHeaders() throws Exception {
    server.requestHandler(req -> {
      req.setExpectMultipart(true);
      req.endHandler(v -> {
        assertEquals(Arrays.asList("1", "2"), req.headers().getAll("bla"));
        req.response().end();
      });
    });
    startServer();
    client.request(new RequestOptions(requestOptions).putHeader("bla", Arrays.asList("1", "2")).setMethod(HttpMethod.POST))
      .compose(req -> req
        .send(ClientMultipartForm.multipartForm())
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .await();
  }

  @Test
  public void testFileUploadWhenFileDoesNotExist() throws Exception {
    server.requestHandler(req -> {
      fail();
    });
    startServer();
    HttpClientRequest request = client
      .request(new RequestOptions(requestOptions).putHeader("bla", Arrays.asList("1", "2")).setMethod(HttpMethod.POST))
      .await();
    Future<HttpClientResponse> response = request.send(ClientMultipartForm
      .multipartForm()
      .textFileUpload("file", "nonexistentFilename", "nonexistentPathname", "text/plain"));
    try {
      response
          .expecting(HttpResponseExpectation.SC_OK)
          .compose(HttpClientResponse::body)
        .await();
    } catch (Exception err) {
      assertEquals(err.getClass(), StreamResetException.class);
      assertEquals(err.getCause().getClass(), HttpPostRequestEncoder.ErrorDataEncoderException.class);
      assertEquals(err.getCause().getCause().getClass(), FileNotFoundException.class);
    }
    assertTrue(request.response().failed());
  }

  @Test
  public void testInvalidMultipartContentType() throws Exception {
    testInvalidContentType(ClientMultipartForm.multipartForm(), HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED.toString());
  }

  @Test
  public void testInvalidContentType() throws Exception {
    testInvalidContentType(ClientMultipartForm.multipartForm(), HttpHeaders.TEXT_HTML.toString());
  }

  private void testInvalidContentType(ClientForm form, String contentType) throws Exception {
    server.requestHandler(req -> {
      fail();
    });
    startServer();
    try {
      client
        .request(new RequestOptions(requestOptions)
          .putHeader(HttpHeaders.CONTENT_TYPE, contentType)
          .setMethod(HttpMethod.POST))
        .compose(request -> request
          .send(form))
        .await();
      fail();
    } catch (Exception expected) {
    }
  }
}
