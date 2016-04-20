package io.vertx.test.core;

import java.util.Base64;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
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
 * <p>
 * A simple Http CONNECT proxy for testing https proxy functionality. HTTP server running on localhost allowing CONNECT
 * requests only. This is basically a socket forwarding protocol allowing to use the proxy server to connect to the
 * internet.
 * <p>
 * Usually the server will be started in @Before and stopped in @After for a unit test using HttpClient with the
 * setProxyXXX methods.
 *
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 */
public class ConnectHttpProxy {

  private static final Logger log = LoggerFactory.getLogger(ConnectHttpProxy.class);

  private HttpServer server = null;
  private String user = null;

  private String lastUri = null;

  /**
   * check the last accessed host:ip
   * @return the lastUri
   */
  public String getLastUri() {
    return lastUri;
  }

  /**
   * Start the server.
   * 
   * @param vertx
   *          Vertx instance to use for creating the server and client
   * @param user
   *          user/password to be checked for Basic Auth (for testing username and password are required to be the same)
   * @param finishedHandler
   *          will be called when the start has started
   */
  public void start(Vertx vertx, Handler<Void> finishedHandler) {
    HttpServerOptions options = new HttpServerOptions();
    options.setHost("localhost").setPort(13128);
    server = vertx.createHttpServer(options);
    server.requestHandler(request -> {
      HttpMethod method = request.method();
      String uri = request.uri();
      log.debug("uri:" + uri);
      if (user  != null) {
        String auth = request.getHeader("Proxy-Authorization");
        String expected = "Basic " + Base64.getEncoder().encodeToString((user + ":" + user).getBytes());
        if (auth == null || !auth.equals(expected)) {
          log.debug("authentication failed: " + auth + "/" + expected);
          request.response().setStatusCode(407).end("proxy authentication failed");
          return;
        }
      }
      if (method != HttpMethod.CONNECT || !uri.contains(":")) {
        request.response().setStatusCode(405).end("method not allowed");
      } else {
        lastUri = uri;
        String split[] = uri.split(":");
        String host = split[0];
        int port;
        try {
          port = Integer.parseInt(split[1]);
        } catch (NumberFormatException ex) {
          port = 443;
        }
        NetSocket serverSocket = request.netSocket();
        NetClientOptions netOptions = new NetClientOptions();
        NetClient netClient = vertx.createNetClient(netOptions);
        log.debug("connecting to " + host + ":" + port);
        netClient.connect(port, host, result -> {
          if (result.succeeded()) {
            log.debug("connected");
            NetSocket clientSocket = result.result();
            serverSocket.write("HTTP/1.0 200 Connection established\n\n");
            log.debug("starting pumps");
            serverSocket.closeHandler(v -> clientSocket.close());
            clientSocket.closeHandler(v -> serverSocket.close());
            Pump.pump(serverSocket, clientSocket).start();
            Pump.pump(clientSocket, serverSocket).start();
          } else {
            request.response().setStatusCode(403).end("request failed");
          }
        });
      }
    });
    server.listen(server -> {
      log.debug("proxy server started");
      finishedHandler.handle(null);
    });
  }

  /**
   * Stop the server.
   *
   * Doesn't wait for the close operation to finish
   */
  public void stop() {
    if (server != null) {
      log.debug("stopping proxy server");
      server.close();
      server = null;
    }
  }

  /**
   * @param object
   */
  public void setUsername(String username) {
    this.user = username;
  }

}
