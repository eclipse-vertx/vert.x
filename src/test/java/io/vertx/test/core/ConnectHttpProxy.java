package io.vertx.test.core;

import java.net.UnknownHostException;
import java.util.Base64;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.Pump;

/**
 * Http Connect Proxy
 *
 * <p>
 * A simple Http CONNECT proxy for testing https proxy functionality. HTTP server running on localhost allowing CONNECT
 * requests only. This is basically a socket forwarding protocol allowing to use the proxy server to connect to the
 * internet.
 *
 * <p>
 * Usually the server will be started in @Before and stopped in @After for a unit test using HttpClient with the
 * setProxyXXX methods.
 *
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 */
public class ConnectHttpProxy extends TestProxyBase {

  private static final int PORT = 13128;

  private static final Logger log = LoggerFactory.getLogger(ConnectHttpProxy.class);

  private HttpServer server;

  private int error = 0;

  public ConnectHttpProxy(String username) {
    super(username);
  }

  /**
   * Start the server.
   * 
   * @param vertx
   *          Vertx instance to use for creating the server and client
   * @param finishedHandler
   *          will be called when the server has started
   */
  @Override
  public void start(Vertx vertx, Handler<Void> finishedHandler) {
    HttpServerOptions options = new HttpServerOptions();
    options.setHost("localhost").setPort(PORT);
    server = vertx.createHttpServer(options);
    server.requestHandler(request -> {
      log.debug("request "+request.absoluteURI());
      log.debug("headers "+new CaseInsensitiveHeaders().addAll(request.headers()).toString());
      HttpMethod method = request.method();
      String uri = request.uri();
      if (username != null) {
        String auth = request.getHeader("Proxy-Authorization");
        String expected = "Basic " + Base64.getEncoder().encodeToString((username + ":" + username).getBytes());
        if (auth == null || !auth.equals(expected)) {
          log.debug("auth failed "+auth+"/"+expected);
          request.response().setStatusCode(407).end("proxy authentication failed");
          return;
        }
      }
      if (error != 0) {
        request.response().setStatusCode(error).end("proxy request failed");
      } else if (method == HttpMethod.CONNECT) {
        if (!uri.contains(":")) {
          request.response().setStatusCode(403).end("invalid request");
        } else {
          lastUri = uri;
          log.info("uri: " + uri);
          if (forceUri != null) {
            uri = forceUri;
          }
          String[] split = uri.split(":");
          String host = split[0];
          int port;
          try {
            port = Integer.parseInt(split[1]);
          } catch (NumberFormatException ex) {
            port = 443;
          }
          // deny ports not considered safe to connect
          // this will deny access to e.g. smtp port 25 to avoid spammers
          if (port == 8080 || port < 1024 && port != 443) {
            request.response().setStatusCode(403).end("access to port denied");
            return;
          }
          NetSocket serverSocket = request.netSocket();
          NetClientOptions netOptions = new NetClientOptions();
          NetClient netClient = vertx.createNetClient(netOptions);
          netClient.connect(port, host, result -> {
            if (result.succeeded()) {
              NetSocket clientSocket = result.result();
              serverSocket.write("HTTP/1.0 200 Connection established\n\n");
              serverSocket.closeHandler(v -> clientSocket.close());
              clientSocket.closeHandler(v -> serverSocket.close());
              Pump.pump(serverSocket, clientSocket).start();
              Pump.pump(clientSocket, serverSocket).start();
            } else {
              log.debug("connect() failed", result.cause());
              request.response().setStatusCode(403).end("request failed");
            }
          });
        }
      } else if (method == HttpMethod.GET) {
        lastUri = request.uri();
        HttpClient client = vertx.createHttpClient();
        HttpClientRequest clientRequest = client.getAbs(request.uri(), resp -> {
          for (String name : resp.headers().names()) {
            request.response().putHeader(name, resp.headers().getAll(name));
          }
          resp.bodyHandler(body -> {
            request.response().end(body);
          });
        });
        for (String name : request.headers().names()) {
          if (!name.equals("Proxy-Authorization")) {
            clientRequest.putHeader(name, request.headers().getAll(name));
          }
        }
        log.debug("client request headers " + clientRequest.headers().toString());
        clientRequest.exceptionHandler(e -> {
          log.debug("exception", e);
          int status;
          if (e instanceof UnknownHostException) {
            status = 504;
          } else {
            status = 400;
          }
          request.response().setStatusCode(status).end(e.toString()+" on client request");
        });
        clientRequest.end();
      } else {
        request.response().setStatusCode(405).end("method not supported");
      }
    });
    server.listen(server -> {
      finishedHandler.handle(null);
    });
  }

  /**
   * Stop the server.
   * <p>
   * Doesn't wait for the close operation to finish
   */
  @Override
  public void stop() {
    if (server != null) {
      server.close();
      server = null;
    }
  }

  @Override
  public int getPort() {
    return PORT;
  }

  public ConnectHttpProxy setError(int error) {
    this.error = error;
    return this;
  }
}
