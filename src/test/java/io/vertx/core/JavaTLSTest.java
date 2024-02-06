package io.vertx.core;

import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.test.tls.Cert;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;

import static org.junit.Assert.fail;

public class JavaTLSTest {


  static {
    String path = "src/test/resources/tls/client-truststore.jks";
    System.setProperty("javax.net.ssl.trustStore", path);
  }

  // To pass you must configure your local host file so that
  // example.com points to 127.0.0.1

  @Test
  public void javaSSLClientDoesNotPerformHostnameVerificationByDefault() throws Exception {
    // This should pass (no hostname verification)
    javaSSLClientTest(null);
  }

  @Test
  public void javaSSLClientExplicitlyConfiguringHostnameVerification() throws Exception {
    // This should not pass, since hostname verification is configured
    SSLParameters sslParams = new SSLParameters();
    sslParams.setEndpointIdentificationAlgorithm("HTTPS");
    try {
      javaSSLClientTest(sslParams);
      fail();
    } catch (SSLHandshakeException e) {
      Assert.assertEquals("No name matching example.com found", e.getMessage());
    }
  }

  public void javaSSLClientTest(SSLParameters sslParameters) throws Exception {

    Vertx vertx = Vertx.vertx();
    HttpServer server = vertx.createHttpServer(new HttpServerOptions().setSsl(true).setKeyCertOptions(Cert.SERVER_JKS.get()))
      .requestHandler(req -> {
      req.response().end("Hello my CN is localhost");
    });
    server.listen(8080, "localhost").toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

    SSLSocketFactory f = (SSLSocketFactory) SSLSocketFactory.getDefault();

    SSLSocket socket = (SSLSocket) f.createSocket("example.com", 8080);

    if (sslParameters != null) {
      socket.setSSLParameters(sslParameters);
    }

    socket.startHandshake();
    BufferedWriter w = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    InputStream r = socket.getInputStream();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    w.write("GET / HTTP/1.1\r\n\r\n");
    w.flush();
    byte[] buffer = new byte[256];
    int l;
    while ((l = r.read(buffer, 0, buffer.length)) != -1) {
      baos.write(buffer, 0, l);
      if (baos.toString().contains("\r\n\r\n")) {
        break;
      }
    }
    w.close();
    r.close();
    socket.close();
  }
}
