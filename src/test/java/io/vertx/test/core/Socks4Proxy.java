package io.vertx.test.core;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.Pump;

/**
 * SOCKS4 Proxy
 * <p>
 * A simple SOCKS4/4a proxy for testing SOCKS functionality. Currently we only support tcp connect and
 * username auth, which is enough to make the currently implemented client tests to pass.
 *
 * <p>
 * Usually the server will be started in @Before and stopped in @After for a unit test using HttpClient or NetClient
 * with the setProxyOptions method.
 *
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 */
public class Socks4Proxy extends TestProxyBase {

  private static final Logger log = LoggerFactory.getLogger(Socks4Proxy.class);

  private static final Buffer clientRequest = Buffer.buffer(new byte[] { 4, 1 });
  private static final Buffer connectResponse = Buffer.buffer(new byte[] { 0, 90, 0, 0, 0, 0, 0, 0 });
  private static final Buffer errorResponse = Buffer.buffer(new byte[] { 0, 91, 0, 0, 0, 0, 0, 0 });

  private static final int PORT = 11080;

  private NetServer server;

  public Socks4Proxy(String username) {
    super(username);
  }

  /**
   * Start the server.
   * 
   * @param vertx
   *          Vertx instance to use for creating the server and client
   * @param finishedHandler
   *          will be called when the start has started
   */
  @Override
  public void start(Vertx vertx, Handler<Void> finishedHandler) {
    NetServerOptions options = new NetServerOptions();
    options.setHost("localhost").setPort(PORT);
    server = vertx.createNetServer(options);
    server.connectHandler(socket -> {
      socket.handler(buffer -> {
        if (!buffer.getBuffer(0, clientRequest.length()).equals(clientRequest)) {
          throw new IllegalStateException("expected " + toHex(clientRequest) + ", got " + toHex(buffer));
        }
        log.debug("got request: " + toHex(buffer));

        int port = buffer.getUnsignedShort(2);
        String ip = getByte4(buffer.getBuffer(4, 8));

        String authUsername = getString(buffer.getBuffer(8, buffer.length()));

        if (username != null && !authUsername.equals(username)) {
          log.debug("auth failed");
          log.debug("writing: " + toHex(errorResponse));
          socket.write(errorResponse);
          socket.close();
        } else {
          String host;
          if (ip.equals("0.0.0.1")) {
            host = getString(buffer.getBuffer(9 + authUsername.length(), buffer.length()));
          } else {
            host = ip;
          }

          log.debug("connect: " + host + ":" + port);
          socket.handler(null);
          lastUri = host + ":" + port;

          if (forceUri != null) {
            host = forceUri.substring(0, forceUri.indexOf(':'));
            port = Integer.valueOf(forceUri.substring(forceUri.indexOf(':') + 1));
          }
          log.debug("connecting to " + host + ":" + port);
          NetClient netClient = vertx.createNetClient(new NetClientOptions());
          netClient.connect(port, host, result -> {
            if (result.succeeded()) {
              log.debug("writing: " + toHex(connectResponse));
              socket.write(connectResponse);
              log.debug("connected, starting pump");
              NetSocket clientSocket = result.result();
              socket.closeHandler(v -> clientSocket.close());
              clientSocket.closeHandler(v -> socket.close());
              Pump.pump(socket, clientSocket).start();
              Pump.pump(clientSocket, socket).start();
            } else {
              log.error("exception", result.cause());
              socket.handler(null);
              log.debug("writing: " + toHex(errorResponse));
              socket.write(errorResponse);
              socket.close();
            }
          });
        }
      });
    });
    server.listen(result -> {
      log.debug("socks4a server started");
      finishedHandler.handle(null);
    });
  }

  private String getString(Buffer buffer) {
    String string = buffer.toString();
    return string.substring(0, string.indexOf('\0'));
  }

  private String getByte4(Buffer buffer) {
    return String.format("%d.%d.%d.%d", buffer.getByte(0), buffer.getByte(1), buffer.getByte(2), buffer.getByte(3));
  }

  private String toHex(Buffer buffer) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < buffer.length(); i++) {
      sb.append(String.format("%02X ", buffer.getByte(i)));
    }
    return sb.toString();
  }

  /**
   * Stop the server.
   *
   * <p>Doesn't wait for the close operation to finish
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
}
